package com.pml.catalog.dto;

import com.pml.shared.constants.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private String organizerId;
    private Boolean published;
    private String cityId;
    private String country;
    private LocalDateTime eventDateAfter;
    private LocalDateTime eventDateBefore;
    private LocalDateTime createdAfter;
    private LocalDateTime createdBefore;
    private Boolean approvedNotPublished;
    private Boolean overdue;
    private Integer daysSinceApprovalMin;
    private Integer daysSinceApprovalMax;
}
