package com.pml.booking.web.graphql.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Statistics for a specific payout issue type.
 * Matches the PayoutIssueTypeStats GraphQL type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutIssueTypeStats {
    private String issueType;
    private int count;
    private double percentage;
    private int unresolvedCount;
    private BigDecimal totalAmount;
}
