package com.pml.booking.web.graphql.dto;

import java.math.BigDecimal;

/**
 * Platform-wide financial summary for admin dashboard.
 * Aggregates data across all escrow accounts and transactions.
 */
public record PlatformSummary(
        // Escrow Account Metrics
        long totalEscrowAccounts,
        long activeEscrowAccounts,
        long lockedEscrowAccounts,
        long payoutEligibleAccounts,
        long closedEscrowAccounts,

        // Balance Metrics
        BigDecimal totalEscrowBalance,
        BigDecimal totalDeposits,
        BigDecimal totalWithdrawals,
        BigDecimal totalRefunds,
        BigDecimal availableForPayout,

        // Transaction Metrics
        long totalTransactions,
        long completedTransactions,
        long pendingTransactions,
        long failedTransactions,
        BigDecimal totalTransactionVolume,
        BigDecimal totalCommissions,

        // Payout Metrics
        long totalPayoutRequests,
        long pendingPayoutRequests,
        long completedPayoutRequests,
        BigDecimal totalPayoutAmount,

        // Ticket Metrics
        long totalTicketsSold,
        BigDecimal totalTicketRevenue,

        // Currency
        String primaryCurrency
) {
    /**
     * Create an empty platform summary with default values.
     */
    public static PlatformSummary empty() {
        return new PlatformSummary(
                0L, 0L, 0L, 0L, 0L,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                0L, 0L, 0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO,
                0L, 0L, 0L, BigDecimal.ZERO,
                0L, BigDecimal.ZERO,
                "ZMW"
        );
    }
}
