package com.pml.catalog.config;

import com.pml.shared.persistence.IndexEnsurer;
import com.pml.shared.persistence.IndexSpec;
import com.pml.catalog.persistence.CatalogCollections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Creates the indexes catalog-service's collections depend on.
 *
 * <h2>What the annotations do not give you</h2>
 * {@code @Indexed} produces one single-field index per annotated field and nothing else. Every
 * compound index below, every TTL, and the sparse-unique pairs are outside what it can express
 * — so before this class existed the platform had none of them, including
 * every compound index behind public discovery, without which the hottest query on the platform is a collection scan.
 *
 * <p>A unique index is a <b>constraint, not an optimisation: each one closes a
 * race the application cannot win by checking first</b>. A missing unique index does not make
 * anything slower. It permits the duplicate, quietly, under exactly the concurrency that makes
 * it matter.</p>
 *
 * <h2>One list, checked against a live database</h2>
 * The list is the single declaration of catalog's indexes —
 * {@code CatalogIndexRegistryTest} asserts every entry exists on a live database with the
 * uniqueness, sparseness and expiry declared here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogIndexInitializer {

    private final ReactiveMongoTemplate mongoTemplate;

    /** Every index catalog-service's collections carry. */
    public static List<IndexSpec> specifications() {
        return List.of(
                // **hot** — public discovery. Every discovery filter combination starts with the
                // status and sorts by the start time, so each one is served by one of these four.
                IndexSpec.on(CatalogCollections.EVENTS, "idx_status_eventDateTime")
                        .asc("status")
                        .asc("eventDateTime")
                        .build(),
                IndexSpec.on(CatalogCollections.EVENTS, "idx_status_categoryId_eventDateTime")
                        .asc("status")
                        .asc("categoryId")
                        .asc("eventDateTime")
                        .build(),
                IndexSpec.on(CatalogCollections.EVENTS, "idx_status_cityId_eventDateTime")
                        .asc("status")
                        .asc("cityId")
                        .asc("eventDateTime")
                        .build(),
                IndexSpec.on(CatalogCollections.EVENTS, "idx_status_categoryId_cityId_eventDateTime")
                        .asc("status")
                        .asc("categoryId")
                        .asc("cityId")
                        .asc("eventDateTime")
                        .build(),

                // the other orders of the public feed: newest, by price either way, most sold
                IndexSpec.on(CatalogCollections.EVENTS, "idx_status_publishedAt")
                        .asc("status")
                        .desc("publishedAt")
                        .build(),
                IndexSpec.on(CatalogCollections.EVENTS, "idx_status_lowestTicketPrice_eventDateTime")
                        .asc("status")
                        .asc("lowestTicketPrice")
                        .asc("eventDateTime")
                        .build(),
                IndexSpec.on(CatalogCollections.EVENTS, "idx_status_lowestTicketPriceDesc_eventDateTime")
                        .asc("status")
                        .desc("lowestTicketPrice")
                        .asc("eventDateTime")
                        .build(),
                IndexSpec.on(CatalogCollections.EVENTS, "idx_status_soldTickets_eventDateTime")
                        .asc("status")
                        .desc("soldTickets")
                        .asc("eventDateTime")
                        .build(),

                // the organizer dashboard
                IndexSpec.on(CatalogCollections.EVENTS, "idx_organizationId_status")
                        .asc("organizationId")
                        .asc("status")
                        .build(),
                // an organization's events newest first, paged by cursor
                IndexSpec.on(CatalogCollections.EVENTS, "idx_organizationId_createdAt")
                        .asc("organizationId")
                        .desc("createdAt")
                        .desc("_id")
                        .build(),

                // images: the file a URL names, an organization's library, the moderation queue, the stock library
                IndexSpec.on(CatalogCollections.MEDIA, "uniq_fileKey")
                        .asc("fileKey")
                        .unique()
                        .build(),
                IndexSpec.on(CatalogCollections.MEDIA, "idx_organizationId_createdAt")
                        .asc("organizationId")
                        .desc("createdAt")
                        .desc("_id")
                        .build(),
                IndexSpec.on(CatalogCollections.MEDIA, "idx_kind_status_createdAt")
                        .asc("kind")
                        .asc("status")
                        .desc("createdAt")
                        .build(),
                IndexSpec.on(CatalogCollections.MEDIA, "idx_stock_purpose_categoryCode_active")
                        .asc("kind")
                        .asc("purpose")
                        .asc("categoryCode")
                        .asc("active")
                        .build(),

                // search: a word in the title counts five times one in the description
                IndexSpec.on(CatalogCollections.EVENTS, "idx_text_search")
                        .text("title", 5)
                        .text("description", 1)
                        .build(),

                // the on-sale query
                IndexSpec.on(CatalogCollections.TICKET_TIERS, "idx_eventId_salesStartAt")
                        .asc("eventId")
                        .asc("salesStartAt")
                        .build(),

                // venue lookup
                IndexSpec.on(CatalogCollections.LOCATIONS, "idx_cityId")
                        .asc("cityId")
                        .build(),

                // **hot** — the drain's claim, every poll
                IndexSpec.on(CatalogCollections.OUTBOX, "idx_status_stagedAt")
                        .asc("status")
                        .asc("stagedAt")
                        .build(),

                // One escalation per level, enforced at the database rather than
                // by a read-then-write an operator's concurrent SLA sweep can still race.
                IndexSpec.on(CatalogCollections.APPROVAL_ESCALATIONS, "uniq_eventId_level")
                        .asc("eventId")
                        .asc("level")
                        .unique()
                        .build(),

                // ── Lookups and constraints once declared as annotations on the model classes ──
                // Names are the ones the annotations produced, so a database that already has
                // them reports each as present rather than building it again.
                // catalog_approval_escalations
                IndexSpec.on(CatalogCollections.APPROVAL_ESCALATIONS, "escalatedTo").asc("escalatedTo").build(),
                IndexSpec.on(CatalogCollections.APPROVAL_ESCALATIONS, "eventId").asc("eventId").build(),
                IndexSpec.on(CatalogCollections.APPROVAL_ESCALATIONS, "nextReminderAt").asc("nextReminderAt").build(),
                IndexSpec.on(CatalogCollections.APPROVAL_ESCALATIONS, "status_escalatedTo_idx").asc("status").asc("escalatedTo").build(),
                IndexSpec.on(CatalogCollections.APPROVAL_ESCALATIONS, "status").asc("status").build(),

                // catalog_approval_timelines
                IndexSpec.on(CatalogCollections.APPROVAL_TIMELINES, "assignedReviewerId").asc("assignedReviewerId").build(),
                IndexSpec.on(CatalogCollections.APPROVAL_TIMELINES, "status_deadline_idx").asc("currentStatus").asc("slaDeadline").build(),
                IndexSpec.on(CatalogCollections.APPROVAL_TIMELINES, "currentStatus").asc("currentStatus").build(),
                IndexSpec.on(CatalogCollections.APPROVAL_TIMELINES, "eventId").asc("eventId").unique().build(),
                IndexSpec.on(CatalogCollections.APPROVAL_TIMELINES, "hasActiveEscalation").asc("hasActiveEscalation").build(),
                IndexSpec.on(CatalogCollections.APPROVAL_TIMELINES, "isOverdue").asc("isOverdue").build(),
                IndexSpec.on(CatalogCollections.APPROVAL_TIMELINES, "organizer_status_idx").asc("organizerId").asc("currentStatus").build(),
                IndexSpec.on(CatalogCollections.APPROVAL_TIMELINES, "organizerId").asc("organizerId").build(),
                IndexSpec.on(CatalogCollections.APPROVAL_TIMELINES, "slaDeadline").asc("slaDeadline").build(),
                IndexSpec.on(CatalogCollections.APPROVAL_TIMELINES, "submittedAt").asc("submittedAt").build(),

                // catalog_categories
                IndexSpec.on(CatalogCollections.CATEGORIES, "code").asc("code").unique().build(),
                IndexSpec.on(CatalogCollections.CATEGORIES, "name").asc("name").unique().build(),

                // catalog_cities
                IndexSpec.on(CatalogCollections.CITIES, "country").asc("country").build(),
                IndexSpec.on(CatalogCollections.CITIES, "name").asc("name").build(),
                IndexSpec.on(CatalogCollections.CITIES, "provinceId").asc("provinceId").build(),

                // catalog_events
                IndexSpec.on(CatalogCollections.EVENTS, "assignedReviewerId").asc("assignedReviewerId").build(),
                IndexSpec.on(CatalogCollections.EVENTS, "categoryId").asc("categoryId").build(),
                IndexSpec.on(CatalogCollections.EVENTS, "cityName").asc("cityName").build(),
                IndexSpec.on(CatalogCollections.EVENTS, "createdBy").asc("createdBy").build(),
                IndexSpec.on(CatalogCollections.EVENTS, "deletedBy").asc("deletedBy").build(),
                IndexSpec.on(CatalogCollections.EVENTS, "eventDateTime").asc("eventDateTime").build(),
                IndexSpec.on(CatalogCollections.EVENTS, "isDeleted").asc("isDeleted").build(),
                IndexSpec.on(CatalogCollections.EVENTS, "isFreeEvent").asc("isFreeEvent").build(),
                IndexSpec.on(CatalogCollections.EVENTS, "organizationId").asc("organizationId").build(),
                IndexSpec.on(CatalogCollections.EVENTS, "organizerId").asc("organizerId").build(),
                IndexSpec.on(CatalogCollections.EVENTS, "status").asc("status").build(),
                IndexSpec.on(CatalogCollections.EVENTS, "updatedBy").asc("updatedBy").build(),

                // catalog_provinces
                IndexSpec.on(CatalogCollections.PROVINCES, "country").asc("country").build(),
                IndexSpec.on(CatalogCollections.PROVINCES, "name").asc("name").unique().build(),

                // catalog_reference_data
                IndexSpec.on(CatalogCollections.REFERENCE_DATA, "type_code_unique").asc("type").asc("code").unique().build(),
                IndexSpec.on(CatalogCollections.REFERENCE_DATA, "type_active_order").asc("type").asc("isActive").asc("displayOrder").build(),
                IndexSpec.on(CatalogCollections.REFERENCE_DATA, "type_parent").asc("type").asc("parentCode").build(),

                // catalog_ticket_tiers
                IndexSpec.on(CatalogCollections.TICKET_TIERS, "event_code_idx").asc("eventId").asc("code").unique().build(),
                IndexSpec.on(CatalogCollections.TICKET_TIERS, "event_active_sort_idx").asc("eventId").asc("isActive").asc("sortOrder").build(),
                IndexSpec.on(CatalogCollections.TICKET_TIERS, "eventId").asc("eventId").build(),
                IndexSpec.on(CatalogCollections.TICKET_TIERS, "organizationId").asc("organizationId").build());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        IndexEnsurer.Report report = new IndexEnsurer(mongoTemplate)
                .ensure(specifications())
                // Boot-time work, so blocking the main thread is acceptable: nothing
                // may serve traffic against a collection whose constraints are absent.
                .block();

        if (report != null && !report.isClean()) {
            log.error("catalog-service index registry is not satisfied: {}", report);
        }
    }
}
