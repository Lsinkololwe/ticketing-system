package com.pml.booking.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.booking.web.graphql.dto.PaginationInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * Field Resolver for PaginationInfo type.
 *
 * Resolves fields with different naming conventions between DTO and schema:
 * - pageNumber: alias for currentPage
 * - totalElements: alias for totalCount
 * - hasNext: alias for hasNextPage
 * - hasPrevious: alias for hasPreviousPage
 */
@Slf4j
@DgsComponent
public class PaginationInfoFieldResolver {

    /**
     * Resolve PaginationInfo.pageNumber - alias for currentPage.
     */
    @DgsData(parentType = "PaginationInfo", field = "pageNumber")
    public Integer pageNumber(DgsDataFetchingEnvironment dfe) {
        PaginationInfo info = dfe.getSource();
        return info.currentPage();
    }

    /**
     * Resolve PaginationInfo.totalElements - alias for totalCount.
     */
    @DgsData(parentType = "PaginationInfo", field = "totalElements")
    public Integer totalElements(DgsDataFetchingEnvironment dfe) {
        PaginationInfo info = dfe.getSource();
        return info.totalCount();
    }

    /**
     * Resolve PaginationInfo.hasNext - alias for hasNextPage.
     */
    @DgsData(parentType = "PaginationInfo", field = "hasNext")
    public Boolean hasNext(DgsDataFetchingEnvironment dfe) {
        PaginationInfo info = dfe.getSource();
        return info.hasNextPage();
    }

    /**
     * Resolve PaginationInfo.hasPrevious - alias for hasPreviousPage.
     */
    @DgsData(parentType = "PaginationInfo", field = "hasPrevious")
    public Boolean hasPrevious(DgsDataFetchingEnvironment dfe) {
        PaginationInfo info = dfe.getSource();
        return info.hasPreviousPage();
    }
}
