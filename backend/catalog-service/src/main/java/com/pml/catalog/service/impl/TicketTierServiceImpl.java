package com.pml.catalog.service.impl;

import com.pml.catalog.service.EventTierMirror;
import com.pml.catalog.service.TicketTierFactory;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import com.pml.shared.security.Permission;
import com.pml.shared.error.DomainRefusal;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.security.EventWriteGuard;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import com.pml.catalog.web.graphql.dto.CreateTicketTierInput;
import com.pml.catalog.web.graphql.dto.UpdateTicketTierInput;
import com.pml.catalog.domain.model.TicketTier;
import com.pml.catalog.repository.EventRepository;
import com.pml.catalog.repository.TicketTierRepository;
import com.pml.catalog.service.TicketTierService;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Ticket Tier Service Implementation
 *
 * Manages ticket pricing tiers with support for early bird pricing,
 * hidden tiers, and sophisticated ordering.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketTierServiceImpl implements TicketTierService {

    private final TicketTierRepository tierRepository;

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;
    private final EventRepository eventRepository;
    private final EventWriteGuard eventWriteGuard;


    /** For the atomic capacity move; see applyCapacityChange. */
    private final ReactiveMongoTemplate mongoTemplate;
    private final TicketTierFactory tierFactory;
    private final EventTierMirror tierMirror;
    /**
     * A tier the caller is entitled to act on, or {@code TIER_UNKNOWN}.
     *
     * <h2>Why every write goes through here</h2>
     * These mutations carry {@code @PreAuthorize("hasAnyRole('ADMIN','ORGANIZER')")} and
     * then act on a caller-supplied {@code tierId}. A role says the caller may edit
     * ticket tiers; it cannot say <em>which</em>. Without this, any account holding
     * the {@code ORGANIZER} realm role could reprice or delete any other
     * organization's tiers by id — OWASP A01:2021, in its CWE-639 form.
     *
     * <p>The check sits in the service rather than the resolver deliberately. A
     * resolver-level guard protects the one entry point that remembered it; this
     * protects the operation, including from the next caller of {@code updateTier}
     * that has not been written yet.
     */
    private Mono<TicketTier> tierVisibleToCaller(String tierId) {
        return CurrentTenantScope.get().flatMap(scope -> TenantGuard.locate(
                scope,
                tierRepository.findById(tierId),
                organizationIds -> tierRepository.findByIdAndOrganizationIdIn(tierId, organizationIds),
                ErrorCode.TIER_UNKNOWN,
                "ticket tier " + tierId));
    }

    @Override
    public Mono<TicketTier> findVisibleToCaller(String tierId) {
        return tierRepository.findByIdAndIsHiddenFalseAndIsActiveTrue(tierId)
                .filterWhen(tier -> publiclyListed(tier.getEventId()))
                .switchIfEmpty(Mono.defer(() -> ownedOrEmpty(tierVisibleToCaller(tierId))));
    }

    @Override
    public Flux<TicketTier> findForCaller(String eventId, boolean includeHidden) {
        Flux<TicketTier> tiers = includeHidden
                ? tierRepository.findByEventIdOrderBySortOrderAsc(eventId)
                : tierRepository.findByEventIdAndIsHiddenOrderBySortOrderAsc(eventId, false);
        return publiclyListed(eventId).flatMapMany(listed -> listed && !includeHidden
                ? tiers
                : ownedOrEmpty(eventOwnedByCaller(eventId)).flatMapMany(event -> tiers));
    }

    @Override
    public Flux<TicketTier> findAvailableForPurchase(String eventId) {
        return publiclyListed(eventId).flatMapMany(listed -> listed
                ? tierRepository.findByEventIdAndIsHiddenOrderBySortOrderAsc(eventId, false)
                        .filter(tier -> tier.isActive() && tier.getAvailableQuantity() > 0)
                : Flux.empty());
    }

    /** Whether the event is published and active, which is what makes its tiers public. */
    private Mono<Boolean> publiclyListed(String eventId) {
        return eventRepository.findByIdAndPublishedTrueAndIsActiveTrue(eventId).hasElement();
    }

    /** The event, when it belongs to one of the caller's organizations. */
    private Mono<Event> eventOwnedByCaller(String eventId) {
        return CurrentTenantScope.get().flatMap(scope -> TenantGuard.locate(
                scope,
                eventRepository.findById(eventId),
                organizationIds -> eventRepository.findByIdAndOrganizationIdIn(eventId, organizationIds),
                ErrorCode.EVENT_UNKNOWN,
                "event " + eventId));
    }

    /**
     * Runs an ownership lookup for a signed-in caller and answers empty when it refuses, so a public
     * query returns nothing instead of an error for something the caller may not see. An anonymous
     * caller has no organization and is answered empty without the lookup.
     */
    private <T> Mono<T> ownedOrEmpty(Mono<T> ownedLookup) {
        return CurrentTenantScope.get()
                .filter(scope -> scope.subject() != null)
                .flatMap(scope -> ownedLookup)
                .onErrorResume(DomainRefusal.class, refused -> Mono.empty());
    }

    @Override
    public Mono<TicketTier> findById(String id) {
        return tierRepository.findById(id);
    }

    @Override
    public Flux<TicketTier> findByEventId(String eventId, boolean includeHidden) {
        if (includeHidden) {
            return tierRepository.findByEventIdOrderBySortOrderAsc(eventId);
        }
        return tierRepository.findByEventIdAndIsHiddenOrderBySortOrderAsc(eventId, false);
    }

    @Override
    public Mono<TicketTier> createTier(String eventId, CreateTicketTierInput input) {
        log.info("Creating ticket tier {} for event {}", input.code(), eventId);

        // Validate code uniqueness
        return tierRepository.findByEventIdAndCode(eventId, input.code())
                .flatMap(existing -> Mono.<TicketTier>error(
                        new IllegalArgumentException("Tier with code " + input.code() + " already exists")))
                .switchIfEmpty(Mono.defer(() -> {
                    // Only an event the caller's organization owns, and that the caller may edit, takes
                    // a new tier; the tier inherits that event's organization.
                    return eventWriteGuard.forWrite(eventId, Permission.EVENT_EDIT)
                            .flatMap(event -> {
                                // Determine sort order if not provided
                                Mono<Integer> sortOrderMono = input.sortOrder() != null
                                        ? Mono.just(input.sortOrder())
                                        : tierRepository.countByEventId(eventId)
                                        .map(Long::intValue);

                                return sortOrderMono.flatMap(sortOrder -> {
                                    List<FieldViolation> violations = tierFactory.check(event, input, "input");
                                    if (!violations.isEmpty()) {
                                        return Mono.<TicketTier>error(new ValidationRefusal(violations));
                                    }
                                    return tierRepository.save(tierFactory.build(event, input, sortOrder, clock.instant()))
                                            .flatMap(created -> tierMirror.refresh(eventId).thenReturn(created))
                                            .doOnSuccess(created -> log.info("Tier created: {}", created.getId()));
                                });
                            });
                }));
    }

    @Override
    public Mono<TicketTier> updateTier(String tierId, UpdateTicketTierInput input) {
        log.info("Updating ticket tier {}", tierId);

        return tierVisibleToCaller(tierId)
                .flatMap(tier -> {
                    if (input.name() != null) {
                        tier.setName(input.name());
                    }
                    if (input.description() != null) {
                        tier.setDescription(input.description());
                    }
                    if (input.price() != null) {
                        tier.setPrice(input.price());
                    }
                    // Capacity is handled after the save, atomically.
                    //
                    // Adjusting it here, in the same read-modify-write as every other field
                    // (`setAvailableQuantity(getAvailableQuantity() + diff)`), looks safe because
                    // TicketTier carries @Version — and it is not, because the purchase path does
                    // not go through Spring Data. `InventoryServiceImpl` moves inventory with
                    // findAndModify and $inc, which does **not** bump the version field.
                    //
                    // So the optimistic lock this document appears to have does not cover the one
                    // field two writers actually contend for. An organiser adding capacity while a
                    // sale commits would read availableQuantity, the sale would decrement it, and
                    // the organiser's save — version unchanged, lock satisfied — would write the
                    // stale value back. The sold seat returns to the pool and gets sold twice.
                    //
                    // Adding capacity during an on-sale is exactly when an organiser does this.
                    if (input.maxPerOrder() != null) {
                        tier.setMaxPerOrder(input.maxPerOrder());
                    }
                    if (input.minPerOrder() != null) {
                        tier.setMinPerOrder(input.minPerOrder());
                    }
                    if (input.benefits() != null) {
                        tier.setBenefits(input.benefits());
                    }
                    if (input.sortOrder() != null) {
                        tier.setSortOrder(input.sortOrder());
                    }
                    if (input.isActive() != null) {
                        tier.setActive(input.isActive());
                    }
                    if (input.salesStartAt() != null) {
                        tier.setSalesStartAt(input.salesStartAt());
                    }
                    if (input.salesEndAt() != null) {
                        tier.setSalesEndAt(input.salesEndAt());
                    }
                    if (input.earlyBirdPrice() != null) {
                        tier.setEarlyBirdPrice(input.earlyBirdPrice());
                    }
                    if (input.earlyBirdEndsAt() != null) {
                        tier.setEarlyBirdEndsAt(input.earlyBirdEndsAt());
                    }
                    if (input.isHidden() != null) {
                        tier.setHidden(input.isHidden());
                    }
                    if (input.accessCode() != null) {
                        tier.setAccessCode(input.accessCode());
                    }
                    if (input.category() != null) {
                        tier.setCategory(input.category());
                    }

                    tier.setUpdatedAt(clock.instant());
                    return tierRepository.save(tier)
                            .flatMap(saved -> applyCapacityChange(saved, input.quantity()))
                            .flatMap(saved -> tierMirror.refresh(saved.getEventId()).thenReturn(saved));
                });
    }

    /**
     * Moves capacity by a single atomic {@code $inc}, so a concurrent sale is not overwritten.
     *
     * <p>{@code quantity} and {@code availableQuantity} move by the same delta and in one
     * operation: raising capacity by ten adds ten sellable seats, whatever happened to the count
     * in between. Nothing is read into Java and compared, which is the point — see
     * {@code InventoryWriteShapeLintTest}.</p>
     *
     * @param requested the organiser's new total, or null when the update did not touch capacity
     */
    private Mono<TicketTier> applyCapacityChange(TicketTier saved, Integer requested) {
        if (requested == null || requested == saved.getQuantity()) {
            return Mono.just(saved);
        }
        int delta = requested - saved.getQuantity();

        // Scoped by organizationId as well as id, though tierVisibleToCaller has already
        // authorised this tier. The filter and the permission check work together and
        // neither replaces the other — a by-id write whose only protection is a check several
        // frames upstream is one refactor away from having no protection at all.
        // TenantBoundaryLintTest counts by-id lookups without a tenant filter, and that count
        // may only fall.
        return mongoTemplate.findAndModify(
                Query.query(Criteria.where("id").is(saved.getId())
                        .and("organizationId").is(saved.getOrganizationId())),
                new Update().inc("quantity", delta).inc("availableQuantity", delta),
                FindAndModifyOptions.options().returnNew(true),
                TicketTier.class);
    }

    @Override
    public Mono<Boolean> deleteTier(String tierId) {
        log.info("Deleting ticket tier {}", tierId);

        // No defaultIfEmpty(false): an id the caller may not reach now refuses with
        // TIER_UNKNOWN, the same answer an unissued id gives. Reporting "false" would
        // have told the caller their delete was received and declined, which is one bit
        // more than they are entitled to know about somebody else's tier.
        return tierVisibleToCaller(tierId)
                .flatMap(tier -> tierRepository.delete(tier)
                        .then(tierMirror.refresh(tier.getEventId()))
                        .thenReturn(true));
    }

    @Override
    public Flux<TicketTier> reorderTiers(String eventId, List<String> tierIds) {
        log.info("Reordering {} tiers for event {}", tierIds.size(), eventId);

        return Flux.fromIterable(tierIds)
                .index()
                .flatMap(tuple -> {
                    int newOrder = tuple.getT1().intValue();
                    String tierId = tuple.getT2();

                    return tierVisibleToCaller(tierId)
                            .flatMap(tier -> {
                                tier.setSortOrder(newOrder);
                                tier.setUpdatedAt(clock.instant());
                                return tierRepository.save(tier);
                            });
                });
    }



    @Override
    public Mono<TicketTier> activateTier(String tierId) {
        log.info("Activating ticket tier: {}", tierId);
        return tierVisibleToCaller(tierId)
                .flatMap(tier -> {
                    tier.setActive(true);
                    tier.setUpdatedAt(clock.instant());
                    return tierRepository.save(tier)
                            .flatMap(saved -> tierMirror.refresh(saved.getEventId()).thenReturn(saved));
                })
                .doOnSuccess(tier -> log.info("Ticket tier activated: {}", tierId));
    }

    @Override
    public Mono<TicketTier> deactivateTier(String tierId) {
        log.info("Deactivating ticket tier: {}", tierId);
        return tierVisibleToCaller(tierId)
                .flatMap(tier -> {
                    tier.setActive(false);
                    tier.setUpdatedAt(clock.instant());
                    return tierRepository.save(tier)
                            .flatMap(saved -> tierMirror.refresh(saved.getEventId()).thenReturn(saved));
                })
                .doOnSuccess(tier -> log.info("Ticket tier deactivated: {}", tierId));
    }
}
