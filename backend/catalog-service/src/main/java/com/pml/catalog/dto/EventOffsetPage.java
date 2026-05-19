package com.pml.catalog.dto;

import com.pml.catalog.domain.model.Event;

import java.util.List;

/**
 * Offset-based pagination result for Events.
 * Matches the GraphQL schema EventOffsetPage type.
 */
public record EventOffsetPage(
        List<Event> content,
        int pageNumber,
        int pageSize,
        int totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
    /**
     * Create an EventOffsetPage from pagination parameters.
     */
    public static EventOffsetPage of(
            List<Event> content,
            int pageNumber,
            int pageSize,
            int totalElements
    ) {
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        return new EventOffsetPage(
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
     * Create an empty EventOffsetPage.
     */
    public static EventOffsetPage empty(int pageNumber, int pageSize) {
        return new EventOffsetPage(
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
