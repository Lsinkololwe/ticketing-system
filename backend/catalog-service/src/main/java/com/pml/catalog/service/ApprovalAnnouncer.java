package com.pml.catalog.service;

import com.pml.catalog.domain.model.Event;
import com.pml.catalog.infrastructure.client.IdentityServiceClient;
import com.pml.catalog.workflow.approval.ApprovalRules;
import com.pml.shared.constants.EventStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Hands an event review's messages to identity, which owns people and their contact details:
 * administrators when an event joins the approvals queue, the organizer when a reviewer decides.
 */
@Slf4j
@Service
public class ApprovalAnnouncer {

    private final ReactiveMongoTemplate template;
    private final IdentityServiceClient identity;

    public ApprovalAnnouncer(ReactiveMongoTemplate template, IdentityServiceClient identity) {
        this.template = template;
        this.identity = identity;
    }

    /** Completes once identity has accepted the request; a status nobody is told about completes at once. */
    public Mono<Void> announce(String eventId, EventStatus reached) {
        String templateKey = ApprovalRules.announcementTemplate(reached);
        if (templateKey == null) {
            return Mono.empty();
        }
        return template.findById(eventId, Event.class)
                .flatMap(event -> identity.notifyApproval(
                        templateKey,
                        ApprovalRules.announcementKey(eventId, event.getSubmissionCount(), reached),
                        eventId,
                        event.getOrganizerId()))
                .switchIfEmpty(Mono.fromRunnable(() -> log.warn("Event {} is gone; its {} announcement is dropped", eventId, reached)));
    }
}
