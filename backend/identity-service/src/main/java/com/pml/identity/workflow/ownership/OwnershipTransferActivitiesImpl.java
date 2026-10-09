package com.pml.identity.workflow.ownership;

import com.pml.identity.domain.model.OwnershipTransferRequest;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.service.OwnershipTransferService;
import com.pml.shared.workflow.Refusals;
import com.pml.identity.workflow.ownership.OwnershipTransferWorkflow.Nomination;
import com.pml.identity.workflow.ownership.OwnershipTransferWorkflow.View;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * The transfer activities, adapting {@link OwnershipTransferService} to Temporal.
 */
@Component
@ActivityImpl(taskQueues = TaskQueues.ONBOARDING)
public class OwnershipTransferActivitiesImpl implements OwnershipTransferActivities {

    private static final Duration AWAIT = Duration.ofSeconds(25);

    private final OwnershipTransferService transfers;

    public OwnershipTransferActivitiesImpl(OwnershipTransferService transfers) {
        this.transfers = transfers;
    }

    @Override
    public View load(String transferId) {
        return await(transfers.findById(transferId)
                .map(OwnershipTransferActivitiesImpl::viewOf)
                .defaultIfEmpty(new View(transferId, null, null, null, null, 0L)));
    }

    @Override
    public View open(Nomination nomination) {
        return view(transfers.initiate(nomination.transferId(), nomination.organizationId(),
                nomination.currentOwnerId(), nomination.newOwnerId(), nomination.reason()));
    }

    @Override
    public View complete(String transferId) {
        return view(transfers.complete(transferId));
    }

    @Override
    public View decline(String transferId, String actorId) {
        return view(transfers.decline(transferId, actorId));
    }

    @Override
    public View cancel(String transferId, String actorId) {
        return view(transfers.cancel(transferId, actorId));
    }

    @Override
    public View expire(String transferId) {
        return view(transfers.expire(transferId));
    }

    @Override
    public void markMirrorPending(String transferId) {
        await(transfers.markMirrorPending(transferId).thenReturn(Boolean.TRUE));
    }

    static View viewOf(OwnershipTransferRequest transfer) {
        return new View(transfer.getId(), transfer.getOrganizationId(), transfer.getCurrentOwnerId(),
                transfer.getNewOwnerId(), transfer.getStatus(),
                transfer.getExpiresAt() == null ? 0L : transfer.getExpiresAt().toEpochMilli());
    }

    private static View view(Mono<OwnershipTransferRequest> write) {
        return await(write.map(OwnershipTransferActivitiesImpl::viewOf));
    }

    private static <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            throw Refusals.forActivity(error);
        }
    }
}
