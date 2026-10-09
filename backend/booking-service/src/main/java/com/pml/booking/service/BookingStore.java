package com.pml.booking.service;

import com.pml.booking.domain.BookingRules;
import com.pml.booking.domain.enums.BookingStatus;
import com.pml.booking.domain.model.Booking;
import com.pml.booking.domain.model.BookingCounter;
import com.pml.booking.domain.model.TicketReservation;
import com.pml.shared.constants.ReservationStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * The durable record of a purchase, and every write that keeps it true.
 *
 * <p>Writes are conditional or additive ({@code $inc}, {@code $set} of a terminal fact) so that
 * the expiry timer, an arriving payment and a refund completing may all touch one booking at once
 * without any of them overwriting another's fact.
 */
@Component
public class BookingStore {

    private final ReactiveMongoTemplate template;
    private final Clock clock;

    public BookingStore(ReactiveMongoTemplate template, Clock clock) {
        this.template = template;
        this.clock = clock;
    }

    // ---- opening -------------------------------------------------------------------------------

    /**
     * Starts the booking for a purchase before its hold exists: the number and the order's contact,
     * under the reservation id the purchase will use. Done by the request, outside the purchase workflow,
     * because the contact is personal data and workflow history is plain text. Repeating it with the same
     * reservation id returns the first booking — the contact first typed stands.
     */
    public Mono<Booking> stage(String reservationId, String userId, String eventId, String contactName,
                               String contactEmail, String contactPhone) {
        return byReservationId(reservationId)
                .switchIfEmpty(Mono.defer(() -> nextNumber(clock.instant())
                        .flatMap(number -> template.insert(Booking.builder()
                                .bookingNumber(number)
                                .reservationId(reservationId)
                                .eventId(eventId)
                                .buyerId(userId)
                                .contactName(blankToNull(contactName))
                                .contactEmail(blankToNull(contactEmail))
                                .contactPhone(blankToNull(contactPhone))
                                .status(BookingStatus.PENDING)
                                .refundedAmount(BigDecimal.ZERO)
                                .createdAt(clock.instant())
                                .build()))
                        .onErrorResume(DuplicateKeyException.class, raced -> byReservationId(reservationId))));
    }

    /**
     * Completes the booking once the hold exists: who is selling, what is being bought, for how much, and
     * until when. Creates the booking too if nothing staged one (an older purchase, or a hold made before
     * staging existed). The number is copied onto the reservation so tickets issued at confirmation can
     * carry it without a second read. Idempotent: the hold activity retries.
     */
    public Mono<Booking> open(TicketReservation reservation, String eventTitle, String eventDate) {
        return stage(reservation.getId(), reservation.getUserId(), reservation.getEventId(), null, null, null)
                .flatMap(staged -> template.findAndModify(
                        Query.query(Criteria.where("_id").is(staged.getId())),
                        enrichment(reservation, eventTitle, eventDate),
                        FindAndModifyOptions.options().returnNew(true), Booking.class))
                .flatMap(booking -> template.updateFirst(
                                Query.query(Criteria.where("_id").is(reservation.getId())),
                                new Update().set("bookingNumber", booking.getBookingNumber()),
                                TicketReservation.class)
                        .thenReturn(booking));
    }

    private Update enrichment(TicketReservation r, String eventTitle, String eventDate) {
        List<Booking.Item> items = r.getItems() == null ? List.of() : r.getItems().stream()
                .map(item -> Booking.Item.builder()
                        .ticketTierId(item.getTicketTierId())
                        .tierName(item.getTierName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .subtotal(item.getSubtotal())
                        .build())
                .toList();
        Update update = new Update()
                .set("eventId", r.getEventId())
                .set("buyerId", r.getUserId())
                .set("organizerId", r.getOrganizerId())
                .set("organizationId", r.getOrganizationId())
                .set("items", items)
                .set("subtotal", r.getSubtotal())
                .set("discountAmount", r.getDiscountAmount())
                .set("totalAmount", r.getTotalAmount())
                .set("currency", r.getCurrency())
                .set("promoCode", r.getPromoCode())
                .set("ticketCount", items.stream().mapToInt(Booking.Item::getQuantity).sum())
                .set("expiresAt", r.getExpiresAt())
                .set("updatedAt", clock.instant());
        if (eventTitle != null) update.set("eventTitle", eventTitle);
        if (eventDate != null) update.set("eventDate", eventDate);
        // The booking takes the reservation's own state, so a hold activity retried after the reservation
        // moved reports where it really is rather than where it started.
        update.set("status", storedStatusOf(r.getStatus()));
        if (r.getConfirmedAt() != null) update.set("confirmedAt", r.getConfirmedAt());
        return update;
    }

    /** Withdraws a staged booking whose hold was refused, so a sold-out attempt leaves nothing behind. */
    public Mono<Void> discardUnheld(String reservationId) {
        return template.remove(Query.query(Criteria.where("reservationId").is(reservationId)
                        .and("status").is(BookingStatus.PENDING).and("expiresAt").is(null)), Booking.class)
                .then();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** The booking status a reservation status stands for. */
    public static BookingStatus storedStatusOf(ReservationStatus status) {
        if (status == null) {
            return BookingStatus.PENDING;
        }
        return switch (status) {
            case HELD -> BookingStatus.PENDING;
            case CONFIRMED -> BookingStatus.CONFIRMED;
            case RELEASED -> BookingStatus.CANCELLED;
            case EXPIRED -> BookingStatus.EXPIRED;
            case FAILED -> BookingStatus.FAILED;
        };
    }

    private Mono<String> nextNumber(Instant at) {
        return template.findAndModify(
                        Query.query(Criteria.where("_id").is(BookingRules.sequenceFor(at))),
                        new Update().inc("seq", 1L),
                        FindAndModifyOptions.options().upsert(true).returnNew(true),
                        BookingCounter.class)
                .map(counter -> BookingRules.format(at, counter.getSeq()));
    }

    // ---- keeping it true -----------------------------------------------------------------------

    /** The reservation left {@code HELD}: the booking follows, in whatever transaction made the move. */
    public static Mono<Void> reservationMoved(ReactiveMongoTemplate template, String reservationId,
                                              ReservationStatus to, Instant now) {
        Update update = new Update().set("status", storedStatusOf(to));
        if (to == ReservationStatus.CONFIRMED) {
            update.set("confirmedAt", now);
        } else {
            update.set("closedAt", now);
        }
        return template.updateFirst(Query.query(Criteria.where("reservationId").is(reservationId)),
                update, Booking.class).then();
    }

    /** A refund against one of this booking's tickets completed. */
    public static Mono<Void> refundCompleted(ReactiveMongoTemplate template, String reservationId, BigDecimal amount) {
        if (reservationId == null) {
            return Mono.empty();
        }
        return template.updateFirst(Query.query(Criteria.where("reservationId").is(reservationId)),
                new Update().inc("refundedAmount", amount), Booking.class).then();
    }

    /** A ticket of this booking was cancelled. */
    public Mono<Void> ticketCancelled(String reservationId) {
        if (reservationId == null) {
            return Mono.empty();
        }
        return template.updateFirst(Query.query(Criteria.where("reservationId").is(reservationId)),
                new Update().inc("cancelledTicketCount", 1), Booking.class).then();
    }

    /** The automatic refund of a payment that arrived after the hold lapsed moved. */
    public static Mono<Void> lateRefundMoved(ReactiveMongoTemplate template, String reservationId, String state) {
        return template.updateFirst(Query.query(Criteria.where("reservationId").is(reservationId)),
                new Update().set("lateRefundStatus", state), Booking.class).then();
    }

    // ---- reads ---------------------------------------------------------------------------------

    public Mono<Booking> byId(String id) {
        return template.findById(id, Booking.class);
    }

    public Mono<Booking> byNumber(String bookingNumber) {
        return template.findOne(Query.query(Criteria.where("bookingNumber").is(bookingNumber)), Booking.class);
    }

    public Mono<Booking> byReservationId(String reservationId) {
        return template.findOne(Query.query(Criteria.where("reservationId").is(reservationId)), Booking.class);
    }
}
