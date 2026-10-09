package com.pml.catalog.domain.model;

import com.pml.shared.constants.Money;

import com.pml.catalog.persistence.CatalogCollections;

import com.pml.catalog.domain.valueobject.CheckoutSettings;
import com.pml.catalog.domain.valueobject.EventAccessibility;
import com.pml.catalog.domain.valueobject.EventFaq;
import com.pml.catalog.domain.valueobject.RunningOrderItem;

import com.pml.shared.constants.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Event Model
 *
 * Represents an event with location information, ticket categories, and pricing.
 */
@Document(collection = CatalogCollections.EVENTS)
@TypeAlias("events")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Event {

    @Id
    private String id;

    /**
     * Every monetary field carries a currency sibling. Launch is ZMW-only
     * and the field still exists: adding a second currency later becomes a data
     * migration rather than an audit of which amounts meant what.
     */
    @Builder.Default
    private String currency = Money.DEFAULT_CURRENCY;

    @NotBlank(message = "Event title is required")
    @Size(min = 3, max = 200)
    private String title;

    @NotBlank(message = "Event description is required")
    @Size(min = 10, max = 2000)
    private String description;

    @NotNull(message = "Event category is required")
    private String categoryId;

    @NotNull(message = "Event date and time is required")
    private Instant eventDateTime;

    @NotNull(message = "Event end time is required")
    private Instant endDateTime;

    private String locationId;
    private String locationName;
    private String locationAddress;

    private String cityName;

    /** The reference-data {@code CITY} code of the venue: the discovery city filter reads this, never a typed name. */
    private String cityId;

    /**
     * The cheapest price among the event's on-sale tiers, kept by every tier write so a price
     * filter never joins to the tiers. Null when the event has no visible tier.
     */
    private java.math.BigDecimal lowestTicketPrice;

    @NotNull(message = "Event organizer is required")
    private String organizerId;

    /**
     * Organization ID that owns this event.
     * Direct link to Organization entity in identity-service.
     * Used for authorization - team members of this organization can manage this event.
     */
    private String organizationId;

    private String organizerName;

    // ═══════════════════════════════════════════════════════════════════════════
    // ORGANIZER CONTACT INFORMATION
    // ═══════════════════════════════════════════════════════════════════════════

    private String organizerFirstName;
    private String organizerLastName;
    private String organizerCompanyName;
    private String organizerEmail;
    private String organizerPhone;
    private String organizerBusinessEmail;
    private String organizerBusinessPhone;

    @NotNull(message = "Event status is required")
    private EventStatus status;

    @Builder.Default
    private boolean published = false;
    private Instant publishedAt;

    @Positive(message = "Total capacity must be positive")
    private int totalCapacity;

    private int availableTickets;

    @Builder.Default
    private int soldTickets = 0;

    private List<EventTicketCategory> ticketCategories;

    private String bannerImageUrl;

    /**
     * Thumbnail image URL for listings
     */
    private String thumbnailImageUrl;

    /**
     * Gallery images for event details
     */
    private List<String> galleryImages;

    private List<String> tags;

    private Map<String, Object> additionalInfo;

    // ═══════════════════════════════════════════════════════════════════════════
    // VIRTUAL EVENT FIELDS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Whether this is a virtual/online event
     */
    @Builder.Default
    private boolean isVirtual = false;

    /**
     * URL for virtual event (Zoom, Teams, etc.)
     */
    private String virtualEventUrl;

    /**
     * Platform for virtual event (zoom, teams, google_meet, etc.)
     */
    private String virtualEventPlatform;

    // ═══════════════════════════════════════════════════════════════════════════
    // RECURRING EVENT FIELDS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Whether this is a recurring event
     */
    @Builder.Default
    private boolean isRecurring = false;

    /**
     * Recurrence pattern (DAILY, WEEKLY, MONTHLY, etc.)
     */
    private String recurrencePattern;

    /**
     * Parent event ID for recurring event instances
     */
    private String parentEventId;

    // ═══════════════════════════════════════════════════════════════════════════
    // WAITLIST FIELDS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Whether this event has a waitlist
     */
    @Builder.Default
    private boolean hasWaitlist = false;

    /**
     * Whether waitlist is currently enabled
     */
    @Builder.Default
    private boolean waitlistEnabled = false;

    /**
     * Maximum capacity of waitlist
     */
    private Integer waitlistCapacity;

    // ═══════════════════════════════════════════════════════════════════════════
    // POLICY FIELDS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Refund policy text
     */
    private String refundPolicy;

    /**
     * Cancellation policy text
     */
    private String cancellationPolicy;

    /**
     * Terms and conditions text
     */
    private String termsAndConditions;

    // ═══════════════════════════════════════════════════════════════════════════
    // VERSION FIELD (for optimistic locking)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Version for optimistic locking.
     * Prevents race conditions during concurrent event updates.
     * Automatically incremented on each save operation.
     *
     * OWASP A04:2021 Compliance: Ensures data integrity under concurrent access.
     */
    @Version
    private Long version;

    // ═══════════════════════════════════════════════════════════════════════════
    // APPROVAL WORKFLOW FIELDS
    // ═══════════════════════════════════════════════════════════════════════════

    private Instant submittedForApprovalAt;
    private Instant approvalDeadline;

    @Builder.Default
    private boolean isOverdue = false;

    /**
     * Assigned reviewer for this event (null if unassigned)
     */
    private String assignedReviewerId;
    private String assignedReviewerName;

    /**
     * When the event was approved
     */
    private Instant approvedAt;

    /**
     * Admin who approved the event
     */
    private String approvedBy;

    /**
     * When the event was rejected
     */
    private Instant rejectedAt;

    /**
     * Admin who rejected the event
     */
    private String rejectedBy;

    /**
     * Reason for rejection (required when rejecting)
     */
    private String rejectionReason;

    /**
     * When changes were requested
     */
    private Instant changesRequestedAt;

    /**
     * Admin who requested changes
     */
    private String changesRequestedBy;

    /**
     * Comments for requested changes
     */
    private String changesRequestedComments;

    /**
     * Number of times the event has been submitted for approval
     */
    @Builder.Default
    private int submissionCount = 0;

    /**
     * The start this event had before its latest reschedule, so a holder can see
     * what changed.
     */
    private Instant previousStartsAt;

    /** How many times this event has been rescheduled; capped at three. */
    @Builder.Default
    private int rescheduleCount = 0;

    @Builder.Default
    private boolean featured = false;

    /**
     * Indicates if this is a free event (no ticket price).
     * Set to true when all ticket categories have price = 0 or when organizer marks it as free.
     */
    @Builder.Default
    private boolean isFreeEvent = false;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;

    @Builder.Default
    private boolean isActive = true;

    // ═══════════════════════════════════════════════════════════════════════════
    // SOFT DELETE FIELDS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Indicates if this event has been soft deleted.
     * Soft deleted events are excluded from queries but retained for audit purposes.
     */
    @Builder.Default
    private boolean isDeleted = false;

    /**
     * When the event was soft deleted.
     */
    private Instant deletedAt;

    /**
     * User ID who deleted the event.
     * Used for audit trail.
     */
    private String deletedBy;

    /**
     * Reason for deletion (optional).
     */
    private String deletionReason;

    /**
     * Accessibility information for this event
     */
    private EventAccessibility accessibility;

    // ═══════════════════════════════════════════════════════════════════════════
    // EVENT PAGE CONTENT · what the organizer says about the event (ET-CAT-004)
    // ═══════════════════════════════════════════════════════════════════════════

    /** One line under the title; at most 100 characters. */
    private String tagline;

    /** One of {@code ALL_AGES}, {@code 13+}, {@code 16+}, {@code 18+}, {@code 21+}. */
    private String ageRestriction;

    /** When the doors open; on or before the start. */
    private Instant doorsOpenAt;

    /** Alt text for the banner image. */
    private String bannerAltText;

    private List<EventFaq> faqs;

    /** The programme, in the order the organizer gave it. */
    private List<RunningOrderItem> runningOrder;

    /** Public transport and directions. */
    private String gettingThere;

    private String parkingInfo;

    /** What may and may not be brought in. */
    private String bagPolicy;

    private CheckoutSettings checkoutSettings;

    // ═══════════════════════════════════════════════════════════════════════════
    // SCHEDULED PUBLICATION
    // ═══════════════════════════════════════════════════════════════════════════

    /** When the organizer wants the event to go live; only honoured once {@link #publishScheduled}. */
    private Instant publishAt;

    /** The organizer pressed Publish with a future {@link #publishAt}: the timer is running. */
    @Builder.Default
    private boolean publishScheduled = false;

    // ═══════════════════════════════════════════════════════════════════════════
    // CANCELLATION
    // ═══════════════════════════════════════════════════════════════════════════

    private String cancellationReason;

    private Instant cancelledAt;

    // ═══════════════════════════════════════════════════════════════════════════
    // SALES TOTALS · denormalised from booking's commits and refunds
    // ═══════════════════════════════════════════════════════════════════════════

    /** What buyers paid for tickets still held; booking reports it with each commit and refund. */
    @Builder.Default
    private BigDecimal grossSales = BigDecimal.ZERO;

    /** The platform's commission on {@link #grossSales}. */
    @Builder.Default
    private BigDecimal commissionAmount = BigDecimal.ZERO;

    /**
     * Event Ticket Category
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventTicketCategory {

        /** The tier document this mirrors; booking reserves and prices by it. */
        private String tierId;

        @NotBlank(message = "Category code is required")
        private String code;

        @NotBlank(message = "Category name is required")
        private String name;

        private String description;

        @NotNull(message = "Price is required")
        @jakarta.validation.constraints.PositiveOrZero(message = "Price cannot be negative")
        private BigDecimal price;

        @Positive(message = "Quantity must be positive")
        private int quantity;

        private int availableQuantity;

        @Builder.Default
        private boolean active = true;

        /** Hidden from the public listing; reserved only with the tier's access code. */
        private boolean hidden;

        private List<String> benefits;

        @Builder.Default
        private boolean isEarlyBird = false;
        private Instant earlyBirdEndDate;
        private BigDecimal earlyBirdPrice;

        public String getFormattedPrice() {
            return "K " + price.toString();
        }

        public boolean hasAvailableTickets() {
            return active && availableQuantity > 0;
        }

        public int getSoldQuantity() {
            return quantity - availableQuantity;
        }
    }

    public BigDecimal getTotalRevenue() {
        if (ticketCategories == null) return BigDecimal.ZERO;

        return ticketCategories.stream()
                .map(cat -> cat.getPrice().multiply(BigDecimal.valueOf(cat.getSoldQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isSoldOut() {
        return availableTickets <= 0;
    }
}
