package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.Ticket;

import java.util.List;
import java.util.Map;

/**
 * Generic Ticket Mutation Response DTO
 *
 * Business Intent: Standard response for admin ticket operations.
 * Used by adminUpdateTicket, regenerateTicketQrCode, etc.
 */
public record TicketMutationResponse(
        boolean success,
        String message,
        Ticket data,
        List<String> errors,
        Map<String, Object> metadata
) {
    /**
     * Factory method for successful operations.
     */
    public static TicketMutationResponse success(String message, Ticket ticket) {
        return new TicketMutationResponse(true, message, ticket, List.of(), null);
    }

    /**
     * Factory method for failed operations.
     */
    public static TicketMutationResponse error(String message) {
        return new TicketMutationResponse(false, message, null, List.of(message), null);
    }

    /**
     * Factory method for failed operations with multiple errors.
     */
    public static TicketMutationResponse error(String message, List<String> errors) {
        return new TicketMutationResponse(false, message, null, errors, null);
    }
}
