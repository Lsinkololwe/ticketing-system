package com.pml.identity.boot;

import com.mongodb.reactivestreams.client.MongoClient;
import com.pml.identity.IdentityServiceApplication;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import com.pml.identity.account.AccountService;
import com.pml.identity.account.AccountStates;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.model.User;
import com.pml.shared.constants.UserType;
import com.pml.identity.security.revocation.MongoRevocationStore;
import com.pml.shared.security.revocation.RevocationIdentifier;
import com.pml.shared.security.revocation.RevocationType;
import com.pml.shared.security.revocation.RevocationDecision;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import com.pml.identity.security.ContactHasher;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.RedisNode;
import com.pml.shared.testing.TemporalDevServer;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.temporal.client.WorkflowNotFoundException;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * Opening a staff account with nothing faked: the {@code createUser} operation through identity's real
 * security chain, the real creation workflow on a Temporal server, MongoDB transactions and the staff
 * realm of a real Keycloak 26.5.2. OWASP A01 (who may create, which roles) and A02 (what Temporal keeps).
 *
 * <p>What only a real server can show: the person is created with the two required actions the realm
 * must enforce and unable to sign in until the end, a role the realm does not hold is refused by Keycloak
 * and the creation is taken back, and a refused request never starts an execution.</p>
 */
@Tag("L2")
@Tag("ET-IDN-001")
@Tag("OWASP")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("ET-IDN-001 · creating a staff account end to end against real Keycloak, Temporal and MongoDB")
@SpringBootTest(classes = IdentityServiceApplication.class)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
class StaffAccountCreationEndToEndTest {

    private static final String DATABASE = IdentityStack.newDatabase();
    private static final String STAFF = "myticketzm-admin";
    private static final String BUYERS = "myticketzm";

    private static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.5.2")
            .withRealmImportFiles("/keycloak/myticketzm-realm.json", "/keycloak/myticketzm-admin-realm.json");

    /** The staff realm's own password rules, as the production realm declares them. */
    private static final String POLICY = "length(12) and notUsername and notEmail and passwordHistory(3)";

    static {
        KEYCLOAK.start();
        // The test realm file is a stub: give it the production password policy and a client that can
        // present a username and password, so a password's validity can be proved against the real server.
        RealmRepresentation realm = KEYCLOAK.getKeycloakAdminClient().realm(STAFF).toRepresentation();
        realm.setPasswordPolicy(POLICY);
        KEYCLOAK.getKeycloakAdminClient().realm(STAFF).update(realm);
        ClientRepresentation direct = new ClientRepresentation();
        direct.setClientId("staff-direct");
        direct.setPublicClient(true);
        direct.setDirectAccessGrantsEnabled(true);
        direct.setEnabled(true);
        try (Response created = KEYCLOAK.getKeycloakAdminClient().realm(STAFF).clients().create(direct)) {
            assertThat(created.getStatus()).isEqualTo(201);
        }
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        IdentityStack.register(registry, DATABASE);
        registry.add("keycloak.server-url", KEYCLOAK::getAuthServerUrl);
        registry.add("keycloak.admin-username", KEYCLOAK::getAdminUsername);
        registry.add("KEYCLOAK_ADMIN_PASSWORD", KEYCLOAK::getAdminPassword);
        // short enough that a creation that cannot finish answers within the test's patience
        registry.add("identity.staff.create-wait", () -> "PT8S");
    }

    @AfterAll
    static void stop() {
        KEYCLOAK.stop();
    }

    @Autowired
    ApplicationContext context;
    @Autowired
    TemporalGateway temporal;
    @Autowired
    ContactHasher hasher;
    @Autowired
    ReactiveMongoTemplate template;
    @Autowired
    MongoRevocationStore revocations;

    // ---------------------------------------------------------------------------------- helpers

    private Map<String, Object> graphql(String subject, String role, String query) {
        return WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient()
                .responseTimeout(Duration.ofSeconds(120)).build()
                .mutateWith(mockJwt().jwt(jwt -> jwt.subject(subject)
                                .claim("jti", UUID.randomUUID().toString()).claim("sid", UUID.randomUUID().toString())
                                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)))
                        .authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                .post().uri("/graphql").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", query)).exchange()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() { })
                .returnResult().getResponseBody();
    }

    private static String create(String email, String role, String extra) {
        return "mutation { createUser(input: {email: \"" + email + "\", firstName: \"Chileshe\", lastName: \"Mwansa\", role: "
                + role + extra + "}) { user { id email roles phoneNumber } temporaryPassword } }";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map<String, Object> response) {
        assertThat(response.get("errors")).as("errors in a createUser answer (the response is not printed: it can hold a password)")
                .isNull();
        return (Map<String, Object>) ((Map<String, Object>) response.get("data")).get("createUser");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> userOf(Map<String, Object> result) {
        return (Map<String, Object>) result.get("user");
    }

    private static String passwordOf(Map<String, Object> result) {
        return (String) result.get("temporaryPassword");
    }

    /**
     * A username and password presented to the staff realm. The real server tells the three cases apart: a
     * wrong password is "Invalid user credentials", a right but temporary one is "Account is not fully set
     * up" (it is valid, and what Keycloak will first demand is a new password and a second factor), and a
     * fully set-up account gets tokens.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> present(String username, String password) throws Exception {
        String form = "grant_type=password&client_id=staff-direct&username="
                + java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8)
                + "&password=" + java.net.URLEncoder.encode(password, java.nio.charset.StandardCharsets.UTF_8);
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create(KEYCLOAK.getAuthServerUrl() + "/realms/" + STAFF + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
        Map<String, Object> body = new ObjectMapper().readValue(response.body(), Map.class);
        body.put("httpStatus", response.statusCode());
        return body;
    }

    private static void assertIsTheTemporaryPassword(String username, String password) throws Exception {
        Map<String, Object> answer = present(username, password);
        assertThat(answer.get("httpStatus")).isEqualTo(400);
        assertThat(String.valueOf(answer.get("error_description")))
                .as("the credential is right but the account still has to set a password and a second factor")
                .isEqualTo("Account is not fully set up");
    }

    private static void assertIsNotAPassword(String username, String password) throws Exception {
        Map<String, Object> answer = present(username, password);
        assertThat(answer.get("httpStatus")).isEqualTo(401);
        assertThat(String.valueOf(answer.get("error_description"))).isEqualTo("Invalid user credentials");
    }

    /** Everything MongoDB holds for this service in this test run, as one string. */
    private String wholeDatabase() {
        MongoClient client = IdentityStack.mongoClient();
        try {
            var database = client.getDatabase(DATABASE);
            StringBuilder everything = new StringBuilder();
            for (String name : Flux.from(database.listCollectionNames()).collectList().block()) {
                Flux.from(database.getCollection(name).find()).collectList().block()
                        .forEach(document -> everything.append(name).append(' ').append(document.toJson()).append('\n'));
            }
            return everything.toString();
        } finally {
            client.close();
        }
    }

    /** Every log line the application writes while a call runs, with its exception text. */
    private static final class LogCapture implements AutoCloseable {
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        private final Logger root = (Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        private final Logger application = (Logger) org.slf4j.LoggerFactory.getLogger("com.pml");
        private final Level before = application.getLevel();

        LogCapture() {
            application.setLevel(Level.DEBUG);
            appender.start();
            root.addAppender(appender);
        }

        String text() {
            StringBuilder all = new StringBuilder();
            for (ILoggingEvent event : List.copyOf(appender.list)) {
                all.append(event.getFormattedMessage()).append('\n');
                if (event.getThrowableProxy() != null) {
                    all.append(ThrowableProxyUtil.asString(event.getThrowableProxy())).append('\n');
                }
            }
            return all.toString();
        }

        int lines() {
            return appender.list.size();
        }

        @Override
        public void close() {
            root.detachAppender(appender);
            application.setLevel(before);
        }
    }

    @SuppressWarnings("unchecked")
    private static String errorCode(Map<String, Object> response) {
        List<Map<String, Object>> errors = (List<Map<String, Object>>) response.get("errors");
        assertThat(errors).as("expected an error in %s", response).isNotNull().isNotEmpty();
        return String.valueOf(((Map<String, Object>) errors.get(0).get("extensions")).get("errorCode"));
    }

    private static String email() {
        return "staff-" + UUID.randomUUID().toString().substring(0, 10) + "@example.org";
    }

    private List<Document> mongo(String collection, Document filter) {
        MongoClient client = IdentityStack.mongoClient();
        try {
            return Flux.from(client.getDatabase(DATABASE).getCollection(collection).find(filter)).collectList().block();
        } finally {
            client.close();
        }
    }

    private static Keycloak admin() {
        return KEYCLOAK.getKeycloakAdminClient();
    }

    private static List<UserRepresentation> staffRealmUsers(String email) {
        return admin().realm(STAFF).users().searchByUsername(email, true);
    }

    private String workflowId(String email) {
        return WorkflowIds.staffAccountCreate(hasher.hash(ContactType.EMAIL, email));
    }

    private void assertNoExecution(String email) {
        assertThatThrownBy(() -> temporal.client().fetchHistory(workflowId(email)))
                .as("no execution was started for this address")
                .isInstanceOfAny(WorkflowNotFoundException.class, io.grpc.StatusRuntimeException.class)
                .hasMessageContaining("not found");
    }

    // ---------------------------------------------------------------------------------- the account

    @Test
    @Order(1)
    @DisplayName("a super administrator opens a staff account: ACTIVE, the staff role in the staff realm, first sign-in forced through password and second factor")
    void opensTheAccount() throws Exception {
        String email = email();

        Map<String, Object> answer = data(graphql("root-1", "SUPER_ADMIN",
                create(email, "FINANCE", ", phoneNumber: \"+260971234567\"")));
        Map<String, Object> created = userOf(answer);

        assertThat(created.get("email")).isEqualTo(email);
        assertThat(created.get("roles").toString()).contains("FINANCE").contains("CUSTOMER");
        UserRepresentation person = staffRealmUsers(email).get(0);
        assertThat(person.getId()).isEqualTo(created.get("id"));
        assertThat(person.isEnabled()).as("sign-in is open once the account is ACTIVE").isTrue();
        assertThat(person.getRequiredActions()).as("what the realm will force at first sign-in, read back from the server")
                .contains("CONFIGURE_TOTP", "UPDATE_PASSWORD");
        String temporary = passwordOf(answer);
        assertThat(temporary).as("the creating call is given the one-time password").isNotBlank().hasSizeGreaterThanOrEqualTo(16)
                .doesNotContain(email).doesNotContain(email.substring(0, email.indexOf('@')));
        assertIsTheTemporaryPassword(email, temporary);
        assertThat(admin().realm(STAFF).users().get(person.getId()).credentials())
                .extracting(CredentialRepresentation::getType).as("one password credential and no second factor yet")
                .containsExactly(CredentialRepresentation.PASSWORD);
        assertThat(admin().realm(STAFF).users().get(person.getId()).roles().realmLevel().listAll())
                .extracting(role -> role.getName()).contains("FINANCE").doesNotContain("SUPER_ADMIN", "ADMIN");
        assertThat(admin().realm(BUYERS).users().searchByUsername(email, true)).as("not in the buyer realm").isEmpty();

        Document stored = mongo("identity_users", new Document("_id", person.getId())).get(0);
        assertThat(stored.getString("status")).isEqualTo("ACTIVE");
        assertThat(stored.getString("keycloakUserId")).isEqualTo(person.getId());
        assertThat(stored.getString("createdVia")).isEqualTo("STAFF_ADMIN");
        assertThat(stored.getString("createdBy")).as("who created whom").isEqualTo("root-1");
        assertThat(stored).doesNotContainKey("staffCreation");
        assertThat(mongo("identity_contacts", new Document("accountId", person.getId())))
                .as("a staff account carries no customer contact sign-in").isEmpty();
        List<Document> grants = mongo("identity_audit_logs", new Document("action", "ROLE_GRANT").append("resourceId", person.getId()));
        assertThat(grants).singleElement().satisfies(row -> assertThat(row.getString("performedBy")).isEqualTo("root-1"));
    }

    @Test
    @Order(2)
    @DisplayName("the one-time password is in no log line, no stored document, no workflow history, no audit row and no notification")
    void passwordIsNowhereButTheAnswer() {
        String email = email();
        Map<String, Object> answer;
        String logged;
        try (LogCapture logs = new LogCapture()) {
            answer = data(graphql("root-1", "SUPER_ADMIN", create(email, "ADMIN", ", phoneNumber: \"+260971234568\"")));
            // the error paths log too: a refused repeat and a refused role are part of the same capture
            graphql("root-2", "SUPER_ADMIN", create(email, "ADMIN", ""));
            graphql("root-1", "SUPER_ADMIN", create(email(), "CUSTOMER", ""));
            org.slf4j.LoggerFactory.getLogger("com.pml.identity.capture.probe").info("capture probe");
            logged = logs.text();
            assertThat(logged).as("the capture is live and saw the application's logging").contains("capture probe");
            assertThat(logs.lines()).isGreaterThan(1);
        }
        String password = passwordOf(answer);
        assertThat(password).isNotBlank();

        String history = temporal.client().fetchHistory(workflowId(email)).toJson(true);
        String stored = wholeDatabase();

        assertThat(history).contains("StaffAccountClaim")
                .doesNotContain(password).doesNotContain(email).doesNotContain(email.substring(0, email.indexOf('@')))
                .doesNotContain("Chileshe").doesNotContain("Mwansa").doesNotContain("260971234568");
        assertThat(logged).as("application logs, DEBUG and above").doesNotContain(password);
        assertThat(stored).as("every collection of the service's database, audit rows and notices included")
                .doesNotContain(password);
        assertThat(stored).contains("ROLE_GRANT");
        assertThat(workflowId(email)).doesNotContain("@").doesNotContain(email.substring(0, 8));
    }

    @Test
    @Order(3)
    @DisplayName("a repeat of a finished request returns the account and no password, and does not touch the credential; another administrator's duplicate is refused")
    void repeatedAndDuplicateRequests() throws Exception {
        String email = email();
        Map<String, Object> first = data(graphql("root-1", "SUPER_ADMIN", create(email, "FINANCE", "")));
        String id = userOf(first).get("id").toString();
        String password = passwordOf(first);

        Map<String, Object> repeat = data(graphql("root-1", "SUPER_ADMIN", create(email, "FINANCE", "")));
        String code = errorCode(graphql("root-2", "SUPER_ADMIN", create(email, "FINANCE", "")));

        assertThat(userOf(repeat).get("id").toString()).isEqualTo(id);
        assertThat(passwordOf(repeat)).as("the password is shown once").isNull();
        assertIsTheTemporaryPassword(email, password);
        assertThat(code).isEqualTo("RESOURCE_CONFLICT");
        assertThat(staffRealmUsers(email)).hasSize(1);
        assertThat(mongo("identity_users", new Document("email", email))).hasSize(1);
    }

    @Test
    @Order(4)
    @DisplayName("an administrator, a finance officer, an organizer and a customer are refused, and no execution, Keycloak user or record exists")
    void refusedCallersStartNothing() {
        String email = email();

        assertThat(errorCode(graphql("admin-1", "ADMIN", create(email, "FINANCE", "")))).isEqualTo("ACTOR_NOT_PERMITTED");
        assertThat(errorCode(graphql("admin-1", "ADMIN", create(email, "SUPER_ADMIN", "")))).isEqualTo("ACTOR_NOT_PERMITTED");
        for (String role : List.of("FINANCE", "ORGANIZER", "CUSTOMER")) {
            Map<String, Object> response = graphql("someone-" + role, role, create(email, "ADMIN", ""));
            assertThat(response.get("errors")).as(role).isNotNull();
            assertThat(response.toString()).as(role).doesNotContain("\"id\"");
        }

        assertThat(staffRealmUsers(email)).isEmpty();
        assertThat(mongo("identity_users", new Document("email", email))).isEmpty();
        assertNoExecution(email);
    }

    @Test
    @Order(5)
    @DisplayName("a buyer role is refused as not well formed even for a super administrator, and nothing starts")
    void buyerRolesAreRefused() {
        String email = email();

        assertThat(errorCode(graphql("root-1", "SUPER_ADMIN", create(email, "CUSTOMER", "")))).isEqualTo("COMMAND_NOT_WELL_FORMED");
        assertThat(errorCode(graphql("root-1", "SUPER_ADMIN", create(email, "ORGANIZER", "")))).isEqualTo("COMMAND_NOT_WELL_FORMED");

        assertThat(staffRealmUsers(email)).isEmpty();
        assertNoExecution(email);
    }

    // ---------------------------------------------------------------------------------- cannot finish

    @Test
    @Order(6)
    @DisplayName("a role the staff realm does not hold is refused by Keycloak: the caller is told to ask again, then the Keycloak user and the record are gone")
    void roleTheRealmDoesNotHoldIsTakenBack() {
        String email = email();

        // the realm file used here defines ADMIN, SUPER_ADMIN and FINANCE; FINANCE_LEAD is a role Keycloak cannot grant
        String code = errorCode(graphql("root-1", "SUPER_ADMIN", create(email, "FINANCE_LEAD", "")));

        assertThat(code).as("not finished within the wait: nothing half-made is returned").isEqualTo("STAFF_CREATION_IN_PROGRESS");
        await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            assertThat(staffRealmUsers(email)).as("the Keycloak user is taken back").isEmpty();
            assertThat(mongo("identity_users", new Document("email", email))).as("and the record").isEmpty();
        });
    }

    @Test
    @Order(7)
    @DisplayName("each creation gets its own password, and every one satisfies the staff realm's password policy")
    void everyCreationGetsItsOwnPassword() throws Exception {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 4; i++) {
            String email = email();
            String password = passwordOf(data(graphql("root-1", "SUPER_ADMIN", create(email, "ADMIN", ""))));
            assertThat(password).hasSizeGreaterThanOrEqualTo(16);
            assertIsTheTemporaryPassword(email, password);
            seen.add(password);
        }
        assertThat(seen).hasSize(4);
    }

    @Test
    @Order(8)
    @DisplayName("an unfinished creation, repeated, is given a fresh password and the first one stops working")
    void unfinishedRepeatGetsAFreshPassword() throws Exception {
        String email = email();
        String firstPassword = "First-Attempt-Pw-1" + UUID.randomUUID().toString().substring(0, 6);
        // What a creation that died after the person was made in Keycloak leaves: a record in PROVISIONING and
        // a Keycloak user that cannot sign in yet, holding a password nobody was ever shown.
        UserRepresentation leftover = new UserRepresentation();
        leftover.setUsername(email);
        leftover.setEmail(email);
        leftover.setFirstName("Chileshe");
        leftover.setLastName("Mwansa");
        leftover.setEnabled(false);
        CredentialRepresentation old = new CredentialRepresentation();
        old.setType(CredentialRepresentation.PASSWORD);
        old.setValue(firstPassword);
        old.setTemporary(true);
        leftover.setCredentials(List.of(old));
        try (Response created = admin().realm(STAFF).users().create(leftover)) {
            assertThat(created.getStatus()).isEqualTo(201);
        }
        User record = User.builder().id(com.pml.identity.account.StaffAccountSteps.creationId(hasher.hash(ContactType.EMAIL, email)))
                .username(email).email(email).firstName("Chileshe").lastName("Mwansa")
                .roles(java.util.EnumSet.of(UserType.CUSTOMER)).createdVia(AccountService.STAFF_ADMIN).createdBy("root-1")
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .staffCreation(new User.StaffCreation(UserType.FINANCE, true, true)).build();
        AccountStates.apply(record, AccountState.PROVISIONING);
        template.insert(record).block();

        Map<String, Object> answer = data(graphql("root-1", "SUPER_ADMIN", create(email, "FINANCE", "")));

        String fresh = passwordOf(answer);
        assertThat(fresh).isNotBlank().isNotEqualTo(firstPassword);
        assertIsNotAPassword(email, firstPassword);
        assertIsTheTemporaryPassword(email, fresh);
        assertThat(staffRealmUsers(email)).singleElement().satisfies(person -> {
            assertThat(person.isEnabled()).isTrue();
            assertThat(person.getRequiredActions()).contains("UPDATE_PASSWORD", "CONFIGURE_TOTP");
        });
    }

    // ---------------------------------------------------------------------------------- reset

    private record Active(String username, String id, String sid, Jwt jwt) {
    }

    /**
     * A staff member who has finished setting up (a password of their own, no pending actions) and is signed in,
     * so there is a real session and a real token for a reset to cut.
     */
    private Active signedInStaff(String role) throws Exception {
        String email = email();
        String id = userOf(data(graphql("root-1", "SUPER_ADMIN", create(email, role, "")))).get("id").toString();
        String own = "Their-Own-Pw-" + UUID.randomUUID().toString().substring(0, 8);
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(own);
        credential.setTemporary(false);
        var resource = admin().realm(STAFF).users().get(id);
        resource.resetPassword(credential);
        UserRepresentation person = resource.toRepresentation();
        person.setRequiredActions(List.of());
        resource.update(person);

        Map<String, Object> grant = present(email, own);
        assertThat(grant.get("httpStatus")).as("the person signs in with their own password").isEqualTo(200);
        var claims = SignedJWT.parse((String) grant.get("access_token")).getJWTClaimsSet();
        Jwt jwt = Jwt.withTokenValue((String) grant.get("access_token")).header("alg", "RS256")
                .claims(c -> c.putAll(claims.getClaims()))
                .issuedAt(claims.getIssueTime().toInstant()).expiresAt(claims.getExpirationTime().toInstant()).build();
        return new Active(email, id, (String) claims.getClaim("sid"), jwt);
    }

    private boolean tokenRefused(Active person) {
        var result = WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient().build()
                .mutateWith(mockJwt().jwt(person.jwt()).authorities(new SimpleGrantedAuthority("ROLE_FINANCE")))
                .post().uri("/graphql").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", "{ __typename }")).exchange().expectBody(String.class).returnResult();
        return result.getStatus().value() == 401;
    }

    private static String reset(String userId) {
        return "mutation { resetStaffTemporaryPassword(userId: \"" + userId + "\") { user { id } temporaryPassword } }";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resetData(Map<String, Object> response) {
        assertThat(response.get("errors")).as("errors in a reset answer (not printed: it can hold a password)").isNull();
        return (Map<String, Object>) ((Map<String, Object>) response.get("data")).get("resetStaffTemporaryPassword");
    }

    @Test
    @Order(9)
    @DisplayName("a reset gives a new temporary password, ends the person's sessions, revokes their token and records who did it")
    void resetCutsAccessAndIssuesANewPassword() throws Exception {
        Active person = signedInStaff("FINANCE");
        assertThat(tokenRefused(person)).as("the person's token works before the reset").isFalse();
        assertThat(admin().realm(STAFF).users().get(person.id()).getUserSessions()).hasSize(1);
        Map<String, Object> answer;
        String logged;
        try (LogCapture logs = new LogCapture()) {
            answer = resetData(graphql("root-1", "SUPER_ADMIN", reset(person.id())));
            logged = logs.text();
        }

        String fresh = (String) answer.get("temporaryPassword");
        assertThat(fresh).isNotBlank().hasSizeGreaterThanOrEqualTo(16);
        assertIsNotAPassword(person.username(), "Their-Own-Pw-anything");
        assertIsTheTemporaryPassword(person.username(), fresh);
        assertThat(admin().realm(STAFF).users().get(person.id()).getUserSessions()).as("their Keycloak sessions are gone").isEmpty();
        assertThat(tokenRefused(person)).as("the token they held is revoked").isTrue();
        assertThat(revocations.check(List.of(new RevocationIdentifier(RevocationType.SESSION, person.sid()))).block())
                .isEqualTo(RevocationDecision.REVOKED);
        assertThat(admin().realm(STAFF).users().get(person.id()).toRepresentation().getRequiredActions())
                .contains("UPDATE_PASSWORD", "CONFIGURE_TOTP");

        List<Document> rows = mongo("identity_audit_logs",
                new Document("action", "STAFF_PASSWORD_RESET").append("resourceId", person.id()));
        assertThat(rows).singleElement().satisfies(row -> assertThat(row.getString("performedBy")).isEqualTo("root-1"));
        assertThat(wholeDatabase()).doesNotContain(fresh);
        assertThat(logged).doesNotContain(fresh);

        // The person may sign in again straight away with what they were given: the revocation covered the
        // sessions that existed, not the person.
        String second = (String) resetData(graphql("root-1", "SUPER_ADMIN", reset(person.id()))).get("temporaryPassword");
        assertThat(second).isNotEqualTo(fresh);
        assertIsNotAPassword(person.username(), fresh);
        assertIsTheTemporaryPassword(person.username(), second);
    }

    @Test
    @Order(10)
    @DisplayName("a reset is refused for an administrator, and for a customer or organizer account, and changes nothing")
    void resetIsRefusedWhereItShould() throws Exception {
        String email = email();
        Map<String, Object> created = data(graphql("root-1", "SUPER_ADMIN", create(email, "FINANCE", "")));
        String id = userOf(created).get("id").toString();
        String password = passwordOf(created);
        String buyerKeycloakId = UUID.randomUUID().toString();
        User buyer = User.builder().id(buyerKeycloakId).keycloakUserId(buyerKeycloakId).username("buyer-" + buyerKeycloakId)
                .roles(java.util.EnumSet.of(UserType.CUSTOMER, UserType.ORGANIZER))
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        AccountStates.apply(buyer, AccountState.ACTIVE);
        template.insert(buyer).block();

        Map<String, Object> byAdmin = graphql("admin-1", "ADMIN", reset(id));
        Map<String, Object> onBuyer = graphql("root-1", "SUPER_ADMIN", reset(buyerKeycloakId));

        assertThat(byAdmin.get("errors")).as("an administrator is refused").isNotNull();
        assertThat(String.valueOf(byAdmin.get("data"))).doesNotContain("temporaryPassword=");
        assertThat(errorCode(onBuyer)).isEqualTo("NOT_A_STAFF_ACCOUNT");
        assertIsTheTemporaryPassword(email, password);
        assertThat(mongo("identity_audit_logs", new Document("action", "STAFF_PASSWORD_RESET"))
                .stream().filter(row -> id.equals(row.getString("resourceId")) || buyerKeycloakId.equals(row.getString("resourceId"))))
                .as("a refused reset leaves no row").isEmpty();
    }

    @SuppressWarnings("unused")
    private static final Class<?>[] FIXTURES = {MongoReplicaSet.class, RedisNode.class, TemporalDevServer.class};
}
