package com.pml.identity.web.graphql.dto.pagination;

import java.util.List;

/**
 * Cursor-based pagination connection for EventAccessGrants.
 * Follows Relay Connection Specification.
 *
 * Schema definition:
 * type EventAccessGrantConnection {
 *     edges: [EventAccessGrantEdge!]!
 *     pageInfo: PageInfo!
 *     totalCount: Int
 * }
 */
public record EventAccessGrantConnection(
        List<EventAccessGrantEdge> edges,
        PageInfo pageInfo,
        Integer totalCount
) {
    public static EventAccessGrantConnection empty() {
        return new EventAccessGrantConnection(List.of(), PageInfo.empty(), 0);
    }
}
