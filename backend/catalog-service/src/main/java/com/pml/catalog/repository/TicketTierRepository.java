package com.pml.catalog.repository;

import com.pml.catalog.domain.model.TicketTier;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * Ticket Tier Repository
 *
 * Reactive repository for managing ticket pricing tiers.
 *
 * Business Intent: Provide efficient queries for ticket tier management including
 * ordering, filtering by visibility, and code-based lookups.
 */
@Repository
public interface TicketTierRepository extends ReactiveMongoRepository<TicketTier, String> {

    /**
     * Find all tiers for an event, ordered by sort order
     *
     * @param eventId Event ID
     * @return Flux of ticket tiers
     */
    Flux<TicketTier> findByEventIdOrderBySortOrderAsc(String eventId);

    /**
     * Find hidden or visible tiers for an event
     *
     * @param eventId Event ID
     * @param isHidden Hidden status
     * @return Flux of ticket tiers
     */
    Flux<TicketTier> findByEventIdAndIsHiddenOrderBySortOrderAsc(String eventId, boolean isHidden);

    /**
     * Find tier by event ID and code
     *
     * @param eventId Event ID
     * @param code Tier code
     * @return Ticket tier if exists
     */
    Mono<TicketTier> findByEventIdAndCode(String eventId, String code);

    /**
     * Count tiers for an event
     *
     * @param eventId Event ID
     * @return Count of tiers
     */
    Mono<Long> countByEventId(String eventId);

    /**
     * Find a tier by id, restricted to the caller's organizations.
     *
     * <p>The boundary is the {@code IN} clause, not a comparison the caller of this
     * method has to remember afterwards. A tier belonging to another organization
     * does not come back, so "not yours" and "no such id" are the same empty
     * {@code Mono} and cannot be told apart from outside — which is what stops the
     * error code becoming an enumeration oracle over real tier ids.
     *
     * <p>A set rather than one id, because a user may belong to several
     * organizations and pinning them to one is how {@code ActorOrganizationResolver}
     * ended up returning {@code organizations.get(0)}.
     *
     * @param id              tier id, caller-supplied and therefore untrusted
     * @param organizationIds the caller's active memberships; empty matches nothing
     */
    Mono<TicketTier> findByIdAndOrganizationIdIn(String id, Collection<String> organizationIds);

    /** A tier anyone may see: active and not hidden. The event's own visibility is checked separately. */
    Mono<TicketTier> findByIdAndIsHiddenFalseAndIsActiveTrue(String id);

    /** A hidden, active tier by id: what an access code is checked against. */
    Mono<TicketTier> findByIdAndIsHiddenTrueAndIsActiveTrue(String id);
}
