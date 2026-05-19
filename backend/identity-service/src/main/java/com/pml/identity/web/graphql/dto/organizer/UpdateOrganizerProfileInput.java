package com.pml.identity.web.graphql.dto.organizer;

/**
 * Input for updating an organizer profile.
 */
public record UpdateOrganizerProfileInput(
        String companyName,
        String companyDescription,
        String website,
        SocialLinksInput socialLinks,
        String taxId,
        String businessRegistrationNumber,
        String businessPhone,
        String businessEmail,
        String businessAddress,
        String city,
        String province
) {}
