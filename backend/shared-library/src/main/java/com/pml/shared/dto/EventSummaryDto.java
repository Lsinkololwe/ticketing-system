package com.pml.shared.dto;

import com.pml.shared.constants.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String locationId;
    private String locationName;
    private String cityName;
    private BigDecimal minimumPrice;
    private BigDecimal maximumPrice;
    private Integer totalCapacity;
    private Integer ticketsSold;
    private List<TicketCategoryDto> ticketCategories;
    private String bannerImageUrl;
    private boolean featured;
    private boolean soldOut;

    /**
     * Check if the event is currently active (published and not past)
     */
    public boolean isActive() {
        return status == EventStatus.PUBLISHED &&
               (endDate == null || endDate.isAfter(LocalDateTime.now()));
    }

    /**
     * Check if the event has started
     */
    public boolean hasStarted() {
        return startDate != null && startDate.isBefore(LocalDateTime.now());
    }

    /**
     * Check if the event has ended
     */
    public boolean hasEnded() {
        return endDate != null && endDate.isBefore(LocalDateTime.now());
    }

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
        private String code;
        private String name;
        private BigDecimal price;
        private Integer capacity;
        private Integer sold;
        private boolean active;

        public Integer getAvailable() {
            if (capacity == null) {
                return null;
            }
            int soldCount = sold != null ? sold : 0;
            return Math.max(0, capacity - soldCount);
        }
    }
}
