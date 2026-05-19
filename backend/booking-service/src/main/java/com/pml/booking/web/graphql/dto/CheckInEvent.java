package com.pml.booking.web.graphql.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Individual check-in event for live dashboard feed.
 *
 * Business Intent: Provides real-time feed of attendee arrivals
 * for organizer's event day dashboard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInEvent {

    /** Ticket identifier */
    private String ticketId;

    /** Ticket number for display */
    private String ticketNumber;

    /** Ticket tier name */
    private String tierName;

    /** Buyer name (optional, for personalized display) */
    private String buyerName;

    /** When the check-in occurred */
    private LocalDateTime checkedInAt;

    /** Scanner device/user ID */
    private String scannerId;

    /** Scanner name for display */
    private String scannerName;

    /** Running total of checked-in attendees */
    private int totalCheckedIn;
}
