package com.pml.identity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code identity.limits.*}: abuse limits on code requests, and the countries we deliver to. */
@Data
@Component
@ConfigurationProperties(prefix = "identity.limits")
public class IdentityLimitsProperties {

    private int contactCodesPerDay = 10;
    private int ipCodesPerHour = 10;
    private int deviceDistinctContactsPerDay = 5;

    /** Codes per country per day; the {@code default} key applies to any country not listed. */
    private Map<String, Integer> countryCodesPerDay = new LinkedHashMap<>(Map.of("default", 2000));

    /** ISO-3166 alpha-2 codes whose numbers may receive a code. */
    private List<String> allowedCountries = List.of("ZM", "GB", "US", "ZA", "ZW", "MW", "TZ", "KE", "BW", "NA",
            "AE", "CA", "AU", "IE", "DE", "FR", "NL");

    public int countryLimit(String country) {
        Integer specific = country == null ? null : countryCodesPerDay.get(country.toUpperCase(java.util.Locale.ROOT));
        return specific != null ? specific : countryCodesPerDay.getOrDefault("default", 2000);
    }
}
