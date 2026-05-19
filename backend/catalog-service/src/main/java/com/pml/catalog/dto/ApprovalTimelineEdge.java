package com.pml.catalog.dto;

import com.pml.catalog.domain.model.ApprovalTimeline;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Edge type for ApprovalTimeline cursor pagination.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalTimelineEdge {

    /**
     * The ApprovalTimeline at this edge
     */
    private ApprovalTimeline node;

    /**
     * Opaque cursor for this edge
     */
    private String cursor;

    /**
     * Create an edge from a timeline (using ID as cursor)
     */
    public static ApprovalTimelineEdge of(ApprovalTimeline timeline) {
        return ApprovalTimelineEdge.builder()
                .node(timeline)
                .cursor(timeline.getId())
                .build();
    }
}
