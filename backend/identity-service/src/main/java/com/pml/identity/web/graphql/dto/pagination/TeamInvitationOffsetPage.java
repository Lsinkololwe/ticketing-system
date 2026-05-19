package com.pml.identity.web.graphql.dto.pagination;

import com.pml.identity.domain.model.TeamInvitation;

import java.util.List;

/**
 * Offset-based pagination result for TeamInvitations.
 *
 * Schema definition:
 * type TeamInvitationOffsetPage {
 *     content: [TeamInvitation!]!
 *     pageInfo: PageInfo!
 * }
 */
public record TeamInvitationOffsetPage(
        List<TeamInvitation> content,
        PageInfo pageInfo
) {
    public static TeamInvitationOffsetPage empty() {
        return new TeamInvitationOffsetPage(List.of(), PageInfo.empty());
    }
}
