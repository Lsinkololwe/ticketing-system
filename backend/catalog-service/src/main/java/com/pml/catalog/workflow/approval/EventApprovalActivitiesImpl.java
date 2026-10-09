package com.pml.catalog.workflow.approval;

import com.pml.catalog.service.ApprovalAnnouncer;
import com.pml.shared.constants.EventStatus;
import com.pml.catalog.infrastructure.temporal.TaskQueues;
import com.pml.catalog.service.EventReviewService;
import com.pml.catalog.error.CatalogRefusalTranslator;
import com.pml.shared.workflow.Refusals;
import com.pml.catalog.workflow.approval.EventApprovalWorkflow.Decision;
import com.pml.catalog.workflow.approval.EventApprovalWorkflow.Snapshot;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * The review activities, adapting {@link EventReviewService} to Temporal.
 *
 * <p>The reactive chain is awaited on the worker's activity executor, for less
 * than the activity's start-to-close timeout.
 */
@Component
@ActivityImpl(taskQueues = TaskQueues.LIFECYCLE)
public class EventApprovalActivitiesImpl implements EventApprovalActivities {

    private static final Duration AWAIT = Duration.ofSeconds(25);

    private final EventReviewService reviews;
    private final ApprovalAnnouncer announcer;

    public EventApprovalActivitiesImpl(EventReviewService reviews, ApprovalAnnouncer announcer) {
        this.reviews = reviews;
        this.announcer = announcer;
    }

    @Override
    public Snapshot current(String eventId) {
        return await(reviews.current(eventId));
    }

    @Override
    public Snapshot submit(String eventId, String actorId, long slaDeadlineMillis) {
        return await(reviews.submit(eventId, actorId, slaDeadlineMillis));
    }

    @Override
    public Snapshot resubmit(String eventId, String actorId, long slaDeadlineMillis) {
        return await(reviews.resubmit(eventId, actorId, slaDeadlineMillis));
    }

    @Override
    public void claim(String eventId, String reviewerId, String actorId, long expiresAtMillis) {
        await(reviews.claim(eventId, reviewerId, actorId).thenReturn(Boolean.TRUE));
    }

    @Override
    public void releaseClaim(String eventId, String reviewerId, boolean expired) {
        await(reviews.releaseClaim(eventId, reviewerId, expired).thenReturn(Boolean.TRUE));
    }

    @Override
    public void escalate(String eventId, int level, String holderId) {
        await(reviews.escalate(eventId, level, holderId).thenReturn(Boolean.TRUE));
    }

    @Override
    public Snapshot approve(Decision decision, long reviewMillis) {
        return await(reviews.approve(decision, reviewMillis));
    }

    @Override
    public Snapshot reject(Decision decision, long reviewMillis) {
        return await(reviews.reject(decision, reviewMillis));
    }

    @Override
    public Snapshot requestChanges(Decision decision, long reviewMillis) {
        return await(reviews.requestChanges(decision, reviewMillis));
    }

    @Override
    public void announce(String eventId, EventStatus reached) {
        await(announcer.announce(eventId, reached).thenReturn(Boolean.TRUE));
    }

    private static <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            throw Refusals.forActivity(error, new CatalogRefusalTranslator());
        }
    }
}
