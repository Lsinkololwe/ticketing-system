package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.enums.ChargebackFundSource;
import com.pml.booking.service.ChargebackRecoveryOps;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateChargebackRecoveryInput(
        @NotNull(message = "Action is required") ChargebackRecoveryOps.Action action,
        @Positive(message = "Amount must be positive") BigDecimal amount,
        ChargebackFundSource fundSource,
        @Size(max = 200) String reference
) {
}
