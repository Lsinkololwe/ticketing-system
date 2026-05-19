package com.pml.booking.web.graphql.dto.stats;

import com.pml.shared.constants.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistics for tickets grouped by status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketStatusStats {
    private TicketStatus status;
    private int count;
    private double percentage;
}
