package com.pml.catalog.service;

import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.model.TicketTier;
import com.pml.catalog.repository.TicketTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Keeps what an event says about its tiers equal to the tiers.
 *
 * <p>Three things are derived from {@code catalog_ticket_tiers} and copied onto the event after every
 * tier write: the tier list booking prices a reservation from, the capacity (the sum of the tiers),
 * and the lowest price of a tier on public sale, which the discovery price filter reads without
 * joining. Recomputing the capacity this way is not an organizer's material change, so it never
 * sends an approved event back for review.
 */
@Component
@RequiredArgsConstructor
public class EventTierMirror {

    private final TicketTierRepository tiers;
    private final ReactiveMongoTemplate mongo;
    private final Clock clock;

    /** Rewrites the event's copy of its tiers and returns the event as it now stands. */
    public Mono<Event> refresh(String eventId) {
        return tiers.findByEventIdOrderBySortOrderAsc(eventId)
                .collectList()
                .flatMap(list -> mongo.findAndModify(Query.query(Criteria.where("_id").is(eventId)), mirrorOf(list),
                        FindAndModifyOptions.options().returnNew(true), Event.class));
    }

    private Update mirrorOf(List<TicketTier> list) {
        Update update = new Update()
                .set("ticketCategories", list.stream().map(EventTierMirror::category).toList())
                .set("lowestTicketPrice", list.stream()
                        .filter(tier -> tier.isActive() && !tier.isHidden())
                        .map(TicketTier::getPrice)
                        .filter(Objects::nonNull)
                        .min(Comparator.naturalOrder())
                        .orElse(null))
                .set("updatedAt", clock.instant())
                .inc("version", 1);
        if (!list.isEmpty()) {
            int capacity = list.stream().mapToInt(TicketTier::getQuantity).sum();
            int sold = list.stream().mapToInt(TicketTier::getSoldQuantity).sum();
            update.set("totalCapacity", capacity).set("availableTickets", Math.max(0, capacity - sold))
                    .set("soldTickets", sold);
        }
        return update;
    }

    private static Event.EventTicketCategory category(TicketTier tier) {
        BigDecimal earlyBird = tier.getEarlyBirdPrice();
        return Event.EventTicketCategory.builder()
                .tierId(tier.getId())
                .code(tier.getCode())
                .name(tier.getName())
                .description(tier.getDescription())
                .price(tier.getPrice())
                .quantity(tier.getQuantity())
                .availableQuantity(tier.getAvailableQuantity())
                .active(tier.isActive())
                .hidden(tier.isHidden())
                .benefits(tier.getBenefits())
                .isEarlyBird(earlyBird != null)
                .earlyBirdPrice(earlyBird)
                .earlyBirdEndDate(tier.getEarlyBirdEndsAt())
                .build();
    }
}
