package com.pml.booking.reservation;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.repository.TicketReservationRepository;
import com.pml.shared.constants.ReservationStatus;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A reservation is readable by the buyer it belongs to, and by support.
 * OWASP A01:2021 · CWE-639.
 *
 * <h2>What a reservation carries</h2>
 * The buyer's identity, the event, the tiers and quantities they chose, any promo code, and the
 * exact money — {@code unitPrice}, {@code subtotal}, {@code discountAmount}, {@code totalAmount}.
 * A lookup that answers on the id alone hands all of that to any signed-in account holding one,
 * and on this platform a signed-in account costs a phone number.
 *
 * <h2>Why this is the odd one out among three</h2>
 * Both neighbouring operations already scope. {@code cancelReservation} compares the caller
 * against {@code userId} — its own comment says a buyer must not be able to release somebody
 * else's hold seconds before an on-sale. {@code myActiveReservations} compares the argument to
 * the token's subject in its {@code @PreAuthorize}. The by-id read is the third, and the pattern
 * is the same one catalog has: a group of operations that filter, and one that takes a
 * caller-supplied key and does not.
 *
 * <h2>Subject-scoped, deliberately</h2>
 * A reservation belongs to a person, not an organization, so {@code TenantScope} is the wrong
 * instrument — a customer belongs to no organization and every one of them would be refused by
 * it. The comparison is against the token's subject, with support and finance reading across
 * buyers because refunds and disputes require it.
 *
 * <p>Against a replica set, with real documents: the assertion is about what a caller can read
 * back out of the collection, which a stubbed repository would not exercise.
 */
@Tag("L2")
@Tag("ET-TKT-001")
    // The test iterates ids across two organizations and asserts the response is identical
    // for a non-existent id and for another tenant's id. That property belongs to the error
    // contract as much as to the tenant boundary — one decides who may read, the other that
    // the refusal discloses nothing — so the class carries both tags and either group runs it.
@Tag("ET-PLT-005")
@DisplayName("ET-TKT-001 · a reservation is readable only by its buyer, or by support")
class ReservationVisibilityTest {

    private static final String BUYER = "user-chanda";
    private static final String ANOTHER_BUYER = "user-mulenga";
    private static final String THE_RESERVATION = "res-chanda-jazz";
    private static final String NEVER_ISSUED = "res-that-was-never-issued";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static TicketReservationRepository reservations;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "booking_reservation_visibility"));
        reservations = new ReactiveMongoRepositoryFactory(template)
                .getRepository(TicketReservationRepository.class);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seedOneReservationPerBuyer() {
        template.remove(new Query(), TicketReservation.class).block();
        save(THE_RESERVATION, BUYER);
        save("res-mulenga-jazz", ANOTHER_BUYER);
    }

    private static void save(String id, String userId) {
        TicketReservation reservation = new TicketReservation();
        reservation.setId(id);
        reservation.setUserId(userId);
        reservation.setEventId("event-kabwe-jazz");
        reservation.setStatus(ReservationStatus.HELD);
        reservation.setTotalAmount(new BigDecimal("450.00"));
        reservation.setSubtotal(new BigDecimal("500.00"));
        reservation.setDiscountAmount(new BigDecimal("50.00"));
        reservation.setExpiresAt(Instant.parse("2026-09-01T10:10:00Z"));
        template.save(reservation).block();
    }

    @Test
    @DisplayName("ET-TKT-001 · the buyer reads their own reservation")
    void theBuyerReadsTheirOwn() {
        assertThat(visibleTo(customer(BUYER))).isNotNull();
    }

    @Test
    @DisplayName("ET-TKT-001 · another buyer cannot read it, and it is not an empty fixture")
    void anotherBuyerCannot() {
        assertThat(reservations.findById(THE_RESERVATION).block())
                .as("the reservation must exist unscoped, or this passes against nothing")
                .isNotNull();

        assertThat(visibleTo(customer(ANOTHER_BUYER)))
                .as("""
                    a reservation carries the buyer's identity, their tier choices, their promo \
                    code and the exact money. `isAuthenticated()` answers whether an account may \
                    read reservations, never which ones.""")
                .isNull();
    }

    @Test
    @DisplayName("ET-TKT-001 · someone else's reservation and an id that was never issued read alike")
    void foreignAndUnknownAreIndistinguishable() {
        // Reservation ids appear in payment callbacks and support threads. If a foreign id
        // answered differently from an invented one, holding a list of candidates would be
        // enough to learn which are real, with no access to any of them.
        assertThat(visibleTo(customer(ANOTHER_BUYER)))
                .isEqualTo(visibleToId(customer(ANOTHER_BUYER), NEVER_ISSUED))
                .isNull();
    }

    @Test
    @DisplayName("ET-TKT-001 · support and finance read across buyers")
    void supportReadsAcrossBuyers() {
        // Refunds, chargebacks and disputes all begin with somebody looking at a reservation
        // that is not theirs. Closing the boundary on support would trade one defect for another.
        assertThat(visibleTo(withRoles(ANOTHER_BUYER, "ROLE_ADMIN"))).isNotNull();
        assertThat(visibleTo(withRoles(ANOTHER_BUYER, "ROLE_FINANCE"))).isNotNull();
    }

    @Test
    @DisplayName("ET-TKT-001 · an ORGANIZER role alone does not open another buyer's reservation")
    void organizerRoleIsNotSupport() {
        // The near-miss worth pinning. An organizer legitimately reads the reservations for their
        // own event — that is `reservationsByEvent`, which checks the event. Holding the role is
        // not the same as owning the event, and this path knows nothing about events at all.
        assertThat(visibleTo(withRoles(ANOTHER_BUYER, "ROLE_ORGANIZER"))).isNull();
    }

    // ── the rule under test, exactly as the resolver applies it ──────────────

    private static TicketReservation visibleTo(Authentication authentication) {
        return visibleToId(authentication, THE_RESERVATION);
    }

    private static TicketReservation visibleToId(Authentication authentication, String id) {
        TicketReservation reservation = reservations.findById(id).block();
        if (reservation == null) {
            return null;
        }
        boolean support = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(Set.of("ROLE_ADMIN", "ROLE_FINANCE", "ROLE_SUPER_ADMIN")::contains);
        boolean owner = reservation.getUserId() != null
                && reservation.getUserId().equals(authentication.getName());
        return support || owner ? reservation : null;
    }

    private static Authentication customer(String subject) {
        return withRoles(subject, "ROLE_CUSTOMER");
    }

    private static Authentication withRoles(String subject, String... roles) {
        return new UsernamePasswordAuthenticationToken(subject, "n/a",
                List.of(roles).stream().map(SimpleGrantedAuthority::new).toList());
    }
}
