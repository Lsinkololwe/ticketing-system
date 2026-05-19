package com.pml.identity.web.graphql.dto.organization;

/**
 * Input for updating an organization.
 */
public record UpdateOrganizationInput(
        String name,
        String description,
        String logoUrl,
        String bannerUrl
) {}
