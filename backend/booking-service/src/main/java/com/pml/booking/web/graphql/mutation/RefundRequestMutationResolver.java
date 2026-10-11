package com.pml.booking.web.graphql.mutation;

import com.pml.shared.security.revocation.FailClosedOnRevocation;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.RefundRequest;
import com.pml.booking.web.graphql.dto.BulkOperationResponse;
import com.pml.booking.web.graphql.dto.CreateRefundRequestInput;
import com.pml.booking.security.OrganizerAccess;
import com.pml.booking.workflow.refund.RefundProcess;
import com.pml.shared.idempotency.Fingerprint;
import com.pml.shared.idempotency.IdempotencyGuard;
import com.pml.shared.security.SecurityContextUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GraphQL mutations for refund requests.
 *
 * <p>Every change reaches the ticket's refund workflow through {@link RefundProcess}, which owns
 * approval, the provider refund and its verified answer. Actor ids come from the JWT.
 */
@Slf4j
@DgsComponent
@FailClosedOnRevocation
@Validated
@RequiredArgsConstructor
public class RefundRequestMutationResolver {

    private final RefundProcess refundProcess;
    private final IdempotencyGuard idempotencyGuard;
    private final ObjectMapper mapper;

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<RefundRequest> createUserRefundRequest(@Valid @InputArgument CreateRefundRequestInput input) {
        String fingerprint = Fingerprint.of(mapper, input, Fingerprint.CLIENT_VARYING);
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(requestedBy -> log.info("Creating refund request for ticket: {} by: {}", input.ticketId(), requestedBy))
                .flatMap(requestedBy -> idempotencyGuard.execute("booking:requestRefund", input.idempotencyKey(), fingerprint,
                        RefundRequest.class, () -> refundProcess.request(input.ticketId(), input.reason(), requestedBy)));
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<RefundRequest> approveRefundRequest(@InputArgument String refundRequestId, @InputArgument String reviewComments,
                                                     @InputArgument String idempotencyKey) {
        Map<String, Object> fingerprinted = new HashMap<>();
        fingerprinted.put("refundRequestId", refundRequestId);
        fingerprinted.put("reviewComments", reviewComments);
        String fingerprint = Fingerprint.of(mapper, fingerprinted, Fingerprint.CLIENT_VARYING);
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(reviewerId -> idempotencyGuard.execute("booking:approveRefund", idempotencyKey, fingerprint,
                        RefundRequest.class, () -> refundProcess.approve(refundRequestId, reviewerId, reviewComments)));
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<RefundRequest> rejectRefundRequest(@InputArgument String refundRequestId, @InputArgument String rejectionReason) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(reviewerId -> refundProcess.reject(refundRequestId, reviewerId, rejectionReason));
    }

    /** The refund is sent once it is approved; this approves a PENDING request and answers any other as it stands. */
    @DgsMutation
    @PreAuthorize("hasRole('FINANCE')")
    public Mono<RefundRequest> processRefundRequest(@InputArgument String refundRequestId) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(processedBy -> refundProcess.process(refundRequestId, processedBy));
    }

    // ========================================================================
    // ADMIN REFUND OPERATIONS
    // ========================================================================

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<RefundRequest> createAdminRefundRequest(@InputArgument String ticketId,
                                                        @InputArgument String reason,
                                                        @InputArgument Boolean bypassApproval,
                                                        @InputArgument java.math.BigDecimal amount,
                                                        @InputArgument String idempotencyKey) {
        boolean bypass = bypassApproval != null && bypassApproval;
        Map<String, Object> fingerprinted = new HashMap<>();
        fingerprinted.put("ticketId", ticketId);
        fingerprinted.put("reason", reason);
        fingerprinted.put("bypassApproval", bypass);
        fingerprinted.put("amount", amount);
        String fingerprint = Fingerprint.of(mapper, fingerprinted, Fingerprint.CLIENT_VARYING);
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(adminId -> log.info("Operator {} creating refund request for ticket: {} (bypass: {}, amount: {})",
                        adminId, ticketId, bypass, amount))
                .flatMap(adminId -> idempotencyGuard.execute("booking:requestRefund:admin", idempotencyKey, fingerprint,
                        RefundRequest.class, () -> refundProcess.requestAsAdmin(ticketId, reason, adminId, bypass, amount)));
    }

    /**
     * Withdraws a refund request. Staff may withdraw any request that has not been sent to the provider;
     * a buyer may withdraw only a request they raised themselves and only while it still waits for a
     * decision.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<RefundRequest> cancelRefundRequest(@InputArgument String refundRequestId, @InputArgument String reason) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(cancelledBy -> OrganizerAccess.isPlatformStaff().flatMap(staff -> staff
                        ? refundProcess.cancel(refundRequestId, cancelledBy, reason)
                        : refundProcess.cancelAsRequester(refundRequestId, cancelledBy, reason)));
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<BulkOperationResponse> bulkApproveRefunds(@InputArgument List<String> refundRequestIds) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(reviewerId -> refundProcess.bulkApprove(refundRequestIds, reviewerId))
                .onErrorResume(e -> {
                    log.error("Bulk approve refunds failed: {}", e.getMessage());
                    return Mono.just(BulkOperationResponse.error("Bulk approve failed: " + e.getMessage(), List.of(e.getMessage())));
                });
    }
}
