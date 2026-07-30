package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.model.ReferenceData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mutation response for ReferenceData create/update/activate operations.
 * Matches the GraphQL {@code ReferenceDataMutationResponse} type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceDataMutationResponse {
    private boolean success;
    private String message;
    private ReferenceData data;
    @Builder.Default
    private List<String> errors = new ArrayList<>();
    private Map<String, Object> metadata;

    public static ReferenceDataMutationResponse success(ReferenceData data, String message) {
        return ReferenceDataMutationResponse.builder()
                .success(true).message(message).data(data).build();
    }

    public static ReferenceDataMutationResponse error(String errorMessage) {
        return ReferenceDataMutationResponse.builder()
                .success(false).message(errorMessage).errors(List.of(errorMessage)).build();
    }
}
