package com.pml.booking.web.rest;

import com.pml.booking.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
     * The sold-ticket count catalog's {@code unpublishEvent} refuses on, from
     * booking's own inventory rather than catalog's denormalised counter.
     */
    @GetMapping("/sold-count/by-event/{eventId}")
    public Mono<ResponseEntity<Long>> getSoldTicketCountByEvent(@PathVariable String eventId) {
        return ticketService.countSoldByEventId(eventId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.ok(0L));
    }
}
