package com.pml.identity.persistence;

/**
 * The collection names this service owns, one constant per collection.
 *
 * <h2>Why the names are constants and not literals</h2>
 * A collection name appears in far more places than its {@code @Document}: aggregation
 * pipelines pass it to {@code mongoTemplate.aggregate}, the index initialiser and the schema
 * validator key off it, and migrations name it directly. A literal in each of those is a
 * rename waiting to go wrong, and it goes wrong <em>silently</em> — an aggregation against a
 * collection that no longer exists does not fail, it returns zero rows. A dashboard showing
 * K0.00 and a count of 0 is indistinguishable from a quiet week.
 *
 * <h2>Ownership</h2>
 * Only this service's collections are named here. The {@code identity_} prefix is the only
 * place write-ownership is recorded, so a constant for another service's collection in this
 * file would be a second writer appearing without anyone deciding on one.
 */
public final class IdentityCollections {

    private IdentityCollections() {
    }

    /** Profile; _id is the Keycloak user ID */
    public static final String USERS = "identity_users";

    /** The tenant, its KYB data and its status */
    public static final String ORGANIZATIONS = "identity_organizations";

    /** Membership and organization role */
    public static final String ORGANIZATION_MEMBERS = "identity_organization_members";

    /** Pending invitations and their tokens */
    public static final String TEAM_INVITATIONS = "identity_team_invitations";

    /** Pending ownership transfers */
    public static final String OWNERSHIP_TRANSFERS = "identity_ownership_transfers";

    /** Event-level role overrides */
    public static final String EVENT_ACCESS_GRANTS = "identity_event_access_grants";

    /**
     * Retired: permissions and role sets are declared in code ({@code com.pml.shared.security.Permission},
     * {@code OrganizationRole}, {@code EventRole}). No model reads or writes these collections; the
     * names remain for the migrations that renamed and then dropped them.
     */
    public static final String PERMISSIONS = "identity_permissions";

    /** Retired with {@link #PERMISSIONS}. */
    public static final String ROLE_PERMISSIONS = "identity_role_permissions";

    /** KYB document metadata and review state */
    public static final String VERIFICATION_DOCUMENTS = "identity_verification_documents";

    /** Delivered and pending notifications */
    public static final String NOTIFICATIONS = "identity_notifications";

    /** Per-user channel preferences */
    public static final String NOTIFICATION_PREFERENCES = "identity_notification_preferences";

    /** Push tokens */
    public static final String USER_DEVICES = "identity_user_devices";

    /** Scheduled reminder state */
    public static final String EVENT_REMINDERS = "identity_event_reminders";

    /** The immutable audit trail */
    public static final String AUDIT_LOGS = "identity_audit_logs";

    /** Versioned message bodies per channel and locale */
    public static final String NOTIFICATION_TEMPLATES = "identity_notification_templates";

    /** Bulk-send batches and their per-recipient outcome */
    public static final String MASS_SENDS = "identity_mass_sends";

    /** One claim per review subject — organizer, document or event */
    public static final String REVIEW_CLAIMS = "identity_review_claims";

    /** Time-limited subject and IP blocks */
    public static final String TEMPORARY_BLOCKS = "identity_temporary_blocks";

    /** Consent given and withdrawn, per purpose */
    public static final String CONSENT_RECORDS = "identity_consent_records";

    /** Erasure lifecycle and the 30-day grace period */
    public static final String ERASURE_REQUESTS = "identity_erasure_requests";

    /** Subject-access export requests and their artefacts */
    public static final String DATA_EXPORTS = "identity_data_exports";

    /** Precomputed identity and organization statistics */
    public static final String STATISTICS_ROLLUPS = "identity_statistics_rollups";

    /** This service's document-version backfills */
    public static final String MIGRATION_RUNS = "identity_migration_runs";

    /** Revoked tokens, sessions and subjects — the system of record */
    public static final String TOKEN_REVOCATIONS = "identity_token_revocations";

    /** Staged cross-service events, written in the business transaction */
    public static final String OUTBOX = "identity_outbox";

    /** Who changed payout configuration and to what */
    public static final String PAYOUT_CONFIG_AUDIT_LOGS = "identity_payout_config_audit_logs";

    /** Verified WhatsApp numbers and emails, each owned by exactly one account */
    public static final String CONTACTS = "identity_contacts";

    /** Consent granted per purpose and version, with withdrawal */
    public static final String CONSENTS = "identity_consents";

    /** Account lifecycle and OTP-lock facts, no personal data */
    public static final String ACCOUNT_EVENTS = "identity_account_events";

    /** Operational alerts raised by services and by the health probe, with their acknowledgement */
    public static final String SYSTEM_ALERTS = "identity_system_alerts";

    /** Administrator announcements shown to a segment of users in a time window */
    public static final String ANNOUNCEMENTS = "identity_announcements";

}
