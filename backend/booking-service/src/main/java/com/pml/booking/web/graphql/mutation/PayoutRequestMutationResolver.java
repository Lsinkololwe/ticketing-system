package com.pml.booking.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.web.graphql.dto.ApprovePayoutRequestMutationResponse;
import com.pml.booking.web.graphql.dto.BulkPayoutOperationResponse;
import com.pml.booking.web.graphql.dto.CreatePayoutRequestInput;
import com.pml.booking.web.graphql.dto.CreatePayoutRequestMutationResponse;
import com.pml.booking.web.graphql.dto.PayoutRequestMutationResponse;
import com.pml.booking.web.graphql.dto.ProcessPayoutRequestMutationResponse;
import com.pml.booking.web.graphql.dto.RejectPayoutRequestMutationResponse;
import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.service.BankAccountService;
import com.pml.booking.service.PayoutRecoveryService;
import com.pml.booking.service.PayoutRequestService;
import com.pml.shared.constants.PayoutRequestStatus;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GraphQL Mutation Resolver for Payout Request Operations
 *
 * <p>Handles payout request lifecycle including creation, approval, rejection,
 * and processing. Implements the platform's payout policy workflow.</p>
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A01:2021 - Broken Access Control: All actor IDs extracted from JWT</li>
 * </ul>
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class PayoutRequestMutationResolver {

    private final PayoutRequestService payoutRequestService;
    private final BankAccountService bankAccountService;
    private final PayoutRecoveryService payoutRecoveryService;

    /**
     * Create a payout request (organizer).
     * User ID extracted from JWT.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated() and (#input.organizerId == authentication.principal.subject or hasAnyRole('ADMIN', 'FINANCE'))")
    public Mono<CreatePayoutRequestMutationResponse> createPayoutRequest(
            @InputArgument CreatePayoutRequestInput input
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("Creating payout request for organizer: {} by: {}",
                        input.organizerId(), userId))
                .flatMap(userId -> bankAccountService.findById(input.bankAccountId())
                        .flatMap(bankAccount -> {
                            // Calculate fees (platform takes 5% + ZMW 10 processing fee)
                            BigDecimal platformFee = input.requestedAmount()
                                    .multiply(new BigDecimal("0.05"));
                            BigDecimal processingFee = new BigDecimal("10.00");
                            BigDecimal netPayoutAmount = input.requestedAmount()
                                    .subtract(platformFee)
                                    .subtract(processingFee);

                            PayoutRequest payoutRequest = PayoutRequest.builder()
                                    .requestId("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                                    .organizerId(input.organizerId())
                                    .eventId(input.eventId())
                                    .escrowAccountId(input.escrowAccountId())
                                    .bankAccountId(input.bankAccountId())
                                    .bankAccountName(bankAccount.getAccountHolderName())
                                    .bankName(bankAccount.getBankName())
                                    .accountNumber(bankAccount.getAccountNumber())
                                    .requestedAmount(input.requestedAmount())
                                    .platformFee(platformFee)
                                    .processingFee(processingFee)
                                    .netPayoutAmount(netPayoutAmount)
                                    .currency(input.currency() != null ? input.currency() : "ZMW")
                                    .status(PayoutRequestStatus.PENDING)
                                    .payoutMethod(input.payoutMethod())
                                    .requestedAt(LocalDateTime.now())
                                    .requestedBy(userId)
                                    .notes(input.notes())
                                    .metadata(input.metadata())
                                    .build();

                            return payoutRequestService.save(payoutRequest);
                        })
                        .map(request -> new CreatePayoutRequestMutationResponse(
                                true, "Payout request created successfully", request, List.of(), null
                        ))
                        .switchIfEmpty(Mono.just(new CreatePayoutRequestMutationResponse(
                                false, "Bank account not found", null, List.of("Bank account not found"), null
                        ))))
                .onErrorResume(SecurityException.class, e -> Mono.just(new CreatePayoutRequestMutationResponse(
                        false, "Authentication required", null, List.of("Please log in"), null)))
                .onErrorResume(e -> {
                    log.error("Create payout request failed: {}", e.getMessage());
                    return Mono.just(new CreatePayoutRequestMutationResponse(
                            false, e.getMessage(), null, List.of(e.getMessage()), null));
                });
    }

    /**
     * Approve a payout request.
     * Approver ID extracted from JWT.
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<ApprovePayoutRequestMutationResponse> approvePayoutRequest(
            @InputArgument String payoutRequestId,
            @InputArgument String notes
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(approverId -> log.info("Approving payout request: {} by: {}", payoutRequestId, approverId))
                .flatMap(approverId -> payoutRequestService.findById(payoutRequestId)
                        .flatMap(request -> {
                            if (request.getStatus() != PayoutRequestStatus.PENDING) {
                                return Mono.just(new ApprovePayoutRequestMutationResponse(
                                        false, "Payout request is not in PENDING status", request,
                                        List.of("Invalid status for approval"), null));
                            }
                            PayoutRequest updated = request.toBuilder()
                                    .status(PayoutRequestStatus.APPROVED)
                                    .approvedAt(LocalDateTime.now())
                                    .approvedBy(approverId)
                                    .notes(notes)
                                    .build();
                            return payoutRequestService.save(updated)
                                    .map(r -> new ApprovePayoutRequestMutationResponse(
                                            true, "Payout request approved successfully", r, List.of(), null));
                        })
                        .switchIfEmpty(Mono.just(new ApprovePayoutRequestMutationResponse(
                                false, "Payout request not found", null,
                                List.of("Payout request not found"), null))))
                .onErrorResume(e -> {
                    log.error("Approve payout request failed: {}", e.getMessage());
                    return Mono.just(new ApprovePayoutRequestMutationResponse(
                            false, e.getMessage(), null, List.of(e.getMessage()), null));
                });
    }

    /**
     * Reject a payout request.
     * Rejector ID extracted from JWT.
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<RejectPayoutRequestMutationResponse> rejectPayoutRequest(
            @InputArgument String payoutRequestId,
            @InputArgument String rejectionReason
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(rejectedBy -> log.info("Rejecting payout request: {} by: {} reason: {}",
                        payoutRequestId, rejectedBy, rejectionReason))
                .flatMap(rejectedBy -> payoutRequestService.findById(payoutRequestId)
                        .flatMap(request -> {
                            if (request.getStatus().isFinal()) {
                                return Mono.just(new RejectPayoutRequestMutationResponse(
                                        false, "Payout request is already in a final status", request,
                                        List.of("Cannot reject a finalized payout request"), null));
                            }
                            PayoutRequest updated = request.toBuilder()
                                    .status(PayoutRequestStatus.REJECTED)
                                    .rejectedAt(LocalDateTime.now())
                                    .rejectedBy(rejectedBy)
                                    .rejectionReason(rejectionReason)
                                    .build();
                            return payoutRequestService.save(updated)
                                    .map(r -> new RejectPayoutRequestMutationResponse(
                                            true, "Payout request rejected", r, List.of(), null));
                        })
                        .switchIfEmpty(Mono.just(new RejectPayoutRequestMutationResponse(
                                false, "Payout request not found", null,
                                List.of("Payout request not found"), null))))
                .onErrorResume(e -> {
                    log.error("Reject payout request failed: {}", e.getMessage());
                    return Mono.just(new RejectPayoutRequestMutationResponse(
                            false, e.getMessage(), null, List.of(e.getMessage()), null));
                });
    }

    /**
     * Process a payout request.
     * Processor ID extracted from JWT.
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<ProcessPayoutRequestMutationResponse> processPayoutRequest(
            @InputArgument String payoutRequestId
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(processedBy -> log.info("Processing payout request: {} by: {}",
                        payoutRequestId, processedBy))
                .flatMap(processedBy -> payoutRequestService.findById(payoutRequestId)
                        .flatMap(request -> {
                            if (request.getStatus() != PayoutRequestStatus.APPROVED) {
                                return Mono.just(new ProcessPayoutRequestMutationResponse(
                                        false, "Payout request must be APPROVED before processing", request,
                                        List.of("Invalid status for processing"), null));
                            }
                            PayoutRequest updated = request.toBuilder()
                                    .status(PayoutRequestStatus.PROCESSING)
                                    .processedBy(processedBy)
                                    .build();
                            return payoutRequestService.save(updated)
                                    .map(r -> new ProcessPayoutRequestMutationResponse(
                                            true, "Payout request is being processed", r, List.of(), null));
                        })
                        .switchIfEmpty(Mono.just(new ProcessPayoutRequestMutationResponse(
                                false, "Payout request not found", null,
                                List.of("Payout request not found"), null))))
                .onErrorResume(e -> {
                    log.error("Process payout request failed: {}", e.getMessage());
                    return Mono.just(new ProcessPayoutRequestMutationResponse(
                            false, e.getMessage(), null, List.of(e.getMessage()), null));
                });
    }

    /**
     * Complete a payout request after successful bank transfer.
     * Completer ID extracted from JWT.
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestMutationResponse> completePayoutRequest(
            @InputArgument String payoutRequestId,
            @InputArgument String bankReference
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(completedBy -> log.info("Completing payout request: {} with bank reference: {} by: {}",
                        payoutRequestId, bankReference, completedBy))
                .flatMap(completedBy -> payoutRequestService.complete(payoutRequestId, bankReference, completedBy)
                        .map(p -> PayoutRequestMutationResponse.success(p, "Payout request completed successfully"))
                        .doOnSuccess(r -> log.info("Payout request {} completed with bank ref: {}",
                                payoutRequestId, bankReference)))
                .onErrorResume(e -> {
                    log.error("Complete payout request failed: {}", e.getMessage());
                    return Mono.just(PayoutRequestMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Cancel a payout request.
     * Canceller ID extracted from JWT.
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @payoutSecurityService.isPayoutRequestOwner(#payoutRequestId, authentication)")
    public Mono<RejectPayoutRequestMutationResponse> cancelPayoutRequest(
            @InputArgument String payoutRequestId,
            @InputArgument String reason
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(cancelledBy -> log.info("Cancelling payout request: {} by: {} reason: {}",
                        payoutRequestId, cancelledBy, reason))
                .flatMap(cancelledBy -> payoutRequestService.findById(payoutRequestId)
                        .flatMap(request -> {
                            if (!request.getStatus().canBeCancelled()) {
                                return Mono.just(new RejectPayoutRequestMutationResponse(
                                        false, "Payout request cannot be cancelled in current status", request,
                                        List.of("Cannot cancel payout request in " + request.getStatus() + " status"), null));
                            }
                            PayoutRequest updated = request.toBuilder()
                                    .status(PayoutRequestStatus.CANCELLED)
                                    .rejectedAt(LocalDateTime.now())
                                    .rejectedBy(cancelledBy)
                                    .rejectionReason(reason)
                                    .build();
                            return payoutRequestService.save(updated)
                                    .map(r -> new RejectPayoutRequestMutationResponse(
                                            true, "Payout request cancelled", r, List.of(), null));
                        })
                        .switchIfEmpty(Mono.just(new RejectPayoutRequestMutationResponse(
                                false, "Payout request not found", null,
                                List.of("Payout request not found"), null))))
                .onErrorResume(e -> {
                    log.error("Cancel payout request failed: {}", e.getMessage());
                    return Mono.just(new RejectPayoutRequestMutationResponse(
                            false, e.getMessage(), null, List.of(e.getMessage()), null));
                });
    }

    // ========================================================================
    // PAYOUT RECOVERY MUTATIONS (Admin Dashboard)
    // ========================================================================

    /**
     * Resume a payout request.
     * Schema: resumePayoutRequest(payoutRequestId: ID!): PayoutRequestMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestMutationResponse> resumePayoutRequest(
            @InputArgument String payoutRequestId
    ) {
        log.info("Resuming payout request: {}", payoutRequestId);
        return payoutRecoveryService.resumePayoutRequest(payoutRequestId)
                .map(p -> PayoutRequestMutationResponse.success(p, "Payout request resumed successfully"))
                .doOnSuccess(r -> log.info("Payout request {} resumed successfully", payoutRequestId))
                .onErrorResume(e -> {
                    log.error("Failed to resume payout request {}: {}", payoutRequestId, e.getMessage());
                    return Mono.just(PayoutRequestMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Mark a payout request for review.
     * Schema: markPayoutForReview(payoutRequestId: ID!, issueType: PayoutIssueType!, notes: String): PayoutRequestMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestMutationResponse> markPayoutForReview(
            @InputArgument String payoutRequestId,
            @InputArgument String issueType,
            @InputArgument String notes
    ) {
        log.info("Marking payout {} for review with issue type: {}", payoutRequestId, issueType);
        return payoutRecoveryService.markForReview(payoutRequestId, issueType, notes)
                .map(p -> PayoutRequestMutationResponse.success(p, "Payout marked for review"))
                .doOnSuccess(r -> log.info("Payout {} marked for review", payoutRequestId))
                .onErrorResume(e -> {
                    log.error("Failed to mark payout {} for review: {}", payoutRequestId, e.getMessage());
                    return Mono.just(PayoutRequestMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Resolve a payout issue.
     * Schema: resolvePayoutIssue(payoutRequestId: ID!, resolutionType: PayoutResolutionType!, notes: String!, newBankAccountId: ID): PayoutRequestMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestMutationResponse> resolvePayoutIssue(
            @InputArgument String payoutRequestId,
            @InputArgument String resolutionType,
            @InputArgument String notes,
            @InputArgument String newBankAccountId
    ) {
        log.info("Resolving payout issue {} with resolution type: {}", payoutRequestId, resolutionType);
        return payoutRecoveryService.resolveIssue(payoutRequestId, resolutionType, null, notes, newBankAccountId)
                .map(p -> PayoutRequestMutationResponse.success(p, "Payout issue resolved"))
                .doOnSuccess(r -> log.info("Payout issue {} resolved", payoutRequestId))
                .onErrorResume(e -> {
                    log.error("Failed to resolve payout issue {}: {}", payoutRequestId, e.getMessage());
                    return Mono.just(PayoutRequestMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Bulk retry failed payout requests.
     * Schema: bulkRetryFailedPayouts(payoutRequestIds: [ID!]!): BulkPayoutOperationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<BulkPayoutOperationResponse> bulkRetryFailedPayouts(
            @InputArgument List<String> payoutRequestIds
    ) {
        log.info("Bulk retrying {} failed payouts", payoutRequestIds.size());
        return payoutRecoveryService.bulkRetryFailedPayouts(payoutRequestIds)
                .collectList()
                .map(processedPayouts -> {
                    List<String> processedIds = processedPayouts.stream()
                            .map(PayoutRequest::getId)
                            .toList();
                    List<String> failedPayoutIds = new ArrayList<>(payoutRequestIds);
                    failedPayoutIds.removeAll(processedIds);

                    return BulkPayoutOperationResponse.builder()
                            .success(true)
                            .message("Bulk retry completed")
                            .processedCount(processedPayouts.size())
                            .failedCount(failedPayoutIds.size())
                            .processedPayouts(processedPayouts)
                            .failedPayoutIds(failedPayoutIds)
                            .errors(List.of())
                            .build();
                })
                .doOnSuccess(r -> log.info("Bulk retry completed: {} processed, {} failed",
                        r.getProcessedCount(), r.getFailedCount()));
    }

    /**
     * Bulk mark payout requests for review.
     * Schema: bulkMarkPayoutsForReview(payoutRequestIds: [ID!]!, issueType: PayoutIssueType!, notes: String): BulkPayoutOperationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<BulkPayoutOperationResponse> bulkMarkPayoutsForReview(
            @InputArgument List<String> payoutRequestIds,
            @InputArgument String issueType,
            @InputArgument String notes
    ) {
        log.info("Bulk marking {} payouts for review with issue type: {}", payoutRequestIds.size(), issueType);
        return payoutRecoveryService.bulkMarkForReview(payoutRequestIds, issueType, notes)
                .collectList()
                .map(processedPayouts -> {
                    List<String> processedIds = processedPayouts.stream()
                            .map(PayoutRequest::getId)
                            .toList();
                    List<String> failedPayoutIds = new ArrayList<>(payoutRequestIds);
                    failedPayoutIds.removeAll(processedIds);

                    return BulkPayoutOperationResponse.builder()
                            .success(true)
                            .message("Bulk mark for review completed")
                            .processedCount(processedPayouts.size())
                            .failedCount(failedPayoutIds.size())
                            .processedPayouts(processedPayouts)
                            .failedPayoutIds(failedPayoutIds)
                            .errors(List.of())
                            .build();
                })
                .doOnSuccess(r -> log.info("Bulk mark for review completed: {} processed, {} failed",
                        r.getProcessedCount(), r.getFailedCount()));
    }

    /**
     * Escalate a payout request.
     * Schema: escalatePayoutRequest(payoutRequestId: ID!, reason: String!): PayoutRequestMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestMutationResponse> escalatePayoutRequest(
            @InputArgument String payoutRequestId,
            @InputArgument String reason
    ) {
        log.info("Escalating payout request: {} with reason: {}", payoutRequestId, reason);
        return payoutRecoveryService.escalatePayoutRequest(payoutRequestId, reason)
                .map(p -> PayoutRequestMutationResponse.success(p, "Payout request escalated"))
                .doOnSuccess(r -> log.info("Payout request {} escalated", payoutRequestId))
                .onErrorResume(e -> {
                    log.error("Failed to escalate payout request {}: {}", payoutRequestId, e.getMessage());
                    return Mono.just(PayoutRequestMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Retry a single failed payout request.
     * Schema: retryPayoutRequest(payoutRequestId: ID!): PayoutRequestMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestMutationResponse> retryPayoutRequest(
            @InputArgument String payoutRequestId
    ) {
        log.info("Retrying payout request: {}", payoutRequestId);
        return payoutRecoveryService.bulkRetryFailedPayouts(List.of(payoutRequestId))
                .next()
                .map(p -> PayoutRequestMutationResponse.success(p, "Payout request queued for retry"))
                .switchIfEmpty(Mono.just(PayoutRequestMutationResponse.error("Payout request not found or not retryable")))
                .doOnSuccess(r -> log.info("Payout request {} retry result: {}", payoutRequestId, r.success()))
                .onErrorResume(e -> {
                    log.error("Failed to retry payout request {}: {}", payoutRequestId, e.getMessage());
                    return Mono.just(PayoutRequestMutationResponse.error(e.getMessage()));
                });
    }
}
