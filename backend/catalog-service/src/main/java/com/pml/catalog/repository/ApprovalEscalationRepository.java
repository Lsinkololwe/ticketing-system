package com.pml.catalog.repository;

import com.pml.catalog.domain.enums.EscalationStatus;
import com.pml.catalog.domain.model.ApprovalEscalation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository for ApprovalEscalation with pagination support.
 */
@Repository
public interface ApprovalEscalationRepository extends ReactiveMongoRepository<ApprovalEscalation, String> {

    // ==========================================
    // Single Entity Queries
    // ==========================================

    /**
     * Find escalation by event ID
     */
    Mono<ApprovalEscalation> findByEventId(String eventId);

    // ==========================================
    // Offset Pagination Queries (Admin Dashboard)
    // ==========================================

    /**
     * All escalations - offset pagination
     */
    Flux<ApprovalEscalation> findAllBy(Pageable pageable);

    /**
     * Escalations by status - offset pagination
     */
    Flux<ApprovalEscalation> findByStatus(EscalationStatus status, Pageable pageable);

    /**
     * Active escalations (PENDING or ACKNOWLEDGED) - offset pagination
     */
    @Query("{ 'status': { $in: ['PENDING', 'ACKNOWLEDGED'] } }")
    Flux<ApprovalEscalation> findActiveEscalations(Pageable pageable);

    /**
     * Active escalations assigned to an admin - offset pagination
     */
    @Query("{ 'escalatedTo': ?0, 'status': { $in: ['PENDING', 'ACKNOWLEDGED'] } }")
    Flux<ApprovalEscalation> findActiveByEscalatedTo(String adminId, Pageable pageable);

    // ==========================================
    // Count Queries
    // ==========================================

    Mono<Long> countByStatus(EscalationStatus status);

    @Query(value = "{ 'status': { $in: ['PENDING', 'ACKNOWLEDGED'] } }", count = true)
    Mono<Long> countActive();

    @Query(value = "{ 'escalatedTo': ?0, 'status': { $in: ['PENDING', 'ACKNOWLEDGED'] } }", count = true)
    Mono<Long> countActiveByEscalatedTo(String adminId);
}
