package com.pml.booking.config.security;

import com.pml.shared.security.KeycloakJwtAuthenticationConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * OAuth2 Resource Server Configuration for Booking Service.
 *
 * <p>Configures Spring Security with Keycloak JWT validation following
 * official Spring Security best practices.</p>
 *
 * <h2>How Token Validation Works</h2>
 * <ol>
 *   <li>JWKS endpoint discovery from {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}</li>
 *   <li>Signature verification using Keycloak's public keys</li>
 *   <li>Standard claims validation (exp, iss, etc.)</li>
 *   <li>Role extraction via {@link KeycloakJwtAuthenticationConverter}</li>
 * </ol>
 *
 * @see <a href="https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/jwt.html">
 *      Spring Security OAuth2 Resource Server JWT</a>
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Value("${keycloak.client-id:booking-service}")
    private String keycloakClientId;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8084/realms/myticketzm}")
    private String issuerUri;

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        return ReactiveJwtDecoders.fromIssuerLocation(issuerUri);
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // Health endpoints - always public
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        // GraphiQL UI - development only
                        .pathMatchers("/graphiql/**").permitAll()
                        // Payment webhooks - external payment providers (signature verified in handler)
                        .pathMatchers("/api/webhooks/payment/**").permitAll()
                        // Internal service-to-service calls require internal scope
                        .pathMatchers("/api/internal/**").hasAnyAuthority("SCOPE_internal-read", "SCOPE_internal-write", "ROLE_INTERNAL_SERVICE", "ROLE_SYSTEM")
                        // GraphQL - authentication handled at resolver level with @PreAuthorize
                        .pathMatchers("/graphql/**").permitAll()
                        // All other endpoints require authentication
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        KeycloakJwtAuthenticationConverter.reactiveConverter(keycloakClientId)
                                )
                        )
                )
                .build();
    }
}
