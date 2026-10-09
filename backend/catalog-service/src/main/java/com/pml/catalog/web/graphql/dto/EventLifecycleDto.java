package com.pml.catalog.web.graphql.dto;

import com.pml.shared.constants.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * DTO for event lifecycle information.
 * Provides audit trail and state machine data for event workflow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventLifecycleDto {

    private String eventId;
    private EventStatus currentStatus;
    private Instant createdAt;
    private Instant lastStatusChange;
    private String createdBy;
    private List<StatusTransitionDto> statusTransitions;
    private List<EventStatus> allowedTransitions;

    /**
     * DTO for a single status transition in the lifecycle.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusTransitionDto {
        private EventStatus fromStatus;
        private EventStatus toStatus;
        private Instant transitionedAt;
        private String transitionedBy;
        private String reason;
        private Object metadata;
    }
}
