package com.pml.identity.security;

import com.pml.shared.security.KeycloakJwtAuthenticationConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * OAuth2 Resource Server Configuration for Identity Service.
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

    @Value("${keycloak.client-id:identity-service}")
    private String keycloakClientId;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchanges -> exchanges
                        // Health endpoints - always public
                        .pathMatchers("/actuator/**").permitAll()
                        // GraphiQL UI - development only
                        .pathMatchers("/graphiql/**").permitAll()
                        // Debug endpoints - development only (TODO: secure or remove in production)
                        .pathMatchers("/api/debug/**").permitAll()
                        // Auth endpoints - public (login, register handled by Keycloak)
                        .pathMatchers("/api/auth/**").permitAll()
                        // Internal service-to-service calls - require internal scope
                        .pathMatchers("/api/internal/**").hasAnyAuthority("SCOPE_internal-read", "SCOPE_internal-write", "ROLE_INTERNAL_SERVICE", "ROLE_SYSTEM")
                        // GraphQL - require authentication so JWT is parsed and available to resolvers
                        // Fine-grained access control is handled at resolver level with @PreAuthorize
                        .pathMatchers("/graphql/**").authenticated()
                        // REST API - require authentication so JWT is parsed
                        .pathMatchers("/api/v1/**").authenticated()
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
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .build();
    }

    /**
     * CORS configuration for file upload endpoints.
     *
     * <p>Allows cross-origin requests from frontend applications for:
     * <ul>
     *   <li>REST API endpoints (document uploads)</li>
     *   <li>GraphQL endpoint</li>
     * </ul>
     *
     * <p><b>Security Note</b>: In production, replace allowedOrigins with specific
     * frontend URLs (e.g., https://organizer.example.com).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allow all origins in development (TODO: restrict in production)
        configuration.setAllowedOriginPatterns(List.of("*"));

        // Allow common HTTP methods for REST APIs
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Allow common headers including Authorization
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With"
        ));

        // Allow credentials (cookies, authorization headers)
        configuration.setAllowCredentials(true);

        // Cache preflight response for 1 hour
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
