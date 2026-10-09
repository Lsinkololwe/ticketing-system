package com.pml.catalog.web.graphql.dto;

import com.pml.shared.graphql.PageSize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Input DTO for Spring Data Pageable pagination.
 * Used for admin dashboard table pagination.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageableInput {

    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer size = 20;

    @Builder.Default
    private String sortBy = "createdAt";

    @Builder.Default
    private SortDirection sortDirection = SortDirection.DESC;

    /**
     * Convert to Spring Data Pageable
     */
    /**
     * @throws com.pml.shared.graphql.PageSize.PageSizeExceeded if {@code size} is above the
     *         maximum — an over-limit page is refused rather than reduced, because
     *         a clamped page is a client that believes it has read everything
     */
    public Pageable toPageable() {
        int pageNum = page != null ? page : 0;
        int pageSize = PageSize.require(size);
        String sortField = sortBy != null ? sortBy : "createdAt";
        Sort.Direction direction = sortDirection == SortDirection.ASC
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;

        return PageRequest.of(pageNum, pageSize, Sort.by(direction, sortField));
    }

    public enum SortDirection {
        ASC,
        DESC
    }
}
