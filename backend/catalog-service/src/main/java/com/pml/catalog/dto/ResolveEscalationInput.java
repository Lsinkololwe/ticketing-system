package com.pml.catalog.dto;

import com.pml.catalog.domain.enums.ApprovalAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input for resolving an escalation.
 * Matches the GraphQL schema ResolveEscalationInput type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveEscalationInput {

    /**
     * Escalation ID to resolve
     */
    private String escalationId;

    /**
     * Resolution notes explaining how the escalation was handled
     */
    private String resolutionNotes;

    /**
     * The action taken to resolve (APPROVED, REJECTED, or CHANGES_REQUESTED)
     */
    private ApprovalAction action;
}
