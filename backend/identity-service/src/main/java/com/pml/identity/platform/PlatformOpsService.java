package com.pml.identity.platform;

import com.pml.identity.domain.enums.AlertSeverity;
import com.pml.identity.domain.enums.AlertStatus;
import com.pml.identity.domain.enums.AnnouncementSegment;
import com.pml.identity.domain.model.AuditLog;
import com.pml.identity.domain.model.SystemAlert;
import com.pml.identity.domain.model.SystemAnnouncement;
import com.pml.identity.service.AdminAuditService;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/** System alerts and announcements: the platform's way of telling operators and users something. */
@Service
@RequiredArgsConstructor
public class PlatformOpsService {

    private final ReactiveMongoTemplate template;
    private final AdminAuditService audit;
    private final Clock clock;

    // ---- alerts ------------------------------------------------------------------------------------

    /**
     * Raises an alert, or, when one is already open for this {@code (source, key)}, counts another
     * occurrence of it. Safe under concurrent callers: the unique partial index on open alerts
     * makes a lost race an update instead of a second alert.
     */
    public Mono<SystemAlert> raise(String source, String key, AlertSeverity severity, String title, String message) {
        Instant now = clock.instant();
        Query open = Query.query(Criteria.where("source").is(source).and("key").is(key)
                .and("status").in(AlertStatus.OPEN, AlertStatus.ACKNOWLEDGED));
        Update bump = new Update().inc("occurrences", 1).set("lastSeenAt", now).set("severity", severity);
        return template.findAndModify(open, bump, org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true),
                        SystemAlert.class)
                .switchIfEmpty(Mono.defer(() -> template.insert(SystemAlert.builder()
                                .source(source).key(key).severity(severity).title(title).message(message)
                                .status(AlertStatus.OPEN).occurrences(1).raisedAt(now).lastSeenAt(now).build())
                        // Two raisers racing: the loser's insert is refused by the unique index and counts as an occurrence.
                        .onErrorResume(DuplicateKeyException.class, race -> template.findAndModify(open, bump,
                                org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true),
                                SystemAlert.class))));
    }

    /** Closes the open alert for a condition that has cleared. Nothing to close is not an error. */
    public Mono<Void> resolve(String source, String key) {
        return template.updateMulti(Query.query(Criteria.where("source").is(source).and("key").is(key)
                                .and("status").in(AlertStatus.OPEN, AlertStatus.ACKNOWLEDGED)),
                        new Update().set("status", AlertStatus.RESOLVED).set("resolvedAt", clock.instant()),
                        SystemAlert.class)
                .then();
    }

    public Flux<SystemAlert> alerts(AlertStatus status, AlertSeverity severity) {
        Criteria criteria = new Criteria();
        if (status != null) criteria = criteria.and("status").is(status);
        if (severity != null) criteria = criteria.and("severity").is(severity);
        return template.find(new Query(criteria).with(Sort.by(Sort.Direction.DESC, "lastSeenAt")), SystemAlert.class);
    }

    /** Marks an open alert as seen by an administrator; acknowledging twice keeps the first acknowledgement. */
    public Mono<SystemAlert> acknowledge(String alertId, String actorId) {
        return template.findById(alertId, SystemAlert.class)
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.RESOURCE_CONFLICT, "no such alert")))
                .flatMap(alert -> {
                    if (alert.getStatus() != AlertStatus.OPEN) {
                        return Mono.just(alert);
                    }
                    return template.findAndModify(
                                    Query.query(Criteria.where("_id").is(alertId).and("status").is(AlertStatus.OPEN)),
                                    new Update().set("status", AlertStatus.ACKNOWLEDGED)
                                            .set("acknowledgedAt", clock.instant()).set("acknowledgedBy", actorId),
                                    org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true),
                                    SystemAlert.class)
                            .switchIfEmpty(template.findById(alertId, SystemAlert.class))
                            .flatMap(saved -> audit.record(AuditLog.AuditAction.SYSTEM_ALERT_ACKNOWLEDGED, "SystemAlert",
                                    alertId, actorId, Map.of("alertKey", saved.getKey())).thenReturn(saved));
                });
    }

    // ---- announcements -----------------------------------------------------------------------------

    public Mono<SystemAnnouncement> broadcast(String title, String message, AnnouncementSegment segment,
                                              AlertSeverity severity, Instant startsAt, Instant endsAt, String actorId) {
        Instant now = clock.instant();
        Instant start = startsAt != null ? startsAt : now;
        AnnouncementRules.validate(title, message, start, endsAt);
        return template.insert(SystemAnnouncement.builder()
                        .title(title.trim()).message(message.trim())
                        .segment(segment != null ? segment : AnnouncementSegment.ALL)
                        .severity(severity != null ? severity : AlertSeverity.INFO)
                        .startsAt(start).endsAt(endsAt).createdBy(actorId).createdAt(now).build())
                .flatMap(saved -> audit.record(AuditLog.AuditAction.ANNOUNCEMENT_PUBLISHED, "SystemAnnouncement",
                        saved.getId(), actorId, Map.of("segment", saved.getSegment().name(),
                                "startsAt", String.valueOf(saved.getStartsAt()))).thenReturn(saved));
    }

    public Mono<SystemAnnouncement> cancel(String announcementId, String actorId) {
        return template.findAndModify(
                        Query.query(Criteria.where("_id").is(announcementId).and("cancelledAt").is(null)),
                        new Update().set("cancelledAt", clock.instant()).set("cancelledBy", actorId),
                        org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true),
                        SystemAnnouncement.class)
                .switchIfEmpty(template.findById(announcementId, SystemAnnouncement.class))
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.RESOURCE_CONFLICT, "no such announcement")));
    }

    /** Every announcement, newest first, for the administrator's list. */
    public Flux<SystemAnnouncement> all() {
        return template.find(new Query().with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(200), SystemAnnouncement.class);
    }

    /** What is on screen now for a caller in these segments. */
    public Flux<SystemAnnouncement> activeFor(Set<AnnouncementSegment> segments) {
        Instant now = clock.instant();
        return template.find(Query.query(new Criteria().andOperator(
                        Criteria.where("cancelledAt").is(null),
                        Criteria.where("startsAt").lte(now),
                        Criteria.where("segment").in(segments),
                        new Criteria().orOperator(Criteria.where("endsAt").is(null), Criteria.where("endsAt").gt(now))))
                .with(Sort.by(Sort.Direction.DESC, "startsAt")), SystemAnnouncement.class);
    }
}
