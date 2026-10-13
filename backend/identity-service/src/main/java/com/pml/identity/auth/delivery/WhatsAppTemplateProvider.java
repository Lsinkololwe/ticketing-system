package com.pml.identity.auth.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pml.identity.config.IdentityDeliveryProperties;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Sends the sign-in code to a buyer's WhatsApp as a template message carrying the code as the
 * template body parameter.
 *
 * <p>The request body is built with Jackson (the code and number are JSON-encoded, never
 * concatenated). The whole send, retries included, must finish within
 * {@code identity.delivery.whatsapp.timeout} (5 seconds by default): the buyer is waiting on the
 * screen, so a slow provider is a failed delivery, not a long wait. A 5xx or a connection error is
 * retried once while budget is left; a 4xx and any kind of timeout are never retried. Nothing
 * sensitive is logged: not the number, not the code, not the response body - only the HTTP
 * status.</p>
 */
@Component
public class WhatsAppTemplateProvider implements CodeDeliveryProvider {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppTemplateProvider.class);
    private static final int RETRIES = 1;

    private final IdentityDeliveryProperties.Whatsapp config;
    private final ObjectMapper json;
    private final WebClient client;

    @Autowired
    public WhatsAppTemplateProvider(IdentityDeliveryProperties properties, WebClient.Builder builder, ObjectMapper json) {
        this.config = properties.getWhatsapp();
        this.json = json;
        Duration timeout = config.getTimeout();
        HttpClient http = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) Math.min(timeout.toMillis(), Integer.MAX_VALUE))
                .responseTimeout(timeout);
        this.client = builder.clone().clientConnector(new ReactorClientHttpConnector(http)).build();
    }

    @Override
    public boolean supports(DeliveryChannel channel) {
        return channel == DeliveryChannel.WHATSAPP;
    }

    @Override
    public boolean enabled() {
        return config.isEnabled();
    }

    @Override
    public Mono<Void> send(DeliveryChannel channel, String recipient, String code) {
        return post(() -> body(recipient, code));
    }

    /** Needs {@code identity.delivery.whatsapp.notice-template-name} (a template with no parameters); without it, silent. */
    @Override
    public Mono<Void> sendNotice(DeliveryChannel channel, String recipient, ContactNotice notice) {
        String name = config.getNoticeTemplateName();
        if (name == null || name.isBlank()) {
            return Mono.empty();
        }
        return post(() -> {
            ObjectNode root = json.createObjectNode();
            root.put("messaging_product", "whatsapp");
            root.put("to", recipient.startsWith("+") ? recipient.substring(1) : recipient);
            root.put("type", "template");
            ObjectNode template = root.putObject("template");
            template.put("name", name + "_" + notice.name().toLowerCase(java.util.Locale.ROOT));
            template.putObject("language").put("code", config.getTemplateLanguage());
            return root.toString();
        });
    }

    private Mono<Void> post(java.util.function.Supplier<String> payload) {
        return Mono.defer(() -> {
            String url = trimTrailingSlash(config.getApiUrl()) + "/" + config.getPhoneNumberId() + "/messages";
            // As bytes: the String encoder would log the body (with the code) at DEBUG.
            byte[] body = payload.get().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return client.post().uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchangeToMono(response -> {
                        if (response.statusCode().is2xxSuccessful()) {
                            return response.releaseBody();
                        }
                        int status = response.statusCode().value();
                        return response.releaseBody().then(Mono.error(new ProviderStatus(status)));
                    })
                    .retryWhen(Retry.fixedDelay(RETRIES, Duration.ofMillis(200))
                            .filter(WhatsAppTemplateProvider::retryable)
                            .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                    // One budget for the first call, the pause and the retry together; leaving it
                    // cancels whatever request is still in flight.
                    .timeout(config.getTimeout())
                    .then();
        }).onErrorMap(error -> {
            log.warn("WhatsApp delivery failed: {}", describe(error));
            return new DeliveryFailedException("whatsapp delivery failed");
        });
    }

    String body(String recipient, String code) {
        ObjectNode root = json.createObjectNode();
        root.put("messaging_product", "whatsapp");
        root.put("to", recipient.startsWith("+") ? recipient.substring(1) : recipient);
        root.put("type", "template");
        ObjectNode template = root.putObject("template");
        template.put("name", config.getTemplateName());
        template.putObject("language").put("code", config.getTemplateLanguage());
        ArrayNode components = template.putArray("components");
        ObjectNode bodyComponent = components.addObject();
        bodyComponent.put("type", "body");
        bodyComponent.putArray("parameters").addObject().put("type", "text").put("text", code);
        return root.toString();
    }

    private static boolean retryable(Throwable error) {
        if (error instanceof ProviderStatus status) {
            return status.status >= 500;
        }
        // A connection-level failure is worth one more try; a timeout is not (the budget is
        // already spent). The client wraps the netty timeouts, so look through the causes.
        return !isTimeout(error);
    }

    private static boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof java.util.concurrent.TimeoutException
                    || cause instanceof io.netty.handler.timeout.TimeoutException
                    || cause instanceof io.netty.channel.ConnectTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static String describe(Throwable error) {
        if (error instanceof ProviderStatus status) {
            return "HTTP " + status.status;
        }
        return error.getClass().getSimpleName();
    }

    private static String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** A non-2xx answer. Carries the status only. */
    private static final class ProviderStatus extends RuntimeException {
        private final int status;

        ProviderStatus(int status) {
            super("status " + status, null, false, false);
            this.status = status;
        }
    }
}
