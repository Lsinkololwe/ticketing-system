package com.pml.shared.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.time.Duration;
import java.util.List;

/**
 * The resource-server security every domain service runs: Keycloak JWTs validated in-process,
 * internal endpoints for service accounts only, everything else authenticated.
 *
 * <p>The gateway permits {@code /graphql/**} and runs in a separate process, so each service's
 * chain stands alone — a token it accepts is a token that can act on its data. Authorization
 * beyond "authenticated" is per operation, with {@code @PreAuthorize}. Built by
 * {@link ServiceSecurityAutoConfiguration}; a service with needs of its own defines its own
 * {@link SecurityWebFilterChain} and this one steps aside.
 *
 * @param issuerUri           the primary realm's issuer
 * @param trustedIssuersCsv   further realms whose tokens are accepted, each validated per its own
 *                            {@code iss}
 * @param clientId            this service's Keycloak client, whose client roles become authorities
 * @param expectedAudiencesCsv audiences a token must carry; blank leaves {@code aud} unchecked
 * @param publicPaths         paths this service serves without a token — a provider's webhook
 * @param jwksCacheTtl        how long a fetched JWK set is trusted ({@code keycloak.jwks-cache-ttl})
 */
public record ServiceSecurity(String issuerUri,
                              String trustedIssuersCsv,
                              String clientId,
                              String expectedAudiencesCsv,
                              List<String> publicPaths,
                              boolean requireAudience,
                              Duration jwksCacheTtl) {

    public ServiceSecurity {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
        jwksCacheTtl = jwksCacheTtl == null ? MultiIssuerJwtResolver.DEFAULT_JWKS_CACHE_TTL : jwksCacheTtl;
    }

    /** A service content with the default JWK-set cache lifetime. */
    public ServiceSecurity(String issuerUri, String trustedIssuersCsv, String clientId,
                           String expectedAudiencesCsv, List<String> publicPaths, boolean requireAudience) {
        this(issuerUri, trustedIssuersCsv, clientId, expectedAudiencesCsv, publicPaths, requireAudience, null);
    }

    public ReactiveJwtDecoder reactiveJwtDecoder() {
        return PlatformResourceServer.decoder(issuerUri, expectedAudiencesCsv, jwksCacheTtl);
    }

    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> {
                    exchanges.pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll();
                    exchanges.pathMatchers("/graphiql/**").permitAll();
                    if (!publicPaths.isEmpty()) {
                        exchanges.pathMatchers(publicPaths.toArray(String[]::new)).permitAll();
                    }
                    // Read and write are different grants: a caller scoped only to read a resource
                    // must not be able to change one by reaching the same prefix with a different
                    // verb. ROLE_INTERNAL_SERVICE and ROLE_SYSTEM bypass the split for the few
                    // callers that are trusted for both.
                    exchanges.pathMatchers(HttpMethod.GET, "/api/internal/**")
                            .hasAnyAuthority("SCOPE_internal-read", "ROLE_INTERNAL_SERVICE", "ROLE_SYSTEM");
                    exchanges.pathMatchers("/api/internal/**")
                            .hasAnyAuthority("SCOPE_internal-write", "ROLE_INTERNAL_SERVICE", "ROLE_SYSTEM");
                    // A tokenless POST that PublicGraphQlFilter judged a public operation (only a service that
                    // declares a PublicOperationPolicy ever has one marked); everything else needs a token.
                    exchanges.matchers(com.pml.shared.security.publicop.PublicGraphQlFilter.isPublicOperation()).permitAll();
                    exchanges.pathMatchers("/graphql/**").authenticated();
                    exchanges.anyExchange().authenticated();
                })
                .oauth2ResourceServer(PlatformResourceServer.jwt(
                        issuerUri, trustedIssuersCsv, clientId, expectedAudiencesCsv, requireAudience, jwksCacheTtl))
                .build();
    }
}
