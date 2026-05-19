package com.pml.booking.web.graphql.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Input for creating a new bank account for organizer payouts.
 *
 * <p><b>Validation Rules:</b></p>
 * <ul>
 *   <li>organizerId: Required</li>
 *   <li>accountHolderName: Required, alphanumeric with spaces</li>
 *   <li>bankName: Required</li>
 *   <li>accountNumber: Required, numeric only</li>
 *   <li>currency: Required, 3-letter ISO code</li>
 * </ul>
 *
 * <p><b>Security:</b></p>
 * <ul>
 *   <li>Account number validated as numeric to prevent injection</li>
 *   <li>All string inputs length-limited to prevent overflow</li>
 *   <li>Service layer validates organizer ownership</li>
 * </ul>
 */
public record CreateBankAccountInput(
    @NotBlank(message = "Organizer ID is required")
    @Size(max = 50, message = "Organizer ID must not exceed 50 characters")
    String organizerId,

    @NotBlank(message = "Account holder name is required")
    @Size(max = 100, message = "Account holder name must not exceed 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\s\\.\\-']+$", message = "Account holder name contains invalid characters")
    String accountHolderName,

    @NotBlank(message = "Bank name is required")
    @Size(max = 100, message = "Bank name must not exceed 100 characters")
    String bankName,

    @Size(max = 20, message = "Bank code must not exceed 20 characters")
    String bankCode,

    @Size(max = 100, message = "Branch name must not exceed 100 characters")
    String branchName,

    @Size(max = 20, message = "Branch code must not exceed 20 characters")
    String branchCode,

    @NotBlank(message = "Account number is required")
    @Size(min = 5, max = 30, message = "Account number must be between 5 and 30 characters")
    @Pattern(regexp = "^[0-9]+$", message = "Account number must contain only digits")
    String accountNumber,

    @Size(max = 20, message = "Account type must not exceed 20 characters")
    String accountType,

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency code must be 3 characters")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid ISO 4217 code")
    String currency,

    @Size(max = 11, message = "SWIFT code must not exceed 11 characters")
    @Pattern(regexp = "^[A-Z0-9]{8,11}$", message = "Invalid SWIFT code format")
    String swiftCode,

    Boolean isDefault
) {}
