package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.PayoutRequest;

import java.util.List;
import java.util.Map;

public record ApprovePayoutRequestMutationResponse(
    boolean success,
    String message,
    PayoutRequest data,
    List<String> errors,
    Map<String, Object> metadata
) {}
