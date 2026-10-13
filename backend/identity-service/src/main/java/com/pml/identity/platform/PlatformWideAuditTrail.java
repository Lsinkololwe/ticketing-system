package com.pml.identity.platform;

import com.pml.identity.domain.model.AuditLog;
import com.pml.shared.security.tenancy.LogOnlyPlatformWideAuditSink;
import com.pml.shared.security.tenancy.PlatformWideAuditRecord;
import com.pml.shared.security.tenancy.PlatformWideAuditSink;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * Where every platform-wide reach becomes a searchable row in the audit trail.
 *
 * <p>Identity's own reaches are written here directly; booking's and catalog's arrive through
 * {@code InternalPlatformWideAuditController}, which hands the same record to {@link #receive}. One
 * writer means one row shape: who (the subject and the role they held), which service, why (the
 * fixed reason), which operation, when it happened and when identity stored it. The row never holds
 * a request, a filter value, a token or a contact detail, and it is only ever inserted: no code path
 * updates or removes it, and no expiry applies to it.
 *
 * <p>A reach made in another service can reach identity late: that service keeps it locally and
 * retries until identity answers. So a row carries two times. {@code timestamp} is when the reach
 * happened, as the sender recorded it, and {@code receivedAt} is when identity stored it, from
 * identity's own clock. A sender cannot place a row at a time of its choosing: the time it claims must
 * lie within {@link #MAX_AGE} before identity's now (and no further than {@link #CLOCK_SKEW} ahead),
 * and the real receipt time is always stored beside it.
 *
 * <p>A reach has a permanent {@code recordId}, assigned where it happened. Storing the same
 * {@code recordId} twice leaves the first row alone and still answers success, which is what lets the
 * sender repeat a delivery whose answer it never saw.
 *
 * <p>Defining this bean is what makes the shared default (a log line) back off. The log line is
 * still written, so the existing log-based alerts keep working beside the searchable row.
 */
@Service
public class PlatformWideAuditTrail implements PlatformWideAuditSink {

    /** The resource type shown for these rows on the audit page's "record" column. */
    static final String RESOURCE_TYPE = "PlatformWideReach";

    /**
     * How long before receipt a delivered reach may have happened. Thirty days is longer than any outage
     * the platform is expected to ride out; a sender that is older than this holds the row as refused
     * and an operator is told, rather than identity accepting a row at an arbitrary past time.
     */
    public static final Duration MAX_AGE = Duration.ofDays(30);

    /** How far ahead of identity's own clock a sender's time may be: only the drift between two servers. */
    public static final Duration CLOCK_SKEW = Duration.ofMinutes(2);

    private final ReactiveMongoTemplate template;
    private final Clock clock;
    private final LogOnlyPlatformWideAuditSink log = new LogOnlyPlatformWideAuditSink();

    public PlatformWideAuditTrail(ReactiveMongoTemplate template, Clock clock) {
        this.template = template;
        this.clock = clock;
    }

    /**
     * Identity's own reach. The log line comes first so the fact exists somewhere searchable even if
     * the insert fails; the failure then surfaces to {@code PlatformWideAccess}, which logs it as an
     * error, counts it and lets the administrator carry on.
     */
    @Override
    public Mono<Void> record(PlatformWideAuditRecord record) {
        return log.record(record).then(Mono.defer(() -> insert(UUID.randomUUID().toString(), record))).then();
    }

    /**
     * A reach delivered by another service. Answers {@code true} when this call stored the row and
     * {@code false} when the {@code recordId} was already held; both are a success for the sender.
     */
    public Mono<Boolean> receive(String recordId, PlatformWideAuditRecord record) {
        return insert(recordId, record).flatMap(stored -> stored ? log.record(record).thenReturn(true) : Mono.just(false));
    }

    private Mono<Boolean> insert(String recordId, PlatformWideAuditRecord record) {
        AuditLog row = AuditLog.success(AuditLog.AuditAction.TENANT_BOUNDARY_CROSSED, null, record.actorSub(), record.at());
        row.setResourceType(RESOURCE_TYPE);
        row.setActorRole(record.role());
        row.setService(record.service());
        row.setReason(record.reason() == null ? null : record.reason().name());
        row.setOperation(record.operation());
        row.setRecordId(recordId);
        row.setReceivedAt(clock.instant());
        return template.insert(row).thenReturn(true)
                .onErrorResume(DuplicateKeyException.class, duplicate -> Mono.just(false));
    }
}
