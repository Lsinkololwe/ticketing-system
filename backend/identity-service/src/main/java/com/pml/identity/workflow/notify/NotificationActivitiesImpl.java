package com.pml.identity.workflow.notify;

import com.pml.identity.domain.model.NotificationPreferences;
import java.util.List;
import java.util.Optional;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.enums.NotificationChannel;
import com.pml.identity.domain.enums.NotificationStatus;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.Notification;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.TeamInvitation;
import com.pml.identity.domain.model.User;
import com.pml.identity.domain.model.VerificationDocument;
import com.pml.identity.infrastructure.messaging.MessagingService;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.security.ContactCrypto;
import com.pml.shared.workflow.Refusals;
import com.pml.identity.workflow.notify.NotificationRules.Message;
import com.pml.identity.workflow.notify.NotificationWorkflow.Request;
import io.temporal.failure.ApplicationFailure;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Writes {@code identity_notifications} and sends through {@link MessagingService}.
 *
 * <p>The destination is resolved here, at send time, from the ids the request carries: the
 * recipient's account, the invitation's addressee, or the document's organization's owner. For an
 * account it is the verified contact of the channel's type - the WhatsApp number or the email - read
 * from {@code identity_contacts} and decrypted only for the send; the channels tried are the ones the
 * account can be reached on, its preferred one first. A destination is handed to the provider and
 * never logged or returned.
 */
@Component
@ActivityImpl(taskQueues = TaskQueues.NOTIFY)
public class NotificationActivitiesImpl implements NotificationActivities {

    private static final Duration AWAIT = Duration.ofSeconds(25);

    private final ReactiveMongoTemplate template;
    private final MessagingService messaging;
    private final ContactCrypto crypto;
    private final Clock clock;

    public NotificationActivitiesImpl(ReactiveMongoTemplate template, MessagingService messaging,
                                      ContactCrypto crypto, Clock clock) {
        this.template = template;
        this.messaging = messaging;
        this.crypto = crypto;
        this.clock = clock;
    }

    @Override
    public String record(Request request) {
        String id = NotificationRules.notificationId(request.deduplicationKey());
        return await(template.findById(id, Notification.class)
                .map(Notification::getId)
                .switchIfEmpty(Mono.defer(() -> insert(id, request))));
    }

    private Mono<String> insert(String id, Request request) {
        Message message = NotificationRules.render(request.templateKey());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deduplicationKey", request.deduplicationKey());
        data.put("templateKey", request.templateKey());
        if (request.subjectType() != null) {
            data.put("subjectType", request.subjectType());
            data.put("subjectId", request.subjectId());
        }
        Notification notification = Notification.builder()
                .id(id)
                .userId(request.recipientUserId())
                .type(message.type())
                .title(message.title())
                .body(message.body())
                .data(data)
                .channels(NotificationRules.CHAIN)
                .status(NotificationStatus.PENDING)
                .createdAt(clock.instant())
                .build();
        return template.insert(notification)
                .map(Notification::getId)
                .onErrorResume(DuplicateKeyException.class, recordedConcurrently -> Mono.just(id));
    }

    @Override
    public void send(String notificationId, NotificationChannel channel) {
        await(template.findById(notificationId, Notification.class)
                .switchIfEmpty(Mono.error(() -> ApplicationFailure.newNonRetryableFailure(
                        "no notification " + notificationId, NotificationRules.NO_DESTINATION)))
                .flatMap(notification -> destination(notification, channel)
                        .switchIfEmpty(Mono.error(() -> ApplicationFailure.newNonRetryableFailure(
                                "the recipient has no verified contact on " + channel, NotificationRules.NO_DESTINATION)))
                        .flatMap(phone -> messaging.sendNotification(phone, notification.getBody(),
                                NotificationRules.channelName(channel))))
                .flatMap(accepted -> accepted
                        ? Mono.just(Boolean.TRUE)
                        : Mono.error(ApplicationFailure.newFailure(channel + " did not accept the message",
                                NotificationRules.CHANNEL_UNAVAILABLE))));
    }

    @Override
    public List<NotificationChannel> channelsFor(String notificationId) {
        return await(template.findById(notificationId, Notification.class)
                .flatMap(notification -> (notification.getUserId() == null
                        ? Mono.<NotificationPreferences>empty()
                        : template.findOne(Query.query(Criteria.where("userId").is(notification.getUserId())),
                                NotificationPreferences.class))
                        .map(Optional::of)
                        .defaultIfEmpty(Optional.empty())
                        .flatMap(preferences -> {
                            PreferenceGate.Verdict verdict =
                                    PreferenceGate.decide(preferences.orElse(null), notification.getType(), clock.instant());
                            if (!verdict.suppressed()) {
                                return reachable(notification).map(reach -> NotificationRules
                                        .chainFor(reach.preferred(), reach.types()).stream()
                                        .filter(verdict.channels()::contains)
                                        .toList());
                            }
                            return template.updateFirst(Query.query(Criteria.where("_id").is(notificationId)),
                                            new Update().set("status", NotificationStatus.SUPPRESSED)
                                                    .set("data.suppressedBecause", verdict.suppressedBecause()),
                                            Notification.class)
                                    .thenReturn(List.<NotificationChannel>of());
                        }))
                .defaultIfEmpty(NotificationRules.CHAIN));
    }

    @Override
    public void markDelivered(String notificationId, NotificationChannel channel) {
        await(template.updateFirst(Query.query(Criteria.where("_id").is(notificationId)),
                        new Update().set("status", NotificationStatus.DELIVERED)
                                .set("sentAt", clock.instant())
                                .set("deliveredAt", clock.instant())
                                .set("data.deliveredVia", channel.name()),
                        Notification.class)
                .thenReturn(Boolean.TRUE));
    }

    @Override
    public void markFailed(String notificationId, String reason) {
        await(template.updateFirst(Query.query(Criteria.where("_id").is(notificationId)),
                        new Update().set("status", NotificationStatus.FAILED).set("data.failureReason", reason),
                        Notification.class)
                .thenReturn(Boolean.TRUE));
    }

    /** Who a notification can reach, and how they would rather be reached. */
    private record Reach(ContactType preferred, Set<ContactType> types) {
    }

    private Mono<Reach> reachable(Notification notification) {
        Map<String, Object> data = notification.getData() == null ? Map.of() : notification.getData();
        if (notification.getUserId() == null
                && NotificationRules.TEAM_INVITATION.equals(String.valueOf(data.get("subjectType")))) {
            return template.findById(String.valueOf(data.get("subjectId")), TeamInvitation.class)
                    .map(invitation -> new Reach(null, java.util.Arrays.stream(ContactType.values())
                            .filter(type -> type == ContactType.WHATSAPP
                                    ? present(invitation.getPhoneNumber()) : present(invitation.getEmail()))
                            .collect(Collectors.toSet())))
                    .defaultIfEmpty(new Reach(null, Set.of()));
        }
        return accountOf(notification)
                .flatMap(accountId -> template.findById(accountId, User.class)
                        .flatMap(account -> template.find(Query.query(Criteria.where("accountId").is(accountId)
                                                .and("verifiedAt").exists(true).and("releasedAt").is(null)), Contact.class)
                                        .map(Contact::getType)
                                        .collect(Collectors.toSet())
                                        .map(types -> {
                                            // An account that predates contacts may still carry its number or address.
                                            Set<ContactType> reach = new java.util.HashSet<>(types);
                                            if (present(account.getPhoneNumber())) {
                                                reach.add(ContactType.WHATSAPP);
                                            }
                                            if (present(account.getEmail())) {
                                                reach.add(ContactType.EMAIL);
                                            }
                                            return new Reach(account.getPreferredChannel(), reach);
                                        })))
                .defaultIfEmpty(new Reach(null, Set.of()));
    }

    /** The account a notification is addressed to: the recipient, or the owner of the document's organization. */
    private Mono<String> accountOf(Notification notification) {
        if (notification.getUserId() != null) {
            return Mono.just(notification.getUserId());
        }
        Map<String, Object> data = notification.getData() == null ? Map.of() : notification.getData();
        if (NotificationRules.VERIFICATION_DOCUMENT.equals(String.valueOf(data.get("subjectType")))) {
            return template.findById(String.valueOf(data.get("subjectId")), VerificationDocument.class)
                    .flatMap(document -> template.findById(document.getOrganizationId(), Organization.class))
                    .map(Organization::getOwnerId);
        }
        return Mono.empty();
    }

    /** Where a notification goes on one channel - the WhatsApp number or the email - or empty when there is none. */
    Mono<String> destination(Notification notification, NotificationChannel channel) {
        ContactType type = NotificationRules.contactTypeOf(channel);
        if (type == null) {
            return Mono.empty();
        }
        Map<String, Object> data = notification.getData() == null ? Map.of() : notification.getData();
        if (notification.getUserId() == null
                && NotificationRules.TEAM_INVITATION.equals(String.valueOf(data.get("subjectType")))) {
            return template.findById(String.valueOf(data.get("subjectId")), TeamInvitation.class)
                    .mapNotNull(invitation -> type == ContactType.WHATSAPP ? invitation.getPhoneNumber() : invitation.getEmail())
                    .filter(NotificationActivitiesImpl::present);
        }
        return accountOf(notification).flatMap(accountId -> contactValue(accountId, type));
    }

    private Mono<String> contactValue(String accountId, ContactType type) {
        return template.findOne(Query.query(Criteria.where("accountId").is(accountId)
                                .and("type").is(type)
                                .and("verifiedAt").exists(true)
                                .and("releasedAt").is(null)), Contact.class)
                .flatMap(contact -> crypto.decrypt(contact.getValueEncrypted()))
                .switchIfEmpty(Mono.defer(() -> template.findById(accountId, User.class)
                        .mapNotNull(account -> type == ContactType.WHATSAPP ? account.getPhoneNumber() : account.getEmail())
                        .filter(NotificationActivitiesImpl::present)));
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            throw Refusals.forActivity(error);
        }
    }
}
