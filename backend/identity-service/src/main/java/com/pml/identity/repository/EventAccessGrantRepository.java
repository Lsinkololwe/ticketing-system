package com.pml.identity.repository;

import com.pml.identity.domain.enums.AccessGrantStatus;
import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.domain.valueobject.EventRole;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Event Access Grant Repository
 */
@Repository
public interface EventAccessGrantRepository extends ReactiveMongoRepository<EventAccessGrant, String> {

    /**
     * Find grant by user ID and event ID (unique combination)
     */
    Mono<EventAccessGrant> findByUserIdAndEventId(String userId, String eventId);

    /**
     * Check if user has access to event
     */
    Mono<Boolean> existsByUserIdAndEventId(String userId, String eventId);

    /**
     * Check if user has active access to event
     */
    Mono<Boolean> existsByUserIdAndEventIdAndStatus(String userId, String eventId, AccessGrantStatus status);

    /**
     * Find all grants for an event
     */
    Flux<EventAccessGrant> findByEventId(String eventId);

    /**
     * Find all grants for an event with pagination
     */
    Flux<EventAccessGrant> findByEventId(String eventId, Pageable pageable);

    /**
     * Find grants for an event by status
     */
    Flux<EventAccessGrant> findByEventIdAndStatus(String eventId, AccessGrantStatus status);

    /**
     * Find grants for an event by status with pagination
     */
    Flux<EventAccessGrant> findByEventIdAndStatus(String eventId, AccessGrantStatus status, Pageable pageable);

    /**
     * Find all grants for a user
     */
    Flux<EventAccessGrant> findByUserId(String userId);

    /**
     * Find active grants for a user
     */
    Flux<EventAccessGrant> findByUserIdAndStatus(String userId, AccessGrantStatus status);

    /**
     * Find all grants for an organization
     */
    Flux<EventAccessGrant> findByOrganizationId(String organizationId);

    /**
     * Find event owner
     */
    Mono<EventAccessGrant> findByEventIdAndEventRole(String eventId, EventRole eventRole);

    /**
     * Find grants by role for an event
     */
    Flux<EventAccessGrant> findByEventIdAndEventRoleAndStatus(
            String eventId,
            EventRole eventRole,
            AccessGrantStatus status
    );

    /**
     * Count grants for an event
     */
    Mono<Long> countByEventId(String eventId);

    /**
     * Count active grants for an event
     */
    Mono<Long> countByEventIdAndStatus(String eventId, AccessGrantStatus status);

    /**
     * Find expired grants (for cleanup)
     */
    @Query("{ 'status': 'ACTIVE', 'expiresAt': { $lt: ?0, $ne: null } }")
    Flux<EventAccessGrant> findExpiredGrants(Instant now);

    /**
     * Delete all grants for an event
     */
    Mono<Void> deleteByEventId(String eventId);

    /**
     * Delete all grants for an organization
     */
    Mono<Void> deleteByOrganizationId(String organizationId);

    /**
     * Find active grants that have expired
     */
    Flux<EventAccessGrant> findByStatusAndExpiresAtBefore(AccessGrantStatus status, Instant expiresAt);
}
