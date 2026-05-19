package com.pml.identity.service;

import com.pml.identity.domain.enums.AccessGrantStatus;
import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.domain.valueobject.EventRole;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Event Access Service Interface
 *
 * Manages event-level access grants that override organization permissions.
 */
public interface EventAccessService {

    // ─────────────────────────────────────────────────────────────────────
    // Read Operations
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Find grant by ID
     */
    Mono<EventAccessGrant> findById(String id);

    /**
     * Find grant by user and event
     */
    Mono<EventAccessGrant> findByUserAndEvent(String userId, String eventId);

    /**
     * Check if user has access to event
     */
    Mono<Boolean> hasAccess(String userId, String eventId);

    /**
     * Check if user has active access to event
     */
    Mono<Boolean> hasActiveAccess(String userId, String eventId);

    /**
     * Find all grants for an event with pagination and optional status filter
     */
    Flux<EventAccessGrant> findByEvent(String eventId, AccessGrantStatus status, Pageable pageable);

    /**
     * Find all grants for an event
     */
    Flux<EventAccessGrant> findByEvent(String eventId);

    /**
     * Find all grants for a user
     */
    Flux<EventAccessGrant> findByUser(String userId);

    /**
     * Find active grants for a user
     */
    Flux<EventAccessGrant> findActiveByUser(String userId);

    /**
     * Find event owner
     */
    Mono<EventAccessGrant> findEventOwner(String eventId);

    /**
     * Count grants for an event
     */
    Mono<Long> countByEvent(String eventId);

    /**
     * Count active grants for an event
     */
    Mono<Long> countActiveByEvent(String eventId);

    // ─────────────────────────────────────────────────────────────────────
    // Write Operations
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Create event owner grant (when event is created)
     */
    Mono<EventAccessGrant> createEventOwner(String eventId, String organizationId, String userId);

    /**
     * Grant event access
     */
    Mono<EventAccessGrant> grant(
            String eventId,
            String organizationId,
            String userId,
            EventRole role,
            Set<String> customPermissions,
            String reason,
            Instant expiresAt,
            String grantedById
    );

    /**
     * Bulk grant event access
     */
    Flux<EventAccessGrant> bulkGrant(
            String eventId,
            String organizationId,
            List<GrantRequest> grants,
            String grantedById
    );

    /**
     * Update event access
     */
    Mono<EventAccessGrant> update(
            String accessId,
            EventRole newRole,
            Set<String> customPermissions,
            Instant expiresAt
    );

    /**
     * Revoke event access
     */
    Mono<EventAccessGrant> revoke(String accessId, String reason, String revokedById);

    /**
     * Expire old grants (scheduled task)
     */
    Mono<Long> expireOldGrants();

    /**
     * Delete all grants for an event (when event is deleted)
     */
    Mono<Void> deleteByEvent(String eventId);

    // ─────────────────────────────────────────────────────────────────────
    // Permission Operations
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Get user's event role
     */
    Mono<EventRole> getUserEventRole(String userId, String eventId);

    /**
     * Check if user has specific permission for event
     */
    Mono<Boolean> hasEventPermission(String userId, String eventId, String permission);

    // ─────────────────────────────────────────────────────────────────────
    // Helper Classes
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Grant request for bulk operations
     */
    record GrantRequest(
            String userId,
            EventRole role,
            Set<String> customPermissions,
            String reason,
            Instant expiresAt
    ) {}
}
