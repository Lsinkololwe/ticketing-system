package com.pml.booking.web.graphql.dto;

import com.pml.shared.constants.TicketStatus;
import java.time.Instant;

public record TicketFilterInput(
        String eventId,
        String buyerId,
        TicketStatus status,
        String category,
        Instant purchaseDateAfter,
        Instant purchaseDateBefore,
        Integer limit,
        Integer offset
) {}
