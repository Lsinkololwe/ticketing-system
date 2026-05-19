package com.pml.booking.web.graphql.dto;

import java.math.BigDecimal;

/**
 * Financial Data Point DTO
 *
 * Business Intent: Time-series financial data point for charts and reports.
 */
public record FinancialDataPoint(
        String period,
        BigDecimal revenue,
        BigDecimal commissions,
        BigDecimal refunds,
        BigDecimal payouts,
        int ticketsSold
) {}
