package com.pml.catalog.workflow.approval;

import com.pml.catalog.infrastructure.temporal.TaskQueues;
import com.pml.shared.workflow.Refusal;
import com.pml.shared.workflow.Refusals;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.error.ErrorCode;
import io.temporal.failure.ActivityFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The claim lease, the SLA clock and its escalations, held in one execution.
 *
 * <pre>
 *   submit ─▶ PENDING_APPROVAL ── SLA clock counting ── 1× ▶ level 1 · 2× ▶ level 2 · 4× ▶ level 3
 *                │  ├─ claim ─▶ lease PT30M ─ expiry ─▶ released in place
 *                │  ├─ requestChanges ─▶ CHANGES_REQUESTED (clock paused) ─ resubmit ─▶ PENDING_APPROVAL
 *                │  ├─ approve ─▶ APPROVED  (claim released, escalations resolved), execution closes
 *                │  └─ reject ──▶ REJECTED  (claim released, escalations resolved), execution closes
 * </pre>
 *
 * <p>Each level fires once: the level reached is workflow state, and the escalation activity is
 * idempotent per level besides.
 */
@WorkflowImpl(taskQueues = TaskQueues.LIFECYCLE)
public class EventApprovalWorkflowImpl implements EventApprovalWorkflow {

    private final EventApprovalActivities reviews =
            Workflow.newActivityStub(EventApprovalActivities.class, ApprovalRules.activityOptions());

    /** Announcements retry a few times and then give up, so a message never blocks the review. */
    private final EventApprovalActivities announcements =
            Workflow.newActivityStub(EventApprovalActivities.class, ApprovalRules.announcementOptions());

    /** Announcements still being handed over; the execution waits for them before it closes. */
    private final List<Promise<Void>> announcing = new ArrayList<>();

    private String eventId;
    private EventStatus status;
    private SlaClock sla;
    private int escalationLevel;
    private String holderId;
    private long holderExpiresAtMillis;
    private boolean submitRefused;
    private boolean deciding;
    private boolean changed;
    private int inFlight;

    @Override
    public void run(Start start) {
        eventId = start.eventId();
        if (start.adopt()) {
            Snapshot snapshot = reviews.current(eventId);
            if (status == null && snapshot != null) {
                adopt(snapshot);
            }
        } else {
            Workflow.await(ApprovalRules.SUBMIT_WINDOW, () -> status != null || submitRefused);
        }

        while (ApprovalRules.isOpen(status)) {
            Workflow.await(() -> inFlight == 0);
            if (!ApprovalRules.isOpen(status)) {
                break;
            }
            long now = Workflow.currentTimeMillis();

            if (holderId != null && now >= holderExpiresAtMillis) {
                String expired = holderId;
                reviews.releaseClaim(eventId, expired, true);
                if (expired.equals(holderId) && holderExpiresAtMillis <= Workflow.currentTimeMillis()) {
                    holderId = null;
                }
                continue;
            }

            int due = ApprovalRules.levelDue(sla.elapsed(now), escalationLevel);
            if (status == EventStatus.PENDING_APPROVAL && due > escalationLevel) {
                reviews.escalate(eventId, due, holderId);
                escalationLevel = due;
                continue;
            }

            Duration wait = ApprovalRules.nextWake(now, sla, escalationLevel,
                    holderId != null ? holderExpiresAtMillis : null);
            changed = false;
            if (wait == null) {
                Workflow.await(() -> changed || !ApprovalRules.isOpen(status));
            } else if (!wait.isZero()) {
                Workflow.await(wait, () -> changed || !ApprovalRules.isOpen(status));
            }
        }
        Workflow.await(Workflow::isEveryHandlerFinished);
        Promise.allOf(announcing).get();
    }

    private void adopt(Snapshot snapshot) {
        status = snapshot.status();
        escalationLevel = snapshot.escalationLevel();
        sla = ApprovalRules.adoptedClock(snapshot.status(), snapshot.submittedAtMillis(),
                snapshot.changesRequestedAtMillis(), Workflow.currentTimeMillis());
    }

    // ---- submission ----------------------------------------------------------------------------

    @Override
    public void validateSubmit(Submission submission) {
        raise(ApprovalRules.submitRefusal(status));
    }

    @Override
    public View submit(Submission submission) {
        if (status == EventStatus.PENDING_APPROVAL) {
            return view();
        }
        if (status == EventStatus.CHANGES_REQUESTED) {
            return resubmit(submission);
        }
        long now = Workflow.currentTimeMillis();
        inFlight++;
        try {
            Snapshot snapshot = reviews.submit(submission.eventId(), submission.actorId(), now + ApprovalRules.SLA.toMillis());
            if (status == null) {
                status = snapshot.status();
                sla = SlaClock.startedAt(now);
            }
            if (snapshot.status() == EventStatus.PENDING_APPROVAL) {
                announce(ANNOUNCE_SUBMISSIONS, EventStatus.PENDING_APPROVAL);
            }
            changed = true;
            return view();
        } catch (ActivityFailure failure) {
            if (status == null) {
                submitRefused = true;
            }
            throw Refusals.rethrow(failure);
        } finally {
            inFlight--;
        }
    }

    @Override
    public void validateResubmit(Submission submission) {
        raise(ApprovalRules.resubmitRefusal(status));
    }

    @Override
    public View resubmit(Submission submission) {
        long now = Workflow.currentTimeMillis();
        long deadline = now + sla.remaining(ApprovalRules.SLA, now);
        inFlight++;
        try {
            reviews.resubmit(submission.eventId(), submission.actorId(), deadline);
            status = EventStatus.PENDING_APPROVAL;
            sla.resume(now);
            announce(ANNOUNCE_SUBMISSIONS, EventStatus.PENDING_APPROVAL);
            changed = true;
            return view();
        } catch (ActivityFailure failure) {
            throw Refusals.rethrow(failure);
        } finally {
            inFlight--;
        }
    }

    // ---- claims --------------------------------------------------------------------------------

    @Override
    public void validateClaim(Claim claim) {
        raise(ApprovalRules.claimRefusal(status, holderId, holderExpiresAtMillis, claim.reviewerId(),
                Workflow.currentTimeMillis()));
    }

    @Override
    public View claim(Claim claim) {
        long expires = Workflow.currentTimeMillis() + ApprovalRules.CLAIM_TTL.toMillis();
        String previousHolder = holderId;
        long previousExpiry = holderExpiresAtMillis;
        // Held before the write: a second claim validated while this one is still writing sees it.
        holderId = claim.reviewerId();
        holderExpiresAtMillis = expires;
        inFlight++;
        try {
            reviews.claim(claim.eventId(), claim.reviewerId(), claim.actorId(), expires);
            changed = true;
            return view();
        } catch (ActivityFailure failure) {
            holderId = previousHolder;
            holderExpiresAtMillis = previousExpiry;
            throw Refusals.rethrow(failure);
        } finally {
            inFlight--;
        }
    }

    @Override
    public void validateRelease(Claim claim) {
        raise(ApprovalRules.releaseRefusal(holderId, holderExpiresAtMillis, Workflow.currentTimeMillis()));
    }

    @Override
    public View release(Claim claim) {
        String held = holderId;
        inFlight++;
        try {
            reviews.releaseClaim(claim.eventId(), held, false);
            if (held != null && held.equals(holderId)) {
                holderId = null;
            }
            changed = true;
            return view();
        } catch (ActivityFailure failure) {
            throw Refusals.rethrow(failure);
        } finally {
            inFlight--;
        }
    }

    // ---- decisions -----------------------------------------------------------------------------

    @Override
    public void validateApprove(Decision decision) {
        refuseWhileDeciding();
        raise(ApprovalRules.decisionRefusal(status, holderId, holderExpiresAtMillis, decision.reviewerId(),
                decision.reason(), false, Workflow.currentTimeMillis()));
    }

    @Override
    public View approve(Decision decision) {
        long now = Workflow.currentTimeMillis();
        return decide(() -> reviews.approve(decision, sla.elapsed(now)), now);
    }

    @Override
    public void validateReject(Decision decision) {
        refuseWhileDeciding();
        raise(ApprovalRules.decisionRefusal(status, holderId, holderExpiresAtMillis, decision.reviewerId(),
                decision.reason(), true, Workflow.currentTimeMillis()));
    }

    @Override
    public View reject(Decision decision) {
        long now = Workflow.currentTimeMillis();
        return decide(() -> reviews.reject(decision, sla.elapsed(now)), now);
    }

    @Override
    public void validateRequestChanges(Decision decision) {
        refuseWhileDeciding();
        raise(ApprovalRules.decisionRefusal(status, holderId, holderExpiresAtMillis, decision.reviewerId(),
                decision.reason(), true, Workflow.currentTimeMillis()));
    }

    @Override
    public View requestChanges(Decision decision) {
        long now = Workflow.currentTimeMillis();
        return decide(() -> reviews.requestChanges(decision, sla.elapsed(now)), now);
    }

    @Override
    public View current() {
        return view();
    }

    // ---- helpers -------------------------------------------------------------------------------

    /**
     * Version markers for the two announcement points. An execution that passed a point before
     * announcements existed replays without them; each point gets its own marker so an execution
     * already past its submission still announces the decision it reaches afterwards.
     */
    private static final String ANNOUNCE_SUBMISSIONS = "approval-announce-submissions";
    private static final String ANNOUNCE_DECISIONS = "approval-announce-decisions";

    /** Starts the announcement without waiting for it; a failed one is dropped once its retries are spent. */
    private void announce(String changeId, EventStatus reached) {
        if (Workflow.getVersion(changeId, Workflow.DEFAULT_VERSION, 1) == Workflow.DEFAULT_VERSION) {
            return;
        }
        announcing.add(Async.procedure(announcements::announce, eventId, reached)
                .exceptionally(failure -> null));
    }

    /** A decision holds the item until its write lands, so a second decision validated meanwhile is refused. */
    private void refuseWhileDeciding() {
        if (deciding) {
            throw new Refusal(ErrorCode.EVENT_STATE_INVALID, "a decision on this event is already being recorded").failure();
        }
    }

    private View decide(Supplier<Snapshot> activity, long decidedAtMillis) {
        deciding = true;
        inFlight++;
        try {
            Snapshot snapshot = activity.get();
            status = snapshot.status();
            holderId = null;
            if (status == EventStatus.CHANGES_REQUESTED) {
                sla.pause(decidedAtMillis);
            }
            announce(ANNOUNCE_DECISIONS, status);
            changed = true;
            return view();
        } catch (ActivityFailure failure) {
            throw Refusals.rethrow(failure);
        } finally {
            deciding = false;
            inFlight--;
        }
    }

    private View view() {
        return new View(eventId, status, holderId, holderId != null ? holderExpiresAtMillis : 0L, escalationLevel);
    }

    private static void raise(Optional<Refusal> refusal) {
        if (refusal.isPresent()) {
            throw refusal.get().failure();
        }
    }
}
