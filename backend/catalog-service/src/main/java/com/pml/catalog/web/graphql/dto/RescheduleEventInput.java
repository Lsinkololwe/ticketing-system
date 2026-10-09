package com.pml.catalog.web.graphql.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * The organizer's reschedule: the event, its new start and why it moved.
 */
public record RescheduleEventInput(
        @NotBlank String eventId,
        @NotNull @Future Instant newStartsAt,
        @NotBlank @Size(max = 1000) String reason) {
}
