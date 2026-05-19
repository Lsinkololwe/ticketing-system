package com.pml.booking.web.graphql.dto;

import java.math.BigDecimal;

/**
 * Transaction Statistics DTO
 *
 * Business Intent: Aggregated statistics for financial transactions.
 * Used for admin dashboards and reporting.
 */
public record TransactionStats(
        int totalTransactions,
        int completedTransactions,
        int failedTransactions,
        int pendingTransactions,
        int timedOutTransactions,
        BigDecimal totalVolume,
        BigDecimal totalCommissions,
        BigDecimal averageTransactionValue
) {}
