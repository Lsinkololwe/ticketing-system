package com.pml.catalog.dto;

import com.pml.catalog.domain.model.EventCategory;
import com.pml.catalog.util.CursorUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Edge wrapper for EventCategory in cursor-based pagination.
 * Follows Relay Cursor Connections Specification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventCategoryEdge {

    private EventCategory node;
    private String cursor;

    /**
     * Create EventCategoryEdge from EventCategory entity
     */
    public static EventCategoryEdge from(EventCategory category) {
        return EventCategoryEdge.builder()
                .node(category)
                .cursor(CursorUtils.encodeCursor(category.getId()))
                .build();
    }
}
