package com.pml.booking.web.rest;

import com.pml.booking.domain.model.Ticket;
import com.pml.booking.service.TicketService;
import com.pml.shared.dto.TicketSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Internal Ticket Controller
 *
 * Provides internal APIs for other microservices to fetch ticket information.
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/tickets")
@RequiredArgsConstructor
public class InternalTicketController {

    private final TicketService ticketService;

    /**
     * Get ticket summary by ID
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<TicketSummaryDto>> getTicketById(@PathVariable String id) {
        log.debug("Internal request for ticket: {}", id);

        return ticketService.findById(id)
                .map(this::mapToSummaryDto)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Get ticket by ticket number
     */
    @GetMapping("/by-number/{ticketNumber}")
    public Mono<ResponseEntity<TicketSummaryDto>> getTicketByNumber(@PathVariable String ticketNumber) {
        log.debug("Internal request for ticket number: {}", ticketNumber);

        return ticketService.findByTicketNumber(ticketNumber)
                .map(this::mapToSummaryDto)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Get tickets by event ID
     */
    @GetMapping("/by-event/{eventId}")
    public Flux<TicketSummaryDto> getTicketsByEventId(@PathVariable String eventId) {
        log.debug("Internal request for tickets by event: {}", eventId);

        return ticketService.findByEventId(eventId)
                .map(this::mapToSummaryDto);
    }

    /**
     * Get tickets by buyer ID
     */
    @GetMapping("/by-buyer/{buyerId}")
    public Flux<TicketSummaryDto> getTicketsByBuyerId(@PathVariable String buyerId) {
        log.debug("Internal request for tickets by buyer: {}", buyerId);

        return ticketService.findByBuyerId(buyerId)
                .map(this::mapToSummaryDto);
    }

    /**
     * Get ticket count by event
     */
    @GetMapping("/count/by-event/{eventId}")
    public Mono<ResponseEntity<Long>> getTicketCountByEvent(@PathVariable String eventId) {
        return ticketService.countByEventId(eventId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.ok(0L));
    }

    /**
     * Validate a ticket
     */
    @PostMapping("/{ticketNumber}/validate")
    public Mono<ResponseEntity<TicketSummaryDto>> validateTicket(@PathVariable String ticketNumber) {
        log.debug("Internal request to validate ticket: {}", ticketNumber);

        return ticketService.validateTicket(ticketNumber)
                .map(this::mapToSummaryDto)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Failed to validate ticket: {}", ticketNumber, e);
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    private TicketSummaryDto mapToSummaryDto(Ticket ticket) {
        return TicketSummaryDto.builder()
                .id(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .eventId(ticket.getEventId())
                .eventTitle(ticket.getEventTitle())
                .buyerId(ticket.getBuyerId())
                .buyerName(ticket.getBuyerName())
                .buyerEmail(ticket.getBuyerEmail())
                .ticketCategoryCode(ticket.getTicketCategoryCode())
                .ticketCategoryName(ticket.getTicketCategoryName())
                .price(ticket.getPrice())
                .currency(ticket.getCurrency())
                .status(ticket.getStatus() != null ? ticket.getStatus().name() : null)
                .purchaseDate(ticket.getPurchaseDate())
                .validFrom(ticket.getValidFrom())
                .validUntil(ticket.getValidUntil())
                .qrCode(ticket.getQrCode())
                .build();
    }
}
