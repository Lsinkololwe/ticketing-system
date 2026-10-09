package com.pml.catalog.migration;

import com.pml.catalog.persistence.CatalogCollections;
import com.pml.shared.migration.CollectionRenameMigration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Moves catalog's collections onto their service-prefixed names.
 *
 * <h2>Why this has to run</h2>
 * The {@code @Document} declarations already name {@code catalog_events} and its siblings. On an
 * environment that has not been migrated the documents are still under {@code events}, and
 * nothing fails: the service reads a collection that exists but is empty, so discovery returns
 * no events and the organizer dashboard reports zero. A missing migration here looks exactly
 * like a platform with no data on it.
 *
 * <h2>The source names are literals on purpose</h2>
 * They have to be. A constant for the old name would be a constant nobody may ever change, and
 * the moment someone "tidied" it to match {@link CatalogCollections} the migration would become
 * a rename of a collection onto itself. Written out, they are visibly historical.
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class CatalogCollectionRenameMigrationService {

    /** Unprefixed name → the name {@link CatalogCollections} gives it. */
    private static final Map<String, String> RENAMES = CollectionRenameMigration.table(
            "events", CatalogCollections.EVENTS,
            "ticket_tiers", CatalogCollections.TICKET_TIERS,
            "locations", CatalogCollections.LOCATIONS,
            "cities", CatalogCollections.CITIES,
            "provinces", CatalogCollections.PROVINCES,
            // Not a prefix change: the collection is catalog_categories, so prefixing
            // `event_categories` alone would produce a collection nothing reads.
            "event_categories", CatalogCollections.CATEGORIES,
            "reference_data", CatalogCollections.REFERENCE_DATA,
            "approval_timelines", CatalogCollections.APPROVAL_TIMELINES,
            "approval_escalations", CatalogCollections.APPROVAL_ESCALATIONS);

    /**
     * The platform settings, moved on their own because the table above is a recorded step that
     * has already run: an entry added to it now would never execute where the data is.
     */
    private static final Map<String, String> PLATFORM_SETTINGS = CollectionRenameMigration.table(
            "platform_configuration", CatalogCollections.PLATFORM_CONFIGURATION);

    private final ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider;

    public Mono<CollectionRenameMigration.Result> migrate() {
        return new CollectionRenameMigration(mongoTemplateProvider.getObject()).migrate(RENAMES);
    }

    public Mono<CollectionRenameMigration.Result> migratePlatformSettings() {
        return new CollectionRenameMigration(mongoTemplateProvider.getObject()).migrate(PLATFORM_SETTINGS);
    }
}
