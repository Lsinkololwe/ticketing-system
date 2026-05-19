package com.pml.catalog.dto;

import com.pml.catalog.domain.model.ApprovalTimeline;

import java.util.List;

/**
 * Offset-based pagination result for ApprovalTimelines.
 * Matches the GraphQL schema ApprovalTimelineOffsetPage type.
 */
public record ApprovalTimelineOffsetPage(
        List<ApprovalTimeline> content,
        int pageNumber,
        int pageSize,
        int totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
    /**
     * Create an ApprovalTimelineOffsetPage from pagination parameters.
     */
    public static ApprovalTimelineOffsetPage of(
            List<ApprovalTimeline> content,
            int pageNumber,
            int pageSize,
            long totalElements
    ) {
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        return new ApprovalTimelineOffsetPage(
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
     * Create an empty ApprovalTimelineOffsetPage.
     */
    public static ApprovalTimelineOffsetPage empty(int pageNumber, int pageSize) {
        return new ApprovalTimelineOffsetPage(
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
