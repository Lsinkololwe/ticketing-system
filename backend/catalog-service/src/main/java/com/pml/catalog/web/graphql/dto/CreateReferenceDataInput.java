package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.shared.constants.WorkflowSemantic;

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
        java.util.List<String> allowedTransitions
) {}
