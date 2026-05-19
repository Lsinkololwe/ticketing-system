package com.pml.catalog.dto;

import com.pml.catalog.domain.model.City;

import java.util.List;

/**
 * Offset-based pagination result for Cities.
 * Matches the GraphQL schema CityOffsetPage type.
 */
public record CityOffsetPage(
        List<City> content,
        int pageNumber,
        int pageSize,
        int totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
    /**
     * Create a CityOffsetPage from pagination parameters.
     */
    public static CityOffsetPage of(
            List<City> content,
            int pageNumber,
            int pageSize,
            int totalElements
    ) {
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        return new CityOffsetPage(
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
     * Create an empty CityOffsetPage.
     */
    public static CityOffsetPage empty(int pageNumber, int pageSize) {
        return new CityOffsetPage(
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
