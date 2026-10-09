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
 * Who holds {@code FINANCE_LEAD}, and a WhatsApp notification to each of them when booking
 * escalates a chargeback or a refund. Booking emails the same people from {@link #contacts()}.
 *
 * <p>Unlike {@link NotificationProcess#request}, a failure to start a notification is returned: the caller
 * is an escalation activity that retries, and each lead's message is deduplicated on the escalation.
 */
@Slf4j
@Service
public class FinanceLeadNotifier {

    private final UserRepository users;
    private final NotificationProcess notifications;

    public FinanceLeadNotifier(UserRepository users, NotificationProcess notifications) {
        this.users = users;
        this.notifications = notifications;
    }

    public record Contact(String userId, String email) {
    }

    /** Active holders of {@code FINANCE_LEAD} who have an email address. */
    public Flux<Contact> contacts() {
        return leads()
                .filter(user -> user.getEmail() != null && !user.getEmail().isBlank())
                .map(user -> new Contact(user.getId(), user.getEmail()));
    }

    /** Requests one notification per active lead and answers how many leads were addressed. */
    public Mono<Integer> notifyLeads(String templateKey, String discriminator, String subjectId) {
        if (!NotificationRules.isFinanceEscalation(templateKey)) {
            return Mono.error(new IllegalArgumentException("not a finance escalation template: " + templateKey));
        }
        return leads()
                .concatMap(user -> Mono.fromRunnable(() -> notifications.startNow(new Request(
                                NotificationRules.key(templateKey, discriminator + ":" + user.getId()),
                                templateKey, user.getId(), NotificationRules.FINANCE_ESCALATION, subjectId)))
                        .subscribeOn(Schedulers.boundedElastic())
                        .thenReturn(user.getId()))
                .count()
                .map(Long::intValue)
                .doOnNext(addressed -> {
                    if (addressed == 0) {
                        log.warn("No active FINANCE_LEAD holder to notify of {} for {}", templateKey, subjectId);
                    }
                });
    }

    private Flux<User> leads() {
        return users.findByRole(UserType.FINANCE_LEAD).filter(User::isActive);
    }
}
