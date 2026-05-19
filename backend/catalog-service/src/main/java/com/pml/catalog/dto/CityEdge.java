package com.pml.catalog.dto;

import com.pml.catalog.domain.model.City;
import com.pml.catalog.util.CursorUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Edge wrapper for City in cursor-based pagination.
 * Follows Relay Cursor Connections Specification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CityEdge {

    private City node;
    private String cursor;

    /**
     * Create CityEdge from City entity
     */
    public static CityEdge from(City city) {
        return CityEdge.builder()
                .node(city)
                .cursor(CursorUtils.encodeCursor(city.getId()))
                .build();
    }
}
