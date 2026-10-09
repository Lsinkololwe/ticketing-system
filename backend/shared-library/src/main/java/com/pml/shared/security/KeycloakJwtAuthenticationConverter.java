package com.pml.shared.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import reactor.core.publisher.Mono;

/**
 * Factory for creating Keycloak-aware JWT Authentication Converters.
 *
 * <p>This class follows Spring Security best practices by using the built-in
 * {@link JwtAuthenticationConverter} with a custom authorities converter
 * ({@link KeycloakGrantedAuthoritiesConverter}) instead of implementing
 * the converter interface directly.</p>
 *
 * <h3>Usage in Security Configuration:</h3>
 * <pre>{@code
 * @Bean
 * public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
 *     return http
 *         .oauth2ResourceServer(oauth2 -> oauth2
 *             .jwt(jwt -> jwt
 *                 .jwtAuthenticationConverter(
 *                     KeycloakJwtAuthenticationConverter.reactiveConverter("my-client-id")
 *                 )
 *             )
 *         )
 *         .build();
 * }
 * }</pre>
 *
 * @see JwtAuthenticationConverter
 * @see KeycloakGrantedAuthoritiesConverter
 */
public final class KeycloakJwtAuthenticationConverter {

    /**
     * Default principal claim name used by Keycloak.
     * Falls back to 'sub' if 'preferred_username' is not present.
     */
    private static final String PRINCIPAL_CLAIM_NAME = "preferred_username";

    private KeycloakJwtAuthenticationConverter() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates a servlet-based JWT authentication converter for Keycloak tokens.
     *
     * <p>Use this for traditional Spring MVC (servlet) applications.</p>
     *
     * @return Configured JwtAuthenticationConverter
     */
    public static JwtAuthenticationConverter servletConverter() {
        return servletConverter(null);
    }

    /**
     * Creates a servlet-based JWT authentication converter for Keycloak tokens
     * with client-specific role extraction.
     *
     * @param clientId The Keycloak client ID for extracting client roles
     * @return Configured JwtAuthenticationConverter
     */
    public static JwtAuthenticationConverter servletConverter(String clientId) {
        KeycloakGrantedAuthoritiesConverter authoritiesConverter =
                new KeycloakGrantedAuthoritiesConverter(clientId);

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        jwtConverter.setPrincipalClaimName(PRINCIPAL_CLAIM_NAME);

        return jwtConverter;
    }

    /**
     * Converter whose authentication name is the application user id ({@code accountId} claim,
     * else {@code sub}) so that {@code Authentication.getName()} matches {@code User._id}.
     */
    private static Converter<Jwt, AbstractAuthenticationToken> identityConverter(String clientId) {
        KeycloakGrantedAuthoritiesConverter authorities = new KeycloakGrantedAuthoritiesConverter(clientId);
        return jwt -> {
            String name = AccountIdentity.userIdOf(jwt);
            if (name == null) {
                name = jwt.getClaimAsString(PRINCIPAL_CLAIM_NAME);
            }
            return new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                    jwt, authorities.convert(jwt), name);
        };
    }

    /**
     * Creates a reactive JWT authentication converter for Keycloak tokens.
     *
     * <p>Use this for Spring WebFlux (reactive) applications.</p>
     *
     * @return Configured reactive converter adapter
     */
    public static Converter<Jwt, Mono<AbstractAuthenticationToken>> reactiveConverter() {
        return reactiveConverter(null);
    }

    /**
     * Creates a reactive JWT authentication converter for Keycloak tokens
     * with client-specific role extraction.
     *
     * <p>Use this for Spring WebFlux (reactive) applications.</p>
     *
     * @param clientId The Keycloak client ID for extracting client roles
     * @return Configured reactive converter adapter
     */
    public static Converter<Jwt, Mono<AbstractAuthenticationToken>> reactiveConverter(String clientId) {
        Converter<Jwt, AbstractAuthenticationToken> converter = identityConverter(clientId);
        return jwt -> Mono.justOrEmpty(converter.convert(jwt));
    }
}
