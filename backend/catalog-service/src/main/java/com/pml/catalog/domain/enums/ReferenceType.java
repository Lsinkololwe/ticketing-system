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
    /** A city within a {@code PROVINCE} (its parent). Venues and the discovery city filter name one. */
    CITY("Cities", ReferenceGroup.GEOGRAPHY),

    // ── Payments ──────────────────────────────────────────────────────────────
    MOBILE_MONEY_OPERATOR("Mobile Money Operators", ReferenceGroup.PAYMENTS),
    BANK("Banks", ReferenceGroup.PAYMENTS),

    // ── Events & Catalog ──────────────────────────────────────────────────────
    EVENT_TYPE("Event Types", ReferenceGroup.EVENTS),
    EVENT_CATEGORY("Event Categories", ReferenceGroup.EVENTS),
    MUSIC_GENRE("Music Genres", ReferenceGroup.EVENTS),
    AGE_RESTRICTION("Age Restrictions", ReferenceGroup.EVENTS),
    /** The tier categories an organizer files a ticket tier under (mirrors the {@code TierCategory} API enum). */
    TICKET_TIER_CATEGORY("Ticket Tier Categories", ReferenceGroup.EVENTS),

    // ── KYB / Onboarding ──────────────────────────────────────────────────────
    KYB_DOCUMENT_TYPE("Verification Document Types", ReferenceGroup.KYB),
    /** Who is applying: individual, business, non-profit ... (mirrors identity's {@code OrganizationType}). */
    ORGANIZER_TYPE("Organizer Types", ReferenceGroup.KYB),
    /**
     * The legal form a business is registered as (mirrors identity's {@code BusinessType}). Carries the
     * document codes that form must supply, so the onboarding wizard asks for exactly what a review needs.
     */
    BUSINESS_TYPE("Legal Business Types", ReferenceGroup.KYB),

    // ── Roles & access ────────────────────────────────────────────────────────
    /** Organization roles with the plain-language description shown wherever one is picked. */
    ORGANIZATION_ROLE("Organization Roles", ReferenceGroup.ACCESS),
    EVENT_ROLE("Event Roles", ReferenceGroup.ACCESS),

    // ── Communication ─────────────────────────────────────────────────────────
    NOTIFICATION_CHANNEL("Notification Channels", ReferenceGroup.COMMUNICATION),
    NOTIFICATION_CATEGORY("Notification Categories", ReferenceGroup.COMMUNICATION),

    // ── Reporting ─────────────────────────────────────────────────────────────
    REPORT_PERIOD("Report Periods", ReferenceGroup.REPORTING),

    // ── Operations (reason codes) ─────────────────────────────────────────────
    CANCELLATION_REASON("Event Cancellation Reasons", ReferenceGroup.OPERATIONS),
    REFUND_REASON("Refund Reasons", ReferenceGroup.OPERATIONS),
    REJECTION_REASON("Approval Rejection Reasons", ReferenceGroup.OPERATIONS),

    // ── Finance ───────────────────────────────────────────────────────────────
    TAX_RATE("Tax Rates", ReferenceGroup.FINANCE),
    CARD_SCHEME("Card Schemes", ReferenceGroup.FINANCE),

    // ── Workflow statuses ─────────────────────────────────────────────────────
    //
    // Configurable in exactly the same way as everything above: an administrator
    // adds "AWAITING_COMPLIANCE_REVIEW" to PAYOUT_STATUS whenever they need it,
    // with no deployment.
    //
    // The one difference is that these DRIVE BEHAVIOUR, so every row must
    // declare a WorkflowSemantic. Code branches on that semantic, never on the
    // code string — because a payout sitting in a status no branch recognises
    // never moves, and nobody finds out until an organizer asks where their
    // money went. Same shape as Jira's status categories: unlimited statuses, a
    // handful of meanings.
    //
    // Three of them carry `codeOwnedMachine`. Their transitions are not the
    // administrator's to redraw — see isCodeOwnedMachine().
    TICKET_STATUS("Ticket Statuses", ReferenceGroup.WORKFLOW, true, true),
    PAYMENT_STATUS("Payment Statuses", ReferenceGroup.WORKFLOW, true),
    PAYOUT_STATUS("Payout Statuses", ReferenceGroup.WORKFLOW, true),
    REFUND_STATUS("Refund Statuses", ReferenceGroup.WORKFLOW, true),
    CHARGEBACK_STATUS("Chargeback Statuses", ReferenceGroup.WORKFLOW, true),
    ESCROW_STATUS("Escrow Statuses", ReferenceGroup.WORKFLOW, true),
    EVENT_STATUS("Event Statuses", ReferenceGroup.WORKFLOW, true, true),
    ORGANIZATION_STATUS("Organization Statuses", ReferenceGroup.WORKFLOW, true),
    VERIFICATION_DOCUMENT_STATUS("Document Statuses", ReferenceGroup.WORKFLOW, true),
    TEAM_INVITATION_STATUS("Invitation Statuses", ReferenceGroup.WORKFLOW, true),
    RESERVATION_STATUS("Reservation Statuses", ReferenceGroup.WORKFLOW, true, true);

    private final String label;
    private final ReferenceGroup group;
    private final boolean workflow;
    private final boolean codeOwnedMachine;

    ReferenceType(String label, ReferenceGroup group) {
        this(label, group, false, false);
    }

    ReferenceType(String label, ReferenceGroup group, boolean workflow) {
        this(label, group, workflow, false);
    }

    ReferenceType(String label, ReferenceGroup group, boolean workflow, boolean codeOwnedMachine) {
        this.label = label;
        this.group = group;
        this.workflow = workflow;
        this.codeOwnedMachine = codeOwnedMachine;
    }

    /**
     * Whether this type's transitions belong to a state machine in code, and so
     * cannot be redrawn by an administrator.
     *
     * <h2>Why some workflows are configurable and some are not</h2>
     * The <em>vocabulary</em> of every workflow type is configurable: an
     * administrator adds a status, gives it a meaning, and code recognises it
     * immediately. The <em>graph</em> is a different question. Three types have
     * their transitions fixed and enforced by a table in Java:
     *
     * <ul>
     *   <li>{@code TICKET_STATUS} — {@code TicketStateMachine}, whose table is
     *       the complete set of ticket transitions; nothing else may add one</li>
     *   <li>{@code RESERVATION_STATUS} — {@code ReservationStateMachine}</li>
     *   <li>{@code EVENT_STATUS} — {@code EventLifecycleServiceImpl}'s table</li>
     * </ul>
     *
     * <p>Letting {@code allowedTransitions} be edited for these would create two
     * disagreeing authorities over the same question, and the database one would
     * lose silently — the Java table is what actually refuses a write. Worse, it
     * would look like it worked: an administrator adds
     * {@code REFUNDED → ISSUED}, sees it saved, and discovers at a gate that
     * refunded tickets still do not scan.
     *
     * <p>So for these three the field is refused at the write, with a sentence
     * naming the class that owns the answer.
     */
    public boolean isCodeOwnedMachine() {
        return codeOwnedMachine;
    }

    /**
     * Whether rows of this type drive behaviour and must therefore declare a
     * {@link WorkflowSemantic}.
     *
     * <p>This is the line between a value that is merely descriptive and one the
     * code resolves behaviour through. Taxonomy can be anything an administrator
     * likes; a status has to mean something.
     */
    public boolean isWorkflow() {
        return workflow;
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
        FINANCE("Finance"),
        ACCESS("Roles & Access"),
        COMMUNICATION("Communication"),
        REPORTING("Reporting"),
        WORKFLOW("Workflow & Statuses");

        private final String label;

        ReferenceGroup(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}
