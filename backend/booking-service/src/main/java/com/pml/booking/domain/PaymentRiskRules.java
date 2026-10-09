package com.pml.booking.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * How risky a mobile-money collection looks, from what the platform already knows about it.
 *
 * <p>A rule list and not a model: every flag has a name an operator can read in the attempt, and the
 * score is the sum of the weights of the flags that fired, capped at 100. Nothing here blocks a payment —
 * it ranks attempts for the people reviewing them — so a rule that is too eager costs a look, not a sale.
 */
public final class PaymentRiskRules {

    public enum Level { LOW, MEDIUM, HIGH }

    /** What is known about an attempt and the buyer's recent history when it is assessed. */
    public record Facts(BigDecimal amount,
                        boolean reachedConfirmation,
                        boolean amountVerified,
                        Boolean webhookSignatureValid,
                        int buyerAttemptsLast10Minutes,
                        int buyerFailuresLastHour,
                        int distinctBuyersOnPhoneLast24Hours,
                        int retryCount) {
    }

    public record Assessment(int score, List<String> flags, Level level) {
    }

    public static final String HIGH_VALUE = "HIGH_VALUE";
    public static final String RAPID_ATTEMPTS = "RAPID_ATTEMPTS";
    public static final String BURST_ATTEMPTS = "BURST_ATTEMPTS";
    public static final String REPEATED_FAILURES = "REPEATED_FAILURES";
    public static final String SHARED_PHONE = "SHARED_PHONE";
    public static final String AMOUNT_NOT_VERIFIED = "AMOUNT_NOT_VERIFIED";
    public static final String BAD_WEBHOOK_SIGNATURE = "BAD_WEBHOOK_SIGNATURE";
    public static final String MANY_RETRIES = "MANY_RETRIES";

    /** A collection of at least this much is high value. */
    public static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("2000.00");

    private PaymentRiskRules() {
    }

    public static Assessment assess(Facts facts) {
        List<String> flags = new ArrayList<>();
        int score = 0;
        if (facts.amount() != null && facts.amount().compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            flags.add(HIGH_VALUE);
            score += 20;
        }
        if (facts.buyerAttemptsLast10Minutes() >= 6) {
            flags.add(BURST_ATTEMPTS);
            score += 40;
        } else if (facts.buyerAttemptsLast10Minutes() >= 3) {
            flags.add(RAPID_ATTEMPTS);
            score += 25;
        }
        if (facts.buyerFailuresLastHour() >= 3) {
            flags.add(REPEATED_FAILURES);
            score += 30;
        }
        if (facts.distinctBuyersOnPhoneLast24Hours() >= 3) {
            flags.add(SHARED_PHONE);
            score += 35;
        }
        if (facts.reachedConfirmation() && !facts.amountVerified()) {
            flags.add(AMOUNT_NOT_VERIFIED);
            score += 50;
        }
        if (Boolean.FALSE.equals(facts.webhookSignatureValid())) {
            flags.add(BAD_WEBHOOK_SIGNATURE);
            score += 60;
        }
        if (facts.retryCount() >= 3) {
            flags.add(MANY_RETRIES);
            score += 10;
        }
        int capped = Math.min(100, score);
        return new Assessment(capped, List.copyOf(flags), levelOf(capped));
    }

    public static Level levelOf(int score) {
        return score >= 60 ? Level.HIGH : score >= 30 ? Level.MEDIUM : Level.LOW;
    }
}
