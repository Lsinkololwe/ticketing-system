package com.pml.booking.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
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

    private Refund refund = new Refund();

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
         * Processing fee percentage for refunds (deducted from refund amount).
         */
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "1.0")
        private BigDecimal processingFeeRate = BigDecimal.ZERO;
    }
}
