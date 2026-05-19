package com.pml.identity.web.graphql.dto.pagination;

import java.util.List;

/**
 * Cursor-based pagination connection for Organizations.
 * Follows Relay Connection Specification.
 *
 * Schema definition:
 * type OrganizationConnection {
 *     edges: [OrganizationEdge!]!
 *     pageInfo: PageInfo!
 *     totalCount: Int
 * }
 */
public record OrganizationConnection(
        List<OrganizationEdge> edges,
        PageInfo pageInfo,
        Integer totalCount
) {
    public static OrganizationConnection empty() {
        return new OrganizationConnection(List.of(), PageInfo.empty(), 0);
    }
}
