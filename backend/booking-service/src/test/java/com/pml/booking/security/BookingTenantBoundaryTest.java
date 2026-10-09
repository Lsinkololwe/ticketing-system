package com.pml.booking.security;

import com.pml.shared.security.tenancy.TenancyProperties;
import com.pml.shared.security.tenancy.TenantScopeAutoConfiguration;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.domain.model.BankAccount;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.domain.model.PromoCode;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.infrastructure.client.CatalogServiceClient;
import com.pml.booking.migration.PromoCodeOwnershipBackfillMigrationService;
import com.pml.booking.repository.BankAccountRepository;
import com.pml.booking.repository.PayoutRequestRepository;
import com.pml.booking.repository.PromoCodeRepository;
import com.pml.booking.repository.TicketRepository;
import com.pml.shared.dto.EventSummaryDto;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantMemberships;
import com.pml.shared.security.tenancy.TenantScope;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Booking's organization-owned records are read only by their own organization, the buyer of a
 * ticket, or a platform-wide caller — against a MongoDB replica set, with the real derived queries.
 *
 * <p>Every refusal case first proves the record is reachable without the guard, so a pass means
 * the guard refused it, not that the fixture was empty. A refused record must be indistinguishable
 * from an id that was never issued.
 */
@Tag("L2")
@Tag("ET-PLT-007")
@DisplayName("Booking records stay inside their organization")
class BookingTenantBoundaryTest {

    private static final String OWNER_ORG = "org-owner";
    private static final String OTHER_ORG = "org-other";
    private static final String BUYER = "user-buyer";
    private static final String MEMBER = "user-member";
    private static final String OUTSIDER = "user-outsider";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static TicketRepository tickets;
    private static PromoCodeRepository promoCodes;
    private static BankAccountRepository bankAccounts;
    private static PayoutRequestRepository payoutRequests;

    private CatalogServiceClient catalog;
    private TenantReads reads;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "booking_tenant_boundary"));
        ReactiveMongoRepositoryFactory factory = new ReactiveMongoRepositoryFactory(template);
        tickets = factory.getRepository(TicketRepository.class);
        promoCodes = factory.getRepository(PromoCodeRepository.class);
        bankAccounts = factory.getRepository(BankAccountRepository.class);
        payoutRequests = factory.getRepository(PayoutRequestRepository.class);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        for (Class<?> type : List.of(Ticket.class, PromoCode.class, BankAccount.class, PayoutRequest.class, EventEscrowAccount.class)) {
            template.remove(new Query(), type).block();
        }
        tickets.save(Ticket.builder().id("ticket-1").ticketNumber("TKT-0001").eventId("event-1")
                .buyerId(BUYER).organizationId(OWNER_ORG).price(BigDecimal.TEN).build()).block();
        promoCodes.save(PromoCode.builder().id("promo-1").code("EARLY10").eventId("event-1")
                .organizationId(OWNER_ORG).build()).block();
        bankAccounts.save(BankAccount.builder().id("bank-1").organizerId("organizer-1")
                .organizationId(OWNER_ORG).accountNumber("+260970000003").build()).block();
        payoutRequests.save(PayoutRequest.builder().id("payout-1").requestId("PAY-0001").organizerId("organizer-1")
                .organizationId(OWNER_ORG).eventId("event-1").escrowAccountId("escrow-1").bankAccountId("bank-1")
                .requestedAmount(new BigDecimal("500.00")).settledAmount(new BigDecimal("500.00")).build()).block();

        catalog = Mockito.mock(CatalogServiceClient.class);
        EventSummaryDto event = new EventSummaryDto();
        event.setId("event-1");
        event.setOrganizationId(OWNER_ORG);
        when(catalog.getEventById("event-1")).thenReturn(Mono.just(event));
        when(catalog.getEventById("event-missing")).thenReturn(Mono.empty());
        reads = new TenantReads(tickets, promoCodes, bankAccounts, payoutRequests, catalog);
    }

    @Test
    @DisplayName("The buyer reads their own ticket without belonging to the organization")
    void aBuyerReadsTheirTicket() {
        Ticket ticket = as(BUYER, TenantScope.of(BUYER, Set.of()), reads.ticketForCaller("ticket-1")).block();
        assertThat(ticket.getId()).isEqualTo("ticket-1");
        assertThat(as(BUYER, TenantScope.of(BUYER, Set.of()), reads.ticketByNumberForCaller("TKT-0001")).block()).isNotNull();
    }

    @Test
    @DisplayName("A member of the owning organization reads every kind of record")
    void aMemberReadsEverything() {
        TenantScope member = TenantScope.of(MEMBER, Set.of(OWNER_ORG));
        assertThat(as(MEMBER, member, reads.ticketForCaller("ticket-1")).block()).isNotNull();
        assertThat(as(MEMBER, member, reads.promoCodeForCaller("promo-1")).block()).isNotNull();
        assertThat(as(MEMBER, member, reads.promoCodeByCodeForCaller("early10")).block()).isNotNull();
        assertThat(as(MEMBER, member, reads.bankAccountForCaller("bank-1")).block()).isNotNull();
        assertThat(as(MEMBER, member, reads.payoutRequestForCaller("payout-1")).block()).isNotNull();
        assertThat(as(MEMBER, member, reads.payoutRequestByRequestIdForCaller("PAY-0001")).block()).isNotNull();
        assertThat(as(MEMBER, member, reads.eventOrganizationForCaller("event-1")).block()).isEqualTo(OWNER_ORG);
    }

    @Test
    @DisplayName("Another organization is refused every record, with the answer an unissued id gets")
    void anotherOrganizationIsRefused() {
        assertThat(tickets.findById("ticket-1").block()).as("reachable without the guard").isNotNull();
        TenantScope outsider = TenantScope.of(OUTSIDER, Set.of(OTHER_ORG));

        assertRefusedLikeUnknown(outsider, reads.ticketForCaller("ticket-1"), reads.ticketForCaller("ticket-never"), ErrorCode.TICKET_UNKNOWN);
        assertRefusedLikeUnknown(outsider, reads.ticketByNumberForCaller("TKT-0001"), reads.ticketByNumberForCaller("TKT-9999"), ErrorCode.TICKET_UNKNOWN);
        assertRefusedLikeUnknown(outsider, reads.promoCodeForCaller("promo-1"), reads.promoCodeForCaller("promo-never"), ErrorCode.PROMO_CODE_UNKNOWN);
        assertRefusedLikeUnknown(outsider, reads.promoCodeByCodeForCaller("EARLY10"), reads.promoCodeByCodeForCaller("NOPE"), ErrorCode.PROMO_CODE_UNKNOWN);
        assertRefusedLikeUnknown(outsider, reads.bankAccountForCaller("bank-1"), reads.bankAccountForCaller("bank-never"), ErrorCode.BANK_ACCOUNT_UNKNOWN);
        assertRefusedLikeUnknown(outsider, reads.payoutRequestForCaller("payout-1"), reads.payoutRequestForCaller("payout-never"), ErrorCode.PAYOUT_REQUEST_UNKNOWN);
        assertRefusedLikeUnknown(outsider, reads.payoutRequestByRequestIdForCaller("PAY-0001"), reads.payoutRequestByRequestIdForCaller("PAY-9999"), ErrorCode.PAYOUT_REQUEST_UNKNOWN);
        assertRefusedLikeUnknown(outsider, reads.eventOrganizationForCaller("event-1"), reads.eventOrganizationForCaller("event-missing"), ErrorCode.EVENT_UNKNOWN);
    }

    @Test
    @DisplayName("An account in no organization reaches nothing it did not buy")
    void anAccountWithNoMembershipsReachesNothing() {
        TenantScope nobody = TenantScope.of(OUTSIDER, Set.of());
        assertRefusedLikeUnknown(nobody, reads.bankAccountForCaller("bank-1"), reads.bankAccountForCaller("bank-never"), ErrorCode.BANK_ACCOUNT_UNKNOWN);
    }

    @Test
    @DisplayName("A platform-wide caller reads any organization's record")
    void aPlatformCallerReadsAcrossOrganizations() {
        TenantScope admin = TenantScope.platformAdministrator("user-admin", Set.of());
        assertThat(as("user-admin", admin, reads.payoutRequestForCaller("payout-1")).block()).isNotNull();
        assertThat(as("user-admin", admin, reads.ticketForCaller("ticket-1")).block()).isNotNull();
    }

    @Test
    @DisplayName("Finance staff are platform-wide in booking; an organizer is not")
    void financeIsPlatformWide() {
        TenantMemberships memberships = subject -> Mono.just(Set.of(OWNER_ORG));
        var filter = new TenantScopeAutoConfiguration().tenantScopeWebFilter(memberships, bookingTenancy());

        assertThat(scopeInstalledFor(filter, "ROLE_FINANCE").platformAdmin()).isTrue();
        TenantScope organizer = scopeInstalledFor(filter, "ROLE_ORGANIZER");
        assertThat(organizer.platformAdmin()).isFalse();
        assertThat(organizer.organizationIds()).containsExactly(OWNER_ORG);
    }

    /** {@code platform.tenancy} as booking's own application.yml sets it, not as a test restates it. */
    private static TenancyProperties bookingTenancy() {
        try {
            var sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
            return new Binder(ConfigurationPropertySources.from(sources))
                    .bindOrCreate("platform.tenancy", TenancyProperties.class);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("Codes without an organization take their event's organization from its escrow account, once")
    void promoCodesAreBackfilled() {
        template.save(PromoCode.builder().id("promo-legacy").code("OLD5").eventId("event-1").build()).block();
        template.save(PromoCode.builder().id("promo-orphan").code("LOST5").eventId("event-no-escrow").build()).block();
        template.save(EventEscrowAccount.builder().id("escrow-1").eventId("event-1").organizationId(OWNER_ORG).build()).block();
        @SuppressWarnings("unchecked")
        ObjectProvider<ReactiveMongoTemplate> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(template);
        PromoCodeOwnershipBackfillMigrationService migration = new PromoCodeOwnershipBackfillMigrationService(provider);

        assertThat(migration.migrate().block()).isEqualTo(1L);
        assertThat(migration.migrate().block()).as("a second run changes nothing").isZero();

        assertThat(promoCodes.findById("promo-legacy").block().getOrganizationId()).isEqualTo(OWNER_ORG);
        assertThat(promoCodes.findById("promo-orphan").block().getOrganizationId()).isNull();
        assertThat(promoCodes.findById("promo-1").block().getOrganizationId()).isEqualTo(OWNER_ORG);
    }

    private static TenantScope scopeInstalledFor(com.pml.shared.security.tenancy.TenantScopeWebFilter filter, String role) {
        AtomicReference<TenantScope> seen = new AtomicReference<>();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user-" + role).build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(role)));
        filter.filter(MockServerWebExchange.from(MockServerHttpRequest.get("/graphql")),
                        exchange -> CurrentTenantScope.get().doOnNext(seen::set).then())
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
                .block();
        return seen.get();
    }

    private static <T> Mono<T> as(String subject, TenantScope scope, Mono<T> call) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).build();
        return call
                .contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope)))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt, List.of())));
    }

    private static void assertRefusedLikeUnknown(TenantScope scope, Mono<?> someoneElses, Mono<?> neverIssued, ErrorCode code) {
        DomainRefusal refusedOwned = refusal(scope, someoneElses);
        DomainRefusal refusedUnknown = refusal(scope, neverIssued);
        assertThat(refusedOwned.errorCode()).isEqualTo(code);
        assertThat(refusedUnknown.errorCode()).isEqualTo(code);
        assertThat(refusedOwned.details()).as("the refusal carries nothing that tells the two apart")
                .isEqualTo(refusedUnknown.details());
    }

    private static DomainRefusal refusal(TenantScope scope, Mono<?> call) {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        assertThatThrownBy(() -> as(scope.subject(), scope, call).block())
                .satisfies(thrown::set)
                .isInstanceOf(DomainRefusal.class);
        return (DomainRefusal) thrown.get();
    }
}
