package com.pml.identity.service;

import com.pml.identity.domain.model.EventReminder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Service interface for managing event reminders.
 *
 * <p>The reads GraphQL uses and the writes {@code ReminderWorkflow}'s activities
 * make. The timers themselves are the workflow's.
 */
public interface EventReminderService {

    Flux<EventReminder> findByUserId(String userId);

    Flux<EventReminder> findByUserIdAndEventId(String userId, String eventId);

    Mono<EventReminder> findById(String reminderId);

    Mono<EventReminder> findByUserIdAndTicketId(String userId, String ticketId);

    /** Upserts the reminder as {@code SCHEDULED} for an event starting at {@code eventStartsAt}. */
    Mono<EventReminder> schedule(String reminderId, String userId, String ticketId, Instant eventStartsAt, Instant reminderAt);

    /** {@code CANCELLED}; a cancelled reminder is returned unchanged. */
    Mono<EventReminder> cancel(String reminderId);

    /** {@code SCHEDULED} to {@code SENT}; any other status is returned unchanged. */
    Mono<EventReminder> markSent(String reminderId);
}
