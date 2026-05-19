package com.pml.booking.web.graphql.dto;

import java.math.BigDecimal;

public record RefundStatusSummary(
        String status,
        int count,
        BigDecimal totalAmount,
        double percentage
) {}
