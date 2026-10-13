package com.pml.identity.workflow.staff;

import com.pml.identity.account.StaffAccountSteps;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.shared.workflow.Refusals;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/** Adapts {@link StaffAccountSteps} to Temporal; a refusal leaves non-retryable and typed by its code. */
@Component
@ActivityImpl(taskQueues = TaskQueues.ONBOARDING)
public class StaffAccountActivitiesImpl implements StaffAccountActivities {

    private static final Duration AWAIT = Duration.ofSeconds(25);

    private final StaffAccountSteps steps;

    public StaffAccountActivitiesImpl(StaffAccountSteps steps) {
        this.steps = steps;
    }

    @Override
    public Claimed claim(String sealed) {
        StaffAccountSteps.Claimed claimed = await(steps.claim(sealed));
        return new Claimed(claimed.accountId(), claimed.finished());
    }

    @Override
    public KeycloakUser createInKeycloak(String accountId, String sealed) {
        StaffAccountSteps.KeycloakUser user = await(steps.createInKeycloak(accountId, sealed));
        return new KeycloakUser(user.id(), user.createdHere());
    }

    @Override
    public String link(Link link) {
        return await(steps.link(link.accountId(), link.keycloakUserId(), link.createdHere()));
    }

    @Override
    public void grantRole(String accountId) {
        await(steps.grantRole(accountId).thenReturn(Boolean.TRUE));
    }

    @Override
    public void activate(String accountId) {
        await(steps.activate(accountId).thenReturn(Boolean.TRUE));
    }

    @Override
    public void compensate(String accountId) {
        await(steps.compensate(accountId).thenReturn(Boolean.TRUE));
    }

    @Override
    public void notifyCreated(String accountId) {
        await(steps.notifyCreated(accountId).thenReturn(Boolean.TRUE));
    }

    private static <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            // A refusal ends the creation whatever its code says about a caller retrying: RESOURCE_CONFLICT is
            // "ask again" to a person, but an activity that retried it would repeat the same conflict.
            if (reactor.core.Exceptions.unwrap(error) instanceof com.pml.shared.error.DomainRefusal refusal) {
                throw Refusals.refusal(refusal.errorCode(), String.valueOf(refusal.getMessage()));
            }
            throw Refusals.forActivity(error);
        }
    }
}
