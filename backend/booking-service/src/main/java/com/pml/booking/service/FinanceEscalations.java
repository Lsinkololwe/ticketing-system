package com.pml.booking.service;

import com.pml.booking.domain.enums.AlertPriority;
import com.pml.booking.infrastructure.client.IdentityServiceClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A chargeback or refund escalation reaches the finance lead by email and WhatsApp, and the
 * shared finance channel gets a copy.
 *
 * <p>Called from escalation activities, so any failure is returned and the activity retries. The copy and
 * the emails may then go out again; identity deduplicates the WhatsApp message on the discriminator.
 */
@Service
public class FinanceEscalations {

    private final NotificationService notifications;
    private final IdentityServiceClient identity;

    public FinanceEscalations(NotificationService notifications, IdentityServiceClient identity) {
        this.notifications = notifications;
        this.identity = identity;
    }

    /**
     * @param templateKey   identity's WhatsApp template for this kind of escalation
     * @param discriminator what makes this escalation one occurrence, such as a refund and its level
     * @param subjectId     the chargeback record or refund request escalated
     */
    public record Escalation(AlertPriority priority, String subject, String message,
                             String templateKey, String discriminator, String subjectId) {
    }

    public Mono<Void> escalate(Escalation escalation) {
        return notifications.sendAdminAlert(escalation.priority(), escalation.subject(), escalation.message())
                .then(Flux.defer(identity::financeLeadContacts)
                        .concatMap(lead -> notifications.sendEmail(lead.email(), escalation.subject(), escalation.message()))
                        .then())
                .then(Mono.defer(() -> identity.notifyFinanceLeads(
                        escalation.templateKey(), escalation.discriminator(), escalation.subjectId())));
    }
}
