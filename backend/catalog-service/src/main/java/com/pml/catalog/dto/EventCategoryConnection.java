package com.pml.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Connection type for EventCategory cursor-based pagination.
 * Follows Relay Cursor Connections Specification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventCategoryConnection {

    @Builder.Default
    private List<EventCategoryEdge> edges = new ArrayList<>();
    private PageInfo pageInfo;

    /**
     * Create an empty connection
     */
    public static EventCategoryConnection empty() {
        return EventCategoryConnection.builder()
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
