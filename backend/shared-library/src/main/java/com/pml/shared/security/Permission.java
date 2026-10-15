package com.pml.shared.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Every permission the platform checks, in {@code module:action} form.
 *
 * <p>The list is closed: a name that is not here grants nothing, and there is no wildcard.
 * Services exchange permissions by {@link #code()}; the enum constant is only the Java handle.
 * Each code has exactly one colon, so a code splits unambiguously into its module and action.
 *
 * <p>What a platform role grants is declared on {@link PlatformRole}, because its reach is the same
 * in every service. Organization and event roles are resolved by the identity service, which owns
 * memberships and grants.
 */
public enum Permission {

    EVENT_VIEW("event:view", Scope.EVENT, "See an event's details and sales data"),
    EVENT_CREATE("event:create", Scope.ORGANIZATION, "Create a draft event"),
    EVENT_EDIT("event:edit", Scope.EVENT, "Edit an event's details and ticket tiers"),
    EVENT_PUBLISH("event:publish", Scope.EVENT, "Publish, unpublish or reschedule an event"),
    EVENT_DELETE("event:delete", Scope.EVENT, "Delete a draft event"),
    EVENT_CANCEL("event:cancel", Scope.EVENT, "Cancel a published event"),
    ATTENDEE_VIEW("attendee:view", Scope.EVENT, "See the attendee list"),
    TICKET_SCAN("ticket:scan", Scope.EVENT, "Validate tickets at the gate"),
    TICKET_REFUND("ticket:refund", Scope.EVENT, "Issue a refund for a ticket"),
    ANALYTICS_VIEW("analytics:view", Scope.EVENT, "See event and sales analytics"),
    PROMOTION_MANAGE("promotion:manage", Scope.ORGANIZATION, "Create and manage promo codes"),
    FINANCIAL_VIEW("financial:view", Scope.ORGANIZATION, "See revenue, escrow and commission figures"),
    PAYOUT_REQUEST("payout:request", Scope.ORGANIZATION, "Request a payout"),
    PAYOUT_APPROVE("payout:approve", Scope.PLATFORM, "Approve a payout"),
    BANK_MANAGE("bank:manage", Scope.ORGANIZATION, "Add and verify bank accounts"),
    TEAM_INVITE("team:invite", Scope.ORGANIZATION, "Invite a team member"),
    TEAM_REMOVE("team:remove", Scope.ORGANIZATION, "Remove a team member"),
    TEAM_ROLE("team:role", Scope.ORGANIZATION, "Change a team member's role"),
    TEAM_VIEW("team:view", Scope.ORGANIZATION, "See the team list"),
    EVENT_ACCESS_GRANT("event_access:grant", Scope.EVENT, "Grant a person access to a single event"),
    ORGANIZATION_VIEW("organization:view", Scope.ORGANIZATION, "See the organization's profile"),
    ORGANIZATION_EDIT("organization:edit", Scope.ORGANIZATION, "Edit the organization's profile and settings"),
    ORGANIZATION_BILLING("organization:billing", Scope.ORGANIZATION, "Manage billing settings"),
    ORGANIZATION_TRANSFER("organization:transfer", Scope.ORGANIZATION, "Transfer ownership"),
    ORGANIZATION_DELETE("organization:delete", Scope.ORGANIZATION, "Request deletion of the organization"),
    ORGANIZATION_SUSPEND("organization:suspend", Scope.PLATFORM, "Suspend an organization"),
    ORGANIZATION_APPROVE("organization:approve", Scope.PLATFORM, "Approve or reject an organization's application"),
    PLATFORM_CONFIGURE("platform:configure", Scope.PLATFORM, "Change platform configuration and feature flags"),
    TRANSACTION_RECOVER("transaction:recover", Scope.PLATFORM, "Resume, retry and resolve stuck transactions"),
    AUDIT_VIEW("audit:view", Scope.PLATFORM, "Read the audit trail"),

    // ---- the rest of what staff and organizations do, named once (D-61, D-62)
    LEDGER_VIEW("ledger:view", Scope.PLATFORM, "Read the general ledger: journal entries, trial balance and the chart of accounts"),
    LEDGER_MANAGE("ledger:manage", Scope.PLATFORM, "Create, post and reverse journal entries"),
    CHART_OF_ACCOUNTS_MANAGE("chart_of_accounts:manage", Scope.PLATFORM, "Add, change and retire accounts in the chart of accounts"),
    RECONCILIATION_VIEW("reconciliation:view", Scope.PLATFORM, "See reconciliation runs and consistency results"),
    RECONCILIATION_MANAGE("reconciliation:manage", Scope.PLATFORM, "Start, resolve and close reconciliation runs and record settlements"),
    ESCROW_VIEW("escrow:view", Scope.PLATFORM, "See any event's escrow account, balance and transactions"),
    ESCROW_MANAGE("escrow:manage", Scope.PLATFORM, "Lock, unlock, close or change the status of an escrow account"),
    CHARGEBACK_VIEW("chargeback:view", Scope.PLATFORM, "See chargebacks and their statistics"),
    CHARGEBACK_MANAGE("chargeback:manage", Scope.PLATFORM, "Receive, review, accept, dispute and close chargebacks"),
    CHARGEBACK_RECOVER("chargeback:recover", Scope.PLATFORM, "Record how a chargeback is being recovered"),
    PAYMENT_VIEW("payment:view", Scope.PLATFORM, "Search and inspect payment attempts and payment risk"),
    PAYMENT_MANAGE("payment:manage", Scope.PLATFORM, "Annotate and review payment attempts"),
    PAYMENT_APPROVE("payment:approve", Scope.PLATFORM, "Force a payment attempt to complete by hand"),
    PAYOUT_VIEW("payout:view", Scope.PLATFORM, "See every organization's payout requests and recovery queues"),
    PAYOUT_MANAGE("payout:manage", Scope.PLATFORM, "Hold, release, reject, process, escalate and retry payouts"),
    REFUND_VIEW("refund:view", Scope.PLATFORM, "See refund requests across the platform"),
    REFUND_MANAGE("refund:manage", Scope.PLATFORM, "Approve, reject and raise refund requests"),
    REFUND_APPROVE("refund:approve", Scope.PLATFORM, "Process an approved refund and move the money"),
    COMMISSION_VIEW("commission:view", Scope.PLATFORM, "See commission records"),
    SETTLEMENT_VIEW("settlement:view", Scope.PLATFORM, "See gateway settlements"),
    PLATFORM_ACCOUNT_VIEW("platform_account:view", Scope.PLATFORM, "See the platform's own accounts"),
    PLATFORM_ACCOUNT_TRANSFER("platform_account:transfer", Scope.PLATFORM, "Move money between the platform's own accounts"),
    STATISTICS_VIEW("statistics:view", Scope.PLATFORM, "See platform-wide booking, transaction and financial statistics"),
    REPORT_CREATE("report:create", Scope.PLATFORM, "Export a financial report"),
    EVENT_REPORT_CREATE("event_report:create", Scope.PLATFORM, "Export event data and event reports"),
    BANK_ACCOUNT_VIEW("bank_account:view", Scope.PLATFORM, "See organizations' bank accounts under review"),
    BANK_ACCOUNT_APPROVE("bank_account:approve", Scope.PLATFORM, "Verify, reject, suspend or reinstate an organization's bank account"),
    RESERVATION_VIEW("reservation:view", Scope.PLATFORM, "See expired and held reservations"),
    RESERVATION_MANAGE("reservation:manage", Scope.PLATFORM, "Force a reservation to expire"),
    TICKET_VIEW("ticket:view", Scope.PLATFORM, "Search tickets across the platform"),
    TICKET_MANAGE("ticket:manage", Scope.PLATFORM, "Cancel, correct and re-issue any ticket"),
    BUYER_VIEW("buyer:view", Scope.PLATFORM, "Look up a buyer's tickets, bookings, reservations and refunds for support"),
    CATALOG_VIEW("catalog:view", Scope.PLATFORM, "See every event in the catalogue and its platform-wide statistics"),
    APPROVAL_VIEW("approval:view", Scope.PLATFORM, "See the approval queue, timelines, escalations and blockers"),
    APPROVAL_MANAGE("approval:manage", Scope.PLATFORM, "Assign reviewers, comment, request changes and handle escalations"),
    EVENT_APPROVE("event:approve", Scope.PLATFORM, "Approve or reject an event for publication"),
    EVENT_MANAGE("event:manage", Scope.PLATFORM, "Feature an event and send publish reminders"),
    REFERENCE_DATA_MANAGE("reference_data:manage", Scope.PLATFORM, "Create and change reference data lists"),
    LOCATION_MANAGE("location:manage", Scope.PLATFORM, "Create and change provinces and cities"),
    CATEGORY_MANAGE("category:manage", Scope.PLATFORM, "Create and change event categories"),
    MEDIA_MODERATION_VIEW("media_moderation:view", Scope.PLATFORM, "See all uploaded media for moderation"),
    MEDIA_MODERATION_MANAGE("media_moderation:manage", Scope.PLATFORM, "Flag, remove and restore media and override an event banner"),
    STOCK_IMAGE_MANAGE("stock_image:manage", Scope.PLATFORM, "Upload, change and delete stock images"),
    PLATFORM_VIEW("platform:view", Scope.PLATFORM, "See platform configuration, service health, alerts and announcements"),
    PLATFORM_MANAGE("platform:manage", Scope.PLATFORM, "Acknowledge alerts and send or cancel announcements"),
    USER_VIEW("user:view", Scope.PLATFORM, "See user accounts, staff accounts and user statistics"),
    USER_MANAGE("user:manage", Scope.PLATFORM, "Create, change, lock, suspend and delete user accounts and their roles"),
    STAFF_MANAGE("staff:manage", Scope.PLATFORM, "Reset a staff member's temporary password"),
    SESSION_MANAGE("session:manage", Scope.PLATFORM, "Revoke tokens, sessions and a user's access"),
    ORGANIZATION_DIRECTORY_VIEW("organization_directory:view", Scope.PLATFORM, "See every organization and its application"),
    ORGANIZATION_COMMISSION_CONFIGURE("organization_commission:configure", Scope.PLATFORM, "Set an organization's commission rate"),
    ORGANIZATION_MANAGE("organization:manage", Scope.PLATFORM, "Set an organization's status"),
    PERMISSION_VIEW("permission:view", Scope.PLATFORM, "See the permission catalogue and what each role holds"),
    KYB_DOCUMENT_VIEW("kyb_document:view", Scope.PLATFORM, "See organizations' verification documents"),
    KYB_DOCUMENT_APPROVE("kyb_document:approve", Scope.PLATFORM, "Approve or reject a verification document"),
    MEDIA_MANAGE("media:manage", Scope.ORGANIZATION, "Upload, change and delete the organization's media");

    /** Where a permission is held: on the platform, in an organization, or on one event. */
    public enum Scope { PLATFORM, ORGANIZATION, EVENT }

    /**
     * The closed set of verbs a code may end in. A verb that is not here cannot be declared, so
     * the catalogue cannot sprawl into one-off words.
     */
    public enum Action {
        VIEW, CREATE, EDIT, PUBLISH, DELETE, CANCEL, SCAN, REFUND, MANAGE, REQUEST, APPROVE,
        INVITE, REMOVE, ROLE, GRANT, BILLING, TRANSFER, SUSPEND, CONFIGURE, RECOVER;

        /** The verb as it appears after the colon. */
        public String verb() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** One lower-case snake-case word, a colon, one lower-case snake-case word. No dot, no wildcard. */
    private static final java.util.regex.Pattern GRAMMAR =
            java.util.regex.Pattern.compile("^[a-z][a-z0-9_]+:[a-z][a-z0-9_]+$");

    private static final Map<String, Permission> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Permission::code, Function.identity()));

    static {
        // A malformed or unknown-verb code fails class loading, so it can never reach a deployment.
        for (Permission permission : values()) {
            if (!GRAMMAR.matcher(permission.code).matches()) {
                throw new IllegalStateException("permission code is not module:action: " + permission.code);
            }
            String verb = permission.code.substring(permission.code.indexOf(':') + 1);
            boolean known = Arrays.stream(Action.values()).anyMatch(action -> action.verb().equals(verb));
            if (!known) {
                throw new IllegalStateException("permission verb is not in the closed vocabulary: " + permission.code);
            }
        }
    }

    private final String code;
    private final Scope scope;
    private final String description;

    Permission(String code, Scope scope, String description) {
        this.code = code;
        this.scope = scope;
        this.description = description;
    }

    public String code() {
        return code;
    }

    public Scope scope() {
        return scope;
    }

    public String description() {
        return description;
    }

    /** The part of the code before the colon — {@code event} for {@code event:edit}. */
    public String module() {
        return code.substring(0, code.indexOf(':'));
    }

    /** The verb after the colon — {@code edit} for {@code event:edit}. */
    public Action action() {
        String verb = code.substring(code.indexOf(':') + 1);
        return Arrays.stream(Action.values()).filter(action -> action.verb().equals(verb)).findFirst().orElseThrow();
    }

    /** The permission a code names, or empty when the code is not in the catalogue. */
    public static Optional<Permission> fromCode(String code) {
        return Optional.ofNullable(code == null ? null : BY_CODE.get(code));
    }

    /** The codes of {@code permissions}, sorted so the output is stable. */
    public static Set<String> codes(Collection<Permission> permissions) {
        return permissions.stream().map(Permission::code)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    /**
     * What platform roles grant, before any organization membership or event grant is read; the
     * rules are {@link PlatformRole#grants()}. Role names are compared without case and without a
     * {@code ROLE_} prefix, so realm roles and Spring authorities both work.
     */
    public static Set<Permission> grantedByPlatformRoles(Collection<String> roles) {
        return PlatformRole.grantedBy(roles);
    }
}
