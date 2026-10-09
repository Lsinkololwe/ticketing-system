package com.pml.identity.workflow.notify;

import java.util.List;
import com.pml.identity.domain.enums.NotificationChannel;
import com.pml.identity.workflow.notify.NotificationWorkflow.Request;
import io.temporal.activity.ActivityInterface;

/**
 * The notification's record and its sends.
 */
@ActivityInterface(namePrefix = "Notification")
public interface NotificationActivities {

    /** Writes the {@code PENDING} notification once, keyed by its deduplication key; returns its id. */
    String record(Request request);

    /**
     * One send on one channel. A provider that does not accept fails retryably as
     * {@code CHANNEL_UNAVAILABLE}; a recipient with no verified contact on the channel fails at once as {@code NO_DESTINATION}.
     */
    void send(String notificationId, NotificationChannel channel);

    /**
     * The channels this message may use, in chain order. An essential message gets the whole chain;
     * an optional one what the recipient's preferences leave, and when that is nothing the
     * notification is recorded as {@code SUPPRESSED} and the list is empty.
     */
    List<NotificationChannel> channelsFor(String notificationId);

    void markDelivered(String notificationId, NotificationChannel channel);

    void markFailed(String notificationId, String reason);
}
