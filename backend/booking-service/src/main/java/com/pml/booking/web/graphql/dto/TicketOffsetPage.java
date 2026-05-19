package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.Ticket;

import java.util.List;

public record TicketOffsetPage(
        List<Ticket> data,
        PaginationInfo pagination
) {}
