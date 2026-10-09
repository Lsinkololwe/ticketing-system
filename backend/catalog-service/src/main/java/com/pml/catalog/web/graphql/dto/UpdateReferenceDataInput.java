package com.pml.catalog.web.graphql.dto;


import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.shared.constants.WorkflowSemantic;

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
        Map<String, Object> metadata,

        /**
         * What this value MEANS to the code.
         *
         * <p>Required when {@code type} is a workflow type. Branches read the
         * semantic and never the {@code code}, which is what lets an
         * administrator add a status without a deployment — and what stops them
         * adding one no branch recognises.
         */
        WorkflowSemantic semantic,

        /** Codes this value may move to. Empty means unconstrained. */
        java.util.List<String> allowedTransitions,

        /** Temporal validity, used by TAX_RATE. Previously unreachable: the resolver
         * hardcoded null for both, so a rate could be created with a validity window
         * and never corrected. */
        java.time.Instant effectiveFrom,
        java.time.Instant effectiveTo
) {}
