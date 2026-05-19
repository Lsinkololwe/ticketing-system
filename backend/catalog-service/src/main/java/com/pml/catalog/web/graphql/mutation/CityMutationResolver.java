package com.pml.catalog.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.web.graphql.dto.CreateCityInput;
import com.pml.catalog.web.graphql.dto.CreateCityMutationResponse;
import com.pml.catalog.web.graphql.dto.DeleteCityMutationResponse;
import com.pml.catalog.web.graphql.dto.UpdateCityInput;
import com.pml.catalog.web.graphql.dto.UpdateCityMutationResponse;
import com.pml.catalog.domain.model.City;
import com.pml.catalog.service.CityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

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
@RequiredArgsConstructor
public class CityMutationResolver {

    private final CityService cityService;

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<CreateCityMutationResponse> createCity(
            @InputArgument CreateCityInput input
    ) {
        log.info("Creating city: {} in province: {}", input.name(), input.provinceId());

        City city = mapInputToCity(input);

        return cityService.createCity(city)
                .map(created -> new CreateCityMutationResponse(
                        true,
                        "City created successfully",
                        created,
                        List.of(),
                        null
                ))
                .onErrorResume(e -> {
                    log.error("Create city failed: {}", e.getMessage());
                    return Mono.just(new CreateCityMutationResponse(
                            false,
                            e.getMessage(),
                            null,
                            List.of(e.getMessage()),
                            null
                    ));
                });
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<UpdateCityMutationResponse> updateCity(
            @InputArgument String id,
            @InputArgument UpdateCityInput input
    ) {
        log.info("Updating city: {}", id);

        return cityService.findById(id)
                .flatMap(existing -> {
                    updateCityFromInput(existing, input);
                    return cityService.updateCity(id, existing);
                })
                .map(updated -> new UpdateCityMutationResponse(
                        true,
                        "City updated successfully",
                        updated,
                        List.of(),
                        null
                ))
                .onErrorResume(e -> {
                    log.error("Update city failed: {}", e.getMessage());
                    return Mono.just(new UpdateCityMutationResponse(
                            false,
                            e.getMessage(),
                            null,
                            List.of(e.getMessage()),
                            null
                    ));
                });
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<DeleteCityMutationResponse> deleteCity(
            @InputArgument String id
    ) {
        log.info("Deleting city: {}", id);

        return cityService.deleteCity(id)
                .then(Mono.just(new DeleteCityMutationResponse(
                        true,
                        "City deleted successfully",
                        true,
                        List.of(),
                        null
                )))
                .onErrorResume(e -> {
                    log.error("Delete city failed: {}", e.getMessage());
                    return Mono.just(new DeleteCityMutationResponse(
                            false,
                            e.getMessage(),
                            false,
                            List.of(e.getMessage()),
                            null
                    ));
                });
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
        city.setUpdatedAt(LocalDateTime.now());
    }
}
