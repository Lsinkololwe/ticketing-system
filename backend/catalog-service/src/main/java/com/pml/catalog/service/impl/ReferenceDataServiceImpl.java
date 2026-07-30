package com.pml.catalog.service.impl;

import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.catalog.domain.model.ReferenceData;
import com.pml.catalog.dto.PageableInput;
import com.pml.catalog.dto.PagedResult;
import com.pml.catalog.dto.ReferenceDataPatch;
import com.pml.catalog.repository.ReferenceDataRepository;
import com.pml.catalog.service.ReferenceDataService;
import com.pml.catalog.service.ReferenceMetadataValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * ReferenceDataService implementation.
 *
 * <p>Writes go through three guards: per-type metadata validation ({@link ReferenceMetadataValidator}),
 * the unique {@code (type, code)} index (surfaced as a friendly duplicate error), and the
 * {@code isSystem} protection on delete.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReferenceDataServiceImpl implements ReferenceDataService {

    private final ReferenceDataRepository repository;
    private final ReferenceMetadataValidator metadataValidator;

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Override
    public Mono<ReferenceData> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public Flux<ReferenceData> findByType(ReferenceType type, boolean activeOnly) {
        return activeOnly
                ? repository.findByTypeAndIsActiveTrueOrderByDisplayOrderAscNameAsc(type)
                : repository.findByTypeOrderByDisplayOrderAscNameAsc(type);
    }

    @Override
    public Mono<ReferenceData> findByCode(ReferenceType type, String code) {
        return repository.findByTypeAndCode(type, code);
    }

    @Override
    public Flux<ReferenceData> findByParent(ReferenceType type, String parentCode) {
        return repository.findByTypeAndParentCodeAndIsActiveTrueOrderByDisplayOrderAscNameAsc(type, parentCode);
    }

    @Override
    public Mono<PagedResult<ReferenceData>> findAdmin(ReferenceType type, PageableInput pageable) {
        Pageable springPageable = pageable.toPageable();
        return Mono.zip(
                repository.findByType(type, springPageable).collectList(),
                repository.countByType(type)
        ).map(tuple -> PagedResult.of(
                tuple.getT1(),
                springPageable.getPageNumber(),
                springPageable.getPageSize(),
                tuple.getT2()
        ));
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    @Override
    public Mono<ReferenceData> create(ReferenceData data) {
        return Mono.fromRunnable(() -> metadataValidator.validate(data.getType(), data.getMetadata()))
                .then(Mono.defer(() -> {
                    data.setId(null);
                    // Admin-created rows are never system rows.
                    data.setSystem(false);
                    log.info("Creating reference data: type={}, code={}", data.getType(), data.getCode());
                    return repository.save(data);
                }))
                .onErrorMap(DuplicateKeyException.class, e -> new IllegalArgumentException(
                        "A %s row with code '%s' already exists".formatted(data.getType(), data.getCode())))
                .doOnSuccess(saved -> log.info("Reference data created: {}", saved.getId()));
    }

    @Override
    public Mono<ReferenceData> update(String id, ReferenceDataPatch patch) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Reference data not found: " + id)))
                .flatMap(existing -> {
                    // null field == leave unchanged (partial update)
                    if (patch.name() != null) existing.setName(patch.name());
                    if (patch.description() != null) existing.setDescription(patch.description());
                    if (patch.parentType() != null) existing.setParentType(patch.parentType());
                    if (patch.parentCode() != null) existing.setParentCode(patch.parentCode());
                    if (patch.effectiveFrom() != null) existing.setEffectiveFrom(patch.effectiveFrom());
                    if (patch.effectiveTo() != null) existing.setEffectiveTo(patch.effectiveTo());
                    if (patch.displayOrder() != null) existing.setDisplayOrder(patch.displayOrder());
                    if (patch.isActive() != null) existing.setActive(patch.isActive());
                    if (patch.metadata() != null && !patch.metadata().isEmpty()) {
                        existing.setMetadata(patch.metadata());
                    }
                    metadataValidator.validate(existing.getType(), existing.getMetadata());
                    return repository.save(existing);
                })
                .doOnSuccess(saved -> log.info("Reference data updated: {}", saved.getId()));
    }

    @Override
    public Mono<Void> delete(String id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Reference data not found: " + id)))
                .flatMap(existing -> {
                    if (existing.isSystem()) {
                        return Mono.error(new IllegalStateException(
                                "System-managed reference data cannot be deleted (type=%s, code=%s). Deactivate it instead."
                                        .formatted(existing.getType(), existing.getCode())));
                    }
                    log.info("Deleting reference data: {} ({}:{})", id, existing.getType(), existing.getCode());
                    return repository.delete(existing);
                });
    }

    @Override
    public Mono<ReferenceData> setActive(String id, boolean active) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Reference data not found: " + id)))
                .flatMap(existing -> {
                    existing.setActive(active);
                    return repository.save(existing);
                })
                .doOnSuccess(saved -> log.info("Reference data {} set active={}", id, active));
    }
}
