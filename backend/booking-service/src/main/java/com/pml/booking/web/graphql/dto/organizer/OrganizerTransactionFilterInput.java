package com.pml.booking.web.graphql.dto.organizer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Filter input for organizer transaction queries.
 */
public record OrganizerTransactionFilterInput(
        OrganizerTransaction.OrganizerTransactionType type,
        String eventId,
        LocalDateTime startDate,
        LocalDateTime endDate,
        BigDecimal minAmount,
        BigDecimal maxAmount
) {
    /**
     * Creates an empty filter (no filtering)
     */
    public static OrganizerTransactionFilterInput empty() {
        return new OrganizerTransactionFilterInput(null, null, null, null, null, null);
    }
}
