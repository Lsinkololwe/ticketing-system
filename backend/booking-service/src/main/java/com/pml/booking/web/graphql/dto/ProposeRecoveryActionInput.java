package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.enums.RecoveryAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProposeRecoveryActionInput(
        @NotNull(message = "Action is required") RecoveryAction action,
        @NotEmpty(message = "Name what the action applies to") @Size(max = 50, message = "At most 50 subjects") List<String> subjectIds,
        @Positive(message = "Amount must be positive") BigDecimal amount,
        Map<String, String> parameters,
        @NotBlank(message = "A reason is required") @Size(max = 1000) String reason
) {
}
