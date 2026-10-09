package com.pml.identity.web.graphql.dto.organization;

import com.pml.identity.domain.enums.BusinessType;
import com.pml.identity.domain.enums.OrganizationType;
import lombok.Builder;

/**
 * Input for creating or updating an organization application.
 *
 * Business details required for application. Banking details are added later
 * when ready for payouts.
 *
 * <h2>Why registration and tax details are collected here</h2>
 * This record carries {@code businessType}, {@code taxId} and
 * {@code businessRegistrationNumber} because {@code businessType} determines which
 * verification documents the applicant must supply, so it has to be known before
 * the documents step can render at all, and it is what
 * {@link com.pml.identity.domain.valueobject.RequiredDocuments} keys off when
 * {@code submitForReview} checks completeness.
 *
 * <p>All three remain optional on the input. Which of them are actually
 * required is a function of business type, decided at submit time, not a blanket
 * rule applied while the applicant is still typing.</p>
 *
 * <h2>Construct this with the builder, not positionally</h2>
 * Thirteen parameters, of which nine are {@code String}, and two of those —
 * {@code businessPhone} and {@code businessEmail} — sit next to each other.
 * Swapping that adjacent pair compiles cleanly and type-checks perfectly; the
 * only thing that catches it is the {@code $jsonSchema} validator on the
 * collection, at write time, in whichever environment gets there first.
 *
 * <p>That is not hypothetical. Every fixture in
 * {@code OrganizationOnboardingWorkflowIntegrationTest} had exactly that pair
 * reversed, writing an email address into a phone field, and the suite failed
 * against the validator rather than against a compiler.
 *
 * <p>The builder does not make the mistake impossible, but it makes it visible
 * — {@code .businessPhone("contact@example.com")} reads wrong on the page.
 */
@Builder
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
     * Legal business type. Drives the required verification document set
     * — a sole proprietor is never asked for a certificate
     * of incorporation.
     */
    BusinessType businessType,

    /**
     * Tax identification number (TPIN in Zambia)
     */
    String taxId,

    /**
     * Business registration number (PACRA number in Zambia)
     */
    String businessRegistrationNumber,

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
