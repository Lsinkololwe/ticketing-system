package com.pml.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pml.shared.testing.jwt.StubIssuer;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * ET-PLT-007-R2 · clock skew is explicit and no greater than 30 seconds, not Spring's own
 * 60-second default.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("A token's exp is honoured within 30 seconds of skew, and refused past it")
class JwtClockSkewTest {

    private static final String AUDIENCE = "myticketzm-catalog-service";
    private static StubIssuer issuer;

    @BeforeAll
    static void start() {
        issuer = StubIssuer.start("myticketzm");
    }

    @AfterAll
    static void stop() {
        issuer.close();
    }

    @Test
    @DisplayName("a token that expired 20 seconds ago is still accepted, inside the skew window")
    void withinSkewIsAccepted() {
        String token = issuer.expiredBy(Duration.ofSeconds(20), AUDIENCE);

        assertThat(MultiIssuerJwtResolver.decoderFor(issuer.issuer(), java.util.List.of())
                .decode(token).block())
                .as("20s past exp, 30s of skew allowed")
                .isNotNull();
    }

    @Test
    @DisplayName("a token that expired 45 seconds ago is refused")
    void pastSkewIsRefused() {
        String token = issuer.expiredBy(Duration.ofSeconds(45), AUDIENCE);

        assertThatThrownBy(() -> MultiIssuerJwtResolver.decoderFor(issuer.issuer(), java.util.List.of())
                .decode(token).block())
                .as("45s past exp exceeds the 30s skew ceiling")
                .isInstanceOf(JwtException.class);
    }
}
