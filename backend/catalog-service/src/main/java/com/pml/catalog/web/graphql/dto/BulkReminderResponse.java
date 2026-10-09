package com.pml.catalog.web.graphql.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outcome of a bulk publish-reminder run.
 *
 * <p>Carries counts and nothing else. A bulk operation that could not run at all
 * raises a refusal, so there is no state of this object meaning "ignore the
 * numbers" — which is what a {@code success} flag beside them would create, and
 * what a caller reading the counts without checking it would miss.</p>
 *
 * <p>{@code failedCount > 0} is not a failure of the operation: the run
 * completed and some recipients were unreachable. That distinction is exactly
 * what a boolean flattened away.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkReminderResponse {
    private int sentCount;
    private int failedCount;

    public static BulkReminderResponse of(int sentCount, int failedCount) {
        return BulkReminderResponse.builder()
                .sentCount(sentCount)
                .failedCount(failedCount)
                .build();
    }
}
