package com.pml.catalog.service.impl;

import com.pml.catalog.domain.model.ApprovalEscalation;
import com.pml.catalog.domain.model.ApprovalTimeline;
import com.pml.catalog.domain.valueobject.TimelineEvent;
import com.pml.catalog.web.graphql.dto.ApprovalStats;
import com.pml.catalog.repository.ApprovalEscalationRepository;
import com.pml.catalog.repository.ApprovalTimelineRepository;
import com.pml.catalog.service.ApprovalEscalationService;
import com.pml.catalog.service.ApprovalTimelineService;
import com.pml.catalog.service.ApprovalWorkflowService;
import com.pml.shared.constants.EventStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Operator actions around a review that change no review state.
 *
 * <p>Submission, claims, decisions, the SLA clock and the three automatic escalation levels are
 * {@code EventApprovalWorkflow}'s; nothing here moves an event's status or starts a timer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalWorkflowServiceImpl implements ApprovalWorkflowService {

    private final ApprovalTimelineService timelineService;

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;
    private final ApprovalEscalationService escalationService;
    private final ApprovalTimelineRepository timelineRepository;
    private final ApprovalEscalationRepository escalationRepository;

    private static final int DEFAULT_REMINDER_INTERVAL_HOURS = 24;

    @Override
    public Mono<ApprovalTimeline> addComment(String eventId, String adminId, String adminName,
                                             String comment, boolean isInternal) {
        log.debug("Adding comment to event {} timeline by {}", eventId, adminId);

        return timelineService.findByEventId(eventId)
                .switchIfEmpty(Mono.error(new IllegalStateException("No timeline found for event: " + eventId)))
                .flatMap(timeline -> {
                    timeline.addTimelineEvent(TimelineEvent.comment(eventId, adminId, adminName, comment, isInternal,
                            clock.instant()));
                    return timelineService.save(timeline);
                });
    }

    @Override
    public Mono<ApprovalEscalation> acknowledgeEscalation(String escalationId, String adminId,
                                                          String adminName, String notes) {
        log.info("Acknowledging escalation {} by admin {}", escalationId, adminId);
        return escalationService.acknowledge(escalationId, adminId, adminName, notes);
    }

    @Override
    public Mono<ApprovalEscalation> resolveEscalation(String escalationId, String adminId,
                                                      String adminName, String resolutionNotes) {
        log.info("Resolving escalation {} by admin {}", escalationId, adminId);
        return escalationService.resolve(escalationId, adminId, adminName, resolutionNotes);
    }

    @Override
    public Mono<ApprovalEscalation> triggerManualEscalation(String eventId, String reason,
                                                            String escalateTo, String escalateToName) {
        log.info("Triggering manual escalation for event {} to {}", eventId, escalateTo);

        return timelineService.findByEventId(eventId)
                .switchIfEmpty(Mono.error(new IllegalStateException("No timeline found for event: " + eventId)))
                .flatMap(timeline -> escalationService.createEscalation(
                                eventId,
                                timeline.getEventTitle(),
                                escalateTo,
                                escalateToName,
                                reason,
                                timeline.getSlaDeadline() != null ? timeline.getSlaDeadline() : clock.instant(),
                                timeline.getAssignedReviewerId(),
                                timeline.getAssignedReviewerName(),
                                DEFAULT_REMINDER_INTERVAL_HOURS)
                        .flatMap(escalation -> {
                            timeline.markEscalated(escalation.getId(), escalateToName, reason, clock.instant());
                            return timelineService.save(timeline).thenReturn(escalation);
                        }));
    }

    @Override
    public Mono<ApprovalStats> getApprovalStats() {
        log.debug("Calculating approval statistics");

        return Mono.zip(
                timelineRepository.countByCurrentStatus(EventStatus.PENDING_APPROVAL).defaultIfEmpty(0L),
                timelineRepository.countOverdue().defaultIfEmpty(0L),
                escalationRepository.countActive().defaultIfEmpty(0L)
        ).map(tuple -> ApprovalStats.builder()
                .totalPendingReviews(tuple.getT1().intValue())
                .totalOverdue(tuple.getT2().intValue())
                .totalEscalated(tuple.getT3().intValue())
                .submittedToday(0)
                .approvedToday(0)
                .rejectedToday(0)
                .changesRequestedToday(0)
                .activeEscalations(tuple.getT3().intValue())
                .escalationsThisWeek(0)
                .slaComplianceRate(95.0)
                .averageProcessingTimeHours(24.0)
                .build());
    }
}
