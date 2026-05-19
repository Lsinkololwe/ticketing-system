package com.pml.catalog.service.impl;

import com.pml.catalog.web.graphql.dto.CreateTicketTierInput;
import com.pml.catalog.web.graphql.dto.UpdateTicketTierInput;
import com.pml.catalog.domain.model.TicketTier;
import com.pml.catalog.repository.EventRepository;
import com.pml.catalog.repository.TicketTierRepository;
import com.pml.catalog.service.TicketTierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
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
    private final EventRepository eventRepository;

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
                    // Fetch event to get organizationId for multi-tenant tracking
                    return eventRepository.findById(eventId)
                            .switchIfEmpty(Mono.error(new IllegalArgumentException("Event not found: " + eventId)))
                            .flatMap(event -> {
                                // Determine sort order if not provided
                                Mono<Integer> sortOrderMono = input.sortOrder() != null
                                        ? Mono.just(input.sortOrder())
                                        : tierRepository.countByEventId(eventId)
                                        .map(Long::intValue);

                                return sortOrderMono.flatMap(sortOrder -> {
                                    TicketTier tier = TicketTier.builder()
                                            .eventId(eventId)
                                            .organizationId(event.getOrganizationId())
                                            .code(input.code())
                                            .name(input.name())
                                            .description(input.description())
                                            .price(input.price())
                                            .originalPrice(input.originalPrice())
                                            .quantity(input.quantity())
                                            .availableQuantity(input.quantity())
                                            .soldQuantity(0)
                                            .maxPerOrder(input.maxPerOrder())
                                            .minPerOrder(input.minPerOrder())
                                            .benefits(input.benefits())
                                            .sortOrder(sortOrder)
                                            .isActive(true)
                                            .salesStartAt(input.salesStartAt())
                                            .salesEndAt(input.salesEndAt())
                                            .earlyBirdPrice(input.earlyBirdPrice())
                                            .earlyBirdEndsAt(input.earlyBirdEndsAt())
                                            .isHidden(input.isHidden() != null ? input.isHidden() : false)
                                            .accessCode(input.accessCode())
                                            .createdAt(LocalDateTime.now())
                                            .updatedAt(LocalDateTime.now())
                                            .build();

                                    return tierRepository.save(tier)
                                            .doOnSuccess(created -> log.info("Tier created: {}", created.getId()));
                                });
                            });
                }));
    }

    @Override
    public Mono<TicketTier> updateTier(String tierId, UpdateTicketTierInput input) {
        log.info("Updating ticket tier {}", tierId);

        return tierRepository.findById(tierId)
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
                    if (input.originalPrice() != null) {
                        tier.setOriginalPrice(input.originalPrice());
                    }
                    if (input.quantity() != null) {
                        // Adjust available quantity proportionally
                        int diff = input.quantity() - tier.getQuantity();
                        tier.setQuantity(input.quantity());
                        tier.setAvailableQuantity(tier.getAvailableQuantity() + diff);
                    }
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

                    tier.setUpdatedAt(LocalDateTime.now());
                    return tierRepository.save(tier);
                });
    }

    @Override
    public Mono<Boolean> deleteTier(String tierId) {
        log.info("Deleting ticket tier {}", tierId);

        return tierRepository.findById(tierId)
                .flatMap(tier -> tierRepository.delete(tier).thenReturn(true))
                .defaultIfEmpty(false);
    }

    @Override
    public Flux<TicketTier> reorderTiers(String eventId, List<String> tierIds) {
        log.info("Reordering {} tiers for event {}", tierIds.size(), eventId);

        return Flux.fromIterable(tierIds)
                .index()
                .flatMap(tuple -> {
                    int newOrder = tuple.getT1().intValue();
                    String tierId = tuple.getT2();

                    return tierRepository.findById(tierId)
                            .flatMap(tier -> {
                                tier.setSortOrder(newOrder);
                                tier.setUpdatedAt(LocalDateTime.now());
                                return tierRepository.save(tier);
                            });
                });
    }

    @Override
    public Mono<TicketTier> decrementAvailability(String tierId, int quantity) {
        return tierRepository.findById(tierId)
                .flatMap(tier -> {
                    if (tier.getAvailableQuantity() < quantity) {
                        return Mono.error(new IllegalStateException(
                                "Insufficient tickets available. Requested: " + quantity +
                                        ", Available: " + tier.getAvailableQuantity()));
                    }

                    tier.setAvailableQuantity(tier.getAvailableQuantity() - quantity);
                    tier.setSoldQuantity(tier.getSoldQuantity() + quantity);
                    tier.setUpdatedAt(LocalDateTime.now());

                    return tierRepository.save(tier);
                });
    }

    @Override
    public Mono<TicketTier> incrementAvailability(String tierId, int quantity) {
        return tierRepository.findById(tierId)
                .flatMap(tier -> {
                    tier.setAvailableQuantity(tier.getAvailableQuantity() + quantity);
                    tier.setSoldQuantity(Math.max(0, tier.getSoldQuantity() - quantity));
                    tier.setUpdatedAt(LocalDateTime.now());

                    return tierRepository.save(tier);
                });
    }

    @Override
    public Mono<TicketTier> activateTier(String tierId) {
        log.info("Activating ticket tier: {}", tierId);
        return tierRepository.findById(tierId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Ticket tier not found: " + tierId)))
                .flatMap(tier -> {
                    tier.setActive(true);
                    tier.setUpdatedAt(LocalDateTime.now());
                    return tierRepository.save(tier);
                })
                .doOnSuccess(tier -> log.info("Ticket tier activated: {}", tierId));
    }

    @Override
    public Mono<TicketTier> deactivateTier(String tierId) {
        log.info("Deactivating ticket tier: {}", tierId);
        return tierRepository.findById(tierId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Ticket tier not found: " + tierId)))
                .flatMap(tier -> {
                    tier.setActive(false);
                    tier.setUpdatedAt(LocalDateTime.now());
                    return tierRepository.save(tier);
                })
                .doOnSuccess(tier -> log.info("Ticket tier deactivated: {}", tierId));
    }
}
