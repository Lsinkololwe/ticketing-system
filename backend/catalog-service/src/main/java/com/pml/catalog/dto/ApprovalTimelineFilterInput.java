package com.pml.catalog.dto;

import com.pml.shared.constants.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Filter input for approval timeline queries.
 * Matches the GraphQL schema ApprovalTimelineFilterInput type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalTimelineFilterInput {

    /**
     * Filter by event status
     */
    private EventStatus status;

    /**
     * Filter by overdue status
     */
    private Boolean isOverdue;

    /**
     * Filter by active escalation
     */
    private Boolean hasActiveEscalation;

    /**
     * Filter by assigned reviewer
     */
    private String assignedReviewerId;

    /**
     * Filter by submission date (after)
     */
    private LocalDateTime submittedAfter;

    /**
     * Filter by submission date (before)
     */
    private LocalDateTime submittedBefore;

    /**
     * Filter by organizer
     */
    private String organizerId;

    /**
     * Search by event title
     */
    private String searchQuery;
}
