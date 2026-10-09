package com.pml.booking.web.graphql.dto;

import java.time.OffsetDateTime;

/**
 * Filter input for searching payout requests.
 */
public record PayoutRequestFilterInput(
        String organizerId,
        String eventId,
        String escrowAccountId,
        // A status name, ON_HOLD included: a hold overlays the stored status, so the filter reads the effective one
        String status,
        com.pml.shared.constants.PayoutMethod payoutMethod,
        OffsetDateTime startDate,
        OffsetDateTime endDate
) {}
