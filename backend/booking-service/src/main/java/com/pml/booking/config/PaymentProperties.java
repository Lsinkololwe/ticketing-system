package com.pml.booking.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Duration;

/**
 * Payment Processing Configuration Properties
 *
 * Business Intent: Centralizes all payment-related configuration including
 * commission rates, timeouts, and retry policies. These values directly impact
 * revenue and user experience, so they should be carefully managed.
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {

    /**
     * Payment intent expiration timeout.
     * After this duration, pending payments are marked as expired.
     */
    private Duration timeout = Duration.ofMinutes(15);

    /**
     * Commission configuration for platform fees.
     */
    private Commission commission = new Commission();

    /**
     * Escrow configuration for organizer funds.
     */
    private Escrow escrow = new Escrow();

    /**
     * Refund policy configuration.
     */
    private Refund refund = new Refund();

    @Data
    public static class Commission {
        /**
         * Platform commission rate (e.g., 0.05 = 5%).
         * Applied to each ticket sale.
         */
        @DecimalMin(value = "0.0", message = "Commission rate cannot be negative")
        @DecimalMax(value = "1.0", message = "Commission rate cannot exceed 100%")
        private BigDecimal rate = new BigDecimal("0.05");

        /**
         * Minimum commission amount per transaction.
         */
        @DecimalMin(value = "0.0")
        private BigDecimal minimum = BigDecimal.ZERO;

        /**
         * Maximum commission amount per transaction (0 = no limit).
         */
        @DecimalMin(value = "0.0")
        private BigDecimal maximum = BigDecimal.ZERO;
    }

    @Data
    public static class Escrow {
        /**
         * Hold period after event completion before payout is eligible.
         * This protects against post-event refund claims.
         */
        private Duration holdPeriod = Duration.ofDays(7);

        /**
         * Allow partial payouts from escrow.
         */
        private boolean allowPartialPayout = true;

        /**
         * Minimum payout amount.
         */
        @Positive
        private BigDecimal minimumPayoutAmount = new BigDecimal("10.00");
    }

    @Data
    public static class Refund {
        /**
         * Cutoff hours before event when refunds are no longer allowed.
         */
        @Positive
        private int cutoffHoursBeforeEvent = 24;

        /**
         * Allow refunds after event has started.
         */
        private boolean allowPostEventRefund = false;

        /**
         * Require manual approval for refunds.
         */
        private boolean requireApproval = true;

        /**
         * Auto-approve refunds below this amount.
         */
        private BigDecimal autoApproveThreshold = new BigDecimal("100.00");

        /**
         * Processing fee percentage for refunds (deducted from refund amount).
         */
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "1.0")
        private BigDecimal processingFeeRate = BigDecimal.ZERO;
    }
}
