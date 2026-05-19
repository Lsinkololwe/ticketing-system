package com.pml.identity.web.graphql.dto.pagination;

import com.pml.identity.domain.model.OrganizationMember;

import java.util.List;

/**
 * Offset-based pagination result for OrganizationMembers.
 *
 * Schema definition:
 * type OrganizationMemberOffsetPage {
 *     content: [OrganizationMember!]!
 *     pageInfo: PageInfo!
 * }
 */
public record OrganizationMemberOffsetPage(
        List<OrganizationMember> content,
        PageInfo pageInfo
) {
    public static OrganizationMemberOffsetPage empty() {
        return new OrganizationMemberOffsetPage(List.of(), PageInfo.empty());
    }
}
