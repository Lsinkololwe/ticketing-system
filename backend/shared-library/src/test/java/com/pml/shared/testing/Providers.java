package com.pml.shared.testing;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * Every external provider, stubbed — and stubbed <strong>failure-first</strong>.
 *
 * <h2>Why failure-first</h2>
 * PawaPay's happy path is the case that already works; nobody ships a
 * checkout that cannot take a successful payment. What breaks the money is the other six:
 * a 503 during on-sale, a connection reset, a read timeout, the same callback delivered
 * twice, a callback that arrives <em>before</em> the call that triggered it has returned,
 * and a payment that simply sits {@code PENDING} past the platform's own patience.
 *
 * <p>Each of those has a named factory here, so a test asks for the scenario by name rather
 * than hand-rolling a stub and quietly getting it slightly wrong.
 *
 * <h2>No test reaches a real provider</h2>
 * PawaPay, the WhatsApp and SMS senders, S3 and the Keycloak Admin API all terminate here.
 *
 * <p><strong>Limitation:</strong> the response bodies below are shaped placeholders — they are
 * structurally right and they are not recordings from a real sandbox. Fixtures recorded
 * against the PawaPay sandbox would be stronger evidence than anything this class provides.
 */
public final class Providers implements AutoCloseable {

    /** The providers that terminate here. */
    public enum Provider {
        PAWAPAY, WHATSAPP, SMS, S3, KEYCLOAK_ADMIN
    }

    private final WireMockServer server;

    private Providers(WireMockServer server) {
        this.server = server;
    }

    /** Starts a stub server on a random free port. */
    public static Providers start() {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        return new Providers(server);
    }

    public String baseUrl() {
        return server.baseUrl();
    }

    public String urlOf(String path) {
        return server.baseUrl() + path;
    }

    public WireMockServer server() {
        return server;
    }

    public Providers reset() {
        server.resetAll();
        return this;
    }

    @Override
    public void close() {
        server.stop();
    }

    // ------------------------------------------------- the six failure modes

    /** 1 — the provider is up and refusing. The commonest on-sale failure. */
    public Providers serviceUnavailable(String path) {
        server.stubFor(any(urlPathEqualTo(path))
                .willReturn(aResponse().withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"errorCode":"SERVICE_UNAVAILABLE","message":"upstream temporarily unavailable"}""")));
        return this;
    }

    /**
     * 2 — the connection dies mid-flight. Distinct from a 503: there is no response at all,
     * so the platform cannot know whether the provider received the request. Every
     * "did the money move?" ambiguity in the corpus starts here.
     */
    public Providers connectionReset(String path) {
        server.stubFor(any(urlPathEqualTo(path))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        return this;
    }

    /** 3 — the provider accepts and never answers within the client's budget. */
    public Providers readTimeout(String path, Duration beyondClientBudget) {
        server.stubFor(any(urlPathEqualTo(path))
                .willReturn(aResponse().withStatus(200)
                        .withFixedDelay((int) beyondClientBudget.toMillis())
                        .withBody("""
                                {"status":"ACCEPTED"}""")));
        return this;
    }

    /**
     * 6 — the status never leaves {@code PENDING}. The never-answering provider:
     * the seat stays held, the payment escalates, and no refund is attempted, because the
     * money may still arrive.
     */
    public Providers stuckPending(String path) {
        server.stubFor(any(urlPathEqualTo(path))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":"PENDING","depositId":"stuck-forever"}""")));
        return this;
    }

    /** Responds only after a delay — the lever the callback-ordering scenarios pull. */
    public Providers respondAfter(String path, Duration delay, String body) {
        server.stubFor(any(urlPathEqualTo(path))
                .willReturn(aResponse().withStatus(200)
                        .withFixedDelay((int) delay.toMillis())
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
        return this;
    }

    /** The happy path, for the cases that genuinely need one. */
    public Providers succeeds(String path, String body) {
        server.stubFor(any(urlPathEqualTo(path))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
        return this;
    }

    /** Accepts callbacks so a test can assert what the platform was sent, and in what order. */
    public Providers acceptsCallbacksAt(String path) {
        server.stubFor(post(urlPathEqualTo(path)).willReturn(aResponse().withStatus(200)));
        return this;
    }

    // ------------------------------------------------------ callback delivery

    /**
     * Drives the two inbound failure modes. These are not stubs — the platform is the
     * receiver, not the caller — so they are deliveries a test performs against it.
     */
    public static final class Callbacks {

        private static final HttpClient CLIENT = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        private Callbacks() {
        }

        /** 4 — the same callback, delivered twice. Providers retry; the platform must not double-apply. */
        public static void deliverTwice(String url, String body) {
            deliver(url, body);
            deliver(url, body);
        }

        /**
         * 5 — the callback arrives before the initiating call returns.
         *
         * <p>Not exotic: it is the normal case on a fast provider. An implementation that
         * assumes the initiating response lands first treats this as an unmatched orphan and
         * leaves the buyer waiting on a poll.
         */
        public static int deliver(String url, String body) {
            try {
                HttpResponse<String> response = CLIENT.send(
                        HttpRequest.newBuilder(URI.create(url))
                                .header("Content-Type", "application/json")
                                .timeout(Duration.ofSeconds(5))
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                return response.statusCode();
            } catch (Exception e) {
                throw new IllegalStateException("callback delivery to " + url + " failed", e);
            }
        }
    }
}
