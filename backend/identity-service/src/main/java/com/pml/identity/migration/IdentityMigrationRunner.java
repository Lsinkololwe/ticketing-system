package com.pml.identity.migration;

import com.pml.identity.config.IdentityIndexInitializer;
import com.pml.identity.persistence.IdentityCollections;
import com.pml.shared.migration.CollectionRenameMigration;
import com.pml.shared.migration.ConflictingIndexRepair;
import com.pml.shared.migration.MigrationLedger;
import com.pml.shared.migration.MigrationRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Identity's data migrations, in dependency order.
 *
 * <h2>Why identity needs a runner of its own</h2>
 * A migration service is only a class until something invokes it, and each service's steps run
 * against its own ledger. This class is what makes identity's steps actually execute, on
 * {@code ApplicationReadyEvent}, in the order declared below.
 *
 * <p>{@link OrganizationStatusSemanticMigrationService} is the one that shows why it matters.
 * Organizations written before {@code statusSemantic} existed carry no semantic, and the admin
 * review queue selects <em>by semantic</em> — so an unmigrated pending application is absent from
 * the queue built to show it, which reads as "nothing to review" rather than as a fault. An
 * integration test cannot catch that on its own: invoking a migration directly proves it works,
 * never that anything runs it.
 *
 * @see MigrationRunner
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityMigrationRunner extends MigrationRunner {

    /** Separate from booking's, so each service's history reads on its own. */
    static final String LEDGER_COLLECTION = "identity_migrations";

    private final ReactiveMongoTemplate mongoTemplate;

    /** The platform clock, handed to the ledger so its audit rows are testable. */
    private final Clock clock;

    private final IdentityCollectionRenameMigrationService collectionRenames;
    private final IdentityRegistryConformanceMigrationService registryConformance;
    private final OrganizationStatusSemanticMigrationService organizationSemantics;
    private final UserFieldCleanupMigrationService userFieldCleanup;
    private final PermissionModelMigrationService permissionModel;
    private final UserLegacyIndexRetirementMigrationService userLegacyIndexes;
    private final AccountPreflightReportMigrationService accountPreflight;
    private final UserAccountFieldsBackfillMigrationService userAccountFields;
    private final UserUsernameNormalizationMigrationService usernameNormalization;
    private final ContactsBackfillMigrationService contactsBackfill;
    private final ContactsUniqueIndexMigrationService contactsUniqueIndex;

    @Value("${identity.migrations.enabled:true}")
    private boolean enabled;

    @Value("${identity.migrations.fail-fast:true}")
    private boolean failFast;

    @Override
    protected MigrationLedger ledger() {
        return new MigrationLedger(mongoTemplate, LEDGER_COLLECTION, clock);
    }

    @Override
    protected boolean isEnabled() {
        return enabled;
    }

    @Override
    protected boolean isFailFast() {
        return failFast;
    }

    @Override
    protected String serviceName() {
        return "identity-service";
    }

    @Override
    protected Map<String, Supplier<Mono<?>>> steps() {
        Map<String, Supplier<Mono<?>>> steps = new LinkedHashMap<>();

        // FIRST: organization-status-semantic below rewrites documents in
        // identity_organizations, which do not live there until this has run.
        steps.put("collection-registry-rename", collectionRenames::migrate);

        // AFTER the rename: these filters name registry collections, and before the move the
        // documents are still under their old names where nothing here would match them.
        steps.put("registry-conformance-backfill", registryConformance::migrate);


        // Stamps statusSemantic onto organizations written before the field
        // existed. It resolves each status against catalog's reference data, so
        // it needs those rows to exist — which they now do, since catalog seeds
        // them at startup rather than never.
        steps.put("organization-status-semantic", organizationSemantics::migrate);

        // Drops the retired registrationEventPublished field from User; publication tracks
        // through the outbox. Order-independent of the steps above (it names identity_users, which
        // no earlier step here touches).
        steps.put("user-field-cleanup", userFieldCleanup::migrate);

        // After collection-registry-rename, which moves the permission collections onto the names
        // this step drops and the members and grants onto the names it rewrites.
        steps.put("permission-model-catalogue", permissionModel::migrate);

        // LAST. An older, undeclared index occupies the keys a declared index wants, so the server
        // refuses the declaration and IndexEnsurer reports a conflict. One of those is a plain
        // index on identity_team_invitations.expiresAt where a TTL index is declared — nothing
        // expires, and the index looks correct unless its options are read. Runs after every
        // collection move above, because a rename carries the indexes along with the collection.
        // Its own step rather than a pair added to collection-registry-rename, which is already
        // recorded SUCCEEDED: a ledger row is immutable, so extending the table that step reads
        // would change what an applied row means without ever re-running it. The collection
        // needs the service prefix every other identity collection carries.
        steps.put("payout-config-audit-rename",
                () -> new CollectionRenameMigration(mongoTemplate).migrate(
                        CollectionRenameMigration.table(
                                "payout_config_audit_logs",
                                IdentityCollections.PAYOUT_CONFIG_AUDIT_LOGS)));

        steps.put("index-registry-conformance-3",
                () -> new ConflictingIndexRepair(mongoTemplate)
                        .repair(IdentityIndexInitializer.startupSpecifications()));

        // The registry now also declares every index the model annotations used to create,
        // under the names they created them with, so a database built before is mostly already
        // conformant. Its own step because a recorded step is never re-run.
        steps.put("index-registry-conformance-4",
                () -> new ConflictingIndexRepair(mongoTemplate)
                        .repair(IdentityIndexInitializer.startupSpecifications()));

        // ---- ET-IDN-004: accounts and contacts. Each step depends on the one before it. ----

        // The plain unique `email` index refuses the second account without an email, which is
        // every phone-only account. Dropped before anything is written under the new model.
        steps.put("users-legacy-indexes-drop", userLegacyIndexes::migrate);

        // Read-only. Logs counts of what the next two steps will meet; changes nothing.
        steps.put("account-preflight-report", accountPreflight::migrate);

        // status, keycloakUserId, provisionedAt on every existing account; orphans become
        // PROVISIONING without a Keycloak link. Old fields stay.
        steps.put("users-account-fields-backfill", userAccountFields::migrate);

        // Startup index creation refuses idx_username on a database whose legacy accounts share or
        // lack a username (E11000). Backfills and de-duplicates them deterministically, then builds
        // the index. After the account fields so keycloakUserId is there to backfill from.
        steps.put("users-username-normalize", usernameNormalization::migrate);

        // Email and phone contacts from the account fields. Resumable; identity.migrations.
        // contacts-backfill-dry-run counts without writing.
        steps.put("contacts-backfill", contactsBackfill::migrate);

        // LAST: builds uniq_verified_contact only after the contacts exist. If a verified contact
        // belongs to two accounts it fails with a report and merges nothing.
        steps.put("contacts-unique-index", contactsUniqueIndex::migrate);

        return steps;
    }
}
