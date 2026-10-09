package com.pml.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Wires {@link InternalServiceWebClients} to this service's client-credentials registration.
 *
 * <p>The registration is {@code platform.internal-client.registration-id}, which defaults to
 * {@code spring.application.name} — each service registers itself under its own name. A token is
 * fetched on first use, cached until it expires, and refreshed without the caller noticing.
 *
 * <p>With no client registration at all the clients send no token, and say so once at startup:
 * the called service then refuses with 401, which is the correct outcome and a clear one.
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration"})
@ConditionalOnClass({WebClient.class, ReactiveClientRegistrationRepository.class})
public class InternalServiceClientAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(InternalServiceClientAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public InternalServiceWebClients internalServiceWebClients(
            WebClient.Builder builder,
            ObjectProvider<ReactiveClientRegistrationRepository> registrations,
            Environment environment) {
        ReactiveClientRegistrationRepository repository = registrations.getIfAvailable();
        if (repository == null) {
            log.warn("[InternalClients] No OAuth2 client registration — calls to other services "
                    + "carry no token and /api/internal/** will refuse them");
            return InternalServiceWebClients.unauthenticated(builder);
        }
        String registrationId = environment.getProperty("platform.internal-client.registration-id",
                environment.getProperty("spring.application.name", ""));

        var manager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                repository, new InMemoryReactiveOAuth2AuthorizedClientService(repository));
        manager.setAuthorizedClientProvider(ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build());
        var serviceToken = new ServerOAuth2AuthorizedClientExchangeFilterFunction(manager);
        serviceToken.setDefaultClientRegistrationId(registrationId);
        return new InternalServiceWebClients(builder, serviceToken);
    }
}
