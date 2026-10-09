package com.pml.catalog.service;

import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ReferenceMetadataValidator — the service-layer contract for the polymorphic {@code metadata} blob.
 *
 * <p>The single collection buys us one CRUD stack for every list; the price is a free-form metadata
 * map. This registry pays that price back: it declares, per {@link ReferenceType}, which metadata
 * keys are required, so a malformed row (e.g. a bank without a SWIFT, an operator without MSISDN
 * prefixes) is rejected before it is ever saved.</p>
 *
 * <p>Adding a new required key for a type is a one-line change here — deliberately co-located with
 * {@link ReferenceType} so the contract stays visible.</p>
 */
@Component
public class ReferenceMetadataValidator {

    /**
     * type → required metadata keys. Types omitted here have no required metadata.
     */
    private static final Map<ReferenceType, List<String>> REQUIRED_KEYS = Map.ofEntries(
            Map.entry(ReferenceType.COUNTRY, List.of("dialCode", "iso3")),
            // A district town has a place on the map once someone surveys it; the venue
            // resolver already tolerates a city without coordinates, so they are not required.
            Map.entry(ReferenceType.CURRENCY, List.of("symbol", "decimals")),
            Map.entry(ReferenceType.MOBILE_MONEY_OPERATOR, List.of("providerCode", "msisdnPrefixes")),
            Map.entry(ReferenceType.BANK, List.of("swift")),
            Map.entry(ReferenceType.AGE_RESTRICTION, List.of("minAge")),
            Map.entry(ReferenceType.KYB_DOCUMENT_TYPE, List.of("appliesTo")),
            Map.entry(ReferenceType.TAX_RATE, List.of("rate", "countryCode")),
            Map.entry(ReferenceType.BUSINESS_TYPE, List.of("requiredDocuments")),
            Map.entry(ReferenceType.ORGANIZATION_ROLE, List.of("invitable")),
            Map.entry(ReferenceType.EVENT_ROLE, List.of("invitable")),
            Map.entry(ReferenceType.NOTIFICATION_CHANNEL, List.of("preferenceKey")),
            Map.entry(ReferenceType.NOTIFICATION_CATEGORY, List.of("preferenceKey", "locked")),
            Map.entry(ReferenceType.REPORT_PERIOD, List.of("days", "bucket"))
    );

    /**
     * Keys that would describe how a row looks. Reference data says what a value is; how a
     * category or an operator is drawn belongs to each application's design, so no row may carry
     * a colour, an icon or an image.
     */
    public static final Set<String> PRESENTATION_KEYS = Set.of(
            "color", "colour", "icon", "iconUrl", "iconName", "emoji", "image", "imageUrl", "style", "theme");

    /**
     * Validate the metadata payload for a given type.
     *
     * @throws ValidationRefusal if the metadata carries a presentation key
     * @throws IllegalArgumentException if a required key is missing or blank.
     */
    public void validate(ReferenceType type, Map<String, Object> metadata) {
        if (metadata != null) {
            List<FieldViolation> presentation = metadata.keySet().stream()
                    .filter(PRESENTATION_KEYS::contains)
                    .sorted()
                    .map(key -> new FieldViolation("input.metadata." + key, "NotPresentation"))
                    .toList();
            if (!presentation.isEmpty()) {
                throw new ValidationRefusal(presentation);
            }
        }
        List<String> required = REQUIRED_KEYS.get(type);
        if (required == null || required.isEmpty()) {
            return;
        }
        Map<String, Object> md = metadata != null ? metadata : Map.of();
        for (String key : required) {
            Object value = md.get(key);
            if (value == null
                    || (value instanceof String s && s.isBlank())
                    || (value instanceof List<?> l && l.isEmpty())) {
                throw new IllegalArgumentException(
                        "Reference type " + type + " requires metadata key '" + key + "'");
            }
        }
    }

    /**
     * Expose the required-key contract (drives dynamic admin form rendering).
     */
    public List<String> requiredKeys(ReferenceType type) {
        return REQUIRED_KEYS.getOrDefault(type, List.of());
    }
}
