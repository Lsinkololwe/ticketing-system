package com.pml.identity.web.graphql.dto.pagination;

import com.pml.identity.domain.model.User;

import java.util.List;

/**
 * Offset-based pagination result for Users.
 *
 * Schema definition:
 * type UserOffsetPage {
 *     content: [User!]!
 *     pageInfo: PageInfo!
 * }
 */
public record UserOffsetPage(
        List<User> content,
        PageInfo pageInfo
) {
    public static UserOffsetPage empty() {
        return new UserOffsetPage(List.of(), PageInfo.empty());
    }
}
