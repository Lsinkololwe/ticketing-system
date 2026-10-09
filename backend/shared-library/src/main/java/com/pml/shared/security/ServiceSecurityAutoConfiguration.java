package com.pml.shared.security;

import com.pml.shared.security.revocation.RevocationRequestGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.List;

/**
 * Installs {@link ServiceSecurity} in a domain service that declares no chain of its own.
 *
 * <p>Ordered before Spring Boot's reactive security, whose default chain would otherwise win.
 * Configured by the keys every service already states:
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}, {@code keycloak.client-id},
 * {@code keycloak.trusted-issuers}, {@code keycloak.expected-audiences}, and
 * {@code platform.security.public-paths}.
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration"})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass({ServerHttpSecurity.class, ReactiveJwtDecoder.class})
@ConditionalOnProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri")
public class ServiceSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ServiceSecurity serviceSecurity(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${keycloak.trusted-issuers:}") String trustedIssuersCsv,
            @Value("${keycloak.client-id:${spring.application.name}}") String clientId,
            @Value("${keycloak.expected-audiences:}") String expectedAudiencesCsv,
            Environment environment) {
        // Bound, not @Value: a YAML list reaches the environment as indexed keys, which @Value
        // cannot read — the list would silently resolve to empty and every public path would 401.
        List<String> publicPaths = Binder.get(environment)
                .bind("platform.security.public-paths", Bindable.listOf(String.class))
                .orElse(List.of());
        return new ServiceSecurity(issuerUri, trustedIssuersCsv, clientId, expectedAudiencesCsv,
                publicPaths.stream().filter(path -> !path.isBlank()).toList());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(SecurityWebFilterChain.class)
    @EnableWebFluxSecurity
    @EnableReactiveMethodSecurity
    static class ServiceChain {

        @Bean
        @ConditionalOnMissingBean
        public ReactiveJwtDecoder reactiveJwtDecoder(ServiceSecurity security) {
            return security.reactiveJwtDecoder();
        }

        @Bean
        public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ServiceSecurity security,
                                                             ObjectProvider<RevocationRequestGuard> revocation) {
            // After authentication, so only a validly signed token is looked up.
            revocation.ifAvailable(guard -> http.addFilterAfter(guard.webFilter(), SecurityWebFiltersOrder.AUTHENTICATION));
            return security.securityWebFilterChain(http);
        }
    }
}
