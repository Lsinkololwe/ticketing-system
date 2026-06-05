package com.pml.identity.web.graphql.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Main container for user statistics.
 * Matches the UserStats GraphQL type.
 * Used for the admin dashboard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStats {
    // Core counts
    private int totalUsers;
    private int organizers;
    private int attendees;
    private int adminUsers;
    private int verifiedUsers;
    private int activeUsers;

    // Account status counts
    private int suspendedUsers;
    private int lockedUsers;
    private int pendingVerificationUsers;

    // Time-based counts
    private int newUsersThisMonth;
    private int newUsersThisWeek;

    // Growth metric
    private Double growthRate;  // Month-over-month percentage

    // Breakdowns
    private List<UserRoleStats> usersByRole;
    private List<UserStatusStats> usersByStatus;
}
