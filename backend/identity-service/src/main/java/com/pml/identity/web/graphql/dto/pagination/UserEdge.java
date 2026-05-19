package com.pml.identity.web.graphql.dto.pagination;

import com.pml.identity.domain.model.User;

/**
 * Edge wrapper for User in cursor-based pagination.
 *
 * Schema definition:
 * type UserEdge {
 *     cursor: String!
 *     node: User!
 * }
 */
public record UserEdge(
        String cursor,
        User node
) {
    public static UserEdge of(User user) {
        return new UserEdge(user.getId(), user);
    }
}
