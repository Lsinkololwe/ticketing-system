package com.pml.booking.web.graphql.dto;

import java.math.BigDecimal;

public record RefundTypeSummary(
        String requestType,
        int count,
        BigDecimal totalAmount,
        double percentage
) {}
