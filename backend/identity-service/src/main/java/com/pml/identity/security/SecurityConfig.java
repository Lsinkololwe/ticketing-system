package com.pml.identity.security;

import com.pml.shared.security.AudienceRequirement;
import com.pml.shared.security.KeycloakJwtAuthenticationConverter;
import com.pml.shared.security.PlatformResourceServer;
import com.pml.shared.security.revocation.RevocationRequestGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
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

    /** Exact origins allowed to call this service from a browser; empty allows none. See {@link #corsConfigurationSource}. */
    @Value("${identity.cors.allowed-origins:}")
    private List<String> allowedOrigins;

    @Value("${keycloak.client-id:myticketzm-identity-service}")
    private String keycloakClientId;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8084/realms/myticketzm}")
    private String issuerUri;

    /**
     * Additional trusted realm issuers (comma-separated), e.g. the platform-admin realm
     * {@code http://localhost:8084/realms/myticketzm-admin}, which {@code application.yml}
     * supplies by default. Tokens are validated per their {@code iss} claim; one from any other
     * issuer is rejected.
     */
    @Value("${keycloak.trusted-issuers:}")
    private String trustedIssuersCsv;

    /**
     * Audiences required in the {@code aud} claim (comma-separated). Blank disables the check,
     * which {@link PlatformResourceServer} logs at WARN — see there for why it is not defaulted
     * to this service's client id.
     */
    @Value("${keycloak.expected-audiences:}")
    private String expectedAudiencesCsv;

    /**
     * How long a fetched signing-key set is trusted before it is fetched again. Bounded to
     * {@code 0 < ttl <= PT15M}; a value outside that stops startup.
     */
    @Value("${keycloak.jwks-cache-ttl:PT5M}")
    private java.time.Duration jwksCacheTtl;

    /** Refuses revoked tokens on every request; absent when revocation is switched off. */
    @Autowired(required = false)
    private RevocationRequestGuard revocationRequestGuard;

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        return PlatformResourceServer.decoder(issuerUri, expectedAudiencesCsv, jwksCacheTtl);
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, Environment environment) {
        if (revocationRequestGuard != null) {
            // After authentication, so only a validly signed token is looked up.
            http.addFilterAfter(revocationRequestGuard.webFilter(), SecurityWebFiltersOrder.AUTHENTICATION);
        }
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchanges -> exchanges
                        // Probes only; every other actuator endpoint needs a token
                        .pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // Local file store (development): the signature in the query string is the credential
                        .pathMatchers("/local-storage/**").permitAll()
                        // GraphiQL UI - development only
                        .pathMatchers("/graphiql/**").permitAll()
                        // Buyer-identity API (challenges, proofs, handles): POST needs internal-write, GET internal-read.
                        .pathMatchers(org.springframework.http.HttpMethod.GET, "/api/internal/auth/**").hasAuthority("SCOPE_internal-read")
                        .pathMatchers(org.springframework.http.HttpMethod.POST, "/api/internal/auth/**").hasAuthority("SCOPE_internal-write")
                        .pathMatchers("/api/internal/auth/**").denyAll()
                        // Internal service-to-service calls, read and write split by method so a
                        // read-scoped caller cannot reach the same prefix with a different verb.
                        .pathMatchers(org.springframework.http.HttpMethod.GET, "/api/internal/**")
                            .hasAnyAuthority("SCOPE_internal-read", "ROLE_INTERNAL_SERVICE", "ROLE_SYSTEM")
                        .pathMatchers("/api/internal/**")
                            .hasAnyAuthority("SCOPE_internal-write", "ROLE_INTERNAL_SERVICE", "ROLE_SYSTEM")
                        // GraphQL - require authentication so JWT is parsed and available to resolvers
                        // Fine-grained access control is handled at resolver level with @PreAuthorize
                        // The one exception: a tokenless POST whose every top-level field is on
                        // PublicOperationFilter's allowlist (the filter marks the exchange; it admits nothing else)
                        .matchers(PublicOperationFilter.isPublicOperation()).permitAll()
                        .pathMatchers("/graphql/**").authenticated()
                        // REST API - require authentication so JWT is parsed
                        .pathMatchers("/api/v1/**").authenticated()
                        // All other endpoints require authentication
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(PlatformResourceServer.jwt(
                        issuerUri, trustedIssuersCsv, keycloakClientId, expectedAudiencesCsv,
                        AudienceRequirement.outsideLocalOrTest(environment), jwksCacheTtl))
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
     * <p><b>Security Note</b>: the allowed origins are {@code identity.cors.allowed-origins}, a
     * comma-separated list of exact origins (e.g. https://organizer.example.com). There is no
     * wildcard and no default.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Only the origins named in configuration. With credentials allowed, a wildcard would let any
        // site a signed-in person visits call this API as them. An empty list allows no cross-origin call.
        configuration.setAllowedOrigins(allowedOrigins);

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
