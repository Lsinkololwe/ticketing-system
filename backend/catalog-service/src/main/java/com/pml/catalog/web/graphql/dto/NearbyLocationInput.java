package com.pml.catalog.web.graphql.dto;

/**
 * Input for nearby location queries.
 * Matches the GraphQL schema NearbyLocationInput type.
 */
public record NearbyLocationInput(
        Double latitude,
        Double longitude,
        Double radiusKm,
        Integer maxResults
) {
    public NearbyLocationInput {
        if (radiusKm == null) radiusKm = 10.0;
        if (maxResults == null) maxResults = 10;
    }
}
