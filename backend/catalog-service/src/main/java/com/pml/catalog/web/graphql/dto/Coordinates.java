package com.pml.catalog.web.graphql.dto;

/**
 * Coordinates DTO for GraphQL schema.
 * Represents geographic coordinates with latitude and longitude.
 */
public record Coordinates(
        Double latitude,
        Double longitude
) {
    /**
     * Check if coordinates are valid
     */
    public boolean isValid() {
        return latitude != null && longitude != null &&
                latitude >= -90 && latitude <= 90 &&
                longitude >= -180 && longitude <= 180;
    }
}
