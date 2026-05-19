package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.enums.ChargebackFundSource;

import java.math.BigDecimal;

/**
 * Input for recovering chargeback funds.
 *
 * <p>Recovery follows a waterfall priority:
 * 1. ORGANIZER_ESCROW - Deduct from organizer's escrow balance
 * 2. ORGANIZER_FUTURE - Hold against future payouts
 * 3. PLATFORM_RESERVE - Use platform reserve funds
 * 4. WRITE_OFF - Write off as bad debt
 * </p>
 *
 * @param fundSource Source of recovery funds
 * @param amount Amount to recover (optional, defaults to full chargeback + fees)
 * @param notes Notes about the recovery
 *
 * @since 1.0.0
 */
public record RecoverChargebackInput(
    ChargebackFundSource fundSource,
    BigDecimal amount,
    String notes
) {
    /**
     * Constructor with validation.
     */
    public RecoverChargebackInput {
        if (fundSource == null) {
            throw new IllegalArgumentException("Fund source is required");
        }
        if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
    }
}
