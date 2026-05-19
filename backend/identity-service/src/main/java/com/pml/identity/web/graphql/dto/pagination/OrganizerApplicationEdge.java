package com.pml.identity.web.graphql.dto.pagination;

import com.pml.identity.domain.model.OrganizerProfile;

/**
 * Edge wrapper for OrganizerProfile (application) in cursor-based pagination.
 *
 * Schema definition:
 * type OrganizerApplicationEdge {
 *     cursor: String!
 *     node: OrganizerProfile!
 * }
 */
public record OrganizerApplicationEdge(
        String cursor,
        OrganizerProfile node
) {
    public static OrganizerApplicationEdge of(OrganizerProfile profile) {
        return new OrganizerApplicationEdge(profile.getId(), profile);
    }
}
