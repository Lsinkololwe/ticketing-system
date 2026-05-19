package com.pml.booking.service;

import com.pml.booking.domain.model.RefundRequest;
import com.pml.booking.web.graphql.dto.BulkOperationResponse;
import com.pml.booking.web.graphql.dto.RefundCalculation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

/**
 * Refund Service Interface
 *
 * Manages refund requests and processing via pawaPay.
 * Integrates with Commission and Escrow services for financial adjustments.
 */
public interface RefundService {

    /**
     * Request a refund for a ticket.
     *
     * @param ticketId The ticket ID
     * @param reason   Reason for refund
     * @param requestedBy User ID of requester
     * @return Created refund request
     */
    Mono<RefundRequest> requestRefund(
            String ticketId,
            String reason,
            String requestedBy
    );

    /**
     * Request a partial refund for a ticket.
     *
     * @param ticketId The ticket ID
     * @param amount   Amount to refund
     * @param reason   Reason for refund
     * @param requestedBy User ID of requester
     * @return Created refund request
     */
    Mono<RefundRequest> requestPartialRefund(
            String ticketId,
            BigDecimal amount,
            String reason,
            String requestedBy
    );

    /**
     * Approve a pending refund request.
     *
     * @param refundRequestId The refund request ID
     * @param approvedBy      User ID of approver
     * @param comments        Approval comments
     * @return Approved refund request
     */
    Mono<RefundRequest> approveRefund(
            String refundRequestId,
            String approvedBy,
            String comments
    );

    /**
     * Reject a pending refund request.
     *
     * @param refundRequestId The refund request ID
     * @param rejectedBy      User ID of rejector
     * @param reason          Rejection reason
     * @return Rejected refund request
     */
    Mono<RefundRequest> rejectRefund(
            String refundRequestId,
            String rejectedBy,
            String reason
    );

    /**
     * Process an approved refund via pawaPay.
     *
     * @param refundRequestId The refund request ID
     * @return Updated refund request
     */
    Mono<RefundRequest> processRefund(String refundRequestId);

    /**
     * Handle refund callback from pawaPay.
     *
     * @param pawaPayRefundId pawaPay refund ID
     * @param status          Final status (COMPLETED, FAILED)
     * @param providerTransactionId Provider's transaction reference
     * @param failureCode     Optional failure code
     * @param failureMessage  Optional failure message
     * @return Updated refund request
     */
    Mono<RefundRequest> handleRefundCallback(
            String pawaPayRefundId,
            String status,
            String providerTransactionId,
            String failureCode,
            String failureMessage
    );

    /**
     * Find refund request by ID.
     */
    Mono<RefundRequest> findById(String id);

    /**
     * Find refund request by ticket ID.
     */
    Mono<RefundRequest> findByTicketId(String ticketId);

    /**
     * Find all refund requests for an event.
     */
    Flux<RefundRequest> findByEventId(String eventId);

    /**
     * Find all refund requests by buyer.
     */
    Flux<RefundRequest> findByBuyerId(String buyerId);

    /**
     * Find pending refund requests (awaiting approval).
     */
    Flux<RefundRequest> findPendingRefunds();

    /**
     * Find approved refunds pending processing.
     */
    Flux<RefundRequest> findApprovedRefundsPendingProcessing();

    /**
     * Process automatic refunds for cancelled events.
     *
     * @param eventId The cancelled event ID
     * @return Count of refunds initiated
     */
    Mono<Long> processAutomaticRefunds(String eventId);

    /**
     * Calculate the refund amount for a ticket before confirming.
     * Shows the customer what they'll receive based on the refund policy.
     *
     * Business Intent: Mobile checkout flow preview - shows breakdown of
     * original amount, refund percentage, fees, and net refund amount.
     *
     * @param ticketId The ticket ID to calculate refund for
     * @return RefundCalculation with full breakdown
     */
    Mono<RefundCalculation> calculateRefundAmount(String ticketId);

    // ========================================================================
    // ADMIN REFUND OPERATIONS
    // ========================================================================

    /**
     * Admin-initiated refund request.
     * Admin can optionally bypass the approval workflow.
     *
     * @param ticketId       The ticket ID
     * @param reason         Reason for refund
     * @param adminId        Admin user ID
     * @param bypassApproval If true, auto-approve the refund
     * @return Created refund request
     */
    Mono<RefundRequest> createAdminRefundRequest(
            String ticketId,
            String reason,
            String adminId,
            boolean bypassApproval
    );

    /**
     * Cancel a pending refund request.
     *
     * @param refundRequestId The refund request ID
     * @param cancelledBy     User ID who cancelled
     * @param reason          Cancellation reason
     * @return Cancelled refund request
     */
    Mono<RefundRequest> cancelRefundRequest(
            String refundRequestId,
            String cancelledBy,
            String reason
    );

    /**
     * Bulk approve multiple refund requests.
     *
     * @param refundRequestIds List of refund request IDs to approve
     * @param reviewerId       Reviewer user ID
     * @return Bulk operation response with counts and errors
     */
    Mono<BulkOperationResponse> bulkApproveRefunds(
            List<String> refundRequestIds,
            String reviewerId
    );
}
