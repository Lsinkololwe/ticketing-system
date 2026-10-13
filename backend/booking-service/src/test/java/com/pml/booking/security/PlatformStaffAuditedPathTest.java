package com.pml.booking.security;

import com.mongodb.reactivestreams.client.MongoClient;
import com.pml.booking.domain.enums.BookingStatus;
import com.pml.booking.domain.enums.RecoveryAction;
import com.pml.booking.domain.model.Booking;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.domain.model.RecoveryProposal;
import com.pml.booking.domain.model.RefundRequest;
import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.booking.it.BookingFixture;
import com.pml.booking.it.BookingFixture.World;
import com.pml.booking.service.BookingReads;
import com.pml.booking.service.BookingStore;
import com.pml.booking.service.ChargebackService;
import com.pml.booking.service.DualControlService;
import com.pml.booking.service.EscrowService;
import com.pml.booking.service.PayoutRecoveryService;
import com.pml.booking.service.PayoutRequestService;
import com.pml.booking.service.PaymentOutcomeService;
import com.pml.booking.service.PlatformTransferService;
import com.pml.booking.service.ReservationService;
import com.pml.booking.service.SalesAnalytics;
import com.pml.booking.web.graphql.dto.CreatePayoutRequestInput;
import com.pml.booking.web.graphql.mutation.PayoutRequestMutationResolver;
import com.pml.booking.web.graphql.mutation.RefundRequestMutationResolver;
import com.pml.booking.web.graphql.query.ReservationQueryResolver;
import com.pml.booking.workflow.payout.PayoutProcess;
import com.pml.booking.workflow.refund.RefundProcess;
import com.pml.shared.constants.ReservationStatus;
import com.pml.shared.dto.authorization.AuthorizationResult;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.security.tenancy.PlatformWideAccess;
import com.pml.shared.security.tenancy.PlatformWideAuditRecord;
import com.pml.shared.security.tenancy.TenantScope;
import com.pml.shared.testing.IdempotencyPassthrough;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The places platform staff, finance included, act on someone else's records now answer
 * through the one recorded path: the same people are let in and kept out as before, and every
 * allowed use leaves an audit record naming who, in which role, for which reason.
 * OWASP A01:2021 · A09:2021.
 */
@Tag("L2")
@Tag("ET-PLT-007")
@Tag("ET-PLT-009")
@DisplayName("ET-PLT-007 · booking's staff checks admit admin, finance and super admin exactly as before, and record each use")
class PlatformStaffAuditedPathTest {

    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");
    private static final String RUN = UUID.randomUUID().toString().substring(0, 8);
    private static final String ORG = "org-" + RUN;
    private static final String EVENT = "ev-" + RUN;
    private static final String ORGANIZER = "organizer-" + RUN;
    private static final String BUYER = "buyer-" + RUN;
    private static final String OTHER_BUYER = "other-buyer-" + RUN;

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static World world;
    private static BookingReads bookings;
    private static SalesAnalytics analytics;
    private static DualControlService dual;
    private static ChargebackService chargebacks;
    private static Booking booking;

    private final List<PlatformWideAuditRecord> rows = new ArrayList<>();
    private PlatformWideAccess access;

    @BeforeAll
    static void seed() {
        client = BookingFixture.newClient(MongoReplicaSet.connectionString());
        template = BookingFixture.template(client, "booking_platform_staff_audit");
        world = new World();
        world.event(EVENT, ORG);
        world.grant(ORGANIZER, EVENT);
        world.grantOrganization(ORGANIZER, ORG);
        var clock = TestClock.frozenAt(NOW);
        bookings = new BookingReads(new BookingStore(template, clock), template, world.access);
        analytics = new SalesAnalytics(template, world.access, clock);
        booking = template.insert(Booking.builder().bookingNumber("BK-" + RUN).reservationId("res-" + RUN).eventId(EVENT)
                .organizationId(ORG).buyerId(BUYER).contactName("Buyer").contactEmail(BUYER + "@example.com")
                .status(BookingStatus.CONFIRMED).totalAmount(BigDecimal.TEN).ticketCount(1).createdAt(NOW.minusSeconds(60))
                .build()).block();
        chargebacks = mock(ChargebackService.class);
        dual = new DualControlService(template, clock, mock(PaymentOutcomeService.class), chargebacks,
                mock(PlatformTransferService.class));
    }

    @AfterAll
    static void stop() {
        client.close();
    }

    @BeforeEach
    void freshSink() {
        rows.clear();
        template.remove(new Query(), RecoveryProposal.class).block();
        access = new PlatformWideAccess("booking-service", record -> {
            rows.add(record);
            return Mono.empty();
        }, java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC), Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_FINANCE"));
    }

    /** One request: the caller's authorities, their tenant scope as the filter would build it, and the recording instance. */
    private <T> Mono<T> as(String userId, Mono<T> call, String... roles) {
        boolean tenantWide = Arrays.stream(roles).anyMatch(r -> r.equals("ROLE_ADMIN") || r.equals("ROLE_SUPER_ADMIN")
                || r.equals("ROLE_FINANCE"));
        TenantScope scope = tenantWide ? TenantScope.platformAdministrator(userId, Set.of())
                : TenantScope.of(userId, Set.of(ORG));
        Jwt jwt = Jwt.withTokenValue("token-material").header("alg", "none").subject(userId).build();
        return call.contextWrite(ctx -> PlatformWideAccess.seed(ctx, access))
                .contextWrite(ctx -> com.pml.shared.security.tenancy.CurrentTenantScope.seed(ctx, Mono.just(scope)))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt,
                        Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList())));
    }

    private void assertOneRecord(String actor, String role, PlatformWideAccess.Reason reason) {
        assertThat(rows).as("one record for %s", reason).hasSize(1);
        assertThat(rows.get(0).actorSub()).isEqualTo(actor);
        assertThat(rows.get(0).role()).isEqualTo(role);
        assertThat(rows.get(0).reason()).isEqualTo(reason);
        assertThat(rows.get(0).service()).isEqualTo("booking-service");
        assertThat(rows.get(0).at()).isEqualTo(NOW);
        assertThat(rows.get(0).toString()).doesNotContain("token-material");
    }

    // ---- site 1: OrganizerAccess.isPlatformStaff, through BookingReads ---------------------------------

    @Test
    @DisplayName("a buyer's bookings are read by admin, finance and super admin and recorded; an organizer or another buyer is refused with no record")
    void buyersBookings() {
        for (String[] staff : new String[][]{{"ROLE_ADMIN"}, {"ROLE_FINANCE"}, {"ROLE_SUPER_ADMIN"}}) {
            rows.clear();
            var page = as("staff-1", bookings.byBuyer(BUYER, null, null), staff).block();
            assertThat(page.data()).extracting(Booking::getId).contains(booking.getId());
            assertOneRecord("staff-1", staff[0], PlatformWideAccess.Reason.BUYER_BOOKINGS_READ);
        }

        rows.clear();
        assertThat(BookingFixture.refusal(as(ORGANIZER, bookings.byBuyer(BUYER, null, null), "ROLE_ORGANIZER")).errorCode())
                .isEqualTo(ErrorCode.BOOKING_UNKNOWN);
        assertThat(BookingFixture.refusal(as(OTHER_BUYER, bookings.byBuyer(BUYER, null, null), "ROLE_CUSTOMER")).errorCode())
                .isEqualTo(ErrorCode.BOOKING_UNKNOWN);
        assertThat(rows).as("a refusal is not a reach").isEmpty();

        assertThat(as(BUYER, bookings.byBuyer(BUYER, null, null), "ROLE_CUSTOMER").block().data()).hasSize(1);
        assertThat(rows).as("a buyer reading their own is not a reach").isEmpty();
    }

    @Test
    @DisplayName("the full contact is seen by the buyer and by staff of all three roles, recorded once per request, and by no organizer")
    void fullContact() {
        for (String role : List.of("ROLE_ADMIN", "ROLE_FINANCE", "ROLE_SUPER_ADMIN")) {
            rows.clear();
            // asked three times, as the contact, phone and payment fields of one booking do
            Mono<Boolean> asked = BookingReads.seesFullContact(booking).then(BookingReads.seesFullContact(booking))
                    .then(BookingReads.seesFullContact(booking));
            assertThat(as("staff-2", asked, role).block()).isTrue();
            assertOneRecord("staff-2", role, PlatformWideAccess.Reason.BOOKING_CONTACT_READ);
        }

        rows.clear();
        assertThat(as(ORGANIZER, BookingReads.seesFullContact(booking), "ROLE_ORGANIZER").block()).isFalse();
        assertThat(as(OTHER_BUYER, BookingReads.seesFullContact(booking), "ROLE_CUSTOMER").block()).isFalse();
        assertThat(as(BUYER, BookingReads.seesFullContact(booking), "ROLE_CUSTOMER").block()).isTrue();
        assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("platform-wide sales analytics are for staff of all three roles and recorded; an organizer who names nothing is refused")
    void platformSales() {
        for (String role : List.of("ROLE_ADMIN", "ROLE_FINANCE", "ROLE_SUPER_ADMIN")) {
            rows.clear();
            assertThat(as("staff-3", analytics.purchasesByDayAndHour(null, null, null, null), role).block()).isNotNull();
            assertOneRecord("staff-3", role, PlatformWideAccess.Reason.PLATFORM_SALES_ANALYTICS_READ);
        }

        rows.clear();
        assertThat(BookingFixture.refusal(as(ORGANIZER, analytics.purchasesByDayAndHour(null, null, null, null),
                "ROLE_ORGANIZER")).errorCode()).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(BookingFixture.refusal(as(BUYER, analytics.purchasesByDayAndHour(null, null, null, null),
                "ROLE_CUSTOMER")).errorCode()).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("staff may withdraw any refund request, recorded; a buyer withdraws only their own through the requester path")
    void refundCancellation() {
        RefundProcess process = mock(RefundProcess.class);
        when(process.cancel(anyString(), anyString(), any())).thenReturn(Mono.just(new RefundRequest()));
        when(process.cancelAsRequester(anyString(), anyString(), any())).thenReturn(Mono.just(new RefundRequest()));
        var resolver = new RefundRequestMutationResolver(process, IdempotencyPassthrough.guard(),
                new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());

        for (String role : List.of("ROLE_ADMIN", "ROLE_FINANCE", "ROLE_SUPER_ADMIN")) {
            rows.clear();
            as("staff-4", resolver.cancelRefundRequest("rr-1", "customer asked by phone"), role).block();
            assertOneRecord("staff-4", role, PlatformWideAccess.Reason.REFUND_REQUEST_CANCEL);
        }
        verify(process, Mockito.times(3)).cancel("rr-1", "staff-4", "customer asked by phone");

        rows.clear();
        as(BUYER, resolver.cancelRefundRequest("rr-2", "changed my mind"), "ROLE_CUSTOMER").block();
        verify(process).cancelAsRequester("rr-2", BUYER, "changed my mind");
        verify(process, never()).cancel("rr-2", BUYER, "changed my mind");
        assertThat(rows).isEmpty();
    }

    // ---- site 2: PayoutRequestMutationResolver — no staff path ---------------------------------------

    private PayoutRequestMutationResolver payoutResolver(PayoutProcess process, IdentityServiceClient identity) {
        EscrowService escrows = mock(EscrowService.class);
        EventEscrowAccount escrow = new EventEscrowAccount();
        escrow.setId("escrow-1");
        escrow.setEventId(EVENT);
        escrow.setOrganizationId(ORG);
        when(escrows.findById("escrow-1")).thenReturn(Mono.just(escrow));
        return new PayoutRequestMutationResolver(mock(PayoutRequestService.class), mock(com.pml.booking.security.TenantReads.class),
                mock(PayoutRecoveryService.class), escrows, new PayoutAccess(identity), process,
                IdempotencyPassthrough.guard(), new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
    }

    private static CreatePayoutRequestInput payoutInput() {
        return new CreatePayoutRequestInput(ORGANIZER, EVENT, "escrow-1", "bank-1", new BigDecimal("500.00"), "ZMW",
                null, null, null, "idem-" + System.nanoTime());
    }

    @Test
    @DisplayName("no staff role can request a payout for an organizer and the refusal leaves no record; a member who holds the permission still can")
    void payoutIsRequestedOnlyByAMemberOfTheOrganization() {
        IdentityServiceClient identity = mock(IdentityServiceClient.class);
        when(identity.checkAuthorization(any())).thenReturn(Mono.just(AuthorizationResult.authorizedAsOwner(ORG)));
        PayoutProcess process = mock(PayoutProcess.class);
        when(process.request(any(), anyString(), anyString())).thenReturn(Mono.just(new PayoutRequest()));
        var resolver = payoutResolver(process, identity);

        // Admin, finance and super admin who are not members of the organization: refused like any
        // outsider, identity is not even asked, and nothing is recorded because no reach happened.
        for (String role : List.of("ROLE_ADMIN", "ROLE_FINANCE", "ROLE_SUPER_ADMIN")) {
            rows.clear();
            Throwable refused = catchThrowable(() -> as("staff-5", resolver.createPayoutRequest(payoutInput()), role).block());
            assertThat(refused).as(role).isInstanceOf(DomainRefusal.class);
            assertThat(((DomainRefusal) refused).errorCode()).as(role).isEqualTo(ErrorCode.ESCROW_ACCOUNT_UNKNOWN);
            assertThat(rows).as("a refused staff caller leaves no record (%s)", role).isEmpty();
        }
        verify(identity, never()).checkAuthorization(any());
        verify(process, never()).request(any(), anyString(), anyString());

        // An organizer who is a member and holds payout:request: the member path, no staff record.
        rows.clear();
        when(identity.checkAuthorization(any())).thenReturn(Mono.just(AuthorizationResult.authorizedAsMember(ORG, "OWNER")));
        as(ORGANIZER, resolver.createPayoutRequest(payoutInput()), "ROLE_ORGANIZER").block();
        assertThat(rows).isEmpty();
        verify(process).request(any(), org.mockito.ArgumentMatchers.eq(ORGANIZER), org.mockito.ArgumentMatchers.eq(ORG));

        // A member without payout:request is told so and nothing is started.
        when(identity.checkAuthorization(any())).thenReturn(Mono.just(AuthorizationResult.denied("switch off")));
        Throwable lacking = catchThrowable(() -> as("member-2", resolver.createPayoutRequest(payoutInput()),
                "ROLE_ORGANIZER").block());
        assertThat(((DomainRefusal) lacking).errorCode()).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(rows).isEmpty();

        // A staff member who is also a member of this organization acts as a member and is not a staff reach.
        when(identity.checkAuthorization(any())).thenReturn(Mono.just(AuthorizationResult.authorizedAsMember(ORG, "ADMIN")));
        TenantScope memberScope = TenantScope.platformAdministrator("staff-member", Set.of(ORG));
        Jwt jwt = Jwt.withTokenValue("token-material").header("alg", "none").subject("staff-member").build();
        resolver.createPayoutRequest(payoutInput())
                .contextWrite(ctx -> PlatformWideAccess.seed(ctx, access))
                .contextWrite(ctx -> com.pml.shared.security.tenancy.CurrentTenantScope.seed(ctx, Mono.just(memberScope)))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))))
                .block();
        assertThat(rows).as("acting as a member records nothing").isEmpty();
    }

    @Test
    @DisplayName("admin and super admin carry attendee:view and read an organizer's booking by id, recorded under the permission; finance does not carry it and is refused; members and strangers leave no staff record")
    void staffPermissionGrantsAreRecorded() {
        for (String role : List.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN")) {
            rows.clear();
            assertThat(as("staff-7", bookings.byId(booking.getId()), role).block().getId()).isEqualTo(booking.getId());
            assertOneRecord("staff-7", role, PlatformWideAccess.Reason.STAFF_PERMISSION_GRANT_READ);
            assertThat(rows.get(0).operation()).isEqualTo(com.pml.shared.security.Permission.ATTENDEE_VIEW.code());
        }

        rows.clear();
        assertThat(BookingFixture.refusal(as("finance-8", bookings.byId(booking.getId()), "ROLE_FINANCE")).errorCode())
                .as("finance's platform role does not carry attendee:view").isEqualTo(ErrorCode.BOOKING_UNKNOWN);
        assertThat(as(ORGANIZER, bookings.byId(booking.getId()), "ROLE_ORGANIZER").block().getId()).isEqualTo(booking.getId());
        assertThat(BookingFixture.refusal(as(OTHER_BUYER, bookings.byId(booking.getId()), "ROLE_CUSTOMER")).errorCode())
                .isEqualTo(ErrorCode.BOOKING_UNKNOWN);
        assertThat(as(BUYER, bookings.byId(booking.getId()), "ROLE_CUSTOMER").block().getId()).isEqualTo(booking.getId());
        assertThat(rows).as("members and strangers are not a staff reach").isEmpty();
    }

    @Test
    @DisplayName("a staff role that does not carry the permission is not admitted by it and leaves no record")
    void aRoleWithoutThePermissionIsNotAdmitted() {
        for (com.pml.shared.security.Permission permission : com.pml.shared.security.Permission.values()) {
            rows.clear();
            boolean financeCarries = com.pml.shared.security.Permission
                    .grantedByPlatformRoles(List.of("ROLE_FINANCE")).contains(permission);
            Boolean admitted = as("finance-9", OrganizerAccess.platformGrants(permission), "ROLE_FINANCE").block();
            assertThat(admitted).as(permission.code()).isEqualTo(financeCarries);
            assertThat(rows).as(permission.code()).hasSize(financeCarries ? 1 : 0);
        }
        rows.clear();
        assertThat(as("nobody", OrganizerAccess.platformGrants(com.pml.shared.security.Permission.ATTENDEE_VIEW),
                "ROLE_CUSTOMER").block()).isFalse();
        assertThat(rows).isEmpty();
    }

    // ---- site 3: ReservationQueryResolver.SUPPORT_AUTHORITIES ---------------------------------------

    @Test
    @DisplayName("a reservation is read by its buyer without a record, and by admin, finance or super admin with one; an organizer and another buyer read nothing")
    void reservations() {
        TicketReservation reservation = new TicketReservation();
        reservation.setId("res-1");
        reservation.setUserId(BUYER);
        reservation.setEventId(EVENT);
        reservation.setStatus(ReservationStatus.HELD);
        ReservationService service = mock(ReservationService.class);
        when(service.findById("res-1")).thenReturn(Mono.just(reservation));
        var resolver = new ReservationQueryResolver(service);

        assertThat(as(BUYER, resolver.reservation("res-1"), "ROLE_CUSTOMER").block()).isNotNull();
        assertThat(rows).isEmpty();

        for (String role : List.of("ROLE_ADMIN", "ROLE_FINANCE", "ROLE_SUPER_ADMIN")) {
            rows.clear();
            assertThat(as("staff-6", resolver.reservation("res-1"), role).block()).isNotNull();
            assertOneRecord("staff-6", role, PlatformWideAccess.Reason.RESERVATION_SUPPORT_READ);
        }

        rows.clear();
        assertThat(as(ORGANIZER, resolver.reservation("res-1"), "ROLE_ORGANIZER").block()).isNull();
        assertThat(as(OTHER_BUYER, resolver.reservation("res-1"), "ROLE_CUSTOMER").block()).isNull();
        assertThat(rows).isEmpty();
    }

    // ---- site 4: DualControlRules — the rank rule is unchanged; the act is what is recorded ----------

    @Test
    @DisplayName("proposing and confirming a finance recovery are recorded under the role held; refusals leave nothing")
    void dualControlActs() {
        when(chargebacks.writeOff(anyString(), any(), anyString(), anyString())).thenReturn(Mono.just(
                new com.pml.booking.domain.model.ChargebackRecord()));
        String reason = "Customer dispute lost at the bank, writing off the residue";

        RecoveryProposal proposal = as("finance-maker", dual.propose(RecoveryAction.WRITE_OFF_CHARGEBACK, List.of("cb-1"),
                new BigDecimal("40.00"), java.util.Map.of(), reason), "ROLE_FINANCE").block();
        assertOneRecord("finance-maker", "ROLE_FINANCE", PlatformWideAccess.Reason.RECOVERY_ACTION_PROPOSE);
        assertThat(rows.get(0).operation()).isEqualTo("WRITE_OFF_CHARGEBACK");

        rows.clear();
        assertThat(BookingFixture.refusal(as("finance-maker", dual.confirm(proposal.getId(), "Confirming my own proposal now"),
                "ROLE_FINANCE")).errorCode()).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(BookingFixture.refusal(as(ORGANIZER, dual.confirm(proposal.getId(), "An organizer trying to confirm this"),
                "ROLE_ORGANIZER")).errorCode()).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(BookingFixture.refusal(as(ORGANIZER, dual.propose(RecoveryAction.WRITE_OFF_CHARGEBACK, List.of("cb-2"),
                new BigDecimal("10.00"), java.util.Map.of(), reason), "ROLE_ORGANIZER")).errorCode())
                .isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(rows).as("nothing happened, so nothing is recorded").isEmpty();

        as("admin-checker", dual.confirm(proposal.getId(), "Checked the bank letter, approved"), "ROLE_ADMIN").block();
        assertOneRecord("admin-checker", "ROLE_ADMIN", PlatformWideAccess.Reason.RECOVERY_ACTION_CONFIRM);
        assertThat(rows.get(0).operation()).isEqualTo("WRITE_OFF_CHARGEBACK");
    }

    @Test
    @DisplayName("when the audit store is down the staff member is still served, exactly as before")
    void auditStoreDownDoesNotBlock() {
        access = new PlatformWideAccess("booking-service", record -> Mono.error(new IllegalStateException("identity down")),
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC), Set.of("ROLE_ADMIN"));

        assertThat(as("finance-9", bookings.byBuyer(BUYER, null, null), "ROLE_FINANCE").block().data()).hasSize(1);
        assertThat(as("finance-9", BookingReads.seesFullContact(booking), "ROLE_FINANCE").block()).isTrue();
    }
}
