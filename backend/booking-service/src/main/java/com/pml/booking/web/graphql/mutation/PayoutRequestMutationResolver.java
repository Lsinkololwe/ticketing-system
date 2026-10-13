package com.pml.booking.web.graphql.mutation;

import com.pml.shared.security.revocation.FailClosedOnRevocation;
import com.pml.booking.security.TenantReads;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.service.PayoutRecoveryService;
import com.pml.booking.service.PayoutRequestService;
import com.pml.booking.web.graphql.dto.BulkPayoutOperationResponse;
import com.pml.booking.web.graphql.dto.CreatePayoutRequestInput;
import com.pml.booking.workflow.payout.PayoutProcess;
import com.pml.shared.constants.PayoutRequestStatus;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TenantBoundary;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
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
    private final com.pml.booking.service.EscrowService escrowService;
    private final com.pml.booking.security.PayoutAccess payoutAccess;
    private final PayoutProcess payoutProcess;
    private final IdempotencyGuard idempotencyGuard;
    private final ObjectMapper mapper;

    /** The most payouts one bulk retry may name. */
    public static final int MAX_BULK_PAYOUTS = 50;

    /** Create a payout request (organizer). */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<PayoutRequest> createPayoutRequest(@Valid @InputArgument CreatePayoutRequestInput input) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("Creating payout request for escrow account: {} by: {}", input.escrowAccountId(), userId))
                .flatMap(userId -> authorize(input, userId)
                        .flatMap(organizationId -> replayOrRequest(input, userId, organizationId)));
    }

    /**
     * The organization whose money is being withdrawn, once the caller is allowed to withdraw it.
     *
     * <p>The money belongs to the organization, and which organization is read from the escrow account
     * and never from the input. Only a member of that organization may ask for it: they need
     * {@code payout:request} there (an administrator holds it only when the owner has switched it on,
     * and an event grant decides alone when there is one), and the organization's status must permit
     * payouts. Platform staff, administrators and finance officers included, cannot request a payout
     * for an organizer: a person who is not a member is answered like any other outsider, and nothing
     * is recorded for the refusal. Staff keep deciding on requests that organizers have made
     * (approve, reject, hold, retry, cancel); they never originate one.
     *
     * <p>Someone outside the organization, and an escrow account that does not exist, read the same:
     * an unknown account, so an id is never confirmed. A member who lacks the permission is told so,
     * because they already know the organization and its events.
     */
    private Mono<String> authorize(CreatePayoutRequestInput input, String userId) {
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
                    return CurrentTenantScope.get().flatMap(scope -> {
                        // Membership, not scope.permits: a tenant-wide scope (administrator, finance)
                        // permits every organization for reads, and must not for requesting money.
                        if (!scope.organizationIds().contains(organizationId)) {
                            return Mono.<String>error(unknownAccount(input.escrowAccountId()));
                        }
                        return payoutAccess.mayRequest(userId, organizationId, escrow.getEventId())
                                .flatMap(allowed -> allowed
                                        ? Mono.just(organizationId)
                                        : Mono.<String>error(notPermitted()));
                    });
                });
    }

    private static RuntimeException unknownAccount(String escrowAccountId) {
        return TenantBoundary.refuse(ErrorCode.ESCROW_ACCOUNT_UNKNOWN, "escrow account " + escrowAccountId);
    }

    private static RuntimeException notPermitted() {
        return new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED,
                "you are not permitted to request payouts for this organization");
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

    /**
     * Retries a batch of failed payouts as one decision. The batch is read as a set: the same payouts in
     * another order, or with a payout named twice, are the same batch, so a double press (or a refresh that
     * re-sends the list) cannot send the money a second time. A payout that is not retryable is skipped and
     * reported as failed, as before; an empty or oversized list is refused outright, never trimmed.
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<BulkPayoutOperationResponse> bulkRetryFailedPayouts(@InputArgument List<String> payoutRequestIds,
                                                                    @InputArgument String idempotencyKey) {
        if (payoutRequestIds == null || payoutRequestIds.isEmpty() || payoutRequestIds.size() > MAX_BULK_PAYOUTS) {
            return Mono.error(new ValidationRefusal(List.of(new FieldViolation("payoutRequestIds",
                    "name between 1 and " + MAX_BULK_PAYOUTS + " payouts"))));
        }
        List<String> batch = payoutRequestIds.stream().distinct().sorted().toList();
        String fingerprint = Fingerprint.of(mapper, Map.of("payoutRequestIds", batch), Fingerprint.CLIENT_VARYING);
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(actorId -> idempotencyGuard.execute("booking:bulkRetryPayouts", idempotencyKey, fingerprint,
                        BulkPayoutOperationResponse.class, () -> Flux.fromIterable(batch)
                                .concatMap(id -> payoutProcess.retry(id, actorId)
                                        .onErrorResume(refused -> {
                                            log.info("Payout {} not retried: {}", id, refused.getMessage());
                                            return Mono.empty();
                                        }))
                                .collectList()
                                .map(processed -> bulkResponse(batch, processed))));
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

    /**
     * Retry a failed payout request. A payout is re-driven at most three times in all, and every re-drive
     * sends the money again, so a second press of the same button must never count as a second attempt.
     * The caller's key says "this is one decision": a repeat of it returns the first result without
     * touching the workflow, and the same key for another payout is refused rather than answered with
     * the wrong payout.
     */
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
