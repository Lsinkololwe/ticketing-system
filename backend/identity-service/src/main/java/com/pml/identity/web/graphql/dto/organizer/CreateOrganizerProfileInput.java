package com.pml.identity.web.graphql.dto.organizer;

/**
 * Input for creating an organizer profile.
 */
public record CreateOrganizerProfileInput(
        String companyName,
        String companyDescription,
        String website,
        String businessPhone,
        String businessEmail,
        String businessAddress,
        String city,
        String province
) {}
