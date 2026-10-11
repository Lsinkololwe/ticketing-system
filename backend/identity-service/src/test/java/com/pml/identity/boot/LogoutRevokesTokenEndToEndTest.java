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
import com.pml.shared.security.revocation.RevocationCacheTrust;
import com.pml.shared.security.revocation.RevocationKeys;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.Document;
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
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Signing out of Keycloak ends the platform's use of the access token, with nothing faked on the
 * token side: two real sessions of one user on a real Keycloak 26.5.2, the real Keycloak logout,
 * the slim event the Keycloak listener posts, MongoDB, Redis and the security chain's revocation
 * guard.
 *
 * <p>The Keycloak event listener plugin is not installed in this container (its jar is built after
 * the tests of this module), so the event it would post for the logout is posted here by the test;
 * the listener's own payload is covered by {@code UserSyncEventListenerTest} in keycloak-extensions
 * and the controller by {@code KeycloakSyncControllerTest}.</p>
 */
@Tag("L3")
@Tag("ET-IDN-003")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("A Keycloak logout stops the logged-out session's access token everywhere, and only that session's")
@SpringBootTest(classes = IdentityServiceApplication.class)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
class LogoutRevokesTokenEndToEndTest {

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
    ReactiveStringRedisTemplate redis;
    @Autowired
    com.pml.identity.security.revocation.RevocationCacheWarmer warmer;

    /** One real login: the raw tokens and the claims the platform keys revocation on. */
    private record Session(String accessToken, String refreshToken, String jti, String sid, String sub, Jwt jwt) {
    }

    private static String username;
    private static Session first;
    private static Session second;

    // ---------------------------------------------------------------------------------- helpers

    private Session login() throws Exception {
        return fromGrant(tokenRequest("grant_type=password&client_id=test-direct&username="
                + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&password=" + PASSWORD));
    }

    private Session fromGrant(Map<String, Object> grant) throws Exception {
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

    private int keycloakLogout(Session session) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK_PORT + "/realms/" + BUYERS
                                + "/protocol/openid-connect/logout"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "client_id=test-direct&refresh_token=" + session.refreshToken())).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private void internal(String path, Map<String, Object> body) {
        WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient().build()
                .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("SCOPE_internal-write")))
                .post().uri(path).contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .exchange().expectStatus().is2xxSuccessful();
    }

    /** The event Keycloak's listener posts for a LOGOUT of that session. */
    private void listenerReports(String eventType, Session session) {
        WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient().build()
                .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("SCOPE_internal-write")))
                .post().uri("/api/internal/keycloak/sync/event").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("eventId", UUID.randomUUID().toString(), "eventType", eventType,
                        "userId", session.sub(), "realm", BUYERS, "timestamp", Instant.now().toEpochMilli(),
                        "sid", session.sid()))
                .exchange().expectStatus().isAccepted();
    }

    /** A request carrying the session's access token through the real security chain. */
    private WebTestClient.ResponseSpec call(Session session) {
        return WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient()
                .responseTimeout(Duration.ofSeconds(30)).build()
                .mutateWith(mockJwt().jwt(session.jwt()))
                .post().uri("/graphql").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", "{ __typename }")).exchange();
    }

    private boolean refused(Session session) {
        var result = call(session).expectBody(String.class).returnResult();
        boolean unauthorized = result.getStatus().value() == 401;
        if (unauthorized) {
            assertThat(result.getResponseBody()).contains("TOKEN_REVOKED");
        }
        return unauthorized;
    }

    private List<Document> revocationRows(String type, String value) {
        MongoClient client = IdentityStack.mongoClient();
        try {
            List<Document> rows = new ArrayList<>();
            reactor.core.publisher.Flux.from(client.getDatabase(DATABASE).getCollection("identity_token_revocations")
                    .find(new Document("type", type).append("value", value))).toIterable().forEach(rows::add);
            return rows;
        } finally {
            client.close();
        }
    }

    // ---------------------------------------------------------------------------------- the story

    @Test
    @Order(1)
    @DisplayName("two real sessions of one user carry different sids, and both are accepted before any logout")
    void bothSessionsWork() throws Exception {
        username = "logout-" + UUID.randomUUID().toString().substring(0, 8);
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setEmail(username + "@example.com");
        user.setFirstName("Logout");
        user.setLastName("Test");
        CredentialRepresentation password = new CredentialRepresentation();
        password.setType(CredentialRepresentation.PASSWORD);
        password.setValue(PASSWORD);
        password.setTemporary(false);
        user.setCredentials(List.of(password));
        try (Response created = KEYCLOAK.getKeycloakAdminClient().realm(BUYERS).users().create(user)) {
            assertThat(created.getStatus()).isEqualTo(201);
        }

        first = login();
        second = login();

        assertThat(first.sid()).as("a real access token carries the session id").isNotBlank();
        assertThat(second.sid()).isNotBlank().isNotEqualTo(first.sid());
        assertThat(first.sub()).isEqualTo(second.sub());
        assertThat(refused(first)).isFalse();
        assertThat(refused(second)).isFalse();
    }

    @Test
    @Order(2)
    @DisplayName("logging the first session out: its access token is refused at once, the other session's is not")
    void logoutCutsOnlyThatSession() throws Exception {
        assertThat(keycloakLogout(first)).isEqualTo(204);
        listenerReports("LOGOUT", first);

        assertThat(refused(first)).as("the logged-out session's token, still unexpired and correctly signed").isTrue();
        assertThat(refused(second)).as("the user's other session").isFalse();

        List<Document> rows = revocationRows("SESSION", first.sid());
        assertThat(rows).hasSize(1);
        Instant expiresAt = rows.get(0).getDate("expiresAt").toInstant();
        assertThat(expiresAt).isAfter(Instant.now()).isBefore(Instant.now().plus(Duration.ofMinutes(7)));
        assertThat(revocationRows("SESSION", second.sid())).isEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("Keycloak itself no longer refreshes the logged-out session")
    void refreshTokenIsDead() throws Exception {
        Map<String, Object> refreshed = tokenRequest(
                "grant_type=refresh_token&client_id=test-direct&refresh_token=" + first.refreshToken());
        assertThat(refreshed.get("httpStatus")).isEqualTo(400);
        assertThat(refreshed.get("error")).isEqualTo("invalid_grant");
    }

    @Test
    @Order(4)
    @DisplayName("a flushed Redis loses nothing: the logged-out token is still refused from MongoDB")
    void flushedCacheStillRefuses() {
        redis.delete(RevocationKeys.session(first.sid()), RevocationCacheTrust.SENTINEL_KEY).block();

        assertThat(refused(first)).isTrue();
        assertThat(refused(second)).isFalse();
        // the warmer rebuilds the cache and its completeness marker from the durable records
        assertThat(warmer.warm().block()).isGreaterThanOrEqualTo(1L);
        assertThat(redis.hasKey(RevocationKeys.session(first.sid())).block()).isTrue();
        assertThat(redis.hasKey(RevocationCacheTrust.SENTINEL_KEY).block()).isTrue();
    }

    @Test
    @Order(5)
    @DisplayName("repeating the logout event keeps one record, pushes its window out, and keeps the first reason")
    void repeatedEventIsIdempotent() throws Exception {
        Document before = revocationRows("SESSION", first.sid()).get(0);
        Thread.sleep(1_100);

        listenerReports("LOGOUT", first);
        listenerReports("LOGOUT", first);

        List<Document> after = revocationRows("SESSION", first.sid());
        assertThat(after).hasSize(1);
        assertThat(after.get(0).getDate("revokedAt")).as("the first cause's timestamp is kept")
                .isEqualTo(before.getDate("revokedAt"));
        assertThat(after.get(0).getDate("expiresAt")).as("the window moves out, it does not stay put")
                .isAfter(before.getDate("expiresAt"));
        assertThat(after.get(0).getString("reason")).isEqualTo(before.getString("reason"));
        assertThat(refused(first)).isTrue();
    }

    @Test
    @Order(6)
    @DisplayName("revoking one token by its jti refuses that token and not the next one minted for the same session")
    void tokenRevocationIsExact() throws Exception {
        Map<String, Object> refreshed = tokenRequest(
                "grant_type=refresh_token&client_id=test-direct&refresh_token=" + second.refreshToken());
        assertThat(refreshed.get("httpStatus")).isEqualTo(200);
        Session next = fromGrant(refreshed);
        assertThat(next.sid()).isEqualTo(second.sid());
        assertThat(next.jti()).isNotEqualTo(second.jti());

        internal("/api/internal/revocations", Map.of("type", "TOKEN", "value", second.jti(),
                "reason", "this one token was leaked in a log"));

        assertThat(refused(second)).as("the revoked token").isTrue();
        assertThat(refused(next)).as("a later token of the same session").isFalse();
        assertThat(revocationRows("SESSION", second.sid())).isEmpty();
        second = next;
    }

    @Test
    @Order(7)
    @DisplayName("revoking a session also refuses tokens minted for it afterwards")
    void sessionRevocationCoversLaterTokens() throws Exception {
        internal("/api/internal/revocations", Map.of("type", "SESSION", "value", second.sid(),
                "reason", "this device was reported stolen"));

        Map<String, Object> refreshed = tokenRequest(
                "grant_type=refresh_token&client_id=test-direct&refresh_token=" + second.refreshToken());
        assertThat(refreshed.get("httpStatus")).as("Keycloak alone would still refresh it").isEqualTo(200);

        assertThat(refused(fromGrant(refreshed))).isTrue();
        second = fromGrant(refreshed);
    }

    @Test
    @Order(8)
    @DisplayName("revoking the user cuts the remaining session too, including tokens minted afterwards")
    void userRevocationCutsEverySession() throws Exception {
        internal("/api/internal/revocations/logout",
                Map.of("sub", second.sub(), "reason", "account compromised, all sessions ended"));

        assertThat(refused(second)).isTrue();
        Session minted = login();
        assertThat(refused(minted)).as("a token Keycloak issues after the revocation").isTrue();
    }
}
