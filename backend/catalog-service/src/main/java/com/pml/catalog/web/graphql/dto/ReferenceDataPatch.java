package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.shared.constants.WorkflowSemantic;

import java.time.Instant;
import java.util.List;
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
        Instant effectiveFrom,
        Instant effectiveTo,
        Map<String, Object> metadata,

        /**
         * What this value MEANS to the code.
         *
         * <p>This record used to omit it, and the resolver mapped it to nothing.
         * An administrator correcting a wrong semantic — the field that decides
         * whether a record moves at all — received "updated successfully" and
         * changed nothing.
         */
        WorkflowSemantic semantic,

        /** Codes this value may move to. Empty means unconstrained. */
        List<String> allowedTransitions
) {}
