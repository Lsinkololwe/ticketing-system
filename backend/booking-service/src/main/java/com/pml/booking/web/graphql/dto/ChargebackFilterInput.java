package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.enums.ChargebackStatus;
import com.pml.booking.domain.enums.RecoveryStatus;

import java.time.LocalDateTime;

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
    LocalDateTime startDate,
    LocalDateTime endDate
) {
    /**
     * Check if any filters are active.
     */
    public boolean hasFilters() {
        return status != null || recoveryStatus != null || eventId != null ||
               organizerId != null || startDate != null || endDate != null;
    }
}
