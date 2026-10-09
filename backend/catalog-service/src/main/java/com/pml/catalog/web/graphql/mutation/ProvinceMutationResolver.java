package com.pml.catalog.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.web.graphql.dto.CreateProvinceInput;
import com.pml.catalog.web.graphql.dto.UpdateProvinceInput;
import com.pml.catalog.domain.model.Province;
import com.pml.catalog.service.ProvinceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

/**
 * GraphQL Mutation Resolver for Province Operations
 *
 * Business Intent: Handles all province management mutations including
 * creation, updates, and deletion. Provinces are geographic administrative
 * regions used for organizing locations and events.
 * All mutations are secured with admin role.
 */
@Slf4j

@DgsComponent
@Validated
@RequiredArgsConstructor
public class ProvinceMutationResolver {

    private final ProvinceService provinceService;

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Province> createProvince(
            @Valid @InputArgument CreateProvinceInput input
    ) {
        log.info("Creating province: {}", input.name());

        Province province = mapInputToProvince(input);

        return provinceService.createProvince(province);
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Province> updateProvince(
            @InputArgument String id,
            @Valid @InputArgument UpdateProvinceInput input
    ) {
        log.info("Updating province: {}", id);

        return provinceService.findById(id)
                .flatMap(existing -> {
                    updateProvinceFromInput(existing, input);
                    return provinceService.updateProvince(id, existing);
                });
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<String> deleteProvince(
            @InputArgument String id
    ) {
        log.info("Deleting province: {}", id);

        return provinceService.deleteProvince(id)
                .thenReturn(id);
    }

    private Province mapInputToProvince(CreateProvinceInput input) {
        return Province.builder()
                .name(input.name())
                .code(input.code())
                .isActive(true)
                .build();
    }

    private void updateProvinceFromInput(Province province, UpdateProvinceInput input) {
        if (input.name() != null) province.setName(input.name());
        if (input.code() != null) province.setCode(input.code());
        if (input.isActive() != null) province.setActive(input.isActive());
        province.setUpdatedAt(clock.instant());
    }
}
