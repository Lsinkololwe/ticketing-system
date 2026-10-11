package com.pml.booking.service;

import com.pml.booking.domain.PayoutEligibility;
import reactor.core.publisher.Mono;

/**
 * Answers "may this organizer draw a payout from this event, and if not, why".
 *
 * <p>The same evaluation runs at request time and is exposed to the organizer
 * before they request, so the button they see and the answer they get agree.
 * A disabled button that names its reason is better than an enabled one whose
 * request the server then rejects.
 *
 * @see PayoutEligibility
 */
public interface PayoutEligibilityService {

    /**
     * Evaluate eligibility for one event.
     *
     * <p>Decided by the organization that owns the event's money: the caller must hold the
     * payout permission there. Anyone else is answered {@code NO_ESCROW_ACCOUNT} rather than
     * being shown a balance or hold date.
     */
    Mono<PayoutEligibility> evaluate(String eventId, String userId);
}
