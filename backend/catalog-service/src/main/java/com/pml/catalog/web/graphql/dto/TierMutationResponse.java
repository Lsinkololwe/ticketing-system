package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.model.TicketTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generic mutation response for ticket tier operations.
 * Used for activateTicketTier and deactivateTicketTier mutations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TierMutationResponse {
    private Boolean success;
    private String message;
    private TicketTier data;
    @Builder.Default
    private List<String> errors = new ArrayList<>();
    private Map<String, Object> metadata;

    public static TierMutationResponse success(TicketTier tier, String message) {
        return TierMutationResponse.builder()
                .success(true)
                .message(message)
                .data(tier)
                .build();
    }

    public static TierMutationResponse success(TicketTier tier, String message, Map<String, Object> metadata) {
        return TierMutationResponse.builder()
                .success(true)
                .message(message)
                .data(tier)
                .metadata(metadata)
                .build();
    }

    public static TierMutationResponse error(String errorMessage) {
        return TierMutationResponse.builder()
                .success(false)
                .message(errorMessage)
                .errors(List.of(errorMessage))
                .build();
    }
}
