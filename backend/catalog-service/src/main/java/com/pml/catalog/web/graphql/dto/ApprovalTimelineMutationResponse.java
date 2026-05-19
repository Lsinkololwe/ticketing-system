package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.model.ApprovalTimeline;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * GraphQL mutation response for approval timeline operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalTimelineMutationResponse {

    private boolean success;
    private String message;
    private ApprovalTimeline data;

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    public static ApprovalTimelineMutationResponse success(ApprovalTimeline timeline, String message) {
        return ApprovalTimelineMutationResponse.builder()
                .success(true)
                .message(message)
                .data(timeline)
                .build();
    }

    public static ApprovalTimelineMutationResponse error(String... errors) {
        return ApprovalTimelineMutationResponse.builder()
                .success(false)
                .errors(List.of(errors))
                .build();
    }
}
