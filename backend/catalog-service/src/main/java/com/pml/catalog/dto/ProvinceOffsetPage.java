package com.pml.catalog.dto;

import com.pml.catalog.domain.model.Province;

import java.util.List;

/**
 * Offset-based pagination result for Provinces.
 * Matches the GraphQL schema ProvinceOffsetPage type.
 */
public record ProvinceOffsetPage(
        List<Province> content,
        int pageNumber,
        int pageSize,
        int totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
    /**
     * Create a ProvinceOffsetPage from pagination parameters.
     */
    public static ProvinceOffsetPage of(
            List<Province> content,
            int pageNumber,
            int pageSize,
            int totalElements
    ) {
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        return new ProvinceOffsetPage(
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
     * Create an empty ProvinceOffsetPage.
     */
    public static ProvinceOffsetPage empty(int pageNumber, int pageSize) {
        return new ProvinceOffsetPage(
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
