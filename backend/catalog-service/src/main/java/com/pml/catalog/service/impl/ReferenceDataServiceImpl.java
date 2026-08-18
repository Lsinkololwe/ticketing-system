package com.pml.catalog.service.impl;

import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.catalog.domain.model.ReferenceData;
import com.pml.catalog.dto.PageableInput;
import com.pml.catalog.dto.PagedResult;
import com.pml.catalog.dto.ReferenceDataPatch;
import com.pml.catalog.repository.ReferenceDataRepository;
import com.pml.catalog.service.ReferenceDataService;
import com.pml.catalog.service.ReferenceMetadataValidator;
import com.pml.shared.referencedata.StatusSemanticResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ReferenceDataService implementation.
 *
 * <h2>The guards, and which one each defect needed</h2>
 * Writes pass through per-type metadata validation
 * ({@link ReferenceMetadataValidator}), the unique {@code (type, code)} index
 * (surfaced as a friendly duplicate error), the {@code isSystem} protection, and
 * — added because their absence was doing real damage — the workflow rules
 * below.
 *
 * <h2>A workflow row must say what it means, on update as well as create</h2>
 * The create path checked it. The update path could not even carry the field:
 * {@code ReferenceDataPatch} omitted {@code semantic}, so the resolver mapped it
 * to nothing and an administrator correcting a wrong meaning got "updated
 * successfully" and changed nothing. That is the worst available outcome for
 * this particular field, because a record sitting in a status no branch
 * recognises simply stops — and the person who tried to fix it believes they
 * did.
 *
 * <h2>Deactivate, never delete</h2>
 * ET-CAT-003 R7 is explicit that reference data is retained. Rows here are
 * referenced by code from documents this service cannot see — an event names its
 * category, a payout names its status — so a hard delete leaves a dangling code
 * with no way to render it. {@link #delete} therefore deactivates and says so.
 *
 * @see ReferenceType#isCodeOwnedMachine()
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReferenceDataServiceImpl implements ReferenceDataService {

    private final ReferenceDataRepository repository;
    private final ReferenceMetadataValidator metadataValidator;
    private final StatusSemanticResolver semanticResolver;

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
        return validateWorkflowRules(data)
                .then(Mono.fromRunnable(() -> metadataValidator.validate(data.getType(), data.getMetadata())))
                .then(Mono.defer(() -> {
                    data.setId(null);
                    // Admin-created rows are never system rows.
                    data.setSystem(false);
                    log.info("Creating reference data: type={}, code={}", data.getType(), data.getCode());
                    return repository.save(data);
                }))
                .onErrorMap(DuplicateKeyException.class, e -> new IllegalArgumentException(
                        "A %s row with code '%s' already exists".formatted(data.getType(), data.getCode())))
                .doOnSuccess(this::invalidateSemanticCache)
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
                    if (patch.semantic() != null) existing.setSemantic(patch.semantic());
                    if (patch.allowedTransitions() != null) {
                        existing.setAllowedTransitions(new ArrayList<>(patch.allowedTransitions()));
                    }
                    if (patch.metadata() != null && !patch.metadata().isEmpty()) {
                        existing.setMetadata(patch.metadata());
                    }
                    metadataValidator.validate(existing.getType(), existing.getMetadata());
                    return validateWorkflowRules(existing).then(repository.save(existing));
                })
                .doOnSuccess(this::invalidateSemanticCache)
                .doOnSuccess(saved -> log.info("Reference data updated: {}", saved.getId()));
    }

    /**
     * Deactivates. Nothing here is ever removed.
     *
     * <p>ET-CAT-003 R7: reference data is deactivated and retained, because rows
     * are referenced <em>by code</em> from documents in collections this service
     * does not own. Deleting the {@code MUSIC} category does not delete the
     * events filed under it — it leaves them naming a code that no longer
     * renders, and no later migration can recover what the row said.
     *
     * <p>This used to hard-delete any non-system row. A system row was refused,
     * which read as though the protection was thought through; the rows an
     * administrator had added themselves — the ones most likely to be in use by
     * something recent — went straight out.
     */
    @Override
    public Mono<Void> delete(String id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Reference data not found: " + id)))
                .flatMap(existing -> {
                    if (existing.isSystem()) {
                        return Mono.error(new IllegalStateException(
                                "System-managed reference data cannot be removed (type=%s, code=%s)."
                                        .formatted(existing.getType(), existing.getCode())));
                    }
                    if (!existing.isActive()) {
                        log.info("Reference data {} ({}:{}) is already inactive",
                                id, existing.getType(), existing.getCode());
                        return Mono.empty();
                    }
                    log.info("Deactivating reference data: {} ({}:{})",
                            id, existing.getType(), existing.getCode());
                    existing.setActive(false);
                    return repository.save(existing)
                            .doOnSuccess(this::invalidateSemanticCache);
                })
                .then();
    }

    @Override
    public Mono<ReferenceData> setActive(String id, boolean active) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Reference data not found: " + id)))
                .flatMap(existing -> {
                    existing.setActive(active);
                    return repository.save(existing);
                })
                .doOnSuccess(this::invalidateSemanticCache)
                .doOnSuccess(saved -> log.info("Reference data {} set active={}", id, active));
    }

    // ── Workflow rules ────────────────────────────────────────────────────────

    /**
     * The three things a workflow row must get right.
     *
     * <p>Returns an error rather than throwing, so the caller composes it and the
     * administrator reads a sentence instead of a validator dump naming an
     * {@code anyOf} clause.
     */
    private Mono<Void> validateWorkflowRules(ReferenceData data) {
        ReferenceType type = data.getType();
        if (type == null) {
            return Mono.error(new IllegalArgumentException("Reference type is required"));
        }

        List<String> transitions = data.getAllowedTransitions() == null
                ? List.of() : data.getAllowedTransitions();

        if (!type.isWorkflow()) {
            // A taxonomy row with a semantic or transitions is not dangerous, it
            // is meaningless — and a meaningless value on screen is one somebody
            // will eventually try to use.
            if (data.getSemantic() != null) {
                return Mono.error(new IllegalArgumentException(
                        "%s is not a workflow type, so '%s' cannot carry a semantic — nothing branches on it."
                                .formatted(type, data.getCode())));
            }
            if (!transitions.isEmpty()) {
                return Mono.error(new IllegalArgumentException(
                        "%s is not a workflow type, so '%s' cannot declare transitions."
                                .formatted(type, data.getCode())));
            }
            return Mono.empty();
        }

        if (data.getSemantic() == null) {
            return Mono.error(new IllegalArgumentException(
                    ("%s is a workflow type, so '%s' must say what it means. Pick a semantic — "
                            + "records reaching a status with no meaning are not recognised by any "
                            + "code path, and stop moving without anybody being told.")
                            .formatted(type, data.getCode())));
        }

        if (!transitions.isEmpty() && type.isCodeOwnedMachine()) {
            // Two authorities over one question, and the Java one wins silently.
            return Mono.error(new IllegalArgumentException(
                    ("%s transitions are fixed by a state machine in code and cannot be edited here. "
                            + "Editing them would appear to work and change nothing, because the Java "
                            + "table is what actually refuses a write. Change the specification and "
                            + "the machine together.").formatted(type)));
        }

        if (transitions.isEmpty()) {
            return Mono.empty();
        }

        // A transition to a code that does not exist is a dead end that reads as
        // a configured route. Checked against the type's own codes, since a
        // status can only move within its own workflow.
        return repository.findByTypeOrderByDisplayOrderAscNameAsc(type)
                .map(ReferenceData::getCode)
                .collect(Collectors.toSet())
                .flatMap(known -> {
                    Set<String> siblings = new java.util.HashSet<>(known);
                    siblings.add(data.getCode());
                    List<String> unknown = transitions.stream()
                            .filter(target -> !siblings.contains(target))
                            .toList();
                    if (unknown.isEmpty()) {
                        return Mono.empty();
                    }
                    return Mono.error(new IllegalArgumentException(
                            "'%s' declares transitions to %s codes that do not exist in %s: %s"
                                    .formatted(data.getCode(), unknown.size(), type, unknown)));
                });
    }

    /**
     * Drop the cached meaning of the row that just changed.
     *
     * <p>{@link StatusSemanticResolver} caches for fifteen minutes so that the
     * write path of every payout and ticket is not a database read. Without this
     * call, an administrator correcting a semantic waits out that window while
     * records keep being classified by the old meaning — and has no way to tell
     * whether the edit took.
     *
     * <p>It clears only <em>this</em> service's cache. Every other service holds
     * its own, and closing that gap needs an eviction event rather than a method
     * call; until then fifteen minutes is the bound, and it is written down here
     * rather than discovered.
     */
    private void invalidateSemanticCache(ReferenceData saved) {
        if (saved != null && saved.getType() != null && saved.getType().isWorkflow()) {
            semanticResolver.invalidate();
        }
    }
}
