package com.pml.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Connection type for Location cursor-based pagination.
 * Follows Relay Cursor Connections Specification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationConnection {

    @Builder.Default
    private List<LocationEdge> edges = new ArrayList<>();
    private PageInfo pageInfo;

    /**
     * Create an empty connection
     */
    public static LocationConnection empty() {
        return LocationConnection.builder()
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
