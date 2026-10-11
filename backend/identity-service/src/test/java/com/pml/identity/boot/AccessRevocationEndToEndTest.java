package com.pml.identity.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.mongodb.reactivestreams.client.MongoClient;
import com.nimbusds.jwt.SignedJWT;
import com.pml.identity.IdentityServiceApplication;
import com.pml.identity.domain.model.User;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Cutting access on purpose, with real Keycloak tokens, MongoDB, Redis and the security chain: an
 * administrator revokes a token and then a person, a signed-in person signs out of their other
 * devices, someone without the role is refused every administrative operation, and an expired
 * token is refused as unauthenticated without being mistaken for a revoked one.
 */
@Tag("L3")
@Tag("ET-IDN-003")
@Tag("OWASP")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Revoking access through the GraphQL surface cuts the real tokens it names, and only those")
@SpringBootTest(classes = IdentityServiceApplication.class)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
class AccessRevocationEndToEndTest {

    private static final String DATABASE = IdentityStack.newDatabase();
    private static final String BUYERS = "myticketzm";
    private static final String PASSWORD = "Test-pass-123";
    private static final int KEYCLOAK_PORT = TestSocketUtils.findAvailableTcpPort();

    private static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.5.2")
            .withRealmImportFiles("/keycloak/myticketzm-realm.json", "/keycloak/myticketzm-admin-realm.json")
            .withCreateContainerCmdModifier(cmd -> {
                Ports ports = new Ports();
                ports.bind(ExposedPort.tcp(8080), Ports.Binding.bindPort(KEYCLOAK_PORT));
                ports.bind(ExposedPort.tcp(8443), Ports.Binding.empty());
                ports.bind(ExposedPort.tcp(9000), Ports.Binding.empty());
                cmd.getHostConfig().withPortBindings(ports);
            });

    static {
        KEYCLOAK.start();
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        IdentityStack.register(registry, DATABASE);
        registry.add("keycloak.server-url", () -> "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK_PORT);
        registry.add("keycloak.admin-username", KEYCLOAK::getAdminUsername);
        registry.add("KEYCLOAK_ADMIN_PASSWORD", KEYCLOAK::getAdminPassword);
    }

    @AfterAll
    static void stop() {
        KEYCLOAK.stop();
    }

    @Autowired
    ApplicationContext context;
    @Autowired
    ReactiveMongoTemplate template;
    @Autowired
    com.pml.identity.service.OrganizationMemberService members;

    private record Session(String accessToken, String refreshToken, String jti, String sid, String sub, Jwt jwt) {
    }

    private static String username;
    private static Session current;
    private static Session phone;
    private static Session leaked;

    // ---------------------------------------------------------------------------------- helpers

    private Session login() throws Exception {
        return login(username);
    }

    /** A person who exists in Keycloak and, with the same id, in identity. */
    private String register(String prefix) {
        String name = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        UserRepresentation user = new UserRepresentation();
        user.setUsername(name);
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setEmail(name + "@example.com");
        user.setFirstName("Access");
        user.setLastName("Test");
        CredentialRepresentation password = new CredentialRepresentation();
        password.setType(CredentialRepresentation.PASSWORD);
        password.setValue(PASSWORD);
        password.setTemporary(false);
        user.setCredentials(List.of(password));
        try (Response created = KEYCLOAK.getKeycloakAdminClient().realm(BUYERS).users().create(user)) {
            assertThat(created.getStatus()).isEqualTo(201);
        }
        return name;
    }

    private Session loginAndKnown(String name) throws Exception {
        Session session = login(name);
        template.save(User.builder().id(session.sub()).keycloakUserId(session.sub()).username(name)
                .email(name + "@example.com").emailVerified(true).firstName("Access").lastName("Test")
                .createdAt(Instant.now()).build()).block();
        return session;
    }

    private Session login(String name) throws Exception {
        Map<String, Object> grant = tokenRequest("grant_type=password&client_id=test-direct&username="
                + URLEncoder.encode(name, StandardCharsets.UTF_8) + "&password=" + PASSWORD);
        assertThat(grant.get("httpStatus")).as("token grant: %s", grant).isEqualTo(200);
        String access = (String) grant.get("access_token");
        var claims = SignedJWT.parse(access).getJWTClaimsSet();
        Jwt jwt = Jwt.withTokenValue(access).header("alg", "RS256")
                .claims(c -> c.putAll(claims.getClaims()))
                .issuedAt(claims.getIssueTime().toInstant()).expiresAt(claims.getExpirationTime().toInstant())
                .build();
        return new Session(access, (String) grant.get("refresh_token"), claims.getJWTID(),
                (String) claims.getClaim("sid"), claims.getSubject(), jwt);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> tokenRequest(String form) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK_PORT + "/realms/" + BUYERS
                                + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
        Map<String, Object> body = new ObjectMapper().readValue(response.body(), Map.class);
        body.put("httpStatus", response.statusCode());
        return body;
    }

    private WebTestClient.ResponseSpec post(Jwt jwt, String role, String query) {
        var mutator = mockJwt().jwt(jwt);
        if (role != null) {
            mutator = mutator.authorities(new SimpleGrantedAuthority("ROLE_" + role));
        }
        return WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient()
                .responseTimeout(Duration.ofSeconds(60)).build()
                .mutateWith(mutator)
                .post().uri("/graphql").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", query)).exchange();
    }

    private String graphql(Jwt jwt, String role, String query) {
        return post(jwt, role, query).expectBody(String.class).returnResult().getResponseBody();
    }

    private Jwt adminToken() {
        return Jwt.withTokenValue("admin").header("alg", "RS256").subject("admin-" + UUID.randomUUID())
                .claim("jti", UUID.randomUUID().toString()).claim("sid", UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    }

    private boolean refused(Session session) {
        var result = post(session.jwt(), "USER", "{ __typename }").expectBody(String.class).returnResult();
        boolean unauthorized = result.getStatus().value() == 401;
        if (unauthorized) {
            assertThat(result.getResponseBody()).contains("\"code\":\"TOKEN_REVOKED\"")
                    .contains("\"retryable\":false");
        }
        return unauthorized;
    }

    private long revocationRows() {
        MongoClient client = IdentityStack.mongoClient();
        try {
            return Long.valueOf(reactor.core.publisher.Mono.from(client.getDatabase(DATABASE)
                    .getCollection("identity_token_revocations").countDocuments()).block());
        } finally {
            client.close();
        }
    }

    // ---------------------------------------------------------------------------------- the story

    @Test
    @Order(1)
    @DisplayName("a person with three live sessions, known to identity, starts with every token accepted")
    void threeSessionsWork() throws Exception {
        username = "access-" + UUID.randomUUID().toString().substring(0, 8);
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setEmail(username + "@example.com");
        user.setFirstName("Access");
        user.setLastName("Test");
        CredentialRepresentation password = new CredentialRepresentation();
        password.setType(CredentialRepresentation.PASSWORD);
        password.setValue(PASSWORD);
        password.setTemporary(false);
        user.setCredentials(List.of(password));
        try (Response created = KEYCLOAK.getKeycloakAdminClient().realm(BUYERS).users().create(user)) {
            assertThat(created.getStatus()).isEqualTo(201);
        }

        current = login();
        phone = login();
        leaked = login();
        template.save(User.builder().id(current.sub()).keycloakUserId(current.sub()).username(username)
                .email(username + "@example.com").emailVerified(true).firstName("Access").lastName("Test")
                .createdAt(Instant.now()).build()).block();

        assertThat(Set.of(current.sid(), phone.sid(), leaked.sid())).hasSize(3);
        assertThat(refused(current)).isFalse();
        assertThat(refused(phone)).isFalse();
        assertThat(refused(leaked)).isFalse();
    }

    @Test
    @Order(2)
    @DisplayName("someone without an administrator role is refused every revocation operation and nothing is written")
    void nonAdministratorIsRefused() {
        long before = revocationRows();

        for (String operation : List.of(
                "mutation { revokeToken(jti: \"" + leaked.jti() + "\", reason: \"this should never be accepted\") { id } }",
                "mutation { revokeSessionAccess(sid: \"" + phone.sid() + "\", reason: \"this should never be accepted\") { id } }",
                "mutation { revokeUserAccess(userId: \"" + current.sub() + "\", reason: \"this should never be accepted\") { id } }",
                "{ activeRevocations { content { id } } }")) {
            String response = graphql(current.jwt(), "USER", operation);
            assertThat(response).as(operation).contains("errors");
            assertThat(response).as(operation).doesNotContain("\"data\":{\"revoke").doesNotContain("\"content\":[");
        }

        assertThat(revocationRows()).as("nothing persisted").isEqualTo(before);
        assertThat(refused(leaked)).isFalse();
    }

    @Test
    @Order(3)
    @DisplayName("an administrator's revocation without a written reason of twenty characters is refused and persists nothing")
    void administratorMustExplain() {
        long before = revocationRows();

        String response = graphql(adminToken(), "ADMIN",
                "mutation { revokeToken(jti: \"" + leaked.jti() + "\", reason: \"too short\") { id } }");

        assertThat(response).contains("COMMAND_NOT_WELL_FORMED");
        assertThat(revocationRows()).isEqualTo(before);
        assertThat(refused(leaked)).isFalse();
    }

    @Test
    @Order(4)
    @DisplayName("an administrator revokes one leaked token: that token is refused with TOKEN_REVOKED, the person's others are not")
    void administratorRevokesOneToken() {
        String response = graphql(adminToken(), "ADMIN",
                "mutation { revokeToken(jti: \"" + leaked.jti()
                        + "\", reason: \"token seen in a public bug report\") { id type trigger } }");

        assertThat(response).contains("\"type\":\"TOKEN\"").contains("\"trigger\":\"ADMIN_REVOKE\"");
        long rows = revocationRows();
        assertThat(refused(leaked)).isTrue();
        assertThat(refused(leaked)).isTrue();
        assertThat(revocationRows()).as("refusing a request writes nothing").isEqualTo(rows);
        assertThat(refused(current)).isFalse();
        assertThat(refused(phone)).isFalse();
    }

    @Test
    @Order(5)
    @DisplayName("signing out everywhere ends the other devices' sessions and leaves the one making the request")
    void signOutEverywhereKeepsTheCurrentSession() throws Exception {
        String response = graphql(current.jwt(), "USER", "mutation { signOutEverywhere }");

        assertThat(response).contains("\"signOutEverywhere\":");
        assertThat(refused(phone)).as("the other device's token").isTrue();
        assertThat(refused(current)).as("the session that asked").isFalse();
        Map<String, Object> refreshed = tokenRequest(
                "grant_type=refresh_token&client_id=test-direct&refresh_token=" + phone.refreshToken());
        assertThat(refreshed.get("httpStatus")).as("Keycloak ended the other session as well").isEqualTo(400);
    }

    @Test
    @Order(6)
    @DisplayName("an expired token is refused as unauthenticated, not as revoked, and writes nothing")
    void expiredTokenIsNotARevocation() {
        long before = revocationRows();
        Jwt expired = Jwt.withTokenValue("old").header("alg", "RS256").subject(current.sub())
                .claim("jti", UUID.randomUUID().toString()).claim("sid", current.sid())
                .issuedAt(Instant.now().minusSeconds(3_600)).expiresAt(Instant.now().minusSeconds(1_800)).build();

        var result = post(expired, "USER", "{ __typename }").expectBody(String.class).returnResult();

        assertThat(result.getStatus().value()).isEqualTo(401);
        assertThat(result.getResponseBody()).contains("ACTOR_NOT_AUTHENTICATED").doesNotContain("TOKEN_REVOKED");
        assertThat(result.getResponseHeaders().getFirst("WWW-Authenticate")).contains("expired");
        assertThat(revocationRows()).isEqualTo(before);
    }

    @Test
    @Order(7)
    @DisplayName("removing a member refuses that member's very next request, in every organization they belong to")
    void removingAMemberRefusesTheNextRequest() throws Exception {
        String name = register("member");
        Session member = loginAndKnown(name);
        String organizationId = new org.bson.types.ObjectId().toHexString();
        template.save(com.pml.identity.domain.model.OrganizationMember.builder()
                .id(new org.bson.types.ObjectId().toHexString()).userId(member.sub())
                .organizationId(organizationId)
                .role(com.pml.identity.domain.valueobject.OrganizationRole.MANAGER)
                .createdAt(Instant.now()).build()).block();
        assertThat(refused(member)).isFalse();

        members.leave(member.sub(), organizationId).block();

        assertThat(refused(member)).as("the removed member's still-unexpired token").isTrue();
        long audited = template.count(org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("action").is("ACCESS_REVOKED")
                                .and("userId").is(member.sub())),
                com.pml.identity.domain.model.AuditLog.class).block();
        assertThat(audited).as("an automatic trigger writes no audit row of its own").isZero();
    }

    @Test
    @Order(8)
    @DisplayName("suspending an organization refuses every one of its members, and not a member of another organization")
    void suspendingAnOrganizationRefusesEveryMember() throws Exception {
        Session first = loginAndKnown(register("suspended-a"));
        Session second = loginAndKnown(register("suspended-b"));
        Session elsewhere = loginAndKnown(register("elsewhere"));
        String suspendedOrganization = new org.bson.types.ObjectId().toHexString();
        String otherOrganization = new org.bson.types.ObjectId().toHexString();
        template.save(com.pml.identity.domain.model.Organization.builder().id(suspendedOrganization)
                .name("Suspend Me").slug("suspend-me-" + suspendedOrganization).ownerId(first.sub())
                .status(com.pml.shared.constants.OrganizationStatus.ACTIVE).createdAt(Instant.now()).build()).block();
        for (Session person : List.of(first, second)) {
            template.save(com.pml.identity.domain.model.OrganizationMember.builder()
                    .id(new org.bson.types.ObjectId().toHexString()).userId(person.sub())
                    .organizationId(suspendedOrganization)
                    .role(com.pml.identity.domain.valueobject.OrganizationRole.MANAGER)
                    .createdAt(Instant.now()).build()).block();
        }
        template.save(com.pml.identity.domain.model.OrganizationMember.builder()
                .id(new org.bson.types.ObjectId().toHexString()).userId(elsewhere.sub())
                .organizationId(otherOrganization)
                .role(com.pml.identity.domain.valueobject.OrganizationRole.MANAGER)
                .createdAt(Instant.now()).build()).block();

        String response = graphql(adminToken(), "ADMIN",
                "mutation { suspendOrganization(id: \"" + suspendedOrganization
                        + "\", reason: \"under investigation for fraud\") { id status } }");

        assertThat(response).contains("SUSPENDED");
        assertThat(refused(first)).isTrue();
        assertThat(refused(second)).isTrue();
        assertThat(refused(elsewhere)).as("a member of another organization").isFalse();
    }

    @Test
    @Order(9)
    @DisplayName("an administrator cuts the person: every session and every token minted afterwards is refused, and the log shows it")
    void administratorCutsThePerson() throws Exception {
        String response = graphql(adminToken(), "ADMIN",
                "mutation { revokeUserAccess(userId: \"" + current.sub()
                        + "\", reason: \"account reported compromised by its owner\") { id type value } }");
        assertThat(response).contains("\"type\":\"USER\"");

        assertThat(refused(current)).isTrue();
        assertThat(refused(login())).as("a token minted after the revocation").isTrue();

        String log = graphql(adminToken(), "ADMIN",
                "{ activeRevocations(type: USER) { content { id trigger reason } pageInfo { totalElements } } }");
        assertThat(log).contains("USER:" + current.sub()).contains("ADMIN_REVOKE")
                .contains("account reported compromised by its owner");
    }
}
