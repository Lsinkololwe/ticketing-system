package com.pml.shared.event;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * How many times a consumer may retry a transient failure, and how long it waits.
 *
 * <h2>Why this is a value and not four scattered properties</h2>
 * {@code maxDeliveryCount}, exponential backoff and a maximum backoff must be
 * <b>explicit rather than defaulted</b>. Spring Cloud Stream has defaults for all three, and
 * they are the reason a service can look configured while retrying a dead provider ten times a
 * second, or giving up after one attempt. Holding the four numbers together makes the whole
 * budget reviewable in one place and lets {@code ConsumerRetryLintTest} assert that every
 * binding states them.
 *
 * <h2>The maximum backoff is the point of the exercise</h2>
 * Exponential backoff without a ceiling reaches hours. A mobile-money provider that is down for
 * ninety seconds should not leave the platform waiting an hour after it recovers, so the delay
 * climbs and then stops climbing.
 *
 * <h2>Jitter, because retries synchronise</h2>
 * Every consumer that failed against the same outage retries at the same moment and knocks it
 * over again as it comes up. Half-range jitter spreads them.
 *
 * <h2>Bound from {@code platform.events.consumer}</h2>
 * {@link ConsumerRetryAutoConfiguration} binds the four numbers each service states in its
 * {@code application.yml}; the defaults here are {@link #DEFAULT}.
 */
@ConfigurationProperties(prefix = "platform.events.consumer")
public record RetryBudget(@DefaultValue("3") int maxAttempts,
                          @DefaultValue("500ms") Duration initialBackoff,
                          @DefaultValue("10s") Duration maxBackoff,
                          @Name("backoff-multiplier") @DefaultValue("2.0") double multiplier) {

    /**
     * The platform default, matching what the three services declare in {@code application.yml}.
     *
     * <p>Three attempts over roughly two seconds: long enough to ride out a driver reconnect or a
     * leader election, short enough that a genuinely broken consumer reaches its dead letter
     * while an operator is still looking at the alert.</p>
     */
    public static final RetryBudget DEFAULT =
            new RetryBudget(3, Duration.ofMillis(500), Duration.ofSeconds(10), 2.0);

    public RetryBudget {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1, got " + maxAttempts);
        }
        if (initialBackoff.isNegative() || initialBackoff.isZero()) {
            throw new IllegalArgumentException("initialBackoff must be positive, got " + initialBackoff);
        }
        if (maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException(
                    "maxBackoff (%s) is below initialBackoff (%s), so the ceiling would shorten the first wait"
                            .formatted(maxBackoff, initialBackoff));
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException(
                    "multiplier below 1.0 makes each retry sooner than the last, got " + multiplier);
        }
    }

    /**
     * The reactor policy, which retries <b>only</b> what {@link PermanentFailure} does not claim.
     *
     * <p>A malformed envelope retried three times is three identical failures and a slower path
     * to the dead letter that was always coming.</p>
     *
     * <h2>The last failure is propagated as itself</h2>
     * Reactor's default is to wrap it in {@code RetryExhaustedException}, whose message is
     * <em>"Retries exhausted"</em>. That would become the description on every dead letter the
     * platform ever writes — the reason a dead letter must carry, replaced by the fact that there were
     * retries. The override lives here rather than at the call site because a caller who forgets
     * it gets a plausible-looking dead letter that is useless, and nothing fails.
     */
    public Retry toRetry() {
        return Retry.backoff(maxAttempts - 1L, initialBackoff)
                .maxBackoff(maxBackoff)
                .jitter(0.5)
                .filter(failure -> !(failure instanceof PermanentFailure))
                .transientErrors(false)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }
}
