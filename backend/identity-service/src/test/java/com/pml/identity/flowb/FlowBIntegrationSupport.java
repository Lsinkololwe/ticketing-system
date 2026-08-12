package com.pml.identity.flowb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.identity.domain.model.User;
import com.pml.identity.flowb.support.FaultInjectingProxy;
import com.pml.identity.flowb.support.KeycloakRegistrationClient;
import com.pml.identity.flowb.support.RealmProvisioner;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.UserRepository;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Shared environment for the Flow B suite: a real Keycloak running the shipped realm, themes
 * and {@code keycloak-extensions} SPI jar; a real MongoDB, PostgreSQL and Redis; and the real
 * Identity Service application on a real port.
 *
 * <h2>Why no mocks</h2>
 * <p>Flow B is a claim about four processes agreeing. A {@code @MockBean} anywhere on the path
 * would let the suite go green while the flow is broken in production, so there are none. The
 * single substitution — the Azure Service Bus wire protocol — is declared in
 * {@code application-flowb.yml} and is still asserted through the real {@code StreamBridge}
 * binding.</p>
 *
 * <h2>Ordering</h2>
 * <p>Keycloak needs {@code IDENTITY_SERVICE_URL} at container-start time, but the Spring Boot
 * port is only known after the context refreshes. {@link FaultInjectingProxy} resolves the
 * cycle: it binds first on a host port exposed to the container network, and is re-pointed at
 * the application in {@link #wireEnvironment()}. It doubles as the fault-injection point for
 * {@code FlowBFailureRecoveryIT}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("flowb")
@Import(TestChannelBinderConfiguration.class)
@Tag("flow-b")
public abstract class FlowBIntegrationSupport {

    protected static final String IDENTITY_EVENTS_DESTINATION = "identity-events";
    protected static final Duration SYNC_TIMEOUT = Duration.ofSeconds(20);

    private static final ObjectMapper JSON = new ObjectMapper();

    // ------------------------------------------------------------------
    // environment (started once per JVM, shared by every Flow B test class)
    // ------------------------------------------------------------------

    protected static final FaultInjectingProxy SYNC_PROXY;
    protected static final MongoDBContainer MONGO;
    protected static final PostgreSQLContainer<?> POSTGRES;
    protected static final GenericContainer<?> REDIS;
    protected static final KeycloakContainer KEYCLOAK;
    protected static final RealmProvisioner REALM;
    protected static final List<String> REALM_FIXTURES_APPLIED;
    protected static final KeycloakRegistrationClient REGISTRATION;
    protected static final String KEYCLOAK_BASE_URL;

    static {
        // 1. the sync hop, so Keycloak has a stable callback address
        SYNC_PROXY = FaultInjectingProxy.start();
        Testcontainers.exposeHostPorts(SYNC_PROXY.getPort());
        String identityServiceUrl =
                "http://host.testcontainers.internal:" + SYNC_PROXY.getPort();

        REALM = RealmProvisioner.load();
        Path extensionsJar = locateExtensionsJar();
        Path themes = REALM.themesDirectory();
        if (!Files.isDirectory(themes)) {
            throw new IllegalStateException(
                    "Keycloak login theme not found at " + themes
                            + " — the registration form under test lives there.");
        }

        // 2. data stores
        MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.0"));
        POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("shared_db")
                .withUsername("app_user")
                .withPassword("app_password")
                .withInitScript("flowb/init-modulith-schema.sql");
        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379)
                .waitingFor(Wait.forListeningPort());

        MONGO.start();
        POSTGRES.start();
        REDIS.start();

        // 3. Keycloak on a fixed host port so the token issuer is known before start-up:
        //    a mismatch between the `iss` the SPI's token carries and the issuer the Identity
        //    Service validates against would fail the sync hop for the wrong reason.
        int keycloakPort = freePort();
        KEYCLOAK_BASE_URL = "http://localhost:" + keycloakPort;
        KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.5.2")
                .withAdminUsername("admin")
                .withAdminPassword("admin");
        KEYCLOAK.setPortBindings(List.of(keycloakPort + ":8080"));
        KEYCLOAK
                .withCopyFileToContainer(MountableFile.forHostPath(extensionsJar),
                        "/opt/keycloak/providers/keycloak-extensions.jar")
                .withCopyFileToContainer(MountableFile.forHostPath(themes),
                        "/opt/keycloak/themes")
                .withEnv("KC_HOSTNAME", KEYCLOAK_BASE_URL)
                .withEnv("KC_HOSTNAME_BACKCHANNEL_DYNAMIC", "false")
                // keycloak-extensions SPI configuration (UserSyncEventListenerFactory)
                .withEnv("IDENTITY_SERVICE_URL", identityServiceUrl)
                .withEnv("OTP_SERVICE_URL", identityServiceUrl)
                .withEnv("USER_SYNC_REALM", REALM.realmName())
                .withEnv("OTP_CLIENT_ID", REALM.value("TICKETING_OTP_AUTHENTICATOR_CLIENT_ID"))
                .withEnv("OTP_CLIENT_SECRET", REALM.value("KEYCLOAK_OTP_AUTHENTICATOR_SECRET"))
                // Container-internal: the SPI calls Keycloak's own token endpoint from inside
                // the container, so it must use the in-container listener. The issued token's
                // `iss` is still KC_HOSTNAME-based, which is what the Identity Service validates.
                .withEnv("KEYCLOAK_TOKEN_URL",
                        "http://localhost:8080/realms/" + REALM.realmName()
                                + "/protocol/openid-connect/token")
                .withStartupTimeout(Duration.ofMinutes(4));
        KEYCLOAK.start();

        REALM_FIXTURES_APPLIED = REALM.importInto(KEYCLOAK.getKeycloakAdminClient(), identityServiceUrl);
        REGISTRATION = new KeycloakRegistrationClient(KEYCLOAK_BASE_URL, REALM.realmName());
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String issuer = KEYCLOAK_BASE_URL + "/realms/" + REALM.realmName();

        registry.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl("flowb"));
        registry.add("spring.data.mongodb.database", () -> "flowb");

        registry.add("spring.datasource.url", () ->
                POSTGRES.getJdbcUrl() + "?currentSchema=dev_ticketing_modulith");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> issuer);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> issuer + "/protocol/openid-connect/certs");
        registry.add("spring.security.oauth2.client.provider.identity-service.token-uri",
                () -> issuer + "/protocol/openid-connect/token");
        registry.add("spring.security.oauth2.client.registration.identity-service.client-id",
                () -> REALM.value("TICKETING_IDENTITY_SERVICE_CLIENT_ID"));
        registry.add("spring.security.oauth2.client.registration.identity-service.client-secret",
                () -> REALM.value("IDENTITY_SERVICE_SECRET"));

        registry.add("keycloak.server-url", () -> KEYCLOAK_BASE_URL);
        registry.add("keycloak.realm", REALM::realmName);
        registry.add("keycloak.admin-username", KEYCLOAK::getAdminUsername);
        registry.add("keycloak.admin-password", KEYCLOAK::getAdminPassword);
        registry.add("keycloak.admin-realm", () -> "master");
        registry.add("keycloak.client-id", () -> REALM.value("TICKETING_IDENTITY_SERVICE_CLIENT_ID"));
        registry.add("keycloak.client-secret", () -> REALM.value("IDENTITY_SERVICE_SECRET"));
    }

    // ------------------------------------------------------------------
    // injected collaborators
    // ------------------------------------------------------------------

    @LocalServerPort
    protected int appPort;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected OrganizationRepository organizationRepository;

    @Autowired
    protected OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    protected ReactiveMongoTemplate mongoTemplate;

    @Autowired
    protected ReactiveStringRedisTemplate redisTemplate;

    @Autowired
    protected DataSource dataSource;

    @Autowired
    protected OutputDestination outboundEvents;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    // ------------------------------------------------------------------
    // per-test wiring and the all-or-nothing preflight
    // ------------------------------------------------------------------

    @BeforeEach
    void wireEnvironment() {
        SYNC_PROXY.reset();
        SYNC_PROXY.setTarget("http://localhost:" + appPort);
        drainOutboundEvents();
        assertAllComponentsLive();
    }

    /**
     * Flow B is only meaningful when every hop is up. This runs before each test so a dead
     * component aborts the run instead of letting an assertion pass for the wrong reason.
     */
    protected void assertAllComponentsLive() {
        List<String> dead = new ArrayList<>();

        if (!MONGO.isRunning()) {
            dead.add("mongodb container");
        } else if (!quietly(() -> mongoTemplate.executeCommand("{ ping: 1 }")
                .block(Duration.ofSeconds(5)) != null)) {
            dead.add("mongodb ping");
        }

        if (!POSTGRES.isRunning()) {
            dead.add("postgres container");
        } else if (!quietly(this::postgresReachable)) {
            dead.add("postgres query (Modulith event registry)");
        }

        if (!REDIS.isRunning()) {
            dead.add("redis container");
        } else if (!quietly(() -> Boolean.TRUE.equals(
                redisTemplate.hasKey("flowb:probe").block(Duration.ofSeconds(5)) != null))) {
            dead.add("redis command");
        }

        if (!KEYCLOAK.isRunning()) {
            dead.add("keycloak container");
        } else if (!quietly(() -> getStatus(KEYCLOAK_BASE_URL + "/realms/" + REALM.realmName()) == 200)) {
            dead.add("keycloak realm endpoint");
        }

        if (!quietly(() -> getStatus("http://localhost:" + appPort + "/actuator/health") < 500)) {
            dead.add("identity-service actuator");
        }

        if (SYNC_PROXY.getPort() <= 0) {
            dead.add("keycloak -> identity-service proxy");
        }

        if (!dead.isEmpty()) {
            fail("Flow B cannot be evaluated — these components are not live: " + dead
                    + ". The flow is all-or-nothing; a partial environment would produce "
                    + "misleading results.");
        }
    }

    // ------------------------------------------------------------------
    // helpers used by the test classes
    // ------------------------------------------------------------------

    protected RealmResource realm() {
        return KEYCLOAK.getKeycloakAdminClient().realm(REALM.realmName());
    }

    protected Optional<UserRepresentation> findKeycloakUser(String username) {
        List<UserRepresentation> found = realm().users().search(username, true);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /** Roles mapped <em>directly</em> to the user (what {@code grantRole} produces). */
    protected List<String> keycloakDirectRealmRoles(String userId) {
        return realm().users().get(userId).roles().realmLevel().listAll().stream()
                .map(r -> r.getName())
                .toList();
    }

    /**
     * Roles the user <em>effectively</em> holds, with composites expanded — this is what ends
     * up in {@code realm_access.roles} and therefore what every downstream service authorizes
     * on. {@code CUSTOMER} reaches users through the {@code default-roles-*} and
     * {@code ORGANIZER} composites rather than as a direct mapping.
     */
    protected List<String> keycloakEffectiveRealmRoles(String userId) {
        return realm().users().get(userId).roles().realmLevel().listEffective().stream()
                .map(r -> r.getName())
                .toList();
    }

    protected Optional<User> findMongoUser(String id) {
        return Optional.ofNullable(userRepository.findById(id).block(Duration.ofSeconds(10)));
    }

    /** Waits for the Keycloak → Identity Service sync to land the user in MongoDB. */
    protected User awaitSyncedUser(String keycloakUserId) {
        User user = await(() -> findMongoUser(keycloakUserId).orElse(null));
        assertThat(user)
                .as("user %s should have been synced to MongoDB within %s", keycloakUserId, SYNC_TIMEOUT)
                .isNotNull();
        return user;
    }

    /** Asserts the sync did <em>not</em> land — used by the drift and rejection tests. */
    protected void assertNotSynced(String keycloakUserId) {
        sleep(Duration.ofSeconds(3));
        assertThat(findMongoUser(keycloakUserId))
                .as("no MongoDB document should exist for %s", keycloakUserId)
                .isEmpty();
    }

    protected <T> T await(java.util.function.Supplier<T> supplier) {
        long deadline = System.nanoTime() + SYNC_TIMEOUT.toNanos();
        T last = null;
        while (System.nanoTime() < deadline) {
            last = supplier.get();
            if (last != null) {
                return last;
            }
            sleep(Duration.ofMillis(250));
        }
        return last;
    }

    protected void awaitTrue(String description, BooleanSupplier condition) {
        long deadline = System.nanoTime() + SYNC_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            sleep(Duration.ofMillis(250));
        }
        fail("Timed out after " + SYNC_TIMEOUT + " waiting for: " + description);
    }

    // ---- outbound events -------------------------------------------------

    /** Drains any events left over from a previous test. */
    protected void drainOutboundEvents() {
        while (outboundEvents.receive(50, IDENTITY_EVENTS_DESTINATION) != null) {
            // discard
        }
    }

    /** Collects every {@code UserRegisteredEvent} published within a short settle window. */
    protected List<Map<String, Object>> collectUserRegisteredEvents(Duration settle) {
        List<Map<String, Object>> events = new ArrayList<>();
        long deadline = System.nanoTime() + settle.toNanos();
        while (System.nanoTime() < deadline) {
            Message<byte[]> message = outboundEvents.receive(250, IDENTITY_EVENTS_DESTINATION);
            if (message == null) {
                continue;
            }
            events.add(readEvent(message));
        }
        return events;
    }

    /** Waits for at least one event, then keeps draining to prove there is not a second. */
    protected List<Map<String, Object>> awaitUserRegisteredEvents(int expected) {
        List<Map<String, Object>> events = new ArrayList<>();
        long deadline = System.nanoTime() + SYNC_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline && events.size() < expected) {
            Message<byte[]> message = outboundEvents.receive(500, IDENTITY_EVENTS_DESTINATION);
            if (message != null) {
                events.add(readEvent(message));
            }
        }
        // settle window: anything arriving now would be a duplicate
        events.addAll(collectUserRegisteredEvents(Duration.ofSeconds(2)));
        return events;
    }

    /**
     * Waits for {@code expected} events <em>for one user</em>, then keeps draining so a
     * duplicate would still be observed. Filtering by user id keeps the assertion honest when
     * other tests in the shared context are publishing concurrently.
     */
    protected List<Map<String, Object>> awaitUserRegisteredEventsFor(String userId, int expected) {
        List<Map<String, Object>> mine = new ArrayList<>();
        long deadline = System.nanoTime() + SYNC_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline && mine.size() < expected) {
            Message<byte[]> message = outboundEvents.receive(500, IDENTITY_EVENTS_DESTINATION);
            if (message != null) {
                Map<String, Object> event = readEvent(message);
                if (userId.equals(event.get("userId"))) {
                    mine.add(event);
                }
            }
        }
        for (Map<String, Object> event : collectUserRegisteredEvents(Duration.ofSeconds(2))) {
            if (userId.equals(event.get("userId"))) {
                mine.add(event);
            }
        }
        return mine;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readEvent(Message<byte[]> message) {
        try {
            return JSON.readValue(message.getPayload(), Map.class);
        } catch (IOException e) {
            throw new IllegalStateException("Unreadable event payload", e);
        }
    }

    // ---- raw HTTP against the Identity Service --------------------------

    protected HttpResponse<String> callIdentityService(String method, String path,
                                                       String body, String bearerToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + appPort + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json");
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        try {
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Call to identity-service failed: " + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted calling " + path, e);
        }
    }

    /** Service-account token for the SPI's own client — the one Flow B uses in production. */
    protected String internalServiceToken() {
        return REGISTRATION.clientCredentialsToken(
                        REALM.value("TICKETING_OTP_AUTHENTICATOR_CLIENT_ID"),
                        REALM.value("KEYCLOAK_OTP_AUTHENTICATOR_SECRET"),
                        "openid internal-read internal-write")
                .orElseThrow(() -> new IllegalStateException(
                        "Could not obtain the internal service token — the sync hop cannot "
                                + "authenticate, so Flow B is broken at the authorization boundary."));
    }

    // ---- misc ------------------------------------------------------------

    protected void cleanMongo() {
        userRepository.deleteAll().block(Duration.ofSeconds(10));
        organizationRepository.deleteAll().block(Duration.ofSeconds(10));
        organizationMemberRepository.deleteAll().block(Duration.ofSeconds(10));
    }

    /** Removes a user from Keycloak so a handle can be reused across tests. */
    protected void deleteKeycloakUser(String username) {
        findKeycloakUser(username).ifPresent(user ->
                realm().users().get(user.getId()).remove());
    }

    protected static String handle(String prefix) {
        return prefix + COUNTER.incrementAndGet() + "x" + (System.nanoTime() % 100_000);
    }

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private boolean postgresReachable() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            return statement.execute("SELECT 1");
        }
    }

    private int getStatus(String url) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static boolean quietly(ThrowingBooleanSupplier supplier) {
        try {
            return supplier.getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    @FunctionalInterface
    private interface ThrowingBooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }

    protected static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * The keycloak-extensions shaded jar is a real input to this suite: it carries
     * {@code AccountTypeRoleMapper} and {@code UserSyncEventListener}. A stale or missing jar
     * would silently reduce Flow B to "Keycloak creates a user", so this fails loudly.
     */
    private static Path locateExtensionsJar() {
        Path jar = Path.of("").toAbsolutePath()
                .resolve("../keycloak-extensions/target/keycloak-extensions-1.0.0.jar")
                .normalize();
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("""
                    keycloak-extensions jar not found at %s
                    Flow B's role minting and user sync live in that jar. Build it first:
                      (cd backend/keycloak-extensions && mvn -q clean package)
                    """.formatted(jar));
        }
        return jar;
    }

    private static int freePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("No free port for Keycloak", e);
        }
    }
}
