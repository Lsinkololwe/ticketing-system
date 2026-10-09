package com.pml.shared.constants;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The scale, the rounding mode and the currency, decided once.
 *
 * <h2>Why one place</h2>
 * Every rounding is {@code HALF_UP} at scale 2 and <b>applied once</b>.
 * Both halves are easy to lose when each call site decides for itself: a second rounding of an
 * already-rounded value is invisible in review and shifts a half-cent every time it happens, and
 * a site that forgets to round at all leaks sub-cent precision into a {@code Decimal128} column
 * where it will be summed later.
 *
 * <h2>ZMW is a default, not an assumption</h2>
 * Launch is Zambia-only, and every monetary field still carries a {@code currency} sibling. The two are not in tension: the field exists so that adding a second currency
 * is a data migration rather than an archaeology exercise, and single-currency assumptions are
 * cheap to make and expensive to remove.
 */
public final class Money {

    /** Ngwee. Two decimal places, and no monetary value is stored with more. */
    public static final int SCALE = 2;

    /**
     * {@code HALF_UP} rather than {@code HALF_EVEN}.
     *
     * <p>Banker's rounding distributes the bias better over a large number of roundings, which
     * is the right choice for statistics and the wrong one here: a customer shown a K0.50 fee
     * expects to be charged K0.50, and a rounding rule that sometimes rounds it down is a
     * discrepancy someone has to explain. Consistency with the displayed figure wins.</p>
     */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /** ISO-4217 for the Zambian kwacha. The default for every {@code currency} sibling. */
    public static final String DEFAULT_CURRENCY = "ZMW";

    private Money() {
    }

    /** Rounds to the platform's scale. Null in, null out — an absent amount is not zero. */
    public static BigDecimal round(BigDecimal amount) {
        return amount == null ? null : amount.setScale(SCALE, ROUNDING);
    }

    /**
     * Percentage of an amount, rounded once at the end.
     *
     * <p>Rounding the percentage first and then multiplying, or rounding an intermediate
     * product, produces a different answer than rounding the final figure — which is how a
     * commission and the ledger entry that records it end up a cent apart.</p>
     */
    public static BigDecimal percentageOf(BigDecimal amount, BigDecimal percentage) {
        if (amount == null || percentage == null) {
            return null;
        }
        return round(amount.multiply(percentage).divide(BigDecimal.valueOf(100), SCALE + 4, ROUNDING));
    }
}
