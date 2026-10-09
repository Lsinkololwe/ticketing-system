package com.pml.catalog.domain.enums;

/**
 * Where an uploaded image stands with moderation. Only {@code ACTIVE} and {@code FLAGGED} assets
 * are served; a {@code REMOVED} asset is kept, with its reason, so an administrator can restore it.
 */
public enum MediaStatus {
    ACTIVE,
    FLAGGED,
    REMOVED
}
