package com.pml.identity.workflow.contactchange;

import com.pml.identity.account.ContactChangeKind;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.workflow.contactchange.ContactChangeActivities.Claim;
import com.pml.identity.workflow.contactchange.ContactChangeActivities.Commit;
import com.pml.identity.workflow.contactchange.ContactChangeActivities.Notice;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.shared.workflow.Refusals;
import io.temporal.failure.ActivityFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.CancellationScope;
import io.temporal.workflow.Workflow;

/** The steps of {@link ContactChangeWorkflow}, in order, each an activity. */
@WorkflowImpl(taskQueues = TaskQueues.ACCOUNT)
public class ContactChangeWorkflowImpl implements ContactChangeWorkflow {

    private final ContactChangeActivities steps =
            Workflow.newActivityStub(ContactChangeActivities.class, ContactChangeRules.options());
    private final ContactChangeActivities notices =
            Workflow.newActivityStub(ContactChangeActivities.class, ContactChangeRules.noticeOptions());
    private final ContactChangeActivities cleanup =
            Workflow.newActivityStub(ContactChangeActivities.class, ContactChangeRules.cleanupOptions());

    private ChangeCommand command;
    private String phase = "STARTING";
    private boolean authorised;
    private String newProofId;
    private int failures;
    private String newChallengeId;
    private String currentChallengeId;
    private boolean cancelled;
    private long expiresAt;

    @Override
    public Outcome run(ChangeCommand c) {
        command = c;
        newChallengeId = c.newChallengeId();
        currentChallengeId = c.currentChallengeId();
        expiresAt = Workflow.currentTimeMillis() + ContactChangeRules.EXPIRY.toMillis();
        Workflow.upsertTypedSearchAttributes(ProcessSearchAttributes.BUSINESS_ID.valueSet(c.accountId()));
        // Declared from the first release: a later step is added behind a higher version.
        Workflow.getVersion(ContactChangeRules.STEPS, Workflow.DEFAULT_VERSION, 1);

        boolean begun = false;
        boolean finished = false;
        try {
            steps.begin(c.accountId());
            begun = true;
            phase = "AWAITING_CODES";

            String proofId = c.proofId();
            if (c.kind() == ContactChangeKind.CHANGE) {
                boolean ready = Workflow.await(ContactChangeRules.EXPIRY,
                        () -> cancelled || exhausted() || (authorised && newProofId != null));
                if (!ready) {
                    throw Refusals.refusal(ErrorCode.OTP_EXPIRED, "the change waited too long for its codes");
                }
                if (cancelled) {
                    finished = true;
                    clearMarker();
                    phase = "DONE";
                    return new Outcome(c.changeId(), "CANCELLED");
                }
                if (exhausted()) {
                    throw Refusals.refusal(ErrorCode.OTP_ATTEMPTS_EXHAUSTED, "too many wrong codes");
                }
                proofId = newProofId;
            }

            phase = "APPLYING";
            String newContactId = null;
            if (c.kind() == ContactChangeKind.ADD || c.kind() == ContactChangeKind.CHANGE) {
                newContactId = steps.claimContact(new Claim(c.accountId(), c.changeId(), proofId, c.newContactKey(), c.type()));
            }
            boolean releases = c.kind() == ContactChangeKind.CHANGE || c.kind() == ContactChangeKind.REMOVE;
            if (c.kind() != ContactChangeKind.PRIMARY) {
                steps.syncKeycloak(c.accountId(), releases ? c.contactId() : null);
            }
            steps.commit(new Commit(c.accountId(), c.changeId(), c.kind(), c.contactId(), newContactId));
            if (releases) {
                steps.endSessions(c.accountId());
            }
            steps.clearMarker(c.accountId());
            finished = true;
            try {
                notices.notifyContacts(new Notice(c.accountId(), c.kind(), c.contactId(), newContactId));
            } catch (ActivityFailure notDelivered) {
                // The change is made; the notice is best effort.
            }
            phase = "DONE";
            return new Outcome(c.changeId(), "COMPLETED");
        } catch (ActivityFailure failure) {
            throw Refusals.rethrow(failure);
        } finally {
            if (begun && !finished) {
                clearMarkerDetached();
            }
            phase = "DONE";
        }
    }

    private void clearMarker() {
        steps.clearMarker(command.accountId());
    }

    private void clearMarkerDetached() {
        Workflow.newDetachedCancellationScope(() -> {
            try {
                cleanup.clearMarker(command.accountId());
            } catch (ActivityFailure cancelled) {
                // The cleanup retries until it lands, so reaching here means the workflow itself was
                // terminated mid-cleanup; the marker stays until the account's change is cleared by hand.
            }
        }).run();
    }

    private boolean exhausted() {
        return failures >= ContactChangeRules.MAX_FAILED_ATTEMPTS;
    }

    @Override
    public void validateAuthorise() {
        requireOpenChange();
    }

    @Override
    public void authorise() {
        authorised = true;
    }

    @Override
    public void validateAcceptNew(String proofId) {
        requireOpenChange();
        if (proofId == null || proofId.isBlank()) {
            throw Refusals.refusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "a proof is required");
        }
    }

    @Override
    public void acceptNew(String proofId) {
        newProofId = proofId;
    }

    private void requireOpenChange() {
        if (command == null || command.kind() != ContactChangeKind.CHANGE
                || !("STARTING".equals(phase) || "AWAITING_CODES".equals(phase)) || exhausted()) {
            throw Refusals.refusal(ErrorCode.CONTACT_UNKNOWN, "no change is waiting for a code");
        }
    }

    @Override
    public void failedAttempt() {
        failures++;
    }

    @Override
    public void codeResent(String target, String challengeId) {
        if ("NEW".equals(target)) {
            newChallengeId = challengeId;
        } else if ("CURRENT".equals(target)) {
            currentChallengeId = challengeId;
        }
    }

    @Override
    public void cancel() {
        cancelled = true;
    }

    @Override
    public Status status() {
        if (command == null) {
            return null;
        }
        return new Status(command.changeId(), command.kind(), phase, command.newMasked(), expiresAt, authorised,
                newProofId != null, ContactChangeRules.attemptsRemaining(failures), newChallengeId,
                currentChallengeId, command.newContactKey());
    }
}
