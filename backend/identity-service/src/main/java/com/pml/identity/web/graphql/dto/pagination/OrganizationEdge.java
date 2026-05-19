package com.pml.identity.web.graphql.dto.pagination;

import com.pml.identity.domain.model.Organization;

/**
 * Edge wrapper for Organization in cursor-based pagination.
 *
 * Schema definition:
 * type OrganizationEdge {
 *     cursor: String!
 *     node: Organization!
 * }
 */
public record OrganizationEdge(
        String cursor,
        Organization node
) {
    public static OrganizationEdge of(Organization organization) {
        return new OrganizationEdge(organization.getId(), organization);
    }
}
