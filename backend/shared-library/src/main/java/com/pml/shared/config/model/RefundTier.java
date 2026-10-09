package com.pml.shared.config.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One step of a refund policy: while the event is at least {@code daysBefore} days away the buyer
 * is refunded {@code percent} of the ticket price. Tiers are evaluated from the largest
 * {@code daysBefore} down, so {@code [{7,100},{1,50}]} reads "full refund until 7 days before,
 * half until 1 day before, nothing after".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundTier {

    private int daysBefore;

    private int percent;
}
