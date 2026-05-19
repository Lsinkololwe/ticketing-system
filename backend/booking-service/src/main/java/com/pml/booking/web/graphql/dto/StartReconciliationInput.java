package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.enums.ReconciliationType;

import java.time.LocalDateTime;

/**
 * Input for starting a reconciliation run.
 *
 * @param reconciliationDate Date for which to run reconciliation
 * @param type Type of reconciliation (GATEWAY, BANK, ESCROW, ESCROW_JOURNAL)
 * @param dataSource External data source identifier (optional)
 * @param includeClosed For ESCROW_JOURNAL only: whether to include CLOSED/CANCELLED accounts.
 *                      Default is false (only verifies OPEN accounts for performance).
 *                      Set to true for full audit purposes.
 *
 * @since 1.0.0
 */
public record StartReconciliationInput(
    LocalDateTime reconciliationDate,
    ReconciliationType type,
    String dataSource,
    Boolean includeClosed
) {
    /**
     * Constructor with validation.
     */
    public StartReconciliationInput {
        if (reconciliationDate == null) {
            throw new IllegalArgumentException("Reconciliation date is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("Reconciliation type is required");
        }
        // Default includeClosed to false if not specified
        if (includeClosed == null) {
            includeClosed = false;
        }
    }

    /**
     * Convenience method to check if closed accounts should be included.
     * @return true if closed accounts should be included, false otherwise
     */
    public boolean shouldIncludeClosed() {
        return includeClosed != null && includeClosed;
    }
}
