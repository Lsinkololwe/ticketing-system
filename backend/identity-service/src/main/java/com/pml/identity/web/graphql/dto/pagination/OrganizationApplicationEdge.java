package com.pml.identity.web.graphql.dto.pagination;

import com.pml.identity.domain.model.Organization;

/**
 * Edge wrapper for Organization Application in cursor-based pagination.
 *
 * Schema definition:
 * type OrganizationApplicationEdge {
 *     cursor: String!
 *     node: Organization!
 * }
 */
public record OrganizationApplicationEdge(
        String cursor,
        Organization node
) {
    public static OrganizationApplicationEdge of(Organization organization) {
        return new OrganizationApplicationEdge(organization.getId(), organization);
    }
}
