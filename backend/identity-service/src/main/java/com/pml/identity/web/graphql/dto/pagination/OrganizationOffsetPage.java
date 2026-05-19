package com.pml.identity.web.graphql.dto.pagination;

import com.pml.identity.domain.model.Organization;

import java.util.List;

/**
 * Offset-based pagination result for Organizations.
 *
 * Schema definition:
 * type OrganizationOffsetPage {
 *     content: [Organization!]!
 *     pageInfo: PageInfo!
 * }
 */
public record OrganizationOffsetPage(
        List<Organization> content,
        PageInfo pageInfo
) {
    public static OrganizationOffsetPage empty() {
        return new OrganizationOffsetPage(List.of(), PageInfo.empty());
    }
}
