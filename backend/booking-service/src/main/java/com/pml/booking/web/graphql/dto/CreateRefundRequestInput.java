package com.pml.booking.web.graphql.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Input for creating a new refund request.
 *
 * <p><b>Validation Rules:</b></p>
 * <ul>
 *   <li>ticketId: Required, must be valid ticket ID</li>
 *   <li>reason: Required, explains why refund is requested</li>
 * </ul>
 *
 * <p><b>Security:</b></p>
 * <ul>
 *   <li>All string inputs length-limited to prevent overflow</li>
 *   <li>Service layer validates ticket ownership and refund eligibility</li>
 *   <li>Duplicate refund requests are rejected</li>
 * </ul>
 */
public record CreateRefundRequestInput(
        @NotBlank(message = "Ticket ID is required")
        @Size(max = 50, message = "Ticket ID must not exceed 50 characters")
        String ticketId,

        @NotBlank(message = "Refund reason is required")
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        String reason,

        @Size(max = 1000, message = "Additional notes must not exceed 1000 characters")
        String additionalNotes,

        @Size(max = 50, message = "Requested by must not exceed 50 characters")
        String requestedBy,

        Map<String, Object> metadata
) {}
