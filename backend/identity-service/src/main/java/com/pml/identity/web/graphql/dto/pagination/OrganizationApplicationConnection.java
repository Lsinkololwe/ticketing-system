package com.pml.identity.web.graphql.dto.pagination;

import java.util.List;

/**
 * Cursor-based pagination connection for Organization Applications (Approval Queue).
 * Follows Relay Connection Specification.
 *
 * Schema definition:
 * type OrganizationApplicationConnection {
 *     edges: [OrganizationApplicationEdge!]!
 *     pageInfo: PageInfo!
 *     totalCount: Int
 * }
 */
public record OrganizationApplicationConnection(
        List<OrganizationApplicationEdge> edges,
        PageInfo pageInfo,
        Integer totalCount
) {
    public static OrganizationApplicationConnection empty() {
        return new OrganizationApplicationConnection(List.of(), PageInfo.empty(), 0);
    }
}
