package com.pml.keycloak.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ET-IDN-001")
@Tag("layer-1-decision")
class IdentityHttpTest {

    HttpServer server;
    final AtomicInteger tokenCalls = new AtomicInteger();
    final AtomicInteger apiCalls = new AtomicInteger();
    final List<String> bearers = Collections.synchronizedList(new ArrayList<>());
    final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-04T08:00:00Z"));
    final Clock clock = new Clock() {
        public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId z) { return this; }
        public Instant instant() { return now.get(); }
    };
    volatile int tokenTtl = 300;
    volatile int tokenStatus = 200;
    volatile int firstApiStatus = 200;
    volatile long apiDelayMillis = 0;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", ex -> {
            int n = tokenCalls.incrementAndGet();
            byte[] body = ("{\"access_token\":\"tok-" + n + "\",\"expires_in\":" + tokenTtl + "}").getBytes(StandardCharsets.UTF_8);
            ex.getRequestBody().readAllBytes();
            ex.sendResponseHeaders(tokenStatus, tokenStatus == 200 ? body.length : -1);
            if (tokenStatus == 200) ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/api/internal/auth/handles/redeem", ex -> {
            int n = apiCalls.incrementAndGet();
            bearers.add(ex.getRequestHeaders().getFirst("Authorization"));
            ex.getRequestBody().readAllBytes();
            if (apiDelayMillis > 0) {
                try { Thread.sleep(apiDelayMillis); } catch (InterruptedException ignored) { }
            }
            int status = n == 1 ? firstApiStatus : 200;
            String json = status == 200 ? "{\"accountId\":\"acc-1\"}"
                    : status == 400 ? "{\"errorCode\":\"LOGIN_HANDLE_INVALID\",\"detail\":\"secret-detail\",\"attemptsRemaining\":2}"
                    : "{}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    IdentityHttp http(Duration timeout) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new IdentityHttp(new IdentityHttp.Settings(base, base + "/token", "cid", "sec",
                Duration.ofSeconds(1), timeout), clock);
    }

    @Test
    @DisplayName("ET-IDN-001-R10 · FAIL CLOSED: any missing variable yields no client and names only the variable")
    void failClosed() {
        assertThat(IdentityHttp.fromEnvironment(Map.of(), clock)).isNull();
        Map<String, String> partial = Map.of("IDENTITY_BASE_URL", "http://x", "IDENTITY_CLIENT_ID", "id",
                "KEYCLOAK_TOKEN_URL", "http://t");
        assertThat(IdentityHttp.missing(partial)).containsExactly("IDENTITY_CLIENT_SECRET");
        assertThat(IdentityHttp.fromEnvironment(partial, clock)).isNull();
        Map<String, String> blank = Map.of("IDENTITY_BASE_URL", "http://x", "IDENTITY_CLIENT_ID", "id",
                "KEYCLOAK_TOKEN_URL", "http://t", "IDENTITY_CLIENT_SECRET", "  ");
        assertThat(IdentityHttp.fromEnvironment(blank, clock)).isNull();
        Map<String, String> full = Map.of("IDENTITY_BASE_URL", "http://x/", "IDENTITY_CLIENT_ID", "id",
                "KEYCLOAK_TOKEN_URL", "http://t", "IDENTITY_CLIENT_SECRET", "s");
        assertThat(IdentityHttp.fromEnvironment(full, clock)).isNotNull();
    }

    @Test
    @DisplayName("ET-IDN-001-R10 · the client-credentials token is cached until shortly before expiry")
    void tokenIsCached() {
        IdentityHttp http = http(Duration.ofSeconds(2));
        for (int i = 0; i < 3; i++) {
            http.post("/api/internal/auth/handles/redeem", new Dto.RedeemRequest("h", "c"), Dto.RedeemResponse.class);
        }
        assertThat(tokenCalls).hasValue(1);
        assertThat(bearers).containsOnly("Bearer tok-1");
        now.set(now.get().plusSeconds(280));                 // inside the 30 s skew window
        http.post("/api/internal/auth/handles/redeem", new Dto.RedeemRequest("h", "c"), Dto.RedeemResponse.class);
        assertThat(tokenCalls).hasValue(2);
        assertThat(bearers).last().isEqualTo("Bearer tok-2");
    }

    @Test
    @DisplayName("ET-IDN-001-R10 · a 401 drops the token, fetches a new one and retries exactly once")
    void refreshOn401() {
        firstApiStatus = 401;
        IdentityHttp http = http(Duration.ofSeconds(2));
        var reply = http.post("/api/internal/auth/handles/redeem", new Dto.RedeemRequest("h", "c"), Dto.RedeemResponse.class);
        assertThat(reply.body().accountId()).isEqualTo("acc-1");
        assertThat(tokenCalls).hasValue(2);
        assertThat(bearers).containsExactly("Bearer tok-1", "Bearer tok-2");
    }

    @Test
    @DisplayName("ET-IDN-001-R10 · problem documents are parsed; the message never carries body content")
    void problemParsedWithoutBodyInMessage() {
        firstApiStatus = 400;
        IdentityHttp http = http(Duration.ofSeconds(2));
        assertThatThrownBy(() -> http.post("/api/internal/auth/handles/redeem", new Dto.RedeemRequest("h", "c"), Dto.RedeemResponse.class))
                .isInstanceOfSatisfying(IdentityApiException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo("LOGIN_HANDLE_INVALID");
                    assertThat(e.problem().attemptsRemaining()).isEqualTo(2);
                    assertThat(e.getMessage()).doesNotContain("secret-detail");
                    assertThat(e.retryable()).isFalse();
                });
    }

    @Test
    @DisplayName("ET-IDN-001-R10 · a slow API call times out as unavailable")
    void requestTimeout() {
        apiDelayMillis = 1500;
        IdentityHttp http = http(Duration.ofMillis(300));
        assertThatThrownBy(() -> http.post("/api/internal/auth/handles/redeem", new Dto.RedeemRequest("h", "c"), Dto.RedeemResponse.class))
                .isInstanceOf(IdentityUnavailableException.class);
    }

    @Test
    @DisplayName("ET-IDN-001-R10 · a failing token endpoint fails closed and sends nothing to the API")
    void tokenFailureFailsClosed() {
        tokenStatus = 401;
        IdentityHttp http = http(Duration.ofSeconds(2));
        assertThatThrownBy(() -> http.post("/api/internal/auth/handles/redeem", new Dto.RedeemRequest("h", "c"), Dto.RedeemResponse.class))
                .isInstanceOf(IdentityUnavailableException.class);
        assertThat(apiCalls).hasValue(0);
    }

    @Test
    @DisplayName("ET-IDN-001-R10 · an unreachable service is unavailable, not an uncaught IOException")
    void unreachable() {
        IdentityHttp dead = new IdentityHttp(new IdentityHttp.Settings("http://127.0.0.1:1", "http://127.0.0.1:1/token",
                "c", "s", Duration.ofMillis(300), Duration.ofMillis(300)), clock);
        assertThatThrownBy(() -> dead.get("/x", Void.class)).isInstanceOf(IdentityUnavailableException.class);
    }
}
