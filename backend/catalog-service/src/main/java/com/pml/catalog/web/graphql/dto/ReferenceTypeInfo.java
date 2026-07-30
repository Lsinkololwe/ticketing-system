package com.pml.catalog.web.graphql.dto;

import java.util.List;

/**
 * Describes one {@code ReferenceType} for the admin UI type picker — its enum name, human label,
 * logical group, and the metadata keys the create/edit form must render. Lets a single admin screen
 * drive every type off the {@code referenceTypes} query with no hardcoded client list.
 */
public record ReferenceTypeInfo(
        String type,
        String label,
        String group,
        String groupLabel,
        List<String> requiredMetadataKeys
) {}
