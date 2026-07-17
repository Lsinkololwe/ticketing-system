package com.pml.shared.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManagerResolver;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerReactiveAuthenticationManagerResolver;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

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
 * <p>This is OFF by default — services only build this resolver when more than one trusted
 * issuer is configured (see {@code keycloak.trusted-issuers}); otherwise they keep the plain
 * single-issuer {@code .jwt(...)} path unchanged.</p>
 */
public final class MultiIssuerJwtResolver {

    private MultiIssuerJwtResolver() {
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

        if (trustedIssuers == null || trustedIssuers.isEmpty()) {
            throw new IllegalArgumentException("At least one trusted issuer must be provided");
        }

        Map<String, ReactiveAuthenticationManager> managersByIssuer = new LinkedHashMap<>();
        for (String issuer : trustedIssuers) {
            String normalized = issuer.trim();
            if (normalized.isEmpty() || managersByIssuer.containsKey(normalized)) {
                continue;
            }
            managersByIssuer.put(normalized, buildManager(normalized, clientId, expectedAudiences));
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

    private static ReactiveAuthenticationManager buildManager(
            String issuer,
            String clientId,
            List<String> expectedAudiences) {

        // Lazy JWKS (Keycloak standard path) — keys are fetched on first token, NOT at startup,
        // so a service is not coupled to every trusted realm being reachable at boot. The issuer
        // is still enforced below via createDefaultWithIssuer.
        String base = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
        String jwkSetUri = base + "/protocol/openid-connect/certs";
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withJwkSetUri(jwkSetUri)
                .build();

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefaultWithIssuer(issuer));
        OAuth2TokenValidator<Jwt> audienceValidator = audienceValidator(expectedAudiences);
        if (audienceValidator != null) {
            validators.add(audienceValidator);
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));

        JwtReactiveAuthenticationManager manager = new JwtReactiveAuthenticationManager(decoder);
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
