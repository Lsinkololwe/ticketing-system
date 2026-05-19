package com.pml.identity.service.impl;

import com.pml.identity.domain.enums.AccessGrantStatus;
import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.domain.valueobject.EventRole;
import com.pml.identity.repository.EventAccessGrantRepository;
import com.pml.identity.service.EventAccessService;
import com.pml.identity.service.PermissionResolutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Event Access Service Implementation
 *
 * Manages event-level access grants that override organization permissions.
 * Supports:
 * - Granting temporary access for specific events
 * - Role-based event permissions
 * - Access expiration handling
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventAccessServiceImpl implements EventAccessService {

    private final EventAccessGrantRepository accessGrantRepository;
    private final PermissionResolutionService permissionResolutionService;

    // ========================================================================
    // READ OPERATIONS
    // ========================================================================

    @Override
    public Mono<EventAccessGrant> findById(String id) {
        return accessGrantRepository.findById(id);
    }

    @Override
    public Mono<EventAccessGrant> findByUserAndEvent(String userId, String eventId) {
        return accessGrantRepository.findByUserIdAndEventId(userId, eventId);
    }

    @Override
    public Mono<Boolean> hasAccess(String userId, String eventId) {
        return accessGrantRepository.existsByUserIdAndEventId(userId, eventId);
    }

    @Override
    public Mono<Boolean> hasActiveAccess(String userId, String eventId) {
        return accessGrantRepository.existsByUserIdAndEventIdAndStatus(userId, eventId, AccessGrantStatus.ACTIVE);
    }

    @Override
    public Flux<EventAccessGrant> findByEvent(String eventId, AccessGrantStatus status, Pageable pageable) {
        if (status != null) {
            return accessGrantRepository.findByEventIdAndStatus(eventId, status, pageable);
        }
        return accessGrantRepository.findByEventId(eventId, pageable);
    }

    @Override
    public Flux<EventAccessGrant> findByEvent(String eventId) {
        return accessGrantRepository.findByEventId(eventId);
    }

    @Override
    public Flux<EventAccessGrant> findByUser(String userId) {
        return accessGrantRepository.findByUserId(userId);
    }

    @Override
    public Flux<EventAccessGrant> findActiveByUser(String userId) {
        return accessGrantRepository.findByUserIdAndStatus(userId, AccessGrantStatus.ACTIVE);
    }

    @Override
    public Mono<EventAccessGrant> findEventOwner(String eventId) {
        return accessGrantRepository.findByEventIdAndEventRole(eventId, EventRole.EVENT_OWNER);
    }

    @Override
    public Mono<Long> countByEvent(String eventId) {
        return accessGrantRepository.countByEventId(eventId);
    }

    @Override
    public Mono<Long> countActiveByEvent(String eventId) {
        return accessGrantRepository.countByEventIdAndStatus(eventId, AccessGrantStatus.ACTIVE);
    }

    // ========================================================================
    // WRITE OPERATIONS
    // ========================================================================

    @Override
    public Mono<EventAccessGrant> createEventOwner(String eventId, String organizationId, String userId) {
        log.info("Creating event owner grant for event: {} user: {}", eventId, userId);

        return accessGrantRepository.existsByUserIdAndEventId(userId, eventId)
                .flatMap(exists -> {
                    if (exists) {
                        // Update existing to owner
                        return accessGrantRepository.findByUserIdAndEventId(userId, eventId)
                                .flatMap(grant -> {
                                    grant.setEventRole(EventRole.EVENT_OWNER);
                                    grant.setStatus(AccessGrantStatus.ACTIVE);
                                    return accessGrantRepository.save(grant);
                                });
                    }

                    EventAccessGrant grant = EventAccessGrant.builder()
                            .userId(userId)
                            .eventId(eventId)
                            .organizationId(organizationId)
                            .eventRole(EventRole.EVENT_OWNER)
                            .status(AccessGrantStatus.ACTIVE)
                            .grantedById(userId) // Self-granted for owner
                            .reason("Event creator")
                            .grantedAt(Instant.now())
                            .build();

                    return accessGrantRepository.save(grant)
                            .doOnSuccess(saved -> log.info("Event owner grant created: {}", saved.getId()));
                });
    }

    @Override
    public Mono<EventAccessGrant> grant(
            String eventId,
            String organizationId,
            String userId,
            EventRole role,
            Set<String> customPermissions,
            String reason,
            Instant expiresAt,
            String grantedById) {
        log.info("Granting event access: event={} user={} role={}", eventId, userId, role);

        // Cannot grant EVENT_OWNER through this method
        if (role == EventRole.EVENT_OWNER) {
            return Mono.error(new IllegalArgumentException("Cannot grant EVENT_OWNER through this method"));
        }

        return accessGrantRepository.existsByUserIdAndEventId(userId, eventId)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalStateException("User already has access to this event"));
                    }

                    EventAccessGrant grant = EventAccessGrant.builder()
                            .userId(userId)
                            .eventId(eventId)
                            .organizationId(organizationId)
                            .eventRole(role)
                            .customPermissions(customPermissions != null ? customPermissions : Set.of())
                            .status(AccessGrantStatus.ACTIVE)
                            .grantedById(grantedById)
                            .reason(reason)
                            .expiresAt(expiresAt)
                            .grantedAt(Instant.now())
                            .build();

                    return accessGrantRepository.save(grant)
                            .doOnSuccess(saved -> log.info("Event access granted: {} for user: {}",
                                    saved.getId(), userId));
                });
    }

    @Override
    public Flux<EventAccessGrant> bulkGrant(
            String eventId,
            String organizationId,
            List<GrantRequest> grants,
            String grantedById) {
        log.info("Bulk granting event access for event: {} - Count: {}", eventId, grants.size());

        return Flux.fromIterable(grants)
                .flatMap(request -> grant(
                        eventId,
                        organizationId,
                        request.userId(),
                        request.role(),
                        request.customPermissions(),
                        request.reason(),
                        request.expiresAt(),
                        grantedById)
                        .onErrorResume(e -> {
                            log.warn("Failed to grant access for user {}: {}",
                                    request.userId(), e.getMessage());
                            return Mono.empty();
                        }));
    }

    @Override
    public Mono<EventAccessGrant> update(
            String accessId,
            EventRole newRole,
            Set<String> customPermissions,
            Instant expiresAt) {
        return accessGrantRepository.findById(accessId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Access grant not found: " + accessId)))
                .flatMap(grant -> {
                    // Cannot change from/to EVENT_OWNER
                    if (grant.getEventRole() == EventRole.EVENT_OWNER || newRole == EventRole.EVENT_OWNER) {
                        return Mono.error(new IllegalStateException("Cannot modify EVENT_OWNER role"));
                    }

                    if (newRole != null) {
                        grant.setEventRole(newRole);
                    }
                    if (customPermissions != null) {
                        grant.setCustomPermissions(customPermissions);
                    }
                    if (expiresAt != null) {
                        grant.setExpiresAt(expiresAt);
                    }

                    return accessGrantRepository.save(grant)
                            .doOnSuccess(saved -> log.info("Event access updated: {}", saved.getId()));
                });
    }

    @Override
    public Mono<EventAccessGrant> revoke(String accessId, String reason, String revokedById) {
        log.info("Revoking event access: {} - Reason: {}", accessId, reason);

        return accessGrantRepository.findById(accessId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Access grant not found: " + accessId)))
                .flatMap(grant -> {
                    // Cannot revoke EVENT_OWNER access
                    if (grant.getEventRole() == EventRole.EVENT_OWNER) {
                        return Mono.error(new IllegalStateException("Cannot revoke EVENT_OWNER access"));
                    }

                    grant.setStatus(AccessGrantStatus.REVOKED);
                    grant.setRevokedById(revokedById);
                    grant.setRevokedAt(Instant.now());
                    grant.setRevocationReason(reason);

                    return accessGrantRepository.save(grant)
                            .doOnSuccess(revoked -> log.info("Event access revoked: {}", revoked.getId()));
                });
    }

    @Override
    public Mono<Long> expireOldGrants() {
        log.info("Expiring old event access grants");

        return accessGrantRepository.findByStatusAndExpiresAtBefore(AccessGrantStatus.ACTIVE, Instant.now())
                .flatMap(grant -> {
                    grant.setStatus(AccessGrantStatus.EXPIRED);
                    return accessGrantRepository.save(grant);
                })
                .count()
                .doOnSuccess(count -> log.info("Expired {} event access grants", count));
    }

    @Override
    public Mono<Void> deleteByEvent(String eventId) {
        log.info("Deleting all access grants for event: {}", eventId);
        return accessGrantRepository.deleteByEventId(eventId);
    }

    // ========================================================================
    // PERMISSION OPERATIONS
    // ========================================================================

    @Override
    public Mono<EventRole> getUserEventRole(String userId, String eventId) {
        return accessGrantRepository.findByUserIdAndEventId(userId, eventId)
                .filter(EventAccessGrant::isValid)
                .map(EventAccessGrant::getEventRole);
    }

    @Override
    public Mono<Boolean> hasEventPermission(String userId, String eventId, String permission) {
        return accessGrantRepository.findByUserIdAndEventId(userId, eventId)
                .filter(EventAccessGrant::isValid)
                .map(grant -> {
                    // Check custom permissions first
                    if (grant.getCustomPermissions() != null && grant.getCustomPermissions().contains(permission)) {
                        return true;
                    }
                    // Check role-based permissions
                    return permissionResolutionService.getEventRolePermissions(grant.getEventRole())
                            .contains(permission);
                })
                .defaultIfEmpty(false);
    }
}
