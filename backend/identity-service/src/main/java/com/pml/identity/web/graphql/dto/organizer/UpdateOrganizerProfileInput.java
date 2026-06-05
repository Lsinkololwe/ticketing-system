package com.pml.identity.web.graphql.dto.organizer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Input for updating an organizer profile.
 *
 * OWASP Security Considerations:
 * - All fields validated with appropriate constraints
 * - String lengths limited to prevent DoS
 * - Format validation for email, phone, URL
 * - Pattern validation for registration numbers
 *
 * @see <a href="https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html">OWASP Input Validation</a>
 */
public record UpdateOrganizerProfileInput(
        // Basic Information
        @Size(min = 2, max = 200, message = "Company name must be between 2 and 200 characters")
        String companyName,

        @Size(max = 200, message = "Tagline must not exceed 200 characters")
        String tagline,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String companyDescription,

        @Size(max = 500, message = "Logo URL must not exceed 500 characters")
        @Pattern(regexp = "^(https?://.*)?$", message = "Logo URL must be a valid HTTP/HTTPS URL")
        String logoUrl,

        @Size(max = 500, message = "Banner URL must not exceed 500 characters")
        @Pattern(regexp = "^(https?://.*)?$", message = "Banner URL must be a valid HTTP/HTTPS URL")
        String bannerUrl,

        @Size(max = 500, message = "Website must not exceed 500 characters")
        @Pattern(regexp = "^(https?://.*)?$", message = "Website must be a valid HTTP/HTTPS URL")
        String website,

        // Business Registration
        @Size(max = 50, message = "Tax ID must not exceed 50 characters")
        @Pattern(regexp = "^[A-Za-z0-9-]*$", message = "Tax ID must contain only letters, numbers, and hyphens")
        String taxId,

        @Size(max = 50, message = "Business registration number must not exceed 50 characters")
        @Pattern(regexp = "^[A-Za-z0-9-/]*$", message = "Registration number must contain only letters, numbers, hyphens, and slashes")
        String businessRegistrationNumber,

        @Size(max = 50, message = "Business type must not exceed 50 characters")
        @Pattern(regexp = "^(SOLE_PROPRIETORSHIP|PARTNERSHIP|LIMITED_COMPANY|NGO|GOVERNMENT|INDIVIDUAL)?$",
                 message = "Business type must be one of: SOLE_PROPRIETORSHIP, PARTNERSHIP, LIMITED_COMPANY, NGO, GOVERNMENT, INDIVIDUAL")
        String businessType,

        // Contact Information
        @Size(max = 30, message = "Phone number must not exceed 30 characters")
        @Pattern(regexp = "^(\\+?[0-9\\s-]{7,20})?$", message = "Phone must be a valid phone number")
        String businessPhone,

        @Size(max = 254, message = "Email must not exceed 254 characters")
        @Email(message = "Must be a valid email address")
        String businessEmail,

        // Address Fields
        @Size(max = 500, message = "Address must not exceed 500 characters")
        String businessAddress,

        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        @Size(max = 100, message = "Province must not exceed 100 characters")
        String province,

        @Size(max = 100, message = "Country must not exceed 100 characters")
        String country,

        @Size(max = 20, message = "Postal code must not exceed 20 characters")
        @Pattern(regexp = "^[A-Za-z0-9- ]*$", message = "Postal code must contain only letters, numbers, hyphens, and spaces")
        String postalCode
) {}
