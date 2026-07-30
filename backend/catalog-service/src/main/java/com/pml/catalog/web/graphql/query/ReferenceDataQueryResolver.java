package com.pml.catalog.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.catalog.domain.model.ReferenceData;
import com.pml.catalog.dto.PageableInput;
import com.pml.catalog.dto.ReferenceDataOffsetPage;
import com.pml.catalog.service.ReferenceDataService;
import com.pml.catalog.service.ReferenceMetadataValidator;
import com.pml.catalog.web.graphql.dto.ReferenceTypeInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * GraphQL query resolver for reference data.
 *
 * <p>Reads are public (dropdowns must work for unauthenticated storefront users); the admin offset
 * table is guarded. {@code referenceTypes} exposes the type registry so a single admin screen can
 * render every type without a hardcoded client list.</p>
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class ReferenceDataQueryResolver {

    private final ReferenceDataService referenceDataService;
    private final ReferenceMetadataValidator metadataValidator;

    @DgsQuery
    public Mono<ReferenceData> referenceItem(
            @InputArgument ReferenceType type,
            @InputArgument String code) {
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(code, "code is required");
        return referenceDataService.findByCode(type, code);
    }

    @DgsQuery
    public Flux<ReferenceData> referenceData(
            @InputArgument ReferenceType type,
            @InputArgument Boolean activeOnly) {
        Objects.requireNonNull(type, "type is required");
        boolean active = activeOnly == null || activeOnly;
        log.debug("GraphQL query: referenceData(type={}, activeOnly={})", type, active);
        return referenceDataService.findByType(type, active);
    }

    @DgsQuery
    public Flux<ReferenceData> referenceDataByParent(
            @InputArgument ReferenceType type,
            @InputArgument String parentCode) {
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(parentCode, "parentCode is required");
        return referenceDataService.findByParent(type, parentCode);
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ReferenceDataOffsetPage> referenceDataOffsetPagination(
            @InputArgument ReferenceType type,
            @InputArgument PageableInput pagination) {
        Objects.requireNonNull(type, "type is required");
        PageableInput effective = pagination != null ? pagination : PageableInput.builder().build();
        return referenceDataService.findAdmin(type, effective)
                .map(page -> ReferenceDataOffsetPage.of(
                        page.getContent(),
                        page.getPageNumber(),
                        page.getPageSize(),
                        page.getTotalElements()));
    }

    /**
     * The type registry — one row per {@link ReferenceType} with its label, group, and the metadata
     * keys the admin form must render. Powers the generic admin management screen.
     */
    @DgsQuery
    public List<ReferenceTypeInfo> referenceTypes() {
        return Arrays.stream(ReferenceType.values())
                .map(t -> new ReferenceTypeInfo(
                        t.name(),
                        t.getLabel(),
                        t.getGroup().name(),
                        t.getGroup().getLabel(),
                        metadataValidator.requiredKeys(t)))
                .toList();
    }
}
