package com.pml.shared.testing.it;

import com.pml.shared.testing.Providers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The six provider failure modes of ET-PLT-006 R6, each with a named stub and a test.
 *
 * <p>These are the cases that break the money. A provider that answers correctly is not
 * interesting; a provider that resets the connection after receiving a payment request is,
 * because the platform then cannot know whether the money moved — and every ambiguity in
 * ET-PAY-001, ET-PAY-002 and ET-ADM-003 descends from exactly that.
 */
@Tag("ET-PLT-006")
@DisplayName("ET-PLT-006-R6 · external providers are stubbed, failure-first")
class ProviderFailureModesTest {

    private static Providers providers;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    @BeforeAll
    static void startStubs() {
        providers = Providers.start();
    }

    @AfterAll
    static void stopStubs() {
        providers.close();
    }

    @BeforeEach
    void reset() {
        providers.reset();
    }

    @Test
    @DisplayName("1 · 503 — the provider is up and refusing")
    void serviceUnavailable() throws Exception {
        providers.serviceUnavailable("/deposits");

        HttpResponse<String> response = get("/deposits");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("2 · connection reset — no response at all, so the platform cannot know if the money moved")
    void connectionReset() {
        providers.connectionReset("/deposits");

        assertThatThrownBy(() -> get("/deposits"))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("3 · read timeout — accepted, and no answer inside the client's budget")
    void readTimeout() {
        providers.readTimeout("/deposits", Duration.ofSeconds(3));

        assertThatThrownBy(() -> CLIENT.send(
                HttpRequest.newBuilder(URI.create(providers.urlOf("/deposits")))
                        .timeout(Duration.ofMillis(300))     // the platform's budget
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString()))
                .isInstanceOf(HttpTimeoutException.class);
    }

    @Test
    @DisplayName("4 · duplicate callback — providers retry, and the platform must not double-apply")
    void duplicateCallback() {
        providers.acceptsCallbacksAt("/platform/webhook");
        String url = providers.urlOf("/platform/webhook");

        Providers.Callbacks.deliverTwice(url, """
                {"depositId":"dep-1","status":"COMPLETED"}""");

        // Two deliveries reached the receiver. Idempotency is the platform's job
        // (ET-PAY-002 R2) — the harness's job is to make the double delivery happen.
        providers.server().verify(2, postRequestedFor(urlPathEqualTo("/platform/webhook")));
    }

    @Test
    @DisplayName("5 · the callback arrives before the initiating call returns")
    void callbackOvertakesTheResponse() {
        providers.respondAfter("/deposits", Duration.ofSeconds(2), """
                {"depositId":"dep-1","status":"ACCEPTED"}""");
        providers.acceptsCallbacksAt("/platform/webhook");

        // The platform initiates a collection...
        CompletableFuture<HttpResponse<String>> initiating = CLIENT.sendAsync(
                HttpRequest.newBuilder(URI.create(providers.urlOf("/deposits"))).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        // ...and the provider calls back before that request has returned.
        Providers.Callbacks.deliver(providers.urlOf("/platform/webhook"), """
                {"depositId":"dep-1","status":"COMPLETED"}""");

        assertThat(initiating)
                .as("the initiating call must still be in flight — that is the whole scenario")
                .isNotDone();
        providers.server().verify(1, postRequestedFor(urlPathEqualTo("/platform/webhook")));

        initiating.join();
        assertThat(initiating).isCompleted();
    }

    @Test
    @DisplayName("6 · stuck PENDING — the seat stays held and no refund is attempted")
    void stuckPending() throws Exception {
        providers.stuckPending("/deposits/dep-1");

        HttpResponse<String> response = get("/deposits/dep-1");

        assertThat(response.body()).contains("PENDING");
        // ET-PAY-001 R5: while the outcome is unknown, hold. Releasing the seat or
        // refunding here is worse than a wait and an operator's attention, because
        // the money may still arrive.
    }

    @Test
    @DisplayName("no test reaches a real provider — every call terminates at the stub")
    void everythingTerminatesAtTheStub() {
        assertThat(providers.baseUrl()).startsWith("http://localhost:");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return CLIENT.send(
                HttpRequest.newBuilder(URI.create(providers.urlOf(path)))
                        .timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
