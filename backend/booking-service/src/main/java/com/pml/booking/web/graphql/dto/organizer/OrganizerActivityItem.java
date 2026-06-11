package com.pml.booking.web.graphql.dto.organizer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Activity item for the organizer's dashboard activity feed.
 *
 * Represents recent activities such as:
 * - Ticket sales
 * - Check-ins
 * - Event publishing
 * - Payout completions
 * - Refund processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizerActivityItem {

    /**
     * Unique identifier for this activity
     */
    private String id;

    /**
     * Type of activity
     */
    private OrganizerActivityType type;

    /**
     * Human-readable activity message
     */
    private String message;

    /**
     * When the activity occurred
     */
    private LocalDateTime timestamp;

    /**
     * Associated event ID (if applicable)
     */
    private String eventId;

    /**
     * Associated event title (if applicable)
     */
    private String eventTitle;

    /**
     * Amount involved (for financial activities)
     */
    private BigDecimal amount;

    /**
     * Currency of the amount
     */
    private String currency;

    /**
     * Activity type enum matching GraphQL schema
     */
    public enum OrganizerActivityType {
        TICKET_SALE,
        CHECK_IN,
        EVENT_PUBLISHED,
        PAYOUT_COMPLETED,
        PAYOUT_REQUESTED,
        REFUND_PROCESSED,
        EVENT_CREATED
    }
}
