package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.PayoutRequest;

import java.util.List;

public record PayoutRequestOffsetPage(
        List<PayoutRequest> data,
        PaginationInfo pagination
) {}
