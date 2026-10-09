package com.pml.catalog.config.security;

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
 * Catalog-service refuses bad tokens on its own, with no gateway in front.
 *
 * <h2>Why this test exists per service rather than once</h2>
 * The property is not "the platform validates JWTs", it is that <em>each service validates
 * independently</em>. Catalog listens on 8085 and the gateway is a separate process; anything
 * that can open a socket to 8085 — another pod, a port-forward, a client with a stale URL —
 * talks to this filter chain and nothing else. The gateway does not even try to help: it
 * permits {@code /graphql/**} outright and leaves authentication to the subgraphs.
 *
 * <p>So the chain built here is the one {@link ServiceSecurity} itself produces, populated from
 * the same properties production reads. A shared test that exercised a representative chain
 * would pass forever while this service's own configuration drifted.</p>
 */
@Tag("L1")
@Tag("ET-PLT-001")
@DisplayName("ET-PLT-001-R7 · catalog-service validates JWTs itself")
class JwtValidationContractTest {

    private static final String AUDIENCE = "myticketzm-catalog-service";

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
    @DisplayName("a token minted for booking-service does not open catalog-service")
    void refusesAnotherServicesToken() {
        // The audience check is what separates the services from each other. Without it any
        // token the realm issued works everywhere, and "which service was this token for?"
        // has no answer.
        JwtRejectionContract.against(configuredWith(AUDIENCE)::securityWebFilterChain)
                .assertRejectsWrongAudience(realm, "myticketzm-booking-service");
    }

    /** The platform's service chain, populated as this service's configuration populates it. */
    private static ServiceSecurity configuredWith(String expectedAudiences) {
        return new ServiceSecurity(realm.issuer(), "", AUDIENCE, expectedAudiences, List.of());
    }
}
