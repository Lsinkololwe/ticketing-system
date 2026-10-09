package com.pml.catalog.domain.enums;

/** The orders the public feed offers. Each is served from an index of {@code catalog_events}. */
public enum EventDiscoverySort {
    /** Starts first; the default. */
    SOONEST,
    /** Published most recently. */
    NEWEST,
    PRICE_ASC,
    PRICE_DESC,
    /** Most tickets sold. */
    POPULAR
}
