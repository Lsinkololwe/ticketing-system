package com.pml.booking.domain.model;

import com.pml.booking.persistence.BookingCollections;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A message an organizer sent to the holders of an event's tickets: who sent it, to how many and what
 * it said. The recipients are not stored — a message is sent to accounts, and which accounts is a fact
 * about the tickets at the moment it went, not something to keep a second copy of.
 */
@Document(collection = BookingCollections.HOLDER_MESSAGES)
@TypeAlias("holder_messages")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HolderMessage {

    public enum Segment { ALL, ADMITTED, NOT_ADMITTED }

    public enum Status { SENDING, SENT, PARTIAL, FAILED }

    @Id
    private String id;

    private String eventId;
    private String eventTitle;
    private String organizationId;
    private String sentBy;

    private String subject;
    private String body;
    private Segment segment;
    private String ticketTierId;

    private int recipientCount;
    private int deliveredCount;
    private Status status;

    @CreatedDate
    private Instant createdAt;
}
