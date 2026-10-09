package com.pml.shared.dto;

import com.pml.shared.constants.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Event Summary DTO
 *
 * Lightweight representation of an event for inter-service communication.
 * Used when services need basic event information without full event details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventSummaryDto {

    private String id;
    private String title;
    private String organizerId;
    private String organizerName;

    /**
     * Organization ID that owns this event.
     * Critical for multi-tenant authorization and financial reporting.
     * Propagated to all booking-service entities for consistent ownership tracking.
     */
    private String organizationId;

    private EventStatus status;
    private Instant startDate;
    private Instant endDate;
    private String locationId;
    private String locationName;
    private String cityName;
    private Integer totalCapacity;
    private Integer ticketsSold;
    private List<TicketCategoryDto> ticketCategories;
    private String bannerImageUrl;
    private boolean featured;
    private boolean soldOut;

    /** The organizer's own cap on tickets per order, below the platform's; null when the organizer set none. */
    private Integer maxTicketsPerOrder;

    /** Whether the buyer names each ticket holder at checkout. */
    private boolean collectHolderNames;

    /** The one extra question the organizer puts to the buyer at checkout, or null. */
    private String extraQuestion;

    /*
     * This DTO carries no time-dependent bean getters such as "isActive". It is a
     * cross-service payload — catalog's InternalEventController sends it, booking's
     * CatalogServiceClient reads it — and any getter Jackson serialised would be decided by the
     * sender's clock at serialisation time, with nothing in the payload saying when, so a consumer
     * caching the response would hold a value that silently goes stale.
     *
     * Callers evaluate "active", "started" or "ended" at the point of use, against their own
     * injected Clock, from startDate/endDate/status, all of which are on the wire.
     */

    /**
     * Get remaining capacity
     */
    public Integer getRemainingCapacity() {
        if (totalCapacity == null) {
            return null;
        }
        int sold = ticketsSold != null ? ticketsSold : 0;
        return Math.max(0, totalCapacity - sold);
    }

    /**
     * Ticket Category DTO for event summary
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketCategoryDto {
        /** Catalog's tier id: what a buyer selects, and what booking reserves against. */
        private String id;
        private String code;
        private String name;
        private BigDecimal price;
        private Integer capacity;
        private Integer sold;
        private boolean active;

        /** A hidden tier is reserved only with its access code; booking asks catalog to verify it. */
        private boolean hidden;

        /** Discounted unit price while the early-bird window is open; null when the tier has none. */
        private BigDecimal earlyBirdPrice;

        /** Exclusive end of the early-bird window; null when the tier has none. */
        private Instant earlyBirdEndsAt;

        /**
         * The unit price a buyer pays at {@code now}: the early-bird price strictly before
         * {@link #earlyBirdEndsAt}, the full price from that instant on. The boundary matches
         * catalog's {@code TicketTier.getCurrentPrice}, so the advertised and charged prices agree.
         *
         * <p>Not a bean getter, so it never reaches the wire.
         *
         * @param now the instant to price at, from the caller's injected clock
         * @return the unit price in force at {@code now}
         */
        public BigDecimal priceAt(Instant now) {
            if (earlyBirdPrice != null && earlyBirdEndsAt != null && now.isBefore(earlyBirdEndsAt)) {
                return earlyBirdPrice;
            }
            return price;
        }

        public Integer getAvailable() {
            if (capacity == null) {
                return null;
            }
            int soldCount = sold != null ? sold : 0;
            return Math.max(0, capacity - soldCount);
        }
    }
}
