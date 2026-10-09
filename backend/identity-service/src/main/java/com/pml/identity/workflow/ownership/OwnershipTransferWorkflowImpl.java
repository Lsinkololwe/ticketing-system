package com.pml.identity.workflow.ownership;

import com.pml.identity.domain.enums.TransferStatus;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.shared.workflow.Refusal;
import com.pml.shared.workflow.Refusals;
import com.pml.identity.workflow.notify.NotificationRules;
import com.pml.identity.workflow.notify.NotificationWorkflow.Request;
import com.pml.identity.workflow.notify.NotifyActivities;
import com.pml.shared.error.ErrorCode;
import io.temporal.failure.ActivityFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * A transfer waits three days for its nominee, then expires.
 *
 * <pre>
 *   open ─▶ PENDING ─accept──▶ COMPLETED ─▶ Keycloak mirror (retried; marked for repair if it never lands)
 *              ├─decline─▶ CANCELLED
 *              ├─cancel──▶ CANCELLED
 *              └─P3D─────▶ EXPIRED
 * </pre>
 *
 * <p>MongoDB wins: the handover commits before Keycloak is touched, and a Keycloak outage delays
 * only the mirror. Notifications are requested, never awaited for delivery, so a messaging outage
 * changes nothing about who owns the organization.
 */
@WorkflowImpl(taskQueues = TaskQueues.ONBOARDING)
public class OwnershipTransferWorkflowImpl implements OwnershipTransferWorkflow {

    private static final String REQUESTED = "ownership.transfer-requested";
    private static final String CONFIRMED = "ownership.confirmed";

    private final OwnershipTransferActivities records =
            Workflow.newActivityStub(OwnershipTransferActivities.class, OwnershipTransferRules.recordOptions());
    private final OwnershipTransferActivities patient =
            Workflow.newActivityStub(OwnershipTransferActivities.class, OwnershipTransferRules.patientOptions());
    private final OwnershipMirrorActivities keycloak =
            Workflow.newActivityStub(OwnershipMirrorActivities.class, OwnershipTransferRules.mirrorOptions());
    private final NotifyActivities notify =
            Workflow.newActivityStub(NotifyActivities.class, NotificationRules.requestOptions());

    private String transferId;
    private View view;
    private boolean loading;
    private boolean resolving;
    private boolean completedHere;
    private int commands;

    @Override
    public void run(Start start) {
        if (transferId == null) {
            transferId = start.transferId();
        }
        Workflow.await(OwnershipTransferRules.FIRST_COMMAND_WINDOW, () -> commands > 0);
        ensureLoaded();
        Workflow.await(Workflow::isEveryHandlerFinished);

        if (view.status() == TransferStatus.PENDING) {
            long current = Workflow.currentTimeMillis();
            if (!OwnershipTransferRules.expired(view.expiresAtMillis(), current)) {
                Workflow.await(OwnershipTransferRules.remaining(view.expiresAtMillis(), current),
                        () -> view.status() != TransferStatus.PENDING || resolving);
            }
            Workflow.await(() -> !resolving);
            if (view.status() == TransferStatus.PENDING) {
                view = patient.expire(transferId);
            }
        }

        if (completedHere) {
            mirrorGroups();
            requestQuietly(CONFIRMED, view.currentOwnerId());
            requestQuietly(CONFIRMED, view.newOwnerId());
        }
        Workflow.await(Workflow::isEveryHandlerFinished);
    }

    private void mirrorGroups() {
        try {
            keycloak.mirror(transferId);
        } catch (ActivityFailure failure) {
            patient.markMirrorPending(transferId);
        }
    }

    // ---- updates -------------------------------------------------------------------------------

    @Override
    public void validateOpen(Nomination nomination) {
        refuseIf(OwnershipTransferRules.nominationRefusal(nomination));
        requireTransfer(nomination.transferId());
    }

    @Override
    public View open(Nomination nomination) {
        begin(nomination.transferId());
        refuseIf(OwnershipTransferRules.nominationRefusal(nomination));
        if (view.status() != null) {
            return view;
        }
        view = command(() -> records.open(nomination));
        requestQuietly(REQUESTED, view.newOwnerId());
        return view;
    }

    @Override
    public void validateAccept(Response response) {
        requireTransfer(response.transferId());
        if (view != null) {
            refuseIf(OwnershipTransferRules.acceptRefusal(view, response.actorId(), Workflow.currentTimeMillis()));
        }
    }

    @Override
    public View accept(Response response) {
        begin(response.transferId());
        refuseIf(OwnershipTransferRules.acceptRefusal(view, response.actorId(), Workflow.currentTimeMillis()));
        view = resolve(() -> records.complete(transferId));
        completedHere = true;
        return view;
    }

    @Override
    public void validateDecline(Response response) {
        requireTransfer(response.transferId());
        if (view != null) {
            refuseIf(OwnershipTransferRules.declineRefusal(view, response.actorId()));
        }
    }

    @Override
    public View decline(Response response) {
        begin(response.transferId());
        refuseIf(OwnershipTransferRules.declineRefusal(view, response.actorId()));
        view = resolve(() -> records.decline(transferId, response.actorId()));
        return view;
    }

    @Override
    public void validateCancel(Response response) {
        requireTransfer(response.transferId());
        if (view != null) {
            refuseIf(OwnershipTransferRules.cancelRefusal(view, response.actorId()));
        }
    }

    @Override
    public View cancel(Response response) {
        begin(response.transferId());
        refuseIf(OwnershipTransferRules.cancelRefusal(view, response.actorId()));
        view = resolve(() -> records.cancel(transferId, response.actorId()));
        return view;
    }

    @Override
    public View current() {
        return view;
    }

    // ---- helpers -------------------------------------------------------------------------------

    /** A resolving command holds the expiry back until its write has landed or been refused. */
    private View resolve(Supplier<View> activity) {
        resolving = true;
        try {
            return command(activity);
        } finally {
            resolving = false;
        }
    }

    private void requestQuietly(String templateKey, String recipientUserId) {
        try {
            notify.request(new Request(NotificationRules.key(templateKey, transferId + ":" + recipientUserId),
                    templateKey, recipientUserId, NotificationRules.OWNERSHIP_TRANSFER, transferId));
        } catch (ActivityFailure failure) {
            // A message that cannot be requested does not change the transfer.
        }
    }

    private void begin(String commandTransferId) {
        commands++;
        if (transferId == null) {
            transferId = commandTransferId;
        }
        requireTransfer(commandTransferId);
        ensureLoaded();
    }

    private void requireTransfer(String commandTransferId) {
        if (commandTransferId == null || commandTransferId.isBlank()) {
            throw Refusals.refusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "a command names its transfer");
        }
        if (transferId != null && !transferId.equals(commandTransferId)) {
            throw Refusals.refusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "this execution belongs to another transfer");
        }
    }

    private void ensureLoaded() {
        if (view != null) {
            return;
        }
        if (loading) {
            Workflow.await(() -> view != null);
            return;
        }
        loading = true;
        try {
            view = patient.load(transferId);
        } finally {
            loading = false;
        }
    }

    private static View command(Supplier<View> activity) {
        try {
            return activity.get();
        } catch (ActivityFailure failure) {
            throw Refusals.rethrow(failure);
        }
    }

    private static void refuseIf(Optional<Refusal> refusal) {
        if (refusal.isPresent()) {
            throw refusal.get().failure();
        }
    }
}
