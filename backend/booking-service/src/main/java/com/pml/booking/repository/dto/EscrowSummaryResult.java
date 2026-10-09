package com.pml.booking.repository.dto;

import java.math.BigDecimal;

/**
 * DTO for escrow account aggregation results.
 *
 * Used to capture MongoDB aggregation pipeline output for platform summary.
 * This avoids fetching all records into memory and leverages MongoDB's
 * native aggregation capabilities for efficient computation at scale.
 *
 * Design for Scale:
 * - Uses MongoDB $group and $sum operators server-side
 * - Single aggregation query instead of multiple round-trips
 * - Supports billions of records without memory issues
 */
public record EscrowSummaryResult(
        // Counts by status
        long totalAccounts,
        long activeAccounts,
        long lockedAccounts,
        long payoutEligibleAccounts,
        long closedAccounts,

        // Balance aggregations
        BigDecimal totalBalance,
        BigDecimal totalDeposits,
        BigDecimal totalWithdrawals,
        BigDecimal totalRefunds,
        BigDecimal availableForPayout
) {
    public static EscrowSummaryResult empty() {
        return new EscrowSummaryResult(
                0L, 0L, 0L, 0L, 0L,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO
        );
    }
}
