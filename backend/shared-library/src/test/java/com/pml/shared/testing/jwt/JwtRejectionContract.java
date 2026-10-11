package com.pml.shared.testing.jwt;

import org.springframework.http.HttpHeaders;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The token-rejection contract every service owes, expressed once.
 *
 * <h2>Why it is shared rather than written per service</h2>
 * Each service validates tokens independently, which is exactly the shape of
 * requirement that decays: three services, three copies of four assertions, and within a
 * release one of them checks three cases and nobody notices which. The assertions live here and
 * the services supply their own filter chain, so a service either satisfies all four or fails.
 *
 * <h2>What "directly" means</h2>
 * The chain under test is the one the service's own {@code SecurityConfig} builds — not a
 * representative one, and not the gateway's. That is the whole point of the requirement: the
 * gateway permits {@code /graphql/**}, so a service that only rejected bad tokens when they
 * arrived through the gateway would reject nothing at all.
 *
 * <h2>Why the chain is exercised without a running context</h2>
 * {@link WebFilterChainProxy} is what Spring Security installs in a real application; binding it
 * over a probe route runs the same filters, in the same order, against the same decoder — with
 * no Mongo, no Service Bus, and no service main class dragged in. A {@code @SpringBootTest}
 * would add a container's worth of startup to assert nothing extra about the security chain.
 */
public final class JwtRejectionContract {

    /**
     * The paths every assertion is run against.
     *
     * <p>{@code /graphql} is named explicitly — a forged JWT sent <b>directly</b> to
     * {@code :8082/graphql} must be rejected — because it is the path that carries
     * essentially all of the platform's traffic, so a service that authenticated everything
     * except its GraphQL endpoint would be wide open in practice while looking locked down. The
     * second path catches the reverse mistake: a chain whose {@code anyExchange()} rule was
     * loosened while the explicit {@code /graphql/**} matcher stayed strict.</p>
     */
    public static final List<String> PROBE_PATHS = List.of("/graphql", "/api/v1/probe");

    private final WebTestClient client;
    private final List<String> paths;

    private JwtRejectionContract(WebTestClient client, List<String> paths) {
        this.client = client;
        this.paths = paths;
    }

    /**
     * Bind a client to the filter chain a service's own {@code SecurityConfig} produces.
     *
     * @param chainFactory the service's {@code securityWebFilterChain(ServerHttpSecurity)} method
     */
    public static JwtRejectionContract against(
            Function<ServerHttpSecurity, SecurityWebFilterChain> chainFactory) {
        return against(chainFactory, PROBE_PATHS);
    }

    /**
     * As {@link #against(Function)}, but probing only the given paths.
     *
     * <p>For a chain that deliberately leaves some paths open: asserting "no credentials is
     * refused" on a path the chain permits would fail for the right reason and say nothing
     * about the token checks, so such a chain is probed on its authenticated paths and its open
     * paths are asserted separately.</p>
     */
    public static JwtRejectionContract against(
            Function<ServerHttpSecurity, SecurityWebFilterChain> chainFactory, List<String> paths) {

        SecurityWebFilterChain chain = chainFactory.apply(ServerHttpSecurity.http());
        RouterFunctions.Builder routes = RouterFunctions.route();
        paths.forEach(path ->
                routes.GET(path, request -> ServerResponse.ok().bodyValue("reached")));

        WebTestClient client = WebTestClient
                .bindToRouterFunction(routes.build())
                .webFilter(new WebFilterChainProxy(chain))
                .configureClient()
                .build();
        return new JwtRejectionContract(client, List.copyOf(paths));
    }

    /**
     * Assert every token-borne rejection, plus the acceptance that makes them meaningful, plus
     * the absence of credentials.
     *
     * <p>The valid-token case is not decoration. Without it, a chain that rejected
     * <em>everything</em> — a typo in the JWKS URL, a decoder that never resolves — would pass
     * the rejection assertions perfectly while authenticating nobody.</p>
     *
     * <p>The name predates the not-before case; it now also proves a token that is not yet
     * valid is refused, so every service that calls this picks the case up unchanged.</p>
     *
     * @param trusted  the realm the service under test trusts
     * @param rogue    a second realm it does not
     * @param audience the audience the service expects, or null when audience is unchecked
     */
    public void assertAllFour(StubIssuer trusted, StubIssuer rogue, String audience) {
        assertAllTokenRejections(trusted, rogue, audience);
        assertRejects(noTokenAtAll(),
                "no credentials at all");
    }

    /**
     * Every rejection that arrives as a bearer token, without the no-credentials case.
     *
     * <p>Split out for a path that is open to anonymous callers by design: a bad token sent
     * there must still be refused, because the bearer filter authenticates whatever token is
     * presented before the path rules are consulted, and that is worth proving separately from
     * whether the path needs a token at all.</p>
     */
    public void assertAllTokenRejections(StubIssuer trusted, StubIssuer rogue, String audience) {
        assertAccepts(trusted.validToken(audience), "a valid token from the trusted realm");

        assertRejects(trusted.forgedSignature(audience),
                "a token signed by a key the realm never published");
        assertRejects(rogue.validToken(audience),
                "a valid token from an untrusted realm");
        assertRejects(trusted.claimingIssuer(rogue.issuer(), audience),
                "a token with a verifiable signature that claims an untrusted issuer");
        assertRejects(trusted.expired(audience),
                "a token that expired beyond the allowed clock skew");
        assertRejectsNotYetValid(trusted, audience);
    }

    /** A correctly signed, unexpired token whose {@code nbf} is beyond the skew is refused. */
    public void assertRejectsNotYetValid(StubIssuer trusted, String audience) {
        assertRejects(trusted.notYetValid(audience),
                "a token whose not-before time is still in the future beyond the allowed clock skew");
    }

    /** The audience check, asserted separately because it is off unless configured. */
    public void assertRejectsWrongAudience(StubIssuer trusted, String someOtherClient) {
        assertRejects(trusted.wrongAudience(someOtherClient),
                "a token this realm minted for a different client");
    }

    public void assertAccepts(String token, String describedAs) {
        for (String path : paths) {
            assertThat(statusFor(path, token))
                    .as("the service must accept %s at %s — four rejections mean nothing if the "
                            + "chain also turns away everyone legitimate", describedAs, path)
                    .isEqualTo(200);
        }
    }

    public void assertRejects(String token, String describedAs) {
        for (String path : paths) {
            assertThat(statusFor(path, token))
                    .as("%s must be refused at %s — the service is reachable on the network "
                            + "without the gateway, and the gateway permits /graphql/** anyway",
                            describedAs, path)
                    .isEqualTo(401);
        }
    }

    private int statusFor(String path, String token) {
        return client.get().uri(path)
                .headers(headers -> {
                    if (token != null) {
                        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                    }
                })
                .exchange()
                .returnResult(Void.class)
                .getStatus()
                .value();
    }

    private static String noTokenAtAll() {
        return null;
    }
}
