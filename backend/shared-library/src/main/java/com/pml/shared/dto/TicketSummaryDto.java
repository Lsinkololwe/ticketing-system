package com.pml.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ticket Summary DTO
 *
 * Lightweight representation of a ticket for inter-service communication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketSummaryDto {

    private String id;
    private String ticketNumber;
    private String eventId;
    private String eventTitle;
    private String buyerId;
    private String buyerName;
    private String buyerEmail;
    private String ticketCategoryCode;
    private String ticketCategoryName;
    private BigDecimal price;
    private String currency;
    private BigDecimal serviceFee;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime purchaseDate;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private LocalDateTime validatedAt;
    private LocalDateTime usedAt;
    private String qrCode;

    /**
     * Check if the ticket is valid for entry
     */
    public boolean isValidForEntry() {
        return "VALIDATED".equals(status) || "CONFIRMED".equals(status) || "PURCHASED".equals(status);
    }

    /**
     * Check if the ticket has been used
     */
    public boolean isUsed() {
        return "USED".equals(status);
    }

    /**
     * Get total paid amount
     */
    public BigDecimal getTotalPaid() {
        BigDecimal priceAmount = price != null ? price : BigDecimal.ZERO;
        BigDecimal feeAmount = serviceFee != null ? serviceFee : BigDecimal.ZERO;
        return priceAmount.add(feeAmount);
    }
}
