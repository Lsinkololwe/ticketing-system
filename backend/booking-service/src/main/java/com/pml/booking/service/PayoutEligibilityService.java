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
     * <p>Scoped by organizer: an event belonging to someone else answers
     * {@code NO_ESCROW_ACCOUNT} rather than revealing their balance or hold
     * date.
     */
    Mono<PayoutEligibility> evaluate(String eventId, String organizerId);
}
