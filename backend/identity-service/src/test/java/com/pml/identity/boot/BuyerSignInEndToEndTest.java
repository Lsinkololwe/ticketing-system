package com.pml.identity.boot;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.mongodb.reactivestreams.client.MongoClient;
import com.pml.identity.IdentityServiceApplication;
import com.pml.identity.auth.delivery.CapturedMessages;
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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * The buyer sign-in path with nothing faked: the internal REST API, Redis, the real
 * {@code AccountEnsureWorkflow} on a Temporal server, MongoDB transactions and a real Keycloak 26.5.2
 * (ET-IDN-001, ET-IDN-004). Request a code, verify it, ensure the account, redeem the login handle.
 *
 * <h2>What is real and what is not</h2>
 * Real: every identity-service bean, the Temporal workers, the Keycloak admin client, and the Keycloak
 * server. The code delivery is the in-memory capture (profile {@code test}), which is the only thing
 * that would otherwise leave the machine.
 *
 * <p>Not real: the production realm. {@code docker-resources/keycloak/myticketzm-realm.json} imports
 * only with the {@code contact-otp-authenticator} plugin installed (its browser flow names it), and the
 * plugin jar exists only after {@code mvn package} of {@code keycloak-extensions}, a later phase than
 * the tests of this module. The realm fixture of {@code KeycloakServiceContainerTest} is used instead,
 * with the same {@code accountId} mapper as production. The plugin's side (screen and handle hand-off,
 * the claims of a real token) is covered by {@code ContactOtpKeycloakIT} in keycloak-extensions against
 * the same Keycloak version; this test covers everything up to and including the handle.</p>
 */
@Tag("L2")
@Tag("ET-IDN-004")
@Tag("ET-IDN-001")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("ET-IDN-004 · request, verify, ensure, handle and redeem against real Redis, Temporal, MongoDB and Keycloak")
@SpringBootTest(classes = IdentityServiceApplication.class)
// Each class owns its database and Keycloak; a cached context from another class would keep polling the same
// Temporal task queue and take this class's workflow tasks into the wrong database.
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
class BuyerSignInEndToEndTest {

    private static final String DATABASE = IdentityStack.newDatabase();
    private static final String BUYERS = "myticketzm";
    private static final String CLIENT = "myticketzm-web";
    private static final int KEYCLOAK_PORT = TestSocketUtils.findAvailableTcpPort();

    /** The host port is fixed so that stopping and starting the container (the outage test) keeps the URL. */
    private static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.5.2")
            .withRealmImportFiles("/keycloak/myticketzm-realm.json", "/keycloak/myticketzm-admin-realm.json")
            .withCreateContainerCmdModifier(cmd -> {
                Ports ports = new Ports();
                ports.bind(ExposedPort.tcp(8080), Ports.Binding.bindPort(KEYCLOAK_PORT));
                ports.bind(ExposedPort.tcp(8443), Ports.Binding.empty());
                ports.bind(ExposedPort.tcp(9000), Ports.Binding.empty());
                cmd.getHostConfig().withPortBindings(ports);
            });

    private static final int PROXY_PORT = TestSocketUtils.findAvailableTcpPort();
    /** Answers genuine HTTP 503 on demand in front of the real Keycloak (see {@link KeycloakFaultProxy}). */
    private static KeycloakFaultProxy PROXY;

    static {
        KEYCLOAK.start();
        try {
            PROXY = new KeycloakFaultProxy(PROXY_PORT, "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK_PORT);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        IdentityStack.register(registry, DATABASE);
        registry.add("keycloak.server-url", () -> "http://127.0.0.1:" + PROXY_PORT);
        registry.add("keycloak.admin-username", KEYCLOAK::getAdminUsername);
        registry.add("KEYCLOAK_ADMIN_PASSWORD", KEYCLOAK::getAdminPassword);
        // a second code for the same contact is allowed after a second, so one test can hold several proofs
        registry.add("identity.challenge.cooldown", () -> "PT1S");
    }

    @AfterAll
    static void stopKeycloak() {
        PROXY.close();
        KEYCLOAK.stop();
    }

    @Autowired
    ApplicationContext context;
    @Autowired
    CapturedMessages captured;
    @Autowired
    ReactiveStringRedisTemplate redis;

    // ---------------------------------------------------------------------------------- helpers

    private record Response(int status, Map<String, Object> body) {
        String text(String key) {
            Object value = body.get(key);
            return value == null ? null : value.toString();
        }
    }

    private WebTestClient api() {
        return WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient()
                .responseTimeout(Duration.ofSeconds(90)).build()
                .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("SCOPE_internal-write"),
                        new SimpleGrantedAuthority("SCOPE_internal-read")));
    }

    @SuppressWarnings("unchecked")
    private Response call(String method, String path, Object body) {
        WebTestClient.RequestBodySpec spec = api().method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(path).contentType(MediaType.APPLICATION_JSON);
        WebTestClient.ResponseSpec exchange = body == null ? spec.exchange() : spec.bodyValue(body).exchange();
        EntityExchangeResult<Map> result = exchange.expectBody(Map.class).returnResult();
        Map<String, Object> map = result.getResponseBody() == null ? Map.of() : (Map<String, Object>) result.getResponseBody();
        return new Response(result.getStatus().value(), map);
    }

    private static String randomIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "203.0.113." + (1 + r.nextInt(250)) + "";
    }

    private static String uniqueEmail() {
        return "e2e-" + UUID.randomUUID().toString().substring(0, 12) + "@example.com";
    }

    private static String uniquePhone() {
        return "+26097" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
    }

    /** Request a code for a normalised contact and verify it; returns the proof. */
    private String proofFor(String contact) {
        Response issued = call("POST", "/api/internal/auth/challenges",
                Map.of("contact", Map.of("value", contact), "clientIp", randomIp()));
        assertThat(issued.status()).as("challenge: %s", issued.body()).isEqualTo(202);
        String code = captured.lastCodeTo(contact).orElseThrow(() -> new AssertionError("no code captured"));
        Response verified = call("POST", "/api/internal/auth/challenges/verify",
                Map.of("challengeId", issued.text("challengeId"), "code", code));
        assertThat(verified.status()).as("verify: %s", verified.body()).isEqualTo(200);
        return verified.text("proof");
    }

    private Response ensureOnce(String proof, boolean issueHandle) {
        return call("POST", "/api/internal/auth/accounts/ensure",
                Map.of("proof", proof, "clientId", CLIENT, "issueHandle", issueHandle));
    }

    /** Ensure, repeating with the same proof while the workflow is still running (202), as the contract says. */
    private Response ensureUntilDone(String proof, boolean issueHandle, List<Integer> statuses) {
        Response[] last = new Response[1];
        await().atMost(Duration.ofMinutes(4)).pollInterval(Duration.ofSeconds(1)).until(() -> {
            last[0] = ensureOnce(proof, issueHandle);
            statuses.add(last[0].status());
            return last[0].status() != 202;
        });
        return last[0];
    }

    private Response ensureUntilDone(String proof, boolean issueHandle) {
        return ensureUntilDone(proof, issueHandle, new ArrayList<>());
    }

    private Keycloak admin() {
        return KEYCLOAK.getKeycloakAdminClient();
    }

    private List<UserRepresentation> keycloakUsers(String username) {
        return admin().realm(BUYERS).users().searchByUsername(username, true);
    }

    private List<Document> mongo(String collection, Document filter) {
        MongoClient client = IdentityStack.mongoClient();
        try {
            return Flux.from(client.getDatabase(DATABASE).getCollection(collection).find(filter)).collectList().block();
        } finally {
            client.close();
        }
    }

    private long accounts() {
        return mongo("identity_users", new Document()).size();
    }

    // ---------------------------------------------------------------------------------- the path

    @Test
    @Order(1)
    @DisplayName("a new email contact: code, verify, ensure to ACTIVE, a Keycloak user by account id, handle, single redeem")
    void newBuyerSignsInByEmail() throws Exception {
        String email = uniqueEmail();
        String proof = proofFor(email);

        Response ensured = ensureUntilDone(proof, true);

        assertThat(ensured.status()).as("%s", ensured.body()).isEqualTo(200);
        assertThat(ensured.text("status")).isEqualTo("ACTIVE");
        assertThat(ensured.body().get("isNew")).isEqualTo(true);
        String accountId = ensured.text("accountId");
        String handle = ensured.text("loginHandle");
        assertThat(accountId).isNotBlank();
        assertThat(handle).isNotBlank();

        // MongoDB: the account is ACTIVE and linked, its contact verified, the activation staged
        Document account = mongo("identity_users", new Document("_id", accountId)).get(0);
        assertThat(account.getString("status")).isEqualTo("ACTIVE");
        assertThat(account.getString("username")).isEqualTo(accountId);
        String keycloakUserId = account.getString("keycloakUserId");
        assertThat(keycloakUserId).isNotBlank().isNotEqualTo(accountId);
        List<Document> contacts = mongo("identity_contacts", new Document("accountId", accountId));
        assertThat(contacts).singleElement().satisfies(c -> {
            assertThat(c.getString("type")).isEqualTo("EMAIL");
            assertThat(c.get("verifiedAt")).isNotNull();
        });
        assertThat(mongo("identity_outbox", new Document("eventType", "identity.AccountActivated")
                .append("payload.userId", accountId))).as("activation staged in the outbox").hasSize(1);

        // Keycloak: username = account id, enabled, role, attribute
        List<UserRepresentation> users = keycloakUsers(accountId);
        assertThat(users).hasSize(1);
        UserRepresentation user = users.get(0);
        assertThat(user.getId()).isEqualTo(keycloakUserId);
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getAttributes().get("accountId")).containsExactly(accountId);
        assertThat(admin().realm(BUYERS).users().get(user.getId()).roles().realmLevel().listAll())
                .extracting(r -> r.getName()).contains("CUSTOMER");

        // a real token for that user carries accountId, and sub is NOT the account id
        assertThat(tokenClaims(user.getId())).containsEntry("accountId", accountId)
                .containsEntry("preferred_username", accountId)
                .containsEntry("sub", keycloakUserId);

        // the status endpoint agrees
        Response status = call("GET", "/api/internal/auth/accounts/" + accountId + "/status", null);
        assertThat(status.status()).isEqualTo(200);
        assertThat(status.text("status")).isEqualTo("ACTIVE");
        assertThat(status.text("keycloakUserId")).isEqualTo(keycloakUserId);

        // the handle redeems once, to this account
        Response redeemed = call("POST", "/api/internal/auth/handles/redeem", Map.of("handle", handle, "clientId", CLIENT));
        assertThat(redeemed.status()).isEqualTo(200);
        assertThat(redeemed.text("accountId")).isEqualTo(accountId);
        Response replay = call("POST", "/api/internal/auth/handles/redeem", Map.of("handle", handle, "clientId", CLIENT));
        assertThat(replay.status()).isIn(400, 410);
        assertThat(replay.text("errorCode")).isEqualTo("LOGIN_HANDLE_INVALID");
    }

    @Test
    @Order(2)
    @DisplayName("a WhatsApp number works the same way, and a second device with the same contact finds the same account")
    void secondDeviceFindsTheSameAccount() {
        String phone = uniquePhone();
        Response first = ensureUntilDone(proofFor(phone), true);
        assertThat(first.status()).isEqualTo(200);
        assertThat(first.body().get("isNew")).isEqualTo(true);
        assertThat(captured.lastTo(phone).orElseThrow().channel().name()).isEqualTo("WHATSAPP");
        long before = accounts();

        // a second device asks for its own code (after the cooldown) and proves the same contact
        sleep(1_200);
        Response second = ensureUntilDone(proofFor(phone), true);

        assertThat(second.status()).isEqualTo(200);
        assertThat(second.text("accountId")).isEqualTo(first.text("accountId"));
        assertThat(second.body().get("isNew")).isEqualTo(false);
        assertThat(second.text("loginHandle")).as("a fresh handle for the second device")
                .isNotBlank().isNotEqualTo(first.text("loginHandle"));
        assertThat(accounts()).as("no second account").isEqualTo(before);
        assertThat(keycloakUsers(first.text("accountId"))).hasSize(1);
        assertThat(mongo("identity_contacts", new Document("accountId", first.text("accountId")))).hasSize(1);
    }

    @Test
    @Order(3)
    @DisplayName("Redis flushed between verify and ensure: PROOF_INVALID and nothing is created anywhere")
    void redisFlushBetweenVerifyAndEnsure() {
        String email = uniqueEmail();
        String proof = proofFor(email);
        long accountsBefore = accounts();
        long contactsBefore = mongo("identity_contacts", new Document()).size();
        int keycloakBefore = admin().realm(BUYERS).users().count();

        redis.execute(connection -> connection.serverCommands().flushAll()).blockLast();

        Response ensured = ensureOnce(proof, true);

        assertThat(ensured.status()).isIn(400, 410);
        assertThat(ensured.text("errorCode")).isEqualTo("PROOF_INVALID");
        assertThat(accounts()).isEqualTo(accountsBefore);
        assertThat(mongo("identity_contacts", new Document()).size()).isEqualTo((int) contactsBefore);
        assertThat(admin().realm(BUYERS).users().count()).isEqualTo(keycloakBefore);

        // and the person can simply start again
        Response again = ensureUntilDone(proofFor(email), false);
        assertThat(again.status()).isEqualTo(200);
        assertThat(again.body()).doesNotContainKey("loginHandle");
    }

    @Test
    @Order(4)
    @DisplayName("concurrent ensure for one contact, with several proofs and repeated calls: one account, one Keycloak user")
    void concurrentEnsureCreatesOneAccount() throws Exception {
        String email = uniqueEmail();
        List<String> proofs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            proofs.add(proofFor(email));
            sleep(1_200);
        }
        long before = accounts();

        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Response>> work = new ArrayList<>();
            for (String proof : proofs) {
                work.add(() -> ensureUntilDone(proof, true));
                work.add(() -> ensureUntilDone(proof, true));
            }
            List<Response> results = new ArrayList<>();
            for (Future<Response> future : pool.invokeAll(work)) {
                results.add(future.get());
            }

            assertThat(results).extracting(Response::status).containsOnly(200);
            Set<String> ids = results.stream().map(r -> r.text("accountId")).collect(Collectors.toSet());
            assertThat(ids).as("every caller got the same account").hasSize(1);
            // Callers that joined the one running workflow all receive its result, including isNew=true;
            // what must hold is that exactly one account came out of it (below), and that a caller arriving
            // after completion is told isNew=false (the second-device test).
            assertThat(results.stream().filter(r -> Boolean.TRUE.equals(r.body().get("isNew"))).count())
                    .as("somebody created it").isGreaterThanOrEqualTo(1);
            String accountId = ids.iterator().next();
            assertThat(accounts()).isEqualTo(before + 1);
            assertThat(mongo("identity_contacts", new Document("accountId", accountId))).hasSize(1);
            assertThat(keycloakUsers(accountId)).hasSize(1);
            assertThat(mongo("identity_outbox", new Document("eventType", "identity.AccountActivated")
                    .append("payload.userId", accountId))).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Order(5)
    @DisplayName("Keycloak stopped during ensure: 202 PROVISIONING, the account survives, and it completes after the restart")
    void keycloakOutageDuringEnsure() throws Exception {
        String email = uniqueEmail();
        String proof = proofFor(email);

        KEYCLOAK.getDockerClient().stopContainerCmd(KEYCLOAK.getContainerId()).withTimeout(5).exec();
        List<Integer> statuses = new ArrayList<>();
        try {
            Response first = ensureOnce(proof, true);
            statuses.add(first.status());
            assertThat(first.status()).as("%s", first.body()).isEqualTo(202);
            assertThat(first.text("status")).isEqualTo("PROVISIONING");
            sleep(2_000);
            Response second = ensureOnce(proof, true);
            assertThat(second.status()).as("still waiting while Keycloak is down").isEqualTo(202);

            List<Document> pending = mongo("identity_users", new Document("status", "PROVISIONING"));
            assertThat(pending).as("the account exists and waits, it was not deleted").hasSize(1);
            assertThat(pending.get(0).getString("keycloakUserId")).isNull();
        } finally {
            KEYCLOAK.getDockerClient().startContainerCmd(KEYCLOAK.getContainerId()).exec();
        }
        await().atMost(Duration.ofMinutes(3)).pollInterval(Duration.ofSeconds(2)).until(this::keycloakAnswers);

        Response done = ensureUntilDone(proof, true, statuses);

        assertThat(done.status()).as("statuses seen: %s", statuses).isEqualTo(200);
        assertThat(done.text("status")).isEqualTo("ACTIVE");
        String accountId = done.text("accountId");
        assertThat(keycloakUsers(accountId)).hasSize(1);
        assertThat(mongo("identity_users", new Document("_id", accountId)).get(0).getString("status")).isEqualTo("ACTIVE");
        assertThat(mongo("identity_users", new Document("status", "PROVISIONING"))).isEmpty();
    }

    @Test
    @Order(6)
    @DisplayName("a crash right after the claim: Keycloak answers 503 to the user create, the account stays PROVISIONING, the retry finishes it - one account, one Keycloak user")
    void crashAfterClaimThenRetry() {
        String email = uniqueEmail();
        String proof = proofFor(email);
        long accountsBefore = accounts();
        PROXY.failBefore(2, request -> request.equals("POST /admin/realms/" + BUYERS + "/users"));

        Response first = ensureOnce(proof, true);
        assertThat(first.status()).as("%s", first.body()).isIn(200, 202);
        Response done = ensureUntilDone(proof, true);

        assertThat(done.status()).isEqualTo(200);
        assertThat(done.text("status")).isEqualTo("ACTIVE");
        assertThat(PROXY.failures()).as("Keycloak really answered 5xx").isEqualTo(2);
        assertThat(accounts()).isEqualTo(accountsBefore + 1);
        String accountId = done.text("accountId");
        assertThat(keycloakUsers(accountId)).hasSize(1);
        assertThat(mongo("identity_contacts", new Document("accountId", accountId))).hasSize(1);
        assertThat(mongo("identity_users", new Document("_id", accountId)).get(0).getString("status")).isEqualTo("ACTIVE");
    }

    @Test
    @Order(7)
    @DisplayName("an orphan Keycloak user (created, then the answer was lost): the retry reads it back by username and adopts it - no second user")
    void orphanKeycloakUserIsAdoptedByUsername() {
        String email = uniqueEmail();
        String proof = proofFor(email);
        long accountsBefore = accounts();
        int usersBefore = admin().realm(BUYERS).users().count();
        // Keycloak creates the user, and the caller is told 503: the worker dies holding an unlinked user
        PROXY.failAfter(1, request -> request.equals("POST /admin/realms/" + BUYERS + "/users"));

        Response done = ensureUntilDone(proof, true);

        assertThat(done.status()).as("%s", done.body()).isEqualTo(200);
        assertThat(PROXY.failures()).isGreaterThanOrEqualTo(1);
        String accountId = done.text("accountId");
        assertThat(accounts()).isEqualTo(accountsBefore + 1);
        assertThat(admin().realm(BUYERS).users().count()).as("one user created, none duplicated").isEqualTo(usersBefore + 1);
        List<UserRepresentation> users = keycloakUsers(accountId);
        assertThat(users).hasSize(1);
        assertThat(mongo("identity_users", new Document("_id", accountId)).get(0).getString("keycloakUserId"))
                .as("the account was linked to the orphan, found by its exact username").isEqualTo(users.get(0).getId());
        assertThat(users.get(0).getAttributes().get("accountId")).containsExactly(accountId);
    }

    @Test
    @Order(8)
    @DisplayName("Keycloak 5xx on the attribute write: the activity retries, the account completes ACTIVE with its role and attribute")
    void keycloakFiveHundredOnTheAttributeStep() {
        String email = uniqueEmail();
        String proof = proofFor(email);
        PROXY.failBefore(2, request -> request.startsWith("PUT /admin/realms/" + BUYERS + "/users/"));

        Response done = ensureUntilDone(proof, false);

        assertThat(done.status()).as("%s", done.body()).isEqualTo(200);
        String accountId = done.text("accountId");
        UserRepresentation user = keycloakUsers(accountId).get(0);
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getAttributes().get("accountId")).containsExactly(accountId);
        assertThat(admin().realm(BUYERS).users().get(user.getId()).roles().realmLevel().listAll())
                .extracting(r -> r.getName()).contains("CUSTOMER");
    }

    // ---------------------------------------------------------------------------------- utilities

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean keycloakAnswers() {
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                    URI.create("http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK_PORT + "/realms/" + BUYERS)).build(),
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /** Password grant of the fixture client: the realm's {@code accountId} mapper decides what the token says. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> tokenClaims(String keycloakUserId) throws Exception {
        CredentialRepresentation password = new CredentialRepresentation();
        password.setType(CredentialRepresentation.PASSWORD);
        password.setValue("Test-pass-123");
        password.setTemporary(false);
        admin().realm(BUYERS).users().get(keycloakUserId).resetPassword(password);
        String username = admin().realm(BUYERS).users().get(keycloakUserId).toRepresentation().getUsername();
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK_PORT + "/realms/" + BUYERS
                                + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=password&client_id=test-direct&username="
                        + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&password=Test-pass-123")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String accessToken = mapper.readTree(response.body()).get("access_token").asText();
        String payload = accessToken.split("\\.")[1];
        return mapper.readValue(Base64.getUrlDecoder().decode(payload), Map.class);
    }

    @SuppressWarnings("unused")
    private static final Class<?>[] FIXTURES = {MongoReplicaSet.class, RedisNode.class, TemporalDevServer.class};
}
