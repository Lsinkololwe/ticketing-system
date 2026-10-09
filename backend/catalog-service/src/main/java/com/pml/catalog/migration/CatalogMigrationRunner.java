package com.pml.catalog.migration;

import com.pml.catalog.config.CatalogIndexInitializer;
import com.pml.catalog.persistence.CatalogCollections;
import com.pml.shared.migration.ConflictingIndexRepair;
import com.pml.shared.migration.RedundantSourceDrop;
import com.pml.shared.migration.RetiredCollectionDrop;
import com.pml.shared.migration.MigrationLedger;
import com.pml.shared.migration.MigrationRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Catalog's data migrations, in dependency order.
 *
 * <h2>Why catalog needs its own runner</h2>
 * Booking and identity each have one. A migration bean with nothing to invoke it is the failure
 * {@link MigrationRunner} exists to prevent — a migration that compiles, tests green, and has
 * never executed against a database it did not create itself.
 *
 * @see MigrationRunner
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogMigrationRunner extends MigrationRunner {

    /** Separate from booking's and identity's, so each service's history reads on its own. */
    static final String LEDGER_COLLECTION = "catalog_migrations";

    private final ReactiveMongoTemplate mongoTemplate;

    /** The platform clock, handed to the ledger so its audit rows are testable. */
    private final Clock clock;

    private final CatalogCollectionRenameMigrationService collectionRenames;
    private final CatalogRegistryConformanceMigrationService registryConformance;
    private final com.pml.catalog.service.EventTierMirror tierMirror;
    private final ReferenceDataSeedMigrationService referenceSeeds;

    @Value("${catalog.migrations.enabled:true}")
    private boolean enabled;

    @Value("${catalog.migrations.fail-fast:true}")
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
        return "catalog-service";
    }

    @Override
    protected Map<String, Supplier<Mono<?>>> steps() {
        Map<String, Supplier<Mono<?>>> steps = new LinkedHashMap<>();

        // Catalog's reference-data seeder runs at startup and writes to
        // catalog_reference_data. This moves the pre-registry rows there first, so the
        // seeder's insert-never-update contract sees the existing rows rather than treating
        // an empty collection as a first run and re-seeding alongside them.
        steps.put("collection-registry-rename", collectionRenames::migrate);

        // The platform settings were shared under an unprefixed name by catalog, which writes
        // them, and identity, which reads the payment defaults. Catalog stays the only writer and
        // the table takes catalog's prefix; every other service reads it through shared-library.
        // Before the seeders, so the default document is not written into an empty new table
        // while the real one still sits under the old name.
        steps.put("platform-settings-rename", collectionRenames::migratePlatformSettings);

        // AFTER the rename: these filters name registry collections, and before the move the
        // documents are still under their old names where nothing here would match them.
        steps.put("registry-conformance-backfill", registryConformance::migrate);

        // The rename above refuses to move onto a target that already holds documents, because
        // that would destroy them — correct, and it leaves both collections in place. This
        // settles the pair, but only on evidence: the source goes only when every _id in it is
        // already in the target, and one unmatched document keeps the whole collection.
        steps.put("drop-redundant-reference-data",
                () -> new RedundantSourceDrop(mongoTemplate)
                        .dropIfRedundant("reference_data", CatalogCollections.REFERENCE_DATA));

        // LAST. A legacy index occupies the keys a declared index wants, so the server
        // refuses the declaration and IndexEnsurer reports a conflict — catalog's is the text
        // index on catalog_events, held under a hand-chosen name. Runs after the renames above,
        // because a rename carries the indexes along with the collection.
        // The suffix is the ledger's requirement, not decoration. A step name maps to one thing
        // that happened once, so a recorded row is immutable — and re-running a corrected step
        // under its old name would either be skipped as already applied or silently redefine
        // what that row means. The suffix says plainly that this is a second conformance pass,
        // made after the declarations themselves were corrected; the earlier row stays as the
        // record of what the earlier pass did.
        // `approval_notifications` is a notification collection catalog does not own —
        // notifications live in identity's identity_notifications. No class or repository in
        // this service reads it, so it is unreachable from the application; the count is
        // logged before it goes.
        steps.put("drop-retired-collections",
                () -> new RetiredCollectionDrop(mongoTemplate)
                        .drop(List.of("approval_notifications")));

        steps.put("index-registry-conformance-3",
                () -> new ConflictingIndexRepair(mongoTemplate)
                        .repair(CatalogIndexInitializer.specifications()));

        // The registry now also declares every index the model annotations used to create,
        // under the names they created them with, so a database built before is mostly already
        // conformant. Its own step because a recorded step is never re-run.
        steps.put("index-registry-conformance-4",
                () -> new ConflictingIndexRepair(mongoTemplate)
                        .repair(CatalogIndexInitializer.specifications()));

        // The discovery indexes were declared on `startsAt` and `cityId`, and the events carry
        // `eventDateTime` and, until now, no `cityId`: they indexed nothing. Their correctly keyed
        // replacements have new names; these go.
        steps.put("drop-discovery-indexes-on-absent-fields",
                () -> dropIndexes(CatalogCollections.EVENTS, List.of("idx_status_startsAt", "idx_categoryId_cityId_startsAt")));

        // The search index now weights the title above the description; the repair rebuilds it.
        steps.put("index-registry-conformance-5",
                () -> new ConflictingIndexRepair(mongoTemplate)
                        .repair(CatalogIndexInitializer.specifications()));


        // Reference data says what a value is, never how it is drawn: the seed no longer carries
        // colours, and the rows seeded before it lose theirs. Each application's design decides.
        steps.put("strip-reference-presentation",
                () -> new ReferencePresentationStrip(mongoTemplate).strip().map(n -> n + " rows cleared"));

        // Events now carry sales totals (sold tickets, gross sales, commission). Those written before
        // have the tiers' truth to recompute from and a zero where money was never reported.
        steps.put("event-sales-totals-backfill",
                () -> new EventSalesTotalsBackfill(mongoTemplate, tierMirror).run());

        // Reference data release 2 (ET-PLT-014-R12): the onboarding, access, communication and
        // reporting vocabularies, the corrected Zambian mobile-money prefixes, KYB document codes
        // aligned with identity, the 116 districts, and the country list from libphonenumber.
        // A later correction is a new step; this row is the record of what version 2 did.
        steps.put("reference-data-seed-v2", referenceSeeds::applyV2);

        return steps;
    }

    /** Drops the named indexes that exist; an absent one is already the desired state. */
    private Mono<String> dropIndexes(String collection, List<String> names) {
        return mongoTemplate.getCollection(collection)
                .flatMapMany(indexes -> reactor.core.publisher.Flux.fromIterable(names)
                        .concatMap(name -> Mono.from(indexes.dropIndex(name))
                                .thenReturn(name)
                                .onErrorResume(com.mongodb.MongoCommandException.class, absent -> Mono.empty())))
                .collectList()
                .map(dropped -> dropped.isEmpty() ? "none present" : "dropped " + dropped);
    }
}
