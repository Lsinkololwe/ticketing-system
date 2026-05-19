package com.pml.identity.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Entity representing a user's notification delivery preferences.
 * Controls which channels are enabled and what types of notifications to receive.
 */
@Document(collection = "notification_preferences")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferences {

    /**
     * Unique identifier for the preferences document
     */
    @Id
    private String id;

    /**
     * ID of the user these preferences belong to
     */
    @Indexed(unique = true)
    private String userId;

    /**
     * Whether email notifications are enabled
     */
    @Builder.Default
    private boolean emailEnabled = true;

    /**
     * Whether SMS notifications are enabled
     */
    @Builder.Default
    private boolean smsEnabled = true;

    /**
     * Whether WhatsApp notifications are enabled
     */
    @Builder.Default
    private boolean whatsappEnabled = true;

    /**
     * Whether push notifications are enabled
     */
    @Builder.Default
    private boolean pushEnabled = true;

    /**
     * Whether to receive event reminder notifications
     */
    @Builder.Default
    private boolean eventReminders = true;

    /**
     * Whether to receive marketing emails
     */
    @Builder.Default
    private boolean marketingEmails = false;

    /**
     * How many hours before an event to send reminder (default: 24)
     */
    @Builder.Default
    private int reminderHoursBefore = 24;

    /**
     * Timestamp when preferences were created
     */
    @CreatedDate
    private LocalDateTime createdAt;

    /**
     * Timestamp when preferences were last updated
     */
    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * Factory method to create default preferences for a new user.
     *
     * @param userId the user ID
     * @return NotificationPreferences with default settings
     */
    public static NotificationPreferences defaultPreferences(String userId) {
        return NotificationPreferences.builder()
            .userId(userId)
            .emailEnabled(true)
            .smsEnabled(true)
            .whatsappEnabled(true)
            .pushEnabled(true)
            .eventReminders(true)
            .marketingEmails(false)
            .reminderHoursBefore(24)
            .createdAt(LocalDateTime.now())
            .build();
    }
}
