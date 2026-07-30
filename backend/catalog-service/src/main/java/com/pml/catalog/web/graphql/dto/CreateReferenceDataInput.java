package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.enums.ReferenceType;

import java.util.Map;

/**
 * Input for creating a reference-data row. {@code metadata} is a JSON object whose required keys
 * depend on {@code type} (see {@code ReferenceMetadataValidator}).
 */
public record CreateReferenceDataInput(
        ReferenceType type,
        String code,
        String name,
        String description,
        ReferenceType parentType,
        String parentCode,
        Integer displayOrder,
        Boolean isActive,
        Map<String, Object> metadata
) {}
