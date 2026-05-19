package com.pml.catalog.dto;

import com.pml.catalog.domain.model.Location;
import com.pml.catalog.util.CursorUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Edge wrapper for Location in cursor-based pagination.
 * Follows Relay Cursor Connections Specification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationEdge {

    private Location node;
    private String cursor;

    /**
     * Create LocationEdge from Location entity
     */
    public static LocationEdge from(Location location) {
        return LocationEdge.builder()
                .node(location)
                .cursor(CursorUtils.encodeCursor(location.getId()))
                .build();
    }
}
