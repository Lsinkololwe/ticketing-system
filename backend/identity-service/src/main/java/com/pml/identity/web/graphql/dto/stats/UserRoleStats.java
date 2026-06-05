package com.pml.identity.web.graphql.dto.stats;

import com.pml.shared.constants.UserType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistics for users grouped by role.
 *
 * Note: Since users can have multiple roles, the total count across all
 * roles may exceed the total number of users (one user counted per role).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleStats {
    private UserType role;
    private int count;
    private double percentage;
}
