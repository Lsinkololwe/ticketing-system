package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.model.Event;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generic mutation response for event operations.
 * Used for completeEvent, featureEvent, and other simple event mutations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventMutationResponse {
    private boolean success;
    private String message;
    private Event data;
    @Builder.Default
    private List<String> errors = new ArrayList<>();
    private Map<String, Object> metadata;

    public static EventMutationResponse success(Event event, String message) {
        return EventMutationResponse.builder()
                .success(true)
                .message(message)
                .data(event)
                .build();
    }

    public static EventMutationResponse success(Event event, String message, Map<String, Object> metadata) {
        return EventMutationResponse.builder()
                .success(true)
                .message(message)
                .data(event)
                .metadata(metadata)
                .build();
    }

    public static EventMutationResponse error(String errorMessage) {
        return EventMutationResponse.builder()
                .success(false)
                .message(errorMessage)
                .errors(List.of(errorMessage))
                .build();
    }
}
