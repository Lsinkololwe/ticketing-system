package com.pml.identity.web.graphql.dto.platform;

import java.time.Instant;
import java.util.List;

/**
 * The part of the platform rules a signed-out buyer may read: what the reservation, booking and
 * refund screens need before anyone has an account. No commission, payout, escrow, approval or
 * administrator data, and no personal data.
 */
public record PublicPlatformRules(
        long version,
        Instant updatedAt,
        String currency,
        int reservationHoldMinutes,
        int reservationGraceMinutes,
        int maxTicketsPerBooking,
        int refundCutoffHours,
        int rescheduleLimit,
        List<RulesRefundPolicy> refundPolicies
) {}
