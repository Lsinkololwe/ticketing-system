package com.pml.booking.domain.model;

import com.pml.booking.domain.enums.TicketTransferStatus;
import com.pml.booking.persistence.BookingCollections;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One offer of one ticket from its holder to another registered user.
 *
 * <p>The recipient is resolved from a verified contact when the transfer is made and held here as an
 * account id; the contact itself is not stored, only a masked rendering for the sender to recognise.
 * There is no claim token: accepting needs the recipient's own sign-in and a match with {@link #toUserId},
 * so a forwarded notification is worth nothing to whoever forwards it to.
 *
 * <p>The ticket's QR is fixed for its life. A transfer changes who holds the ticket, never what the
 * ticket is, so the gate's fallback (ticket code plus id) keeps working across a transfer.
 */
@Document(collection = BookingCollections.TICKET_TRANSFERS)
@TypeAlias("ticket_transfers")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TicketTransfer {

    @Id
    private String id;

    @Version
    private Long version;

    private String ticketId;
    private String ticketNumber;
    private String bookingId;
    private String bookingNumber;
    private String eventId;
    private String eventTitle;
    private String organizationId;

    private String fromUserId;
    /** First name and initial, as identity renders it; never the full name. */
    private String fromDisplayName;
    private String toUserId;
    private String toDisplayName;
    /** WHATSAPP or EMAIL: how the sender named the recipient. */
    private String recipientChannel;
    /** The contact the sender typed, masked. */
    private String recipientMasked;

    /** An optional note from the sender, shown to the recipient. */
    private String note;

    private TicketTransferStatus status;

    private Instant expiresAt;
    private Instant resolvedAt;
    /** Who ended it: the recipient, the sender, or {@code SYSTEM} for an expiry. */
    private String resolvedBy;

    @CreatedDate
    private Instant createdAt;
}
