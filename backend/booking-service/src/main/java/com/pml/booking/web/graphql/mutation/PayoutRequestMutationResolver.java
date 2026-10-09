package com.pml.booking.web.graphql.mutation;

import com.pml.shared.security.revocation.FailClosedOnRevocation;
import com.pml.shared.security.Permission;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import com.pml.booking.security.TenantReads;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.exception.BusinessValidationException;
import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.booking.service.PayoutRecoveryService;
import com.pml.booking.service.PayoutRequestService;
import com.pml.booking.web.graphql.dto.BulkPayoutOperationResponse;
import com.pml.booking.web.graphql.dto.CreatePayoutRequestInput;
import com.pml.booking.workflow.payout.PayoutProcess;
import com.pml.shared.constants.PayoutRequestStatus;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.security.SecurityContextUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * GraphQL mutations for payout requests.
 *
 * <p>Every state change goes through {@link PayoutProcess} to the request's payout workflow, which
 * owns the sequence, the dual-control check and the settlement steps. The resolver authenticates,
 * authorizes and reads the result back; it never writes a payout status itself.
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A01:2021 - Broken Access Control: all actor ids come from the JWT</li>
 * </ul>
 */
@Slf4j
@DgsComponent
@FailClosedOnRevocation
@Validated
@RequiredArgsConstructor
public class PayoutRequestMutationResolver {

    private final PayoutRequestService payoutRequestService;
    private final TenantReads tenantReads;
    private final PayoutRecoveryService payoutRecoveryService;
    private final IdentityServiceClient identityServiceClient;
    private final PayoutProcess payoutProcess;

    /** Create a payout request (organizer). */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<PayoutRequest> createPayoutRequest(@Valid @InputArgument CreatePayoutRequestInput input) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("Creating payout request for organizer: {} by: {}", input.organizerId(), userId))
                .zipWith(isPlatformStaff())
                // Identity decides, for the organization the organizer owns: a team member needs
                // payout:request there (an admin holds it only when the owner has switched it on),
                // and platform staff acting on the organizer's behalf are held to the organizer's
                // own authority. Either way the organization's status must permit payouts.
                .flatMap(callerAndStaff -> identityServiceClient.checkAuthorization(
                                AuthorizationRequest.builder()
                                        .userId(callerAndStaff.getT2() ? input.organizerId() : callerAndStaff.getT1())
                                        .organizationOwnerId(input.organizerId())
                                        .requiredPermission(Permission.PAYOUT_REQUEST.code())
                                        .build())
                        .map(authz -> reactor.util.function.Tuples.of(callerAndStaff.getT1(), authz)))
                .flatMap(callerAndAuthz -> {
                    String userId = callerAndAuthz.getT1();
                    var authz = callerAndAuthz.getT2();
                    if (!authz.isAuthorized()) {
                        log.warn("Payout request denied for organizer {}: {}", input.organizerId(), authz.getReason());
                        return Mono.error(new BusinessValidationException("Payout not permitted: " + authz.getReason()));
                    }
                    return replayOrRequest(input, userId);
                });
    }

    /** Whether the caller holds a platform role that acts on organizers' behalf. */
    private static Mono<Boolean> isPlatformStaff() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication() != null && context.getAuthentication().getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                                || "ROLE_FINANCE".equals(authority.getAuthority())))
                .defaultIfEmpty(false);
    }

    /**
     * A retried create — double-click, dropped connection, refresh — returns the payout that already
     * exists for its idempotency key. Replay is answered before the workflow is reached, so a replay
     * of a legitimately created payout is never refused because its own execution is open.
     */
    private Mono<PayoutRequest> replayOrRequest(CreatePayoutRequestInput input, String userId) {
        if (input.idempotencyKey() == null || input.idempotencyKey().isBlank()) {
            return payoutProcess.request(input, userId);
        }
        return payoutRequestService.findByIdempotencyKey(input.idempotencyKey())
                .doOnNext(existing -> log.info("Idempotent replay of payout request {} for key {}",
                        existing.getRequestId(), input.idempotencyKey()))
                .switchIfEmpty(Mono.defer(() -> payoutProcess.request(input, userId)));
    }

    /** Freezes a payout before any money moves; approval, retry and settlement wait for the release. */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequest> holdPayoutRequest(@InputArgument String payoutRequestId, @InputArgument String reason) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(actor -> payoutProcess.hold(payoutRequestId, actor, reason));
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequest> releasePayoutHold(@InputArgument String payoutRequestId, @InputArgument String note) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(actor -> payoutProcess.release(payoutRequestId, actor, note));
    }

    /** Approve a payout request; the approver must not be the requester. */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequest> approvePayoutRequest(@InputArgument String payoutRequestId, @InputArgument String notes) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(approverId -> payoutProcess.approve(payoutRequestId, approverId, notes));
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequest> rejectPayoutRequest(@InputArgument String payoutRequestId, @InputArgument String rejectionReason) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(rejectedBy -> payoutProcess.reject(payoutRequestId, rejectedBy, rejectionReason));
    }

    /**
     * Settlement begins when a request is approved, so there is nothing left to start by hand. The
     * operation answers with the request when it is already settling and refuses otherwise.
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequest> processPayoutRequest(@InputArgument String payoutRequestId) {
        return payoutProcess.current(payoutRequestId)
                .flatMap(request -> request.getStatus() == PayoutRequestStatus.PROCESSING
                        ? Mono.just(request)
                        : Mono.error(new TranslatedRefusal(ErrorCode.PAYOUT_STATE_INVALID,
                                "settlement begins when the request is approved; this request is " + request.getStatus())));
    }

    /** Finance confirms a manual bank transfer with the bank's reference. */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequest> completePayoutRequest(@InputArgument String payoutRequestId, @InputArgument String bankReference) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(completedBy -> payoutProcess.confirmTransfer(payoutRequestId, completedBy, bankReference));
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'ORGANIZER')")
    public Mono<PayoutRequest> cancelPayoutRequest(@InputArgument String payoutRequestId, @InputArgument String reason) {
        return tenantReads.payoutRequestForCaller(payoutRequestId)
                .then(SecurityContextUtils.requireCurrentUserId())
                .flatMap(cancelledBy -> payoutProcess.cancel(payoutRequestId, cancelledBy, reason));
    }

    // ========================================================================
    // PAYOUT RECOVERY MUTATIONS (Admin Dashboard)
    // ========================================================================

    /** A stuck or failed payout is re-driven through its workflow's retry, never by setting a status. */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequest> resumePayoutRequest(@InputArgument String payoutRequestId) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(actorId -> payoutProcess.retry(payoutRequestId, actorId));
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequest> markPayoutForReview(@InputArgument String payoutRequestId,
                                                   @InputArgument String issueType,
                                                   @InputArgument String notes) {
        log.info("Marking payout {} for review with issue type: {}", payoutRequestId, issueType);
        return payoutRecoveryService.markForReview(payoutRequestId, issueType, notes);
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequest> resolvePayoutIssue(@InputArgument String payoutRequestId,
                                                  @InputArgument String resolutionType,
                                                  @InputArgument String notes) {
        log.info("Resolving payout issue {} with resolution type: {}", payoutRequestId, resolutionType);
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(resolvedBy -> payoutRecoveryService.resolveIssue(payoutRequestId, resolutionType, resolvedBy, notes));
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<BulkPayoutOperationResponse> bulkRetryFailedPayouts(@InputArgument List<String> payoutRequestIds) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(actorId -> Flux.fromIterable(payoutRequestIds)
                        .concatMap(id -> payoutProcess.retry(id, actorId)
                                .onErrorResume(refused -> {
                                    log.info("Payout {} not retried: {}", id, refused.getMessage());
                                    return Mono.empty();
                                }))
                        .collectList())
                .map(processed -> bulkResponse(payoutRequestIds, processed));
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<BulkPayoutOperationResponse> bulkMarkPayoutsForReview(@InputArgument List<String> payoutRequestIds,
                                                                      @InputArgument String issueType,
                                                                      @InputArgument String notes) {
        log.info("Bulk marking {} payouts for review with issue type: {}", payoutRequestIds.size(), issueType);
        return payoutRecoveryService.bulkMarkForReview(payoutRequestIds, issueType, notes)
                .collectList()
                .map(processed -> bulkResponse(payoutRequestIds, processed));
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequest> escalatePayoutRequest(@InputArgument String payoutRequestId, @InputArgument String reason) {
        log.info("Escalating payout request: {} with reason: {}", payoutRequestId, reason);
        return payoutRecoveryService.escalatePayoutRequest(payoutRequestId, reason);
    }

    /** Retry a failed payout request: at most three attempts in all. */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequest> retryPayoutRequest(@InputArgument String payoutRequestId) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(actorId -> payoutProcess.retry(payoutRequestId, actorId));
    }

    private static BulkPayoutOperationResponse bulkResponse(List<String> requested, List<PayoutRequest> processed) {
        List<String> processedIds = processed.stream().map(PayoutRequest::getId).toList();
        List<String> failedPayoutIds = new ArrayList<>(requested);
        failedPayoutIds.removeAll(processedIds);
        return BulkPayoutOperationResponse.builder()
                .processedCount(processed.size())
                .failedCount(failedPayoutIds.size())
                .processedPayouts(processed)
                .failedPayoutIds(failedPayoutIds)
                .build();
    }
}
