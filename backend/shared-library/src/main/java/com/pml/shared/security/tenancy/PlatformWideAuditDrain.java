package com.pml.shared.security.tenancy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Starts the delivery of every row still waiting, once, when this service has started.
 *
 * <p>This is the only way a row whose delivery never got started (Temporal was unreachable at the
 * moment of the reach, or the service stopped between the local write and the start) is picked up.
 * It is not a sweep: it runs once per process start, reads at most {@link #LIMIT} rows, has no timer,
 * and does nothing a delivery already running does not. Starting a delivery that is already running
 * simply joins it, so every instance doing this at start-up is harmless.
 *
 * <p>If Temporal is still unreachable the first failed start ends the pass; the rows stay
 * {@code PENDING} and wait for the next start.
 */
public class PlatformWideAuditDrain {

    /** Rows re-driven at one start-up; the oldest first. */
    static final int LIMIT = 1000;

    private static final Logger log = LoggerFactory.getLogger(PlatformWideAuditDrain.class);

    private final PlatformWideAuditOutbox outbox;
    private final PlatformWideAuditDelivery delivery;
    private final PlatformWideAuditMetrics metrics;

    public PlatformWideAuditDrain(PlatformWideAuditOutbox outbox, PlatformWideAuditDelivery delivery,
                                  PlatformWideAuditMetrics metrics) {
        this.outbox = outbox;
        this.delivery = delivery;
        this.metrics = metrics;
    }

    @EventListener(ApplicationReadyEvent.class)
    void atStartUp() {
        redrive().subscribe(started -> {
            if (started > 0) {
                log.info("Started the delivery of {} platform-wide reaches that were still waiting", started);
            }
        });
    }

    /** Starts every waiting row; answers how many starts succeeded before the pass ended. Never fails. */
    public Mono<Long> redrive() {
        AtomicLong started = new AtomicLong();
        return outbox.pendingIds(LIMIT)
                .concatMap(recordId -> Mono.fromRunnable(() -> delivery.start(recordId))
                        .subscribeOn(Schedulers.boundedElastic()).doOnSuccess(done -> started.incrementAndGet()))
                .then(Mono.fromSupplier(started::get))
                .onErrorResume(failure -> {
                    metrics.startFailed();
                    log.warn("Waiting platform-wide reaches could not all be started at start-up ({}); "
                            + "the rest wait for the next start", failure.getClass().getSimpleName());
                    return Mono.just(started.get());
                });
    }
}
