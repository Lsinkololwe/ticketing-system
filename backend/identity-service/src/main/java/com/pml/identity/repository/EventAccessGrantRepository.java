package com.pml.identity.repository;

import java.util.Collection;
import com.pml.identity.domain.enums.AccessGrantStatus;
import com.pml.identity.domain.model.EventAccessGrant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
     * Find all grants for an event
     */
    Flux<EventAccessGrant> findByEventId(String eventId);

    /**
     * Find all grants for an event with pagination
     */
    Flux<EventAccessGrant> findByEventId(String eventId, Pageable pageable);

    /**
     * Find all grants for a user
     */
    /**
     * Every grant one user holds for one organization's events.
     *
     * <p>Scoped to the organization rather than the user, because a member removed from one
     * organization keeps whatever access they hold in another — revoking by user alone would
     * take access they were never removed from.
     */
    Flux<EventAccessGrant> findByUserIdAndOrganizationId(String userId, String organizationId);

    Flux<EventAccessGrant> findByUserId(String userId);

    /**
     * Find active grants for a user
     */
    Flux<EventAccessGrant> findByUserIdAndStatus(String userId, AccessGrantStatus status);

    /**
     * Find all grants for an organization
     */
    Flux<EventAccessGrant> findByOrganizationId(String organizationId);

    Mono<EventAccessGrant> findByIdAndUserId(String id, String userId);

    Mono<EventAccessGrant> findByIdAndOrganizationIdIn(String id, Collection<String> organizationIds);

    Flux<EventAccessGrant> findByEventIdAndOrganizationIdIn(String eventId, Collection<String> organizationIds);

    Flux<EventAccessGrant> findByUserIdAndEventIdAndOrganizationIdIn(String userId, String eventId,
                                                                     Collection<String> organizationIds);
}
