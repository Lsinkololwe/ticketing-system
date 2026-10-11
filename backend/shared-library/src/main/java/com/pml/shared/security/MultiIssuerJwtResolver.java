package com.pml.shared.security;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManagerResolver;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerReactiveAuthenticationManagerResolver;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.function.Function;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a reactive {@link ReactiveAuthenticationManagerResolver} that trusts JWTs from
 * MULTIPLE Keycloak realm issuers, selecting the right validator by the token's {@code iss}
 * claim.
 *
 * <p>This enables the platform-admin realm split: the public realm ({@code myticketzm}) and
 * the internal admin realm ({@code myticketzm-admin}) issue tokens with different issuers and
 * signing keys. Each resource server can accept both, while a token from an unknown issuer is
 * rejected (the resolver returns no manager → 401).</p>
 *
 * <p>Each per-issuer manager:</p>
 * <ul>
 *   <li>discovers JWKS via OIDC ({@code <issuer>/.well-known/openid-configuration})</li>
 *   <li>validates signature + standard claims (exp/nbf) + {@code iss} == issuer</li>
 *   <li>optionally validates the {@code aud} claim against {@code expectedAudiences}</li>
 *   <li>extracts Keycloak roles via {@link KeycloakJwtAuthenticationConverter}</li>
 * </ul>
 *
 * <h2>One path, not two</h2>
 * <p>Every service and the gateway route through this resolver, whether one issuer is trusted
 * or several. There used to be a branch — {@code issuers.size() > 1} took this path and
 * anything else fell back to a plain {@code .jwt(...)} decoder — and the fallback validated
 * signature, issuer and expiry but <em>silently ignored the configured audience</em>. A
 * security control that applies only in the multi-realm configuration is not a control; it is
 * a coincidence. Collapsing the branch means the four checks in
 * {@link #decoderFor(String, java.util.List)} are the only ones that exist.</p>
 *
 * <p>The branch was also mislabelled everywhere it appeared. Its comment read "OFF by default",
 * while {@code keycloak.trusted-issuers} in fact defaults to the admin realm — so the branch
 * nobody thought was live was the only one running, and the documented fallback never
 * executed.</p>
 */
public final class MultiIssuerJwtResolver {

    /**
     * The platform's clock-skew ceiling (ET-PLT-007 R2). Narrower than Spring's own 60-second
     * default, so the issuer and timestamp checks are composed explicitly below instead of
     * reusing {@code JwtValidators.createDefaultWithIssuer}, which cannot be narrowed.
     */
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    /** How long a fetched JWK set is trusted when {@code keycloak.jwks-cache-ttl} is not set. */
    public static final Duration DEFAULT_JWKS_CACHE_TTL = Duration.ofMinutes(5);

    /**
     * The longest a signing key may stay trusted after Keycloak stops publishing it. A larger
     * value means a key that was rotated out because it leaked is still accepted that much
     * longer, so the setting is refused beyond this rather than merely discouraged.
     */
    public static final Duration MAX_JWKS_CACHE_TTL = Duration.ofMinutes(15);

    /**
     * The shortest gap between two network fetches of one realm's JWK set. Without it, a
     * stream of tokens carrying made-up {@code kid} values (each one a deliberate cache miss)
     * would turn into one request to Keycloak per token.
     */
    private static final Duration MIN_JWKS_REFRESH_INTERVAL = Duration.ofSeconds(10);

    private MultiIssuerJwtResolver() {
    }

    /**
     * Refuse a JWK-set cache lifetime that is absent, zero, negative or beyond
     * {@link #MAX_JWKS_CACHE_TTL}. Called when a decoder is built, so a misconfigured service
     * stops at startup instead of running with a cache nobody chose.
     *
     * @return the same value, for use in an expression
     */
    public static Duration requireValidJwksCacheTtl(Duration ttl) {
        if (ttl == null || ttl.toMillis() <= 0 || ttl.compareTo(MAX_JWKS_CACHE_TTL) > 0) {
            throw new IllegalArgumentException("keycloak.jwks-cache-ttl must be greater than zero and at most "
                    + MAX_JWKS_CACHE_TTL + ", but was " + ttl);
        }
        return ttl;
    }

    /**
     * Create a resolver over the given trusted issuer URLs.
     *
     * @param trustedIssuers    realm issuer URLs (e.g. {@code http://localhost:8084/realms/myticketzm})
     * @param clientId          Keycloak client id used for client-role extraction (may be null)
     * @param expectedAudiences audiences to require in the {@code aud} claim; null/empty disables audience checks
     * @return a reactive authentication manager resolver keyed by the token issuer
     */
    public static ReactiveAuthenticationManagerResolver<ServerWebExchange> forIssuers(
            List<String> trustedIssuers,
            String clientId,
            List<String> expectedAudiences) {
        return forIssuers(trustedIssuers, clientId, expectedAudiences, DEFAULT_JWKS_CACHE_TTL);
    }

    /**
     * As {@link #forIssuers(List, String, List)}, with the JWK-set cache lifetime stated.
     *
     * @param jwksCacheTtl how long a fetched JWK set is trusted; see {@link #requireValidJwksCacheTtl}
     */
    public static ReactiveAuthenticationManagerResolver<ServerWebExchange> forIssuers(
            List<String> trustedIssuers,
            String clientId,
            List<String> expectedAudiences,
            Duration jwksCacheTtl) {

        requireValidJwksCacheTtl(jwksCacheTtl);
        if (trustedIssuers == null || trustedIssuers.isEmpty()) {
            throw new IllegalArgumentException("At least one trusted issuer must be provided");
        }

        Map<String, ReactiveAuthenticationManager> managersByIssuer = new LinkedHashMap<>();
        for (String issuer : trustedIssuers) {
            String normalized = issuer.trim();
            if (normalized.isEmpty() || managersByIssuer.containsKey(normalized)) {
                continue;
            }
            managersByIssuer.put(normalized, buildManager(normalized, clientId, expectedAudiences, jwksCacheTtl));
        }

        // JwtIssuerReactiveAuthenticationManagerResolver selects the manager by the token's
        // `iss` claim. Unknown issuer → empty Mono → AuthenticationServiceException → 401.
        return new JwtIssuerReactiveAuthenticationManagerResolver(
                (String issuer) -> Mono.justOrEmpty(managersByIssuer.get(issuer))
        );
    }

    /**
     * Merge a primary issuer with a comma-separated list of additional trusted issuers,
     * de-duplicating and preserving order. Blank entries are ignored.
     */
    public static List<String> mergeIssuers(String primaryIssuer, String additionalCsv) {
        List<String> out = new ArrayList<>();
        if (primaryIssuer != null && !primaryIssuer.isBlank()) {
            out.add(primaryIssuer.trim());
        }
        for (String entry : csv(additionalCsv)) {
            if (!out.contains(entry)) {
                out.add(entry);
            }
        }
        return out;
    }

    /** Split a comma-separated string into a trimmed, non-empty list. */
    public static List<String> csv(String value) {
        List<String> out = new ArrayList<>();
        if (value != null && !value.isBlank()) {
            for (String entry : value.split(",")) {
                String trimmed = entry.trim();
                if (!trimmed.isEmpty()) {
                    out.add(trimmed);
                }
            }
        }
        return out;
    }

    /**
     * The one place a JWT decoder is built. Every check a token must pass is applied here:
     *
     * <ol>
     *   <li><b>signature</b> — {@code NimbusReactiveJwtDecoder} against the realm's JWKS</li>
     *   <li><b>issuer</b> — {@code createDefaultWithIssuer} pins {@code iss}</li>
     *   <li><b>expiry</b> — {@code exp} and {@code nbf}, also from the default validator</li>
     *   <li><b>audience</b> — {@code aud}, when {@code expectedAudiences} is non-empty</li>
     * </ol>
     *
     * <p>The fourth is the one that is easy to omit, and omitting it is not a small gap: a
     * signature-and-issuer check accepts a token the right realm minted for a completely
     * different client. See {@link PlatformResourceServer}, which is where services configure
     * the audience and where an unset one is announced rather than assumed.</p>
     *
     * <p><b>JWKS is fetched lazily</b>, on the first token rather than at startup, and from
     * Keycloak's fixed {@code /protocol/openid-connect/certs} path rather than by OIDC
     * discovery. Both are deliberate: discovery ({@code ReactiveJwtDecoders.fromIssuerLocation})
     * performs a network call during bean creation, which makes every service unable to start
     * whenever Keycloak is briefly unreachable — a boot-time coupling with no security benefit,
     * since {@code iss} is pinned below either way.</p>
     *
     * <p><b>The key cache is bounded.</b> Spring's own remote JWK source keeps the set it first
     * fetched for the life of the process: a key Keycloak has since withdrawn would verify
     * signatures until restart. The set is therefore read through Nimbus's
     * {@link JWKSourceBuilder}, which discards it after {@code jwksCacheTtl}. A token whose
     * {@code kid} is not in the cached set triggers one refetch regardless of the TTL — that is
     * Nimbus's own behaviour, kept on purpose, and it is what makes a newly rotated-in key
     * usable immediately instead of after the TTL.</p>
     *
     * @param issuer            the realm issuer URL
     * @param expectedAudiences audiences to require in {@code aud}; empty disables the check
     */
    public static NimbusReactiveJwtDecoder decoderFor(String issuer, List<String> expectedAudiences) {
        return decoderFor(issuer, expectedAudiences, DEFAULT_JWKS_CACHE_TTL);
    }

    /**
     * As {@link #decoderFor(String, List)}, with the JWK-set cache lifetime stated.
     *
     * @param jwksCacheTtl how long a fetched JWK set is trusted; see {@link #requireValidJwksCacheTtl}
     */
    public static NimbusReactiveJwtDecoder decoderFor(
            String issuer, List<String> expectedAudiences, Duration jwksCacheTtl) {
        return decoderFor(issuer, expectedAudiences, jwksCacheTtl, MIN_JWKS_REFRESH_INTERVAL);
    }

    /**
     * Seam for tests: a zero {@code minRefreshInterval} turns the refetch rate limit off, so a
     * rotation can be exercised without waiting out the production interval.
     */
    static NimbusReactiveJwtDecoder decoderFor(
            String issuer, List<String> expectedAudiences, Duration jwksCacheTtl, Duration minRefreshInterval) {
        requireValidJwksCacheTtl(jwksCacheTtl);
        String base = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withJwkSource(keySource(base + "/protocol/openid-connect/certs", jwksCacheTtl, minRefreshInterval))
                .build();

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtIssuerValidator(issuer));
        validators.add(new JwtTimestampValidator(CLOCK_SKEW));
        OAuth2TokenValidator<Jwt> audienceValidator = audienceValidator(expectedAudiences);
        if (audienceValidator != null) {
            validators.add(audienceValidator);
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    /**
     * The keys a token's header selects from the realm's JWK set, fetched through a TTL cache.
     *
     * <p>Nimbus's source is blocking, so each lookup is moved to the elastic scheduler. Nothing
     * is fetched until the first token arrives.</p>
     */
    private static Function<SignedJWT, Flux<JWK>> keySource(
            String jwkSetUri, Duration ttl, Duration minRefreshInterval) {
        JWKSourceBuilder<SecurityContext> builder;
        try {
            builder = JWKSourceBuilder.create(URI.create(jwkSetUri).toURL());
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Not a usable JWK set URL: " + jwkSetUri, e);
        }
        builder.cache(ttl.toMillis(), JWKSourceBuilder.DEFAULT_CACHE_REFRESH_TIMEOUT);
        // Refresh-ahead keeps a set alive past its TTL by refetching in the background, and it
        // insists on a TTL longer than its own lead time. A plain TTL is what bounds the cache.
        builder.refreshAheadCache(false);
        if (minRefreshInterval.toMillis() > 0) {
            builder.rateLimited(minRefreshInterval.toMillis());
        } else {
            builder.rateLimited(false);
        }
        JWKSource<SecurityContext> source = builder.build();

        return jwt -> {
            JWKMatcher matcher = JWKMatcher.forJWSHeader(jwt.getHeader());
            if (matcher == null) {
                // An algorithm no key can verify (none, HMAC): no keys, so the token is refused.
                return Flux.empty();
            }
            JWKSelector selector = new JWKSelector(matcher);
            return Mono.fromCallable(() -> source.get(selector, null))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMapMany(Flux::fromIterable);
        };
    }

    private static ReactiveAuthenticationManager buildManager(
            String issuer,
            String clientId,
            List<String> expectedAudiences,
            Duration jwksCacheTtl) {

        JwtReactiveAuthenticationManager manager =
                new JwtReactiveAuthenticationManager(decoderFor(issuer, expectedAudiences, jwksCacheTtl));
        manager.setJwtAuthenticationConverter(
                KeycloakJwtAuthenticationConverter.reactiveConverter(clientId));
        return manager;
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(List<String> expectedAudiences) {
        if (expectedAudiences == null || expectedAudiences.isEmpty()) {
            return null;
        }
        return new JwtClaimValidator<>(JwtClaimNames.AUD, aud -> {
            if (aud instanceof String) {
                return expectedAudiences.contains(aud);
            }
            if (aud instanceof Collection<?> audiences) {
                return audiences.stream().anyMatch(a -> expectedAudiences.contains(String.valueOf(a)));
            }
            return false;
        });
    }
}
