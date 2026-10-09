package com.pml.shared.config.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Platform-wide payment / payout / commission defaults.
 *
 * <p>Embedded section of the single {@code platform_configuration} document. This is the
 * <b>source of truth</b> for the financial defaults applied to a new organization's payout
 * configuration at creation time — the default commission rate, payout method, payout
 * schedule and minimum payout amount.</p>
 *
 * <p>Lives in the shared library so the owning service (catalog) and the consuming service
 * (identity) map the same nested type against the same collection, rather than each defining
 * a private configuration collection. Payout method/schedule are held as {@code String} to
 * keep this value object free of any service-specific enum; the consumer converts them to its
 * own enum and the collection's JSON Schema constrains the allowed values.</p>
 *
 * <p>No defaults are baked into the fields — the values are seeded into the configuration
 * document (see the owning service's {@code createDefault()} factory).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformPaymentDefaults {

    /** Default platform commission rate (e.g. {@code 0.05} = 5%). */
    private Double commissionRate;

    /** Default payout method for a new organization (e.g. {@code MOBILE_MONEY}). */
    private String payoutMethod;

    /** Default payout schedule for a new organization (e.g. {@code WEEKLY}). */
    private String payoutSchedule;

    /**
     * Minimum payout amount, in ZMW.
     *
     * <p>{@code BigDecimal} because it is compared against an escrow balance that is also a
     * BigDecimal, and a {@code Double} threshold has to be converted to make that comparison.
     * K0.10 has no exact binary representation, so a payout of precisely the minimum can be
     * refused — a rejection with no explanation anyone can find in the numbers.</p>
     */
    private java.math.BigDecimal minimumPayoutAmount;
}
