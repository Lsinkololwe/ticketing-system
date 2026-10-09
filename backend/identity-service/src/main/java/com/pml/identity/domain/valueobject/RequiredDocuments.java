package com.pml.identity.domain.valueobject;

import com.pml.identity.domain.enums.BusinessType;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which verification documents a business type must supply.
 *
 * <h2>Why the set varies</h2>
 * A sole proprietor does not have a certificate of incorporation, and asking
 * for one is how an application is abandoned. The
 * required set is a function of {@link BusinessType} and nothing else, declared
 * once here and consulted by {@code submitForReview}.
 *
 * <p>This is the server-side authority. The wizard keeps a mirror of this table
 * so it can gate its own Continue button without a round-trip, but a submit is
 * only ever accepted or refused on the strength of {@link #missingFor}.</p>
 *
 * <h2>Document type vocabulary</h2>
 * {@code VerificationDocument.documentType} is a free-form {@code String} on the
 * wire rather than an enum, so the constants below are the agreed vocabulary
 * rather than a compiler-enforced one. They match the frontend's
 * {@code lib/onboarding/documents.ts} exactly.
 */
public final class RequiredDocuments {

    // ─────────────────────────────────────────────────────────────────────
    // Document type constants
    // ─────────────────────────────────────────────────────────────────────

    public static final String NATIONAL_ID = "NATIONAL_ID";
    public static final String TAX_CERTIFICATE = "TAX_CERTIFICATE";
    public static final String CERTIFICATE_OF_INCORPORATION = "CERTIFICATE_OF_INCORPORATION";
    public static final String PARTNERSHIP_AGREEMENT = "PARTNERSHIP_AGREEMENT";
    public static final String NGO_REGISTRATION = "NGO_REGISTRATION";
    public static final String AUTHORISATION_LETTER = "AUTHORISATION_LETTER";

    /**
     * Optional everywhere. These are what a reviewer asks for via
     * {@code requestOrganizationChanges} when something does not add up — never
     * part of the up-front required set.
     */
    public static final String PROOF_OF_ADDRESS = "PROOF_OF_ADDRESS";
    public static final String BANK_STATEMENT = "BANK_STATEMENT";

    // ─────────────────────────────────────────────────────────────────────
    // The table
    // ─────────────────────────────────────────────────────────────────────

    private static final Map<BusinessType, List<String>> REQUIRED = new EnumMap<>(BusinessType.class);

    static {
        // The wizard and reviewers call this SOLE_PROPRIETOR; the enum spells it
        // SOLE_PROPRIETORSHIP. Same thing, same requirements.
        REQUIRED.put(BusinessType.SOLE_PROPRIETORSHIP, List.of(NATIONAL_ID, TAX_CERTIFICATE));
        REQUIRED.put(BusinessType.INDIVIDUAL, List.of(NATIONAL_ID, TAX_CERTIFICATE));
        REQUIRED.put(BusinessType.PARTNERSHIP,
                List.of(NATIONAL_ID, TAX_CERTIFICATE, PARTNERSHIP_AGREEMENT));
        REQUIRED.put(BusinessType.LIMITED_COMPANY,
                List.of(NATIONAL_ID, TAX_CERTIFICATE, CERTIFICATE_OF_INCORPORATION));
        REQUIRED.put(BusinessType.NGO,
                List.of(NATIONAL_ID, TAX_CERTIFICATE, NGO_REGISTRATION));
        REQUIRED.put(BusinessType.GOVERNMENT,
                List.of(NATIONAL_ID, AUTHORISATION_LETTER));
    }

    private RequiredDocuments() {
    }

    // ─────────────────────────────────────────────────────────────────────
    // API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * The documents this business type must supply.
     *
     * @param businessType the applicant's declared business type; may be null
     * @return the required document types, never null. An unknown or null type
     *         yields an empty list — refusing a submit for documents we cannot
     *         justify asking for is worse than letting the missing-businessType
     *         check report the real problem.
     */
    public static List<String> forBusinessType(BusinessType businessType) {
        if (businessType == null) {
            return List.of();
        }
        return REQUIRED.getOrDefault(businessType, List.of());
    }

    /**
     * Which required documents are still outstanding.
     *
     * <p>A document in {@code REJECTED} does not satisfy its requirement — the
     * caller is expected to have filtered those out before calling, or
     * to pass only the types of non-rejected documents.</p>
     *
     * @param businessType the applicant's declared business type
     * @param suppliedTypes document types the organization has on file, excluding rejected ones
     * @return the missing types in declaration order, empty when the set is satisfied
     */
    public static List<String> missingFor(BusinessType businessType, Collection<String> suppliedTypes) {
        Set<String> supplied = suppliedTypes == null
                ? Set.of()
                : suppliedTypes.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());

        return forBusinessType(businessType).stream()
                .filter(required -> !supplied.contains(required))
                .toList();
    }
}
