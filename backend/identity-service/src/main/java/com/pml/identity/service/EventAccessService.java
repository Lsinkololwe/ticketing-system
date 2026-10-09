package com.pml.identity.service;

import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.domain.valueobject.EventRole;
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
     * Find all grants for a user
     */
    Flux<EventAccessGrant> findByUser(String userId);

    /**
     * Find active grants for a user
     */
    Flux<EventAccessGrant> findActiveByUser(String userId);

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
