package com.pml.booking.web.graphql.dto;

import com.pml.shared.constants.RefundRequestStatus;
import com.pml.shared.constants.RefundRequestType;

import java.time.Instant;

public record RefundRequestFilterInput(
        String ticketId,
        String buyerId,
        String eventId,
        String organizerId,
        RefundRequestStatus status,
        RefundRequestType requestType,
        Instant startDate,
        Instant endDate
) {}
