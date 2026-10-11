package com.pml.booking.web.graphql.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Summary of an escrow account for dashboard display.
 * Provides key metrics for a single account.
 */
public record AccountSummary(
        String accountId,
        String accountNumber,
        String eventId,
        String eventTitle,
        String organizerId,
        /** The {@code Organization} entity reference identity resolves, or null. */
        Map<String, Object> organization,
        BigDecimal currentBalance,
        BigDecimal totalDeposits,
        BigDecimal totalWithdrawals,
        BigDecimal totalRefunds,
        BigDecimal totalCommissions,
        BigDecimal availableForPayout,
        String status,
        String currency,
        int transactionCount
) {}
