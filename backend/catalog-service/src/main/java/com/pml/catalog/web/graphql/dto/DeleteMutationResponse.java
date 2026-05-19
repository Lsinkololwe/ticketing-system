package com.pml.catalog.web.graphql.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generic mutation response for delete operations.
 * Matches the GraphQL DeleteMutationResponse type in schema.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteMutationResponse {
    private boolean success;
    private String message;
    @Builder.Default
    private List<String> errors = new ArrayList<>();
    private Map<String, Object> metadata;

    public static DeleteMutationResponse success(String message) {
        return DeleteMutationResponse.builder()
                .success(true)
                .message(message)
                .build();
    }

    public static DeleteMutationResponse success(String message, Map<String, Object> metadata) {
        return DeleteMutationResponse.builder()
                .success(true)
                .message(message)
                .metadata(metadata)
                .build();
    }

    public static DeleteMutationResponse error(String errorMessage) {
        return DeleteMutationResponse.builder()
                .success(false)
                .message(errorMessage)
                .errors(List.of(errorMessage))
                .build();
    }
}
