package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.RefundRequest;

import java.util.List;
import java.util.Map;

public record ApproveRefundRequestMutationResponse(
        boolean success,
        String message,
        RefundRequest data,
        List<String> errors,
        Map<String, Object> metadata
) {}
