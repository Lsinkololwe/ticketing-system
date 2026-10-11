package com.pml.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import java.time.Duration;
import java.util.List;

/**
 * How every service configures itself as an OAuth2 resource server.
 *
 * <h2>The gateway is not the security boundary</h2>
 * Each service validates tokens independently, and the gateway's own
 * configuration makes the reason concrete: it permits {@code /graphql/**} outright, delegating
 * authentication to the subgraphs. A service that trusted the gateway would therefore trust
 * every request. Anything reachable on the network — a misrouted client, a pod in the same
 * namespace, a developer with {@code kubectl port-forward} — reaches the service directly, and
 * the service is the only thing standing there.
 *
 * <h2>Four checks, not three</h2>
 * Signature, issuer, expiry and <b>audience</b>. The first three are what
 * {@code ReactiveJwtDecoders.fromIssuerLocation} gives you for free, which is exactly why the
 * fourth goes missing: nothing complains, tokens validate, and the service accepts any token
 * the right realm minted for any client at all. {@link MultiIssuerJwtResolver#decoderFor} owns
 * all four; this class owns the decision of what to check them against.
 *
 * <h2>An unset audience is announced, not assumed</h2>
 * {@code keycloak.expected-audiences} is empty by default and audience validation is off when
 * it is. That is a real gap, so it is logged at WARN on every startup rather than left to be
 * discovered. Turning it on requires the other half of the change, in the realm rather than
 * here: Keycloak emits {@code "aud": "account"} unless the client has an
 * {@code oidc-audience-mapper}, so switching this on against a realm without one would reject
 * every token the platform issues. The setting is deliberately not defaulted to a service name
 * for that reason — a default that breaks all authentication is worse than a logged gap.
 */
public final class PlatformResourceServer {

    private static final Logger log = LoggerFactory.getLogger(PlatformResourceServer.class);

    private PlatformResourceServer() {
    }

    /**
     * Configure {@code oauth2ResourceServer} for a service or the gateway, logging rather than
     * refusing to start when the audience check is off.
     *
     * @param primaryIssuer       {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}
     * @param trustedIssuersCsv   additional realms to trust, comma-separated; may be blank
     * @param clientId            Keycloak client id, used for client-role extraction
     * @param expectedAudiencesCsv audiences to require in {@code aud}; blank disables the check
     */
    public static Customizer<ServerHttpSecurity.OAuth2ResourceServerSpec> jwt(
            String primaryIssuer,
            String trustedIssuersCsv,
            String clientId,
            String expectedAudiencesCsv) {
        return jwt(primaryIssuer, trustedIssuersCsv, clientId, expectedAudiencesCsv, false);
    }

    /**
     * As {@link #jwt(String, String, String, String)}, but refuses to start at all when {@code
     * requireAudience} is set and the audience check is off — a service a caller has marked as
     * running somewhere other than local development or a test does not get to run with this
     * check silently missing. The caller decides what "somewhere else" means; this method only
     * acts on the answer.
     */
    public static Customizer<ServerHttpSecurity.OAuth2ResourceServerSpec> jwt(
            String primaryIssuer,
            String trustedIssuersCsv,
            String clientId,
            String expectedAudiencesCsv,
            boolean requireAudience) {
        return jwt(primaryIssuer, trustedIssuersCsv, clientId, expectedAudiencesCsv, requireAudience,
                MultiIssuerJwtResolver.DEFAULT_JWKS_CACHE_TTL);
    }

    /**
     * As the five-argument form, with {@code keycloak.jwks-cache-ttl} stated. An out-of-range
     * value fails here, while the service is starting.
     */
    public static Customizer<ServerHttpSecurity.OAuth2ResourceServerSpec> jwt(
            String primaryIssuer,
            String trustedIssuersCsv,
            String clientId,
            String expectedAudiencesCsv,
            boolean requireAudience,
            Duration jwksCacheTtlOrNull) {

        // A caller that predates the property (a test building the chain directly) gets the default.
        Duration jwksCacheTtl = jwksCacheTtlOrNull != null
                ? jwksCacheTtlOrNull : MultiIssuerJwtResolver.DEFAULT_JWKS_CACHE_TTL;
        MultiIssuerJwtResolver.requireValidJwksCacheTtl(jwksCacheTtl);
        List<String> issuers = MultiIssuerJwtResolver.mergeIssuers(primaryIssuer, trustedIssuersCsv);
        List<String> audiences = MultiIssuerJwtResolver.csv(expectedAudiencesCsv);
        if (audiences.isEmpty() && requireAudience) {
            throw new IllegalStateException(
                    "keycloak.expected-audiences is unset for " + clientId + " outside local "
                            + "development or a test. Signature, issuer and expiry alone accept a "
                            + "token this realm minted for any other client — set "
                            + "KEYCLOAK_EXPECTED_AUDIENCES, with the matching oidc-audience-mapper "
                            + "already in place on the client in the realm.");
        }
        warnIfAudienceUnchecked(clientId, audiences);

        return oauth2 -> oauth2.authenticationManagerResolver(
                MultiIssuerJwtResolver.forIssuers(issuers, clientId, audiences, jwksCacheTtl));
    }

    /**
     * The {@link ReactiveJwtDecoder} bean a service should publish.
     *
     * <p>Two reasons it exists even though {@link #jwt} does not use it. It backs Spring Boot's
     * {@code ReactiveOAuth2ResourceServerAutoConfiguration} off — Boot would otherwise build a
     * decoder via {@code fromIssuerLocation}, which fetches OIDC discovery <em>during bean
     * creation</em>, so a service could not start while Keycloak was unreachable even though
     * nothing was going to use that decoder. And anything that injects a decoder directly gets
     * one carrying the same four checks rather than a weaker second opinion.</p>
     */
    public static ReactiveJwtDecoder decoder(String primaryIssuer, String expectedAudiencesCsv) {
        return decoder(primaryIssuer, expectedAudiencesCsv, MultiIssuerJwtResolver.DEFAULT_JWKS_CACHE_TTL);
    }

    /** As {@link #decoder(String, String)}, with {@code keycloak.jwks-cache-ttl} stated. */
    public static ReactiveJwtDecoder decoder(
            String primaryIssuer, String expectedAudiencesCsv, Duration jwksCacheTtl) {
        return MultiIssuerJwtResolver.decoderFor(
                primaryIssuer, MultiIssuerJwtResolver.csv(expectedAudiencesCsv), jwksCacheTtl);
    }

    private static void warnIfAudienceUnchecked(String clientId, List<String> audiences) {
        if (audiences.isEmpty()) {
            log.warn("""
                    JWT audience is NOT validated (keycloak.expected-audiences is unset). \
                    Signature, issuer and expiry are checked; a token this realm minted for a \
                    different client is accepted by {}. To close this, add an \
                    oidc-audience-mapper to the client in the realm and set \
                    KEYCLOAK_EXPECTED_AUDIENCES — both halves, or every token starts failing.""",
                    clientId);
        }
    }
}
