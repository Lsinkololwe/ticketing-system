package com.pml.identity.web.graphql.dto.stats;

import com.pml.identity.domain.enums.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistics for users grouped by account status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusStats {
    private AccountStatus status;
    private int count;
    private double percentage;
}
