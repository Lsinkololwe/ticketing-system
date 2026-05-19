package com.pml.catalog.service.impl;

import com.pml.catalog.domain.model.ApprovalTimeline;
import com.pml.catalog.domain.valueobject.TimelineEvent;
import com.pml.catalog.dto.EventLifecycleDto;
import com.pml.catalog.repository.ApprovalTimelineRepository;
import com.pml.catalog.repository.EventRepository;
import com.pml.catalog.service.EventLifecycleService;
import com.pml.shared.constants.EventStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of EventLifecycleService.
 * Provides event lifecycle tracking and state machine logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventLifecycleServiceImpl implements EventLifecycleService {

    private final EventRepository eventRepository;
    private final ApprovalTimelineRepository approvalTimelineRepository;

    /**
     * State machine definition for event status transitions.
     * Maps each status to its valid next statuses.
     */
    private static final Map<EventStatus, List<EventStatus>> STATE_MACHINE = Map.of(
            // DRAFT can be submitted for review or deleted
            EventStatus.DRAFT, List.of(EventStatus.PENDING_APPROVAL),

            // PENDING_REVIEW can be approved, rejected, or have changes requested
            EventStatus.PENDING_APPROVAL, List.of(
                    EventStatus.APPROVED,
                    EventStatus.REJECTED,
                    EventStatus.CHANGES_REQUESTED
            ),

            // CHANGES_REQUESTED can be resubmitted
            EventStatus.CHANGES_REQUESTED, List.of(EventStatus.PENDING_APPROVAL),

            // APPROVED can be published
            EventStatus.APPROVED, List.of(EventStatus.PUBLISHED),

            // PUBLISHED can be cancelled or completed
            EventStatus.PUBLISHED, List.of(
                    EventStatus.CANCELLED,
                    EventStatus.COMPLETED
            ),

            // CANCELLED is terminal (no further transitions)
            EventStatus.CANCELLED, List.of(),

            // COMPLETED is terminal (no further transitions)
            EventStatus.COMPLETED, List.of(),

            // REJECTED is terminal (no further transitions)
            EventStatus.REJECTED, List.of()
    );

    @Override
    public Mono<EventLifecycleDto> getEventLifecycle(String eventId) {
        log.debug("Getting lifecycle for event: {}", eventId);

        return eventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event not found: " + eventId)))
                .flatMap(event -> {
                    // Try to get approval timeline for status history
                    return approvalTimelineRepository.findByEventId(eventId)
                            .map(timeline -> buildLifecycleWithTimeline(event.getId(), event.getStatus(),
                                    event.getCreatedAt(), event.getCreatedBy(), timeline))
                            .switchIfEmpty(Mono.just(buildLifecycleWithoutTimeline(event.getId(), event.getStatus(),
                                    event.getCreatedAt(), event.getCreatedBy(), event.getUpdatedAt())));
                });
    }

    @Override
    public Mono<List<EventStatus>> getAllowedStatusTransitions(String eventId) {
        log.debug("Getting allowed transitions for event: {}", eventId);

        return eventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event not found: " + eventId)))
                .map(event -> getAllowedTransitionsForStatus(event.getStatus()));
    }

    @Override
    public List<EventStatus> getAllowedTransitionsForStatus(EventStatus currentStatus) {
        if (currentStatus == null) {
            return List.of(EventStatus.DRAFT);
        }
        return STATE_MACHINE.getOrDefault(currentStatus, List.of());
    }

    /**
     * Build lifecycle DTO with approval timeline history.
     */
    private EventLifecycleDto buildLifecycleWithTimeline(
            String eventId,
            EventStatus currentStatus,
            java.time.LocalDateTime createdAt,
            String createdBy,
            ApprovalTimeline timeline) {

        List<EventLifecycleDto.StatusTransitionDto> transitions = new ArrayList<>();

        // Add creation as first transition
        transitions.add(EventLifecycleDto.StatusTransitionDto.builder()
                .fromStatus(null)
                .toStatus(EventStatus.DRAFT)
                .transitionedAt(createdAt)
                .transitionedBy(createdBy)
                .reason("Event created")
                .build());

        // Convert timeline events to status transitions
        if (timeline.getTimelineEvents() != null) {
            EventStatus previousStatus = EventStatus.DRAFT;
            for (TimelineEvent event : timeline.getTimelineEvents()) {
                if (event.getNewStatus() != null && !event.getNewStatus().equals(previousStatus)) {
                    transitions.add(EventLifecycleDto.StatusTransitionDto.builder()
                            .fromStatus(previousStatus)
                            .toStatus(event.getNewStatus())
                            .transitionedAt(event.getTimestamp())
                            .transitionedBy(event.getActorId())
                            .reason(event.getDescription())
                            .metadata(Map.of(
                                    "action", event.getAction().name(),
                                    "actorName", event.getActorName() != null ? event.getActorName() : ""
                            ))
                            .build());
                    previousStatus = event.getNewStatus();
                }
            }
        }

        // Find last status change timestamp
        java.time.LocalDateTime lastStatusChange = transitions.isEmpty() ? createdAt :
                transitions.get(transitions.size() - 1).getTransitionedAt();

        return EventLifecycleDto.builder()
                .eventId(eventId)
                .currentStatus(currentStatus)
                .createdAt(createdAt)
                .lastStatusChange(lastStatusChange)
                .createdBy(createdBy)
                .statusTransitions(transitions)
                .allowedTransitions(getAllowedTransitionsForStatus(currentStatus))
                .build();
    }

    /**
     * Build lifecycle DTO without approval timeline (basic info only).
     */
    private EventLifecycleDto buildLifecycleWithoutTimeline(
            String eventId,
            EventStatus currentStatus,
            java.time.LocalDateTime createdAt,
            String createdBy,
            java.time.LocalDateTime updatedAt) {

        List<EventLifecycleDto.StatusTransitionDto> transitions = new ArrayList<>();

        // Add creation transition
        transitions.add(EventLifecycleDto.StatusTransitionDto.builder()
                .fromStatus(null)
                .toStatus(EventStatus.DRAFT)
                .transitionedAt(createdAt)
                .transitionedBy(createdBy)
                .reason("Event created")
                .build());

        // If current status is not DRAFT, add a transition (we don't have history details)
        if (currentStatus != EventStatus.DRAFT) {
            transitions.add(EventLifecycleDto.StatusTransitionDto.builder()
                    .fromStatus(EventStatus.DRAFT)
                    .toStatus(currentStatus)
                    .transitionedAt(updatedAt != null ? updatedAt : createdAt)
                    .transitionedBy(null)
                    .reason("Status changed (history not available)")
                    .build());
        }

        return EventLifecycleDto.builder()
                .eventId(eventId)
                .currentStatus(currentStatus)
                .createdAt(createdAt)
                .lastStatusChange(updatedAt != null ? updatedAt : createdAt)
                .createdBy(createdBy)
                .statusTransitions(transitions)
                .allowedTransitions(getAllowedTransitionsForStatus(currentStatus))
                .build();
    }
}
