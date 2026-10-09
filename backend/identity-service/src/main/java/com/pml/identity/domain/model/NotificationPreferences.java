package com.pml.identity.domain.model;

import com.pml.identity.persistence.IdentityCollections;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Entity representing a user's notification delivery preferences.
 * Controls which channels are enabled and what types of notifications to receive.
 */
@Document(collection = IdentityCollections.NOTIFICATION_PREFERENCES)
@TypeAlias("notification_preferences")
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

    /** Whether in-app notifications are shown. */
    @Builder.Default
    private boolean inAppEnabled = true;

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

    /** Start of the nightly quiet period, {@code HH:mm} in {@link #timezone}; reminders are not sent inside it. */
    private String quietHoursStart;

    /** End of the quiet period, {@code HH:mm}; earlier than the start when the period crosses midnight. */
    private String quietHoursEnd;

    /** The IANA zone the quiet hours are read in; the platform zone when unset. */
    private String timezone;

    /**
     * Timestamp when preferences were created
     */
    @CreatedDate
    private Instant createdAt;

    /**
     * Timestamp when preferences were last updated
     */
    @LastModifiedDate
    private Instant updatedAt;

    // Messages about tickets, payments, refunds, payouts, event changes, the organization and team
    // invitations are always sent: they are not a preference, so nothing stores them and these
    // read as on for every user.

    public boolean isTicketNotifications() {
        return true;
    }

    public boolean isPaymentNotifications() {
        return true;
    }

    public boolean isEventUpdates() {
        return true;
    }

    public boolean isTeamNotifications() {
        return true;
    }

    public boolean isSystemAnnouncements() {
        return true;
    }

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
            .build();
    }
}
