package com.pml.identity.security;

import com.pml.shared.testing.jwt.JwtRejectionContract;
import com.pml.shared.testing.jwt.StubIssuer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Identity-service refuses bad tokens on its own, with no gateway in front.
 *
 * <h2>Why the identity service is not exempt</h2>
 * It is tempting to treat the service that talks to Keycloak as the one place authentication is
 * already handled. It is not: identity exposes user profiles, organization records and the
 * organizer approval surface over 8083, and a token it accepts is a token that can read or
 * change who someone is. The gateway permits {@code /graphql/**} and runs in a separate
 * process, so this filter chain stands alone.
 *
 * <p>Only the health and info probes are public in {@link SecurityConfig}; the probe path used
 * here is not, so the assertions below measure the authenticated path rather than an
 * accidentally-open one.</p>
 */
@Tag("L1")
@Tag("ET-PLT-001")
@DisplayName("ET-PLT-001-R7 · identity-service validates JWTs itself")
class JwtValidationContractTest {

    private static final String AUDIENCE = "myticketzm-identity-service";

    private static StubIssuer realm;
    private static StubIssuer otherRealm;

    @BeforeAll
    static void startRealms() {
        realm = StubIssuer.start("myticketzm");
        otherRealm = StubIssuer.start("not-our-realm");
    }

    @AfterAll
    static void stopRealms() {
        realm.close();
        otherRealm.close();
    }

    @Test
    @DisplayName("forged, wrong-issuer, expired and absent tokens are refused; a valid one is accepted")
    void refusesEveryInvalidToken() {
        JwtRejectionContract.against(configuredWith(AUDIENCE)::securityWebFilterChain)
                .assertAllFour(realm, otherRealm, AUDIENCE);
    }

    @Test
    @DisplayName("a token minted for booking-service does not open identity-service")
    void refusesAnotherServicesToken() {
        JwtRejectionContract.against(configuredWith(AUDIENCE)::securityWebFilterChain)
                .assertRejectsWrongAudience(realm, "myticketzm-booking-service");
    }

    /** The real configuration class, populated the way Spring populates it. */
    private static SecurityConfig configuredWith(String expectedAudiences) {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "issuerUri", realm.issuer());
        ReflectionTestUtils.setField(config, "trustedIssuersCsv", "");
        ReflectionTestUtils.setField(config, "keycloakClientId", AUDIENCE);
        ReflectionTestUtils.setField(config, "expectedAudiencesCsv", expectedAudiences);
        return config;
    }
}
