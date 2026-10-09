package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.enums.PaymentAttemptStatus;
import com.pml.booking.domain.enums.PaymentAttemptType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PaymentAttemptFilterInput(
        @Size(max = 10) List<PaymentAttemptStatus> statuses,
        PaymentAttemptType attemptType,
        String provider,
        String eventId,
        String organizationId,
        String buyerId,
        String reviewStatus,
        // LOW, MEDIUM or HIGH
        String riskLevel,
        Instant createdAfter,
        Instant createdBefore,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        // A deposit id, attempt number or provider reference, exactly as shown
        @Size(max = 100) String reference,
        // Collections still short of fulfilment for at least this many minutes
        @Min(1) Integer stuckForMinutes
) {
}
