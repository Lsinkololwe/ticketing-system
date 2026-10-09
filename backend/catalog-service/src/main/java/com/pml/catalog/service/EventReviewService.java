package com.pml.catalog.service;

import com.pml.catalog.domain.enums.ApprovalAction;
import com.pml.catalog.domain.enums.EscalationStatus;
import com.pml.catalog.domain.model.ApprovalEscalation;
import com.pml.catalog.domain.model.ApprovalTimeline;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.model.TicketTier;
import com.pml.catalog.domain.valueobject.TimelineEvent;
import com.pml.catalog.exception.EventNotFoundException;
import com.pml.catalog.exception.InvalidEventStateException;
import com.pml.catalog.repository.ApprovalTimelineRepository;
import com.pml.shared.workflow.Refusal;
import com.pml.catalog.workflow.approval.ApprovalRules;
import com.pml.catalog.workflow.approval.EventApprovalWorkflow.Decision;
import com.pml.catalog.workflow.approval.EventApprovalWorkflow.Snapshot;
import com.pml.shared.constants.EventStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The MongoDB half of an event review, called only from its workflow's activities.
 *
 * <p>Every state change is one transaction: the event's status moves by compare-and-set on its
 * current status, the timeline row is appended and the claim projection is cleared in the same
 * commit, so a decision and its claim release are never seen apart. A second execution of any
 * method finds the event already moved and appends nothing.
 *
 * <p>The claim itself and the SLA clock belong to the workflow; {@code assignedReviewerId} on the
 * event and on the timeline is their projection, which the queue reads.
 */
@Slf4j
@Service
public class EventReviewService {

    private static final Set<EscalationStatus> OPEN_ESCALATIONS = EnumSet.of(EscalationStatus.PENDING, EscalationStatus.ACKNOWLEDGED);

    private final ReactiveMongoTemplate template;
    private final ApprovalTimelineRepository timelines;
    private final TransactionalOperator transaction;

    /** Every timestamp comes from here, never from the wall clock. */
    private final Clock clock;

    public EventReviewService(ReactiveMongoTemplate template, ApprovalTimelineRepository timelines,
                              TransactionalOperator transaction, Clock clock) {
        this.template = template;
        this.timelines = timelines;
        this.transaction = transaction;
        this.clock = clock;
    }

    public Mono<Snapshot> current(String eventId) {
        return event(eventId).flatMap(this::snapshotOf);
    }

    /** DRAFT or REJECTED → PENDING_APPROVAL, and the SUBMITTED row. */
    public Mono<Snapshot> submit(String eventId, String actorId, long slaDeadlineMillis) {
        Instant now = clock.instant();
        Instant deadline = Instant.ofEpochMilli(slaDeadlineMillis);
        Update update = new Update()
                .set("submittedForApprovalAt", now)
                .set("approvalDeadline", deadline)
                .set("isOverdue", false)
                .unset("rejectedAt")
                .unset("rejectedBy")
                .unset("rejectionReason")
                .inc("submissionCount", 1);
        return transition(eventId, EnumSet.of(EventStatus.DRAFT, EventStatus.REJECTED), EventStatus.PENDING_APPROVAL, update)
                .flatMap(moved -> !moved.moved()
                        ? Mono.just(moved.event())
                        : timelines.findByEventId(eventId)
                                .switchIfEmpty(Mono.fromSupplier(() -> ApprovalTimeline.create(eventId,
                                        moved.event().getTitle(), moved.event().getOrganizerId(), null)))
                                .flatMap(timeline -> {
                                    timeline.recordSubmission(actorId, null, deadline, now);
                                    timeline.setOverdue(false);
                                    timeline.setHasActiveEscalation(false);
                                    timeline.unassignReviewer();
                                    return timelines.save(timeline);
                                })
                                .thenReturn(moved.event()))
                .flatMap(this::snapshotOf)
                .as(transaction::transactional);
    }

    /** CHANGES_REQUESTED → PENDING_APPROVAL, and the RESUBMITTED row. */
    public Mono<Snapshot> resubmit(String eventId, String actorId, long slaDeadlineMillis) {
        Instant now = clock.instant();
        Instant deadline = Instant.ofEpochMilli(slaDeadlineMillis);
        Update update = new Update().set("approvalDeadline", deadline).inc("submissionCount", 1);
        return transition(eventId, EnumSet.of(EventStatus.CHANGES_REQUESTED), EventStatus.PENDING_APPROVAL, update)
                .flatMap(moved -> !moved.moved()
                        ? Mono.just(moved.event())
                        : timeline(eventId, EventStatus.CHANGES_REQUESTED)
                                .flatMap(timeline -> {
                                    timeline.recordResubmission(actorId, null, deadline, now);
                                    return timelines.save(timeline);
                                })
                                .thenReturn(moved.event()))
                .flatMap(this::snapshotOf)
                .as(transaction::transactional);
    }

    /** Projects a claim; the holder re-claiming appends no second row. */
    public Mono<Void> claim(String eventId, String reviewerId, String actorId) {
        Instant now = clock.instant();
        Query pending = byId(eventId).addCriteria(Criteria.where("status").is(EventStatus.PENDING_APPROVAL));
        return template.updateFirst(pending,
                        new Update().set("assignedReviewerId", reviewerId).set("updatedAt", now).inc("version", 1),
                        Event.class)
                .flatMap(result -> result.getMatchedCount() == 0
                        ? refuseState(eventId, "only an event awaiting review is claimed")
                        : timeline(eventId, EventStatus.PENDING_APPROVAL).flatMap(timeline -> {
                            if (reviewerId.equals(timeline.getAssignedReviewerId())) {
                                return Mono.just(timeline);
                            }
                            timeline.assignReviewer(actorId, null, reviewerId, reviewerId, now);
                            return timelines.save(timeline);
                        }).then())
                .as(transaction::transactional);
    }

    /** Clears a claim held by {@code reviewerId}; one no longer held by them changes nothing. */
    public Mono<Void> releaseClaim(String eventId, String reviewerId, boolean expired) {
        if (reviewerId == null) {
            return Mono.empty();
        }
        Instant now = clock.instant();
        Query held = byId(eventId).addCriteria(Criteria.where("assignedReviewerId").is(reviewerId));
        return template.updateFirst(held,
                        new Update().unset("assignedReviewerId").unset("assignedReviewerName").set("updatedAt", now).inc("version", 1),
                        Event.class)
                .then(timelines.findByEventId(eventId))
                .filter(timeline -> reviewerId.equals(timeline.getAssignedReviewerId()))
                .flatMap(timeline -> {
                    timeline.unassignReviewer();
                    timeline.addTimelineEvent(claimEnded(eventId, reviewerId, expired, now));
                    return timelines.save(timeline);
                })
                .then()
                .as(transaction::transactional);
    }

    /**
     * One {@code catalog_approval_escalations} row per level; a repeated level writes
     * nothing.
     *
     * <p>The exists-check is the fast path for the ordinary case — a retried activity, or the SLA
     * check running again after the level is already recorded. The unique {@code {eventId, level}}
     * index is the actual guarantee for two checks racing concurrently: MongoDB aborts a
     * transaction that hits the constraint, so the loser's caller — a Temporal activity —
     * retries, and the retry's exists-check then finds the level the winner recorded
     * and does nothing. A duplicate key error is not caught here: catching it would leave the
     * transaction it aborted half-applied, since MongoDB does not allow a transaction to continue
     * past a write it has already aborted.
     */
    public Mono<Void> escalate(String eventId, int level, String holderId) {
        Instant now = clock.instant();
        Query sameLevel = Query.query(Criteria.where("eventId").is(eventId).and("level").is(level));
        return template.exists(sameLevel, ApprovalEscalation.class)
                .flatMap(already -> already ? Mono.<Void>empty() : recordEscalation(eventId, level, holderId, now))
                .as(transaction::transactional);
    }

    /**
     * A decision to approve first asks whether the event may be — the same precondition
     * the queue would show as outstanding. Reject and request-changes carry no such gate: a
     * reviewer is never trapped by an event's own incompleteness.
     */
    public Mono<Snapshot> approve(Decision decision, long reviewMillis) {
        Instant now = clock.instant();
        Update update = new Update()
                .set("approvedAt", now)
                .set("approvedBy", decision.reviewerId())
                .set("isOverdue", false);
        return approvalPrecondition(decision.eventId())
                .flatMap(refusal -> refusal.<Mono<Snapshot>>map(r -> Mono.error(r.failure()))
                        .orElseGet(() -> decide(decision, EventStatus.APPROVED, update, reviewMillis, now,
                                timeline -> timeline.recordApproval(decision.reviewerId(), null, decision.reason(), now))));
    }

    /**
     * The approval check. Skipped for an event not currently awaiting review, so a retried or
     * already-decided approval is left to {@link #decide}'s own idempotency rather than refused for
     * a tier deactivated afterward.
     */
    private Mono<Optional<Refusal>> approvalPrecondition(String eventId) {
        return event(eventId)
                .switchIfEmpty(Mono.error(new EventNotFoundException(eventId)))
                .flatMap(event -> event.getStatus() != EventStatus.PENDING_APPROVAL
                        ? Mono.just(Optional.<Refusal>empty())
                        : hasPublishedTier(eventId).map(hasPublishedTier -> ApprovalRules.approvalPreconditionRefusal(
                                hasPublishedTier, event.getLocationId(), event.getTotalCapacity())));
    }

    /**
     * What {@code event} lacks before it can be approved — the list the approvals queue shows, from
     * the same rule the approval itself applies.
     */
    public Mono<List<ApprovalRules.ApprovalBlocker>> approvalBlockers(Event event) {
        return hasPublishedTier(event.getId()).map(hasPublishedTier ->
                ApprovalRules.approvalBlockers(hasPublishedTier, event.getLocationId(), event.getTotalCapacity()));
    }

    private Mono<Boolean> hasPublishedTier(String eventId) {
        return template.exists(Query.query(Criteria.where("eventId").is(eventId).and("isActive").is(true)), TicketTier.class);
    }

    public Mono<Snapshot> reject(Decision decision, long reviewMillis) {
        Instant now = clock.instant();
        Update update = new Update()
                .set("rejectedAt", now)
                .set("rejectedBy", decision.reviewerId())
                .set("rejectionReason", decision.reason());
        return decide(decision, EventStatus.REJECTED, update, reviewMillis, now,
                timeline -> timeline.recordRejection(decision.reviewerId(), null, decision.reason(), now));
    }

    public Mono<Snapshot> requestChanges(Decision decision, long reviewMillis) {
        Instant now = clock.instant();
        Update update = new Update()
                .set("changesRequestedAt", now)
                .set("changesRequestedBy", decision.reviewerId())
                .set("changesRequestedComments", decision.reason());
        return decide(decision, EventStatus.CHANGES_REQUESTED, update, reviewMillis, now,
                timeline -> timeline.recordChangesRequested(decision.reviewerId(), null, decision.reason(), now));
    }

    // ---- helpers -------------------------------------------------------------------------------

    /** The decision, its timeline row, the claim release and the escalations' resolution, in one commit. */
    private Mono<Snapshot> decide(Decision decision, EventStatus to, Update update, long reviewMillis, Instant now,
                                  Consumer<ApprovalTimeline> record) {
        update.unset("assignedReviewerId").unset("assignedReviewerName");
        return transition(decision.eventId(), EnumSet.of(EventStatus.PENDING_APPROVAL), to, update)
                .flatMap(moved -> !moved.moved()
                        ? Mono.just(moved.event())
                        : timeline(decision.eventId(), EventStatus.PENDING_APPROVAL)
                                .flatMap(timeline -> {
                                    record.accept(timeline);
                                    stampReviewTime(timeline, reviewMillis);
                                    timeline.unassignReviewer();
                                    timeline.resolveEscalation();
                                    return timelines.save(timeline);
                                })
                                .then(resolveEscalations(decision.eventId(), decision.reviewerId(), now))
                                .thenReturn(moved.event()))
                .flatMap(this::snapshotOf)
                .as(transaction::transactional);
    }

    /**
     * Compare-and-set on the event's status. When nothing matched, the event either already stands
     * where this transition leads — a retried activity — or is somewhere it may not start from.
     */
    private Mono<Moved> transition(String eventId, Set<EventStatus> from, EventStatus to, Update update) {
        Query query = byId(eventId).addCriteria(Criteria.where("status").in(from));
        update.set("status", to).set("updatedAt", clock.instant()).inc("version", 1);
        return template.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Event.class)
                .map(event -> new Moved(event, true))
                .switchIfEmpty(Mono.defer(() -> event(eventId)
                        .switchIfEmpty(Mono.error(new EventNotFoundException(eventId)))
                        .flatMap(event -> event.getStatus() == to
                                ? Mono.just(new Moved(event, false))
                                : Mono.error(new InvalidEventStateException(eventId,
                                        String.valueOf(event.getStatus()), from.toString())))));
    }

    private Mono<Void> recordEscalation(String eventId, int level, String holderId, Instant now) {
        String recipient = ApprovalRules.recipient(level, holderId);
        String reason = "SLA level " + level + ": unresolved after " + ApprovalRules.threshold(level);
        return event(eventId)
                .switchIfEmpty(Mono.error(new EventNotFoundException(eventId)))
                .flatMap(event -> timeline(eventId, event.getStatus()).flatMap(timeline -> {
                    Instant deadline = timeline.getSlaDeadline() != null ? timeline.getSlaDeadline()
                            : event.getApprovalDeadline() != null ? event.getApprovalDeadline() : now;
                    ApprovalEscalation escalation = ApprovalEscalation.create(eventId, event.getTitle(), recipient,
                            recipient, reason, deadline, holderId, null, (int) ApprovalRules.SLA.toHours(), now);
                    escalation.setLevel(level);
                    return template.insert(escalation)
                            .flatMap(saved -> {
                                timeline.markEscalated(saved.getId(), recipient, reason, now);
                                if (level == 1) {
                                    timeline.setOverdue(true);
                                }
                                return timelines.save(timeline);
                            })
                            .then(level == 1 ? markOverdue(eventId, now) : Mono.<Void>empty());
                }))
                .doOnSuccess(done -> log.info("Event {} escalated to level {} ({})", eventId, level, recipient));
    }

    private Mono<Void> markOverdue(String eventId, Instant now) {
        return template.updateFirst(byId(eventId),
                new Update().set("isOverdue", true).set("updatedAt", now).inc("version", 1), Event.class).then();
    }

    private Mono<Void> resolveEscalations(String eventId, String reviewerId, Instant now) {
        Query open = Query.query(Criteria.where("eventId").is(eventId).and("status").in(OPEN_ESCALATIONS));
        Update resolved = new Update()
                .set("status", EscalationStatus.RESOLVED)
                .set("resolvedAt", now)
                .set("resolvedBy", reviewerId)
                .set("resolutionNotes", "Resolved by the review decision");
        return template.updateMulti(open, resolved, ApprovalEscalation.class).then();
    }

    private Mono<Event> event(String eventId) {
        return template.findOne(byId(eventId), Event.class);
    }

    private Mono<ApprovalTimeline> timeline(String eventId, EventStatus status) {
        return timelines.findByEventId(eventId).switchIfEmpty(Mono.fromSupplier(() -> {
            ApprovalTimeline created = ApprovalTimeline.create(eventId, null, null, null);
            created.setCurrentStatus(status);
            return created;
        }));
    }

    private Mono<Snapshot> snapshotOf(Event event) {
        Query highest = Query.query(Criteria.where("eventId").is(event.getId()))
                .with(Sort.by(Sort.Direction.DESC, "level"))
                .limit(1);
        return template.findOne(highest, ApprovalEscalation.class)
                .map(ApprovalEscalation::getLevel)
                .defaultIfEmpty(0)
                .map(level -> new Snapshot(event.getId(), event.getStatus(), millis(event.getSubmittedForApprovalAt()),
                        millis(event.getChangesRequestedAt()), level));
    }

    private static void stampReviewTime(ApprovalTimeline timeline, long reviewMillis) {
        List<TimelineEvent> rows = timeline.getTimelineEvents();
        if (rows == null || rows.isEmpty()) {
            return;
        }
        TimelineEvent decided = rows.get(rows.size() - 1);
        Map<String, Object> metadata = decided.getMetadata() != null ? new HashMap<>(decided.getMetadata()) : new HashMap<>();
        metadata.put("timeToDecisionMs", reviewMillis);
        decided.setMetadata(metadata);
    }

    private static TimelineEvent claimEnded(String eventId, String reviewerId, boolean expired, Instant now) {
        return TimelineEvent.builder()
                .timestamp(now)
                .eventId(eventId)
                .action(expired ? ApprovalAction.CLAIM_EXPIRED : ApprovalAction.CLAIM_RELEASED)
                .actorId(expired ? "SYSTEM" : reviewerId)
                .actorRole(expired ? "SYSTEM" : "ADMIN")
                .description(expired ? "Claim expired" : "Claim released")
                .build();
    }

    private static <T> Mono<T> refuseState(String eventId, String message) {
        return Mono.error(new InvalidEventStateException("Event " + eventId + ": " + message));
    }

    private static Query byId(String eventId) {
        return Query.query(Criteria.where("_id").is(eventId));
    }

    private static long millis(Instant instant) {
        return instant != null ? instant.toEpochMilli() : 0L;
    }

    private record Moved(Event event, boolean moved) {
    }
}
