package com.pml.catalog.migration;

import com.pml.catalog.persistence.CatalogCollections;
import com.pml.shared.constants.Money;
import com.pml.shared.migration.DocumentBackfill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Brings catalog-service's stored documents up to the shape the current model requires.
 *
 * <h2>What a live database is missing without this</h2>
 * Each entry below closes a gap that a code change opened and cannot close by itself:
 *
 * <ul>
 *   <li><b>{@code _class}</b> — {@code @TypeAlias} changed what new writes store; existing
 *       documents keep the fully-qualified class name and stop deserialising the day that class
 *       moves package.</li>
 *   <li><b>{@code version}</b> — a document with {@code @Version} and no {@code version} field
 *       is treated as new, so optimistic locking guards nothing until something writes it.</li>
 *   <li><b>{@code currency}</b> — {@code @Builder.Default} runs when an object is built, not when
 *       one is read, so amounts written earlier deserialise with a null currency.</li>
 * </ul>
 *
 * <p>Runs after the collection renames: these filters name registry collections, and before the
 * rename the documents are still under their old names where nothing here would match them.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class CatalogRegistryConformanceMigrationService {

    private final ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider;

    public Mono<DocumentBackfill.Result> migrate() {
        return new DocumentBackfill(mongoTemplateProvider.getObject())
                // _class → the alias each document now declares
                .rewriteClassTo(CatalogCollections.APPROVAL_ESCALATIONS,
                        "com.pml.catalog.domain.model.ApprovalEscalation", "approval_escalations")
                .rewriteClassTo(CatalogCollections.APPROVAL_TIMELINES,
                        "com.pml.catalog.domain.model.ApprovalTimeline", "approval_timelines")
                .rewriteClassTo(CatalogCollections.CATEGORIES,
                        "com.pml.catalog.domain.model.EventCategory", "categories")
                .rewriteClassTo(CatalogCollections.CITIES,
                        "com.pml.catalog.domain.model.City", "cities")
                .rewriteClassTo(CatalogCollections.EVENTS,
                        "com.pml.catalog.domain.model.Event", "events")
                .rewriteClassTo(CatalogCollections.LOCATIONS,
                        "com.pml.catalog.domain.model.Location", "locations")
                .rewriteClassTo(CatalogCollections.PLATFORM_CONFIGURATION,
                        "com.pml.catalog.domain.model.PlatformConfiguration", "platformConfiguration")
                .rewriteClassTo(CatalogCollections.PROVINCES,
                        "com.pml.catalog.domain.model.Province", "provinces")
                .rewriteClassTo(CatalogCollections.REFERENCE_DATA,
                        "com.pml.catalog.domain.model.ReferenceData", "reference_data")
                .rewriteClassTo(CatalogCollections.TICKET_TIERS,
                        "com.pml.catalog.domain.model.TicketTier", "ticket_tiers")

                // optimistic locking needs a version to start from
                .setWhereMissing(CatalogCollections.EVENTS, "version", 0L)
                .setWhereMissing(CatalogCollections.TICKET_TIERS, "version", 0L)

                // Every monetary field has a currency sibling
                .setWhereMissing(CatalogCollections.EVENTS, "currency", Money.DEFAULT_CURRENCY)
                .setWhereMissing(CatalogCollections.TICKET_TIERS, "currency", Money.DEFAULT_CURRENCY)
                .run();
    }
}
