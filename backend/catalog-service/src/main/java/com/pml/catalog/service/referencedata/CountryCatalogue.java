package com.pml.catalog.service.referencedata;

import com.google.i18n.phonenumbers.PhoneNumberUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;

/**
 * The countries the platform can take a phone number from, taken from a maintained standard rather
 * than typed out: the regions Google's libphonenumber supports, with their calling codes, named by the
 * JDK's CLDR data. When a libphonenumber release adds a region, the next seed picks it up with no
 * edit here.
 *
 * <p>Zambia leads, then its neighbours in the order the platform's own traffic suggests, then every
 * other region alphabetically. {@code displayOrder} is data, so an administrator can reorder it.
 */
public final class CountryCatalogue {

    /** Zambia first, then the SADC neighbours buyers most often travel from. */
    private static final List<String> LEADING = List.of("ZM", "ZW", "MW", "TZ", "CD", "BW", "ZA", "MZ", "NA", "AO");

    /** Regions libphonenumber supports that the JDK has no ISO 3166 alpha-3 for. */
    private static final Map<String, String> ISO3_EXTRA = Map.of("XK", "XKX", "AC", "ASC", "TA", "TAA");

    private CountryCatalogue() {
    }

    /** One country row, in the shape the seed upsert writes. */
    public static List<Map<String, Object>> rows() {
        PhoneNumberUtil phones = PhoneNumberUtil.getInstance();
        Set<String> regions = phones.getSupportedRegions();
        List<Map<String, Object>> rows = new ArrayList<>();
        regions.stream()
                .map(region -> Map.entry(region, name(region)))
                .sorted(Comparator.<Map.Entry<String, String>>comparingInt(e -> rank(e.getKey()))
                        .thenComparing(Map.Entry::getValue))
                .forEach(entry -> {
                    String region = entry.getKey();
                    String iso3 = iso3(region);
                    if (iso3 == null) {
                        return;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("type", "COUNTRY");
                    row.put("code", region);
                    row.put("name", entry.getValue());
                    row.put("displayOrder", rows.size());
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("dialCode", "+" + phones.getCountryCodeForRegion(region));
                    metadata.put("iso3", iso3);
                    row.put("metadata", metadata);
                    rows.add(row);
                });
        return rows;
    }

    private static int rank(String region) {
        int i = LEADING.indexOf(region);
        return i < 0 ? LEADING.size() : i;
    }

    private static String name(String region) {
        String name = Locale.of("", region).getDisplayCountry(Locale.ENGLISH);
        return name == null || name.isBlank() ? region : name;
    }

    private static String iso3(String region) {
        try {
            String iso3 = Locale.of("", region).getISO3Country();
            return iso3 == null || iso3.isBlank() ? ISO3_EXTRA.get(region) : iso3;
        } catch (MissingResourceException unknown) {
            return ISO3_EXTRA.get(region);
        }
    }
}
