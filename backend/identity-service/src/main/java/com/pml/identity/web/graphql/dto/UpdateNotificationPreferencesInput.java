package com.pml.identity.web.graphql.dto;

/**
 * Input DTO for updating user notification preferences.
 * All fields are optional - only provided fields will be updated.
 *
 * @param emailEnabled whether to enable email notifications
 * @param smsEnabled whether to enable SMS notifications
 * @param whatsappEnabled whether to enable WhatsApp notifications
 * @param pushEnabled whether to enable push notifications
 * @param eventReminders whether to receive event reminder notifications
 * @param marketingEmails whether to receive marketing emails
 * @param reminderHoursBefore how many hours before an event to send reminder
 */
public record UpdateNotificationPreferencesInput(
    Boolean emailEnabled,
    Boolean smsEnabled,
    Boolean whatsappEnabled,
    Boolean pushEnabled,
    Boolean eventReminders,
    Boolean marketingEmails,
    Integer reminderHoursBefore
) {}
