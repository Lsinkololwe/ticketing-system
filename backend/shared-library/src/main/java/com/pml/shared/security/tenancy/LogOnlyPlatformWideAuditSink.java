package com.pml.shared.security.tenancy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Writes each platform-wide reach as one structured WARN line on {@code audit.platform-wide}.
 *
 * <p>WARN because a reach across every organization is the unusual path and should survive a
 * production log level; {@code securityIncident=false} because it is an authorized use, and the
 * incident marker is reserved for refusals that someone should be paged about.
 */
public final class LogOnlyPlatformWideAuditSink implements PlatformWideAuditSink {

    static final String LOGGER_NAME = "audit.platform-wide";

    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    @Override
    public Mono<Void> record(PlatformWideAuditRecord record) {
        return Mono.fromRunnable(() -> write(record));
    }

    /** Synchronous form, for the workflow path that has no reactive chain to join. */
    void write(PlatformWideAuditRecord record) {
        log.warn("securityIncident=false platform-wide access: actor={} role={} service={} reason={} operation={} at={}",
                record.actorSub(), record.role(), record.service(), record.reason(),
                record.operation(), record.at());
    }
}
