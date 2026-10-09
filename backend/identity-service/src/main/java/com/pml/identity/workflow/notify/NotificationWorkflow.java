package com.pml.identity.workflow.notify;

import com.pml.identity.domain.enums.NotificationChannel;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.List;

/**
 * One message, delivered through its fallback chain or recorded as failed.
 *
 * <p>Addressed as {@code notify/{deduplicationKey}} and started with id reuse policy
 * {@code REJECT_DUPLICATE}: a second request for the same fact reaches the running execution or is
 * refused by the server, so a redelivered trigger produces one message.
 *
 * <p>The request names who and what by id. The destination is loaded by an activity at send time,
 * so no phone number or name enters the history, and a recipient who changed their number receives
 * the message at the new one.
 */
@WorkflowInterface
public interface NotificationWorkflow {

    @WorkflowMethod
    void run(Request request);

    @QueryMethod
    Delivery delivery();

    /**
     * @param recipientUserId the recipient's account, or null when the subject names the destination
     * @param subjectType     what the message is about: {@code team-invitation}, {@code verification-document},
     *                        {@code ownership-transfer} or {@code event-reminder}
     * @param subjectId       that record's id
     */
    record Request(String deduplicationKey,
                   String templateKey,
                   String recipientUserId,
                   String subjectType,
                   String subjectId) {
    }

    record Attempt(NotificationChannel channel, String outcome, String failureType) {
    }

    record Delivery(String notificationId, NotificationChannel deliveredVia, List<Attempt> attempts, boolean failed) {
    }
}
