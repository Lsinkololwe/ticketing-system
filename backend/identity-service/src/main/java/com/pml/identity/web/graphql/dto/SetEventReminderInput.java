package com.pml.identity.web.graphql.dto;

import java.time.Instant;

/**
 * Input DTO for setting an event reminder.
 *
 * <p>The caller supplies the event's real start so the workflow's two fixed
 * offsets (T-24h, T-1h) are computed against it, not against the moment this mutation is called.
 *
 * @param ticketId the ID of the ticket to set a reminder for
 * @param eventStartsAt the event's start instant
 */
public record SetEventReminderInput(
    String ticketId,
    Instant eventStartsAt
) {}
