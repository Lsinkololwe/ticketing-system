package com.pml.shared.config.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The runtime rules organizers and buyers must obey, held as the {@code rules} section of the
 * single platform settings document (ET-ADM-002). Catalog is the only writer; every other service
 * reads it through {@code PlatformConfigurationReader}.
 *
 * <p>The commission default, the minimum payout and the approval SLA are not repeated here: they
 * already live in the {@code payment} section and the approval fields of the same document, and a
 * second copy would be a second source of truth.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformRulesSection {

    /** The refund policy codes the platform defines. Closed: a code outside it is refused. */
    public static final List<String> POLICY_CODES = List.of("FLEXIBLE", "MODERATE", "STRICT", "NO_REFUNDS");

    /** Minutes a buyer's reserved tickets are held while they pay. */
    private int reservationHoldMinutes;

    /** Extra minutes granted to a payment already in flight when the hold ends. */
    private int reservationGraceMinutes;

    /** Days a sale's proceeds stay in escrow before they can be paid out. */
    private int escrowHoldDays;

    /** Hours before the event after which a buyer can no longer ask for a refund. */
    private int refundCutoffHours;

    private int maxTicketsPerBooking;

    /** How many times an organizer may move an event's date. */
    private int rescheduleLimit;

    /** ISO-4217 code. */
    private String currency;

    /** Keyed by {@link #POLICY_CODES}. */
    private Map<String, RefundPolicyDefinition> refundPolicies;

    /** Bumped on every save, so a client can tell it is looking at a changed set of rules. */
    private long version;

    /** The platform's documented starting values; seeded once, then edited by an administrator. */
    public static PlatformRulesSection defaults() {
        Map<String, RefundPolicyDefinition> policies = new LinkedHashMap<>();
        policies.put("FLEXIBLE", policy("Flexible", "Full refund until 24 hours before the event.",
                new RefundTier(1, 100)));
        policies.put("MODERATE", policy("Moderate",
                "Full refund until 7 days before, 50% until 24 hours before.",
                new RefundTier(7, 100), new RefundTier(1, 50)));
        policies.put("STRICT", policy("Strict", "50% refund until 7 days before, then no refund.",
                new RefundTier(7, 50)));
        policies.put("NO_REFUNDS", policy("No refunds",
                "Tickets cannot be refunded unless the event is cancelled."));
        return PlatformRulesSection.builder()
                .reservationHoldMinutes(10)
                .reservationGraceMinutes(5)
                .escrowHoldDays(7)
                .refundCutoffHours(24)
                .maxTicketsPerBooking(8)
                .rescheduleLimit(3)
                .currency("ZMW")
                .refundPolicies(policies)
                .version(1)
                .build();
    }

    private static RefundPolicyDefinition policy(String label, String summary, RefundTier... tiers) {
        return RefundPolicyDefinition.builder()
                .label(label)
                .summary(summary)
                .rules(new java.util.ArrayList<>(List.of(tiers)))
                .build();
    }
}
