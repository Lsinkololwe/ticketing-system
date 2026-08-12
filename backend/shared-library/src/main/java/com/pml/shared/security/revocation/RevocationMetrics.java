package com.pml.shared.security.revocation;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer instrumentation for the revocation control.
 *
 * <p>Exposed through the service's existing Prometheus endpoint. These counters are the signal
 * that reports whether revocation is currently being enforced.</p>
 *
 * <h2>Alert on these</h2>
 * <ul>
 *   <li>{@code identity_revocation_degraded_total} rising — Redis is gone, every check is
 *       hitting MongoDB. Correctness is intact; latency is not.</li>
 *   <li>{@code identity_revocation_unavailable_total} rising — <b>page someone</b>. Neither
 *       store can answer, so sensitive operations are being refused.</li>
 *   <li>{@code identity_revocation_writes_total{outcome="durable_failed"}} non-zero — a
 *       revocation was requested and could not be persisted. That token is still live.</li>
 * </ul>
 */
public class RevocationMetrics {

    private final MeterRegistry registry;

    /** Mirrors the last check's source so the health indicator can report without probing. */
    private final AtomicLong lastDegradedAt = new AtomicLong(0);
    private final AtomicLong lastUnavailableAt = new AtomicLong(0);

    public RevocationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * One revocation check completed.
     *
     * @param source   {@code redis} or {@code mongo}, or {@code none} when nothing could answer
     * @param decision the outcome the caller will act on
     */
    public void checkCompleted(String source, RevocationDecision decision) {
        Counter.builder("identity.revocation.checks")
                .description("Revocation checks by the store that answered and the decision reached")
                .tag("source", source)
                .tag("decision", decision.name().toLowerCase())
                .register(registry)
                .increment();
    }

    /**
     * The cache could not answer and the durable store was used instead.
     *
     * @param reason {@code timeout}, {@code error} or {@code circuit_open}
     */
    public void degraded(String reason) {
        lastDegradedAt.set(System.currentTimeMillis());
        Counter.builder("identity.revocation.degraded")
                .description("Checks that fell back from the Redis cache to the durable store")
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    /** Neither store answered, so fail-closed operations are being refused. */
    public void unavailable() {
        lastUnavailableAt.set(System.currentTimeMillis());
        Counter.builder("identity.revocation.unavailable")
                .description("Checks where neither the cache nor the durable store could answer")
                .register(registry)
                .increment();
    }

    /**
     * A revocation write finished.
     *
     * @param outcome {@code ok}, {@code cache_failed} (durable write landed, cache did not) or
     *                {@code durable_failed} (nothing landed — the revocation was lost)
     */
    public void writeCompleted(String outcome) {
        Counter.builder("identity.revocation.writes")
                .description("Revocation writes by outcome")
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    /** A guarded operation was refused. */
    public void denied(String operation, RevocationDecision decision) {
        Counter.builder("identity.revocation.denials")
                .description("Sensitive operations refused by the fail-closed guard")
                .tag("operation", operation)
                .tag("decision", decision.name().toLowerCase())
                .register(registry)
                .increment();
    }

    /** Wall-clock time of a full check, cache and fallback included. */
    public Timer checkTimer() {
        return Timer.builder("identity.revocation.check.duration")
                .description("End-to-end duration of a revocation check")
                .register(registry);
    }

    /** Epoch millis of the last cache fallback, or 0. Used by the health indicator. */
    public long lastDegradedAt() {
        return lastDegradedAt.get();
    }

    /** Epoch millis of the last total outage, or 0. Used by the health indicator. */
    public long lastUnavailableAt() {
        return lastUnavailableAt.get();
    }
}
