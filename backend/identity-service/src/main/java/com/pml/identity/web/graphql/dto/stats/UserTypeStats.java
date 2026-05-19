package com.pml.identity.web.graphql.dto.stats;

import com.pml.shared.constants.UserType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistics for users grouped by type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTypeStats {
    private UserType userType;
    private int count;
    private double percentage;
}
