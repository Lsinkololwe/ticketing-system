package com.pml.booking.web.graphql.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Live Dashboard DTO for event day check-in management.
 *
 * Business Intent: Provides real-time check-in statistics for organizers
 * during active events. Used for entry management dashboards.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveDashboard {

    /** Event identifier */
    private String eventId;

    /** Event title for display */
    private String eventTitle;

    /** Total tickets sold for this event */
    private int totalSold;

    /** Total venue capacity */
    private int totalCapacity;

    /** Number of attendees who have checked in */
    private int checkedIn;

    /** Check-in rate as percentage (checkedIn / totalSold * 100) */
    private float checkInRate;

    /** Number of check-ins in the last hour */
    private int checkInsLastHour;

    /** Peak check-in time (highest activity) */
    private LocalDateTime peakCheckInTime;

    /** Current check-in rate per minute */
    private Float currentCheckInRate;

    /** Check-in statistics broken down by ticket tier */
    @Builder.Default
    private List<TierCheckInStats> checkInsByTier = List.of();

    /** Recent check-in events for live feed */
    @Builder.Default
    private List<CheckInEvent> recentCheckIns = List.of();

    /**
     * Factory method to create an empty dashboard for events with no activity.
     */
    public static LiveDashboard empty(String eventId, String eventTitle, int totalCapacity) {
        return LiveDashboard.builder()
                .eventId(eventId)
                .eventTitle(eventTitle)
                .totalSold(0)
                .totalCapacity(totalCapacity)
                .checkedIn(0)
                .checkInRate(0f)
                .checkInsLastHour(0)
                .currentCheckInRate(0f)
                .checkInsByTier(List.of())
                .recentCheckIns(List.of())
                .build();
    }
}
