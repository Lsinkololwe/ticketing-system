package com.pml.identity.config;

import com.pml.shared.persistence.IndexEnsurer;
import com.pml.shared.persistence.IndexSpec;
import com.pml.identity.persistence.IdentityCollections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Creates identity-service's MongoDB indexes, one entry per row of the index registry.
 *
 * <h2>What the annotations do not give you</h2>
 * {@code @Indexed} produces one single-field index per annotated field and nothing else. Every
 * compound index below, every TTL, and the sparse-unique pairs are outside what it can express
 * — without this class the platform has none of them, including
 * the unique {@code (userId, organizationId)} pair that allows one membership per user per organization, and the sparse-unique {@code phoneNumber} that makes phone the login identity.
 *
 * <p>A unique index is a <b>constraint, not an optimisation: each one is a race the
 * application cannot win by checking first</b>. A missing unique index does not make
 * anything slower. It permits the duplicate, quietly, under exactly the concurrency that makes
 * it matter.</p>
 *
 * <h2>One list, checked against a live server</h2>
 * Each entry is one index, named and shaped explicitly so it can be compared mechanically —
 * {@code IdentityIndexRegistryTest} asserts every entry exists on a live database with the
 * declared uniqueness, sparseness and expiry.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityIndexInitializer {

    private final ReactiveMongoTemplate mongoTemplate;

    /** The unique partial index that makes a verified contact belong to one account. */
    public static final String UNIQUE_VERIFIED_CONTACT = "uniq_verified_contact";

    /**
     * Registry entries that are deliberately not created at startup. They are built by a migration
     * step once the data can satisfy them; the registry still lists them so the registry tests and
     * the index census describe the whole picture.
     */
    public static final Set<String> DEFERRED = Set.of(UNIQUE_VERIFIED_CONTACT);

    /** The registry entries startup creates: everything except {@link #DEFERRED}. */
    public static List<IndexSpec> startupSpecifications() {
        return specifications().stream()
                .filter(spec -> !DEFERRED.contains(spec.name()))
                .toList();
    }

    /** The deferred entry for {@code name}. */
    public static IndexSpec deferred(String name) {
        return specifications().stream()
                .filter(spec -> spec.name().equals(name) && DEFERRED.contains(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no deferred index named " + name));
    }

    /** Every index identity-service owns, in the order they are ensured. */
    public static List<IndexSpec> specifications() {
        return List.of(
                // One account per address — among the users that have one.
                //
                // Partial, not sparse. Sparse excludes a document where the field is ABSENT; it
                // still indexes one that stores `email: null`, which is what mapping a Java
                // object with a null field produces. Under `unique` the second such document
                // then collides with the first on the key `null`, and no further user without an
                // address can be created. The type test excludes both the absent field and the
                // stored null in one condition.
                IndexSpec.on(IdentityCollections.USERS, "idx_email")
                        .asc("email")
                        .unique()
                        .partialWhereTypeIs("email", "string")
                        .build(),

                // Phone is the login identity — same reasoning, and this is the one that proved
                // it: the sparse version failed to build against the live database with
                // `E11000 dup key: { phoneNumber: null }`.
                IndexSpec.on(IdentityCollections.USERS, "idx_phoneNumber")
                        .asc("phoneNumber")
                        .unique()
                        .partialWhereTypeIs("phoneNumber", "string")
                        .build(),

                // One account per username — and the collection is shared with Better
                // Auth, which creates a document on first OIDC login *without* one; the
                // username arrives later from the Keycloak sync. So nulls are not an
                // edge case here, they are the normal state of every account between
                // signup and sync, and several coexist at once. Sparse would index all
                // of them under the single key `null`, so the second concurrent signup
                // would be rejected as a duplicate. Partial is what makes registration
                // work.
                IndexSpec.on(IdentityCollections.USERS, "idx_username")
                        .asc("username")
                        .unique()
                        .partialWhereTypeIs("username", "string")
                        .build(),

                // The Keycloak user an account is linked to. Unique among the accounts that have
                // one: orphans and accounts still PROVISIONING carry none, and several coexist.
                IndexSpec.on(IdentityCollections.USERS, "idx_keycloakUserId")
                        .asc("keycloakUserId")
                        .unique()
                        .partialWhereTypeIs("keycloakUserId", "string")
                        .build(),

                // the repair schedule and admin lists read accounts by state
                IndexSpec.on(IdentityCollections.USERS, "idx_status")
                        .asc("status")
                        .build(),

                // the accounts folded into a survivor
                IndexSpec.on(IdentityCollections.USERS, "idx_mergedInto")
                        .asc("mergedInto")
                        .partialWhereTypeIs("mergedInto", "string")
                        .build(),

                // One verified, unreleased owner per contact. NOT created at startup: the contacts
                // backfill has to run first or the build fails on whatever duplicates the old data
                // holds. Migration step `contacts-unique-index` creates it; see DEFERRED.
                // `releasedAt: null` rather than `$exists: false`, which a partial filter cannot
                // express; the filter matches a document that lacks the field or stores null.
                IndexSpec.on(IdentityCollections.CONTACTS, UNIQUE_VERIFIED_CONTACT)
                        .asc("type")
                        .asc("valueHash")
                        .unique()
                        .partial(new org.bson.Document("verifiedAt", new org.bson.Document("$exists", true))
                                .append("releasedAt", null))
                        .build(),

                IndexSpec.on(IdentityCollections.CONTACTS, "idx_accountId")
                        .asc("accountId")
                        .build(),

                IndexSpec.on(IdentityCollections.CONSENTS, "idx_accountId_purpose")
                        .asc("accountId")
                        .asc("purpose")
                        .build(),

                IndexSpec.on(IdentityCollections.ACCOUNT_EVENTS, "idx_accountId_at")
                        .asc("accountId")
                        .desc("at")
                        .build(),

                IndexSpec.on(IdentityCollections.ACCOUNT_EVENTS, "idx_kind_at")
                        .asc("kind")
                        .desc("at")
                        .build(),

                // the slug is a public URL and a Keycloak group name
                IndexSpec.on(IdentityCollections.ORGANIZATIONS, "idx_slug")
                        .asc("slug")
                        .unique()
                        .build(),

                // "my organization"
                IndexSpec.on(IdentityCollections.ORGANIZATIONS, "idx_ownerId")
                        .asc("ownerId")
                        .build(),

                // the approval queue, oldest first
                IndexSpec.on(IdentityCollections.ORGANIZATIONS, "idx_status_submittedAt")
                        .asc("status")
                        .asc("submittedAt")
                        .build(),

                // one membership per user per org
                IndexSpec.on(IdentityCollections.ORGANIZATION_MEMBERS, "idx_userId_organizationId")
                        .asc("userId")
                        .asc("organizationId")
                        .unique()
                        .build(),

                // the team list and the owner-count invariant
                IndexSpec.on(IdentityCollections.ORGANIZATION_MEMBERS, "idx_organizationId_role_status")
                        .asc("organizationId")
                        .asc("role")
                        .asc("status")
                        .build(),

                // token lookup on the acceptance page
                // Exactly one OWNER per organization, enforced by the database.
                //
                // A constraint, not an optimisation. The race is two ownership transfers
                // confirmed at the same moment: both read one owner, both write a second, and the
                // organization ends with two people who can each remove the other, move the bank
                // account and take the payouts. No amount of checking first wins that — the check
                // and the write are separate operations, and the whole window lives between them.
                //
                // The partial filter scopes uniqueness to the row that is currently OWNER, so a
                // previous owner demoted to ADMIN does not occupy the key forever.
                // The group-mirror sweep's only query, run every minute.
                // Unindexed it is a full scan of every membership on the platform to find the
                // handful that are behind.
                IndexSpec.on(IdentityCollections.ORGANIZATION_MEMBERS, "idx_mirrorPending")
                        .asc("mirrorPending")
                        .build(),

                IndexSpec.on(IdentityCollections.ORGANIZATION_MEMBERS, "uniq_organization_owner")
                        .asc("organizationId")
                        .unique()
                        .partial(new org.bson.Document("role", "OWNER"))
                        .build(),

                IndexSpec.on(IdentityCollections.TEAM_INVITATIONS, "idx_invitationToken")
                        .asc("invitationToken")
                        .unique()
                        .build(),

                // "is there already a pending invite"
                IndexSpec.on(IdentityCollections.TEAM_INVITATIONS, "idx_organizationId_email_status")
                        .asc("organizationId")
                        .asc("email")
                        .asc("status")
                        .build(),

                // expired invitations remove themselves
                // The token is how a recipient's acceptance finds its transfer,
                // so a duplicate would make one link resolve to two handshakes.
                IndexSpec.on(IdentityCollections.OWNERSHIP_TRANSFERS, "idx_transferToken")
                        .asc("transferToken")
                        .unique()
                        .build(),

                // "is a transfer already in flight for this organization"
                IndexSpec.on(IdentityCollections.OWNERSHIP_TRANSFERS, "idx_organizationId_status")
                        .asc("organizationId")
                        .asc("status")
                        .build(),

                // "invitations sent to this WhatsApp number": pending-invitation lookup and supersede-on-resend
                IndexSpec.on(IdentityCollections.TEAM_INVITATIONS, "idx_phoneNumber_status")
                        .asc("phoneNumber")
                        .asc("status")
                        .build(),

                // the admin alert list, newest activity first
                IndexSpec.on(IdentityCollections.SYSTEM_ALERTS, "idx_status_lastSeenAt")
                        .asc("status")
                        .desc("lastSeenAt")
                        .build(),

                // one live alert per condition: concurrent raisers cannot open two
                IndexSpec.on(IdentityCollections.SYSTEM_ALERTS, "uniq_live_source_key")
                        .asc("source")
                        .asc("key")
                        .unique()
                        .partial(new org.bson.Document("status",
                                new org.bson.Document("$in", java.util.List.of("OPEN", "ACKNOWLEDGED"))))
                        .build(),

                // what is on screen now
                IndexSpec.on(IdentityCollections.ANNOUNCEMENTS, "idx_startsAt_endsAt")
                        .asc("startsAt")
                        .asc("endsAt")
                        .build(),

                // the user-growth series scans a date range
                IndexSpec.on(IdentityCollections.USERS, "idx_createdAt")
                        .asc("createdAt")
                        .build(),

                IndexSpec.on(IdentityCollections.TEAM_INVITATIONS, "idx_expiresAt_ttl")
                        .asc("expiresAt")
                        .expireAfter(Duration.ZERO)
                        .build(),

                // one grant per user per event
                IndexSpec.on(IdentityCollections.EVENT_ACCESS_GRANTS, "idx_userId_eventId")
                        .asc("userId")
                        .asc("eventId")
                        .unique()
                        .build(),

                // who may scan this event
                IndexSpec.on(IdentityCollections.EVENT_ACCESS_GRANTS, "idx_eventId_status")
                        .asc("eventId")
                        .asc("status")
                        .build(),

                // the review panel
                IndexSpec.on(IdentityCollections.VERIFICATION_DOCUMENTS, "idx_organizationId_documentType")
                        .asc("organizationId")
                        .asc("documentType")
                        .build(),

                // the notification feed
                IndexSpec.on(IdentityCollections.NOTIFICATIONS, "idx_userId_status_createdAt")
                        .asc("userId")
                        .asc("status")
                        .desc("createdAt")
                        .build(),

                // one registration per device
                IndexSpec.on(IdentityCollections.USER_DEVICES, "idx_deviceToken")
                        .asc("deviceToken")
                        .unique()
                        .build(),

                // bounded growth
                IndexSpec.on(IdentityCollections.AUDIT_LOGS, "idx_createdAt_ttl")
                        .asc("createdAt")
                        .expireAfter(Duration.ofDays(365))
                        .build(),

                // **hot** — the drain's claim, every poll
                IndexSpec.on(IdentityCollections.OUTBOX, "idx_status_stagedAt")
                        .asc("status")
                        .asc("stagedAt")
                        .build(),

                // ── Lookups and constraints once declared as annotations on the model classes ──
                // Names are the ones the annotations produced, so a database that already has
                // them reports each as present rather than building it again.
                // identity_audit_logs
                IndexSpec.on(IdentityCollections.AUDIT_LOGS, "action_timestamp_idx").asc("action").desc("timestamp").build(),
                IndexSpec.on(IdentityCollections.AUDIT_LOGS, "action").asc("action").build(),
                IndexSpec.on(IdentityCollections.AUDIT_LOGS, "performedBy").asc("performedBy").build(),
                IndexSpec.on(IdentityCollections.AUDIT_LOGS, "status_timestamp_idx").asc("status").desc("timestamp").build(),
                IndexSpec.on(IdentityCollections.AUDIT_LOGS, "status").asc("status").build(),
                IndexSpec.on(IdentityCollections.AUDIT_LOGS, "timestamp").asc("timestamp").build(),
                IndexSpec.on(IdentityCollections.AUDIT_LOGS, "user_action_idx").asc("userId").asc("action").desc("timestamp").build(),
                IndexSpec.on(IdentityCollections.AUDIT_LOGS, "userId").asc("userId").build(),

                // identity_event_access_grants
                IndexSpec.on(IdentityCollections.EVENT_ACCESS_GRANTS, "eventId").asc("eventId").build(),
                IndexSpec.on(IdentityCollections.EVENT_ACCESS_GRANTS, "organizationId").asc("organizationId").build(),
                IndexSpec.on(IdentityCollections.EVENT_ACCESS_GRANTS, "userId").asc("userId").build(),

                // identity_event_reminders
                IndexSpec.on(IdentityCollections.EVENT_REMINDERS, "eventId").asc("eventId").build(),
                IndexSpec.on(IdentityCollections.EVENT_REMINDERS, "userId").asc("userId").build(),

                // identity_notification_preferences
                IndexSpec.on(IdentityCollections.NOTIFICATION_PREFERENCES, "userId").asc("userId").unique().build(),

                // identity_notifications
                IndexSpec.on(IdentityCollections.NOTIFICATIONS, "userId").asc("userId").build(),

                // identity_organization_members
                IndexSpec.on(IdentityCollections.ORGANIZATION_MEMBERS, "org_role_idx").asc("organizationId").asc("role").build(),
                IndexSpec.on(IdentityCollections.ORGANIZATION_MEMBERS, "org_status_idx").asc("organizationId").asc("status").build(),
                IndexSpec.on(IdentityCollections.ORGANIZATION_MEMBERS, "organizationId").asc("organizationId").build(),
                IndexSpec.on(IdentityCollections.ORGANIZATION_MEMBERS, "userId").asc("userId").build(),

                // identity_organizations
                IndexSpec.on(IdentityCollections.ORGANIZATIONS, "businessEmail").asc("businessEmail").build(),
                IndexSpec.on(IdentityCollections.ORGANIZATIONS, "status").asc("status").build(),
                IndexSpec.on(IdentityCollections.ORGANIZATIONS, "statusSemantic").asc("statusSemantic").build(),

                // identity_ownership_transfers
                IndexSpec.on(IdentityCollections.OWNERSHIP_TRANSFERS, "expiresAt").asc("expiresAt").build(),
                IndexSpec.on(IdentityCollections.OWNERSHIP_TRANSFERS, "organizationId").asc("organizationId").build(),

                // identity_payout_config_audit_logs
                IndexSpec.on(IdentityCollections.PAYOUT_CONFIG_AUDIT_LOGS, "action").asc("action").build(),
                IndexSpec.on(IdentityCollections.PAYOUT_CONFIG_AUDIT_LOGS, "org_timestamp_idx").asc("organizationId").desc("timestamp").build(),
                IndexSpec.on(IdentityCollections.PAYOUT_CONFIG_AUDIT_LOGS, "organizationId").asc("organizationId").build(),
                IndexSpec.on(IdentityCollections.PAYOUT_CONFIG_AUDIT_LOGS, "timestamp").asc("timestamp").build(),
                IndexSpec.on(IdentityCollections.PAYOUT_CONFIG_AUDIT_LOGS, "user_timestamp_idx").asc("userId").desc("timestamp").build(),
                IndexSpec.on(IdentityCollections.PAYOUT_CONFIG_AUDIT_LOGS, "userId").asc("userId").build(),

                // identity_team_invitations
                IndexSpec.on(IdentityCollections.TEAM_INVITATIONS, "email_org_idx").asc("email").asc("organizationId").build(),
                IndexSpec.on(IdentityCollections.TEAM_INVITATIONS, "email").asc("email").build(),
                IndexSpec.on(IdentityCollections.TEAM_INVITATIONS, "org_status_idx").asc("organizationId").asc("status").build(),
                IndexSpec.on(IdentityCollections.TEAM_INVITATIONS, "organizationId").asc("organizationId").build(),

                // identity_token_revocations
                IndexSpec.on(IdentityCollections.TOKEN_REVOCATIONS, "expiresAt").asc("expiresAt").expireAfter(Duration.ofSeconds(0)).build(),

                // identity_user_devices
                IndexSpec.on(IdentityCollections.USER_DEVICES, "deviceToken").asc("deviceToken").build(),
                IndexSpec.on(IdentityCollections.USER_DEVICES, "userId").asc("userId").build(),

                // identity_verification_documents
                IndexSpec.on(IdentityCollections.VERIFICATION_DOCUMENTS, "organizationId").asc("organizationId").build());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        IndexEnsurer.Report report = new IndexEnsurer(mongoTemplate)
                .ensure(startupSpecifications())
                // Boot-time work, so blocking on the main thread is allowed here: nothing
                // may serve traffic against a collection whose constraints are absent.
                .block();

        if (report != null && !report.isClean()) {
            log.error("identity-service index registry is not satisfied: {}", report);
        }
    }
}
