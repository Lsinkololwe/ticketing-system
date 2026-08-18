package com.pml.booking.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A purchase that the platform cannot resolve on its own.
 *
 * <h2>Why a document rather than an ERROR log</h2>
 * ET-TKT-001 R8 requires that a reservation stuck with a pending intent be
 * "escalated to ET-ADM-003, not silently released — the money may still arrive".
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
 *       the capture has to be refunded by hand until ET-FIN-004's ticketless
 *       refund path exists.</li>
 *   <li>A reservation whose intent has been pending beyond
 *       {@code booking.payment.max-pending}. Releasing it would look tidy and
 *       could be wrong: mobile-money confirmations do arrive late, and a
 *       released hold plus an arriving payment is an oversell.</li>
 * </ul>
 *
 * @see <a href="file:../../../../../../specs/ticketing/001-reservation-and-hold/spec.md">ET-TKT-001 R7, R8</a>
 */
@Document(collection = "booking_purchase_escalations")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseEscalation {

    @Id
    private String id;

    /**
     * Unique, so the sweep can run every thirty seconds without producing a
     * queue of duplicates for one stuck reservation. An operator should see one
     * row per problem, not one row per sweep tick.
     */
    @Indexed(unique = true)
    private String reservationId;

    @Indexed
    private String eventId;

    private String userId;

    private String paymentIntentId;

    @Indexed
    private Reason reason;

    /** What the operator has to act on, in words. */
    private String detail;

    /** Present when a capture may need returning, so the amount is not re-derived. */
    private BigDecimal amount;

    private String currency;

    @Indexed
    @Builder.Default
    private boolean resolved = false;

    private String resolvedBy;

    private LocalDateTime resolvedAt;

    private String resolution;

    @CreatedDate
    private LocalDateTime createdAt;

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
