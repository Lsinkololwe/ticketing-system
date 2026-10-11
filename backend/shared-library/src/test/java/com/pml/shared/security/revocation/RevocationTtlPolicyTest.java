package com.pml.shared.security.revocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * How long a revocation is remembered, and when a token counts as expired. The rule is the
 * standard one for deny lists: a key lives exactly as long as the token it blocks could still be
 * presented, never longer, so Redis holds only what can still matter.
 */
@Tag("L1")
@Tag("ET-IDN-003")
@DisplayName("A revocation is remembered for as long as its token could be presented, and no longer")
class RevocationTtlPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");

    private final RevocationProperties properties = new RevocationProperties();

    @Test
    @DisplayName("a single token is kept until its own expiry plus skew")
    void tokenIsKeptUntilItsOwnExpiry() {
        Duration ttl = properties.ttlFor(RevocationType.TOKEN, NOW.plusSeconds(30), NOW);

        assertThat(ttl).isEqualTo(Duration.ofSeconds(30).plus(properties.getClockSkew()));
    }

    @Test
    @DisplayName("a token that has already expired is kept for the skew only")
    void expiredTokenIsKeptForTheSkewOnly() {
        assertThat(properties.ttlFor(RevocationType.TOKEN, NOW.minusSeconds(600), NOW))
                .isEqualTo(properties.getClockSkew());
    }

    @Test
    @DisplayName("a token claiming a lifetime beyond the platform's is capped at one lifespan plus skew")
    void tokenIsCappedAtTheLifespan() {
        assertThat(properties.ttlFor(RevocationType.TOKEN, NOW.plus(Duration.ofDays(1)), NOW))
                .isEqualTo(properties.recordTtl());
    }

    @Test
    @DisplayName("a token with no known expiry is kept for the full window")
    void unknownExpiryIsTheFullWindow() {
        assertThat(properties.ttlFor(RevocationType.TOKEN, null, NOW)).isEqualTo(properties.recordTtl());
    }

    @Test
    @DisplayName("a session or user revocation covers tokens minted later, so it is kept for the full window")
    void sessionAndUserAreKeptForTheFullWindow() {
        assertThat(properties.ttlFor(RevocationType.SESSION, NOW.plusSeconds(5), NOW))
                .isEqualTo(properties.recordTtl());
        assertThat(properties.ttlFor(RevocationType.USER, NOW.plusSeconds(5), NOW))
                .isEqualTo(properties.recordTtl());
    }

    @Test
    @DisplayName("the durable timeout must be longer than the cache timeout, or the platform refuses to start")
    void durableTimeoutMustExceedCacheTimeout() {
        properties.setCacheTimeout(Duration.ofSeconds(2));
        properties.setDurableTimeout(Duration.ofSeconds(2));

        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("durable-timeout");
    }

    @Test
    @DisplayName("a negative skew or a non-positive lifespan is refused")
    void unsafeWindowsAreRefused() {
        properties.setClockSkew(Duration.ofSeconds(-1));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);

        RevocationProperties zero = new RevocationProperties();
        zero.setAccessTokenLifespan(Duration.ZERO);
        assertThatThrownBy(zero::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("the defaults are a valid configuration with the durable budget the longer one")
    void defaultsAreValid() {
        properties.validate();
        assertThat(properties.getDurableTimeout()).isGreaterThan(properties.getCacheTimeout());
    }

    @Test
    @DisplayName("a token is expired once its exp plus the tolerance has passed, and not before")
    void expiryBoundary() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("u")
                .issuedAt(NOW.minusSeconds(300)).expiresAt(NOW).build();
        Duration tolerance = Duration.ofSeconds(30);

        assertThat(AccessTokenLifetime.isExpired(jwt, NOW.plusSeconds(30), tolerance)).isFalse();
        assertThat(AccessTokenLifetime.isExpired(jwt, NOW.plusSeconds(31), tolerance)).isTrue();
        assertThat(AccessTokenLifetime.isExpired(jwt, NOW.minusSeconds(1), tolerance)).isFalse();
    }
}
