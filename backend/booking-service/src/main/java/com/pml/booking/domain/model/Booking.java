package com.pml.booking.domain.model;

import com.pml.booking.domain.enums.BookingStatus;
import com.pml.booking.persistence.BookingCollections;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A purchase as a person understands it: the number on the receipt, who it is for, what was
 * bought and where it stands.
 *
 * <p>A reservation is the hold and is removed by TTL shortly after it lapses; tickets are one
 * row per seat. Neither can answer "show me BK-2026-00001234" for ever, so the booking is its
 * own durable document, keyed one-to-one by {@code reservationId}.
 *
 * <p>The fields are written at three moments only: when the hold is taken (everything but the
 * outcome), when the reservation leaves {@code HELD} ({@link #status}), and when money or
 * tickets move afterwards ({@link #refundedAmount}, {@link #cancelledTicketCount},
 * {@link #lateRefundStatus}). What a viewer is shown is a pure function of those fields.
 */
@Document(collection = BookingCollections.BOOKINGS)
@TypeAlias("bookings")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    @Id
    private String id;

    @Version
    private Long version;

    /** {@code BK-2026-00001234}; unique. */
    private String bookingNumber;

    /** One booking per reservation; unique. Tickets carry this as their {@code bookingId}. */
    private String reservationId;

    private String eventId;
    private String eventTitle;
    private String eventDate;

    /** The buyer who paid. Transfers never move it: a refund returns to whoever paid. */
    private String buyerId;
    private String organizerId;
    private String organizationId;

    /** Who the order is for; the email is optional. */
    private String contactName;
    private String contactEmail;
    private String contactPhone;

    private List<Item> items;

    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    @Builder.Default
    private String currency = "ZMW";
    private String promoCode;

    /** The stored lifecycle: PENDING, CONFIRMED, CANCELLED, EXPIRED or FAILED. */
    private BookingStatus status;

    private int ticketCount;

    /** Sum of completed refunds against this booking's tickets. */
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    /** Tickets cancelled by an operator or an event cancellation. */
    private int cancelledTicketCount;

    /** The state of the automatic refund of a payment that arrived after the hold lapsed, if any. */
    private String lateRefundStatus;

    private Instant expiresAt;
    private Instant confirmedAt;
    private Instant closedAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String ticketTierId;
        private String tierName;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }
}
