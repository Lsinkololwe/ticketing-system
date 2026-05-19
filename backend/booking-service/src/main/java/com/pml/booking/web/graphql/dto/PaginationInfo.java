package com.pml.booking.web.graphql.dto;

public record PaginationInfo(
        int totalCount,
        int pageSize,
        int currentPage,
        int totalPages,
        boolean hasNextPage,
        boolean hasPreviousPage
) {}
