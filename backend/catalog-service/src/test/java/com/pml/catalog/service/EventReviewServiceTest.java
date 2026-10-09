package com.pml.catalog.service;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.domain.enums.ApprovalAction;
import com.pml.catalog.domain.enums.EscalationStatus;
import com.pml.catalog.domain.model.ApprovalEscalation;
import com.pml.catalog.domain.model.ApprovalTimeline;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.valueobject.TimelineEvent;
import com.pml.catalog.exception.InvalidEventStateException;
import com.pml.catalog.config.CatalogIndexInitializer;
import com.pml.catalog.persistence.CatalogCollections;
import com.pml.catalog.repository.ApprovalTimelineRepository;
import com.pml.catalog.workflow.approval.ApprovalRules;
import com.pml.catalog.workflow.approval.EventApprovalWorkflow.Decision;
import com.pml.catalog.workflow.approval.EventApprovalWorkflow.Snapshot;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.persistence.IndexEnsurer;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.Persistence;
import com.pml.shared.testing.TestClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The writes a review's activities make, against a real replica set.
 *
 * <p>Each write runs twice, as a retried activity would, and the assertion is on the documents: one
 * transition, one timeline row, one escalation per level, and a decision that releases its claim and
 * resolves its escalations in the same commit. The workflow's timers over these steps are
 * {@code EventApprovalWorkflowTest}.
 */
@Tag("L2")
@Tag("ET-ADM-001")
@DisplayName("ET-ADM-001-R2/R3/R5 · review writes move once, append once, and release the claim with the decision")
class EventReviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T10:00:00Z");
    private static final String EVENT = "event-review-probe";
    private static final String ORGANIZER = "organizer-review";
    private static final String ALICE = "reviewer-alice";
    private static final String REASON = "The venue capacity does not match the tiers";
    private static final long DEADLINE = NOW.plus(ApprovalRules.SLA).toEpochMilli();

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static ApprovalTimelineRepository timelines;
    private static EventReviewService service;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "catalog_event_review"));
        timelines = new ReactiveMongoRepositoryFactory(template).getRepository(ApprovalTimelineRepository.class);
        TransactionalOperator transaction = TransactionalOperator.create(
                new ReactiveMongoTransactionManager(template.getMongoDatabaseFactory()));
        service = new EventReviewService(template, timelines, transaction, TestClock.frozenAt(NOW));
        // The one-escalation-per-level guarantee is the unique {eventId, level} index, not
        // application code, so the test needs it present just as boot does.
        new IndexEnsurer(template).ensure(CatalogIndexInitializer.specifications()).block();
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        template.remove(new Query(), Event.class).block();
        template.remove(new Query(), ApprovalTimeline.class).block();
        template.remove(new Query(), ApprovalEscalation.class).block();
        template.remove(new Query(), com.pml.catalog.domain.model.TicketTier.class).block();
        template.save(com.pml.catalog.domain.model.TicketTier.builder()
                .id("tier-review")
                .eventId(EVENT)
                .code("GA")
                .name("General Admission")
                .price(java.math.BigDecimal.TEN)
                .quantity(100)
                .isActive(true)
                .build()).block();
        template.save(Event.builder()
                .id(EVENT)
                .title("Review probe")
                .organizationId("org-review")
                .organizerId(ORGANIZER)
                .status(EventStatus.DRAFT)
                .isActive(true)
                .isDeleted(false)
                .locationId("location-review")
                .totalCapacity(100)
                .eventDateTime(NOW.plus(Duration.ofDays(30)))
                .build()).block();
    }

    @Test
    @DisplayName("a submission moves the event once and appends one SUBMITTED row with the workflow's deadline")
    void aSubmissionMovesOnce() {
        service.submit(EVENT, ORGANIZER, DEADLINE).block();
        Snapshot snapshot = service.submit(EVENT, ORGANIZER, DEADLINE).block();

        assertThat(snapshot.status()).isEqualTo(EventStatus.PENDING_APPROVAL);
        assertThat(snapshot.submittedAtMillis()).isEqualTo(NOW.toEpochMilli());
        Event event = event();
        assertThat(event.getSubmissionCount()).isEqualTo(1);
        assertThat(event.getApprovalDeadline()).isEqualTo(Instant.ofEpochMilli(DEADLINE));
        assertThat(actions()).containsExactly(ApprovalAction.SUBMITTED);
    }

    @Test
    @DisplayName("R3 · each level writes one escalation row however often it runs, and level 1 marks the item overdue")
    void oneEscalationPerLevel() {
        service.submit(EVENT, ORGANIZER, DEADLINE).block();

        service.escalate(EVENT, 1, null).block();
        service.escalate(EVENT, 1, null).block();
        service.escalate(EVENT, 2, ALICE).block();

        List<ApprovalEscalation> escalations = template.findAll(ApprovalEscalation.class).collectList().block();
        assertThat(escalations).hasSize(2);
        escalations.sort(Comparator.comparingInt(ApprovalEscalation::getLevel));
        assertThat(escalations.get(0).getLevel()).isEqualTo(1);
        assertThat(escalations.get(0).getEscalatedTo()).isEqualTo(ApprovalRules.QUEUE);
        assertThat(escalations.get(1).getLevel()).isEqualTo(2);
        assertThat(escalations.get(1).getEscalatedTo()).isEqualTo(ApprovalRules.SUPERVISOR);
        assertThat(event().isOverdue()).isTrue();
        assertThat(timeline().isOverdue()).isTrue();
        assertThat(actions()).filteredOn(action -> action == ApprovalAction.ESCALATED).hasSize(2);
        assertThat(service.current(EVENT).block().escalationLevel()).isEqualTo(2);
    }

    @Test
    @DisplayName("ET-PLT-002 §4 · ten concurrent escalations at one level still write exactly one row")
    void concurrentEscalationsAtOneLevelWriteOnce() {
        service.submit(EVENT, ORGANIZER, DEADLINE).block();

        Flux.range(0, 10)
                .flatMap(i -> service.escalate(EVENT, 1, null)
                        // the losing side of a real race gets a transient transaction failure and
                        // retries the whole activity in production; here it is enough
                        // that at most one side's write survives
                        .onErrorResume(error -> Mono.empty())
                        .subscribeOn(Schedulers.boundedElastic()), 10)
                .collectList()
                .block();

        List<ApprovalEscalation> level1 = template.find(
                        Query.query(Criteria.where("eventId").is(EVENT).and("level").is(1)),
                        ApprovalEscalation.class)
                .collectList().block();
        assertThat(level1).hasSize(1);
    }

    @Test
    @DisplayName("R5 · a decision releases the claim and resolves the escalations in the same commit, and a retry appends nothing")
    void aDecisionReleasesItsClaimTogether() {
        service.submit(EVENT, ORGANIZER, DEADLINE).block();
        service.claim(EVENT, ALICE, ALICE).block();
        service.escalate(EVENT, 1, ALICE).block();
        assertThat(event().getAssignedReviewerId()).isEqualTo(ALICE);

        service.approve(new Decision(EVENT, ALICE, null), Duration.ofHours(1).toMillis()).block();
        Snapshot retried = service.approve(new Decision(EVENT, ALICE, null), Duration.ofHours(1).toMillis()).block();

        assertThat(retried.status()).isEqualTo(EventStatus.APPROVED);
        Event event = event();
        assertThat(event.getStatus()).isEqualTo(EventStatus.APPROVED);
        assertThat(event.getApprovedBy()).isEqualTo(ALICE);
        assertThat(event.getAssignedReviewerId()).isNull();
        ApprovalTimeline timeline = timeline();
        assertThat(timeline.getAssignedReviewerId()).isNull();
        assertThat(timeline.isHasActiveEscalation()).isFalse();
        assertThat(actions()).filteredOn(action -> action == ApprovalAction.APPROVED).hasSize(1);
        TimelineEvent decided = timeline.getTimelineEvents().get(timeline.getTimelineEvents().size() - 1);
        assertThat(decided.getMetadata()).containsEntry("timeToDecisionMs", Duration.ofHours(1).toMillis());
        assertThat(template.findAll(ApprovalEscalation.class).collectList().block())
                .extracting(ApprovalEscalation::getStatus)
                .containsOnly(EscalationStatus.RESOLVED);
    }

    @Test
    @DisplayName("R4 · an event with no published tier cannot be approved, and nothing is written")
    void noPublishedTierBlocksApproval() {
        template.remove(new Query(), com.pml.catalog.domain.model.TicketTier.class).block();
        service.submit(EVENT, ORGANIZER, DEADLINE).block();

        assertThatThrownBy(() -> service.approve(new Decision(EVENT, ALICE, null), 0L).block())
                .satisfies(error -> assertThat(error.getMessage()).contains("published ticket tier"));

        assertThat(event().getStatus()).isEqualTo(EventStatus.PENDING_APPROVAL);
    }

    @Test
    @DisplayName("R4 · an event with no location cannot be approved, and nothing is written")
    void noLocationBlocksApproval() {
        service.submit(EVENT, ORGANIZER, DEADLINE).block();
        template.updateFirst(Query.query(Criteria.where("_id").is(EVENT)),
                new org.springframework.data.mongodb.core.query.Update().unset("locationId"), Event.class).block();

        assertThatThrownBy(() -> service.approve(new Decision(EVENT, ALICE, null), 0L).block())
                .satisfies(error -> assertThat(error.getMessage()).contains("a location"));

        assertThat(event().getStatus()).isEqualTo(EventStatus.PENDING_APPROVAL);
    }

    @Test
    @DisplayName("R4 · an event with no capacity cannot be approved, but reject remains available")
    void noCapacityBlocksApprovalButNotRejection() {
        service.submit(EVENT, ORGANIZER, DEADLINE).block();
        template.updateFirst(Query.query(Criteria.where("_id").is(EVENT)),
                new org.springframework.data.mongodb.core.query.Update().set("totalCapacity", 0), Event.class).block();

        assertThatThrownBy(() -> service.approve(new Decision(EVENT, ALICE, null), 0L).block())
                .satisfies(error -> assertThat(error.getMessage()).contains("a capacity"));

        service.reject(new Decision(EVENT, ALICE, REASON), 0L).block();
        assertThat(event().getStatus()).isEqualTo(EventStatus.REJECTED);
    }

    @Test
    @DisplayName("R1 · a decision on an event not awaiting review is refused and writes nothing")
    void aDecisionFromTheWrongStateWritesNothing() {
        assertThatThrownBy(() -> service.reject(new Decision(EVENT, ALICE, REASON), 0L).block())
                .isInstanceOf(InvalidEventStateException.class);

        assertThat(event().getStatus()).isEqualTo(EventStatus.DRAFT);
        Persistence.assertNothingPersisted(template, CatalogCollections.APPROVAL_TIMELINES);
    }

    @Test
    @DisplayName("R3 · changes requested and a resubmission each append once, with the unspent deadline")
    void changesAndResubmission() {
        service.submit(EVENT, ORGANIZER, DEADLINE).block();
        service.requestChanges(new Decision(EVENT, ALICE, REASON), Duration.ofHours(20).toMillis()).block();
        long remaining = NOW.plus(Duration.ofHours(4)).toEpochMilli();

        service.resubmit(EVENT, ORGANIZER, remaining).block();
        service.resubmit(EVENT, ORGANIZER, remaining).block();

        Event event = event();
        assertThat(event.getStatus()).isEqualTo(EventStatus.PENDING_APPROVAL);
        assertThat(event.getChangesRequestedComments()).isEqualTo(REASON);
        assertThat(event.getApprovalDeadline()).isEqualTo(Instant.ofEpochMilli(remaining));
        assertThat(actions()).containsExactly(ApprovalAction.SUBMITTED, ApprovalAction.CHANGES_REQUESTED,
                ApprovalAction.RESUBMITTED);
    }

    @Test
    @DisplayName("R2 · a re-claim appends no second row, and an expiry clears the claim once")
    void claimsAndExpiry() {
        service.submit(EVENT, ORGANIZER, DEADLINE).block();

        service.claim(EVENT, ALICE, ALICE).block();
        service.claim(EVENT, ALICE, ALICE).block();
        service.releaseClaim(EVENT, ALICE, true).block();
        service.releaseClaim(EVENT, ALICE, true).block();

        assertThat(actions()).containsExactly(ApprovalAction.SUBMITTED, ApprovalAction.ASSIGNED, ApprovalAction.CLAIM_EXPIRED);
        assertThat(event().getAssignedReviewerId()).isNull();
        assertThat(timeline().getAssignedReviewerId()).isNull();
        assertThat(event().getSubmittedForApprovalAt()).as("an expired claim keeps the queue position").isEqualTo(NOW);
    }

    @Test
    @DisplayName("R2 · a decided event cannot be claimed")
    void aDecidedEventIsNotClaimable() {
        service.submit(EVENT, ORGANIZER, DEADLINE).block();
        service.approve(new Decision(EVENT, ALICE, null), 0L).block();

        assertThatThrownBy(() -> service.claim(EVENT, ALICE, ALICE).block())
                .isInstanceOf(InvalidEventStateException.class);
    }

    private static Event event() {
        Event event = template.findById(EVENT, Event.class).block();
        assertThat(event).isNotNull();
        return event;
    }

    private static ApprovalTimeline timeline() {
        ApprovalTimeline timeline = timelines.findByEventId(EVENT).block();
        assertThat(timeline).isNotNull();
        return timeline;
    }

    private static List<ApprovalAction> actions() {
        return timeline().getTimelineEvents().stream().map(TimelineEvent::getAction).toList();
    }
}
