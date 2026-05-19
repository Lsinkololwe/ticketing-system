package com.pml.shared.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
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
        JwtAuthenticationConverter servletConverter = servletConverter(clientId);
        return new ReactiveJwtAuthenticationConverterAdapter(servletConverter);
    }

    /**
     * Creates a custom configured reactive converter with specific options.
     *
     * @param clientId           Keycloak client ID (can be null)
     * @param extractRealmRoles  Whether to extract realm-level roles
     * @param extractClientRoles Whether to extract client-level roles
     * @param extractScopes      Whether to extract scopes
     * @return Configured reactive converter adapter
     */
    public static Converter<Jwt, Mono<AbstractAuthenticationToken>> reactiveConverterWithOptions(
            String clientId,
            boolean extractRealmRoles,
            boolean extractClientRoles,
            boolean extractScopes) {

        KeycloakGrantedAuthoritiesConverter authoritiesConverter =
                new KeycloakGrantedAuthoritiesConverter(clientId);
        authoritiesConverter.setExtractRealmRoles(extractRealmRoles);
        authoritiesConverter.setExtractClientRoles(extractClientRoles);
        authoritiesConverter.setExtractScopes(extractScopes);

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        jwtConverter.setPrincipalClaimName(PRINCIPAL_CLAIM_NAME);

        return new ReactiveJwtAuthenticationConverterAdapter(jwtConverter);
    }
}
