package com.pml.booking.web.graphql.dto.organizer;

import com.pml.booking.domain.PayoutEligibility;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Whether a payout can be requested for one event, for the organizer's screen.
 *
 * <p>Carries the failing reasons rather than a bare boolean so the UI can say
 * "the hold opens on the 14th" instead of greying out a button with no
 * explanation — which produces a support message rather than a wait.
 *
 * @param availableAmount the withdrawable balance; a payout is all of it or none
 * @param opensAt         when the hold elapses; null once it has
 * @param minimumAmount   the floor, so the client can say how far short a balance is
 */
public record PayoutEligibilityResult(
        boolean eligible,
        List<PayoutEligibility.Reason> reasons,
        BigDecimal availableAmount,
        String currency,
        LocalDateTime opensAt,
        BigDecimal minimumAmount
) {
    public static PayoutEligibilityResult from(PayoutEligibility eligibility) {
        return new PayoutEligibilityResult(
                eligibility.eligible(),
                eligibility.reasons(),
                eligibility.availableAmount(),
                eligibility.currency(),
                eligibility.opensAt(),
                eligibility.minimumAmount());
    }
}
