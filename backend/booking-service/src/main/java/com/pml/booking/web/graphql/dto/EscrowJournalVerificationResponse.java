package com.pml.booking.web.graphql.dto;

import com.pml.booking.service.ReconciliationService.EscrowJournalVerificationResult;
import com.pml.booking.service.ReconciliationService.VerificationStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * GraphQL response for escrow-journal cross-verification operations.
 *
 * <p>Contains the result of comparing EventEscrowAccount balance
 * with the calculated balance from journal entries.</p>
 *
 * @since 1.0.0
 */
public record EscrowJournalVerificationResponse(
        /**
         * Whether the operation was successful.
         */
        boolean success,

        /**
         * Human-readable status message.
         */
        String message,

        /**
         * The event ID that was verified.
         */
        String eventId,

        /**
         * The escrow account ID (null if not found).
         */
        String escrowAccountId,

        /**
         * The journal account code (e.g., "2010-abc12345").
         */
        String journalAccountCode,

        /**
         * Balance from EventEscrowAccount.currentBalance.
         */
        BigDecimal escrowBalance,

        /**
         * Balance calculated from journal entries.
         */
        BigDecimal journalBalance,

        /**
         * Difference: escrowBalance - journalBalance.
         */
        BigDecimal variance,

        /**
         * Whether the balances are consistent (within tolerance).
         */
        boolean isConsistent,

        /**
         * Verification status code.
         */
        VerificationStatus status,

        /**
         * Detailed messages about the verification result.
         */
        List<String> details,

        /**
         * Error messages if the operation failed.
         */
        List<String> errors
) {
    /**
     * Creates a successful response from a verification result.
     *
     * @param result The verification result
     * @return Response DTO
     */
    public static EscrowJournalVerificationResponse fromResult(EscrowJournalVerificationResult result) {
        String message = result.isConsistent()
                ? "Escrow and journal balances are consistent"
                : String.format("Inconsistency detected: %s (variance: K%s)",
                        result.status(), result.variance());

        return new EscrowJournalVerificationResponse(
                true,
                message,
                result.eventId(),
                result.escrowAccountId(),
                result.journalAccountCode(),
                result.escrowBalance(),
                result.journalBalance(),
                result.variance(),
                result.isConsistent(),
                result.status(),
                result.details(),
                List.of()
        );
    }

    /**
     * Creates an error response.
     *
     * @param eventId The event ID that was being verified
     * @param errorMessage The error message
     * @return Error response
     */
    public static EscrowJournalVerificationResponse error(String eventId, String errorMessage) {
        return new EscrowJournalVerificationResponse(
                false,
                "Verification failed: " + errorMessage,
                eventId,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                List.of(),
                List.of(errorMessage)
        );
    }

    /**
     * Creates a "not found" response when the event doesn't have escrow tracking.
     *
     * @param eventId The event ID
     * @return Response indicating no escrow account exists
     */
    public static EscrowJournalVerificationResponse notFound(String eventId) {
        return new EscrowJournalVerificationResponse(
                true,
                "No escrow account found for event",
                eventId,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                VerificationStatus.NOT_FOUND,
                List.of("Neither escrow account nor journal entries exist for this event"),
                List.of()
        );
    }
}
