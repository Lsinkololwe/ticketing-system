package com.pml.booking.web.graphql.dto.organizer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * Paginated response for organizer transactions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizerTransactionOffsetPage {

    /**
     * List of transactions on this page
     */
    @Builder.Default
    private List<OrganizerTransaction> content = Collections.emptyList();

    /**
     * Total number of transactions
     */
    @Builder.Default
    private Integer totalElements = 0;

    /**
     * Total number of pages
     */
    @Builder.Default
    private Integer totalPages = 0;

    /**
     * Current page number (0-indexed)
     */
    @Builder.Default
    private Integer page = 0;

    /**
     * Page size
     */
    @Builder.Default
    private Integer size = 20;

    /**
     * Whether there is a next page
     */
    @Builder.Default
    private Boolean hasNext = false;

    /**
     * Whether there is a previous page
     */
    @Builder.Default
    private Boolean hasPrevious = false;

    /**
     * Creates an empty page
     */
    public static OrganizerTransactionOffsetPage empty() {
        return OrganizerTransactionOffsetPage.builder()
                .content(Collections.emptyList())
                .totalElements(0)
                .totalPages(0)
                .page(0)
                .size(20)
                .hasNext(false)
                .hasPrevious(false)
                .build();
    }
}
