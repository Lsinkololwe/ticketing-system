package com.pml.booking.web.graphql.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tier-level check-in statistics for live dashboard.
 *
 * Business Intent: Shows check-in progress by ticket tier (VIP, General, etc.)
 * to help organizers understand which attendee segments have arrived.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TierCheckInStats {

    /** Ticket tier identifier */
    private String tierId;

    /** Tier name for display (e.g., "VIP", "General Admission") */
    private String tierName;

    /** Total tickets sold for this tier */
    private int sold;

    /** Number of tickets checked in for this tier */
    private int checkedIn;

    /** Check-in rate as percentage (checkedIn / sold * 100) */
    private float checkInRate;

    /**
     * Factory method with automatic rate calculation.
     */
    public static TierCheckInStats of(String tierId, String tierName, int sold, int checkedIn) {
        float rate = sold > 0 ? ((float) checkedIn / sold) * 100 : 0f;
        return new TierCheckInStats(tierId, tierName, sold, checkedIn, rate);
    }
}
