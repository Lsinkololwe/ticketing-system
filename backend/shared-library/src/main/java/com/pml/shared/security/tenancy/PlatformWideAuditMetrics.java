package com.pml.shared.security.tenancy;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * What an operator can watch about reaches that have not yet reached identity.
 *
 * <p>Two gauges say how many rows wait in the outbox and how old the oldest is, and three counters
 * say how many were delivered, how many identity refused for good, and how many times starting a
 * delivery failed. An alert on the age gauge is the signal that identity, Temporal or this
 * service's start-up re-drive is not keeping up.
 *
 * <p>The gauges are read on the scrape thread, which must not wait on the database. Each read
 * returns the last answer and, at most once every few seconds, asks the database for a fresh one
 * in the background, so a value is at most one scrape old and nothing here runs on a timer of its
 * own.
 */
public class PlatformWideAuditMetrics {

    static final String PENDING_METER = "platform.wide.audit.outbox.pending";
    static final String OLDEST_AGE_METER = "platform.wide.audit.outbox.oldest.age.seconds";
    static final String DELIVERED_METER = "platform.wide.audit.delivered";
    static final String REFUSED_METER = "platform.wide.audit.delivery.failed";
    static final String START_FAILURE_METER = "platform.wide.audit.delivery.start.failures";

    /** The shortest time between two background reads of the backlog. */
    private static final Duration MIN_REFRESH = Duration.ofSeconds(5);

    private static final Logger log = LoggerFactory.getLogger(PlatformWideAuditMetrics.class);

    private final String service;
    private final PlatformWideAuditOutbox outbox;
    private final MeterRegistry meters;
    private final Clock clock;
    private final AtomicReference<PlatformWideAuditOutbox.Backlog> backlog =
            new AtomicReference<>(PlatformWideAuditOutbox.Backlog.EMPTY);
    private final AtomicReference<Instant> lastRefresh = new AtomicReference<>(Instant.MIN);
    private final AtomicBoolean refreshing = new AtomicBoolean();

    public PlatformWideAuditMetrics(String service, PlatformWideAuditOutbox outbox, MeterRegistry meters, Clock clock) {
        this.service = service;
        this.outbox = outbox;
        this.meters = meters;
        this.clock = clock;
        if (meters != null) {
            Gauge.builder(PENDING_METER, this, PlatformWideAuditMetrics::pending)
                    .description("Platform-wide reaches kept locally and not yet confirmed by identity")
                    .tag("service", service).register(meters);
            Gauge.builder(OLDEST_AGE_METER, this, PlatformWideAuditMetrics::oldestAgeSeconds)
                    .description("Seconds since the oldest unconfirmed platform-wide reach happened")
                    .baseUnit("seconds").tag("service", service).register(meters);
        }
    }

    double pending() {
        refreshInBackground();
        return backlog.get().pending();
    }

    double oldestAgeSeconds() {
        refreshInBackground();
        Instant oldest = backlog.get().oldest();
        return oldest == null ? 0 : Math.max(0, Duration.between(oldest, clock.instant()).toSeconds());
    }

    private void refreshInBackground() {
        Instant now = clock.instant();
        if (Duration.between(lastRefresh.get(), now).compareTo(MIN_REFRESH) < 0 || !refreshing.compareAndSet(false, true)) {
            return;
        }
        lastRefresh.set(now);
        outbox.backlog().subscribe(
                latest -> {
                    backlog.set(latest);
                    refreshing.set(false);
                },
                failure -> {
                    refreshing.set(false);
                    log.warn("The platform-wide audit backlog could not be read: {}", failure.getClass().getSimpleName());
                },
                () -> refreshing.set(false));
    }

    void delivered() {
        count(DELIVERED_METER, "Platform-wide reaches identity confirmed");
    }

    void refused() {
        count(REFUSED_METER, "Platform-wide reaches identity refused for good; they stay in the outbox as REFUSED");
    }

    void startFailed() {
        count(START_FAILURE_METER, "Deliveries that could not be started; the row waits for the next start-up");
    }

    private void count(String name, String description) {
        if (meters != null) {
            Counter.builder(name).description(description).tag("service", service).register(meters).increment();
        }
    }
}
