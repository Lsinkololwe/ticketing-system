package com.pml.booking.web.graphql.dto;

import java.time.Instant;

/**
 * Financial Report Filter Input DTO
 *
 * Business Intent: Filter criteria for generating financial reports.
 */
public record FinancialReportFilterInput(
        Instant startDate,
        Instant endDate,
        String eventId,
        String organizerId,
        TimeUnit groupBy
) {}
