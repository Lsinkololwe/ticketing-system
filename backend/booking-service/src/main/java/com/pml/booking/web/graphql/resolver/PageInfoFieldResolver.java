package com.pml.booking.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.booking.web.graphql.dto.PageInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * Field Resolver for PageInfo type (cursor-based pagination).
 *
 * Resolves fields that need to be computed or aliased:
 * - totalElements: alias for totalCount
 * - totalPages: computed from totalCount and pageSize
 * - currentPage: not typically used in cursor pagination, returns null
 * - pageSize: not typically stored, returns null or default
 * - hasNext: alias for hasNextPage
 * - hasPrevious: alias for hasPreviousPage
 */
@Slf4j
@DgsComponent
public class PageInfoFieldResolver {

    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * Resolve PageInfo.totalElements - alias for totalCount.
     */
    @DgsData(parentType = "PageInfo", field = "totalElements")
    public Integer totalElements(DgsDataFetchingEnvironment dfe) {
        PageInfo info = dfe.getSource();
        return info.totalCount();
    }

    /**
     * Resolve PageInfo.totalPages - computed from totalCount and default page size.
     * Returns null if totalCount is not available.
     */
    @DgsData(parentType = "PageInfo", field = "totalPages")
    public Integer totalPages(DgsDataFetchingEnvironment dfe) {
        PageInfo info = dfe.getSource();
        Integer totalCount = info.totalCount();
        if (totalCount == null || totalCount == 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalCount / DEFAULT_PAGE_SIZE);
    }

    /**
     * Resolve PageInfo.currentPage - not typically used in cursor pagination.
     * Returns null as cursor pagination doesn't track page numbers.
     */
    @DgsData(parentType = "PageInfo", field = "currentPage")
    public Integer currentPage(DgsDataFetchingEnvironment dfe) {
        // Cursor pagination doesn't track page numbers
        return null;
    }

    /**
     * Resolve PageInfo.pageSize - returns default page size.
     */
    @DgsData(parentType = "PageInfo", field = "pageSize")
    public Integer pageSize(DgsDataFetchingEnvironment dfe) {
        return DEFAULT_PAGE_SIZE;
    }

    /**
     * Resolve PageInfo.hasNext - alias for hasNextPage.
     */
    @DgsData(parentType = "PageInfo", field = "hasNext")
    public Boolean hasNext(DgsDataFetchingEnvironment dfe) {
        PageInfo info = dfe.getSource();
        return info.hasNextPage();
    }

    /**
     * Resolve PageInfo.hasPrevious - alias for hasPreviousPage.
     */
    @DgsData(parentType = "PageInfo", field = "hasPrevious")
    public Boolean hasPrevious(DgsDataFetchingEnvironment dfe) {
        PageInfo info = dfe.getSource();
        return info.hasPreviousPage();
    }
}
