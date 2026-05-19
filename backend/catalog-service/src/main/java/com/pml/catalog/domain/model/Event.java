package com.pml.catalog.domain.model;

import com.pml.catalog.domain.valueobject.EventAccessibility;

import com.pml.shared.constants.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Event Model
 *
 * Represents an event with location information, ticket categories, and pricing.
 */
@Document(collection = "events")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Event {

    @Id
    private String id;

    @NotBlank(message = "Event title is required")
    @Size(min = 3, max = 200)
    @TextIndexed
    private String title;

    @NotBlank(message = "Event description is required")
    @Size(min = 10, max = 2000)
    @TextIndexed
    private String description;

    @NotNull(message = "Event category is required")
    @Indexed
    private String categoryId;

    @NotNull(message = "Event date and time is required")
    @Indexed
    private LocalDateTime eventDateTime;

    @NotNull(message = "Event end time is required")
    private LocalDateTime endDateTime;

    private String locationId;
    private String locationName;
    private String locationAddress;

    @Indexed
    private String cityName;

    @NotNull(message = "Event organizer is required")
    @Indexed
    private String organizerId;

    /**
     * Organization ID that owns this event.
     * Direct link to Organization entity in identity-service.
     * Used for authorization - team members of this organization can manage this event.
     */
    @Indexed
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
    @Indexed
    private EventStatus status;

    @Builder.Default
    private boolean published = false;
    private LocalDateTime publishedAt;

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

    private LocalDateTime submittedForApprovalAt;
    private LocalDateTime approvalDeadline;

    @Builder.Default
    private boolean isOverdue = false;

    /**
     * Assigned reviewer for this event (null if unassigned)
     */
    @Indexed
    private String assignedReviewerId;
    private String assignedReviewerName;

    /**
     * When the event was approved
     */
    private LocalDateTime approvedAt;

    /**
     * Admin who approved the event
     */
    private String approvedBy;

    /**
     * When the event was rejected
     */
    private LocalDateTime rejectedAt;

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
    private LocalDateTime changesRequestedAt;

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

    @Builder.Default
    private boolean featured = false;

    /**
     * Indicates if this is a free event (no ticket price).
     * Set to true when all ticket categories have price = 0 or when organizer marks it as free.
     */
    @Builder.Default
    @Indexed
    private boolean isFreeEvent = false;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @CreatedBy
    @Indexed
    private String createdBy;

    @LastModifiedBy
    @Indexed
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
    @Indexed
    private boolean isDeleted = false;

    /**
     * When the event was soft deleted.
     */
    private LocalDateTime deletedAt;

    /**
     * User ID who deleted the event.
     * Used for audit trail.
     */
    @Indexed
    private String deletedBy;

    /**
     * Reason for deletion (optional).
     */
    private String deletionReason;

    /**
     * Accessibility information for this event
     */
    private EventAccessibility accessibility;

    /**
     * Event Ticket Category
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventTicketCategory {

        @NotBlank(message = "Category code is required")
        private String code;

        @NotBlank(message = "Category name is required")
        private String name;

        private String description;

        @NotNull(message = "Price is required")
        @Positive(message = "Price must be positive")
        private BigDecimal price;

        @Positive(message = "Quantity must be positive")
        private int quantity;

        private int availableQuantity;

        @Builder.Default
        private boolean active = true;

        private List<String> benefits;

        @Builder.Default
        private boolean isEarlyBird = false;
        private LocalDateTime earlyBirdEndDate;
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

    public boolean isHappeningToday() {
        LocalDateTime now = LocalDateTime.now();
        return eventDateTime.toLocalDate().equals(now.toLocalDate());
    }

    public boolean isInThePast() {
        return eventDateTime.isBefore(LocalDateTime.now());
    }

    public boolean isInTheFuture() {
        return eventDateTime.isAfter(LocalDateTime.now());
    }
}
