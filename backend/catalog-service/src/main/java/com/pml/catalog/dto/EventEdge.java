package com.pml.catalog.dto;

import com.pml.catalog.domain.model.Event;
import com.pml.catalog.util.CursorUtils;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Edge wrapper for Event in Relay Cursor Connections.
 * Contains the node (Event) and its cursor.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventEdge {

    /**
     * Opaque cursor for this edge
     */
    private String cursor;

    /**
     * The Event at this edge
     */
    private Event node;

    /**
     * Create an EventEdge from an Event
     *
     * @param event the event to wrap
     * @return EventEdge with encoded cursor
     */
    public static EventEdge from(Event event) {
        if (event == null) {
            return null;
        }
        EventEdge edge = new EventEdge();
        edge.setCursor(CursorUtils.encodeCursor(event.getId()));
        edge.setNode(event);
        return edge;
    }
}
