package com.pml.identity.web.graphql.dto.pagination;

import java.util.List;

/**
 * Cursor-based pagination connection for OrganizerProfile (applications).
 * Follows Relay Connection Specification.
 *
 * Schema definition:
 * type OrganizerApplicationConnection {
 *     edges: [OrganizerApplicationEdge!]!
 *     pageInfo: PageInfo!
 *     totalCount: Int
 * }
 */
public record OrganizerApplicationConnection(
        List<OrganizerApplicationEdge> edges,
        PageInfo pageInfo,
        Integer totalCount
) {
    public static OrganizerApplicationConnection empty() {
        return new OrganizerApplicationConnection(List.of(), PageInfo.empty(), 0);
    }
}
