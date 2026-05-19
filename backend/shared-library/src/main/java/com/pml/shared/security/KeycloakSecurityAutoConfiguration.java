package com.pml.shared.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Auto-configuration for Keycloak security integration.
 *
 * <p>This configuration provides:</p>
 * <ul>
 *   <li>Keycloak-aware JWT authentication converter</li>
 *   <li>Audience validation for JWT tokens</li>
 *   <li>Configurable role/scope extraction</li>
 * </ul>
 *
 * <p>Enable this configuration by adding to application.yml:</p>
 * <pre>{@code
 * keycloak:
 *   security:
 *     enabled: true
 *     client-id: my-service
 *     expected-audiences:
 *       - my-service
 * }</pre>
 *
 * <p>This works alongside Spring Boot's standard OAuth2 Resource Server
 * configuration:</p>
 * <pre>{@code
 * spring:
 *   security:
 *     oauth2:
 *       resourceserver:
 *         jwt:
 *           issuer-uri: http://localhost:8084/realms/my-realm
 * }</pre>
 */
@Configuration
@ConditionalOnClass({Jwt.class, NimbusJwtDecoder.class})
@ConditionalOnProperty(prefix = "keycloak.security", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KeycloakSecurityProperties.class)
public class KeycloakSecurityAutoConfiguration {

    private final KeycloakSecurityProperties properties;

    public KeycloakSecurityAutoConfiguration(KeycloakSecurityProperties properties) {
        this.properties = properties;
    }

    /**
     * Provides a reactive JWT authentication converter for WebFlux applications.
     *
     * <p>This bean is only created if no other converter is defined.</p>
     */
    @Bean
    @ConditionalOnMissingBean(name = "keycloakReactiveJwtAuthenticationConverter")
    public Converter<Jwt, Mono<AbstractAuthenticationToken>> keycloakReactiveJwtAuthenticationConverter() {
        return KeycloakJwtAuthenticationConverter.reactiveConverterWithOptions(
                properties.getClientId(),
                properties.isExtractRealmRoles(),
                properties.isExtractClientRoles(),
                properties.isExtractScopes()
        );
    }

    /**
     * Creates an audience validator for JWT tokens.
     *
     * <p>Validates that the token's 'aud' claim contains at least one
     * of the expected audiences configured in properties.</p>
     *
     * @return OAuth2TokenValidator for audience validation, or null if no audiences configured
     */
    @Bean
    @ConditionalOnMissingBean(name = "audienceValidator")
    public OAuth2TokenValidator<Jwt> audienceValidator() {
        List<String> expectedAudiences = properties.getExpectedAudiences();

        if (expectedAudiences == null || expectedAudiences.isEmpty()) {
            // No audience validation required
            return null;
        }

        return new JwtClaimValidator<>(
                JwtClaimNames.AUD,
                aud -> {
                    if (aud == null) {
                        return false;
                    }
                    if (aud instanceof String) {
                        return expectedAudiences.contains(aud);
                    }
                    if (aud instanceof Collection) {
                        Collection<?> audiences = (Collection<?>) aud;
                        return audiences.stream()
                                .anyMatch(a -> expectedAudiences.contains(a.toString()));
                    }
                    return false;
                }
        );
    }

    /**
     * Creates a combined token validator with default validations plus audience check.
     *
     * <p>This can be used to customize the JwtDecoder:</p>
     * <pre>{@code
     * @Bean
     * public ReactiveJwtDecoder jwtDecoder(OAuth2TokenValidator<Jwt> combinedValidator) {
     *     NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
     *         .withJwkSetUri(jwkSetUri)
     *         .build();
     *     decoder.setJwtValidator(combinedValidator);
     *     return decoder;
     * }
     * }</pre>
     */
    @Bean
    @ConditionalOnMissingBean(name = "combinedJwtValidator")
    public OAuth2TokenValidator<Jwt> combinedJwtValidator(
            org.springframework.beans.factory.ObjectProvider<OAuth2TokenValidator<Jwt>> audienceValidatorProvider,
            org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties oAuth2Properties) {

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();

        // Add default validators (issuer, expiry, etc.)
        String issuerUri = oAuth2Properties.getJwt().getIssuerUri();
        if (issuerUri != null && !issuerUri.isEmpty()) {
            validators.add(JwtValidators.createDefaultWithIssuer(issuerUri));
        } else {
            validators.add(JwtValidators.createDefault());
        }

        // Add audience validator if configured
        OAuth2TokenValidator<Jwt> audienceValidator = audienceValidatorProvider.getIfAvailable();
        if (audienceValidator != null) {
            validators.add(audienceValidator);
        }

        return new DelegatingOAuth2TokenValidator<>(validators);
    }
}
