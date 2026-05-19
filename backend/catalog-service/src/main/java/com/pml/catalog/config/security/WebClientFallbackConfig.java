package com.pml.catalog.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Fallback WebClient Configuration for Catalog Service.
 * Provides basic WebClient beans when OAuth2 client is not configured.
 *
 * <p>This configuration activates when the OAuth2 WebClient beans are not available,
 * typically in development environments without Keycloak configured.</p>
 *
 * <h2>OWASP Note</h2>
 * <p>This fallback does NOT include authentication tokens. It should only be used
 * in development environments where service-to-service auth is not required.
 * In production, the OAuth2ClientConfig should be active.</p>
 */
@Configuration
public class WebClientFallbackConfig {

    /**
     * Fallback WebClient for calling Identity Service without OAuth2.
     * Only created if the OAuth2 version is not available.
     */
    @Bean("identityServiceWebClient")
    @ConditionalOnMissingBean(name = "identityServiceWebClient")
    public WebClient identityServiceWebClientFallback(
            @Value("${services.identity.url:http://localhost:8083}") String identityServiceUrl) {

        return WebClient.builder()
                .baseUrl(identityServiceUrl)
                .build();
    }

    /**
     * Fallback WebClient for calling Booking Service without OAuth2.
     * Only created if the OAuth2 version is not available.
     */
    @Bean("bookingServiceWebClient")
    @ConditionalOnMissingBean(name = "bookingServiceWebClient")
    public WebClient bookingServiceWebClientFallback(
            @Value("${services.booking.url:http://localhost:8082}") String bookingServiceUrl) {

        return WebClient.builder()
                .baseUrl(bookingServiceUrl)
                .build();
    }

    /**
     * Fallback general OAuth2 WebClient.
     * Only created if the OAuth2 version is not available.
     */
    @Bean("oauth2WebClient")
    @ConditionalOnMissingBean(name = "oauth2WebClient")
    public WebClient oauth2WebClientFallback() {
        return WebClient.builder().build();
    }
}
