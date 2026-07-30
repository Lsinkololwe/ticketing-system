package com.pml.catalog.domain.enums;

/**
 * ReferenceType — the discriminator for the polymorphic {@code reference_data} collection.
 *
 * <p>This enum IS the contract: adding a new reference-data <em>type</em> is a deliberate,
 * compiled change (it may require a metadata schema, seed rows, and a consumer). Adding new
 * <em>rows</em> within an existing type is runtime admin CRUD and needs no code change.</p>
 *
 * <p>Each type carries a human {@code label} and a {@code group} so the admin UI can render a
 * grouped type picker directly off {@code referenceTypes}, without hardcoding a list on the client.</p>
 */
public enum ReferenceType {

    // ── Geography & Localization ──────────────────────────────────────────────
    COUNTRY("Countries", ReferenceGroup.GEOGRAPHY),
    CURRENCY("Currencies", ReferenceGroup.GEOGRAPHY),
    LANGUAGE("Languages", ReferenceGroup.GEOGRAPHY),
    TIMEZONE("Timezones", ReferenceGroup.GEOGRAPHY),
    PROVINCE("Provinces", ReferenceGroup.GEOGRAPHY),

    // ── Payments ──────────────────────────────────────────────────────────────
    MOBILE_MONEY_OPERATOR("Mobile Money Operators", ReferenceGroup.PAYMENTS),
    BANK("Banks", ReferenceGroup.PAYMENTS),

    // ── Events & Catalog ──────────────────────────────────────────────────────
    EVENT_TYPE("Event Types", ReferenceGroup.EVENTS),
    EVENT_CATEGORY("Event Categories", ReferenceGroup.EVENTS),
    MUSIC_GENRE("Music Genres", ReferenceGroup.EVENTS),
    AGE_RESTRICTION("Age Restrictions", ReferenceGroup.EVENTS),

    // ── KYB / Onboarding ──────────────────────────────────────────────────────
    KYB_DOCUMENT_TYPE("Verification Document Types", ReferenceGroup.KYB),

    // ── Operations (reason codes) ─────────────────────────────────────────────
    CANCELLATION_REASON("Event Cancellation Reasons", ReferenceGroup.OPERATIONS),
    REFUND_REASON("Refund Reasons", ReferenceGroup.OPERATIONS),
    REJECTION_REASON("Approval Rejection Reasons", ReferenceGroup.OPERATIONS),

    // ── Finance ───────────────────────────────────────────────────────────────
    TAX_RATE("Tax Rates", ReferenceGroup.FINANCE),
    CARD_SCHEME("Card Schemes", ReferenceGroup.FINANCE);

    private final String label;
    private final ReferenceGroup group;

    ReferenceType(String label, ReferenceGroup group) {
        this.label = label;
        this.group = group;
    }

    public String getLabel() {
        return label;
    }

    public ReferenceGroup getGroup() {
        return group;
    }

    /**
     * Logical grouping used purely for presentation (admin UI navigation).
     */
    public enum ReferenceGroup {
        GEOGRAPHY("Geography"),
        PAYMENTS("Payments"),
        EVENTS("Events"),
        KYB("KYB & Onboarding"),
        OPERATIONS("Operations"),
        FINANCE("Finance");

        private final String label;

        ReferenceGroup(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}
