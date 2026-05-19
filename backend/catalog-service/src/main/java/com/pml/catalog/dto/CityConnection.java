package com.pml.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Connection type for City cursor-based pagination.
 * Follows Relay Cursor Connections Specification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CityConnection {

    @Builder.Default
    private List<CityEdge> edges = new ArrayList<>();
    private PageInfo pageInfo;

    /**
     * Create an empty connection
     */
    public static CityConnection empty() {
        return CityConnection.builder()
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
