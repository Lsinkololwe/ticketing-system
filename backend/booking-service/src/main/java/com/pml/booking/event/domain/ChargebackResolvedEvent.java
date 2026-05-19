package com.pml.booking.event.domain;

import com.pml.booking.domain.enums.ChargebackFundSource;
import com.pml.booking.domain.enums.ChargebackStatus;
import com.pml.booking.domain.enums.RecoveryStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Domain Event: Chargeback Resolved
 *
 * <p>Published when a chargeback reaches a final state (accepted, won, lost)
 * and any necessary recovery has been completed.</p>
 *
 * <h2>Triggered By</h2>
 * <ul>
 *   <li>ChargebackService.acceptChargeback()</li>
 *   <li>ChargebackService.recordOutcome()</li>
 *   <li>ChargebackService.recoverFunds()</li>
 * </ul>
 *
 * <h2>Potential Listeners</h2>
 * <ul>
 *   <li>Notification service (inform organizer of outcome)</li>
 *   <li>Escrow service (release or debit funds)</li>
 *   <li>Accounting service (record journal entries)</li>
 *   <li>Analytics service (update chargeback statistics)</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Value
@Builder
public class ChargebackResolvedEvent {

    /**
     * Internal chargeback record ID.
     */
    String chargebackRecordId;

    /**
     * External chargeback ID from payment provider.
     */
    String chargebackId;

    /**
     * Associated ticket ID.
     * Required for restoring ticket status after dispute win.
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
     * Final status of the chargeback.
     */
    ChargebackStatus finalStatus;

    /**
     * Whether the dispute was won (true) or lost (false).
     * Null if not disputed (accepted directly).
     */
    Boolean disputeWon;

    /**
     * Recovery status.
     */
    RecoveryStatus recoveryStatus;

    /**
     * Source of recovered funds.
     */
    ChargebackFundSource fundSource;

    /**
     * Total chargeback amount (including fees).
     */
    BigDecimal totalAmount;

    /**
     * Amount recovered from organizer/platform.
     */
    BigDecimal recoveredAmount;

    /**
     * Amount written off as loss.
     */
    BigDecimal writtenOffAmount;

    /**
     * Journal entry ID for the recovery transaction.
     */
    String journalEntryId;

    /**
     * Date when the chargeback was resolved.
     */
    LocalDateTime resolvedAt;

    /**
     * Timestamp when the event was created.
     */
    Instant eventTimestamp;

    /**
     * Factory method for creating from a resolved chargeback.
     */
    public static ChargebackResolvedEvent of(
            String chargebackRecordId,
            String chargebackId,
            String ticketId,
            String eventId,
            String organizerId,
            ChargebackStatus finalStatus,
            Boolean disputeWon,
            RecoveryStatus recoveryStatus,
            ChargebackFundSource fundSource,
            BigDecimal totalAmount,
            BigDecimal recoveredAmount,
            BigDecimal writtenOffAmount,
            String journalEntryId,
            LocalDateTime resolvedAt
    ) {
        return ChargebackResolvedEvent.builder()
                .chargebackRecordId(chargebackRecordId)
                .chargebackId(chargebackId)
                .ticketId(ticketId)
                .eventId(eventId)
                .organizerId(organizerId)
                .finalStatus(finalStatus)
                .disputeWon(disputeWon)
                .recoveryStatus(recoveryStatus)
                .fundSource(fundSource)
                .totalAmount(totalAmount)
                .recoveredAmount(recoveredAmount)
                .writtenOffAmount(writtenOffAmount)
                .journalEntryId(journalEntryId)
                .resolvedAt(resolvedAt)
                .eventTimestamp(Instant.now())
                .build();
    }
}
