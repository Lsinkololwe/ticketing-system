package com.pml.catalog.dto;

import com.pml.catalog.domain.model.ApprovalEscalation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Edge type for ApprovalEscalation cursor pagination.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalEscalationEdge {

    /**
     * The ApprovalEscalation at this edge
     */
    private ApprovalEscalation node;

    /**
     * Opaque cursor for this edge
     */
    private String cursor;

    /**
     * Create an edge from an escalation (using ID as cursor)
     */
    public static ApprovalEscalationEdge of(ApprovalEscalation escalation) {
        return ApprovalEscalationEdge.builder()
                .node(escalation)
                .cursor(escalation.getId())
                .build();
    }
}
