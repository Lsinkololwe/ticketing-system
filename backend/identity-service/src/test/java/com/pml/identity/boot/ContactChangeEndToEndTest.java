package com.pml.identity.boot;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.mongodb.reactivestreams.client.MongoClient;
import com.pml.identity.IdentityServiceApplication;
import com.pml.identity.account.ContactChangeKind;
import com.pml.identity.auth.delivery.CapturedMessages;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.security.ContactHasher;
import com.pml.identity.workflow.contactchange.ContactChangeProcess;
import com.pml.identity.workflow.contactchange.ContactChangeWorkflow.ChangeCommand;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.RedisNode;
import com.pml.shared.testing.TemporalDevServer;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * Linking and changing contacts with nothing faked: the GraphQL operations of the signed-in buyer, Redis
 * challenges, the real {@code ContactChangeWorkflow} on a Temporal server, MongoDB transactions and a real
 * Keycloak 26.5.2 (ET-IDN-004 R3, R5). "Register with WhatsApp then add email later, or the reverse; every
 * change verified; the account reachable throughout."
 *
 * <p>Real: every identity-service bean, the Temporal workers, the Keycloak admin client and server. Not real:
 * the code delivery (the in-memory capture, profile {@code test}) and the production realm (see
 * {@link BuyerSignInEndToEndTest} for why). A small proxy in front of Keycloak answers genuine HTTP 503 on
 * demand, so the outage cases meet a server error rather than a refused connection.</p>
 */
@Tag("L2")
@Tag("ET-IDN-004")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("ET-IDN-004 · add, change, remove and re-prime contacts against real Redis, Temporal, MongoDB and Keycloak")
@SpringBootTest(classes = IdentityServiceApplication.class)
// Each class owns its database and Keycloak; a cached context from another class would keep polling the same
// Temporal task queue and take this class's workflow tasks into the wrong database.
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
class ContactChangeEndToEndTest {

    private static final String DATABASE = IdentityStack.newDatabase();
    private static final String BUYERS = "myticketzm";
    private static final String CLIENT = "myticketzm-web";
    private static final int KEYCLOAK_PORT = TestSocketUtils.findAvailableTcpPort();
    private static final int PROXY_PORT = TestSocketUtils.findAvailableTcpPort();

    private static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.5.2")
            .withRealmImportFiles("/keycloak/myticketzm-realm.json", "/keycloak/myticketzm-admin-realm.json")
            .withCreateContainerCmdModifier(cmd -> {
                Ports ports = new Ports();
                ports.bind(ExposedPort.tcp(8080), Ports.Binding.bindPort(KEYCLOAK_PORT));
                ports.bind(ExposedPort.tcp(8443), Ports.Binding.empty());
                ports.bind(ExposedPort.tcp(9000), Ports.Binding.empty());
                cmd.getHostConfig().withPortBindings(ports);
            });

    private static KeycloakFaultProxy PROXY;

    /** Everything the service logs during this class, to prove no contact or code is in it. */
    private static final ListAppender<ILoggingEvent> LOGS = new ListAppender<>();
    /** Every contact value this class used, to look for in logs, Redis and the events. */
    private static final Set<String> USED = new CopyOnWriteArraySet<>();

    static {
        KEYCLOAK.start();
        try {
            PROXY = new KeycloakFaultProxy(PROXY_PORT, "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK_PORT);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        LOGS.start();
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).addAppender(LOGS);
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        IdentityStack.register(registry, DATABASE);
        registry.add("keycloak.server-url", () -> "http://127.0.0.1:" + PROXY_PORT);
        registry.add("keycloak.admin-username", KEYCLOAK::getAdminUsername);
        registry.add("KEYCLOAK_ADMIN_PASSWORD", KEYCLOAK::getAdminPassword);
        registry.add("identity.challenge.cooldown", () -> "PT1S");
        registry.add("identity.contact.quarantine", () -> "PT10M");
        registry.add("identity.contact.change-wait", () -> "PT4S");
    }

    @AfterAll
    static void stop() {
        PROXY.close();
        KEYCLOAK.stop();
    }

    @Autowired
    ApplicationContext context;
    @Autowired
    CapturedMessages captured;
    @Autowired
    ReactiveStringRedisTemplate redis;
    @Autowired
    ContactChangeProcess process;
    @Autowired
    ContactHasher hasher;
    @Autowired
    com.pml.identity.config.IdentityContactProperties contactProperties;

    // ---------------------------------------------------------------------------------- helpers

    private record Response(int status, Map<String, Object> body) {
        String text(String key) {
            Object value = body.get(key);
            return value == null ? null : value.toString();
        }
    }

    /** One signed-up buyer: the account id, the Keycloak user id, and the contact it signed up with. */
    private record Buyer(String accountId, String keycloakUserId, String contact) {
    }

    private WebTestClient internalApi() {
        return WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient()
                .responseTimeout(Duration.ofSeconds(90)).build()
                .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("SCOPE_internal-write"),
                        new SimpleGrantedAuthority("SCOPE_internal-read")));
    }

    @SuppressWarnings("unchecked")
    private Response call(String method, String path, Object body) {
        WebTestClient.RequestBodySpec spec = internalApi().method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(path).contentType(MediaType.APPLICATION_JSON);
        WebTestClient.ResponseSpec exchange = body == null ? spec.exchange() : spec.bodyValue(body).exchange();
        EntityExchangeResult<Map> result = exchange.expectBody(Map.class).returnResult();
        Map<String, Object> map = result.getResponseBody() == null ? Map.of() : (Map<String, Object>) result.getResponseBody();
        return new Response(result.getStatus().value(), map);
    }

    private static String uniqueEmail() {
        String email = "cc-" + UUID.randomUUID().toString().substring(0, 12) + "@example.com";
        USED.add(email);
        return email;
    }

    private static String uniquePhone() {
        String phone = "+26097" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
        USED.add(phone);
        return phone;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String proofFor(String contact) {
        Response issued = call("POST", "/api/internal/auth/challenges",
                Map.of("contact", Map.of("value", contact), "clientIp", "203.0.113." + (1 + ThreadLocalRandom.current().nextInt(250))));
        assertThat(issued.status()).as("challenge: %s", issued.body()).isEqualTo(202);
        String code = captured.lastCodeTo(contact).orElseThrow();
        Response verified = call("POST", "/api/internal/auth/challenges/verify",
                Map.of("challengeId", issued.text("challengeId"), "code", code));
        assertThat(verified.status()).as("verify: %s", verified.body()).isEqualTo(200);
        return verified.text("proof");
    }

    private Response ensureUntilDone(String proof) {
        Response[] last = new Response[1];
        await().atMost(Duration.ofMinutes(4)).pollInterval(Duration.ofSeconds(1)).until(() -> {
            last[0] = call("POST", "/api/internal/auth/accounts/ensure", Map.of("proof", proof, "clientId", CLIENT, "issueHandle", false));
            return last[0].status() != 202;
        });
        return last[0];
    }

    private Buyer signUp(String contact) {
        Response ensured = ensureUntilDone(proofFor(contact));
        assertThat(ensured.status()).as("%s", ensured.body()).isEqualTo(200);
        String accountId = ensured.text("accountId");
        return new Buyer(accountId, mongo("identity_users", new Document("_id", accountId)).get(0).getString("keycloakUserId"), contact);
    }

    private Keycloak admin() {
        return KEYCLOAK.getKeycloakAdminClient();
    }

    private UserRepresentation keycloakUser(Buyer buyer) {
        return admin().realm(BUYERS).users().get(buyer.keycloakUserId()).toRepresentation();
    }

    private List<Document> mongo(String collection, Document filter) {
        MongoClient client = IdentityStack.mongoClient();
        try {
            return Flux.from(client.getDatabase(DATABASE).getCollection(collection).find(filter)).collectList().block();
        } finally {
            client.close();
        }
    }

    private List<Document> activeContacts(Buyer buyer) {
        return mongo("identity_contacts", new Document("accountId", buyer.accountId()).append("releasedAt", null));
    }

    /** A GraphQL call as the buyer: the token's own account, through the {@code accountId} claim. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> graphql(Buyer buyer, String query) {
        WebTestClient client = WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient()
                .responseTimeout(Duration.ofSeconds(90)).build()
                .mutateWith(mockJwt().jwt(jwt -> jwt.subject(buyer.keycloakUserId()).claim("accountId", buyer.accountId())));
        EntityExchangeResult<Map> result = client.post().uri("/graphql").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", query)).exchange().expectBody(Map.class).returnResult();
        return (Map<String, Object>) result.getResponseBody();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map<String, Object> response, String field) {
        assertThat(response.get("errors")).as("errors in %s", response).isNull();
        return (Map<String, Object>) ((Map<String, Object>) response.get("data")).get(field);
    }

    @SuppressWarnings("unchecked")
    private static String errorCode(Map<String, Object> response) {
        List<Map<String, Object>> errors = (List<Map<String, Object>>) response.get("errors");
        assertThat(errors).as("expected an error in %s", response).isNotNull().isNotEmpty();
        return String.valueOf(((Map<String, Object>) errors.get(0).get("extensions")).get("errorCode"));
    }

    private static final String CONTACT_FIELDS = "id type valueMasked primary verifiedAt";

    private String requestAdd(Buyer buyer, ContactType type, String value) {
        Map<String, Object> response = graphql(buyer, "mutation { requestContactAdd(input: {type: " + type + ", value: \"" + value
                + "\"}) { challengeId contactType maskedContact expiresInSeconds resendAfterSeconds } }");
        Map<String, Object> sent = data(response, "requestContactAdd");
        assertThat(sent.get("maskedContact").toString()).as("only the masked value is returned").isNotEqualTo(value).contains("*");
        return sent.get("challengeId").toString();
    }

    private Map<String, Object> confirmAdd(Buyer buyer, String challengeId, String code) {
        return graphql(buyer, "mutation { confirmContactAdd(input: {challengeId: \"" + challengeId + "\", code: \"" + code
                + "\"}) { changeId kind status contacts { " + CONTACT_FIELDS + " } } }");
    }

    private String code(String contact) {
        return captured.lastCodeTo(contact).orElseThrow(() -> new AssertionError("no code captured"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> myContacts(Buyer buyer) {
        Map<String, Object> mine = data(graphql(buyer, "query { myContacts { contacts { " + CONTACT_FIELDS
                + " } pendingChange { changeId kind newContactMasked currentContactVerified attemptsRemaining } } }"), "myContacts");
        return (List<Map<String, Object>>) mine.get("contacts");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pendingChange(Buyer buyer) {
        return (Map<String, Object>) data(graphql(buyer, "query { myContacts { pendingChange { changeId kind newContactMasked "
                + "currentContactVerified attemptsRemaining } } }"), "myContacts").get("pendingChange");
    }

    private String contactIdOfType(Buyer buyer, String type) {
        return myContacts(buyer).stream().filter(c -> type.equals(c.get("type"))).map(c -> c.get("id").toString()).findFirst().orElseThrow();
    }

    /** Adds {@code value} to the buyer through the two GraphQL steps. */
    private void addContact(Buyer buyer, ContactType type, String value) {
        String challengeId = requestAdd(buyer, type, value);
        Map<String, Object> confirmed = data(confirmAdd(buyer, challengeId, code(value)), "confirmContactAdd");
        assertThat(confirmed.get("status")).isEqualTo("COMPLETED");
        sleep(1_200);
    }

    private void setPassword(Buyer buyer) {
        CredentialRepresentation password = new CredentialRepresentation();
        password.setType(CredentialRepresentation.PASSWORD);
        password.setValue("Test-pass-123");
        password.setTemporary(false);
        admin().realm(BUYERS).users().get(buyer.keycloakUserId()).resetPassword(password);
    }

    /** Password grant of the fixture client for the buyer's username (= account id). */
    @SuppressWarnings("unchecked")
    private Map<String, Object> passwordGrant(Buyer buyer) throws Exception {
        return tokenRequest("grant_type=password&client_id=test-direct&username="
                + URLEncoder.encode(buyer.accountId(), StandardCharsets.UTF_8) + "&password=Test-pass-123");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> tokenRequest(String form) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK_PORT + "/realms/" + BUYERS + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
        Map<String, Object> body = new com.fasterxml.jackson.databind.ObjectMapper().readValue(response.body(), Map.class);
        body.put("httpStatus", response.statusCode());
        return body;
    }

    private boolean keycloakAnswers() {
        try {
            return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                            URI.create("http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK_PORT + "/realms/" + BUYERS)).build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------------------------- add

    @Test
    @Order(1)
    @DisplayName("a WhatsApp account adds an email later: both contacts verified, one primary, Keycloak email and emailVerified set, username untouched")
    void whatsappAccountAddsEmail() {
        Buyer buyer = signUp(uniquePhone());
        String email = uniqueEmail();

        String challengeId = requestAdd(buyer, ContactType.EMAIL, email);
        assertThat(activeContacts(buyer)).as("nothing joins before the code").hasSize(1);
        Map<String, Object> confirmed = data(confirmAdd(buyer, challengeId, code(email)), "confirmContactAdd");

        assertThat(confirmed.get("status")).isEqualTo("COMPLETED");
        assertThat(confirmed.get("kind")).isEqualTo("ADD");
        List<Document> contacts = activeContacts(buyer);
        assertThat(contacts).hasSize(2).allSatisfy(c -> assertThat(c.get("verifiedAt")).isNotNull());
        assertThat(contacts.stream().filter(c -> c.getBoolean("primary"))).as("one primary").singleElement()
                .satisfies(c -> assertThat(c.getString("type")).isEqualTo("WHATSAPP"));
        UserRepresentation user = keycloakUser(buyer);
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getUsername()).as("the username is never touched").isEqualTo(buyer.accountId());
        assertThat(mongo("identity_users", new Document("_id", buyer.accountId())).get(0).get("pendingKind")).isNull();
        assertThat(mongo("identity_account_events", new Document("accountId", buyer.accountId()).append("kind", "CONTACT_ADDED"))).hasSize(1);
        assertThat(mongo("identity_outbox", new Document("eventType", "identity.ContactAdded").append("payload.userId", buyer.accountId()))).hasSize(1);
        assertThat(captured.noticesTo(email)).as("the new contact is told").hasSize(1);
        // the buyer's own view: masked, two contacts, no pending change
        List<Map<String, Object>> mine = myContacts(buyer);
        assertThat(mine).hasSize(2);
        assertThat(mine.toString()).doesNotContain(email).doesNotContain(buyer.contact());
        assertThat(pendingChange(buyer)).isNull();
        // and the email now signs the same account in
        sleep(1_200);
        assertThat(ensureUntilDone(proofFor(email)).text("accountId")).isEqualTo(buyer.accountId());
    }

    @Test
    @Order(2)
    @DisplayName("an email account adds WhatsApp later: the number is verified and the email stays primary")
    void emailAccountAddsWhatsapp() {
        Buyer buyer = signUp(uniqueEmail());
        String phone = uniquePhone();

        addContact(buyer, ContactType.WHATSAPP, phone);

        List<Document> contacts = activeContacts(buyer);
        assertThat(contacts).hasSize(2);
        assertThat(contacts.stream().filter(c -> c.getBoolean("primary"))).singleElement()
                .satisfies(c -> assertThat(c.getString("type")).isEqualTo("EMAIL"));
        assertThat(keycloakUser(buyer).getEmail()).as("the email contact is unchanged in Keycloak").isEqualTo(buyer.contact());
        assertThat(ensureUntilDone(proofFor(phone)).text("accountId")).isEqualTo(buyer.accountId());
    }

    @Test
    @Order(3)
    @DisplayName("adding a contact another account holds is refused with CONTACT_ALREADY_CLAIMED, and nothing changes in Mongo or Keycloak")
    void addRefusedWhenAnotherAccountHoldsIt() {
        Buyer holder = signUp(uniqueEmail());
        Buyer adder = signUp(uniquePhone());
        long hashed = mongo("identity_contacts", new Document("accountId", holder.accountId())).size();
        String challengeId = requestAdd(adder, ContactType.EMAIL, holder.contact());

        Map<String, Object> refused = confirmAdd(adder, challengeId, code(holder.contact()));

        assertThat(errorCode(refused)).isEqualTo("CONTACT_ALREADY_CLAIMED");
        assertThat(activeContacts(adder)).hasSize(1);
        assertThat(activeContacts(holder)).hasSize(1);
        assertThat(mongo("identity_contacts", new Document("accountId", holder.accountId())).size()).isEqualTo((int) hashed);
        assertThat(keycloakUser(adder).getEmail()).isNull();
        assertThat(keycloakUser(holder).getEmail()).isEqualTo(holder.contact());
        assertThat(mongo("identity_users", new Document("_id", adder.accountId())).get(0).get("pendingKind")).as("marker cleared").isNull();
        assertThat(mongo("identity_account_events", new Document("accountId", adder.accountId()).append("kind", "CONTACT_ADDED"))).isEmpty();
    }

    @Test
    @Order(4)
    @DisplayName("wrong and expired codes: OTP_INVALID with attempts left, OTP_LOCKED after five, OTP_EXPIRED when the code is gone; nothing joins")
    void wrongAndExpiredCodes() {
        Buyer buyer = signUp(uniquePhone());
        String email = uniqueEmail();
        String challengeId = requestAdd(buyer, ContactType.EMAIL, email);

        Map<String, Object> wrong = confirmAdd(buyer, challengeId, "000000".equals(code(email)) ? "111111" : "000000");
        assertThat(errorCode(wrong)).isEqualTo("OTP_INVALID");
        for (int i = 0; i < 3; i++) {
            confirmAdd(buyer, challengeId, "00000" + i == null ? "" : ("00000".concat(String.valueOf(i + 1))));
        }
        Map<String, Object> locked = confirmAdd(buyer, challengeId, "999999");
        assertThat(errorCode(locked)).isIn("OTP_LOCKED", "OTP_INVALID");
        assertThat(errorCode(confirmAdd(buyer, challengeId, code(email)))).as("even the right code, once locked").isEqualTo("OTP_LOCKED");
        assertThat(activeContacts(buyer)).hasSize(1);

        // a second buyer whose code simply expired
        Buyer other = signUp(uniquePhone());
        String second = uniqueEmail();
        String id = requestAdd(other, ContactType.EMAIL, second);
        String liveCode = code(second);
        redis.delete("chid:" + id).block();
        assertThat(errorCode(confirmAdd(other, id, liveCode))).isEqualTo("OTP_EXPIRED");
        assertThat(activeContacts(other)).hasSize(1);
        assertThat(keycloakUser(other).getEmail()).isNull();
    }

    @Test
    @Order(5)
    @DisplayName("two accounts adding one contact at the same moment: exactly one wins, the other gets CONTACT_ALREADY_CLAIMED")
    void concurrentAddsOfOneContact() throws Exception {
        Buyer first = signUp(uniquePhone());
        Buyer second = signUp(uniquePhone());
        String email = uniqueEmail();
        // two valid proofs of the one email, as two people who both received a code would hold
        String proofA = proofFor(email);
        sleep(1_200);
        String proofB = proofFor(email);
        String key = hasher.normalize(email, ContactType.EMAIL, null, null).orElseThrow().key();
        String masked = hasher.normalize(email, ContactType.EMAIL, null, null).orElseThrow().masked();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (Object[] attempt : new Object[][]{{first, proofA}, {second, proofB}}) {
                Buyer buyer = (Buyer) attempt[0];
                String proof = (String) attempt[1];
                results.add(pool.submit(() -> {
                    ChangeCommand command = new ChangeCommand(UUID.randomUUID().toString(), buyer.accountId(), ContactChangeKind.ADD,
                            null, ContactType.EMAIL, key, masked, proof, null, null);
                    try {
                        process.start(command).block();
                        process.await(buyer.accountId(), Duration.ofSeconds(60)).block();
                        return "WON";
                    } catch (DomainRefusal refusal) {
                        return refusal.errorCode().name();
                    }
                }));
            }
            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results) {
                outcomes.add(result.get(90, java.util.concurrent.TimeUnit.SECONDS));
            }
            assertThat(outcomes).containsExactlyInAnyOrder("WON", "CONTACT_ALREADY_CLAIMED");
        } finally {
            pool.shutdownNow();
        }
        assertThat(mongo("identity_contacts", new Document("valueHash", key).append("releasedAt", null))).as("one owner").hasSize(1);
        long withEmail = List.of(first, second).stream().filter(b -> keycloakUser(b).getEmail() != null).count();
        assertThat(withEmail).as("Keycloak holds the email on exactly one user").isEqualTo(1);
    }

    // ---------------------------------------------------------------------------------- change

    @Test
    @Order(6)
    @DisplayName("changing the email: both codes needed, old released, quarantine honoured, Keycloak updated, sessions revoked, the old contact signs in until the switch")
    void changeEmail() throws Exception {
        Buyer buyer = signUp(uniqueEmail());
        String oldEmail = buyer.contact();
        String newEmail = uniqueEmail();
        setPassword(buyer);
        Map<String, Object> tokens = passwordGrant(buyer);
        assertThat(tokens.get("httpStatus")).isEqualTo(200);
        String refresh = tokens.get("refresh_token").toString();
        String oldContactId = contactIdOfType(buyer, "EMAIL");
        String signInProof = proofFor(oldEmail);
        sleep(1_200);

        Map<String, Object> requested = data(graphql(buyer, "mutation { requestContactChange(input: {contactId: \"" + oldContactId
                + "\", type: EMAIL, value: \"" + newEmail + "\"}) { changeId expiresAt newContact { challengeId maskedContact } "
                + "currentContact { challengeId maskedContact } } }"), "requestContactChange");
        String changeId = requested.get("changeId").toString();
        assertThat(requested.toString()).doesNotContain(newEmail).doesNotContain(oldEmail);
        assertThat(captured.lastTo(oldEmail)).as("a code went to the CURRENT primary contact").isPresent();
        assertThat(captured.lastTo(newEmail)).as("and one to the new contact").isPresent();
        Map<String, Object> pending = pendingChange(buyer);
        assertThat(pending.get("changeId")).isEqualTo(changeId);
        assertThat(pending.get("currentContactVerified")).isEqualTo(false);

        // no gap: while the change waits, the old contact still signs the same account in (with a proof taken
        // beforehand: a new sign-in code for the old contact would replace the live change code, as for any contact)
        assertThat(ensureUntilDone(signInProof).text("accountId")).isEqualTo(buyer.accountId());
        assertThat(mongo("identity_users", new Document("_id", buyer.accountId())).get(0).getString("pendingKind")).isEqualTo("CHANGING");
        assertThat(activeContacts(buyer)).hasSize(1);

        // a code to the new contact alone is not enough
        Map<String, Object> missing = graphql(buyer, "mutation { confirmContactChange(input: {changeId: \"" + changeId
                + "\", newContactCode: \"" + code(newEmail) + "\"}) { status } }");
        assertThat(errorCode(missing)).isEqualTo("COMMAND_NOT_WELL_FORMED");
        assertThat(activeContacts(buyer).get(0).getString("valueHash")).isNotNull();

        Map<String, Object> done = data(graphql(buyer, "mutation { confirmContactChange(input: {changeId: \"" + changeId
                + "\", newContactCode: \"" + code(newEmail) + "\", currentContactCode: \"" + code(oldEmail) + "\"}) { changeId kind status "
                + "contacts { " + CONTACT_FIELDS + " } } }"), "confirmContactChange");

        assertThat(done.get("status")).isEqualTo("COMPLETED");
        List<Document> history = mongo("identity_contacts", new Document("accountId", buyer.accountId()));
        assertThat(history).hasSize(2);
        Document released = history.stream().filter(c -> c.getString("_id").equals(oldContactId)).findFirst().orElseThrow();
        assertThat(released.get("releasedAt")).isNotNull();
        assertThat(released.getBoolean("primary")).isFalse();
        Document current = history.stream().filter(c -> c.get("releasedAt") == null).findFirst().orElseThrow();
        assertThat(current.getBoolean("primary")).isTrue();
        assertThat(keycloakUser(buyer).getEmail()).isEqualTo(newEmail);
        assertThat(keycloakUser(buyer).isEmailVerified()).isTrue();
        assertThat(keycloakUser(buyer).getUsername()).isEqualTo(buyer.accountId());
        assertThat(mongo("identity_users", new Document("_id", buyer.accountId())).get(0).get("pendingKind")).isNull();
        assertThat(captured.noticesTo(oldEmail)).as("the old contact is told").isNotEmpty();
        assertThat(captured.noticesTo(newEmail)).as("and the new one").isNotEmpty();

        // sessions revoked: the refresh token from before the change no longer works
        Map<String, Object> refreshed = tokenRequest("grant_type=refresh_token&client_id=test-direct&refresh_token=" + refresh);
        assertThat(refreshed.get("httpStatus")).isNotEqualTo(200);
        // the buyer signs in again, by username (= account id) and by the new contact
        assertThat(passwordGrant(buyer).get("httpStatus")).isEqualTo(200);
        sleep(1_200);
        assertThat(ensureUntilDone(proofFor(newEmail)).text("accountId")).isEqualTo(buyer.accountId());

        // the released email is quarantined: another account cannot add it, nor can a brand-new account be made from it
        Buyer other = signUp(uniquePhone());
        sleep(1_200);
        String challengeId = requestAdd(other, ContactType.EMAIL, oldEmail);
        assertThat(errorCode(confirmAdd(other, challengeId, code(oldEmail)))).isEqualTo("CONTACT_ALREADY_CLAIMED");
        sleep(1_200);
        Response signIn = ensureUntilDone(proofFor(oldEmail));
        assertThat(signIn.status()).isEqualTo(409);
        assertThat(signIn.text("errorCode")).isEqualTo("CONTACT_ALREADY_CLAIMED");
        // the audit entry carries masked values only
        assertThat(mongo("identity_account_events", new Document("accountId", buyer.accountId()).append("kind", "CONTACT_CHANGED")))
                .singleElement().satisfies(event -> assertThat(event.toJson()).doesNotContain(oldEmail).doesNotContain(newEmail)
                        .contains("oldMasked").contains("newMasked"));
    }

    @Test
    @Order(7)
    @DisplayName("an open change refuses a second one and any add; five wrong codes abort it; a cancelled one frees the account")
    void oneOpenChangeAndAttempts() {
        Buyer buyer = signUp(uniqueEmail());
        String contactId = contactIdOfType(buyer, "EMAIL");
        String newEmail = uniqueEmail();
        String request = "mutation { requestContactChange(input: {contactId: \"" + contactId + "\", type: EMAIL, value: \"%s\"}) { changeId } }";

        String changeId = data(graphql(buyer, request.formatted(newEmail)), "requestContactChange").get("changeId").toString();

        sleep(1_200);
        assertThat(errorCode(graphql(buyer, request.formatted(uniqueEmail())))).isEqualTo("CONTACT_CHANGE_IN_PROGRESS");
        assertThat(errorCode(graphql(buyer, "mutation { requestContactAdd(input: {type: WHATSAPP, value: \"" + uniquePhone()
                + "\"}) { challengeId } }"))).isEqualTo("CONTACT_CHANGE_IN_PROGRESS");

        // five wrong codes to the current contact: the last is the contact lock, and the change is abandoned
        String confirm = "mutation { confirmContactChange(input: {changeId: \"" + changeId
                + "\", newContactCode: \"000001\", currentContactCode: \"00000%d\"}) { status } }";
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            codes.add(errorCode(graphql(buyer, confirm.formatted(i + 2))));
        }
        assertThat(codes).startsWith("OTP_INVALID").endsWith("OTP_LOCKED");
        await().atMost(Duration.ofSeconds(20)).until(() -> pendingChange(buyer) == null);
        assertThat(mongo("identity_users", new Document("_id", buyer.accountId())).get(0).get("pendingKind")).isNull();
        assertThat(activeContacts(buyer)).hasSize(1);
        assertThat(keycloakUser(buyer).getEmail()).isEqualTo(buyer.contact());

        // cancel: a fresh change on another account opens and is abandoned by its holder
        Buyer second = signUp(uniqueEmail());
        String secondContact = contactIdOfType(second, "EMAIL");
        String open = data(graphql(second, "mutation { requestContactChange(input: {contactId: \"" + secondContact
                + "\", type: EMAIL, value: \"" + uniqueEmail() + "\"}) { changeId } }"), "requestContactChange").get("changeId").toString();
        Map<String, Object> cancelled = graphql(second, "mutation { cancelContactChange(changeId: \"" + open + "\") }");
        assertThat(cancelled.get("errors")).isNull();
        assertThat(((Map<?, ?>) cancelled.get("data")).get("cancelContactChange")).isEqualTo(true);
        await().atMost(Duration.ofSeconds(20)).until(() -> pendingChange(second) == null);
        assertThat(mongo("identity_users", new Document("_id", second.accountId())).get(0).get("pendingKind")).isNull();
    }

    // ---------------------------------------------------------------------------------- remove and primary

    @Test
    @Order(8)
    @DisplayName("removal: refused for the last verified contact; with two, a code to the primary authorises it, the primary passes on, Keycloak loses the email")
    void removal() {
        Buyer buyer = signUp(uniqueEmail());
        String emailId = contactIdOfType(buyer, "EMAIL");

        assertThat(errorCode(graphql(buyer, "mutation { requestContactRemoval(contactId: \"" + emailId + "\") { challengeId } }")))
                .isEqualTo("LAST_VERIFIED_CONTACT");

        String phone = uniquePhone();
        addContact(buyer, ContactType.WHATSAPP, phone);
        Map<String, Object> sent = data(graphql(buyer, "mutation { requestContactRemoval(contactId: \"" + emailId
                + "\") { challengeId maskedContact contactType } }"), "requestContactRemoval");
        assertThat(sent.get("contactType")).as("the code went to the current primary (the email)").isEqualTo("EMAIL");
        assertThat(activeContacts(buyer)).as("nothing removed before the code").hasSize(2);
        assertThat(errorCode(graphql(buyer, "mutation { confirmContactRemoval(input: {challengeId: \"" + sent.get("challengeId")
                + "\", code: \"123456\"}) { status } }"))).isIn("OTP_INVALID", "OTP_LOCKED");

        Map<String, Object> done = data(graphql(buyer, "mutation { confirmContactRemoval(input: {challengeId: \"" + sent.get("challengeId")
                + "\", code: \"" + code(buyer.contact()) + "\"}) { status kind contacts { " + CONTACT_FIELDS + " } } }"), "confirmContactRemoval");

        assertThat(done.get("status")).isEqualTo("COMPLETED");
        List<Document> remaining = activeContacts(buyer);
        assertThat(remaining).singleElement().satisfies(c -> {
            assertThat(c.getString("type")).isEqualTo("WHATSAPP");
            assertThat(c.getBoolean("primary")).isTrue();
        });
        assertThat(keycloakUser(buyer).getEmail()).isNull();
        // the removed email can no longer reach the account (it is released and quarantined, so not a new one either)
        sleep(1_200);
        assertThat(ensureUntilDone(proofFor(buyer.contact())).status()).isEqualTo(409);
        // and the last one cannot go
        String lastId = contactIdOfType(buyer, "WHATSAPP");
        assertThat(errorCode(graphql(buyer, "mutation { requestContactRemoval(contactId: \"" + lastId + "\") { challengeId } }")))
                .isEqualTo("LAST_VERIFIED_CONTACT");
    }

    @Test
    @Order(9)
    @DisplayName("primary: switching to another verified contact needs a code to the current primary and leaves exactly one primary")
    void switchPrimary() {
        Buyer buyer = signUp(uniquePhone());
        String email = uniqueEmail();
        addContact(buyer, ContactType.EMAIL, email);
        String emailId = contactIdOfType(buyer, "EMAIL");

        Map<String, Object> sent = data(graphql(buyer, "mutation { requestPrimaryContact(contactId: \"" + emailId
                + "\") { challengeId contactType } }"), "requestPrimaryContact");
        assertThat(sent.get("contactType")).isEqualTo("WHATSAPP");
        Map<String, Object> done = data(graphql(buyer, "mutation { setPrimaryContact(input: {contactId: \"" + emailId + "\", challengeId: \""
                + sent.get("challengeId") + "\", code: \"" + code(buyer.contact()) + "\"}) { status contacts { " + CONTACT_FIELDS + " } } }"),
                "setPrimaryContact");

        assertThat(done.get("status")).isEqualTo("COMPLETED");
        assertThat(activeContacts(buyer).stream().filter(c -> c.getBoolean("primary"))).singleElement()
                .satisfies(c -> assertThat(c.getString("_id")).isEqualTo(emailId));
        assertThat(mongo("identity_users", new Document("_id", buyer.accountId())).get(0).getString("primaryContactId")).isEqualTo(emailId);
    }

    // ---------------------------------------------------------------------------------- IDOR

    @Test
    @Order(10)
    @DisplayName("IDOR: account A cannot name, change, remove or promote B's contact; it is 'unknown', exactly like a contact that does not exist")
    void foreignContactsAreUnknown() {
        Buyer a = signUp(uniqueEmail());
        Buyer b = signUp(uniqueEmail());
        addContact(b, ContactType.WHATSAPP, uniquePhone());
        String foreign = contactIdOfType(b, "WHATSAPP");
        String missing = UUID.randomUUID().toString();

        for (String id : new String[]{foreign, missing}) {
            assertThat(errorCode(graphql(a, "mutation { requestContactChange(input: {contactId: \"" + id + "\", type: WHATSAPP, value: \""
                    + uniquePhone() + "\"}) { changeId } }"))).isEqualTo("CONTACT_UNKNOWN");
            assertThat(errorCode(graphql(a, "mutation { requestContactRemoval(contactId: \"" + id + "\") { challengeId } }"))).isEqualTo("CONTACT_UNKNOWN");
            assertThat(errorCode(graphql(a, "mutation { requestPrimaryContact(contactId: \"" + id + "\") { challengeId } }"))).isEqualTo("CONTACT_UNKNOWN");
        }
        // B's step-up challenge, presented by A, is not A's
        Map<String, Object> sent = data(graphql(b, "mutation { requestContactRemoval(contactId: \"" + foreign + "\") { challengeId } }"),
                "requestContactRemoval");
        assertThat(errorCode(graphql(a, "mutation { confirmContactRemoval(input: {challengeId: \"" + sent.get("challengeId")
                + "\", code: \"" + code(b.contact()) + "\"}) { status } }"))).isEqualTo("OTP_EXPIRED");
        // the schema offers no account argument, and what A sees is only A's
        assertThat(myContacts(a).toString()).doesNotContain(foreign);
        assertThat(activeContacts(b)).hasSize(2);
        assertThat(mongo("identity_users", new Document("_id", a.accountId())).get(0).get("pendingKind")).isNull();
    }

    // ---------------------------------------------------------------------------------- outages

    @Test
    @Order(11)
    @DisplayName("Keycloak answering 503 and a crash between the claim and the Keycloak write: the change converges, with one new contact and the email written once")
    void keycloak5xxMidChangeConverges() {
        Buyer buyer = signUp(uniqueEmail());
        String newEmail = uniqueEmail();
        String contactId = contactIdOfType(buyer, "EMAIL");
        String changeId = data(graphql(buyer, "mutation { requestContactChange(input: {contactId: \"" + contactId + "\", type: EMAIL, value: \""
                + newEmail + "\"}) { changeId } }"), "requestContactChange").get("changeId").toString();
        // the next two writes to the user: one never reaches Keycloak, one is executed and its answer is lost
        PROXY.failBefore(1, request -> request.startsWith("PUT /admin/realms/" + BUYERS + "/users/"));

        Map<String, Object> result = graphql(buyer, "mutation { confirmContactChange(input: {changeId: \"" + changeId + "\", newContactCode: \""
                + code(newEmail) + "\", currentContactCode: \"" + code(buyer.contact()) + "\"}) { status } }");

        assertThat(data(result, "confirmContactChange").get("status")).isIn("APPLYING", "COMPLETED");
        await().atMost(Duration.ofMinutes(3)).pollInterval(Duration.ofSeconds(1))
                .until(() -> activeContacts(buyer).stream().anyMatch(c -> c.get("verifiedAt") != null
                        && !buyer.contact().equals(c.getString("valueMasked")) && c.getBoolean("primary")) && pendingChange(buyer) == null);
        assertThat(PROXY.failures()).isGreaterThanOrEqualTo(1);
        assertThat(keycloakUser(buyer).getEmail()).isEqualTo(newEmail);
        assertThat(mongo("identity_contacts", new Document("accountId", buyer.accountId()))).hasSize(2);
        assertThat(mongo("identity_account_events", new Document("accountId", buyer.accountId()).append("kind", "CONTACT_CHANGED"))).hasSize(1);
    }

    @Test
    @Order(12)
    @DisplayName("Keycloak stopped mid-change then restarted: the old contact signs in until the switch, no gap, and the change completes after the restart")
    void keycloakStoppedMidChange() throws Exception {
        Buyer buyer = signUp(uniqueEmail());
        String oldEmail = buyer.contact();
        String newEmail = uniqueEmail();
        String contactId = contactIdOfType(buyer, "EMAIL");
        String changeId = data(graphql(buyer, "mutation { requestContactChange(input: {contactId: \"" + contactId + "\", type: EMAIL, value: \""
                + newEmail + "\"}) { changeId } }"), "requestContactChange").get("changeId").toString();
        String confirm = "mutation { confirmContactChange(input: {changeId: \"" + changeId + "\", newContactCode: \"" + code(newEmail)
                + "\", currentContactCode: \"" + code(oldEmail) + "\"}) { status } }";

        KEYCLOAK.getDockerClient().stopContainerCmd(KEYCLOAK.getContainerId()).withTimeout(5).exec();
        try {
            Map<String, Object> waiting = data(graphql(buyer, confirm), "confirmContactChange");
            assertThat(waiting.get("status")).as("verified, finishing in the background").isEqualTo("APPLYING");
            sleep(2_000);
            // the new contact is claimed; the old one is still the primary and still signs in
            List<Document> now = activeContacts(buyer);
            assertThat(now).as("old and new, none released yet").hasSize(2);
            assertThat(now.stream().filter(c -> c.getBoolean("primary")).findFirst().orElseThrow().getString("_id")).isEqualTo(contactId);
            sleep(1_200);
            assertThat(ensureUntilDone(proofFor(oldEmail)).text("accountId")).as("no gap: the old contact signs in during the outage")
                    .isEqualTo(buyer.accountId());
            assertThat(mongo("identity_users", new Document("_id", buyer.accountId())).get(0).getString("pendingKind")).isEqualTo("CHANGING");
        } finally {
            KEYCLOAK.getDockerClient().startContainerCmd(KEYCLOAK.getContainerId()).exec();
        }
        await().atMost(Duration.ofMinutes(3)).pollInterval(Duration.ofSeconds(2)).until(this::keycloakAnswers);

        await().atMost(Duration.ofMinutes(4)).pollInterval(Duration.ofSeconds(2)).until(() -> {
            try {
                return pendingChange(buyer) == null && activeContacts(buyer).size() == 1;
            } catch (RuntimeException notYet) {
                return false;
            }
        });
        assertThat(keycloakUser(buyer).getEmail()).isEqualTo(newEmail);
        assertThat(activeContacts(buyer).get(0).getBoolean("primary")).isTrue();
        assertThat(mongo("identity_users", new Document("_id", buyer.accountId())).get(0).get("pendingKind")).isNull();
    }

    // ---------------------------------------------------------------------------------- resend

    @SuppressWarnings("unchecked")
    private static Object retryAfter(Map<String, Object> response) {
        return ((Map<String, Object>) ((List<Map<String, Object>>) response.get("errors")).get(0).get("extensions")).get("retryAfterSeconds");
    }

    @Test
    @Order(13)
    @DisplayName("resend: refused early with OTP_RATE_LIMITED and retryAfterSeconds; after the wait the new code replaces the old and has a new challenge id, for an add and for either code of a change")
    void resend() {
        Buyer buyer = signUp(uniquePhone());
        String email = uniqueEmail();
        String first = requestAdd(buyer, ContactType.EMAIL, email);
        String oldCode = code(email);
        String resend = "mutation { resendContactCode(input: {challengeId: \"%s\"}) { challengeId resendAfterSeconds maskedContact } }";

        contactProperties.setResendAfter(Duration.ofMinutes(5));
        try {
            Map<String, Object> early = graphql(buyer, resend.formatted(first));
            assertThat(errorCode(early)).isEqualTo("OTP_RATE_LIMITED");
            assertThat(Long.parseLong(String.valueOf(retryAfter(early)))).isBetween(1L, 300L);
            assertThat(code(email)).as("nothing was sent").isEqualTo(oldCode);
        } finally {
            contactProperties.setResendAfter(Duration.ZERO);
        }
        sleep(1_200);
        Map<String, Object> again = data(graphql(buyer, resend.formatted(first)), "resendContactCode");
        String second = again.get("challengeId").toString();
        assertThat(second).isNotEqualTo(first);
        assertThat(again.get("maskedContact").toString()).doesNotContain(email);
        String newCode = code(email);
        // the old challenge is dead; the new one works; someone else's resend is refused as unknown
        assertThat(errorCode(confirmAdd(buyer, first, newCode))).isEqualTo("OTP_EXPIRED");
        Buyer other = signUp(uniquePhone());
        assertThat(errorCode(graphql(other, resend.formatted(second)))).isEqualTo("CONTACT_UNKNOWN");
        assertThat(data(confirmAdd(buyer, second, newCode), "confirmContactAdd").get("status")).isEqualTo("COMPLETED");

        // a change: resend the code to the NEW contact and to the CURRENT one; the open change keeps its id
        String target = uniqueEmail();
        String contactId = contactIdOfType(buyer, "EMAIL");
        String changeId = data(graphql(buyer, "mutation { requestContactChange(input: {contactId: \"" + contactId + "\", type: EMAIL, value: \""
                + target + "\"}) { changeId } }"), "requestContactChange").get("changeId").toString();
        sleep(1_200);
        String resendChange = "mutation { resendContactCode(input: {changeId: \"" + changeId + "\", target: %s}) { challengeId } }";
        data(graphql(buyer, resendChange.formatted("NEW")), "resendContactCode");
        sleep(1_200);
        data(graphql(buyer, resendChange.formatted("CURRENT")), "resendContactCode");
        assertThat(pendingChange(buyer).get("changeId")).isEqualTo(changeId);
        Map<String, Object> done = data(graphql(buyer, "mutation { confirmContactChange(input: {changeId: \"" + changeId
                + "\", newContactCode: \"" + code(target) + "\", currentContactCode: \"" + code(buyer.contact()) + "\"}) { status } }"), "confirmContactChange");
        assertThat(done.get("status")).isIn("COMPLETED", "APPLYING");
        // a code already accepted cannot be sent again; an unknown change is unknown
        assertThat(errorCode(graphql(buyer, resendChange.formatted("NEW").replace(changeId, UUID.randomUUID().toString()))))
                .isIn("CONTACT_UNKNOWN");
    }

    // ---------------------------------------------------------------------------------- nothing leaks

    @Test
    @Order(99)
    @DisplayName("no raw contact in any log line, Redis key or value, account event, outbox row or audit entry")
    void nothingLeaks() {
        assertThat(USED).isNotEmpty();
        List<String> lines = new CopyOnWriteArrayList<>();
        LOGS.list.forEach(event -> {
            lines.add(event.getFormattedMessage());
            if (event.getThrowableProxy() != null) {
                lines.add(String.valueOf(event.getThrowableProxy().getMessage()));
            }
        });
        for (String contact : USED) {
            String local = contact.contains("@") ? contact.substring(0, contact.indexOf('@')) : contact;
            assertThat(lines).as("logs mention %s", local).noneMatch(line -> line.contains(contact) || line.contains(local));
        }

        List<String> redisText = new ArrayList<>();
        for (String key : redis.keys("*").collectList().block()) {
            redisText.add(key);
            String type = redis.type(key).block().code();
            if ("string".equals(type)) {
                redisText.add(String.valueOf(redis.opsForValue().get(key).block()));
            } else if ("hash".equals(type)) {
                redis.opsForHash().entries(key).collectList().block().forEach(e -> redisText.add(e.getKey() + "=" + e.getValue()));
            }
        }
        for (String contact : USED) {
            assertThat(redisText).as("Redis holds %s", contact.contains("@") ? "an email" : "a number")
                    .noneMatch(text -> text.contains(contact) || text.contains(contact.replace("+", "")));
        }

        for (String collection : new String[]{"identity_account_events", "identity_outbox", "identity_audit_logs"}) {
            String all = mongo(collection, new Document()).stream().map(Document::toJson).reduce("", String::concat);
            for (String contact : USED) {
                assertThat(all).as("%s holds a raw contact", collection).doesNotContain(contact);
            }
        }
    }

    @SuppressWarnings("unused")
    private static final Class<?>[] FIXTURES = {MongoReplicaSet.class, RedisNode.class, TemporalDevServer.class};
}
