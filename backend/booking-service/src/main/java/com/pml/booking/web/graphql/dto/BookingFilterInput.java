package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.enums.BookingStatus;

import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record BookingFilterInput(
        String eventId,
        BookingStatus status,
        List<BookingStatus> statuses,
        @Size(max = 100) String search,
        Instant createdAfter,
        Instant createdBefore
) {
}
