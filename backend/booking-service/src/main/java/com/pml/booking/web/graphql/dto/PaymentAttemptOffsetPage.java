package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.PaymentAttempt;

import java.util.List;

public record PaymentAttemptOffsetPage(List<PaymentAttempt> data, PaginationInfo pagination) {
}
