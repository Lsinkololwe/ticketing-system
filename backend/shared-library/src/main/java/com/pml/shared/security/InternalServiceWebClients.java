package com.pml.shared.security;

import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Builds the {@link WebClient} a service uses to call another service's {@code /api/internal/**}.
 *
 * <p>Every such endpoint requires the caller's own client-credentials token, so every internal
 * client is built here, where the token filter is applied once, rather than by each client class
 * remembering to. {@link InternalServiceClientAutoConfiguration} provides the instance.
 */
public final class InternalServiceWebClients {

    private final WebClient.Builder builder;
    private final ExchangeFilterFunction serviceToken;

    InternalServiceWebClients(WebClient.Builder builder, ExchangeFilterFunction serviceToken) {
        this.builder = builder;
        this.serviceToken = serviceToken;
    }

    /** Clients that send no token: for a service with no OAuth2 client registration, and for tests. */
    public static InternalServiceWebClients unauthenticated(WebClient.Builder builder) {
        return new InternalServiceWebClients(builder, null);
    }

    /** A client for the service at {@code baseUrl}, carrying this service's token when it has one. */
    public WebClient to(String baseUrl) {
        WebClient.Builder client = builder.clone().baseUrl(baseUrl);
        if (serviceToken != null) {
            client.filter(serviceToken);
        }
        return client.build();
    }
}
