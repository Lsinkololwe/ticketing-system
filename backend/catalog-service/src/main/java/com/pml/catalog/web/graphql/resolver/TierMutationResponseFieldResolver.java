package com.pml.catalog.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.catalog.domain.model.TicketTier;
import com.pml.catalog.web.graphql.dto.TierMutationResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Field Resolver for TierMutationResponse type.
 *
 * Explicitly resolves all fields to ensure proper GraphQL mapping.
 */
@Slf4j
@DgsComponent
public class TierMutationResponseFieldResolver {

    @DgsData(parentType = "TierMutationResponse", field = "success")
    public Boolean success(DgsDataFetchingEnvironment dfe) {
        TierMutationResponse response = dfe.getSource();
        return response.getSuccess() != null ? response.getSuccess() : false;
    }

    @DgsData(parentType = "TierMutationResponse", field = "message")
    public String message(DgsDataFetchingEnvironment dfe) {
        TierMutationResponse response = dfe.getSource();
        return response.getMessage();
    }

    @DgsData(parentType = "TierMutationResponse", field = "data")
    public TicketTier data(DgsDataFetchingEnvironment dfe) {
        TierMutationResponse response = dfe.getSource();
        return response.getData();
    }

    @DgsData(parentType = "TierMutationResponse", field = "errors")
    public List<String> errors(DgsDataFetchingEnvironment dfe) {
        TierMutationResponse response = dfe.getSource();
        return response.getErrors() != null ? response.getErrors() : List.of();
    }

    @DgsData(parentType = "TierMutationResponse", field = "metadata")
    public Map<String, Object> metadata(DgsDataFetchingEnvironment dfe) {
        TierMutationResponse response = dfe.getSource();
        return response.getMetadata();
    }
}
