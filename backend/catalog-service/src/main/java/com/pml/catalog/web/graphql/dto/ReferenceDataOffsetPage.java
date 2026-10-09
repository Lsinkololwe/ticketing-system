package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.model.ReferenceData;

import java.util.List;

/**
 * Offset-based pagination result for ReferenceData admin tables.
 * Matches the GraphQL schema {@code ReferenceDataOffsetPage} type.
 */
public record ReferenceDataOffsetPage(
        List<ReferenceData> content,
        int pageNumber,
        int pageSize,
        int totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
    public static ReferenceDataOffsetPage of(
            List<ReferenceData> content,
            int pageNumber,
            int pageSize,
            long totalElements
    ) {
        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);
        return new ReferenceDataOffsetPage(
                content,
                pageNumber,
                pageSize,
                (int) totalElements,
                totalPages,
                pageNumber < totalPages - 1,
                pageNumber > 0
        );
    }

    public static ReferenceDataOffsetPage empty(int pageNumber, int pageSize) {
        return new ReferenceDataOffsetPage(List.of(), pageNumber, pageSize, 0, 0, false, false);
    }
}
