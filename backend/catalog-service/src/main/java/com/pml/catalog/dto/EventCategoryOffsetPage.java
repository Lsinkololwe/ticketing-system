package com.pml.catalog.dto;

import com.pml.catalog.domain.model.EventCategory;

import java.util.List;

/**
 * Offset-based pagination result for EventCategories.
 * Matches the GraphQL schema EventCategoryOffsetPage type.
 */
public record EventCategoryOffsetPage(
        List<EventCategory> content,
        int pageNumber,
        int pageSize,
        int totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
    /**
     * Create an EventCategoryOffsetPage from pagination parameters.
     */
    public static EventCategoryOffsetPage of(
            List<EventCategory> content,
            int pageNumber,
            int pageSize,
            int totalElements
    ) {
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        return new EventCategoryOffsetPage(
                content,
                pageNumber,
                pageSize,
                totalElements,
                totalPages,
                pageNumber < totalPages - 1,
                pageNumber > 0
        );
    }

    /**
     * Create an empty EventCategoryOffsetPage.
     */
    public static EventCategoryOffsetPage empty(int pageNumber, int pageSize) {
        return new EventCategoryOffsetPage(
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
