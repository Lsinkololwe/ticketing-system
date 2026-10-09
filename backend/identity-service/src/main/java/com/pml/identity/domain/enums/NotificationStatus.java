package com.pml.identity.domain.enums;

/**
 * Enumeration representing the lifecycle status of a notification.
 */
public enum NotificationStatus {
    /**
     * Notification has been created but not yet sent
     */
    PENDING,

    /**
     * Notification has been sent to the delivery channel
     */
    SENT,

    /**
     * Notification has been delivered to the recipient
     */
    DELIVERED,

    /**
     * Notification has been read by the user
     */
    READ,

    /**
     * Notification failed to send or deliver
     */
    FAILED,

    /**
     * Not sent, because the recipient switched off this optional category, every channel it could
     * use, or it fell in their quiet hours. Recorded rather than dropped, with the reason.
     */
    SUPPRESSED
}
