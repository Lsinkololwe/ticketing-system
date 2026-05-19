package com.pml.identity.domain.enums;

/**
 * Enumeration of notification types supported by the system.
 * Defines the category/purpose of each notification sent to users.
 */
public enum NotificationType {
    /**
     * Notification sent when a user purchases a ticket
     */
    TICKET_PURCHASED,

    /**
     * Reminder notification before an event starts
     */
    EVENT_REMINDER,

    /**
     * Notification sent when an event is cancelled
     */
    EVENT_CANCELLED,

    /**
     * Notification sent when event details are updated
     */
    EVENT_UPDATED,

    /**
     * Notification sent when a refund is processed
     */
    REFUND_PROCESSED,

    /**
     * Notification sent when organizer payout is completed
     */
    PAYOUT_COMPLETED,

    /**
     * Notification sent when organizer account is approved
     */
    ORGANIZER_APPROVED,

    /**
     * Notification sent when organizer account is rejected
     */
    ORGANIZER_REJECTED,

    /**
     * System-wide announcement notification
     */
    SYSTEM_ANNOUNCEMENT
}
