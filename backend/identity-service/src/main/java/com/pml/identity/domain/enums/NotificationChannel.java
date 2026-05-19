package com.pml.identity.domain.enums;

/**
 * Enumeration of channels through which notifications can be delivered.
 */
public enum NotificationChannel {
    /**
     * Push notification to mobile device or web browser
     */
    PUSH,

    /**
     * SMS text message
     */
    SMS,

    /**
     * WhatsApp message
     */
    WHATSAPP,

    /**
     * Email notification
     */
    EMAIL,

    /**
     * In-app notification (stored in database only)
     */
    IN_APP
}
