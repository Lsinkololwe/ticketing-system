package com.pml.shared.security;

import com.pml.shared.testing.jwt.JwtRejectionContract;
import com.pml.shared.testing.jwt.StubIssuer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The four token checks — signature, issuer, expiry, audience — asserted where they are
 * implemented.
 *
 * <h2>The gap this guards</h2>
 * {@code keycloak.expected-audiences} is easy to read into a field and then apply only on the
 * branch that runs when more than one issuer is configured, leaving the single-issuer branch to
 * accept, store and silently ignore it. A resource server that checks signature,
 * issuer and expiry but not audience accepts any token the realm minted for any client — the
 * mobile app's token works against the internal admin API, and every log line looks normal.
 *
 * <h2>Both halves are asserted here</h2>
 * That the four rejections happen, and that a valid token is still accepted. Only the pair says
 * anything: a decoder pointed at a JWKS URL that 404s rejects all five inputs and would look
 * like a perfect result.
 */
@Tag("L1")
@Tag("ET-PLT-001")
@DisplayName("ET-PLT-001-R7 · signature, issuer, audience and expiry are all validated")
class PlatformResourceServerTest {

    private static final String AUDIENCE = "myticketzm-catalog-service";

    private static StubIssuer trusted;
    private static StubIssuer rogue;

    @BeforeAll
    static void startRealms() {
        trusted = StubIssuer.start("myticketzm");
        rogue = StubIssuer.start("someone-elses-realm");
    }

    @AfterAll
    static void stopRealms() {
        trusted.close();
        rogue.close();
    }

    @Test
    @DisplayName("forged, wrong-issuer, expired and absent tokens are all refused; a valid one is not")
    void theFourRejections() {
        JwtRejectionContract.against(chainTrusting(trusted, AUDIENCE))
                .assertAllFour(trusted, rogue, AUDIENCE);
    }

    @Test
    @DisplayName("a token this realm minted for another client is refused when an audience is configured")
    void audienceIsEnforcedWhenConfigured() {
        JwtRejectionContract.against(chainTrusting(trusted, AUDIENCE))
                .assertRejectsWrongAudience(trusted, "myticketzm-booking-service");
    }

    @Test
    @DisplayName("without a configured audience the same token is accepted — the gap the WARN announces")
    void audienceIsNotEnforcedWhenUnconfigured() {
        // Not an aspiration about what should happen — this is what an unset
        // KEYCLOAK_EXPECTED_AUDIENCES actually costs, pinned so that the WARN in
        // PlatformResourceServer describes a real consequence rather than a theoretical one.
        JwtRejectionContract.against(chainTrusting(trusted, ""))
                .assertAccepts(trusted.wrongAudience("some-entirely-different-client"),
                        "a token for another client, with audience checking off");
    }

    @Test
    @DisplayName("JWKS is fetched on the first token, not at startup")
    void jwksIsLazy() {
        try (StubIssuer realm = StubIssuer.start("lazy-realm")) {
            MultiIssuerJwtResolver.decoderFor(realm.issuer(), List.of());

            assertThat(realm.jwksFetchCount())
                    .as("building a decoder must not reach Keycloak — otherwise no service can "
                            + "start while the realm is briefly unreachable, and a rolling "
                            + "Keycloak restart takes the platform down with it")
                    .isZero();
        }
    }

    @Test
    @DisplayName("a single trusted issuer takes the same path as several")
    void oneIssuerIsNotASpecialCase() {
        // The audience check must not depend on how many realms happen to be trusted.
        JwtRejectionContract.against(chainTrusting(trusted, AUDIENCE, ""))
                .assertRejectsWrongAudience(trusted, "myticketzm-booking-service");
    }

    // ------------------------------------------------------------------ helpers

    private static java.util.function.Function<ServerHttpSecurity, SecurityWebFilterChain>
            chainTrusting(StubIssuer primary, String audiences) {
        return chainTrusting(primary, audiences, rogueIsNotTrusted());
    }

    private static java.util.function.Function<ServerHttpSecurity, SecurityWebFilterChain>
            chainTrusting(StubIssuer primary, String audiences, String trustedIssuersCsv) {
        return http -> http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().authenticated())
                .oauth2ResourceServer(PlatformResourceServer.jwt(
                        primary.issuer(), trustedIssuersCsv, "myticketzm-catalog-service", audiences))
                .build();
    }

    /** No additional realms — the rogue issuer is deliberately absent from the trusted set. */
    private static String rogueIsNotTrusted() {
        return "";
    }
}
