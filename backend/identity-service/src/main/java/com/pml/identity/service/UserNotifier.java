package com.pml.identity.service;

import com.pml.identity.account.AccountStates;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.enums.NotificationChannel;
import com.pml.identity.domain.enums.NotificationStatus;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.Notification;
import com.pml.identity.domain.model.User;
import com.pml.identity.repository.ContactRepository;
import com.pml.identity.repository.NotificationRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.workflow.notify.NotificationProcess;
import com.pml.identity.workflow.notify.NotificationRules;
import com.pml.identity.workflow.notify.NotificationWorkflow.Request;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import com.pml.shared.util.ContactMasking;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Sends one templated message to accounts another service names by id: a ticket resent, a transfer
 * offered, a message from an organizer to the holders of an event.
 *
 * <p>The caller never learns more than it needs. A receipt carries the channel and the masked
 * destination, and an account that does not exist, is not active or has nowhere verified to be
 * reached all answer the same {@link #NO_VERIFIED_CONTACT}. Each message is deduplicated on the
 * template, the caller's discriminator and the recipient, so a caller that retries sends nothing twice.
 */
@Service
public class UserNotifier {

    public static final String QUEUED = "QUEUED";
    public static final String DUPLICATE = "DUPLICATE";
    public static final String NO_VERIFIED_CONTACT = "NO_VERIFIED_CONTACT";

    /** The most accounts one batch may name. */
    public static final int MAX_BATCH = 100;

    static final int MAX_PARAMS = 20;
    static final int MAX_KEY = 64;
    static final int MAX_VALUE = 500;

    private static final Pattern KEY = Pattern.compile("[A-Za-z][A-Za-z0-9]*");

    /** What one request reached; the channel and masked destination are null when nothing was sent. */
    public record Receipt(String status, String channel, String destination, int recipients) {
    }

    /**
     * What a batch reached, per outcome.
     *
     * @param status     {@link #QUEUED} when any account was reached, {@link #DUPLICATE} when every account had
     *                   already been sent this batch, {@link #NO_VERIFIED_CONTACT} when none could be reached
     * @param recipients accounts reached, counting those already sent to by an earlier identical request
     */
    public record BatchReceipt(String status, String channel, String destination, int recipients,
                               int queued, int duplicate, int noVerifiedContact) {
    }

    private record Reach(NotificationChannel channel, String destination) {
    }

    private final UserRepository users;
    private final ContactRepository contacts;
    private final NotificationRepository notifications;
    private final NotificationProcess process;
    private final Clock clock;

    public UserNotifier(UserRepository users, ContactRepository contacts, NotificationRepository notifications,
                        NotificationProcess process, Clock clock) {
        this.users = users;
        this.contacts = contacts;
        this.notifications = notifications;
        this.process = process;
        this.clock = clock;
    }

    public Mono<Receipt> notifyUser(String templateKey, String discriminator, String userId, Map<String, ?> rawParams) {
        Map<String, String> params;
        try {
            params = checked(templateKey, discriminator, rawParams);
        } catch (ValidationRefusal refused) {
            return Mono.error(refused);
        }
        if (userId == null || userId.isBlank() || userId.length() > 100) {
            return Mono.error(violation("userId", "must be an account id"));
        }
        return reach(userId)
                .flatMap(reach -> dispatch(templateKey, discriminator, userId, params)
                        .map(started -> started
                                ? new Receipt(QUEUED, reach.channel().name(), reach.destination(), 1)
                                : new Receipt(DUPLICATE, null, null, 0)))
                .defaultIfEmpty(new Receipt(NO_VERIFIED_CONTACT, null, null, 0));
    }

    public Mono<BatchReceipt> notifyUsers(String templateKey, String discriminator, Collection<String> userIds,
                                          Map<String, ?> rawParams) {
        Map<String, String> params;
        Set<String> ids = new LinkedHashSet<>();
        try {
            params = checked(templateKey, discriminator, rawParams);
            if (userIds == null || userIds.isEmpty()) {
                throw violation("userIds", "must name at least one account");
            }
            if (userIds.size() > MAX_BATCH) {
                throw violation("userIds", "must name at most " + MAX_BATCH + " accounts");
            }
            for (String id : userIds) {
                if (id == null || id.isBlank() || id.length() > 100) {
                    throw violation("userIds", "must be account ids");
                }
                ids.add(id);
            }
        } catch (ValidationRefusal refused) {
            return Mono.error(refused);
        }
        // Outcomes are only counted, so which account fell into which is never returned.
        return Flux.fromIterable(ids)
                .concatMap(id -> notifyUser(templateKey, discriminator, id, params)
                        .map(Receipt::status))
                .collectList()
                .map(UserNotifier::tally);
    }

    private static BatchReceipt tally(List<String> outcomes) {
        int queued = (int) outcomes.stream().filter(QUEUED::equals).count();
        int duplicate = (int) outcomes.stream().filter(DUPLICATE::equals).count();
        int none = outcomes.size() - queued - duplicate;
        String status = queued > 0 ? QUEUED : duplicate > 0 ? DUPLICATE : NO_VERIFIED_CONTACT;
        return new BatchReceipt(status, null, null, queued + duplicate, queued, duplicate, none);
    }

    /**
     * True when this call began the message; false when the same message had already been requested.
     *
     * <p>The message is rendered here and stored as a pending notification before the workflow starts, so
     * the workflow's request carries only ids: a name, an event title or a free-text note from the caller
     * never enters the workflow history. The row is the deduplication record too: the insert of an id
     * that exists is the second request. If the workflow cannot be started the row is removed again, so a
     * retry begins afresh.
     */
    private Mono<Boolean> dispatch(String templateKey, String discriminator, String userId, Map<String, String> params) {
        String key = NotificationRules.key(templateKey, discriminator + ":" + userId);
        String id = NotificationRules.notificationId(key);
        NotificationRules.Message message = NotificationRules.render(templateKey, params);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deduplicationKey", key);
        data.put("templateKey", templateKey);
        Notification pending = Notification.builder()
                .id(id)
                .userId(userId)
                .type(message.type())
                .title(message.title())
                .body(message.body())
                .data(data)
                .channels(NotificationRules.CHAIN)
                .status(NotificationStatus.PENDING)
                .createdAt(clock.instant())
                .build();
        return notifications.insert(pending)
                .flatMap(stored -> Mono.fromCallable(() -> process.start(new Request(key, templateKey, userId, null, null)))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(failed -> notifications.deleteById(id).then(Mono.<Boolean>error(failed))))
                .onErrorResume(DuplicateKeyException.class, again -> Mono.just(false));
    }

    /** The first channel the account would be tried on and its masked destination; empty when it cannot be reached. */
    private Mono<Reach> reach(String userId) {
        return users.findById(userId)
                .filter(UserNotifier::eligible)
                .flatMap(user -> contacts.findByAccountIdAndReleasedAtIsNull(userId)
                        .filter(contact -> contact.getVerifiedAt() != null)
                        .collectList()
                        .flatMap(held -> Mono.justOrEmpty(firstReach(user, held))));
    }

    private static java.util.Optional<Reach> firstReach(User user, List<Contact> held) {
        Map<ContactType, String> masked = new LinkedHashMap<>();
        // The primary contact wins when an account holds two of one type.
        held.stream().sorted((a, b) -> Boolean.compare(b.isPrimary(), a.isPrimary()))
                .forEach(contact -> masked.putIfAbsent(contact.getType(), contact.getValueMasked()));
        // An account that predates contacts may still carry its number or address.
        if (!masked.containsKey(ContactType.WHATSAPP) && user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank()) {
            masked.put(ContactType.WHATSAPP, ContactMasking.maskPhone(user.getPhoneNumber()));
        }
        if (!masked.containsKey(ContactType.EMAIL) && user.getEmail() != null && !user.getEmail().isBlank()) {
            masked.put(ContactType.EMAIL, ContactMasking.maskEmail(user.getEmail()));
        }
        if (masked.isEmpty()) {
            return java.util.Optional.empty();
        }
        NotificationChannel channel = NotificationRules.chainFor(user.getPreferredChannel(), masked.keySet()).get(0);
        ContactType type = NotificationRules.contactTypeOf(channel);
        String destination = type == null ? null : masked.get(type);
        return destination == null ? java.util.Optional.empty() : java.util.Optional.of(new Reach(channel, destination));
    }

    /** Whether an account may be messaged: active, not locked and not suspended, merged or deleted. */
    public static boolean eligible(User user) {
        return user.isActive() && !user.isLocked() && AccountStates.of(user) == AccountState.ACTIVE;
    }

    private static Map<String, String> checked(String templateKey, String discriminator, Map<String, ?> raw) {
        List<FieldViolation> violations = new ArrayList<>();
        if (templateKey == null || templateKey.isBlank() || templateKey.length() > 64) {
            violations.add(new FieldViolation("templateKey", "must be a template key"));
        } else if (!NotificationRules.registered(templateKey)) {
            // The key is the caller's own, so naming it back reveals nothing; no list of templates is given.
            violations.add(new FieldViolation("templateKey", "is not a known template"));
        }
        if (discriminator == null || discriminator.isBlank() || discriminator.length() > 200) {
            violations.add(new FieldViolation("discriminator", "must be 1 to 200 characters"));
        }
        Map<String, String> params = new LinkedHashMap<>();
        if (raw != null) {
            if (raw.size() > MAX_PARAMS) {
                violations.add(new FieldViolation("params", "must have at most " + MAX_PARAMS + " entries"));
            } else {
                raw.forEach((key, value) -> {
                    if (key == null || key.length() > MAX_KEY || !KEY.matcher(key).matches()) {
                        violations.add(new FieldViolation("params", "keys must be 1 to " + MAX_KEY + " letters or digits"));
                    } else if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
                        String text = value.toString();
                        if (text.length() > MAX_VALUE) {
                            violations.add(new FieldViolation("params." + key, "must be at most " + MAX_VALUE + " characters"));
                        } else {
                            // Control characters have no place in a message; they are replaced, not rejected.
                            params.put(key, text.replaceAll("\\p{Cntrl}", " ").trim());
                        }
                    } else if (value != null) {
                        violations.add(new FieldViolation("params." + key, "must be text"));
                    }
                });
            }
        }
        if (!violations.isEmpty()) {
            throw new ValidationRefusal(violations);
        }
        return params.isEmpty() ? null : params;
    }

    private static ValidationRefusal violation(String path, String constraint) {
        return new ValidationRefusal(List.of(new FieldViolation(path, constraint)));
    }
}
