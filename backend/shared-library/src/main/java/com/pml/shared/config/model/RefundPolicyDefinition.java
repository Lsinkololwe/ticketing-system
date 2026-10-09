package com.pml.shared.config.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A platform refund policy: the label an organizer picks, the sentence a buyer reads, and the
 * schedule that decides how much comes back. The platform defines these; an organizer chooses one
 * per event. The schedule is data, so the quote a buyer sees and the amount actually refunded are
 * computed by the same rule.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundPolicyDefinition {

    private String label;

    private String summary;

    @Builder.Default
    private List<RefundTier> rules = new ArrayList<>();

    /**
     * The percentage of the price refunded when the event starts {@code daysUntilEvent} days from
     * now. Zero when no tier applies, which is how a policy with no tiers refuses refunds.
     */
    public int percentAt(double daysUntilEvent) {
        if (rules == null) {
            return 0;
        }
        return rules.stream()
                .sorted(Comparator.comparingInt(RefundTier::getDaysBefore).reversed())
                .filter(tier -> daysUntilEvent >= tier.getDaysBefore())
                .mapToInt(RefundTier::getPercent)
                .findFirst()
                .orElse(0);
    }
}
