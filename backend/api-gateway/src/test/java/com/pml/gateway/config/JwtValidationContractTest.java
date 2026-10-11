package com.pml.gateway.config;

import com.pml.shared.testing.jwt.JwtRejectionContract;
import com.pml.shared.testing.jwt.StubIssuer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

/**
 * The gateway's own filter chain refuses bad tokens, driven through the same contract the
 * services satisfy.
 *
 * <h2>Why the gateway needs two probes where a service needs one</h2>
 * A service authenticates every path, so each rejection is asserted at both of the shared probe
 * paths. The gateway permits {@code /graphql/**} on purpose: it relays the request and each
 * subgraph validates the token itself. Two things follow, and they are asserted separately
 * rather than blurred together:
 * <ul>
 *   <li>on a path the gateway protects, the whole contract applies, including that a request
 *       with no credentials at all is refused;</li>
 *   <li>on {@code /graphql}, a request with no credentials is passed through, but a request that
 *       <em>presents</em> a bad token is still refused, because the bearer filter authenticates
 *       whatever token it is handed before the path rules are consulted.</li>
 * </ul>
 * The shared "no credentials is refused" assertion therefore cannot be pointed at
 * {@code /graphql} on this chain, and is not.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("api-gateway refuses bad tokens on its own filter chain")
class JwtValidationContractTest {

    private static final String AUDIENCE = "myticketzm-api-gateway";

    /** A path no permit rule matches, so it falls to the chain's authenticated default. */
    private static final List<String> PROTECTED_PATHS = List.of("/api/v1/probe");

    private static final List<String> GRAPHQL_PATHS = List.of("/graphql");

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
    @DisplayName("on a protected path: forged, wrong-issuer, expired, not-yet-valid and absent tokens are refused")
    void protectedPathRefusesEveryInvalidToken() {
        JwtRejectionContract.against(configuredWith(AUDIENCE)::springSecurityFilterChain, PROTECTED_PATHS)
                .assertAllFour(realm, otherRealm, AUDIENCE);
    }

    @Test
    @DisplayName("on /graphql: a presented bad token is refused, a valid one is accepted")
    void graphqlRefusesEveryPresentedInvalidToken() {
        JwtRejectionContract.against(configuredWith(AUDIENCE)::springSecurityFilterChain, GRAPHQL_PATHS)
                .assertAllTokenRejections(realm, otherRealm, AUDIENCE);
    }

    @Test
    @DisplayName("on /graphql: a request with no credentials is passed through to the subgraph")
    void graphqlPassesAnonymousRequestsThrough() {
        JwtRejectionContract.against(configuredWith(AUDIENCE)::springSecurityFilterChain, GRAPHQL_PATHS)
                .assertAccepts(null, "a request carrying no token, which the gateway leaves to the subgraphs");
    }

    @Test
    @DisplayName("a token minted for another client does not open the gateway")
    void refusesAnotherClientsToken() {
        JwtRejectionContract.against(configuredWith(AUDIENCE)::springSecurityFilterChain, PROTECTED_PATHS)
                .assertRejectsWrongAudience(realm, "myticketzm-booking-service");
    }

    @Test
    @DisplayName("a token minted for another client is refused on /graphql too")
    void graphqlRefusesAnotherClientsToken() {
        JwtRejectionContract.against(configuredWith(AUDIENCE)::springSecurityFilterChain, GRAPHQL_PATHS)
                .assertRejectsWrongAudience(realm, "myticketzm-booking-service");
    }

    /** The real configuration class, populated the way Spring populates it. */
    private static GatewaySecurityConfig configuredWith(String expectedAudiences) {
        GatewaySecurityConfig config = new GatewaySecurityConfig();
        ReflectionTestUtils.setField(config, "issuerUri", realm.issuer());
        ReflectionTestUtils.setField(config, "trustedIssuersCsv", "");
        ReflectionTestUtils.setField(config, "keycloakClientId", AUDIENCE);
        ReflectionTestUtils.setField(config, "expectedAudiencesCsv", expectedAudiences);
        return config;
    }
}
