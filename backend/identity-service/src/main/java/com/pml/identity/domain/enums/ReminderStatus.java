package com.pml.identity.domain.enums;

/**
 * Enumeration representing the status of an event reminder.
 */
public enum ReminderStatus {
    /**
     * Reminder is scheduled for future delivery
     */
    SCHEDULED,

    /**
     * Reminder has been sent
     */
    SENT,

    /**
     * Reminder has been cancelled by user or system
     */
    CANCELLED
}
