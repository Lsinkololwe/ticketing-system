package com.pml.catalog.domain.enums;

/**
 * ApprovalNotificationChannel Enum
 *
 * Defines the delivery channels for approval workflow notifications.
 */
public enum ApprovalNotificationChannel {

    EMAIL("EMAIL", "Email", "Notification sent via email"),
    IN_APP("IN_APP", "In-App", "Notification shown in application"),
    BOTH("BOTH", "Both", "Notification sent via both email and in-app");

    private final String code;
    private final String displayName;
    private final String description;

    ApprovalNotificationChannel(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static ApprovalNotificationChannel fromCode(String code) {
        for (ApprovalNotificationChannel channel : values()) {
            if (channel.code.equals(code)) {
                return channel;
            }
        }
        return null;
    }

    /**
     * Check if this channel includes email
     */
    public boolean includesEmail() {
        return this == EMAIL || this == BOTH;
    }

    /**
     * Check if this channel includes in-app notifications
     */
    public boolean includesInApp() {
        return this == IN_APP || this == BOTH;
    }
}
