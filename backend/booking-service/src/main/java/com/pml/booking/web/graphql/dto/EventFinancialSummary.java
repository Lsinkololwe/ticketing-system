package com.pml.booking.web.graphql.dto;

import java.math.BigDecimal;

/**
 * Event Financial Summary DTO
 *
 * Business Intent: Event-level financial summary for breakdown in reports.
 */
public record EventFinancialSummary(
        String eventId,
        String eventTitle,
        BigDecimal totalRevenue,
        BigDecimal totalCommissions,
        BigDecimal totalRefunds,
        BigDecimal escrowBalance,
        String payoutStatus
) {}
