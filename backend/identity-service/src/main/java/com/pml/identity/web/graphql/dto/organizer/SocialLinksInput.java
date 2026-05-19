package com.pml.identity.web.graphql.dto.organizer;

/**
 * Input for social media links.
 */
public record SocialLinksInput(
        String facebook,
        String twitter,
        String instagram,
        String linkedin,
        String youtube
) {}
