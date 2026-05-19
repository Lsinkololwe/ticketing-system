package com.pml.booking.web.graphql.dto;

import java.math.BigDecimal;

/**
 * Input for a journal entry line (debit or credit).
 *
 * <p>Each line must have exactly one of debit or credit populated (XOR constraint).</p>
 *
 * @param accountCode The account code being affected
 * @param accountName Human-readable account name
 * @param debit Debit amount (null if credit)
 * @param credit Credit amount (null if debit)
 * @param description Line-level description (optional)
 * @param referenceType Type of reference entity (e.g., "TICKET", "PAYMENT")
 * @param referenceId ID of reference entity (optional)
 *
 * @since 1.0.0
 */
public record JournalLineInput(
    String accountCode,
    String accountName,
    BigDecimal debit,
    BigDecimal credit,
    String description,
    String referenceType,
    String referenceId
) {
    /**
     * Constructor with validation.
     */
    public JournalLineInput {
        if (accountCode == null || accountCode.isBlank()) {
            throw new IllegalArgumentException("Account code is required");
        }

        // XOR validation: exactly one of debit or credit must be positive
        boolean hasDebit = debit != null && debit.compareTo(BigDecimal.ZERO) > 0;
        boolean hasCredit = credit != null && credit.compareTo(BigDecimal.ZERO) > 0;

        if (hasDebit == hasCredit) {
            throw new IllegalArgumentException(
                "Exactly one of debit or credit must be positive for account: " + accountCode
            );
        }
    }

    /**
     * Check if this is a debit line.
     */
    public boolean isDebit() {
        return debit != null && debit.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Get the effective amount (debit or credit).
     */
    public BigDecimal getAmount() {
        return isDebit() ? debit : credit;
    }
}
