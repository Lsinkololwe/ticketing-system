package com.pml.booking.web.graphql.dto;

import com.pml.shared.constants.ChargebackStatus;
import com.pml.booking.domain.enums.RecoveryStatus;

import java.time.Instant;

/**
 * Filter input for querying chargebacks.
 *
 * @param status Filter by chargeback status
 * @param recoveryStatus Filter by recovery status
 * @param eventId Filter by event ID
 * @param organizerId Filter by organizer ID
 * @param startDate Start of date range (inclusive)
 * @param endDate End of date range (inclusive)
 *
 * @since 1.0.0
 */
public record ChargebackFilterInput(
    ChargebackStatus status,
    RecoveryStatus recoveryStatus,
    String eventId,
    String organizerId,
    Instant startDate,
    Instant endDate,
    // The provider's response deadline falls on or before / on or after this day (Lusaka time)
    Instant deadlineBefore,
    Instant deadlineAfter,
    // Only chargebacks still waiting for a decision (received or under review)
    Boolean awaitingResponse
) {
    /**
     * Check if any filters are active.
     */
    public boolean hasFilters() {
        return status != null || recoveryStatus != null || eventId != null ||
               organizerId != null || startDate != null || endDate != null ||
               deadlineBefore != null || deadlineAfter != null || awaitingResponse != null;
    }
}
