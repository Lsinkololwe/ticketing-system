package com.pml.booking.web.graphql.dto;

/**
 * Input for resolving a reconciliation item.
 *
 * @param externalId External ID of the item to resolve
 * @param resolution Resolution description/action taken
 *
 * @since 1.0.0
 */
public record ResolveReconciliationItemInput(
    String externalId,
    String resolution
) {
    /**
     * Constructor with validation.
     */
    public ResolveReconciliationItemInput {
        if (externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("External ID is required");
        }
        if (resolution == null || resolution.isBlank()) {
            throw new IllegalArgumentException("Resolution is required");
        }
    }
}
