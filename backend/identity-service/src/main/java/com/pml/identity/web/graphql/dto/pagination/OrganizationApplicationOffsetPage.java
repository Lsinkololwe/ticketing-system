package com.pml.identity.web.graphql.dto.pagination;

import com.pml.identity.domain.model.Organization;

import java.util.List;

/**
 * Offset-based pagination result for Organization Applications (Approval Queue).
 *
 * Schema definition:
 * type OrganizationApplicationOffsetPage {
 *     content: [Organization!]!
 *     pageInfo: PageInfo!
 * }
 */
public record OrganizationApplicationOffsetPage(
        List<Organization> content,
        PageInfo pageInfo
) {
    public static OrganizationApplicationOffsetPage empty() {
        return new OrganizationApplicationOffsetPage(List.of(), PageInfo.empty());
    }
}
