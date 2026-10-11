package com.pml.booking.config.security;

import com.pml.shared.security.ServiceSecurity;
import java.util.List;
import com.pml.shared.testing.jwt.JwtRejectionContract;
import com.pml.shared.testing.jwt.StubIssuer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Booking-service refuses bad tokens on its own, with no gateway in front.
 *
 * <h2>The service where this matters most</h2>
 * Booking owns tickets, payments, escrow and payouts. It listens on 8082 and the gateway is a
 * separate process that permits {@code /graphql/**} anyway, so the filter chain asserted here
 * is the only thing between a socket and the money. A token this chain accepts can reserve
 * inventory and move funds.
 *
 * <p>The chain is the one {@link ServiceSecurity} itself builds, populated from the same
 * properties production reads — not a representative chain that could pass while this
 * service's own configuration drifted.</p>
 */
@Tag("L1")
@Tag("ET-PLT-001")
@DisplayName("ET-PLT-001-R7 · booking-service validates JWTs itself")
class JwtValidationContractTest {

    private static final String AUDIENCE = "myticketzm-booking-service";

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
    @DisplayName("a token minted for catalog-service does not open booking-service")
    void refusesAnotherServicesToken() {
        // Catalog is read-mostly and booking holds the ledger. Without the audience check the
        // realm's catalog token is a booking token, and the blast radius of any leaked token is
        // the whole platform rather than one service.
        JwtRejectionContract.against(configuredWith(AUDIENCE)::securityWebFilterChain)
                .assertRejectsWrongAudience(realm, "myticketzm-catalog-service");
    }

    /** The platform's service chain, populated as this service's configuration populates it. */
    private static ServiceSecurity configuredWith(String expectedAudiences) {
        return new ServiceSecurity(realm.issuer(), "", AUDIENCE, expectedAudiences, List.of(), false);
    }
}
