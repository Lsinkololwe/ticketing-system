package com.pml.catalog.dto;

import com.pml.catalog.domain.model.Province;
import com.pml.catalog.util.CursorUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Edge wrapper for Province in cursor-based pagination.
 * Follows Relay Cursor Connections Specification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvinceEdge {

    private Province node;
    private String cursor;

    /**
     * Create ProvinceEdge from Province entity
     */
    public static ProvinceEdge from(Province province) {
        return ProvinceEdge.builder()
                .node(province)
                .cursor(CursorUtils.encodeCursor(province.getId()))
                .build();
    }
}
