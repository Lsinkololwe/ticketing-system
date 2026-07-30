package com.pml.catalog.repository;

import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.catalog.domain.model.ReferenceData;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
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
}
