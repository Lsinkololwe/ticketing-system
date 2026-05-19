package com.pml.identity.repository;

import com.pml.identity.domain.model.EventReminder;
import com.pml.identity.domain.enums.ReminderStatus;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Repository for managing EventReminder entities in MongoDB.
 * Provides reactive queries for event reminder scheduling and retrieval.
 */
@Repository
public interface EventReminderRepository extends ReactiveMongoRepository<EventReminder, String> {

    /**
     * Find reminders for a user with a specific status.
     *
     * @param userId the user ID
     * @param status the reminder status
     * @return Flux of reminders
     */
    Flux<EventReminder> findByUserIdAndStatus(String userId, ReminderStatus status);

    /**
     * Find reminders scheduled before a specific time with a given status.
     * Used by scheduler to find reminders that need to be sent.
     *
     * @param status the reminder status (typically SCHEDULED)
     * @param time the cutoff time
     * @return Flux of reminders ready to be sent
     */
    Flux<EventReminder> findByStatusAndReminderAtBefore(ReminderStatus status, LocalDateTime time);

    /**
     * Find a reminder by user ID and ticket ID.
     *
     * @param userId the user ID
     * @param ticketId the ticket ID
     * @return Mono containing the reminder, or empty if not found
     */
    Mono<EventReminder> findByUserIdAndTicketId(String userId, String ticketId);

    /**
     * Delete all reminders for a specific ticket.
     * Used when a ticket is cancelled or refunded.
     *
     * @param ticketId the ticket ID
     * @return Mono signaling completion
     */
    Mono<Void> deleteByTicketId(String ticketId);

    /**
     * Find reminders for a user and specific event.
     *
     * @param userId the user ID
     * @param eventId the event ID
     * @return Flux of reminders for the user and event
     */
    Flux<EventReminder> findByUserIdAndEventId(String userId, String eventId);
}
