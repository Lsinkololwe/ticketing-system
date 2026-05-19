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
    FAILED
}
