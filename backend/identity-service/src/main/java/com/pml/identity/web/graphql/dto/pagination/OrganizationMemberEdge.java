package com.pml.identity.web.graphql.dto.pagination;

import com.pml.identity.domain.model.OrganizationMember;

/**
 * Edge wrapper for OrganizationMember in cursor-based pagination.
 *
 * Schema definition:
 * type OrganizationMemberEdge {
 *     cursor: String!
 *     node: OrganizationMember!
 * }
 */
public record OrganizationMemberEdge(
        String cursor,
        OrganizationMember node
) {
    public static OrganizationMemberEdge of(OrganizationMember member) {
        return new OrganizationMemberEdge(member.getId(), member);
    }
}
