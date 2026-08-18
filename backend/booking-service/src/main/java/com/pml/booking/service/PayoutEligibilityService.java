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
 * @see <a href="file:../../../../../../../specs/finance/003-payouts-and-settlement/spec.md">ET-FIN-003</a>
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

    /**
     * Evaluate for the escrow account a payout request names.
     *
     * <p>The request path knows the escrow id, not the event id, and looking
     * the event up only to look the escrow back up again would let the two
     * diverge under a concurrent write.
     */
    Mono<PayoutEligibility> evaluateForEscrow(String escrowAccountId, String organizerId);
}
