package com.pml.catalog.web.graphql.dto;

import com.pml.shared.constants.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Filter input for Event admin queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventFilterInput {

    private String categoryId;
    private EventStatus status;
    /** Any of these statuses; combined with {@code status}, an event must satisfy both. */
    private java.util.List<EventStatus> statuses;
    /** Words from the title or description, 3 to 100 characters. */
    private String searchQuery;
    private String organizerId;
    private Boolean published;
    private String cityId;
    private String country;
    private Instant eventDateAfter;
    private Instant eventDateBefore;
    private Instant createdAfter;
    private Instant createdBefore;
    private Boolean approvedNotPublished;
    private Boolean overdue;
    private Integer daysSinceApprovalMin;
    private Integer daysSinceApprovalMax;
}
