package com.pml.identity.web.graphql.dto.organization;

import com.pml.identity.domain.enums.BusinessType;

/**
 * The organization profile an owner or admin may edit ({@code organization:edit}). Every field is
 * optional: only the ones present are written. The KYB identity fields ({@code businessType},
 * {@code taxId}, {@code businessRegistrationNumber}) are accepted only while the application can
 * still be edited; see {@code OrganizationRules#kybFieldsEditable}.
 */
public record UpdateOrganizationInput(
        String name,
        String description,
        String logoUrl,
        String bannerUrl,
        String tagline,
        String website,
        OrganizationApplicationInput.SocialLinksInput socialLinks,
        BusinessType businessType,
        String taxId,
        String businessRegistrationNumber,
        Integer yearEstablished,
        String businessPhone,
        String businessEmail,
        BusinessAddressInput businessAddress
) {
    public record BusinessAddressInput(
            String addressLine1,
            String addressLine2,
            String city,
            String province,
            String postalCode,
            String country,
            String countryCode
    ) {}
}
