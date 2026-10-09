package com.pml.catalog.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.model.EventCategory;
import com.pml.catalog.domain.model.TicketTier;
import com.pml.catalog.service.EventCategoryService;
import com.pml.catalog.service.TicketTierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.time.Clock;

/**
 * Computed Field Resolver for Event type.
 *
 * Resolves fields that require computation or additional data fetching:
 * - category: Resolves categoryId to EventCategory
 * - ticketTiers: Fetches all TicketTiers for the event
 * - minTicketPrice/maxTicketPrice: Computed from ticketTiers
 * - soldOut: Computed from availableTickets
 * - currency: Default currency for pricing
 *
 * Note: organizer and location are handled by EventFieldResolver in federation package
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class EventComputedFieldResolver {

    private final EventCategoryService categoryService;
    private final TicketTierService ticketTierService;

    /**
     * minTicketPrice and maxTicketPrice are early-bird aware, so they are time-dependent, so they
     * need a clock a test can freeze. Reading the wall clock inside {@code TicketTier} would leave
     * the storefront's own price without a boundary test: nothing could assert what it shows the
     * minute before an early-bird window closes, or the minute after.
     */
    private final Clock clock;

    /**
     * Resolve Event.category from categoryId
     */
    @DgsData(parentType = "Event", field = "category")
    public CompletableFuture<EventCategory> category(DgsDataFetchingEnvironment dfe) {
        Event event = dfe.getSource();
        if (event.getCategoryId() == null) {
            return CompletableFuture.completedFuture(null);
        }
        log.debug("Resolving category for event {}", event.getId());
        return categoryService.findById(event.getCategoryId()).toFuture();
    }

    /**
     * Resolve Event.ticketTiers - fetch all tiers for this event
     */
    @DgsData(parentType = "Event", field = "ticketTiers")
    public reactor.core.publisher.Mono<java.util.List<TicketTier>> ticketTiers(DgsDataFetchingEnvironment dfe) {
        Event event = dfe.getSource();
        log.debug("Resolving ticketTiers for event {}", event.getId());
        // A hidden tier is for the organization that owns the event and for platform administrators;
        // everyone else sees the public tiers, and reaches a hidden one only through
        // unlockTierWithAccessCode.
        return com.pml.shared.security.tenancy.CurrentTenantScope.get()
                .map(scope -> scope.permits(event.getOrganizationId()))
                .defaultIfEmpty(false)
                .onErrorReturn(false)
                .flatMap(owner -> ticketTierService.findByEventId(event.getId(), owner).collectList());
    }

    /**
     * Compute Event.minTicketPrice from ticketTiers
     */
    @DgsData(parentType = "Event", field = "minTicketPrice")
    public CompletableFuture<BigDecimal> minTicketPrice(DgsDataFetchingEnvironment dfe) {
        Event event = dfe.getSource();
        return ticketTierService.findByEventId(event.getId(), false)
                .filter(TicketTier::isActive)
                .map(tier -> tier.getCurrentPrice(clock.instant()))
                .reduce(BigDecimal::min)
                .toFuture();
    }

    /**
     * Compute Event.maxTicketPrice from ticketTiers
     */
    @DgsData(parentType = "Event", field = "maxTicketPrice")
    public CompletableFuture<BigDecimal> maxTicketPrice(DgsDataFetchingEnvironment dfe) {
        Event event = dfe.getSource();
        return ticketTierService.findByEventId(event.getId(), false)
                .filter(TicketTier::isActive)
                .map(tier -> tier.getCurrentPrice(clock.instant()))
                .reduce(BigDecimal::max)
                .toFuture();
    }

    /**
     * Compute Event.soldOut - true if no tickets available
     */
    @DgsData(parentType = "Event", field = "soldOut")
    public Boolean soldOut(DgsDataFetchingEnvironment dfe) {
        Event event = dfe.getSource();
        return event.getAvailableTickets() <= 0;
    }

    /**
     * Resolve Event.currency - default currency for the event
     */
    @DgsData(parentType = "Event", field = "currency")
    public CompletableFuture<String> currency(DgsDataFetchingEnvironment dfe) {
        // Default currency - ZMW (Zambian Kwacha)
        return CompletableFuture.completedFuture("ZMW");
    }
}
