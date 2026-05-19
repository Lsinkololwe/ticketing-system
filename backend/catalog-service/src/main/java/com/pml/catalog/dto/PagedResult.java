package com.pml.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic paged result DTO for admin dashboard tables.
 * Wraps Spring Data Page information for GraphQL responses.
 *
 * @param <T> The type of content in the page
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedResult<T> {

    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    /**
     * Create PagedResult from Spring Data reactive page info
     */
    public static <T> PagedResult<T> of(
            List<T> content,
            int pageNumber,
            int pageSize,
            long totalElements) {

        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        return PagedResult.<T>builder()
                .content(content)
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .hasNext(pageNumber < totalPages - 1)
                .hasPrevious(pageNumber > 0)
                .build();
    }
}
