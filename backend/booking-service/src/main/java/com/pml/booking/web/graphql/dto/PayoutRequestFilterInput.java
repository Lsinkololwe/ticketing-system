package com.pml.booking.web.graphql.dto;

import com.pml.shared.constants.PayoutRequestStatus;

import java.time.OffsetDateTime;

/**
 * Filter input for searching payout requests.
 */
public record PayoutRequestFilterInput(
        String organizerId,
        String eventId,
        PayoutRequestStatus status,
        OffsetDateTime startDate,
        OffsetDateTime endDate
) {}
