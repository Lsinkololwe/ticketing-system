package com.pml.booking.security;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.repository.PayoutRequestRepository;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantScope;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A caller-scoped read returns the caller's rows and no others.
 * OWASP A01:2021 · CWE-639.
 *
 * <h2>What separates this from the operations it sits beside</h2>
 * booking also has {@code payoutRequestsByOrganizer(organizerId)}: it returns the right rows
 * for whichever id the client sends, and nothing in it consults the token. A caller-scoped read
 * answers a different question — the caller's own rows — and the difference only shows up when
 * the client sends an id that is not theirs.
 *
 * <p>So every case here sends an id belonging to somebody else, and asserts the answer is
 * indistinguishable from asking after an organization that does not exist. The
 * {@code organizationId} argument is a <b>selector</b> over memberships the token established; it
 * must be able to narrow and never to widen.
 *
 * <h2>Against a replica set, because the filter is a query</h2>
 * The boundary is {@code findByOrganizationIdIn}, a derived Mongo query. A mocked repository
 * would assert the mock. Every case below first proves the other tenant's row <em>is</em> present
 * and reachable unscoped — without that, a refusal proves nothing but an empty fixture.
 */
@Tag("L2")
@Tag("ET-PLT-007")
@DisplayName("F-001 · myPayoutRequests narrows to the caller and cannot be widened")
class CallerScopedReadTest {

    private static final String MINE = "org-kabwe-collective";
    private static final String ALSO_MINE = "org-ndola-nights";
    private static final String THEIRS = "org-lusaka-live";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static PayoutRequestRepository payouts;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "booking_caller_scope"));
        payouts = new ReactiveMongoRepositoryFactory(template).getRepository(PayoutRequestRepository.class);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seedThreeOrganizations() {
        template.remove(new Query(), PayoutRequest.class).block();
        save("payout-mine-1", MINE);
        save("payout-mine-2", MINE);
        save("payout-also-mine", ALSO_MINE);
        save("payout-theirs-1", THEIRS);
        save("payout-theirs-2", THEIRS);
    }

    private static void save(String id, String organizationId) {
        PayoutRequest r = new PayoutRequest();
        r.setId(id);
        r.setOrganizationId(organizationId);
        r.setOrganizerId("user-" + organizationId);
        r.setRequestedAmount(new BigDecimal("500.00"));
        r.setRequestedAt(Instant.parse("2026-09-01T10:00:00Z"));
        template.save(r).block();
    }

    @Nested
    @DisplayName("with no selector")
    class NoSelector {

        @Test
        @DisplayName("ET-PLT-007 · a member of two organizations sees both, and only those")
        void seesEveryOrganizationOfTheirOwn() {
            // The multi-organization case ActorOrganizationResolver gets wrong by returning
            // organizations.get(0): one of these two would silently vanish.
            List<String> seen = idsFor(TenantScope.of("u", Set.of(MINE, ALSO_MINE)), null);

            assertThat(seen).containsExactlyInAnyOrder("payout-mine-1", "payout-mine-2", "payout-also-mine");
            assertThat(seen).doesNotContain("payout-theirs-1", "payout-theirs-2");
        }

        @Test
        @DisplayName("ET-PLT-007 · a caller with no memberships is refused, not shown an empty page")
        void noMembershipsIsRefused() {
            // An empty page would say "you have no payouts". A refusal says nothing at all,
            // which is the only honest answer to a caller who cannot see the resource.
            assertThatThrownBy(() -> idsFor(TenantScope.of("u", Set.of()), null))
                    .isInstanceOf(DomainRefusal.class);
        }
    }

    @Nested
    @DisplayName("with a selector")
    class WithSelector {

        @Test
        @DisplayName("ET-PLT-007 · selecting one of my organizations narrows to it")
        void narrowsToTheSelectedOrganization() {
            List<String> seen = idsFor(TenantScope.of("u", Set.of(MINE, ALSO_MINE)), MINE);

            assertThat(seen).containsExactlyInAnyOrder("payout-mine-1", "payout-mine-2");
            assertThat(seen).doesNotContain("payout-also-mine");
        }

        @Test
        @DisplayName("ET-PLT-007 · selecting somebody else's organization refuses, and reveals nothing")
        void cannotSelectAnotherTenant() {
            // The row is present and reachable unscoped. Without this the refusal below would
            // pass just as happily against an empty collection.
            assertThat(payouts.findById("payout-theirs-1").block()).isNotNull();

            DomainRefusal onTheirs = refusal(TenantScope.of("u", Set.of(MINE)), THEIRS);
            DomainRefusal onNothing = refusal(TenantScope.of("u", Set.of(MINE)), "org-never-issued");

            assertThat(onTheirs.errorCode()).isEqualTo(onNothing.errorCode());
            assertThat(onTheirs.details()).isEqualTo(onNothing.details()).isEmpty();
        }

        @Test
        @DisplayName("ET-PLT-007 · the selector cannot widen beyond the caller's memberships")
        void selectorNeverGrants() {
            // The distinction from payoutRequestsByOrganizer(organizerId), which answers for
            // whatever id arrives. Here the argument may only ever narrow a set the token fixed.
            assertThatThrownBy(() -> idsFor(TenantScope.of("u", Set.of(MINE)), THEIRS))
                    .isInstanceOf(DomainRefusal.class);
            assertThat(idsFor(TenantScope.of("u", Set.of(MINE)), null))
                    .as("without a selector the caller still sees only their own")
                    .containsExactlyInAnyOrder("payout-mine-1", "payout-mine-2");
        }
    }

    @Test
    @DisplayName("ET-PLT-007 · a platform administrator reads across organizations")
    void administratorIsUnscoped() {
        Set<String> ids = CallerScope.organizationIds(null, ErrorCode.ORGANIZATION_UNKNOWN)
                .contextWrite(ctx -> CurrentTenantScope.seed(ctx,
                        Mono.just(TenantScope.platformAdministrator("ops", Set.of()))))
                .block();

        assertThat(ids).as("an empty set here means unscoped, and only an administrator gets it").isEmpty();
    }

    @Test
    @DisplayName("ET-PLT-007 · without a scope in the context the read fails rather than proceeding")
    void missingScopeFailsClosed() {
        assertThatThrownBy(() -> CallerScope.organizationIds(MINE, ErrorCode.ORGANIZATION_UNKNOWN).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TenantScopeWebFilter");
    }


    @Nested
    @DisplayName("subject-scoped reads")
    class SubjectScoped {

        @Test
        @DisplayName("ET-PLT-007 · the subject comes from the token, and there is no argument to override it")
        void subjectIsTheToken() {
            String subject = CallerScope.subject()
                    .contextWrite(ctx -> CurrentTenantScope.seed(ctx,
                            Mono.just(TenantScope.of("user-buyer-7", Set.of()))))
                    .block();

            assertThat(subject)
                    .as("""
                        ET-FIN-004 declares myRefundRequests AUTHENTICATED with no id argument. \
                        A buyer is not a tenant, so this deliberately ignores memberships — the \
                        caller above has none and is still answered.""")
                    .isEqualTo("user-buyer-7");
        }

        @Test
        @DisplayName("ET-PLT-007 · an unauthenticated caller is told so, not told their refunds do not exist")
        void unauthenticatedSaysSo() {
            // TenantBoundary's disguise is for cross-tenant reaches, where a supplied id would
            // otherwise be confirmed. Nothing is supplied here, so there is no oracle to close —
            // and answering *_UNKNOWN would tell a signed-out user their own refunds are gone.
            assertThatThrownBy(() -> CallerScope.subject()
                    .contextWrite(ctx -> CurrentTenantScope.seed(ctx,
                            Mono.just(TenantScope.denyAll(null))))
                    .block())
                    .isInstanceOf(DomainRefusal.class)
                    .satisfies(t -> assertThat(((DomainRefusal) t).errorCode())
                            .isEqualTo(ErrorCode.ACTOR_NOT_AUTHENTICATED));
        }

        @Test
        @DisplayName("ET-PLT-007 · without a scope in the context the read fails rather than proceeding")
        void missingScopeFailsClosedForSubject() {
            assertThatThrownBy(() -> CallerScope.subject().block())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TenantScopeWebFilter");
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static List<String> idsFor(TenantScope scope, String selector) {
        return CallerScope.organizationIds(selector, ErrorCode.ORGANIZATION_UNKNOWN)
                .flatMapMany(ids -> ids.isEmpty()
                        ? reactor.core.publisher.Flux.<PayoutRequest>empty()
                        : payouts.findByOrganizationIdIn(ids))
                .map(PayoutRequest::getId)
                .collectList()
                .contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope)))
                .block();
    }

    private static DomainRefusal refusal(TenantScope scope, String selector) {
        try {
            idsFor(scope, selector);
        } catch (DomainRefusal refused) {
            return refused;
        }
        throw new AssertionError("expected a refusal for selector " + selector);
    }
}
