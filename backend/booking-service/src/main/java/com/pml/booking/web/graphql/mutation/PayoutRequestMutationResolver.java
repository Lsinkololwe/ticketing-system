package com.pml.booking.web.graphql.mutation;

import com.pml.shared.security.revocation.FailClosedOnRevocation;
import com.pml.shared.security.Permission;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import com.pml.booking.security.TenantReads;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.booking.service.PayoutRecoveryService;
import com.pml.booking.service.PayoutRequestService;
import com.pml.booking.web.graphql.dto.BulkPayoutOperationResponse;
import com.pml.booking.web.graphql.dto.CreatePayoutRequestInput;
import com.pml.booking.workflow.payout.PayoutProcess;
import com.pml.shared.constants.PayoutRequestStatus;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TenantBoundary;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.idempotency.Fingerprint;
import com.pml.shared.idempotency.IdempotencyGuard;
import com.pml.shared.security.SecurityContextUtils;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final com.pml.booking.service.EscrowService escrowService;
    private final com.pml.booking.security.PayoutAccess payoutAccess;
    private final PayoutProcess payoutProcess;
    private final IdempotencyGuard idempotencyGuard;
    private final ObjectMapper mapper;

    /** Create a payout request (organizer). */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<PayoutRequest> createPayoutRequest(@Valid @InputArgument CreatePayoutRequestInput input) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("Creating payout request for escrow account: {} by: {}", input.escrowAccountId(), userId))
                .zipWith(isPlatformStaff())
                .flatMap(callerAndStaff -> authorize(input, callerAndStaff.getT1(), callerAndStaff.getT2())
                        .flatMap(organizationId -> replayOrRequest(input, callerAndStaff.getT1(), organizationId)));
    }

    /**
     * The organization whose money is being withdrawn, once the caller is allowed to withdraw it.
     *
     * <p>The money belongs to the organization, and which organization is read from the escrow account
     * and never from the input. A member needs {@code payout:request} there (an administrator holds it
     * only when the owner has switched it on, and an event grant decides alone when there is one), and
     * the organization's status must permit payouts. Platform staff acting on an organizer's behalf are
     * held to that organizer's own authority, and the organization they resolve to must be the one the
     * escrow account belongs to.
     *
     * <p>Someone outside the organization, and an escrow account that does not exist, read the same:
     * an unknown account, so an id is never confirmed. A member who lacks the permission is told so,
     * because they already know the organization and its events.
     */
    private Mono<String> authorize(CreatePayoutRequestInput input, String userId, boolean platformStaff) {
        if (input.escrowAccountId() == null || input.escrowAccountId().isBlank()) {
            return Mono.error(new IllegalArgumentException("A payout request names its escrow account"));
        }
        return escrowService.findById(input.escrowAccountId())
                .switchIfEmpty(Mono.error(() -> unknownAccount(input.escrowAccountId())))
                .flatMap(escrow -> {
                    String organizationId = escrow.getOrganizationId();
                    if (organizationId == null || organizationId.isBlank()) {
                        return Mono.<String>error(unknownAccount(input.escrowAccountId()));
                    }
                    if (!platformStaff) {
                        return CurrentTenantScope.get().flatMap(scope -> {
                            if (!scope.permits(organizationId)) {
                                return Mono.<String>error(unknownAccount(input.escrowAccountId()));
                            }
                            return payoutAccess.mayRequest(userId, organizationId, escrow.getEventId())
                                    .flatMap(allowed -> allowed
                                            ? Mono.just(organizationId)
                                            : Mono.<String>error(notPermitted()));
                        });
                    }
                    return identityServiceClient.checkAuthorization(AuthorizationRequest.builder()
                                    .userId(input.organizerId())
                                    .organizationOwnerId(input.organizerId())
                                    .requiredPermission(Permission.PAYOUT_REQUEST.code())
                                    .build())
                            .flatMap(authz -> authz.isAuthorized() && organizationId.equals(authz.getOrganizationId())
                                    ? Mono.just(organizationId)
                                    : Mono.<String>error(notPermitted()));
                });
    }

    private static RuntimeException unknownAccount(String escrowAccountId) {
        return TenantBoundary.refuse(ErrorCode.ESCROW_ACCOUNT_UNKNOWN, "escrow account " + escrowAccountId);
    }

    private static RuntimeException notPermitted() {
        return new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED,
                "you are not permitted to request payouts for this organization");
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
     * exists for its idempotency key; a different body reusing the same key is refused rather than
     * replayed. The shared {@link IdempotencyGuard}'s Mongo ledger is the authority, not a bare
     * lookup by key, so a mismatched retry can no longer be silently handed the wrong payout back.
     */
    private Mono<PayoutRequest> replayOrRequest(CreatePayoutRequestInput input, String userId, String organizationId) {
        String idempotencyKey = input.idempotencyKey();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Mono.error(new IllegalArgumentException(
                    "IDEMPOTENCY_KEY_REQUIRED: createPayoutRequest needs a client-supplied key"));
        }
        String fingerprint = Fingerprint.of(mapper, input, Fingerprint.CLIENT_VARYING);
        return idempotencyGuard.execute("booking:requestPayout", idempotencyKey, fingerprint,
                PayoutRequest.class, () -> payoutProcess.request(input, userId, organizationId));
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
    public Mono<PayoutRequest> approvePayoutRequest(@InputArgument String payoutRequestId, @InputArgument String notes,
                                                     @InputArgument String idempotencyKey) {
        Map<String, Object> fingerprinted = new HashMap<>();
        fingerprinted.put("payoutRequestId", payoutRequestId);
        fingerprinted.put("notes", notes);
        String fingerprint = Fingerprint.of(mapper, fingerprinted, Fingerprint.CLIENT_VARYING);
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(approverId -> idempotencyGuard.execute("booking:approvePayout", idempotencyKey, fingerprint,
                        PayoutRequest.class, () -> payoutProcess.approve(payoutRequestId, approverId, notes)));
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
    public Mono<PayoutRequest> retryPayoutRequest(@InputArgument String payoutRequestId, @InputArgument String idempotencyKey) {
        String fingerprint = Fingerprint.of(mapper, Map.of("payoutRequestId", payoutRequestId), Fingerprint.CLIENT_VARYING);
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(actorId -> idempotencyGuard.execute("booking:retryPayout", idempotencyKey, fingerprint,
                        PayoutRequest.class, () -> payoutProcess.retry(payoutRequestId, actorId)));
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
