package com.pml.identity.web.graphql.dto.pagination;

import com.pml.identity.domain.model.TeamInvitation;

/**
 * Edge wrapper for TeamInvitation in cursor-based pagination.
 *
 * Schema definition:
 * type TeamInvitationEdge {
 *     cursor: String!
 *     node: TeamInvitation!
 * }
 */
public record TeamInvitationEdge(
        String cursor,
        TeamInvitation node
) {
    public static TeamInvitationEdge of(TeamInvitation invitation) {
        return new TeamInvitationEdge(invitation.getId(), invitation);
    }
}
