package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.enums.PlatformAccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PlatformTransferInput(
        @NotNull(message = "Choose the account to move from") PlatformAccountType fromAccount,
        @NotNull(message = "Choose the account to move to") PlatformAccountType toAccount,
        @NotNull(message = "Amount is required") @Positive(message = "Amount must be positive") BigDecimal amount,
        @NotBlank(message = "A reason is required") @Size(max = 1000) String reason,
        @NotBlank(message = "An idempotency key is required") @Size(max = 100) String idempotencyKey
) {
}
