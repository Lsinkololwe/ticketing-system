package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.EventEscrowAccount;

import java.util.List;

/**
 * Paginated response for escrow accounts.
 * Used for admin dashboard table with pagination.
 */
public record EscrowAccountOffsetPage(
        List<EventEscrowAccount> data,
        PaginationInfo pagination
) {}
