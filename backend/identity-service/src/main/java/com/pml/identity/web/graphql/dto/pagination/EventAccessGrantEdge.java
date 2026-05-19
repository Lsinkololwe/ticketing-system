package com.pml.identity.web.graphql.dto.pagination;

import com.pml.identity.domain.model.EventAccessGrant;

/**
 * Edge wrapper for EventAccessGrant in cursor-based pagination.
 *
 * Schema definition:
 * type EventAccessGrantEdge {
 *     cursor: String!
 *     node: EventAccessGrant!
 * }
 */
public record EventAccessGrantEdge(
        String cursor,
        EventAccessGrant node
) {
    public static EventAccessGrantEdge of(EventAccessGrant grant) {
        return new EventAccessGrantEdge(grant.getId(), grant);
    }
}
