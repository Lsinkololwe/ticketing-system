package com.pml.identity.domain.enums;

/**
 * Schedule for automatic payouts.
 */
public enum PayoutSchedule {
    /**
     * Daily payouts (for high-volume organizers)
     */
    DAILY,

    /**
     * Weekly payouts (default)
     */
    WEEKLY,

    /**
     * Bi-weekly payouts (every 2 weeks)
     */
    BIWEEKLY,

    /**
     * Monthly payouts
     */
    MONTHLY,

    /**
     * Manual payouts only (organizer must request)
     */
    MANUAL
}
