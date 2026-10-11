package com.pml.shared.testing.jwt;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * A Keycloak realm's signing identity, stubbed — enough of one to mint tokens a real resource
 * server accepts, and to mint the four kinds it must refuse.
 *
 * <h2>Why not a Keycloak container</h2>
 * There are four rejection cases to prove: bad signature, wrong issuer, wrong audience,
 * expired. A real Keycloak can produce none of them on demand. It will not sign a token with a
 * key it does not hold, it will not mint one that expired ten minutes ago, and getting a wrong
 * audience out of it means provisioning a second client with an audience mapper first. Driving
 * a container into those states costs a minute of pull-and-boot per module and still ends with
 * the token being hand-assembled — so the container would be ceremony around the same
 * Nimbus-signed JWT this class produces directly, with a slower and flakier build attached.
 *
 * <p>What the container <em>would</em> add is confidence that Keycloak's real token shape is
 * handled. That is a different question from "is an invalid token rejected", it belongs with
 * the login-flow tests that already exist against a live realm, and it is worth saying plainly
 * that this class does not answer it.
 *
 * <h2>Two of these make the interesting case</h2>
 * The realistic wrong-issuer attack is not a malformed token. It is a perfectly valid one from
 * a realm outside the service's trusted set. Start a second {@code StubIssuer} and mint from
 * it: correctly signed, unexpired, structurally impeccable, and refused on {@code iss} alone.
 */
public final class StubIssuer implements AutoCloseable {

    /** The platform allows 30s of clock skew, so "expired" must clear it by a wide margin. */
    private static final Duration WELL_PAST_EXPIRY = Duration.ofMinutes(10);

    /** Likewise for {@code nbf}: far enough ahead that no skew allowance could excuse it. */
    private static final Duration WELL_BEFORE_VALID = Duration.ofMinutes(10);

    private final WireMockServer server;
    private final RSAKey signingKey;
    private final RSAKey unpublishedKey;
    private final String realm;

    private StubIssuer(WireMockServer server, RSAKey signingKey, RSAKey unpublishedKey, String realm) {
        this.server = server;
        this.signingKey = signingKey;
        this.unpublishedKey = unpublishedKey;
        this.realm = realm;
    }

    /**
     * Start a realm on a random free port, serving JWKS at Keycloak's path.
     *
     * @param realm the realm name, e.g. {@code myticketzm}
     */
    public static StubIssuer start(String realm) {
        try {
            RSAKey signing = new RSAKeyGenerator(2048)
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID(UUID.randomUUID().toString())
                    .algorithm(JWSAlgorithm.RS256)
                    .generate();

            // Never published. Signing with this and claiming the published `kid` is what a
            // forged token looks like from the outside: right header, wrong hand.
            RSAKey unpublished = new RSAKeyGenerator(2048)
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID(signing.getKeyID())
                    .algorithm(JWSAlgorithm.RS256)
                    .generate();

            WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
            server.start();
            server.stubFor(get(urlPathEqualTo("/realms/" + realm + "/protocol/openid-connect/certs"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(new JWKSet(signing.toPublicJWK()).toString())));

            return new StubIssuer(server, signing, unpublished, realm);
        } catch (Exception e) {
            throw new IllegalStateException("could not start a stub issuer", e);
        }
    }

    /** The {@code iss} this realm claims, and the value a service configures as its issuer-uri. */
    public String issuer() {
        return "http://localhost:" + server.port() + "/realms/" + realm;
    }

    /** How many times the JWKS endpoint has been fetched — JWKS is lazy, so this starts at zero. */
    public int jwksFetchCount() {
        return server.countRequestsMatching(
                        com.github.tomakehurst.wiremock.client.WireMock
                                .getRequestedFor(urlPathEqualTo(
                                        "/realms/" + realm + "/protocol/openid-connect/certs"))
                                .build())
                .getCount();
    }

    // ------------------------------------------------------------------ minting

    /** A token this realm's resource servers should accept. */
    public String validToken(String audience, String... realmRoles) {
        return sign(signingKey, claims(issuer(), audience, Instant.now().plus(Duration.ofMinutes(30)), realmRoles));
    }

    /** A valid token carrying the given space-separated OAuth scopes (the Keycloak {@code scope} claim). */
    public String scopedToken(String audience, String scope) {
        JWTClaimsSet base = claims(issuer(), audience, Instant.now().plus(Duration.ofMinutes(30)));
        return sign(signingKey, new JWTClaimsSet.Builder(base).claim("scope", scope).build());
    }

    /** Right claims, wrong hand: signed by a key whose public half was never published. */
    public String forgedSignature(String audience) {
        return sign(unpublishedKey,
                claims(issuer(), audience, Instant.now().plus(Duration.ofMinutes(30))));
    }

    /** Correctly signed by this realm, but expired well beyond the allowed clock skew. */
    public String expired(String audience) {
        return sign(signingKey, claims(issuer(), audience, Instant.now().minus(WELL_PAST_EXPIRY)));
    }

    /**
     * Correctly signed and unexpired, but its {@code nbf} lies well beyond the allowed clock skew.
     *
     * <p>Every other claim is the same as {@link #validToken}'s, so the not-before claim is the
     * only thing that can account for a refusal.</p>
     */
    public String notYetValid(String audience) {
        JWTClaimsSet base = claims(issuer(), audience, Instant.now().plus(Duration.ofMinutes(30)));
        return sign(signingKey, new JWTClaimsSet.Builder(base)
                .notBeforeTime(Date.from(Instant.now().plus(WELL_BEFORE_VALID)))
                .build());
    }

    /** A token whose {@code exp} fell exactly {@code amount} ago, for pinning a clock-skew boundary. */
    public String expiredBy(Duration amount, String audience) {
        return sign(signingKey, claims(issuer(), audience, Instant.now().minus(amount)));
    }

    /** Correctly signed and unexpired — for a different client entirely. */
    public String wrongAudience(String audience) {
        return sign(signingKey, claims(issuer(), audience, Instant.now().plus(Duration.ofMinutes(30))));
    }

    /**
     * Signed by <em>this</em> realm's published key, but claiming to come from another issuer.
     *
     * <p>This is what isolates the issuer check. A token from a genuinely separate realm carries
     * that realm's signature, so refusing it proves only that the signature check works — the
     * {@code iss} claim is never reached. Here the signature verifies against the JWKS the
     * service is configured with, so the only thing left that can refuse the token is
     * {@code iss} itself.</p>
     */
    public String claimingIssuer(String otherIssuer, String audience) {
        return sign(signingKey, claims(otherIssuer, audience, Instant.now().plus(Duration.ofMinutes(30))));
    }

    private static JWTClaimsSet claims(String issuer, String audience, Instant expiry, String... realmRoles) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(UUID.randomUUID().toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(expiry.isBefore(now) ? expiry.minus(Duration.ofMinutes(5)) : now))
                .notBeforeTime(Date.from(expiry.isBefore(now) ? expiry.minus(Duration.ofMinutes(5)) : now))
                .expirationTime(Date.from(expiry))
                .claim("typ", "Bearer")
                .claim("preferred_username", "stub-user");
        if (audience != null) {
            builder.audience(audience);
        }
        if (realmRoles.length > 0) {
            builder.claim("realm_access", Map.of("roles", List.of(realmRoles)));
        }
        return builder.build();
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(key.getKeyID())
                            .type(JOSEObjectType.JWT)
                            .build(),
                    claims);
            jwt.sign(new RSASSASigner(key.toPrivateKey()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("could not sign a stub token", e);
        }
    }

    @Override
    public void close() {
        server.stop();
    }
}
