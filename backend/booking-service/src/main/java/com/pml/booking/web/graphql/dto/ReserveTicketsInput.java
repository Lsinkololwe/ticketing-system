package com.pml.booking.web.graphql.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Input for creating a ticket reservation.
 */
public record ReserveTicketsInput(
    @NotBlank(message = "Event ID is required")
    String eventId,

    @NotNull(message = "Ticket selections are required")
    @Size(min = 1, message = "At least one ticket selection is required")
    List<TicketSelectionInput> selections
) {}
