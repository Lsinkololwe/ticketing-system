package com.pml.catalog.dto;

import com.pml.shared.constants.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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
    private LocalDateTime createdAt;
    private LocalDateTime lastStatusChange;
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
        private LocalDateTime transitionedAt;
        private String transitionedBy;
        private String reason;
        private Object metadata;
    }
}
