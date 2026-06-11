package com.pml.booking.web.graphql.dto.organizer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Upcoming event summary for organizer's dashboard.
 *
 * Shows key metrics for events on the dashboard:
 * - Basic event info (title, date, status)
 * - Ticket sales progress
 * - Revenue earned
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizerUpcomingEvent {

    /**
     * Event ID
     */
    private String id;

    /**
     * Event title
     */
    private String title;

    /**
     * Event date and time
     */
    private LocalDateTime eventDateTime;

    /**
     * Number of tickets sold
     */
    @Builder.Default
    private Integer ticketsSold = 0;

    /**
     * Total ticket capacity
     */
    @Builder.Default
    private Integer totalCapacity = 0;

    /**
     * Event status (published, draft, ended)
     */
    private String status;

    /**
     * Total revenue from ticket sales
     */
    @Builder.Default
    private BigDecimal revenue = BigDecimal.ZERO;

    /**
     * Currency for revenue
     */
    @Builder.Default
    private String currency = "ZMW";

    /**
     * Calculate ticket sales percentage
     */
    public Float getSalesPercentage() {
        if (totalCapacity == null || totalCapacity == 0) {
            return 0.0f;
        }
        return (float) ticketsSold / totalCapacity * 100;
    }
}
