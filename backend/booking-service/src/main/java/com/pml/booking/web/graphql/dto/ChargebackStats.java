package com.pml.booking.web.graphql.dto;

import java.math.BigDecimal;

/**
 * Chargeback statistics summary.
 *
 * @param totalCount Total number of chargebacks
 * @param pendingCount Chargebacks pending review
 * @param disputedCount Chargebacks being disputed
 * @param wonCount Disputes won
 * @param lostCount Disputes lost or accepted
 * @param totalAmount Total chargeback amount
 * @param recoveredAmount Total amount recovered
 * @param writtenOffAmount Total amount written off
 * @param pendingRecoveryAmount Amount pending recovery
 * @param averageChargebackAmount Average chargeback amount
 * @param chargebackRate Chargeback rate as percentage of transactions
 *
 * @since 1.0.0
 */
public record ChargebackStats(
    long totalCount,
    long pendingCount,
    long disputedCount,
    long wonCount,
    long lostCount,
    BigDecimal totalAmount,
    BigDecimal recoveredAmount,
    BigDecimal writtenOffAmount,
    BigDecimal pendingRecoveryAmount,
    BigDecimal averageChargebackAmount,
    BigDecimal chargebackRate
) {
    /**
     * Create empty statistics.
     */
    public static ChargebackStats empty() {
        return new ChargebackStats(
            0, 0, 0, 0, 0,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO
        );
    }

    /**
     * Calculate win rate as percentage.
     */
    public BigDecimal getWinRate() {
        long disputed = wonCount + lostCount;
        if (disputed == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(wonCount * 100.0 / disputed);
    }

    /**
     * Calculate recovery rate as percentage.
     */
    public BigDecimal getRecoveryRate() {
        if (totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return recoveredAmount.multiply(BigDecimal.valueOf(100))
                .divide(totalAmount, 2, java.math.RoundingMode.HALF_UP);
    }
}
