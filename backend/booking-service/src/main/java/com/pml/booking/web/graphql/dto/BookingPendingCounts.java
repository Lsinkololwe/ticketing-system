package com.pml.booking.web.graphql.dto;

/**
 * Pending request-queue counts owned by the Booking Service.
 *
 * Matches the {@code BookingPendingCounts} GraphQL type. Feeds the admin
 * action-center sidebar badges via the federated {@code bookingPendingCounts}
 * root query. Computed with MongoDB aggregation (never client-side counting).
 *
 * @param payoutRequests payout requests awaiting review (status = PENDING)
 * @param refundRequests refund requests awaiting review (status = PENDING)
 * @see com.pml.booking.service.PendingApprovalStatsService
 */
public record BookingPendingCounts(
        int payoutRequests,
        int refundRequests
) {}
