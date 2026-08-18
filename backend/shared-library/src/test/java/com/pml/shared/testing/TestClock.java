package com.pml.shared.testing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A {@link Clock} that does not move unless a test moves it.
 *
 * <h2>Why the platform can be tested at all</h2>
 * ET-PLT-001 R3 removes every inline {@code Instant.now()} and injects a
 * {@code Clock} instead. This is what that buys: a reservation can be asserted
 * live at 9:59 and expired at 10:01 without sleeping for ten minutes, and a
 * sales window can be driven across its close in a millisecond.
 *
 * <p>Every boundary in the corpus is specified on <em>both</em> sides for the
 * same reason — a test that only checks "expired after eleven minutes" passes on
 * an implementation that expires after thirty seconds.
 *
 * <h2>Deliberately mutable</h2>
 * {@link Clock} is documented as immutable, and this breaks that on purpose: the
 * whole point is that a test advances it while a service holds a reference. It
 * is confined to test scope and must never reach a service's bean graph in
 * production.
 */
public final class TestClock extends Clock {

    private final ZoneId zone;

    private volatile Instant instant;

    private TestClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    /** Frozen at an explicit instant, UTC. */
    public static TestClock frozenAt(Instant instant) {
        return new TestClock(instant, ZoneId.of("UTC"));
    }

    /** Frozen at an ISO-8601 instant, e.g. {@code "2026-03-01T18:00:00Z"}. */
    public static TestClock frozenAt(String iso8601) {
        return frozenAt(Instant.parse(iso8601));
    }

    /**
     * Moves time forward. Negative durations are rejected: a test that needs to
     * go backwards is expressing a different scenario and should say so with
     * {@link #setTo(Instant)}, rather than quietly rewinding a clock some
     * service has already read.
     */
    public TestClock advance(Duration amount) {
        if (amount.isNegative()) {
            throw new IllegalArgumentException(
                    "advance() only moves forward; use setTo() to express a different starting point");
        }
        this.instant = this.instant.plus(amount);
        return this;
    }

    public TestClock setTo(Instant newInstant) {
        this.instant = newInstant;
        return this;
    }

    /** Advances to one millisecond <em>before</em> the given instant — the "still valid" side of a boundary. */
    public TestClock justBefore(Instant boundary) {
        return setTo(boundary.minusMillis(1));
    }

    /** Advances to one millisecond <em>after</em> the given instant — the "now expired" side of a boundary. */
    public TestClock justAfter(Instant boundary) {
        return setTo(boundary.plusMillis(1));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new TestClock(instant, newZone);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public String toString() {
        return "TestClock[" + instant + "]";
    }
}
