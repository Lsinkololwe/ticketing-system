package com.pml.booking.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A purchase that the platform cannot resolve on its own.
 *
 * <h2>Why a document rather than an ERROR log</h2>
 * A reservation stuck with a pending intent is escalated to transaction recovery,
 * never silently released — the money may still arrive.
 * An escalation that exists only as a log line is silent in every way that
 * matters: nothing lists it, nothing counts it, nothing notices when it is never
 * dealt with. These are cases where a buyer may have been charged, so the record
 * of them has to outlive a log rotation.
 *
 * <h2>What lands here</h2>
 * Two cases, both involving money the platform cannot automatically undo:
 * <ul>
 *   <li>A payment that completed <em>after</em> its hold expired. The seats were
 *       given back and may already be sold, so the tickets cannot be issued and
 *       the capture has to be refunded by hand, because the refund flow works
 *       from issued tickets and there are none.</li>
 *   <li>A reservation whose intent has been pending beyond
 *       {@code booking.payment.max-pending}. Releasing it would look tidy and
 *       could be wrong: mobile-money confirmations do arrive late, and a
 *       released hold plus an arriving payment is an oversell.</li>
 * </ul>
 */
@Document(collection = "booking_purchase_escalations")
@TypeAlias("booking_purchase_escalations")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseEscalation {

    @Id
    private String id;

    /**
     * Unique, so a retried escalation cannot produce a queue of duplicates for
     * one stuck reservation. An operator should see one row per problem, not one
     * row per attempt.
     */
    private String reservationId;

    private String eventId;

    private String userId;

    private String paymentIntentId;

    private Reason reason;

    /** What the operator has to act on, in words. */
    private String detail;

    /** Present when a capture may need returning, so the amount is not re-derived. */
    private BigDecimal amount;

    private String currency;

    @Builder.Default
    private boolean resolved = false;

    private String resolvedBy;

    private Instant resolvedAt;

    private String resolution;

    @CreatedDate
    private Instant createdAt;

    public enum Reason {
        /**
         * The provider confirmed a payment for a hold that had already lapsed.
         * The buyer has been charged and holds no tickets.
         */
        PAID_AFTER_EXPIRY,

        /**
         * The intent has been pending past {@code booking.payment.max-pending}.
         * The money may still arrive, so the hold is kept rather than released.
         */
        PAYMENT_PENDING_TOO_LONG,

        /**
         * Confirmation ran but could not complete, and the compensation could
         * not be applied either.
         */
        CONFIRMATION_FAILED
    }
}
