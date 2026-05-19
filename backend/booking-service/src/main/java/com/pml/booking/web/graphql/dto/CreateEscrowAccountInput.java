package com.pml.booking.web.graphql.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Input for creating an escrow account.
 * Used by internal services when an event is published.
 */
public record CreateEscrowAccountInput(
    @NotBlank(message = "Event ID is required")
    String eventId,

    @NotBlank(message = "Organizer ID is required")
    String organizerId,

    String eventTitle,

    String organizerName,

    @NotBlank(message = "Currency is required")
    String currency
) {}
