package com.pml.catalog.web.graphql.dto.stats;

import com.pml.shared.constants.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistics for events grouped by status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventStatusStats {
    private EventStatus status;
    private int count;
    private double percentage;
}
