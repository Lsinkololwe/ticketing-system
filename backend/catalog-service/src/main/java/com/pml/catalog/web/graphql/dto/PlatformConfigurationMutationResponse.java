package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.model.PlatformConfiguration;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * GraphQL mutation response for platform configuration updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformConfigurationMutationResponse {

    private boolean success;
    private String message;
    private PlatformConfiguration data;

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    public static PlatformConfigurationMutationResponse success(PlatformConfiguration config, String message) {
        return PlatformConfigurationMutationResponse.builder()
                .success(true)
                .message(message)
                .data(config)
                .build();
    }

    public static PlatformConfigurationMutationResponse error(String... errors) {
        return PlatformConfigurationMutationResponse.builder()
                .success(false)
                .errors(List.of(errors))
                .build();
    }
}
