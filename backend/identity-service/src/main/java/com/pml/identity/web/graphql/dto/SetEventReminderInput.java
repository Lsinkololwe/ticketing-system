package com.pml.identity.web.graphql.dto;

/**
 * Input DTO for setting an event reminder.
 *
 * @param ticketId the ID of the ticket to set reminder for
 * @param minutesBefore how many minutes before the event to send reminder
 */
public record SetEventReminderInput(
    String ticketId,
    int minutesBefore
) {}
