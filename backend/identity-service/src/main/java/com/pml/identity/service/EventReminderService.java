package com.pml.identity.service;

import com.pml.identity.web.graphql.dto.SetEventReminderInput;
import com.pml.identity.domain.model.EventReminder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service interface for managing event reminders.
 * Handles reminder scheduling, cancellation, and automated delivery.
 */
public interface EventReminderService {

    /**
     * Set a reminder for an event.
     *
     * @param userId the user ID
     * @param input reminder details
     * @return Mono containing the created/updated reminder
     */
    Mono<EventReminder> setReminder(String userId, SetEventReminderInput input);

    /**
     * Cancel a reminder.
     *
     * @param reminderId the reminder ID
     * @return Mono containing true if cancelled successfully
     */
    Mono<Boolean> cancelReminder(String reminderId);

    /**
     * Find all reminders for a user.
     *
     * @param userId the user ID
     * @return Flux of reminders
     */
    Flux<EventReminder> findByUserId(String userId);

    /**
     * Find reminders for a user and specific event.
     *
     * @param userId the user ID
     * @param eventId the event ID
     * @return Flux of reminders for the event
     */
    Flux<EventReminder> findByUserIdAndEventId(String userId, String eventId);

    /**
     * Process scheduled reminders that are due.
     * Called by scheduler to send reminders.
     *
     * @return Mono signaling completion
     */
    Mono<Void> processScheduledReminders();
}
