package com.pml.catalog.web.graphql.mutation;

import com.pml.shared.security.revocation.FailClosedOnRevocation;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.domain.model.ReferenceData;
import com.pml.catalog.web.graphql.dto.ReferenceDataPatch;
import com.pml.catalog.service.ReferenceDataService;
import com.pml.catalog.web.graphql.dto.CreateReferenceDataInput;
import com.pml.catalog.web.graphql.dto.ReferenceDataMutationResponse;
import com.pml.catalog.web.graphql.dto.UpdateReferenceDataInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

/**
 * GraphQL mutation resolver for reference data. All mutations are admin-only.
 */
@Slf4j

@DgsComponent
@FailClosedOnRevocation
@Validated
@RequiredArgsConstructor
public class ReferenceDataMutationResolver {

    private final ReferenceDataService referenceDataService;

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ReferenceDataMutationResponse> createReferenceData(
            @Valid @InputArgument CreateReferenceDataInput input) {
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
            @Valid @InputArgument UpdateReferenceDataInput input) {
        log.info("Updating reference data: {}", id);
        return referenceDataService.update(id, mapUpdate(input))
                .map(updated -> ReferenceDataMutationResponse.success(updated, "Reference data updated successfully"))
                .onErrorResume(e -> {
                    log.error("Update reference data failed: {}", e.getMessage());
                    return Mono.just(ReferenceDataMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Retires a row. The row is kept, marked retired, rather than deleted.
     *
     * <p>The name is kept because it is the mutation clients already call, but
     * the message says what actually happened — a row removed from the platform
     * would leave every document naming its code unable to render, and this
     * service cannot see those documents to know.
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<String> deleteReferenceData(@InputArgument String id) {
        log.info("Retiring reference data: {}", id);
        // Retired, not removed: the row is deactivated and kept, so anything
        // already referencing it still resolves. Returning the id makes that
        // explicit to a client evicting it from a cache.
        return referenceDataService.delete(id)
                .thenReturn(id);
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
                // A workflow row without a semantic is refused by the collection
                // validator, so this is carried through rather than dropped —
                // otherwise every admin-created status would fail at the write
                // with a document-failed-validation dump and no explanation.
                .semantic(input.semantic())
                .allowedTransitions(input.allowedTransitions() != null
                        ? new java.util.ArrayList<>(input.allowedTransitions())
                        : new java.util.ArrayList<>())
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
                input.effectiveFrom(),
                input.effectiveTo(),
                input.metadata(),
                input.semantic(),
                input.allowedTransitions());
    }
}
