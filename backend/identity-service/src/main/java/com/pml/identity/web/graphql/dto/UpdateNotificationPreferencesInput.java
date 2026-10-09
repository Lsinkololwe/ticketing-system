package com.pml.identity.web.graphql.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The {@code UpdateNotificationPreferencesInput} GraphQL type; a null field leaves the stored value.
 *
 * <p>The five essential categories — tickets, payments, event updates, team, system — are always on:
 * sending {@code true} is accepted, sending {@code false} is refused rather than ignored.
 */
public record UpdateNotificationPreferencesInput(
        Boolean emailEnabled,
        Boolean smsEnabled,
        Boolean whatsappEnabled,
        Boolean pushEnabled,
        Boolean inAppEnabled,
        Boolean ticketNotifications,
        Boolean eventReminders,
        Boolean eventUpdates,
        Boolean paymentNotifications,
        Boolean teamNotifications,
        Boolean marketingEmails,
        Boolean systemAnnouncements,
        @Min(1) @Max(168) Integer reminderHoursBefore,
        @Pattern(regexp = QUIET_HOUR) String quietHoursStart,
        @Pattern(regexp = QUIET_HOUR) String quietHoursEnd,
        @Size(max = 64) String timezone
) {
    /** A 24-hour clock time, {@code 00:00} to {@code 23:59}. */
    public static final String QUIET_HOUR = "([01]\\d|2[0-3]):[0-5]\\d";
}
