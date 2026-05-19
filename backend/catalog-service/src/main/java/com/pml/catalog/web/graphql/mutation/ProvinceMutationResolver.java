package com.pml.catalog.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.web.graphql.dto.CreateProvinceInput;
import com.pml.catalog.web.graphql.dto.CreateProvinceMutationResponse;
import com.pml.catalog.web.graphql.dto.DeleteProvinceMutationResponse;
import com.pml.catalog.web.graphql.dto.UpdateProvinceInput;
import com.pml.catalog.web.graphql.dto.UpdateProvinceMutationResponse;
import com.pml.catalog.domain.model.Province;
import com.pml.catalog.service.ProvinceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

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
@RequiredArgsConstructor
public class ProvinceMutationResolver {

    private final ProvinceService provinceService;

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<CreateProvinceMutationResponse> createProvince(
            @InputArgument CreateProvinceInput input
    ) {
        log.info("Creating province: {}", input.name());

        Province province = mapInputToProvince(input);

        return provinceService.createProvince(province)
                .map(created -> new CreateProvinceMutationResponse(
                        true,
                        "Province created successfully",
                        created,
                        List.of(),
                        null
                ))
                .onErrorResume(e -> {
                    log.error("Create province failed: {}", e.getMessage());
                    return Mono.just(new CreateProvinceMutationResponse(
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
    public Mono<UpdateProvinceMutationResponse> updateProvince(
            @InputArgument String id,
            @InputArgument UpdateProvinceInput input
    ) {
        log.info("Updating province: {}", id);

        return provinceService.findById(id)
                .flatMap(existing -> {
                    updateProvinceFromInput(existing, input);
                    return provinceService.updateProvince(id, existing);
                })
                .map(updated -> new UpdateProvinceMutationResponse(
                        true,
                        "Province updated successfully",
                        updated,
                        List.of(),
                        null
                ))
                .onErrorResume(e -> {
                    log.error("Update province failed: {}", e.getMessage());
                    return Mono.just(new UpdateProvinceMutationResponse(
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
    public Mono<DeleteProvinceMutationResponse> deleteProvince(
            @InputArgument String id
    ) {
        log.info("Deleting province: {}", id);

        return provinceService.deleteProvince(id)
                .then(Mono.just(new DeleteProvinceMutationResponse(
                        true,
                        "Province deleted successfully",
                        true,
                        List.of(),
                        null
                )))
                .onErrorResume(e -> {
                    log.error("Delete province failed: {}", e.getMessage());
                    return Mono.just(new DeleteProvinceMutationResponse(
                            false,
                            e.getMessage(),
                            false,
                            List.of(e.getMessage()),
                            null
                    ));
                });
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
        province.setUpdatedAt(LocalDateTime.now());
    }
}
