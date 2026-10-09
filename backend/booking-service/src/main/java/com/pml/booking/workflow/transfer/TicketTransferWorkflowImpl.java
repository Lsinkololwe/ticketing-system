package com.pml.booking.workflow.transfer;

import com.pml.booking.domain.enums.TicketTransferStatus;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.workflow.Refusals;
import io.temporal.failure.ActivityFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.time.Duration;

@WorkflowImpl(taskQueues = TaskQueues.CHECKOUT)
public class TicketTransferWorkflowImpl implements TicketTransferWorkflow {

    private final TicketTransferActivities writes =
            Workflow.newActivityStub(TicketTransferActivities.class, TicketTransferRules.writeOptions());
    private final TicketTransferActivities notifications =
            Workflow.newActivityStub(TicketTransferActivities.class, TicketTransferRules.notifyOptions());

    private View view;
    private boolean refused;

    @Override
    public void run(Start start) {
        Workflow.await(TicketTransferRules.BEGIN_WINDOW, () -> view != null || refused);
        if (view == null) {
            return;
        }
        tell(() -> notifications.notifyRecipient(view.transferId()));

        // The offer's life is a timer: when it lapses with nobody having answered, the ticket goes back.
        long remaining = view.expiresAtMillis() - Workflow.currentTimeMillis();
        if (remaining > 0) {
            Workflow.await(Duration.ofMillis(remaining), () -> view.status() != TicketTransferStatus.PENDING);
        }
        if (view.status() == TicketTransferStatus.PENDING) {
            view = writes.release(view.transferId(), TicketTransferStatus.EXPIRED, TicketTransferRules.SYSTEM_ACTOR);
        }
        tell(() -> notifications.notifySender(view.transferId()));
        Workflow.await(Workflow::isEveryHandlerFinished);
    }

    @Override
    public View begin(Begin begin) {
        if (view != null) {
            return view;
        }
        try {
            view = writes.open(begin);
            return view;
        } catch (ActivityFailure failure) {
            refused = true;
            throw Refusals.rethrow(failure);
        }
    }

    @Override
    public void validateAccept(Decision decision) {
        requireOpen();
        if (!decision.actorId().equals(view.toUserId())) {
            throw Refusals.refusal(ErrorCode.TRANSFER_NOT_PENDING, "this transfer was not offered to you");
        }
    }

    @Override
    public View accept(Decision decision) {
        return decide(() -> writes.accept(view.transferId(), decision.actorId()));
    }

    @Override
    public void validateDecline(Decision decision) {
        requireOpen();
        if (!decision.actorId().equals(view.toUserId())) {
            throw Refusals.refusal(ErrorCode.TRANSFER_NOT_PENDING, "this transfer was not offered to you");
        }
    }

    @Override
    public View decline(Decision decision) {
        return decide(() -> writes.release(view.transferId(), TicketTransferStatus.DECLINED, decision.actorId()));
    }

    @Override
    public void validateCancel(Decision decision) {
        requireOpen();
        if (!decision.actorId().equals(view.fromUserId())) {
            throw Refusals.refusal(ErrorCode.TRANSFER_NOT_PENDING, "only the sender can withdraw this transfer");
        }
    }

    @Override
    public View cancel(Decision decision) {
        return decide(() -> writes.release(view.transferId(), TicketTransferStatus.CANCELLED, decision.actorId()));
    }

    @Override
    public View current() {
        return view;
    }

    private View decide(java.util.function.Supplier<View> activity) {
        try {
            view = activity.get();
            return view;
        } catch (ActivityFailure failure) {
            throw Refusals.rethrow(failure);
        }
    }

    private void requireOpen() {
        if (view == null || view.status() != TicketTransferStatus.PENDING) {
            throw Refusals.refusal(ErrorCode.TRANSFER_NOT_PENDING,
                    "the transfer is " + (view == null ? "not open" : view.status()));
        }
    }

    /** A notification that cannot be sent must not stop the ticket being returned or taken. */
    private static void tell(Runnable notification) {
        try {
            notification.run();
        } catch (ActivityFailure unsent) {
            Workflow.getLogger(TicketTransferWorkflowImpl.class).warn("transfer notification was not delivered");
        }
    }
}
