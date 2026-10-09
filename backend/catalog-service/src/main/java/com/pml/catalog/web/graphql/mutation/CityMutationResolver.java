package com.pml.catalog.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.web.graphql.dto.CreateCityInput;
import com.pml.catalog.web.graphql.dto.UpdateCityInput;
import com.pml.catalog.domain.model.City;
import com.pml.catalog.service.CityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

/**
 * GraphQL Mutation Resolver for City Operations
 *
 * Business Intent: Handles all city management mutations including
 * creation, updates, and deletion. Cities are geographic entities
 * that belong to provinces and are used for organizing locations
 * and events. All mutations are secured with admin role.
 */
@Slf4j

@DgsComponent
@Validated
@RequiredArgsConstructor
public class CityMutationResolver {

    private final CityService cityService;

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<City> createCity(
            @Valid @InputArgument CreateCityInput input
    ) {
        log.info("Creating city: {} in province: {}", input.name(), input.provinceId());
        return cityService.createCity(mapInputToCity(input));
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<City> updateCity(
            @InputArgument String id,
            @Valid @InputArgument UpdateCityInput input
    ) {
        log.info("Updating city: {}", id);

        return cityService.findById(id)
                .flatMap(existing -> {
                    updateCityFromInput(existing, input);
                    return cityService.updateCity(id, existing);
                });
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<String> deleteCity(
            @InputArgument String id
    ) {
        log.info("Deleting city: {}", id);

        return cityService.deleteCity(id)
                .thenReturn(id);
    }

    private City mapInputToCity(CreateCityInput input) {
        return City.builder()
                .name(input.name())
                .provinceId(input.provinceId())
                .isActive(true)
                .build();
    }

    private void updateCityFromInput(City city, UpdateCityInput input) {
        if (input.name() != null) city.setName(input.name());
        if (input.provinceId() != null) city.setProvinceId(input.provinceId());
        if (input.isActive() != null) city.setActive(input.isActive());
        city.setUpdatedAt(clock.instant());
    }
}
