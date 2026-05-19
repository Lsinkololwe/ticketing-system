package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.model.EventCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generic mutation response for event category operations.
 * Used for activateEventCategory and deactivateEventCategory mutations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryMutationResponse {
    private boolean success;
    private String message;
    private EventCategory data;
    @Builder.Default
    private List<String> errors = new ArrayList<>();
    private Map<String, Object> metadata;

    public static CategoryMutationResponse success(EventCategory category, String message) {
        return CategoryMutationResponse.builder()
                .success(true)
                .message(message)
                .data(category)
                .build();
    }

    public static CategoryMutationResponse success(EventCategory category, String message, Map<String, Object> metadata) {
        return CategoryMutationResponse.builder()
                .success(true)
                .message(message)
                .data(category)
                .metadata(metadata)
                .build();
    }

    public static CategoryMutationResponse error(String errorMessage) {
        return CategoryMutationResponse.builder()
                .success(false)
                .message(errorMessage)
                .errors(List.of(errorMessage))
                .build();
    }
}
