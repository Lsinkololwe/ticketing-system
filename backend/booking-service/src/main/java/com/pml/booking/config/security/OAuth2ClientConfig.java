package com.pml.booking.config.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * OAuth2 Client Configuration for Booking Service.
 * Configures WebClient with client_credentials grant for service-to-service authentication.
 * This configuration is only active when OAuth2 client registration is available.
 */
@Configuration
@ConditionalOnBean(ReactiveClientRegistrationRepository.class)
public class OAuth2ClientConfig {

    /**
     * ReactiveOAuth2AuthorizedClientManager for managing OAuth2 authorized clients.
     */
    @Bean
    public ReactiveOAuth2AuthorizedClientManager authorizedClientManager(
            ReactiveClientRegistrationRepository clientRegistrationRepository) {

        ReactiveOAuth2AuthorizedClientProvider authorizedClientProvider =
                ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()
                        .refreshToken()
                        .build();

        AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientManager =
                new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                        clientRegistrationRepository,
                        new InMemoryReactiveOAuth2AuthorizedClientService(clientRegistrationRepository));

        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        return authorizedClientManager;
    }

    /**
     * OAuth2-enabled WebClient for calling other services with client_credentials token.
     * Uses the booking-service client registration by default.
     */
    @Bean("oauth2WebClient")
    public WebClient oauth2WebClient(ReactiveOAuth2AuthorizedClientManager authorizedClientManager) {
        ServerOAuth2AuthorizedClientExchangeFilterFunction oauth2Filter =
                new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);

        // Set default client registration to use for requests
        oauth2Filter.setDefaultClientRegistrationId("booking-service");

        return WebClient.builder()
                .filter(oauth2Filter)
                .build();
    }

    /**
     * WebClient for calling Catalog Service with OAuth2 token.
     */
    @Bean("catalogServiceWebClient")
    public WebClient catalogServiceWebClient(
            ReactiveOAuth2AuthorizedClientManager authorizedClientManager,
            @org.springframework.beans.factory.annotation.Value("${services.catalog.url:http://localhost:8081}") String catalogServiceUrl) {

        ServerOAuth2AuthorizedClientExchangeFilterFunction oauth2Filter =
                new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);

        oauth2Filter.setDefaultClientRegistrationId("booking-service");

        return WebClient.builder()
                .baseUrl(catalogServiceUrl)
                .filter(oauth2Filter)
                .build();
    }

    /**
     * WebClient for calling Identity Service with OAuth2 token.
     */
    @Bean("identityServiceWebClient")
    public WebClient identityServiceWebClient(
            ReactiveOAuth2AuthorizedClientManager authorizedClientManager,
            @org.springframework.beans.factory.annotation.Value("${services.identity.url:http://localhost:8083}") String identityServiceUrl) {

        ServerOAuth2AuthorizedClientExchangeFilterFunction oauth2Filter =
                new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);

        oauth2Filter.setDefaultClientRegistrationId("booking-service");

        return WebClient.builder()
                .baseUrl(identityServiceUrl)
                .filter(oauth2Filter)
                .build();
    }
}
