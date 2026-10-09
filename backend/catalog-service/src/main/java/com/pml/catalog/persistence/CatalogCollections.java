package com.pml.catalog.persistence;

/**
 * The collection names this service owns — a closed set; a collection not named here is not
 * catalog's to write.
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
 * Only this service's collections are named here. The {@code catalog_} prefix is the only
 * place write-ownership is recorded, so a constant for another service's collection in this
 * file would be a second writer appearing without anyone deciding on one.
 */
public final class CatalogCollections {

    private CatalogCollections() {
    }

    /** The event, its state, schedule and organizer · versioned */
    public static final String EVENTS = "catalog_events";

    /** Tier definition — name, price, capacity, sales window, status · versioned */
    public static final String TICKET_TIERS = "catalog_ticket_tiers";

    /** Venues */
    public static final String LOCATIONS = "catalog_locations";

    /** Reference: cities */
    public static final String CITIES = "catalog_cities";

    /** Reference: provinces */
    public static final String PROVINCES = "catalog_provinces";

    /** Event categories */
    public static final String CATEGORIES = "catalog_categories";

    /** Typed lookup lists and workflow statuses, one row per (type, code) */
    public static final String REFERENCE_DATA = "catalog_reference_data";

    /**
     * Platform settings — one table for the whole platform, written only by catalog and read
     * directly by the other services, the same arrangement as the reference data.
     */
    public static final String PLATFORM_CONFIGURATION = "catalog_platform_configuration";

    /** The audit of an event's approval steps */
    public static final String APPROVAL_TIMELINES = "catalog_approval_timelines";

    /** SLA escalation state */
    public static final String APPROVAL_ESCALATIONS = "catalog_approval_escalations";

    /** Precomputed catalog statistics — events by city, by category, growth */
    public static final String STATISTICS_ROLLUPS = "catalog_statistics_rollups";

    /** This service's document-version backfills */
    public static final String MIGRATION_RUNS = "catalog_migration_runs";

    /** Uploaded images: organizers' own, and the platform's stock library · versioned */
    public static final String MEDIA = "catalog_media";

    /** Staged cross-service events, written in the business transaction */
    public static final String OUTBOX = "catalog_outbox";

}
