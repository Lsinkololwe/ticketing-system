package com.pml.identity.web.graphql.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pending approval-queue counts owned by the Identity Service.
 *
 * Matches the {@code IdentityPendingCounts} GraphQL type. Feeds the admin
 * action-center sidebar badges via the federated {@code identityPendingCounts}
 * root query. Values are computed with MongoDB aggregation (never client-side
 * counting).
 *
 * @see com.pml.identity.service.PendingApprovalStatsService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityPendingCounts {

    /** Organizations awaiting admin review (status = PENDING_REVIEW). */
    private int organizerApplications;

    /** Verification documents awaiting review (status = PENDING). */
    private int documentVerifications;
}
