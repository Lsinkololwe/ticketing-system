package com.pml.shared.security.tenancy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Keeps each platform-wide reach in this service's own database, then has it carried to identity.
 *
 * <p>What the administrator's request waits for is one insert into a collection in this service's
 * own database. The trip to identity happens afterwards and independently: a workflow per row keeps
 * trying until identity confirms, so identity being down delays the searchable row but never loses
 * it and never delays the person. If even the local insert fails, the failure surfaces to
 * {@link PlatformWideAccess}, which lets the reach proceed and records an error line and a counter,
 * exactly as it does for any sink that fails.
 *
 * <p>The reach is also written to this service's {@code audit.platform-wide} log first, so the
 * existing log-based alerts keep working beside the searchable row.
 *
 * <p>If starting the delivery fails (Temporal is unreachable), the row stays {@code PENDING}; it is
 * started again by {@link PlatformWideAuditDrain} the next time this service starts.
 */
public class OutboxPlatformWideAuditSink implements PlatformWideAuditSink {

    private static final Logger log = LoggerFactory.getLogger(OutboxPlatformWideAuditSink.class);

    private final PlatformWideAuditOutbox outbox;
    private final PlatformWideAuditDelivery delivery;
    private final PlatformWideAuditMetrics metrics;
    private final LogOnlyPlatformWideAuditSink localLog = new LogOnlyPlatformWideAuditSink();

    public OutboxPlatformWideAuditSink(PlatformWideAuditOutbox outbox, PlatformWideAuditDelivery delivery,
                                       PlatformWideAuditMetrics metrics) {
        this.outbox = outbox;
        this.delivery = delivery;
        this.metrics = metrics;
    }

    @Override
    public Mono<Void> record(PlatformWideAuditRecord record) {
        localLog.write(record);
        return outbox.append(record).doOnNext(this::startInBackground).then();
    }

    /** Off the request's thread and not awaited: a slow or absent Temporal never slows the reach. */
    private void startInBackground(String recordId) {
        Mono.fromRunnable(() -> delivery.start(recordId)).subscribeOn(Schedulers.boundedElastic())
                .subscribe(done -> { }, failure -> {
                    metrics.startFailed();
                    log.warn("Platform-wide reach {} is kept locally and its delivery could not be started "
                            + "({}); it is re-driven when this service next starts", recordId,
                            failure.getClass().getSimpleName());
                });
    }
}
