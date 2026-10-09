package com.pml.booking.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.booking.domain.BookingRules;
import com.pml.booking.domain.enums.BookingStatus;
import com.pml.booking.domain.model.Booking;
import com.pml.booking.domain.model.PaymentIntent;
import com.pml.booking.domain.model.RefundRequest;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.repository.PaymentIntentRepository;
import com.pml.booking.service.BookingReads;
import com.pml.shared.util.ContactMasking;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * The parts of a {@link Booking} that are not stored on it: what it currently is, who may read the
 * contact in full, and its seats, refunds and payment.
 */
@DgsComponent
@RequiredArgsConstructor
public class BookingFieldResolver {

    private final ReactiveMongoTemplate template;
    private final PaymentIntentRepository intents;

    @DgsData(parentType = "Booking", field = "status")
    public BookingStatus status(DgsDataFetchingEnvironment dfe) {
        return BookingRules.effectiveStatus(dfe.getSource());
    }

    @DgsData(parentType = "Booking", field = "refundedAmount")
    public BigDecimal refundedAmount(DgsDataFetchingEnvironment dfe) {
        Booking booking = dfe.getSource();
        return booking.getRefundedAmount() == null ? BigDecimal.ZERO : booking.getRefundedAmount();
    }

    @DgsData(parentType = "Booking", field = "refundableAmount")
    public Mono<BigDecimal> refundableAmount(DgsDataFetchingEnvironment dfe) {
        Booking booking = dfe.getSource();
        if (BookingRules.effectiveStatus(booking) != BookingStatus.CONFIRMED
                && BookingRules.effectiveStatus(booking) != BookingStatus.PARTIALLY_REFUNDED) {
            return Mono.just(BigDecimal.ZERO);
        }
        return seats(booking)
                .filter(ticket -> ticket.getStatus() != null && ticket.getStatus().isRefundable())
                .map(BookingFieldResolver::remaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @DgsData(parentType = "Booking", field = "contactEmail")
    public Mono<String> contactEmail(DgsDataFetchingEnvironment dfe) {
        Booking booking = dfe.getSource();
        if (booking.getContactEmail() == null) {
            return Mono.empty();
        }
        return BookingReads.seesFullContact(booking)
                .map(full -> full ? booking.getContactEmail() : ContactMasking.maskEmail(booking.getContactEmail()));
    }

    @DgsData(parentType = "Booking", field = "contactPhone")
    public Mono<String> contactPhone(DgsDataFetchingEnvironment dfe) {
        Booking booking = dfe.getSource();
        if (booking.getContactPhone() == null) {
            return Mono.empty();
        }
        return BookingReads.seesFullContact(booking)
                .map(full -> full ? booking.getContactPhone() : ContactMasking.maskPhone(booking.getContactPhone()));
    }

    @DgsData(parentType = "Booking", field = "tickets")
    public Flux<Ticket> tickets(DgsDataFetchingEnvironment dfe) {
        return seats(dfe.getSource());
    }

    @DgsData(parentType = "Booking", field = "refundRequests")
    public Flux<RefundRequest> refundRequests(DgsDataFetchingEnvironment dfe) {
        Booking booking = dfe.getSource();
        return seats(booking).map(Ticket::getId).collectList().flatMapMany(ids -> ids.isEmpty()
                ? Flux.<RefundRequest>empty()
                : template.find(new Query(Criteria.where("ticketId").in(ids)).with(Sort.by(Sort.Direction.DESC, "createdAt")),
                        RefundRequest.class));
    }

    @DgsData(parentType = "Booking", field = "payment")
    public Mono<Map<String, Object>> payment(DgsDataFetchingEnvironment dfe) {
        Booking booking = dfe.getSource();
        return intents.findByReservationId(booking.getReservationId())
                .flatMap(intent -> BookingReads.seesFullContact(booking).map(full -> paymentOf(intent, full)));
    }

    private static Map<String, Object> paymentOf(PaymentIntent intent, boolean full) {
        Instant paidAt = intent.getStatus() == PaymentIntent.PaymentStatus.SUCCEEDED
                || intent.getStatus() == PaymentIntent.PaymentStatus.REFUNDED ? intent.getProcessedAt() : null;
        Map<String, Object> payment = new java.util.HashMap<>();
        payment.put("provider", intent.getCorrespondent() != null ? intent.getCorrespondent()
                : intent.getProvider() == null ? null : intent.getProvider().name());
        payment.put("status", intent.getStatus() == null ? null : intent.getStatus().name());
        payment.put("reference", intent.getTransactionRef());
        payment.put("amount", intent.getAmount());
        payment.put("currency", intent.getCurrency());
        payment.put("payerPhone", full ? intent.getPhoneNumber() : ContactMasking.maskPhone(intent.getPhoneNumber()));
        payment.put("paidAt", paidAt);
        return payment;
    }

    private Flux<Ticket> seats(Booking booking) {
        return template.find(new Query(Criteria.where("reservationId").is(booking.getReservationId()))
                .with(Sort.by(Sort.Direction.ASC, "ticketNumber")), Ticket.class);
    }

    /** What can still be refunded on one seat. */
    public static BigDecimal remaining(Ticket ticket) {
        BigDecimal refunded = ticket.getRefundedAmount() == null ? BigDecimal.ZERO : ticket.getRefundedAmount();
        BigDecimal left = (ticket.getPrice() == null ? BigDecimal.ZERO : ticket.getPrice()).subtract(refunded);
        return left.signum() < 0 ? BigDecimal.ZERO : left;
    }
}
