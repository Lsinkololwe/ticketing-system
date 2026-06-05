package com.pml.identity.domain.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Business address for an organization.
 * Embedded document within Organization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessAddress {

    /**
     * Street address line 1
     */
    private String addressLine1;

    /**
     * Street address line 2 (optional)
     */
    private String addressLine2;

    /**
     * City
     */
    private String city;

    /**
     * Province/State (e.g., "Lusaka", "Copperbelt", "Southern")
     */
    private String province;

    /**
     * Postal code
     */
    private String postalCode;

    /**
     * Country (defaults to Zambia)
     */
    @Builder.Default
    private String country = "Zambia";

    /**
     * Country code (ISO 3166-1 alpha-2)
     */
    @Builder.Default
    private String countryCode = "ZM";

    /**
     * Get formatted address for display
     */
    public String getFormattedAddress() {
        StringBuilder sb = new StringBuilder();
        if (addressLine1 != null) sb.append(addressLine1);
        if (addressLine2 != null) sb.append(", ").append(addressLine2);
        if (city != null) sb.append(", ").append(city);
        if (province != null) sb.append(", ").append(province);
        if (postalCode != null) sb.append(" ").append(postalCode);
        if (country != null) sb.append(", ").append(country);
        return sb.toString();
    }
}
