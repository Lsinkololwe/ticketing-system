package com.pml.identity.service;

import com.pml.identity.domain.model.User;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.workflow.notify.NotificationProcess;
import com.pml.identity.workflow.notify.NotificationRules;
import com.pml.identity.workflow.notify.NotificationWorkflow.Request;
import com.pml.shared.constants.UserType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Tells people about an event's review: every active platform administrator when an event joins the
 * approvals queue, and the event's organizer when a reviewer decides.
 *
 * <p>Each recipient's message is deduplicated on the template, the caller's discriminator and the
 * recipient, so a caller that retries sends nothing twice. A failure to start a message is returned
 * to the caller, which is an activity that retries.
 */
@Slf4j
@Service
public class ApprovalNotifier {

    private final UserRepository users;
    private final NotificationProcess notifications;

    public ApprovalNotifier(UserRepository users, NotificationProcess notifications) {
        this.users = users;
        this.notifications = notifications;
    }

    /**
     * Requests the messages and answers how many people were addressed.
     *
     * @param templateKey   {@link NotificationRules#EVENT_PENDING} or one of {@link NotificationRules#EVENT_DECISIONS}
     * @param discriminator what makes this occurrence one message, such as the event and its submission number
     * @param eventId       the event reviewed
     * @param organizerId   who is told of a decision; ignored for {@link NotificationRules#EVENT_PENDING}
     */
    public Mono<Integer> notify(String templateKey, String discriminator, String eventId, String organizerId) {
        if (!NotificationRules.isEventReview(templateKey)) {
            return Mono.error(new IllegalArgumentException("not an event review template: " + templateKey));
        }
        Flux<User> recipients = NotificationRules.EVENT_PENDING.equals(templateKey)
                ? users.findByRole(UserType.ADMIN).filter(User::isActive)
                : organizer(organizerId);
        return recipients
                .concatMap(user -> Mono.fromRunnable(() -> notifications.startNow(new Request(
                                NotificationRules.key(templateKey, discriminator + ":" + user.getId()),
                                templateKey, user.getId(), NotificationRules.EVENT_REVIEW, eventId)))
                        .subscribeOn(Schedulers.boundedElastic())
                        .thenReturn(user.getId()))
                .count()
                .map(Long::intValue)
                .doOnNext(addressed -> {
                    if (addressed == 0) {
                        log.warn("Nobody to notify of {} for event {}", templateKey, eventId);
                    }
                });
    }

    private Flux<User> organizer(String organizerId) {
        if (organizerId == null || organizerId.isBlank()) {
            return Flux.empty();
        }
        return users.findById(organizerId).filter(User::isActive).flux();
    }
}
