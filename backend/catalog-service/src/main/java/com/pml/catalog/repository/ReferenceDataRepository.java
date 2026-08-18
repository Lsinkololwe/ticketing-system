package com.pml.catalog.repository;

import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.catalog.domain.model.ReferenceData;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reference Data repository.
 *
 * <p>All reads are scoped by {@link ReferenceType}. Public reads use the active + ordered variants;
 * admin tables use the {@code findByType} + count pair for offset pagination.</p>
 */
@Repository
public interface ReferenceDataRepository extends ReactiveMongoRepository<ReferenceData, String> {

    // ── Public reads (dropdowns) ──────────────────────────────────────────────

    Flux<ReferenceData> findByTypeAndIsActiveTrueOrderByDisplayOrderAscNameAsc(ReferenceType type);

    Flux<ReferenceData> findByTypeOrderByDisplayOrderAscNameAsc(ReferenceType type);

    Mono<ReferenceData> findByTypeAndCode(ReferenceType type, String code);

    Flux<ReferenceData> findByTypeAndParentCodeAndIsActiveTrueOrderByDisplayOrderAscNameAsc(
            ReferenceType type, String parentCode);

    // ── Admin pagination ──────────────────────────────────────────────────────

    Flux<ReferenceData> findByType(ReferenceType type, Pageable pageable);

    Mono<Long> countByType(ReferenceType type);

    // ── Seeding support ───────────────────────────────────────────────────────

    Mono<Boolean> existsByTypeAndCode(ReferenceType type, String code);

    /**
     * Give every row lacking {@code allowedTransitions} an empty array.
     *
     * <p>{@code $exists: false} rather than a null check: a row that already has
     * the field — empty or populated with an administrator's configured routes —
     * must not be touched.
     *
     * @return how many rows were normalised
     */
    @Update("{ '$set': { 'allowedTransitions': [] } }")
    @Query("{ 'allowedTransitions': { '$exists': false } }")
    Mono<Long> normaliseMissingTransitions();
}
