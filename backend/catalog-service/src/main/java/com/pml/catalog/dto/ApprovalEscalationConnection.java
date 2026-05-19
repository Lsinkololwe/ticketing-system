package com.pml.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Connection type for ApprovalEscalation cursor pagination.
 * Follows the Relay Cursor Connections Specification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalEscalationConnection {

    /**
     * List of edges containing escalations and cursors
     */
    @Builder.Default
    private List<ApprovalEscalationEdge> edges = new ArrayList<>();

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
    public static ApprovalEscalationConnection empty() {
        return ApprovalEscalationConnection.builder()
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
