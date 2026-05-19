package com.pml.catalog.web.rest;

import com.pml.catalog.service.EventService;
import com.pml.shared.dto.EventSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

/**
 * Internal Event Controller
 *
 * Provides internal APIs for other microservices to fetch event information.
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/events")
@RequiredArgsConstructor
public class InternalEventController {

    private final EventService eventService;

    /**
     * Get event summary by ID
     * Used by Booking service to fetch event details for ticket purchases
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<EventSummaryDto>> getEventById(@PathVariable String id) {
        log.debug("Internal request for event: {}", id);

        return eventService.findById(id)
                .map(event -> EventSummaryDto.builder()
                        .id(event.getId())
                        .title(event.getTitle())
                        .organizerId(event.getOrganizerId())
                        .organizerName(event.getOrganizerName())
                        .organizationId(event.getOrganizationId())
                        .status(event.getStatus())
                        .startDate(event.getEventDateTime())
                        .endDate(event.getEndDateTime())
                        .locationId(event.getLocationId())
                        .locationName(event.getLocationName())
                        .cityName(event.getCityName())
                        .totalCapacity(event.getTotalCapacity())
                        .ticketsSold(event.getSoldTickets())
                        .bannerImageUrl(event.getBannerImageUrl())
                        .featured(event.isFeatured())
                        .soldOut(event.isSoldOut())
                        .ticketCategories(event.getTicketCategories() != null ?
                                event.getTicketCategories().stream()
                                        .map(cat -> EventSummaryDto.TicketCategoryDto.builder()
                                                .code(cat.getCode())
                                                .name(cat.getName())
                                                .price(cat.getPrice())
                                                .capacity(cat.getQuantity())
                                                .sold(cat.getSoldQuantity())
                                                .active(cat.isActive())
                                                .build())
                                        .collect(Collectors.toList())
                                : null)
                        .build())
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Get ticket category for an event
     */
    @GetMapping("/{id}/categories/{code}")
    public Mono<ResponseEntity<EventSummaryDto.TicketCategoryDto>> getTicketCategory(
            @PathVariable String id, @PathVariable String code) {
        log.debug("Internal request for event {} category: {}", id, code);

        return eventService.findById(id)
                .flatMap(event -> {
                    if (event.getTicketCategories() == null) {
                        return Mono.empty();
                    }
                    return Mono.justOrEmpty(event.getTicketCategories().stream()
                            .filter(cat -> cat.getCode().equals(code))
                            .findFirst()
                            .map(cat -> EventSummaryDto.TicketCategoryDto.builder()
                                    .code(cat.getCode())
                                    .name(cat.getName())
                                    .price(cat.getPrice())
                                    .capacity(cat.getQuantity())
                                    .sold(cat.getSoldQuantity())
                                    .active(cat.isActive())
                                    .build()));
                })
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Update sold ticket count for an event
     * Called by Booking service after successful ticket purchase
     */
    @PutMapping("/{id}/sold-tickets")
    public Mono<ResponseEntity<Void>> updateSoldTickets(
            @PathVariable String id,
            @RequestBody UpdateSoldTicketsRequest request) {
        log.debug("Internal request to update sold tickets for event: {}", id);

        return eventService.updateSoldTickets(id, request.count())
                .map(event -> ResponseEntity.ok().<Void>build())
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Check if event exists and is available for ticket purchase
     */
    @GetMapping("/{id}/available")
    public Mono<ResponseEntity<Boolean>> isEventAvailable(@PathVariable String id) {
        return eventService.findById(id)
                .map(event -> ResponseEntity.ok(
                        event.isPublished() &&
                                event.isActive() &&
                                !event.isSoldOut() &&
                                !event.isInThePast()))
                .defaultIfEmpty(ResponseEntity.ok(false));
    }

    public record UpdateSoldTicketsRequest(int count) {}
}
