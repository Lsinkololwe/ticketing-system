package com.pml.catalog.dto;

import com.pml.catalog.domain.model.ApprovalEscalation;

import java.util.List;

/**
 * Offset-based pagination result for ApprovalEscalations.
 * Matches the GraphQL schema ApprovalEscalationOffsetPage type.
 */
public record ApprovalEscalationOffsetPage(
        List<ApprovalEscalation> content,
        int pageNumber,
        int pageSize,
        int totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
    /**
     * Create an ApprovalEscalationOffsetPage from pagination parameters.
     */
    public static ApprovalEscalationOffsetPage of(
            List<ApprovalEscalation> content,
            int pageNumber,
            int pageSize,
            long totalElements
    ) {
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        return new ApprovalEscalationOffsetPage(
                content,
                pageNumber,
                pageSize,
                (int) totalElements,
                totalPages,
                pageNumber < totalPages - 1,
                pageNumber > 0
        );
    }

    /**
     * Create an empty ApprovalEscalationOffsetPage.
     */
    public static ApprovalEscalationOffsetPage empty(int pageNumber, int pageSize) {
        return new ApprovalEscalationOffsetPage(
                List.of(),
                pageNumber,
                pageSize,
                0,
                0,
                false,
                false
        );
    }
}
