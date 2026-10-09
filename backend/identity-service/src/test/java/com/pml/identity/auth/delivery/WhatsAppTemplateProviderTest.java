package com.pml.identity.auth.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.identity.config.IdentityDeliveryProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The WhatsApp Cloud API call, against a stub HTTP server (ET-IDN-001-R2, R4). */
@Tag("L1")
@Tag("ET-IDN-001")
@DisplayName("ET-IDN-001-R2/R4 · the WhatsApp provider sends a template with the code, retries 5xx only, times out, and logs nothing sensitive")
class WhatsAppTemplateProviderTest {

    private static final String NUMBER = "+260971234567";
    private static final String RESPONSE_BODY = "{\"error\":{\"message\":\"provider-said-SECRETBODY\"}}";

    private HttpServer server;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile IntSupplier statusFor = () -> 200;
    private volatile long delayMillis = 0;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            paths.add(exchange.getRequestURI().getPath());
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            int status = statusFor.getAsInt();
            byte[] response = (status == 200 ? "{\"messages\":[{\"id\":\"wamid.X\"}]}" : RESPONSE_BODY).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private WhatsAppTemplateProvider provider(Duration timeout) {
        IdentityDeliveryProperties properties = new IdentityDeliveryProperties();
        var whatsapp = properties.getWhatsapp();
        whatsapp.setEnabled(true);
        whatsapp.setApiUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v21.0/");
        whatsapp.setPhoneNumberId("109876543210");
        whatsapp.setAccessToken("EAAG-test-access-token");
        whatsapp.setTemplateName("login_code");
        whatsapp.setTemplateLanguage("en_GB");
        whatsapp.setTimeout(timeout);
        return new WhatsAppTemplateProvider(properties, WebClient.builder(), new ObjectMapper());
    }

    @Test
    @DisplayName("posts a template message whose body parameter is the code, to the configured phone number id, with the bearer token")
    void request() throws Exception {
        provider(Duration.ofSeconds(2)).send(DeliveryChannel.WHATSAPP, NUMBER, "482913").block();

        assertThat(calls.get()).isEqualTo(1);
        assertThat(paths).containsExactly("/v21.0/109876543210/messages");
        assertThat(authorizations).containsExactly("Bearer EAAG-test-access-token");
        JsonNode body = new ObjectMapper().readTree(bodies.get(0));
        assertThat(body.get("messaging_product").asText()).isEqualTo("whatsapp");
        assertThat(body.get("to").asText()).isEqualTo("260971234567");
        assertThat(body.get("type").asText()).isEqualTo("template");
        assertThat(body.at("/template/name").asText()).isEqualTo("login_code");
        assertThat(body.at("/template/language/code").asText()).isEqualTo("en_GB");
        assertThat(body.at("/template/components/0/type").asText()).isEqualTo("body");
        assertThat(body.at("/template/components/0/parameters/0/type").asText()).isEqualTo("text");
        assertThat(body.at("/template/components/0/parameters/0/text").asText()).isEqualTo("482913");
    }

    @Test
    @DisplayName("the code is JSON-encoded, never concatenated: quotes and backslashes cannot break out of the string")
    void encoding() throws Exception {
        String hostile = "12\"},\"x\":{\"\\u00e9";
        provider(Duration.ofSeconds(2)).send(DeliveryChannel.WHATSAPP, NUMBER, hostile).block();
        JsonNode body = new ObjectMapper().readTree(bodies.get(0));
        assertThat(body.at("/template/components/0/parameters/0/text").asText()).isEqualTo(hostile);
        assertThat(body.has("x")).isFalse();
    }

    @Test
    @DisplayName("a 5xx is retried once and a recovered call succeeds")
    void retriesServerError() {
        AtomicInteger n = new AtomicInteger();
        statusFor = () -> n.incrementAndGet() == 1 ? 503 : 200;
        provider(Duration.ofSeconds(2)).send(DeliveryChannel.WHATSAPP, NUMBER, "111111").block();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("a persistent 5xx stops after the bounded retry and is a delivery failure")
    void boundedRetry() {
        statusFor = () -> 500;
        assertThatThrownBy(() -> provider(Duration.ofSeconds(2)).send(DeliveryChannel.WHATSAPP, NUMBER, "111111").block())
                .isInstanceOf(DeliveryFailedException.class);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("a 4xx is never retried")
    void noRetryOnClientError() {
        statusFor = () -> 400;
        assertThatThrownBy(() -> provider(Duration.ofSeconds(2)).send(DeliveryChannel.WHATSAPP, NUMBER, "111111").block())
                .isInstanceOf(DeliveryFailedException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a provider that does not answer within the timeout is a delivery failure, promptly")
    void timeout() {
        delayMillis = 3_000;
        long started = System.nanoTime();
        assertThatThrownBy(() -> provider(Duration.ofMillis(300)).send(DeliveryChannel.WHATSAPP, NUMBER, "111111").block())
                .isInstanceOf(DeliveryFailedException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("nothing sensitive is logged at any level: not the code, the number, the token or a response body")
    void noSensitiveLogging() {
        try (LogWatch logs = new LogWatch()) {
            provider(Duration.ofSeconds(2)).send(DeliveryChannel.WHATSAPP, NUMBER, "735190").block();
            statusFor = () -> 502;
            assertThatThrownBy(() -> provider(Duration.ofSeconds(2)).send(DeliveryChannel.WHATSAPP, NUMBER, "735190").block())
                    .isInstanceOf(DeliveryFailedException.class);
            delayMillis = 2_000;
            assertThatThrownBy(() -> provider(Duration.ofMillis(200)).send(DeliveryChannel.WHATSAPP, NUMBER, "735190").block())
                    .isInstanceOf(DeliveryFailedException.class);

            logs.assertNoneContains("735190", "260971234567", "971234567", "EAAG-test-access-token", "SECRETBODY");
        }
    }
}
