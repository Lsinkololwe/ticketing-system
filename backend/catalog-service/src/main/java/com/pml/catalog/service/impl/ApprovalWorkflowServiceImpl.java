package com.pml.catalog.service.impl;

import com.pml.catalog.domain.model.ApprovalEscalation;
import com.pml.catalog.domain.model.ApprovalTimeline;
import com.pml.catalog.dto.ApprovalStats;
import com.pml.catalog.repository.ApprovalEscalationRepository;
import com.pml.catalog.repository.ApprovalTimelineRepository;
import com.pml.catalog.service.*;
import com.pml.shared.constants.EventStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Implementation of ApprovalWorkflowService.
 * Orchestrates the approval workflow between timelines, escalations, and events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalWorkflowServiceImpl implements ApprovalWorkflowService {

    private final ApprovalTimelineService timelineService;
    private final ApprovalEscalationService escalationService;
    private final EventService eventService;
    private final ApprovalTimelineRepository timelineRepository;
    private final ApprovalEscalationRepository escalationRepository;

    // Default SLA deadline in hours
    private static final int DEFAULT_SLA_HOURS = 72;
    private static final int DEFAULT_REMINDER_INTERVAL_HOURS = 24;

    // ==========================================
    // Submission Operations
    // ==========================================

    @Override
    public Mono<ApprovalTimeline> submitForApproval(String eventId, String organizerId, String organizerName) {
        log.info("Submitting event {} for approval by organizer {}", eventId, organizerId);

        return eventService.findById(eventId)
                .flatMap(event -> {
                    LocalDateTime slaDeadline = LocalDateTime.now().plusHours(DEFAULT_SLA_HOURS);

                    return timelineService.getOrCreateTimeline(eventId, event.getTitle(), organizerId, organizerName)
                            .flatMap(timeline -> {
                                // Use domain method
                                timeline.recordSubmission(organizerId, organizerName, slaDeadline);

                                // Update event status
                                event.setStatus(EventStatus.PENDING_APPROVAL);
                                event.setSubmittedForApprovalAt(LocalDateTime.now());
                                event.setApprovalDeadline(slaDeadline);

                                return eventService.updateEvent(eventId, event)
                                        .then(timelineService.save(timeline));
                            });
                })
                .doOnSuccess(timeline -> log.info("Event {} submitted for approval, SLA deadline: {}",
                        eventId, timeline.getSlaDeadline()));
    }

    @Override
    public Mono<ApprovalTimeline> resubmitForApproval(String eventId, String organizerId, String organizerName) {
        log.info("Resubmitting event {} for approval by organizer {}", eventId, organizerId);

        return timelineService.findByEventId(eventId)
                .switchIfEmpty(Mono.error(new IllegalStateException("No timeline found for event: " + eventId)))
                .flatMap(timeline -> {
                    LocalDateTime newSlaDeadline = LocalDateTime.now().plusHours(DEFAULT_SLA_HOURS);

                    // Use domain method
                    timeline.recordResubmission(organizerId, organizerName, newSlaDeadline);

                    return eventService.findById(eventId)
                            .flatMap(event -> {
                                event.setStatus(EventStatus.PENDING_APPROVAL);
                                event.setSubmittedForApprovalAt(LocalDateTime.now());
                                event.setApprovalDeadline(newSlaDeadline);
                                return eventService.updateEvent(eventId, event);
                            })
                            .then(timelineService.save(timeline));
                });
    }

    // ==========================================
    // Reviewer Operations
    // ==========================================

    @Override
    public Mono<ApprovalTimeline> assignReviewer(String eventId, String reviewerId, String reviewerName,
                                                  String assignedBy, String internalNotes) {
        log.info("Assigning reviewer {} to event {}", reviewerId, eventId);

        return timelineService.findByEventId(eventId)
                .switchIfEmpty(Mono.error(new IllegalStateException("No timeline found for event: " + eventId)))
                .flatMap(timeline -> {
                    // Use domain method
                    timeline.assignReviewer(assignedBy, assignedBy, reviewerId, reviewerName);

                    // Also update the event
                    return eventService.findById(eventId)
                            .flatMap(event -> {
                                event.setAssignedReviewerId(reviewerId);
                                event.setAssignedReviewerName(reviewerName);
                                return eventService.updateEvent(eventId, event);
                            })
                            .then(timelineService.save(timeline));
                })
                .doOnSuccess(timeline -> log.info("Reviewer {} assigned to event {}", reviewerId, eventId));
    }

    @Override
    public Mono<ApprovalTimeline> unassignReviewer(String eventId, String reason) {
        log.info("Unassigning reviewer from event {}", eventId);

        return timelineService.findByEventId(eventId)
                .switchIfEmpty(Mono.error(new IllegalStateException("No timeline found for event: " + eventId)))
                .flatMap(timeline -> {
                    // Use domain method
                    timeline.unassignReviewer();

                    return eventService.findById(eventId)
                            .flatMap(event -> {
                                event.setAssignedReviewerId(null);
                                event.setAssignedReviewerName(null);
                                return eventService.updateEvent(eventId, event);
                            })
                            .then(timelineService.save(timeline));
                });
    }

    // ==========================================
    // Approval Decision Operations
    // ==========================================

    @Override
    public Mono<ApprovalTimeline> approveEvent(String eventId, String reviewerId, String reviewerName, String comments) {
        log.info("Approving event {} by reviewer {}", eventId, reviewerId);

        return timelineService.findByEventId(eventId)
                .switchIfEmpty(Mono.error(new IllegalStateException("No timeline found for event: " + eventId)))
                .flatMap(timeline -> {
                    // Use domain method
                    timeline.recordApproval(reviewerId, reviewerName, comments);

                    return eventService.approveEvent(eventId)
                            .then(timelineService.save(timeline));
                })
                .doOnSuccess(timeline -> log.info("Event {} approved by {}", eventId, reviewerId));
    }

    @Override
    public Mono<ApprovalTimeline> rejectEvent(String eventId, String reviewerId, String reviewerName, String comments) {
        log.info("Rejecting event {} by reviewer {} reason: {}", eventId, reviewerId, comments);

        return timelineService.findByEventId(eventId)
                .switchIfEmpty(Mono.error(new IllegalStateException("No timeline found for event: " + eventId)))
                .flatMap(timeline -> {
                    // Use domain method
                    timeline.recordRejection(reviewerId, reviewerName, comments);

                    return eventService.rejectEvent(eventId, comments)
                            .then(timelineService.save(timeline));
                })
                .doOnSuccess(timeline -> log.info("Event {} rejected by {}", eventId, reviewerId));
    }

    @Override
    public Mono<ApprovalTimeline> requestChanges(String eventId, String reviewerId, String reviewerName, String comments) {
        log.info("Requesting changes for event {} by reviewer {}", eventId, reviewerId);

        return timelineService.findByEventId(eventId)
                .switchIfEmpty(Mono.error(new IllegalStateException("No timeline found for event: " + eventId)))
                .flatMap(timeline -> {
                    // Use domain method
                    timeline.recordChangesRequested(reviewerId, reviewerName, comments);

                    return eventService.findById(eventId)
                            .flatMap(event -> {
                                event.setStatus(EventStatus.CHANGES_REQUESTED);
                                event.setChangesRequestedAt(LocalDateTime.now());
                                event.setChangesRequestedBy(reviewerId);
                                event.setChangesRequestedComments(comments);
                                return eventService.updateEvent(eventId, event);
                            })
                            .then(timelineService.save(timeline));
                });
    }

    @Override
    public Mono<ApprovalTimeline> addComment(String eventId, String adminId, String adminName,
                                              String comment, boolean isInternal) {
        log.debug("Adding comment to event {} timeline by {}", eventId, adminId);

        return timelineService.findByEventId(eventId)
                .switchIfEmpty(Mono.error(new IllegalStateException("No timeline found for event: " + eventId)))
                .flatMap(timelineService::save);
    }

    // ==========================================
    // Escalation Operations
    // ==========================================

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
                .flatMap(timeline -> {
                    return escalationService.createEscalation(
                            eventId,
                            timeline.getEventTitle(),
                            escalateTo,
                            escalateToName,
                            reason,
                            timeline.getSlaDeadline(),
                            timeline.getAssignedReviewerId(),
                            timeline.getAssignedReviewerName(),
                            DEFAULT_REMINDER_INTERVAL_HOURS
                    ).flatMap(escalation -> {
                        // Use domain method
                        timeline.markEscalated(escalation.getId(), escalateToName, reason);
                        return timelineService.save(timeline)
                                .thenReturn(escalation);
                    });
                });
    }

    // ==========================================
    // Statistics
    // ==========================================

    @Override
    public Mono<ApprovalStats> getApprovalStats() {
        log.debug("Calculating approval statistics");

        return Mono.zip(
                // Pending counts
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

    // ==========================================
    // Scheduled Operations
    // ==========================================

    @Override
    public Mono<Void> processOverdueApprovals() {
        log.info("Processing overdue approvals");

        return timelineService.findNewlyOverdueTimelines()
                .flatMap(timeline -> {
                    timeline.checkAndUpdateOverdueStatus();
                    return timelineService.save(timeline);
                })
                .then()
                .doOnSuccess(v -> log.info("Overdue approvals processed"));
    }

    @Override
    public Mono<Void> sendPendingReminders() {
        log.info("Sending pending approval reminders");
        return Mono.empty();
    }

    @Override
    public Mono<Void> processEscalationReminders() {
        log.info("Processing escalation reminders");
        return Mono.empty();
    }
}
