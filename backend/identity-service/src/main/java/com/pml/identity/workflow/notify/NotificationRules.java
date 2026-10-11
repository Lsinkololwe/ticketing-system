package com.pml.identity.workflow.notify;

import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.enums.NotificationChannel;
import com.pml.identity.domain.enums.NotificationType;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.workflow.notify.NotificationWorkflow.Attempt;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The delivery decisions that need neither a server nor a provider.
 */
public final class NotificationRules {

    /**
     * The channels a person can be reached on, in the order tried when they have no preference:
     * WhatsApp, then email. Which of them are used for one person is {@link #chainFor}.
     */
    public static final List<NotificationChannel> CHAIN = List.of(NotificationChannel.WHATSAPP, NotificationChannel.EMAIL);

    /**
     * What every notification used before accounts were addressed by their contacts: WhatsApp, then
     * SMS, to a phone number. Executions already running replay on it; new ones never choose it.
     */
    public static final List<NotificationChannel> LEGACY_CHAIN =
            List.of(NotificationChannel.WHATSAPP, NotificationChannel.SMS);

    /** {@code notification.max-attempts}, per channel. */
    public static final int ATTEMPTS_PER_CHANNEL = 3;

    /** The first retry interval; each later one doubles. */
    public static final Duration BACKOFF = Duration.ofSeconds(30);

    /** The recipient has no verified contact on this channel: it is skipped at once rather than retried. */
    public static final String NO_DESTINATION = "NO_DESTINATION";

    /** The provider did not accept the message; retried within the channel's budget. */
    public static final String CHANNEL_UNAVAILABLE = "CHANNEL_UNAVAILABLE";

    public static final String TEAM_INVITATION = "team-invitation";
    public static final String VERIFICATION_DOCUMENT = "verification-document";
    public static final String OWNERSHIP_TRANSFER = "ownership-transfer";
    public static final String EVENT_REMINDER = "event-reminder";

    /** A booking escalation addressed to a holder of {@code FINANCE_LEAD}. */
    public static final String FINANCE_ESCALATION = "finance-escalation";

    /** An event's review: its arrival in the approvals queue, and the decision taken on it. */
    public static final String EVENT_REVIEW = "event-review";

    /** Sent to every active platform administrator when an event is submitted or resubmitted. */
    public static final String EVENT_PENDING = "admin.event-pending";

    /** Sent to the event's organizer when a reviewer decides. */
    public static final Set<String> EVENT_DECISIONS = Set.of("event.approved", "event.rejected", "event.changes-requested");

    /**
     * What a template says. A template's text may hold {@code {name}} or {@code {name|fallback}}
     * placeholders, filled from the text the caller stored with the notification (the workflow's request never
     * carries it); a placeholder with neither a value nor a fallback is removed.
     *
     * <p>Wording rules for every template: lead with what happened, name the one thing to do next, say what
     * becomes of the ticket, carry no link (a message with a link is what a phishing message looks like, so the
     * person is sent to the app instead), and use another person's name only where the message is about them.
     */
    public record Message(NotificationType type, String title, String body) {
    }

    private static final Map<String, Message> TEMPLATES = Map.ofEntries(
            Map.entry("team.invitation", new Message(NotificationType.SYSTEM_ANNOUNCEMENT, "Team invitation",
                    "You have been invited to join an organization on MyTicketZM. Open the app to respond.")),
            Map.entry("team.accepted", new Message(NotificationType.SYSTEM_ANNOUNCEMENT, "Invitation accepted",
                    "Your team invitation was accepted.")),
            Map.entry("document.approved", new Message(NotificationType.SYSTEM_ANNOUNCEMENT, "Document approved",
                    "A verification document for your organization was approved.")),
            Map.entry("document.rejected", new Message(NotificationType.SYSTEM_ANNOUNCEMENT, "Document rejected",
                    "A verification document for your organization was rejected. Open the app to see why.")),
            Map.entry("ownership.transfer-requested", new Message(NotificationType.SYSTEM_ANNOUNCEMENT, "Ownership transfer",
                    "You have been nominated to take over an organization. Open the app to accept or decline.")),
            Map.entry("ownership.confirmed", new Message(NotificationType.SYSTEM_ANNOUNCEMENT, "Ownership transferred",
                    "The organization's ownership transfer is complete.")),
            Map.entry("event.reminder.24h", new Message(NotificationType.EVENT_REMINDER, "Event reminder",
                    "Your event starts in 24 hours.")),
            Map.entry("event.reminder.1h", new Message(NotificationType.EVENT_REMINDER, "Event reminder",
                    "Your event starts in one hour.")),
            Map.entry("finance.chargeback-undecided", new Message(NotificationType.SYSTEM_ANNOUNCEMENT, "Chargeback needs a decision",
                    "A chargeback has no decision and is accepted automatically in 24 hours. Open the admin app to decide.")),
            Map.entry("finance.refund-waiting", new Message(NotificationType.SYSTEM_ANNOUNCEMENT, "Refund waiting for approval",
                    "A refund request is still waiting for approval. Open the admin app to approve or reject it.")),
            Map.entry(EVENT_PENDING, new Message(NotificationType.SYSTEM_ANNOUNCEMENT, "Event waiting for approval",
                    "An event was submitted and is waiting for approval. Open the admin app to review it.")),
            Map.entry("event.approved", new Message(NotificationType.SYSTEM_ANNOUNCEMENT, "Event approved",
                    "Your event was approved. Open the app to publish it.")),
            Map.entry("event.rejected", new Message(NotificationType.SYSTEM_ANNOUNCEMENT, "Event not approved",
                    "Your event was not approved. Open the app to see the reviewer's reason.")),
            Map.entry("event.changes-requested", new Message(NotificationType.SYSTEM_ANNOUNCEMENT, "Changes requested",
                    "A reviewer asked for changes to your event. Open the app to see what to change and resubmit.")),
            Map.entry("ticket.resend", new Message(NotificationType.TICKET_PURCHASED, "Your ticket",
                    "Your ticket {ticketNumber} for {eventTitle|your event} is ready in the MyTicketZM app. Show it at the door.")),
            Map.entry("ticket.transfer.offered", new Message(NotificationType.SYSTEM_ANNOUNCEMENT, "Ticket offered to you",
                    "{fromDisplayName|Someone} sent you a ticket for {eventTitle|an event}. Open the MyTicketZM app to accept or decline it.")),
            Map.entry("ticket.transfer.accepted", new Message(NotificationType.SYSTEM_ANNOUNCEMENT, "Ticket transfer accepted",
                    "{toDisplayName|The recipient} accepted the ticket you sent for {eventTitle|your event}. The ticket is now theirs.")),
            Map.entry("ticket.transfer.declined", new Message(NotificationType.SYSTEM_ANNOUNCEMENT, "Ticket transfer declined",
                    "{toDisplayName|The recipient} declined the ticket you sent for {eventTitle|your event}. The ticket is back in your account.")),
            Map.entry("ticket.transfer.expired", new Message(NotificationType.SYSTEM_ANNOUNCEMENT, "Ticket transfer expired",
                    "The ticket you sent for {eventTitle|your event} was not accepted in time. The ticket is back in your account.")),
            Map.entry("event.holders.message", new Message(NotificationType.EVENT_UPDATED, "{subject|Message from the organizer}",
                    "{eventTitle|Your event} · message from the organizer: {message}")));

    private static final java.util.regex.Pattern PLACEHOLDER =
            java.util.regex.Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*)(?:\\|([^}]*))?}");

    private NotificationRules() {
    }

    /** The key a fact's notification is deduplicated on: the template and what distinguishes this occurrence. */
    public static String key(String templateKey, String discriminator) {
        if (templateKey == null || templateKey.isBlank() || discriminator == null || discriminator.isBlank()) {
            throw new IllegalArgumentException("a deduplication key needs its template and its discriminator");
        }
        return templateKey + ":" + discriminator;
    }

    /** The {@code identity_notifications} id for a key, so recording a notification twice writes one row. */
    public static String notificationId(String deduplicationKey) {
        return "notify:" + deduplicationKey;
    }

    /** A template that is not registered is refused, never sent as an empty message. */
    public static Message render(String templateKey) {
        return render(templateKey, null);
    }

    /**
     * The template with its placeholders filled from {@code params}. Values are inserted as text and
     * never re-scanned, so a value that itself looks like a placeholder is sent as written.
     */
    public static Message render(String templateKey, Map<String, String> params) {
        Message message = TEMPLATES.get(templateKey);
        if (message == null) {
            throw new IllegalArgumentException("no template " + templateKey);
        }
        if (params == null || params.isEmpty()) {
            return new Message(message.type(), fill(message.title(), Map.of()), fill(message.body(), Map.of()));
        }
        return new Message(message.type(), fill(message.title(), params), fill(message.body(), params));
    }

    private static String fill(String text, Map<String, String> params) {
        if (text.indexOf('{') < 0) {
            return text;
        }
        return PLACEHOLDER.matcher(text).replaceAll(match -> {
            String value = params.get(match.group(1));
            String chosen = value != null && !value.isBlank() ? value : match.group(2);
            return java.util.regex.Matcher.quoteReplacement(chosen == null ? "" : chosen);
        });
    }

    /**
     * The channels to try for one recipient, from what they can actually be reached on.
     *
     * <p>Only channels with a verified contact are used, the person's preferred one first. A
     * recipient reachable on email alone gets email without a WhatsApp attempt that is certain to
     * fail. A recipient reachable on nothing gets the default chain, so the notification fails
     * visibly as {@code NO_DESTINATION} instead of vanishing.
     *
     * @param preferred the recipient's preferred channel; null when they have not chosen
     * @param reachable the contact types they hold verified
     */
    public static List<NotificationChannel> chainFor(ContactType preferred, java.util.Collection<ContactType> reachable) {
        List<NotificationChannel> chain = new java.util.ArrayList<>();
        if (preferred != null && reachable.contains(preferred)) {
            chain.add(channelOf(preferred));
        }
        for (NotificationChannel channel : CHAIN) {
            ContactType type = contactTypeOf(channel);
            if (type != null && reachable.contains(type) && !chain.contains(channel)) {
                chain.add(channel);
            }
        }
        return chain.isEmpty() ? CHAIN : List.copyOf(chain);
    }

    public static NotificationChannel channelOf(ContactType type) {
        return type == ContactType.EMAIL ? NotificationChannel.EMAIL : NotificationChannel.WHATSAPP;
    }

    /** The contact type a channel sends to; null for channels that are not addressed by a contact. */
    public static ContactType contactTypeOf(NotificationChannel channel) {
        return switch (channel) {
            case WHATSAPP -> ContactType.WHATSAPP;
            case EMAIL -> ContactType.EMAIL;
            case SMS, PUSH, IN_APP -> null;
        };
    }

    /** The templates a booking escalation may name. */
    public static boolean isFinanceEscalation(String templateKey) {
        return templateKey != null && templateKey.startsWith("finance.") && registered(templateKey);
    }

    /** The templates an event review may name. */
    public static boolean isEventReview(String templateKey) {
        // Set.of rejects a null lookup with an exception, so a missing key is answered here.
        return templateKey != null && (EVENT_PENDING.equals(templateKey) || EVENT_DECISIONS.contains(templateKey));
    }

    public static boolean registered(String templateKey) {
        return TEMPLATES.containsKey(templateKey);
    }

    /** The name {@code MessagingService} routes on. */
    public static String channelName(NotificationChannel channel) {
        return channel.name().toLowerCase(Locale.ROOT);
    }

    /** The reason a notification rests as failed, naming each channel's last failure. */
    public static String exhausted(List<Attempt> attempts) {
        if (attempts.isEmpty()) {
            return "no channel was attempted";
        }
        return "every channel failed: " + attempts.stream()
                .map(attempt -> attempt.channel() + "=" + attempt.failureType())
                .collect(Collectors.joining(", "));
    }

    /** Three attempts per channel, exponential from thirty seconds; a missing destination is not retried. */
    static ActivityOptions sendOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.NOTIFY)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(ATTEMPTS_PER_CHANNEL)
                        .setInitialInterval(BACKOFF)
                        .setBackoffCoefficient(2.0)
                        .setDoNotRetry(NO_DESTINATION)
                        .build())
                .build();
    }

    /** Writes to {@code identity_notifications}: retried until they land. */
    static ActivityOptions recordOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.NOTIFY)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setMaximumInterval(Duration.ofMinutes(1))
                        .build())
                .build();
    }

    /**
     * A workflow asking for a message: a few attempts to start the notification's execution, after
     * which the asking process carries on — a message never holds up the business step that caused it.
     */
    public static ActivityOptions requestOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.NOTIFY)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(5)
                        .setInitialInterval(Duration.ofSeconds(2))
                        .setMaximumInterval(Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
