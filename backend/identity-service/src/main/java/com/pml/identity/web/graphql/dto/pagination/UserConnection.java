package com.pml.identity.web.graphql.dto.pagination;

import java.util.List;

/**
 * Cursor-based pagination connection for Users.
 * Follows Relay Connection Specification.
 *
 * Schema definition:
 * type UserConnection {
 *     edges: [UserEdge!]!
 *     pageInfo: PageInfo!
 *     totalCount: Int
 * }
 */
public record UserConnection(
        List<UserEdge> edges,
        PageInfo pageInfo,
        Integer totalCount
) {
    public static UserConnection empty() {
        return new UserConnection(List.of(), PageInfo.empty(), 0);
    }
}
