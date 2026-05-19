package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.model.ApprovalEscalation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * GraphQL mutation response for approval escalation operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalEscalationMutationResponse {

    private boolean success;
    private String message;
    private ApprovalEscalation data;

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    public static ApprovalEscalationMutationResponse success(ApprovalEscalation escalation, String message) {
        return ApprovalEscalationMutationResponse.builder()
                .success(true)
                .message(message)
                .data(escalation)
                .build();
    }

    public static ApprovalEscalationMutationResponse error(String... errors) {
        return ApprovalEscalationMutationResponse.builder()
                .success(false)
                .errors(List.of(errors))
                .build();
    }
}
