package com.pml.booking.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How a refund divides between the platform's commission and the organizer's escrow.
 *
 * <p>The commission record holds the sale as it stands: the price still standing and the commission
 * still owed on it. A refund of the whole of what stands hands back all of that commission and debits
 * the rest of the escrow. A refund of part of it takes the same proportion of each — the commission
 * share rounded once to the ngwee, the escrow debit being whatever of the refund is left, so the two
 * always add up to exactly the refund.
 *
 * @param commissionShare the part of the refund the platform gives back out of its commission
 * @param escrowDebit     the part the organizer's escrow gives back
 * @param whole           whether the refund takes everything that stands (so the commission record closes)
 */
public record RefundSplit(BigDecimal commissionShare, BigDecimal escrowDebit, boolean whole) {

    private static final int SCALE = 2;

    public static RefundSplit of(BigDecimal standingPrice, BigDecimal standingCommission, BigDecimal refund) {
        if (refund.compareTo(standingPrice) >= 0) {
            BigDecimal commission = standingCommission.setScale(SCALE, RoundingMode.HALF_UP);
            return new RefundSplit(commission, standingPrice.subtract(standingCommission).setScale(SCALE, RoundingMode.HALF_UP), true);
        }
        BigDecimal share = refund.multiply(standingCommission).divide(standingPrice, SCALE, RoundingMode.HALF_UP);
        return new RefundSplit(share, refund.subtract(share).setScale(SCALE, RoundingMode.HALF_UP), false);
    }
}
