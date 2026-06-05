package com.pml.gateway.config;

import com.pml.shared.security.KeycloakJwtAuthenticationConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for the API Gateway.
 *
 * <h2>How JWT Validation Works</h2>
 * <pre>
 * 1. Request arrives with header: Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
 *
 * 2. Spring Security extracts the JWT token from the header
 *
 * 3. Fetches Keycloak's public keys from JWK Set URI (configured in application.yml):
 *    GET http://keycloak:8084/realms/event-ticketing/protocol/openid-connect/certs
 *    Response: { "keys": [{ "kty": "RSA", "kid": "...", "n": "...", "e": "..." }] }
 *    (Keys are cached for performance)
 *
 * 4. Validates the JWT:
 *    - Signature: Verifies token was signed by Keycloak's private key
 *    - Issuer (iss): Must match issuer-uri (http://keycloak:8084/realms/event-ticketing)
 *    - Expiration (exp): Token must not be expired
 *    - Not Before (nbf): Token must be valid now
 *
 * 5. If valid: Creates JwtAuthenticationToken with claims → populates SecurityContext
 *    If invalid: Returns 401 Unauthorized
 *
 * 6. KeycloakJwtAuthenticationConverter extracts roles from Keycloak's realm_access
 *    and resource_access claims into Spring Security GrantedAuthority objects
 * </pre>
 *
 * <h2>Path Security Rules</h2>
 * <pre>
 * PUBLIC (no JWT required):
 *   /realms/**                    → Keycloak OAuth2/OIDC endpoints
 *   /oauth2/**, /.well-known/**   → Legacy OAuth2 compatibility
 *   /api/webhooks/**              → Payment provider callbacks (signature-verified in service)
 *   /graphql/**, /graphiql/**     → GraphQL (auth delegated to subgraphs)
 *   /actuator/health, /info       → Health checks for Kubernetes
 *
 * BLOCKED (internal only):
 *   /api/internal/**              → Service-to-service communication only
 *
 * PROTECTED (JWT required):
 *   Everything else               → Must have valid JWT
 * </pre>
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Value("${keycloak.client-id:api-gateway}")
    private String keycloakClientId;

    /**
     * Configures the security filter chain for reactive (WebFlux) gateway.
     *
     * @param http ServerHttpSecurity builder (reactive equivalent of HttpSecurity)
     * @return SecurityWebFilterChain that defines security rules
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                // Disable CSRF: Gateway is stateless, uses JWT tokens not sessions
                // CSRF protection is for session-based auth where browser auto-sends cookies
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                // Enable CORS - uses corsConfigurationSource bean below
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Define authorization rules for paths
                .authorizeExchange(exchanges -> exchanges
                        // Allow CORS preflight requests (OPTIONS) without authentication
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // ─────────────────────────────────────────────────────────────
                        // PUBLIC ENDPOINTS - No authentication required
                        // ─────────────────────────────────────────────────────────────

                        // Keycloak OAuth2/OIDC: Login, token exchange, discovery
                        .pathMatchers("/realms/**").permitAll()
                        .pathMatchers("/oauth2/**", "/.well-known/**").permitAll()
                        .pathMatchers("/resources/**").permitAll()

                        // Payment webhooks: Called by payment provider, not users
                        // Security: Webhook signature verified in booking-service
                        .pathMatchers("/api/webhooks/**").permitAll()

                        // Health checks: Kubernetes liveness/readiness probes
                        .pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()

                        // Fallback endpoints: Circuit breaker fallbacks
                        .pathMatchers("/fallback/**").permitAll()

                        // GraphQL: Authentication delegated to subgraphs via Apollo Router
                        // Subgraphs read X-User-* headers added by OAuth2TokenRelayFilter
                        .pathMatchers("/graphql/**", "/graphiql/**", "/ws").permitAll()

                        // ─────────────────────────────────────────────────────────────
                        // BLOCKED ENDPOINTS - Internal service-to-service only
                        // ─────────────────────────────────────────────────────────────

                        // Internal APIs: NEVER expose to external clients
                        // Services communicate directly (not through gateway) for internal calls
                        .pathMatchers("/api/internal/**").denyAll()

                        // ─────────────────────────────────────────────────────────────
                        // PROTECTED ENDPOINTS - Valid JWT required
                        // ─────────────────────────────────────────────────────────────

                        // Everything else requires authentication
                        .anyExchange().authenticated()
                )

                // Configure as OAuth2 Resource Server (validates JWTs)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                // Custom converter: Extracts Keycloak roles into Spring authorities
                                // Converts realm_access.roles and resource_access.{client}.roles
                                // to GrantedAuthority objects (e.g., ROLE_ADMIN, ROLE_ORGANIZER)
                                .jwtAuthenticationConverter(
                                        KeycloakJwtAuthenticationConverter.reactiveConverter(keycloakClientId)
                                )
                        )
                )
                .build();
    }

    /**
     * CORS configuration source for Spring Security.
     * Defines allowed origins, methods, and headers for cross-origin requests.
     *
     * <p>This is required because Spring Security intercepts requests BEFORE
     * the Gateway's CORS filter runs. Without this, preflight OPTIONS requests
     * would be rejected with 401/403.</p>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allowed origins - must match frontend URLs
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000",   // Customer web
                "http://localhost:3003",   // Organization admin
                "http://localhost:3030",   // Platform admin
                "http://localhost:5173"    // Vite dev server
        ));

        // Allowed HTTP methods
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));

        // Allow all headers (Authorization, Content-Type, etc.)
        configuration.setAllowedHeaders(List.of("*"));

        // Allow credentials (cookies, authorization headers)
        configuration.setAllowCredentials(true);

        // Cache preflight response for 1 hour
        configuration.setMaxAge(3600L);

        // Expose headers that frontend can read
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "X-Request-Id",
                "X-Correlation-Id"
        ));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
