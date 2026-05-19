package com.pml.identity.web.graphql.dto.pagination;

import com.pml.identity.domain.model.OrganizerProfile;

import java.util.List;

/**
 * Offset-based pagination result for OrganizerProfile (applications).
 *
 * Schema definition:
 * type OrganizerApplicationOffsetPage {
 *     content: [OrganizerProfile!]!
 *     pageInfo: PageInfo!
 * }
 */
public record OrganizerApplicationOffsetPage(
        List<OrganizerProfile> content,
        PageInfo pageInfo
) {
    public static OrganizerApplicationOffsetPage empty() {
        return new OrganizerApplicationOffsetPage(List.of(), PageInfo.empty());
    }
}
