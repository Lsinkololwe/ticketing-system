package com.pml.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Approval workflow statistics for admin dashboard.
 * Matches the GraphQL schema ApprovalStats type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalStats {

    // Volume metrics
    private int totalPendingReviews;
    private int totalOverdue;
    private int totalEscalated;

    // Today's metrics
    private int submittedToday;
    private int approvedToday;
    private int rejectedToday;
    private int changesRequestedToday;

    // SLA metrics
    private double averageProcessingTimeHours;
    private double slaComplianceRate;

    // Breakdown by days waiting
    private List<DaysWaitingBreakdown> pendingByDaysWaiting;

    // Escalation metrics
    private int activeEscalations;
    private int escalationsThisWeek;
    private Double averageEscalationResolutionHours;

    /**
     * Breakdown of pending approvals by days waiting.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DaysWaitingBreakdown {
        private int daysWaiting;  // 0, 1, 2, 3+
        private int count;
        private double percentage;
    }
}
