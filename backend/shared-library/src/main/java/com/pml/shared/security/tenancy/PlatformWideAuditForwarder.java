package com.pml.shared.security.tenancy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends one outbox row to identity's audit trail and records what identity answered.
 *
 * <p>The body is the facts of the row and nothing else, with the row's {@code recordId} so identity
 * can recognise a delivery it has already stored, and the time the reach happened so the row in the
 * trail keeps its original time even when delivery was late. Identity adds its own receipt time.
 *
 * <p>Three outcomes. Identity accepting (or already holding) the record settles the row as
 * {@code DELIVERED}. Identity refusing it as malformed or too old settles it as {@code REFUSED},
 * because asking again cannot change that answer. Anything else (identity down, slow, or failing)
 * leaves the row {@code PENDING}, notes the attempt, and surfaces the failure so the caller retries.
 */
public class PlatformWideAuditForwarder {

    static final String PATH = "/api/internal/audit/platform-wide";

    /** How long one delivery attempt waits for identity. */
    static final Duration ATTEMPT_TIMEOUT = Duration.ofSeconds(10);

    private static final Logger log = LoggerFactory.getLogger(PlatformWideAuditForwarder.class);

    /** What a forward did to the row. */
    public enum Outcome {
        DELIVERED,
        REFUSED,
        /** The row was already delivered or refused, or no longer exists; nothing was sent. */
        NOTHING_TO_SEND
    }

    private final PlatformWideAuditOutbox outbox;
    private final WebClient identity;
    private final PlatformWideAuditMetrics metrics;

    public PlatformWideAuditForwarder(PlatformWideAuditOutbox outbox, WebClient identity, PlatformWideAuditMetrics metrics) {
        this.outbox = outbox;
        this.identity = identity;
        this.metrics = metrics;
    }

    public Mono<Outcome> forward(String recordId) {
        return outbox.find(recordId)
                .filter(entry -> PlatformWideAuditOutbox.PENDING.equals(entry.state()))
                .flatMap(entry -> send(entry)
                        .then(Mono.defer(() -> settle(entry, Outcome.DELIVERED)))
                        .onErrorResume(failure -> failure instanceof WebClientResponseException refusal && isFinal(refusal)
                                ? settle(entry, Outcome.REFUSED)
                                : noteAndFail(entry, failure)))
                .defaultIfEmpty(Outcome.NOTHING_TO_SEND);
    }

    private Mono<Void> send(PlatformWideAuditOutbox.Entry entry) {
        PlatformWideAuditRecord record = entry.record();
        // A map of strings rather than a record: it serializes without bean introspection, and the
        // set of keys is visible in one place.
        Map<String, String> body = new LinkedHashMap<>();
        body.put("recordId", entry.recordId());
        body.put("occurredAt", record.at().toString());
        body.put("actorSub", record.actorSub());
        body.put("role", record.role());
        body.put("service", record.service());
        body.put("reason", record.reason().name());
        body.put("operation", record.operation());
        return identity.post().uri(PATH).bodyValue(body).retrieve().toBodilessEntity()
                .timeout(ATTEMPT_TIMEOUT).then();
    }

    /** A 400 or 422 is identity's final word on this record; every other status may pass with time. */
    private static boolean isFinal(WebClientResponseException failure) {
        int status = failure.getStatusCode().value();
        return status == 400 || status == 422;
    }

    private Mono<Outcome> settle(PlatformWideAuditOutbox.Entry entry, Outcome outcome) {
        PlatformWideAuditRecord record = entry.record();
        if (outcome == Outcome.DELIVERED) {
            return outbox.markDelivered(entry.recordId()).doOnSuccess(done -> metrics.delivered()).thenReturn(outcome);
        }
        log.error("securityIncident=false platform-wide reach REFUSED by identity and kept in the outbox: recordId={} "
                        + "actor={} role={} service={} reason={} operation={} at={}",
                entry.recordId(), record.actorSub(), record.role(), record.service(), record.reason(),
                record.operation(), record.at());
        return outbox.markRefused(entry.recordId()).doOnSuccess(done -> metrics.refused()).thenReturn(outcome);
    }

    private Mono<Outcome> noteAndFail(PlatformWideAuditOutbox.Entry entry, Throwable failure) {
        log.warn("Platform-wide reach {} is still waiting to be delivered to identity: {}",
                entry.recordId(), failure.getClass().getSimpleName());
        return outbox.noteAttempt(entry.recordId()).then(Mono.error(failure));
    }
}
