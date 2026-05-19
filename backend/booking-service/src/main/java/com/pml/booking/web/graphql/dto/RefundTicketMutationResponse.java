package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.Ticket;
import java.util.List;
import java.util.Map;

public record RefundTicketMutationResponse(
        boolean success,
        String message,
        Ticket data,
        List<String> errors,
        Map<String, Object> metadata
) {}
