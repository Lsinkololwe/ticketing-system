package com.pml.booking.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.web.graphql.dto.ApproveRefundRequestMutationResponse;
import com.pml.booking.web.graphql.dto.BulkOperationResponse;
import com.pml.booking.web.graphql.dto.CreateRefundRequestInput;
import com.pml.booking.web.graphql.dto.CreateRefundRequestMutationResponse;
import com.pml.booking.web.graphql.dto.ProcessRefundRequestMutationResponse;
import com.pml.booking.web.graphql.dto.RefundRequestMutationResponse;
import com.pml.booking.web.graphql.dto.RejectRefundRequestMutationResponse;
import com.pml.booking.service.RefundService;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * GraphQL Mutation Resolver for Refund Request Operations
 *
 * <p>Handles refund request lifecycle including creation, approval, rejection,
 * and processing. Implements the platform's refund policy workflow.</p>
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A01:2021 - Broken Access Control: All actor IDs (reviewerId, processedBy, etc.)
 *       are extracted from JWT, never from client input</li>
 * </ul>
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class RefundRequestMutationResolver {

    private final RefundService refundService;

    /**
     * Create a refund request (customer-initiated).
     * User ID extracted from JWT.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<CreateRefundRequestMutationResponse> createUserRefundRequest(
            @InputArgument CreateRefundRequestInput input
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(requestedBy -> log.info("Creating refund request for ticket: {} by: {}",
                        input.ticketId(), requestedBy))
                .flatMap(requestedBy -> refundService.requestRefund(input.ticketId(), input.reason(), requestedBy)
                        .map(request -> new CreateRefundRequestMutationResponse(
                                true,
                                "Refund request created successfully",
                                request,
                                List.of(),
                                null
                        )))
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(new CreateRefundRequestMutationResponse(
                                false, "Authentication required", null, List.of("Please log in"), null)))
                .onErrorResume(e -> {
                    log.error("Create refund request failed: {}", e.getMessage());
                    return Mono.just(new CreateRefundRequestMutationResponse(
                            false, e.getMessage(), null, List.of(e.getMessage()), null));
                });
    }

    /**
     * Approve a refund request (admin/finance).
     * Reviewer ID extracted from JWT.
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<ApproveRefundRequestMutationResponse> approveRefundRequest(
            @InputArgument String refundRequestId,
            @InputArgument String reviewComments
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(reviewerId -> log.info("Approving refund request: {} by: {}",
                        refundRequestId, reviewerId))
                .flatMap(reviewerId -> refundService.approveRefund(refundRequestId, reviewerId, reviewComments)
                        .map(request -> new ApproveRefundRequestMutationResponse(
                                true,
                                "Refund request approved successfully",
                                request,
                                List.of(),
                                null
                        )))
                .onErrorResume(e -> {
                    log.error("Approve refund request failed: {}", e.getMessage());
                    return Mono.just(new ApproveRefundRequestMutationResponse(
                            false, e.getMessage(), null, List.of(e.getMessage()), null));
                });
    }

    /**
     * Reject a refund request (admin/finance).
     * Reviewer ID extracted from JWT.
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<RejectRefundRequestMutationResponse> rejectRefundRequest(
            @InputArgument String refundRequestId,
            @InputArgument String rejectionReason
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(reviewerId -> log.info("Rejecting refund request: {} by: {} reason: {}",
                        refundRequestId, reviewerId, rejectionReason))
                .flatMap(reviewerId -> refundService.rejectRefund(refundRequestId, reviewerId, rejectionReason)
                        .map(request -> new RejectRefundRequestMutationResponse(
                                true,
                                "Refund request rejected",
                                request,
                                List.of(),
                                null
                        )))
                .onErrorResume(e -> {
                    log.error("Reject refund request failed: {}", e.getMessage());
                    return Mono.just(new RejectRefundRequestMutationResponse(
                            false, e.getMessage(), null, List.of(e.getMessage()), null));
                });
    }

    /**
     * Process a refund request (finance).
     * Processor ID extracted from JWT.
     */
    @DgsMutation
    @PreAuthorize("hasRole('FINANCE')")
    public Mono<ProcessRefundRequestMutationResponse> processRefundRequest(
            @InputArgument String refundRequestId
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(processedBy -> log.info("Processing refund request: {} by: {}",
                        refundRequestId, processedBy))
                .flatMap(processedBy -> refundService.processRefund(refundRequestId)
                        .map(request -> new ProcessRefundRequestMutationResponse(
                                true,
                                "Refund processed successfully",
                                request,
                                List.of(),
                                null
                        )))
                .onErrorResume(e -> {
                    log.error("Process refund request failed: {}", e.getMessage());
                    return Mono.just(new ProcessRefundRequestMutationResponse(
                            false, e.getMessage(), null, List.of(e.getMessage()), null));
                });
    }

    // ========================================================================
    // ADMIN REFUND OPERATIONS
    // ========================================================================

    /**
     * Admin-initiated refund request.
     * Admin ID extracted from JWT.
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RefundRequestMutationResponse> createAdminRefundRequest(
            @InputArgument String ticketId,
            @InputArgument String reason,
            @InputArgument Boolean bypassApproval
    ) {
        boolean bypass = bypassApproval != null && bypassApproval;
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(adminId -> log.info("Admin {} creating refund request for ticket: {} (bypass: {})",
                        adminId, ticketId, bypass))
                .flatMap(adminId -> refundService.createAdminRefundRequest(ticketId, reason, adminId, bypass)
                        .map(request -> RefundRequestMutationResponse.success(
                                bypass ? "Refund request created and auto-approved" : "Refund request created",
                                request
                        )))
                .onErrorResume(e -> {
                    log.error("Admin create refund request failed: {}", e.getMessage());
                    return Mono.just(RefundRequestMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Cancel a pending refund request.
     * Canceller ID extracted from JWT.
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RefundRequestMutationResponse> cancelRefundRequest(
            @InputArgument String refundRequestId,
            @InputArgument String reason
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(cancelledBy -> log.info("Cancelling refund request: {} by: {}",
                        refundRequestId, cancelledBy))
                .flatMap(cancelledBy -> refundService.cancelRefundRequest(refundRequestId, cancelledBy, reason)
                        .map(request -> RefundRequestMutationResponse.success("Refund request cancelled", request)))
                .onErrorResume(e -> {
                    log.error("Cancel refund request failed: {}", e.getMessage());
                    return Mono.just(RefundRequestMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Bulk approve multiple refund requests.
     * Reviewer ID extracted from JWT.
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<BulkOperationResponse> bulkApproveRefunds(
            @InputArgument List<String> refundRequestIds
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(reviewerId -> log.info("Bulk approving {} refund requests by: {}",
                        refundRequestIds.size(), reviewerId))
                .flatMap(reviewerId -> refundService.bulkApproveRefunds(refundRequestIds, reviewerId))
                .onErrorResume(e -> {
                    log.error("Bulk approve refunds failed: {}", e.getMessage());
                    return Mono.just(BulkOperationResponse.error(
                            "Bulk approve failed: " + e.getMessage(),
                            List.of(e.getMessage())
                    ));
                });
    }
}
