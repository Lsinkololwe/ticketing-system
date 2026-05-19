package com.pml.identity.web.graphql.dto.pagination;

import java.util.List;

/**
 * Cursor-based pagination connection for TeamInvitations.
 * Follows Relay Connection Specification.
 *
 * Schema definition:
 * type TeamInvitationConnection {
 *     edges: [TeamInvitationEdge!]!
 *     pageInfo: PageInfo!
 *     totalCount: Int
 * }
 */
public record TeamInvitationConnection(
        List<TeamInvitationEdge> edges,
        PageInfo pageInfo,
        Integer totalCount
) {
    public static TeamInvitationConnection empty() {
        return new TeamInvitationConnection(List.of(), PageInfo.empty(), 0);
    }
}
