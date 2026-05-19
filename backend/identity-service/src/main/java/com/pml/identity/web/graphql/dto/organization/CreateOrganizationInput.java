package com.pml.identity.web.graphql.dto.organization;

/**
 * Input for creating an organization manually (admin only).
 */
public record CreateOrganizationInput(
        String name,
        String slug,
        String description,
        String logoUrl,
        String bannerUrl,
        String ownerId
) {}
