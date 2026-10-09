package com.pml.identity.workflow.ensure;

import com.pml.identity.account.AccountGuard;
import com.pml.identity.account.AccountProvisioning;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.shared.workflow.Refusals;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Adapts {@link AccountProvisioning} to Temporal. The blocking happens here, on the activity's own
 * thread; a refusal leaves as a non-retryable failure typed by its error code, anything else is
 * retried by the activity's policy.
 */
@Component
@ActivityImpl(taskQueues = TaskQueues.ACCOUNT)
public class AccountEnsureActivitiesImpl implements AccountEnsureActivities {

    private static final Duration AWAIT = Duration.ofSeconds(25);

    private final AccountProvisioning accounts;

    public AccountEnsureActivitiesImpl(AccountProvisioning accounts) {
        this.accounts = accounts;
    }

    @Override
    public Claimed claimContact(Claim claim) {
        AccountProvisioning.Claimed claimed = await(accounts.claim(claim.proofId(), claim.contactKey(), claim.contactType()));
        // Non-retryable on purpose: ACCOUNT_MERGING is "try again later" to a person, but a workflow
        // that retried it would hold the execution open for the whole merge.
        AccountGuard.refusal(claimed.state(), claimed.pendingKind()).ifPresent(code -> {
            throw Refusals.refusal(code, "account " + claimed.accountId() + " cannot be signed in");
        });
        return new Claimed(claimed.accountId(), claimed.state(), claimed.created());
    }

    @Override
    public String createKeycloakUser(String accountId) {
        return await(accounts.createKeycloakUser(accountId));
    }

    @Override
    public void applyAttributesAndRoles(String accountId, String keycloakUserId) {
        await(accounts.applyAttributes(accountId, keycloakUserId).thenReturn(Boolean.TRUE));
    }

    @Override
    public AccountState activateAccount(Activation activation) {
        return await(accounts.activate(activation.accountId(), activation.keycloakUserId(), activation.clientId(),
                activation.displayName(), activation.consents())).state();
    }

    @Override
    public void stageOutbox(String accountId) {
        await(accounts.stageOutbox(accountId).thenReturn(Boolean.TRUE));
    }

    private static <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            // A refusal ends the process whatever its code says about a caller retrying. CONTACT_ALREADY_CLAIMED is
            // "try again" to a person (and from claim() it is only raised for a contact in quarantine), but an
            // activity that retried it would hold the execution open for the whole quarantine.
            if (reactor.core.Exceptions.unwrap(error) instanceof com.pml.shared.error.DomainRefusal refusal) {
                throw Refusals.refusal(refusal.errorCode(), String.valueOf(refusal.getMessage()));
            }
            throw Refusals.forActivity(error);
        }
    }
}
