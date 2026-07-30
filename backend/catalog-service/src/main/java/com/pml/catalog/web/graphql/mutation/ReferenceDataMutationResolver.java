package com.pml.catalog.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.domain.model.ReferenceData;
import com.pml.catalog.dto.ReferenceDataPatch;
import com.pml.catalog.service.ReferenceDataService;
import com.pml.catalog.web.graphql.dto.CreateReferenceDataInput;
import com.pml.catalog.web.graphql.dto.DeleteMutationResponse;
import com.pml.catalog.web.graphql.dto.ReferenceDataMutationResponse;
import com.pml.catalog.web.graphql.dto.UpdateReferenceDataInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.util.HashMap;

/**
 * GraphQL mutation resolver for reference data. All mutations are admin-only.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class ReferenceDataMutationResolver {

    private final ReferenceDataService referenceDataService;

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ReferenceDataMutationResponse> createReferenceData(
            @InputArgument CreateReferenceDataInput input) {
        log.info("Creating reference data: type={}, code={}", input.type(), input.code());
        return referenceDataService.create(mapCreate(input))
                .map(created -> ReferenceDataMutationResponse.success(created, "Reference data created successfully"))
                .onErrorResume(e -> {
                    log.error("Create reference data failed: {}", e.getMessage());
                    return Mono.just(ReferenceDataMutationResponse.error(e.getMessage()));
                });
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ReferenceDataMutationResponse> updateReferenceData(
            @InputArgument String id,
            @InputArgument UpdateReferenceDataInput input) {
        log.info("Updating reference data: {}", id);
        return referenceDataService.update(id, mapUpdate(input))
                .map(updated -> ReferenceDataMutationResponse.success(updated, "Reference data updated successfully"))
                .onErrorResume(e -> {
                    log.error("Update reference data failed: {}", e.getMessage());
                    return Mono.just(ReferenceDataMutationResponse.error(e.getMessage()));
                });
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<DeleteMutationResponse> deleteReferenceData(@InputArgument String id) {
        log.info("Deleting reference data: {}", id);
        return referenceDataService.delete(id)
                .then(Mono.just(DeleteMutationResponse.success("Reference data deleted successfully")))
                .onErrorResume(e -> {
                    log.error("Delete reference data failed: {}", e.getMessage());
                    return Mono.just(DeleteMutationResponse.error(e.getMessage()));
                });
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ReferenceDataMutationResponse> setReferenceDataActive(
            @InputArgument String id,
            @InputArgument Boolean active) {
        log.info("Setting reference data {} active={}", id, active);
        return referenceDataService.setActive(id, active != null && active)
                .map(updated -> ReferenceDataMutationResponse.success(
                        updated, "Reference data " + (updated.isActive() ? "activated" : "deactivated") + " successfully"))
                .onErrorResume(e -> {
                    log.error("Set-active reference data failed: {}", e.getMessage());
                    return Mono.just(ReferenceDataMutationResponse.error(e.getMessage()));
                });
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private ReferenceData mapCreate(CreateReferenceDataInput input) {
        return ReferenceData.builder()
                .type(input.type())
                .code(input.code())
                .name(input.name())
                .description(input.description())
                .parentType(input.parentType())
                .parentCode(input.parentCode())
                .displayOrder(input.displayOrder() != null ? input.displayOrder() : 0)
                .isActive(input.isActive() == null || input.isActive())
                .isSystem(false)
                .metadata(input.metadata() != null ? new HashMap<>(input.metadata()) : new HashMap<>())
                .build();
    }

    private ReferenceDataPatch mapUpdate(UpdateReferenceDataInput input) {
        return new ReferenceDataPatch(
                input.name(),
                input.description(),
                input.parentType(),
                input.parentCode(),
                input.displayOrder(),
                input.isActive(),
                null,
                null,
                input.metadata());
    }
}
