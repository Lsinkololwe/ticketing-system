package com.pml.booking.event.domain;

import com.pml.booking.domain.enums.ChargebackReason;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Domain Event: Chargeback Received
 *
 * <p>Published when a chargeback is received from the payment provider.
 * This initiates the chargeback handling workflow.</p>
 *
 * <h2>Triggered By</h2>
 * <ul>
 *   <li>ChargebackService.receiveChargeback()</li>
 *   <li>Webhook from payment provider (pawaPay)</li>
 * </ul>
 *
 * <h2>Potential Listeners</h2>
 * <ul>
 *   <li>Notification service (alert organizer and admin)</li>
 *   <li>Escrow lock service (hold affected funds)</li>
 *   <li>Fraud detection service (pattern analysis)</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Value
@Builder
public class ChargebackReceivedEvent {

    /**
     * Internal chargeback record ID.
     */
    String chargebackRecordId;

    /**
     * External chargeback ID from payment provider.
     */
    String chargebackId;

    /**
     * Original payment transaction ID.
     */
    String originalTransactionId;

    /**
     * Associated ticket ID.
     */
    String ticketId;

    /**
     * Associated event ID.
     */
    String eventId;

    /**
     * Event organizer ID.
     */
    String organizerId;

    /**
     * Customer who filed the chargeback.
     */
    String customerId;

    /**
     * Original transaction amount.
     */
    BigDecimal originalAmount;

    /**
     * Chargeback amount.
     */
    BigDecimal chargebackAmount;

    /**
     * Chargeback fee from provider.
     */
    BigDecimal chargebackFee;

    /**
     * Currency code.
     */
    String currency;

    /**
     * Chargeback reason category.
     */
    ChargebackReason reason;

    /**
     * Deadline for responding to the chargeback.
     */
    LocalDateTime responseDeadline;

    /**
     * Timestamp when the event was created.
     */
    Instant eventTimestamp;

    /**
     * Factory method for creating from a chargeback record.
     */
    public static ChargebackReceivedEvent of(
            String chargebackRecordId,
            String chargebackId,
            String originalTransactionId,
            String ticketId,
            String eventId,
            String organizerId,
            String customerId,
            BigDecimal originalAmount,
            BigDecimal chargebackAmount,
            BigDecimal chargebackFee,
            String currency,
            ChargebackReason reason,
            LocalDateTime responseDeadline
    ) {
        return ChargebackReceivedEvent.builder()
                .chargebackRecordId(chargebackRecordId)
                .chargebackId(chargebackId)
                .originalTransactionId(originalTransactionId)
                .ticketId(ticketId)
                .eventId(eventId)
                .organizerId(organizerId)
                .customerId(customerId)
                .originalAmount(originalAmount)
                .chargebackAmount(chargebackAmount)
                .chargebackFee(chargebackFee)
                .currency(currency)
                .reason(reason)
                .responseDeadline(responseDeadline)
                .eventTimestamp(Instant.now())
                .build();
    }
}
