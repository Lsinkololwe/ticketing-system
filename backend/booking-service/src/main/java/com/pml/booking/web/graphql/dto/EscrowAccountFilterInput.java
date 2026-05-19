package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.EventEscrowAccount.EscrowStatus;

/**
 * Filter input for escrow account queries.
 */
public record EscrowAccountFilterInput(
        String organizerId,
        String eventId,
        EscrowStatus status,
        String currency
) {}
