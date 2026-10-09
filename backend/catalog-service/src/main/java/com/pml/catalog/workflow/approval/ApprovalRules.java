package com.pml.catalog.workflow.approval;

import com.pml.catalog.infrastructure.temporal.TaskQueues;
import com.pml.shared.workflow.Refusal;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.error.ErrorCode;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The review decisions that need neither a server nor a database.
 *
 * <p>The escalation schedule, the claim contention rule and the decision preconditions live here so
 * each is tested at layer 1, and so the workflow's validators and its timers read one definition.
 */
public final class ApprovalRules {

    /** The review SLA, {@code admin.sla.event}. */
    public static final Duration SLA = Duration.ofHours(24);

    /** How long a reviewer's claim holds, {@code admin.review.claim-ttl}. */
    public static final Duration CLAIM_TTL = Duration.ofMinutes(30);

    /** A started execution that receives no submission in this window closes. */
    public static final Duration SUBMIT_WINDOW = Duration.ofMinutes(5);

    /** A rejection or a change request explains itself in at least this many characters. */
    public static final int MIN_REASON_LENGTH = 20;

    /** The highest escalation level. */
    public static final int TOP_LEVEL = 3;

    /** Level n fires at this multiple of the SLA. */
    private static final int[] MULTIPLIERS = {1, 2, 4};

    public static final String QUEUE = "APPROVAL_QUEUE";
    public static final String SUPERVISOR = "APPROVAL_SUPERVISOR";
    public static final String LEADERSHIP = "PLATFORM_LEADERSHIP";

    private ApprovalRules() {
    }

    /** Whether a review is still undecided: awaiting a reviewer, or awaiting the applicant. */
    public static boolean isOpen(EventStatus status) {
        return status == EventStatus.PENDING_APPROVAL || status == EventStatus.CHANGES_REQUESTED;
    }

    /** The elapsed SLA time at which {@code level} fires: 1×, 2× and 4× the SLA. */
    public static Duration threshold(int level) {
        if (level < 1 || level > TOP_LEVEL) {
            throw new IllegalArgumentException("escalation levels are 1 to " + TOP_LEVEL + ", not " + level);
        }
        return SLA.multipliedBy(MULTIPLIERS[level - 1]);
    }

    /**
     * The next level to fire, or {@code fired} when none is due. Levels fire one at a time and
     * in order, so an item that crossed two thresholds while unobserved records both.
     */
    public static int levelDue(long elapsedMillis, int fired) {
        return fired < TOP_LEVEL && elapsedMillis >= threshold(fired + 1).toMillis() ? fired + 1 : fired;
    }

    /**
     * The next moment the workflow must act without being asked: the next escalation threshold while
     * the clock counts, or the claim's expiry, whichever is sooner. {@code null} when neither applies.
     */
    public static Duration nextWake(long nowMillis, SlaClock clock, int fired, Long claimExpiresAtMillis) {
        Long wake = null;
        if (clock != null && clock.running() && fired < TOP_LEVEL) {
            wake = Math.max(0L, threshold(fired + 1).toMillis() - clock.elapsed(nowMillis));
        }
        if (claimExpiresAtMillis != null) {
            long untilExpiry = Math.max(0L, claimExpiresAtMillis - nowMillis);
            wake = wake == null ? untilExpiry : Math.min(wake, untilExpiry);
        }
        return wake == null ? null : Duration.ofMillis(wake);
    }

    /** A claim holds only until its expiry. */
    public static boolean claimLive(String holderId, long expiresAtMillis, long nowMillis) {
        return holderId != null && nowMillis < expiresAtMillis;
    }

    /** A claim is refused while somebody else holds the item; the holder re-claiming extends it. */
    public static Optional<Refusal> claimRefusal(EventStatus status, String holderId, long expiresAtMillis,
                                                 String reviewerId, long nowMillis) {
        if (reviewerId == null || reviewerId.isBlank()) {
            return refuse(ErrorCode.COMMAND_NOT_WELL_FORMED, "a claim names its reviewer");
        }
        if (status != EventStatus.PENDING_APPROVAL) {
            return refuse(ErrorCode.EVENT_STATE_INVALID, "only an event awaiting review is claimed; this one is " + status);
        }
        if (claimLive(holderId, expiresAtMillis, nowMillis) && !holderId.equals(reviewerId)) {
            return refuse(ErrorCode.RESOURCE_CONFLICT,
                    "held by " + holderId + " until " + Instant.ofEpochMilli(expiresAtMillis));
        }
        return Optional.empty();
    }

    public static Optional<Refusal> releaseRefusal(String holderId, long expiresAtMillis, long nowMillis) {
        if (!claimLive(holderId, expiresAtMillis, nowMillis)) {
            return refuse(ErrorCode.EVENT_STATE_INVALID, "no claim is held on this event");
        }
        return Optional.empty();
    }

    /**
     * Whether a decision may be taken: the item awaits review, a rejection or change request
     * carries its reason, and nobody else holds the claim. An unclaimed item is always decidable, so
     * a reviewer is never trapped.
     */
    public static Optional<Refusal> decisionRefusal(EventStatus status, String holderId, long expiresAtMillis,
                                                    String reviewerId, String reason, boolean reasonRequired,
                                                    long nowMillis) {
        if (reviewerId == null || reviewerId.isBlank()) {
            return refuse(ErrorCode.COMMAND_NOT_WELL_FORMED, "a decision names its reviewer");
        }
        if (status != EventStatus.PENDING_APPROVAL) {
            return refuse(ErrorCode.EVENT_STATE_INVALID, "only an event awaiting review is decided; this one is " + status);
        }
        if (reasonRequired && !reasonSufficient(reason)) {
            return refuse(ErrorCode.COMMAND_NOT_WELL_FORMED,
                    "a rejection or a change request carries a reason of at least " + MIN_REASON_LENGTH + " characters");
        }
        if (claimLive(holderId, expiresAtMillis, nowMillis) && !holderId.equals(reviewerId)) {
            return refuse(ErrorCode.ACTOR_NOT_PERMITTED, "this event is claimed by " + holderId);
        }
        return Optional.empty();
    }

    /** What stands between an event awaiting review and its approval. */
    public enum ApprovalBlocker {
        NO_PUBLISHED_TIER("a published ticket tier"),
        NO_LOCATION("a location"),
        NO_CAPACITY("a capacity");

        private final String missing;

        ApprovalBlocker(String missing) {
            this.missing = missing;
        }

        public String missing() {
            return missing;
        }
    }

    /**
     * Everything the event lacks before it may be approved, in a fixed order. The approval decision
     * and the approvals queue both read this list, so the queue shows exactly what would refuse the
     * approval. Rejecting or requesting changes is never blocked.
     */
    public static List<ApprovalBlocker> approvalBlockers(boolean hasPublishedTier, String locationId, int totalCapacity) {
        List<ApprovalBlocker> blockers = new ArrayList<>();
        if (!hasPublishedTier) {
            blockers.add(ApprovalBlocker.NO_PUBLISHED_TIER);
        }
        if (locationId == null || locationId.isBlank()) {
            blockers.add(ApprovalBlocker.NO_LOCATION);
        }
        if (totalCapacity <= 0) {
            blockers.add(ApprovalBlocker.NO_CAPACITY);
        }
        return List.copyOf(blockers);
    }

    public static Optional<Refusal> approvalPreconditionRefusal(boolean hasPublishedTier, String locationId, int totalCapacity) {
        List<ApprovalBlocker> blockers = approvalBlockers(hasPublishedTier, locationId, totalCapacity);
        if (blockers.isEmpty()) {
            return Optional.empty();
        }
        return refuse(ErrorCode.EVENT_STATE_INVALID, "the event cannot be approved: it is missing "
                + String.join(", ", blockers.stream().map(ApprovalBlocker::missing).toList()));
    }

    /**
     * The message sent when a review reaches {@code status}: administrators hear of an event joining
     * the queue, the organizer hears of a decision. Null for a status nobody is told about.
     */
    public static String announcementTemplate(EventStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case PENDING_APPROVAL -> "admin.event-pending";
            case APPROVED -> "event.approved";
            case REJECTED -> "event.rejected";
            case CHANGES_REQUESTED -> "event.changes-requested";
            default -> null;
        };
    }

    /**
     * One message per submission round and outcome. The submission number rises on every submit and
     * resubmit, so a resubmitted event is announced again while a retried announcement is not.
     */
    public static String announcementKey(String eventId, int submissionNumber, EventStatus status) {
        return eventId + ":" + submissionNumber + ":" + status.name();
    }

    public static Optional<Refusal> resubmitRefusal(EventStatus status) {
        if (status != EventStatus.CHANGES_REQUESTED) {
            return refuse(ErrorCode.EVENT_STATE_INVALID, "only an event with changes requested is resubmitted; this one is " + status);
        }
        return Optional.empty();
    }

    public static Optional<Refusal> submitRefusal(EventStatus status) {
        if (status != null && !isOpen(status)) {
            return refuse(ErrorCode.EVENT_STATE_INVALID, "this submission was already decided: " + status);
        }
        return Optional.empty();
    }

    public static boolean reasonSufficient(String reason) {
        return reason != null && reason.strip().length() >= MIN_REASON_LENGTH;
    }

    /** Who a level notifies: the holder or the queue, then a supervisor, then leadership. */
    public static String recipient(int level, String holderId) {
        return switch (level) {
            case 1 -> holderId != null ? holderId : QUEUE;
            case 2 -> SUPERVISOR;
            case 3 -> LEADERSHIP;
            default -> throw new IllegalArgumentException("escalation levels are 1 to " + TOP_LEVEL + ", not " + level);
        };
    }

    /**
     * The clock of a review adopted from a stored event. Time before adoption is reconstructed from
     * the event's own stamps: a pending item has been waiting since submission, and one awaiting
     * changes stopped counting when they were requested.
     */
    public static SlaClock adoptedClock(EventStatus status, long submittedAtMillis, long changesRequestedAtMillis,
                                        long nowMillis) {
        if (status == EventStatus.CHANGES_REQUESTED) {
            long counted = changesRequestedAtMillis >= submittedAtMillis ? changesRequestedAtMillis - submittedAtMillis : 0L;
            return SlaClock.of(counted, false, nowMillis);
        }
        return SlaClock.of(submittedAtMillis > 0 ? nowMillis - submittedAtMillis : 0L, true, nowMillis);
    }

    /** MongoDB writes: retried until they land; a refusal is non-retryable and ends the attempt at once. */
    static ActivityOptions activityOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.LIFECYCLE)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setMaximumInterval(Duration.ofMinutes(1))
                        .build())
                .build();
    }

    /**
     * A few attempts to hand an announcement to identity, after which the review carries on: a
     * message never holds up or undoes the decision that caused it.
     */
    static ActivityOptions announcementOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.LIFECYCLE)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(5)
                        .setInitialInterval(Duration.ofSeconds(2))
                        .setMaximumInterval(Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private static Optional<Refusal> refuse(ErrorCode code, String message) {
        return Optional.of(new Refusal(code, message));
    }
}
