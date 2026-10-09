package com.pml.identity.web.graphql.dto.pagination;

import java.util.List;

/**
 * One page cut from a list already in memory, with the {@link PageInfo} every offset page carries.
 * Used by the admin tables whose rows are derived (so there is no collection to page in the
 * database); a derived list is bounded by the number of organizations or accounts, not by traffic.
 */
public record OffsetSlice<T>(List<T> content, PageInfo pageInfo) {

    public static <T> OffsetSlice<T> of(List<T> all, OffsetPaginationInput pagination) {
        OffsetPaginationInput p = pagination != null ? pagination : OffsetPaginationInput.defaults();
        int limit = p.getLimit();
        int offset = p.getOffset();
        int total = all.size();
        int pages = (int) Math.ceil((double) total / limit);
        List<T> content = all.stream().skip(offset).limit(limit).toList();
        return new OffsetSlice<>(content,
                PageInfo.forOffset(total, limit, p.page(), pages, offset + limit < total, p.page() > 0));
    }
}
