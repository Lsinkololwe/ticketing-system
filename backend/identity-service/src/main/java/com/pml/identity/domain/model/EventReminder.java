package com.pml.identity.domain.model;

import com.pml.identity.persistence.IdentityCollections;

import com.pml.identity.domain.enums.ReminderStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Entity representing a scheduled reminder for an upcoming event.
 * Users can set reminders for events they have tickets for.
 */
@Document(collection = IdentityCollections.EVENT_REMINDERS)
@TypeAlias("event_reminders")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class EventReminder {

    /**
     * Unique identifier for the reminder
     */
    @Id
    private String id;

    /**
     * ID of the user who set the reminder
     */
    private String userId;

    /**
     * ID of the event to remind about
     */
    private String eventId;

    /**
     * ID of the ticket associated with this reminder
     */
    private String ticketId;

    /**
     * Timestamp when the reminder should be sent
     */
    private Instant reminderAt;

    /**
     * When the event starts; the reminders fire 24 hours and 1 hour before it.
     * {@code reminderAt} holds the next of those moments.
     */
    private Instant eventStartsAt;

    /**
     * Current status of the reminder
     */
    private ReminderStatus status;

    /**
     * Timestamp when reminder was sent
     */
    private Instant sentAt;

    /**
     * Timestamp when reminder was created
     */
    @CreatedDate
    private Instant createdAt;
}
