package com.pml.identity.web.graphql.dto.platform;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The values organizers and buyers must obey, as the platform currently has them. No personal data.
 * Commission figures are percentages (5 means 5%).
 */
public record PlatformRules(
        long version,
        Instant updatedAt,
        String updatedBy,
        String currency,
        double commissionDefault,
        Double commissionRate,
        BigDecimal minimumPayout,
        int reservationHoldMinutes,
        int reservationGraceMinutes,
        int escrowHoldDays,
        int refundCutoffHours,
        int maxTicketsPerBooking,
        int rescheduleLimit,
        List<RulesRefundPolicy> refundPolicies,
        PlatformRulesApproval approval
) {}
