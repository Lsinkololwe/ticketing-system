package com.pml.booking.web.graphql.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Financial Report DTO
 *
 * Business Intent: Comprehensive financial report with aggregated data
 * and time-series breakdown.
 */
public record FinancialReport(
        LocalDateTime startDate,
        LocalDateTime endDate,
        BigDecimal totalRevenue,
        BigDecimal totalCommissions,
        BigDecimal totalRefunds,
        BigDecimal totalPayouts,
        BigDecimal pendingPayouts,
        BigDecimal escrowBalance,
        BigDecimal netPlatformRevenue,
        List<FinancialDataPoint> dataPoints,
        List<EventFinancialSummary> eventBreakdown
) {}
