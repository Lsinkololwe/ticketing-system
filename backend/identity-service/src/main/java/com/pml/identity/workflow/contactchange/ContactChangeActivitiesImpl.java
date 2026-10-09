package com.pml.identity.workflow.contactchange;

import com.pml.identity.account.ContactChangeSteps;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.shared.workflow.Refusals;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/** Adapts {@link ContactChangeSteps} to Temporal; a refusal leaves non-retryable and typed by its code. */
@Component
@ActivityImpl(taskQueues = TaskQueues.ACCOUNT)
public class ContactChangeActivitiesImpl implements ContactChangeActivities {

    private static final Duration AWAIT = Duration.ofSeconds(25);

    private final ContactChangeSteps steps;

    public ContactChangeActivitiesImpl(ContactChangeSteps steps) {
        this.steps = steps;
    }

    @Override
    public void begin(String accountId) {
        await(steps.begin(accountId).thenReturn(Boolean.TRUE));
    }

    @Override
    public String claimContact(Claim claim) {
        return await(steps.claim(claim.accountId(), claim.changeId(), claim.proofId(), claim.contactKey(), claim.type()))
                .contactId();
    }

    @Override
    public void syncKeycloak(String accountId, String excludeContactId) {
        await(steps.syncKeycloak(accountId, excludeContactId).thenReturn(Boolean.TRUE));
    }

    @Override
    public void commit(Commit commit) {
        await(steps.commit(commit.accountId(), commit.changeId(), commit.kind(), commit.contactId(), commit.newContactId())
                .thenReturn(Boolean.TRUE));
    }

    @Override
    public void endSessions(String accountId) {
        await(steps.endSessions(accountId).thenReturn(Boolean.TRUE));
    }

    @Override
    public void clearMarker(String accountId) {
        await(steps.clearMarker(accountId).thenReturn(Boolean.TRUE));
    }

    @Override
    public void notifyContacts(Notice notice) {
        await(steps.notifyContacts(notice.accountId(), notice.kind(), notice.contactId(), notice.newContactId())
                .thenReturn(Boolean.TRUE));
    }

    private static <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            // A refusal ends the change whatever its code says about a caller retrying: CONTACT_ALREADY_CLAIMED is
            // "retry" to a person, but an activity that retried it would hold the change open for ever.
            if (reactor.core.Exceptions.unwrap(error) instanceof com.pml.shared.error.DomainRefusal refusal) {
                throw Refusals.refusal(refusal.errorCode(), String.valueOf(refusal.getMessage()));
            }
            throw Refusals.forActivity(error);
        }
    }
}
