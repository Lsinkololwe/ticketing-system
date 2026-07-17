package com.pml.catalog.web.graphql.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pending approval-queue counts owned by the Catalog Service.
 *
 * Matches the {@code CatalogPendingCounts} GraphQL type. Feeds the admin
 * action-center sidebar badges via the federated {@code catalogPendingCounts}
 * root query. Computed with MongoDB aggregation (never client-side counting).
 *
 * @see com.pml.catalog.service.PendingApprovalStatsService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogPendingCounts {

    /** Events awaiting admin review (status = PENDING_APPROVAL, not soft-deleted). */
    private int eventReviews;
}
