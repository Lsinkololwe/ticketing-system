package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.EventEscrowAccount;

/**
 * Edge wrapper for EventEscrowAccount in cursor-based pagination.
 */
public record EscrowAccountEdge(
        String cursor,
        EventEscrowAccount node
) {
    public static EscrowAccountEdge of(EventEscrowAccount account) {
        return new EscrowAccountEdge(account.getId(), account);
    }
}
