package com.pml.catalog.service;

import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.catalog.domain.model.ReferenceData;
import com.pml.catalog.dto.PageableInput;
import com.pml.catalog.dto.PagedResult;
import com.pml.catalog.dto.ReferenceDataPatch;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * ReferenceDataService — CRUD + reads for the polymorphic reference-data collection.
 */
public interface ReferenceDataService {

    // ── Reads ─────────────────────────────────────────────────────────────────

    Mono<ReferenceData> findById(String id);

    /** Rows of a type. {@code activeOnly=true} is the dropdown query. */
    Flux<ReferenceData> findByType(ReferenceType type, boolean activeOnly);

    Mono<ReferenceData> findByCode(ReferenceType type, String code);

    /** Child rows of a hierarchy (active only), e.g. genres under a category. */
    Flux<ReferenceData> findByParent(ReferenceType type, String parentCode);

    Mono<PagedResult<ReferenceData>> findAdmin(ReferenceType type, PageableInput pageable);

    // ── Writes (admin) ────────────────────────────────────────────────────────

    Mono<ReferenceData> create(ReferenceData data);

    Mono<ReferenceData> update(String id, ReferenceDataPatch patch);

    /** Deletes a row. System rows ({@code isSystem=true}) are protected and cannot be deleted. */
    Mono<Void> delete(String id);

    Mono<ReferenceData> setActive(String id, boolean active);
}
