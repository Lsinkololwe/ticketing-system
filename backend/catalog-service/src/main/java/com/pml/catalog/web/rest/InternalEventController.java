package com.pml.catalog.web.rest;

import com.pml.catalog.domain.model.Event;
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
                        .maxTicketsPerOrder(event.getCheckoutSettings() == null ? null
                                : event.getCheckoutSettings().maxTicketsPerOrder())
                        .collectHolderNames(event.getCheckoutSettings() != null
                                && event.getCheckoutSettings().collectHolderNames())
                        .extraQuestion(event.getCheckoutSettings() == null ? null
                                : event.getCheckoutSettings().extraQuestion())
                        .ticketCategories(event.getTicketCategories() != null ?
                                event.getTicketCategories().stream()
                                        .map(InternalEventController::tierMirror)
                                        .collect(Collectors.toList())
                                : null)
                        .build())
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * The tier as booking prices it. The early-bird pair travels with the full price so booking
     * charges what the storefront advertises; booking decides which price applies against
     * its own clock at reservation time.
     */
    static EventSummaryDto.TicketCategoryDto tierMirror(Event.EventTicketCategory cat) {
        return EventSummaryDto.TicketCategoryDto.builder()
                .id(cat.getTierId())
                .code(cat.getCode())
                .name(cat.getName())
                .price(cat.getPrice())
                .capacity(cat.getQuantity())
                .sold(cat.getSoldQuantity())
                .active(cat.isActive())
                .hidden(cat.isHidden())
                .earlyBirdPrice(cat.isEarlyBird() ? cat.getEarlyBirdPrice() : null)
                .earlyBirdEndsAt(cat.isEarlyBird() ? cat.getEarlyBirdEndDate() : null)
                .build();
    }
}
