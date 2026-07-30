package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.enums.ReferenceType;

import java.util.Map;

/**
 * Input for updating a reference-data row. All fields optional; only non-null fields are applied.
 * {@code type} and {@code code} are immutable and therefore not updatable here.
 */
public record UpdateReferenceDataInput(
        String name,
        String description,
        ReferenceType parentType,
        String parentCode,
        Integer displayOrder,
        Boolean isActive,
        Map<String, Object> metadata
) {}
