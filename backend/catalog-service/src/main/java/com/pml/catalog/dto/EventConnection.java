package com.pml.catalog.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Connection type for Event pagination.
 * Follows the Relay Cursor Connections Specification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventConnection {

    /**
     * List of edges containing events and cursors
     */
    @Builder.Default
    private List<EventEdge> edges = new ArrayList<>();

    /**
     * Pagination metadata
     */
    private PageInfo pageInfo;

    /**
     * Create an empty connection
     */
    public static EventConnection empty() {
        return EventConnection.builder()
                .edges(new ArrayList<>())
                .pageInfo(PageInfo.builder()
                        .hasNextPage(false)
                        .hasPreviousPage(false)
                        .startCursor(null)
                        .endCursor(null)
                        .build())
                .build();
    }
}
