package com.pml.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;

/**
 * A rotated signing key is accepted without a restart, a withdrawn one stops being accepted
 * once the cache has aged out, and the cache lifetime that makes the second true is bounded.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("The JWK-set cache picks up a rotation and bounds how long a withdrawn key is trusted")
class JwksRotationTest {

    private static final String REALM = "rotation";

    private HttpServer server;
    private final AtomicReference<String> served = new AtomicReference<>();
    private final AtomicInteger fetches = new AtomicInteger();
    private RSAKey keyA;
    private RSAKey keyB;

    @BeforeEach
    void start() throws Exception {
        keyA = newKey();
        keyB = newKey();
        served.set(new JWKSet(keyA.toPublicJWK()).toString());

        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/realms/" + REALM + "/protocol/openid-connect/certs", exchange -> {
            fetches.incrementAndGet();
            byte[] body = served.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    @DisplayName("a token signed by the published key is accepted")
    void publishedKeyIsAccepted() throws Exception {
        NimbusReactiveJwtDecoder decoder = MultiIssuerJwtResolver.decoderFor(issuer(), List.of());

        assertThat(decoder.decode(tokenSignedBy(keyA)).block()).isNotNull();
    }

    @Test
    @DisplayName("a key rotated in is accepted by the same decoder through the unknown-kid refetch")
    void rotationIsPickedUpBeforeTheTtl() throws Exception {
        NimbusReactiveJwtDecoder decoder =
                MultiIssuerJwtResolver.decoderFor(issuer(), List.of(), Duration.ofMinutes(15), Duration.ZERO);
        assertThat(decoder.decode(tokenSignedBy(keyA)).block()).isNotNull();

        served.set(new JWKSet(keyB.toPublicJWK()).toString());

        assertThat(decoder.decode(tokenSignedBy(keyB)).block())
                .as("the TTL is 15 minutes; only the unknown kid can have caused this refetch")
                .isNotNull();
        assertThat(fetches.get()).as("first use, then the unknown kid").isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("a token signed by the withdrawn key is refused once the cached set has aged out")
    void withdrawnKeyIsRefusedAfterTheTtl() throws Exception {
        NimbusReactiveJwtDecoder decoder =
                MultiIssuerJwtResolver.decoderFor(issuer(), List.of(), Duration.ofMillis(200), Duration.ZERO);
        assertThat(decoder.decode(tokenSignedBy(keyA)).block()).isNotNull();

        served.set(new JWKSet(keyB.toPublicJWK()).toString());
        Thread.sleep(400);

        assertThatThrownBy(() -> decoder.decode(tokenSignedBy(keyA)).block())
                .as("the cached set expired, was refetched, and no longer holds key A")
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("a withdrawn key is still trusted inside the TTL, which is what the bound is for")
    void withdrawnKeyIsTrustedUntilTheTtlElapses() throws Exception {
        NimbusReactiveJwtDecoder decoder =
                MultiIssuerJwtResolver.decoderFor(issuer(), List.of(), Duration.ofMinutes(15), Duration.ZERO);
        assertThat(decoder.decode(tokenSignedBy(keyA)).block()).isNotNull();

        served.set(new JWKSet(keyB.toPublicJWK()).toString());

        assertThat(decoder.decode(tokenSignedBy(keyA)).block())
                .as("key A is still in the cached set, so no refetch is triggered")
                .isNotNull();
    }

    @Test
    @DisplayName("a cache lifetime of zero, a negative one or one beyond fifteen minutes stops startup")
    void outOfRangeTtlFailsFast() {
        for (Duration bad : List.of(Duration.ZERO, Duration.ofSeconds(-1), Duration.ofMinutes(15).plusMillis(1))) {
            assertThatThrownBy(() -> MultiIssuerJwtResolver.decoderFor(issuer(), List.of(), bad))
                    .as("decoder with ttl %s", bad)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("keycloak.jwks-cache-ttl");
            assertThatThrownBy(() -> MultiIssuerJwtResolver.forIssuers(List.of(issuer()), "client", List.of(), bad))
                    .as("resolver with ttl %s", bad)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("keycloak.jwks-cache-ttl");
        }
    }

    @Test
    @DisplayName("the boundary values, one millisecond and fifteen minutes, are accepted")
    void boundaryTtlsAreAccepted() {
        assertThat(MultiIssuerJwtResolver.requireValidJwksCacheTtl(Duration.ofMillis(1)))
                .isEqualTo(Duration.ofMillis(1));
        assertThat(MultiIssuerJwtResolver.requireValidJwksCacheTtl(Duration.ofMinutes(15)))
                .isEqualTo(Duration.ofMinutes(15));
        assertThat(MultiIssuerJwtResolver.DEFAULT_JWKS_CACHE_TTL).isEqualTo(Duration.ofMinutes(5));
    }

    private String issuer() {
        return "http://localhost:" + server.getAddress().getPort() + "/realms/" + REALM;
    }

    private static RSAKey newKey() throws Exception {
        return new RSAKeyGenerator(2048)
                .keyUse(KeyUse.SIGNATURE)
                .keyID(UUID.randomUUID().toString())
                .algorithm(JWSAlgorithm.RS256)
                .generate();
    }

    private String tokenSignedBy(RSAKey key) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer())
                .subject(UUID.randomUUID().toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofMinutes(30))))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(key.getKeyID())
                        .type(JOSEObjectType.JWT)
                        .build(),
                claims);
        jwt.sign(new RSASSASigner(key.toPrivateKey()));
        return jwt.serialize();
    }
}
