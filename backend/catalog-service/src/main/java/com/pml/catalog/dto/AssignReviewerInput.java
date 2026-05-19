package com.pml.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input for assigning a reviewer to an event.
 * Matches the GraphQL schema AssignReviewerInput type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignReviewerInput {

    /**
     * Event ID to assign reviewer to
     */
    private String eventId;

    /**
     * Reviewer's user ID
     */
    private String reviewerId;

    /**
     * Reviewer's display name
     */
    private String reviewerName;

    /**
     * Optional internal notes about the assignment
     */
    private String internalNotes;
}
