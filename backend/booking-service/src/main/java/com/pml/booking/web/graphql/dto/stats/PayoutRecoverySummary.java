package com.pml.booking.web.graphql.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payout recovery summary for admin dashboard.
 * Matches the PayoutRecoverySummary GraphQL type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutRecoverySummary {
    private int totalPayoutsForReview;
    private int pendingReviewCount;
    private int underReviewCount;
    private int stuckPayoutsCount;
    private int retryablePayoutsCount;
    private List<PayoutIssueTypeStats> issuesByType;
    private int recentlyResolvedCount;
    private Double averageResolutionTimeMinutes;
    private BigDecimal totalAmountAtRisk;
}
