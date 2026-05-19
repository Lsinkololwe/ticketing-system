package com.pml.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Connection type for ApprovalTimeline cursor pagination.
 * Follows the Relay Cursor Connections Specification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalTimelineConnection {

    /**
     * List of edges containing timelines and cursors
     */
    @Builder.Default
    private List<ApprovalTimelineEdge> edges = new ArrayList<>();

    /**
     * Pagination metadata
     */
    private PageInfo pageInfo;

    /**
     * Total count of items (optional, but useful for UI)
     */
    private Integer totalCount;

    /**
     * Create an empty connection
     */
    public static ApprovalTimelineConnection empty() {
        return ApprovalTimelineConnection.builder()
                .edges(new ArrayList<>())
                .pageInfo(PageInfo.builder()
                        .hasNextPage(false)
                        .hasPreviousPage(false)
                        .startCursor(null)
                        .endCursor(null)
                        .build())
                .totalCount(0)
                .build();
    }
}
