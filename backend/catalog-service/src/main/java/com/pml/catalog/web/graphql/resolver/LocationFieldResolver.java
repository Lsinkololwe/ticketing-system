package com.pml.catalog.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.catalog.domain.model.Location;
import com.pml.catalog.web.graphql.dto.Coordinates;
import lombok.extern.slf4j.Slf4j;

/**
 * Field Resolver for Location type.
 *
 * Resolves all Location fields including:
 * - id, name, address, country, postalCode, description: Direct entity mappings
 * - coordinates: Returns Coordinates object from latitude/longitude
 * - city: Maps cityName to schema field
 * - province: Maps provinceName to schema field
 */
@Slf4j
@DgsComponent
public class LocationFieldResolver {

    // ========================================================================
    // BASIC FIELD RESOLVERS
    // ========================================================================

    /**
     * Resolve Location.id - direct mapping
     */
    @DgsData(parentType = "Location", field = "id")
    public String id(DgsDataFetchingEnvironment dfe) {
        Location location = dfe.getSource();
        return location.getId();
    }

    /**
     * Resolve Location.name - direct mapping
     */
    @DgsData(parentType = "Location", field = "name")
    public String name(DgsDataFetchingEnvironment dfe) {
        Location location = dfe.getSource();
        return location.getName() != null ? location.getName() : "";
    }

    /**
     * Resolve Location.address - direct mapping
     */
    @DgsData(parentType = "Location", field = "address")
    public String address(DgsDataFetchingEnvironment dfe) {
        Location location = dfe.getSource();
        return location.getAddress() != null ? location.getAddress() : "";
    }

    /**
     * Resolve Location.country - direct mapping
     */
    @DgsData(parentType = "Location", field = "country")
    public String country(DgsDataFetchingEnvironment dfe) {
        Location location = dfe.getSource();
        return location.getCountry() != null ? location.getCountry() : "";
    }

    /**
     * Resolve Location.postalCode - direct mapping
     */
    @DgsData(parentType = "Location", field = "postalCode")
    public String postalCode(DgsDataFetchingEnvironment dfe) {
        Location location = dfe.getSource();
        return location.getPostalCode();
    }

    /**
     * Resolve Location.description - direct mapping
     */
    @DgsData(parentType = "Location", field = "description")
    public String description(DgsDataFetchingEnvironment dfe) {
        Location location = dfe.getSource();
        return location.getDescription();
    }

    // ========================================================================
    // COMPUTED FIELD RESOLVERS
    // ========================================================================

    /**
     * Resolve Location.coordinates - creates Coordinates object from lat/long
     */
    @DgsData(parentType = "Location", field = "coordinates")
    public Coordinates coordinates(DgsDataFetchingEnvironment dfe) {
        Location location = dfe.getSource();
        if (location.getLatitude() == null || location.getLongitude() == null) {
            return null;
        }
        return new Coordinates(location.getLatitude(), location.getLongitude());
    }

    /**
     * Resolve Location.city - maps to cityName field
     */
    @DgsData(parentType = "Location", field = "city")
    public String city(DgsDataFetchingEnvironment dfe) {
        Location location = dfe.getSource();
        return location.getCityName() != null ? location.getCityName() : "";
    }

    /**
     * Resolve Location.province - maps to provinceName field
     */
    @DgsData(parentType = "Location", field = "province")
    public String province(DgsDataFetchingEnvironment dfe) {
        Location location = dfe.getSource();
        return location.getProvinceName();
    }
}
