package com.pml.catalog.dto;

import com.pml.catalog.domain.enums.ReferenceType;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Nullable patch for partial updates of a {@code ReferenceData} row. A {@code null} field means
 * "leave unchanged" — this preserves partial-update semantics for the primitive fields
 * ({@code displayOrder}, {@code isActive}) that the domain model cannot represent as null.
 */
public record ReferenceDataPatch(
        String name,
        String description,
        ReferenceType parentType,
        String parentCode,
        Integer displayOrder,
        Boolean isActive,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        Map<String, Object> metadata
) {}
