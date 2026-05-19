package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.RefundRequest;

import java.util.List;

public record RefundRequestOffsetPage(
        List<RefundRequest> data,
        PaginationInfo pagination
) {}
