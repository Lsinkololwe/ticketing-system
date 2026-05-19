package com.pml.booking.web.graphql.dto;

import java.time.LocalDateTime;

/**
 * Financial Report Filter Input DTO
 *
 * Business Intent: Filter criteria for generating financial reports.
 */
public record FinancialReportFilterInput(
        LocalDateTime startDate,
        LocalDateTime endDate,
        String eventId,
        String organizerId,
        TimeUnit groupBy
) {}
