package com.pml.shared.security.tenancy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How tenancy is established, and what happens when it cannot be.
 *
 * <p>Three behaviours here are load-bearing and none is obvious from the happy
 * path: the lookup runs at most once per request, an outage denies without lying
 * about why, and an administrator is not locked out by that outage.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("F-001 · TenantScopeWebFilter resolves tenancy once, and fails closed")
class TenantScopeWebFilterTest {

    private static final String SUBJECT = "user-42";
    private static final String ORG = "org-mine";

    @Test
    @DisplayName("ET-PLT-007 · an authenticated caller gets their active memberships")
    void resolvesMemberships() {
        TenantScope resolved = run(subject -> Mono.just(Set.of(ORG)), authenticated("ROLE_ORGANIZER"));

        assertThat(resolved.subject()).isEqualTo(SUBJECT);
        assertThat(resolved.permits(ORG)).isTrue();
        assertThat(resolved.platformAdmin()).isFalse();
    }

    @Test
    @DisplayName("ET-PLT-007 · an anonymous request permits nothing and costs no lookup")
    void anonymousDeniesWithoutLookup() {
        AtomicInteger lookups = new AtomicInteger();

        TenantScope resolved = runWithoutAuthentication(subject -> {
            lookups.incrementAndGet();
            return Mono.just(Set.of(ORG));
        });

        assertThat(resolved.permitsNothing()).isTrue();
        assertThat(lookups)
                .as("an unauthenticated request must not reach identity-service at all")
                .hasValue(0);
    }

    @Test
    @DisplayName("ET-PLT-007 · the membership lookup runs at most once per request")
    void lookupIsMemoisedPerRequest() {
        // A single request consults tenancy several times — resolver, service, and any
        // check on the way out. Without the cache in CurrentTenantScope.seed, each one
        // is another HTTP call to identity-service on the hot path.
        AtomicInteger lookups = new AtomicInteger();
        TenantMemberships counting = subject -> Mono.fromSupplier(() -> {
            lookups.incrementAndGet();
            return Set.of(ORG);
        });

        TenantScopeWebFilter filter = new TenantScopeWebFilter(counting);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/graphql"));

        // Three independent reads inside one request.
        Mono<Void> chain = CurrentTenantScope.get()
                .then(CurrentTenantScope.get())
                .then(CurrentTenantScope.get())
                .then();

        StepVerifier.create(filter.filter(exchange, ex -> chain)
                        .contextWrite(ReactiveSecurityContextHolder
                                .withAuthentication(authenticated("ROLE_ORGANIZER"))))
                .verifyComplete();

        assertThat(lookups).hasValue(1);
    }

    @Test
    @DisplayName("ET-PLT-007 · a failed lookup denies as an error, never as 'no memberships'")
    void lookupFailurePropagates() {
        // The distinction the whole TenantMemberships contract exists for. Both outcomes
        // deny; only this one is retryable, and only this one is true. Swallowing it
        // would tell every organizer on the platform that their own events do not exist
        // for as long as identity-service is unwell.
        TenantMemberships broken = subject -> Mono.error(new IllegalStateException("identity-service unreachable"));

        StepVerifier.create(scopeFrom(broken, authenticated("ROLE_ORGANIZER")))
                .verifyErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("identity-service unreachable"));
    }

    @Test
    @DisplayName("ET-PLT-007 · a platform administrator survives an identity-service outage")
    void administratorSurvivesOutage() {
        // An administrator's reach does not depend on membership data, and the console
        // they would use to diagnose the outage must not be the thing the outage closes.
        TenantMemberships broken = subject -> Mono.error(new IllegalStateException("down"));

        TenantScope resolved = run(broken, authenticated("ROLE_ADMIN"));

        assertThat(resolved.platformAdmin()).isTrue();
        assertThat(resolved.permits(ORG)).isTrue();
    }

    @Test
    @DisplayName("ET-PLT-007 · an ORGANIZER cannot become an administrator by claiming the role name")
    void organizerIsNotAdministrator() {
        TenantScope resolved = run(subject -> Mono.just(Set.of(ORG)),
                authenticated("ROLE_ORGANIZER", "ADMIN", "admin", "ROLE_ORG_ADMIN"));

        assertThat(resolved.platformAdmin())
                .as("only the exact platform authority grants the bypass")
                .isFalse();
        assertThat(resolved.permits("org-someone-else")).isFalse();
    }

    @Test
    @DisplayName("ET-PLT-007 · without the filter, reading the scope fails rather than allowing")
    void missingFilterFailsClosed() {
        StepVerifier.create(CurrentTenantScope.get())
                .verifyErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("TenantScopeWebFilter"));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static UsernamePasswordAuthenticationToken authenticated(String... authorities) {
        return new UsernamePasswordAuthenticationToken(SUBJECT, "n/a",
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
    }

    private static TenantScope run(TenantMemberships memberships, UsernamePasswordAuthenticationToken auth) {
        return scopeFrom(memberships, auth).block();
    }

    private static Mono<TenantScope> scopeFrom(
            TenantMemberships memberships, UsernamePasswordAuthenticationToken auth) {

        return capture(new TenantScopeWebFilter(memberships))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
    }

    private static TenantScope runWithoutAuthentication(TenantMemberships memberships) {
        return capture(new TenantScopeWebFilter(memberships)).block();
    }

    /**
     * Runs the filter and yields whatever scope the downstream chain would have seen.
     *
     * <p>The scope is read <em>inside</em> the chain. {@code CurrentTenantScope.get()}
     * is a deferred context read, so capturing the {@code Mono} and subscribing to it
     * after the filter returns resolves it against an empty context — which fails, and
     * fails identically to the wiring fault these tests exist to detect.
     */
    private static Mono<TenantScope> capture(TenantScopeWebFilter filter) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/graphql"));
        AtomicReference<TenantScope> seen = new AtomicReference<>();

        return filter.filter(exchange, ex -> CurrentTenantScope.get().doOnNext(seen::set).then())
                .then(Mono.fromSupplier(seen::get));
    }

    @Test
    @DisplayName("ET-PLT-007 · the filter is ordered after Spring Security's chain")
    void ordersAfterSecurity() {
        // It reads the principal Spring Security establishes. Ordered before that
        // chain it would see every request as anonymous and deny the whole platform.
        assertThat(new TenantScopeWebFilter(subject -> Mono.just(Set.of())).getOrder())
                .isEqualTo(TenantScopeWebFilter.ORDER);
    }
}
