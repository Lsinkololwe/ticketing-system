package com.pml.booking.web.graphql.dto;

import com.pml.shared.constants.TicketStatus;
import java.time.Instant;

public record TicketFilterInput(
        String eventId,
        String buyerId,
        String organizerId,
        TicketStatus status,
        java.util.List<TicketStatus> statuses,
        String category,
        Instant purchaseDateAfter,
        Instant purchaseDateBefore,
        @jakarta.validation.constraints.Size(max = 100) String searchQuery
) {}
