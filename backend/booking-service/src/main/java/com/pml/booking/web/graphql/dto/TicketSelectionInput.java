package com.pml.booking.web.graphql.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Input for selecting a quantity of tickets from a specific tier.
 */
public record TicketSelectionInput(
    @NotBlank(message = "Ticket tier ID is required")
    String ticketTierId,

    @Positive(message = "Quantity must be positive")
    int quantity
) {}
