package com.pml.identity.domain.model;

import com.pml.identity.domain.enums.ReminderStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Entity representing a scheduled reminder for an upcoming event.
 * Users can set reminders for events they have tickets for.
 */
@Document(collection = "event_reminders")
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
    @Indexed
    private String userId;

    /**
     * ID of the event to remind about
     */
    @Indexed
    private String eventId;

    /**
     * ID of the ticket associated with this reminder
     */
    private String ticketId;

    /**
     * Timestamp when the reminder should be sent
     */
    private LocalDateTime reminderAt;

    /**
     * Current status of the reminder
     */
    private ReminderStatus status;

    /**
     * Timestamp when reminder was sent
     */
    private LocalDateTime sentAt;

    /**
     * Timestamp when reminder was created
     */
    @CreatedDate
    private LocalDateTime createdAt;
}
