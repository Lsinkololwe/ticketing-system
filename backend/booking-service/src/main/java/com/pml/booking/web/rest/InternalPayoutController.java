package com.pml.booking.web.rest;

import com.pml.booking.service.PayoutRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Internal Payout Controller
 *
 * <p>Provides internal APIs for other microservices to read booking's payout state. Reached only
 * from {@code /api/internal/**}, which {@code SecurityConfig} restricts to
 * {@code SCOPE_internal-read}/{@code SCOPE_internal-write}/{@code ROLE_INTERNAL_SERVICE}.
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/payouts")
@RequiredArgsConstructor
public class InternalPayoutController {

    private final PayoutRequestService payoutRequestService;

    /**
     * Whether the event has a payout request that has not yet reached a terminal
     * state. Catalog's event cancellation refuses while this is true, so a cancellation and a
     * settlement can never race for the same escrow balance.
     */
    @GetMapping("/by-event/{eventId}/open")
    public Mono<ResponseEntity<Boolean>> hasOpenPayoutRequest(@PathVariable String eventId) {
        log.debug("Internal request for open payout state of event: {}", eventId);
        return payoutRequestService.hasOpenPayoutRequest(eventId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.ok(false));
    }
}
