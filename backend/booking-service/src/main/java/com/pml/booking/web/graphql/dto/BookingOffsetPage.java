package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.Booking;

import java.util.List;

public record BookingOffsetPage(List<Booking> data, PaginationInfo pagination) {
}
