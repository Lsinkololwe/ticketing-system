package com.pml.identity.web.graphql.dto.pagination;

import java.util.List;

/**
 * Cursor-based pagination connection for OrganizationMembers.
 * Follows Relay Connection Specification.
 *
 * Schema definition:
 * type OrganizationMemberConnection {
 *     edges: [OrganizationMemberEdge!]!
 *     pageInfo: PageInfo!
 *     totalCount: Int
 * }
 */
public record OrganizationMemberConnection(
        List<OrganizationMemberEdge> edges,
        PageInfo pageInfo,
        Integer totalCount
) {
    public static OrganizationMemberConnection empty() {
        return new OrganizationMemberConnection(List.of(), PageInfo.empty(), 0);
    }
}
