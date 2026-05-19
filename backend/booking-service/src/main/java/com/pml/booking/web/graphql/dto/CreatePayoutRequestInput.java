package com.pml.booking.web.graphql.dto;

import com.pml.shared.constants.PayoutMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Input for creating a new payout request.
 *
 * <p><b>Validation Rules:</b></p>
 * <ul>
 *   <li>organizerId: Required, must be valid organizer ID</li>
 *   <li>bankAccountId: Required, must be verified bank account</li>
 *   <li>requestedAmount: Required, must be positive</li>
 *   <li>currency: Required, default ZMW</li>
 * </ul>
 *
 * <p><b>Security:</b></p>
 * <ul>
 *   <li>All string inputs are validated for length to prevent overflow</li>
 *   <li>Amount validation prevents negative withdrawals</li>
 *   <li>Service layer verifies organizer owns the escrow account</li>
 * </ul>
 */
public record CreatePayoutRequestInput(
    @NotBlank(message = "Organizer ID is required")
    @Size(max = 50, message = "Organizer ID must not exceed 50 characters")
    String organizerId,

    @Size(max = 50, message = "Event ID must not exceed 50 characters")
    String eventId,

    @Size(max = 50, message = "Escrow account ID must not exceed 50 characters")
    String escrowAccountId,

    @NotBlank(message = "Bank account ID is required")
    @Size(max = 50, message = "Bank account ID must not exceed 50 characters")
    String bankAccountId,

    @NotNull(message = "Requested amount is required")
    @Positive(message = "Requested amount must be positive")
    BigDecimal requestedAmount,

    @NotBlank(message = "Currency is required")
    @Size(max = 3, message = "Currency code must be 3 characters")
    String currency,

    PayoutMethod payoutMethod,

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    String notes,

    Map<String, Object> metadata
) {}
