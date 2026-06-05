package com.pml.identity.web.graphql.dto.organization;

import com.pml.identity.domain.enums.OrganizationType;
import com.pml.identity.domain.valueobject.SocialLinks;

/**
 * Input for creating or updating an organization application.
 *
 * Business details required for application - NO registration/tax details.
 * Banking details are added later when ready for payouts.
 */
public record OrganizationApplicationInput(
    /**
     * Organization/business name (required)
     */
    String name,

    /**
     * Description of the organization
     */
    String description,

    /**
     * Short tagline
     */
    String tagline,

    /**
     * URL to organization logo
     */
    String logoUrl,

    /**
     * URL to organization banner image
     */
    String bannerUrl,

    /**
     * Company website URL
     */
    String website,

    /**
     * Organization type: INDIVIDUAL or BUSINESS
     */
    OrganizationType type,

    /**
     * Business contact phone number
     */
    String businessPhone,

    /**
     * Business contact email
     */
    String businessEmail,

    /**
     * City
     */
    String city,

    /**
     * Province/State
     */
    String province,

    /**
     * Country (defaults to Zambia)
     */
    String country,

    /**
     * Social media links
     */
    SocialLinksInput socialLinks
) {
    /**
     * Input for social links
     */
    public record SocialLinksInput(
        String facebook,
        String instagram,
        String twitter,
        String linkedin,
        String youtube,
        String tiktok
    ) {}
}
