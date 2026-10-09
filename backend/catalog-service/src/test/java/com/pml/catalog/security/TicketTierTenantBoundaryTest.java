package com.pml.catalog.security;

import com.pml.catalog.testing.CatalogWiring;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.domain.model.TicketTier;
import com.pml.catalog.repository.TicketTierRepository;
import com.pml.catalog.service.impl.TicketTierServiceImpl;
import com.pml.catalog.web.graphql.dto.UpdateTicketTierInput;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.GraphQlErrors;
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
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * An organizer cannot reach another organization's ticket tier.
 * OWASP A01:2021 · CWE-639 (authorization bypass through a user-controlled key).
 *
 * <h2>The vulnerability this proves closed</h2>
 * {@code updateTicketTier} and {@code deleteTicketTier} carried
 * {@code @PreAuthorize("hasAnyRole('ADMIN','ORGANIZER')")} and then called
 * {@code tierService.updateTier(tierId, …)} — a bare {@code findById} with no
 * ownership comparison anywhere beneath it. Any account holding the
 * {@code ORGANIZER} realm role could reprice or delete any other organization's
 * tiers by id. A role answers "may this account edit ticket tiers"; the question
 * is "may it edit <em>this</em> one", and no role can answer that.
 *
 * <h2>Against a container, because the boundary is a query</h2>
 * The fix puts the filter in the query — {@code findByIdAndOrganizationIdIn} —
 * rather than in a comparison a call site must remember. That is a claim about
 * what MongoDB returns, so a mocked repository would be asserting the mock. This
 * runs the real derived query against the real driver on {@link MongoReplicaSet}.
 *
 * <h2>Each test proves the guard, not the fixture</h2>
 * Every refusal case first asserts the row <em>is</em> reachable by unscoped id.
 * Without that, a test passes just as happily when the fixture never wrote the
 * tier, when the id is misspelled, or when some unrelated error fires first — and
 * a green security test that would pass against the vulnerable code is worse than
 * no test, because it is cited as proof.
 */
@Tag("L2")
@Tag("ET-PLT-007")
    // The class also iterates ids across two organizations and asserts the response is
    // identical for a non-existent id and for another tenant's id. That property belongs to
    // the error contract as much as to the tenant boundary — one decides who may read, the
    // other that the refusal discloses nothing — so the class carries both tags.
@Tag("ET-PLT-005")
@DisplayName("F-001 · a tier is reachable only from the organization that owns it")
class TicketTierTenantBoundaryTest {

    private static final String OWNER_ORG = "org-kabwe-collective";
    private static final String OTHER_ORG = "org-lusaka-live";
    private static final String OWNED_TIER = "tier-owned-by-kabwe";
    private static final String NEVER_ISSUED = "tier-that-was-never-issued";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static TicketTierRepository tiers;
    private static TicketTierServiceImpl service;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "catalog_tenant_boundary"));

        // The real derived query, not a stub: findByIdAndOrganizationIdIn is the boundary.
        tiers = new ReactiveMongoRepositoryFactory(template).getRepository(TicketTierRepository.class);

        com.pml.catalog.repository.EventRepository events =
                new ReactiveMongoRepositoryFactory(template).getRepository(com.pml.catalog.repository.EventRepository.class);
        service = new TicketTierServiceImpl(
                tiers,
                Clock.fixed(Instant.parse("2026-08-31T09:00:00Z"), ZoneOffset.UTC),
                events,
                new EventWriteGuard(events, org.mockito.Mockito.mock(com.pml.catalog.infrastructure.client.IdentityServiceClient.class)),
                // The real template: applyCapacityChange moves quantity atomically through it,
                // and a null here would pass every test in this class while the capacity path
                // threw at runtime.
                template,
                CatalogWiring.tierFactory(),
                CatalogWiring.mirror(template, Clock.fixed(Instant.parse("2026-08-31T09:00:00Z"), ZoneOffset.UTC)));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seedOneTierOwnedByKabwe() {
        template.remove(new Query(), TicketTier.class).block();

        TicketTier tier = new TicketTier();
        tier.setId(OWNED_TIER);
        tier.setEventId("event-kabwe-jazz-night");
        tier.setOrganizationId(OWNER_ORG);
        tier.setCode("EARLY");
        tier.setName("Early bird");
        tier.setPrice(new BigDecimal("150.00"));
        tier.setQuantity(200);
        tier.setAvailableQuantity(200);
        template.save(tier).block();
    }

    // ── the attack ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("a member of another organization")
    class Outsider {

        private final TenantScope outsider = TenantScope.of("user-outsider", Set.of(OTHER_ORG));

        @Test
        @DisplayName("cannot reprice the tier, and the tier is unchanged")
        void cannotReprice() {
            // The row is reachable by id. Without this the refusal below proves nothing.
            assertThat(tiers.findById(OWNED_TIER).block()).isNotNull();

            UpdateTicketTierInput repriceToNothing = priceOf(new BigDecimal("1.00"));

            assertThatThrownBy(() -> as(outsider, service.updateTier(OWNED_TIER, repriceToNothing)).block())
                    .isInstanceOf(DomainRefusal.class)
                    .satisfies(thrown ->
                            assertThat(((DomainRefusal) thrown).errorCode()).isEqualTo(ErrorCode.TIER_UNKNOWN));

            // A refused operation persists nothing — the price the buyer sees is untouched.
            assertThat(tiers.findById(OWNED_TIER).block().getPrice())
                    .isEqualByComparingTo(new BigDecimal("150.00"));
        }

        @Test
        @DisplayName("cannot delete the tier, and the tier survives")
        void cannotDelete() {
            assertThat(tiers.findById(OWNED_TIER).block()).isNotNull();

            assertThatThrownBy(() -> as(outsider, service.deleteTier(OWNED_TIER)).block())
                    .isInstanceOf(DomainRefusal.class);

            assertThat(tiers.count().block()).isEqualTo(1L);
        }

        @Test
        @DisplayName("cannot tell the tier apart from an id that was never issued")
        void refusalIsIndistinguishable() {
            // The requirement that makes the boundary more than a lock: if the two answers
            // differ in any way the caller can observe, the error is an oracle and anyone
            // holding a list of candidate ids learns which are real without any access.
            DomainRefusal onSomeoneElses = refusalFrom(outsider, OWNED_TIER);
            DomainRefusal onNeverIssued = refusalFrom(outsider, NEVER_ISSUED);

            // Everything the client receives, and this is the whole of it: the handler
            // renders the message from the code alone and never copies getMessage().
            assertThat(onSomeoneElses.errorCode()).isEqualTo(ErrorCode.TIER_UNKNOWN);
            assertThat(onNeverIssued.errorCode()).isEqualTo(ErrorCode.TIER_UNKNOWN);
            assertThat(GraphQlErrors.refusalMessage(onSomeoneElses.errorCode()))
                    .isEqualTo(GraphQlErrors.refusalMessage(onNeverIssued.errorCode()));
            assertThat(onSomeoneElses.details()).isEqualTo(onNeverIssued.details()).isEmpty();

            // The developer message differs — it names the id the caller asked for — and
            // that is safe only because it is log-only. Pin it: the day a handler starts
            // copying getMessage() onto the wire (CWE-209), this stops being safe and nothing
            // else would say so.
            assertThat(GraphQlErrors.refusalMessage(ErrorCode.TIER_UNKNOWN))
                    .doesNotContain(OWNED_TIER, OWNER_ORG, NEVER_ISSUED);

            // Neither refusal names the owner or hints that a permission was the problem,
            // even in the log line.
            assertThat(onSomeoneElses.getMessage())
                    .doesNotContain(OWNER_ORG, "permission", "not permitted");
        }
    }

    @Nested
    @DisplayName("an account with no organizations")
    class Unaffiliated {

        @Test
        @DisplayName("reaches nothing, even holding a valid tier id")
        void reachesNothing() {
            assertThat(tiers.findById(OWNED_TIER).block()).isNotNull();

            TenantScope none = TenantScope.of("user-fresh-signup", Set.of());

            assertThatThrownBy(() -> as(none, service.updateTier(OWNED_TIER, priceOf(BigDecimal.ONE))).block())
                    .isInstanceOf(DomainRefusal.class);
        }
    }

    // ── the legitimate paths, which must keep working ───────────────────────

    @Nested
    @DisplayName("the organization that owns the tier")
    class Owner {

        @Test
        @DisplayName("can still reprice it")
        void canReprice() {
            TenantScope owner = TenantScope.of("user-kabwe-manager", Set.of(OWNER_ORG));

            StepVerifier.create(as(owner, service.updateTier(OWNED_TIER, priceOf(new BigDecimal("175.00")))))
                    .assertNext(tier -> assertThat(tier.getPrice()).isEqualByComparingTo("175.00"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("is not blocked by also belonging to other organizations")
        void multipleMembershipsStillReach() {
            // ActorOrganizationResolver returns organizations.get(0) for a multi-org user,
            // so whether this passes depends on iteration order there. Carrying the whole
            // set is what makes it deterministic.
            TenantScope both = TenantScope.of("user-consultant", Set.of(OTHER_ORG, OWNER_ORG));

            StepVerifier.create(as(both, service.updateTier(OWNED_TIER, priceOf(new BigDecimal("180.00")))))
                    .assertNext(tier -> assertThat(tier.getPrice()).isEqualByComparingTo("180.00"))
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("a platform administrator reaches it without any membership")
    void platformAdministratorBypasses() {
        TenantScope admin = TenantScope.platformAdministrator("user-platform-ops", Set.of());

        StepVerifier.create(as(admin, service.updateTier(OWNED_TIER, priceOf(new BigDecimal("199.00")))))
                .assertNext(tier -> assertThat(tier.getPrice()).isEqualByComparingTo("199.00"))
                .verifyComplete();
    }

    @Test
    @DisplayName("without a scope in the context the call fails rather than proceeding")
    void missingScopeFailsClosed() {
        // The wiring fault has to break the service, not the boundary. If an absent
        // TenantScope quietly meant "no restriction", forgetting the filter in one
        // deployment would open cross-tenant access everywhere with nothing to show for it.
        assertThatThrownBy(() -> service.updateTier(OWNED_TIER, priceOf(BigDecimal.TEN)).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TenantScopeWebFilter");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** Runs the operation as {@code scope}, the way the filter would have seeded it. */
    private static <T> Mono<T> as(TenantScope scope, Mono<T> operation) {
        return operation.contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope)));
    }

    private static DomainRefusal refusalFrom(TenantScope scope, String tierId) {
        try {
            as(scope, service.updateTier(tierId, priceOf(BigDecimal.ONE))).block();
        } catch (DomainRefusal refusal) {
            return refusal;
        }
        throw new AssertionError("expected a refusal for tier " + tierId + " but the call succeeded");
    }

    private static UpdateTicketTierInput priceOf(BigDecimal price) {
        return new UpdateTicketTierInput(
                null, null, price, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }
}
