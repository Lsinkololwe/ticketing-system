package com.pml.booking.service.impl;

import com.pml.booking.config.PaymentProperties;
import com.pml.booking.infrastructure.client.PawaPayClient;
import com.pml.booking.event.domain.RefundCompletedEvent;
import com.pml.booking.domain.model.CommissionRecord;
import com.pml.booking.domain.model.RefundRequest;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.repository.RefundRequestRepository;
import com.pml.booking.repository.TicketRepository;
import com.pml.booking.service.AccountingService;
import com.pml.booking.service.CommissionService;
import com.pml.booking.service.EscrowService;
import com.pml.booking.service.RefundService;
import com.pml.booking.web.graphql.dto.RefundCalculation;
import com.pml.booking.web.graphql.dto.BulkOperationResponse;
import com.pml.shared.constants.RefundRequestStatus;
import com.pml.shared.constants.RefundRequestType;
import com.pml.shared.constants.TicketStatus;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Refund Service Implementation
 *
 * Handles refund requests with integration to pawaPay for mobile money refunds.
 * Coordinates with Commission and Escrow services for proper financial adjustments.
 *
 * Refund Financial Flow:
 * 1. If commission is PENDING → Cancel commission (no money movement)
 * 2. If commission is EARNED → Clawback commission (rare)
 * 3. Debit escrow account for the refund amount
 * 4. Process refund via pawaPay
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final RefundRequestRepository refundRequestRepository;
    private final TicketRepository ticketRepository;
    private final PawaPayClient pawaPayClient;
    private final CommissionService commissionService;
    private final EscrowService escrowService;
    private final AccountingService accountingService;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentProperties paymentProperties;

    @Override
    @Transactional
    public Mono<RefundRequest> requestRefund(String ticketId, String reason, String requestedBy) {
        log.info("Processing refund request for ticket: {}", ticketId);

        return ticketRepository.findById(ticketId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Ticket not found: " + ticketId)))
                .flatMap(ticket -> {
                    if (!isRefundable(ticket)) {
                        return Mono.error(new IllegalStateException(
                                "Ticket is not eligible for refund. Status: " + ticket.getStatus()));
                    }

                    RefundRequest refundRequest = buildRefundRequest(ticket, ticket.getPrice(), reason, requestedBy);
                    return refundRequestRepository.save(refundRequest)
                            .doOnSuccess(rr -> log.info("Refund request created: {} for ticket: {}",
                                    rr.getId(), ticketId));
                });
    }

    @Override
    @Transactional
    public Mono<RefundRequest> requestPartialRefund(
            String ticketId,
            BigDecimal amount,
            String reason,
            String requestedBy
    ) {
        log.info("Processing partial refund request for ticket: {}, amount: {}", ticketId, amount);

        return ticketRepository.findById(ticketId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Ticket not found: " + ticketId)))
                .flatMap(ticket -> {
                    if (!isRefundable(ticket)) {
                        return Mono.error(new IllegalStateException("Ticket is not eligible for refund"));
                    }
                    if (amount.compareTo(ticket.getPrice()) > 0) {
                        return Mono.error(new IllegalArgumentException(
                                "Refund amount cannot exceed ticket price"));
                    }

                    RefundRequest refundRequest = buildRefundRequest(ticket, amount, reason, requestedBy);
                    refundRequest.setRequestType(RefundRequestType.PARTIAL);
                    return refundRequestRepository.save(refundRequest);
                });
    }

    @Override
    @Transactional
    public Mono<RefundRequest> approveRefund(String refundRequestId, String approvedBy, String comments) {
        log.info("Approving refund request: {}", refundRequestId);

        return refundRequestRepository.findById(refundRequestId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Refund request not found")))
                .flatMap(refundRequest -> {
                    if (refundRequest.getStatus() != RefundRequestStatus.PENDING) {
                        return Mono.error(new IllegalStateException(
                                "Can only approve pending refunds. Current: " + refundRequest.getStatus()));
                    }

                    refundRequest.setStatus(RefundRequestStatus.APPROVED);
                    refundRequest.setReviewedBy(approvedBy);
                    refundRequest.setReviewedAt(LocalDateTime.now());
                    refundRequest.setReviewComments(comments);

                    return refundRequestRepository.save(refundRequest)
                            .doOnSuccess(rr -> log.info("Refund approved: {}", rr.getId()));
                });
    }

    @Override
    @Transactional
    public Mono<RefundRequest> rejectRefund(String refundRequestId, String rejectedBy, String reason) {
        log.info("Rejecting refund request: {}", refundRequestId);

        return refundRequestRepository.findById(refundRequestId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Refund request not found")))
                .flatMap(refundRequest -> {
                    if (refundRequest.getStatus() != RefundRequestStatus.PENDING) {
                        return Mono.error(new IllegalStateException("Can only reject pending refunds"));
                    }

                    refundRequest.setStatus(RefundRequestStatus.REJECTED);
                    refundRequest.setReviewedBy(rejectedBy);
                    refundRequest.setReviewedAt(LocalDateTime.now());
                    refundRequest.setRejectionReason(reason);

                    return refundRequestRepository.save(refundRequest)
                            .doOnSuccess(rr -> log.info("Refund rejected: {}", rr.getId()));
                });
    }

    @Override
    @Transactional
    public Mono<RefundRequest> processRefund(String refundRequestId) {
        log.info("Processing refund: {}", refundRequestId);

        return refundRequestRepository.findById(refundRequestId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Refund request not found")))
                .flatMap(refundRequest -> {
                    if (refundRequest.getStatus() != RefundRequestStatus.APPROVED &&
                            refundRequest.getStatus() != RefundRequestStatus.PENDING) {
                        return Mono.error(new IllegalStateException(
                                "Refund must be approved before processing. Status: " + refundRequest.getStatus()));
                    }

                    // Step 1: Handle commission adjustment
                    return handleCommissionAdjustment(refundRequest)
                            .then(handleEscrowDebit(refundRequest))
                            .then(initiatePayaPayRefund(refundRequest));
                });
    }

    @Override
    @Transactional
    public Mono<RefundRequest> handleRefundCallback(
            String pawaPayRefundId,
            String status,
            String providerTransactionId,
            String failureCode,
            String failureMessage
    ) {
        log.info("Handling refund callback: {}, status: {}", pawaPayRefundId, status);

        return refundRequestRepository.findByPawaPayRefundId(pawaPayRefundId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Refund request not found for pawaPay ID: " + pawaPayRefundId)))
                .flatMap(refundRequest -> {
                    refundRequest.setProviderTransactionId(providerTransactionId);

                    if ("COMPLETED".equals(status)) {
                        refundRequest.setStatus(RefundRequestStatus.COMPLETED);
                        refundRequest.setProcessedAt(LocalDateTime.now());

                        /*
                         * ACCOUNTING ENTRIES for completed refund:
                         *
                         * Step 1: Record the refund (creates refund payable)
                         *   DR Event Escrow (2010-XXX)     - OUT: organizer's money reduced
                         *   DR Deferred Commission (2031)  - OUT: commission clawed back
                         *      CR Customer Refunds Payable (2022)  - IN: we now owe customer
                         *
                         * Step 2: Record the disbursement (clears refund payable)
                         *   DR Customer Refunds Payable (2022)  - OUT: liability cleared
                         *      CR Operating Bank Account (1011)        - OUT: money left bank
                         *
                         * These two entries happen in quick succession when gateway
                         * confirms the refund was sent to the customer.
                         */
                        return commissionService.findByTicketId(refundRequest.getTicketId())
                                .flatMap(commission -> {
                                    BigDecimal commissionClawback = commission != null ?
                                            commission.getAmount() : BigDecimal.ZERO;

                                    // Step 1: Record refund - creates Refunds Payable liability
                                    return accountingService.recordRefund(
                                            refundRequest.getId(),
                                            refundRequest.getOriginalPaymentTransactionId(),
                                            refundRequest.getTicketId(),
                                            refundRequest.getEventId(),
                                            refundRequest.getRefundAmount(),
                                            commissionClawback,
                                            refundRequest.getCurrency()
                                    )
                                    // Step 2: Record disbursement - clears the payable, debits bank
                                    .then(accountingService.recordRefundDisbursement(
                                            refundRequest.getId(),
                                            refundRequest.getRefundAmount(),
                                            providerTransactionId != null ? providerTransactionId : pawaPayRefundId,
                                            refundRequest.getCurrency()
                                    ));
                                })
                                .then(refundRequestRepository.save(refundRequest))
                                .flatMap(this::updateTicketForCompletedRefund)
                                .doOnSuccess(this::publishRefundCompletedEvent);
                    } else if ("FAILED".equals(status)) {
                        refundRequest.setStatus(RefundRequestStatus.FAILED);
                        refundRequest.setRejectionReason(failureMessage);

                        return refundRequestRepository.save(refundRequest)
                                .doOnSuccess(rr -> log.error("Refund failed: {}, code: {}, message: {}",
                                        rr.getId(), failureCode, failureMessage));
                    } else {
                        return refundRequestRepository.save(refundRequest);
                    }
                });
    }

    @Override
    public Mono<RefundRequest> findById(String id) {
        return refundRequestRepository.findById(id);
    }

    @Override
    public Mono<RefundRequest> findByTicketId(String ticketId) {
        return refundRequestRepository.findByTicketId(ticketId).next();
    }

    @Override
    public Flux<RefundRequest> findByEventId(String eventId) {
        return refundRequestRepository.findByEventId(eventId);
    }

    @Override
    public Flux<RefundRequest> findByBuyerId(String buyerId) {
        return refundRequestRepository.findByBuyerId(buyerId);
    }

    @Override
    public Flux<RefundRequest> findPendingRefunds() {
        return refundRequestRepository.findByStatus(RefundRequestStatus.PENDING);
    }

    @Override
    public Flux<RefundRequest> findApprovedRefundsPendingProcessing() {
        return refundRequestRepository.findByStatus(RefundRequestStatus.APPROVED);
    }

    @Override
    @Transactional
    public Mono<Long> processAutomaticRefunds(String eventId) {
        log.info("Processing automatic refunds for event: {}", eventId);

        return refundRequestRepository.findByEventIdAndIsAutomatic(eventId, true)
                .filter(rr -> rr.getStatus() == RefundRequestStatus.PENDING)
                .flatMap(rr -> {
                    rr.setStatus(RefundRequestStatus.APPROVED);
                    rr.setReviewComments("Auto-approved for event cancellation");
                    return refundRequestRepository.save(rr)
                            .flatMap(saved -> processRefund(saved.getId()));
                })
                .count()
                .doOnSuccess(count -> log.info("Processed {} automatic refunds for event: {}", count, eventId));
    }

    @Override
    public Mono<RefundCalculation> calculateRefundAmount(String ticketId) {
        log.info("Calculating refund amount for ticket: {}", ticketId);

        return ticketRepository.findById(ticketId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Ticket not found: " + ticketId)))
                .flatMap(ticket -> {
                    // Parse event date
                    LocalDateTime eventDate = parseEventDate(ticket.getEventDate());
                    if (eventDate == null) {
                        return Mono.just(RefundCalculation.ineligible(
                                ticketId,
                                ticket.getTicketNumber(),
                                ticket.getEventId(),
                                null,
                                ticket.getPrice(),
                                "Unable to determine event date"
                        ));
                    }

                    // Check ticket status eligibility
                    if (!isRefundable(ticket)) {
                        return Mono.just(RefundCalculation.ineligible(
                                ticketId,
                                ticket.getTicketNumber(),
                                ticket.getEventId(),
                                eventDate,
                                ticket.getPrice(),
                                "Ticket status '" + ticket.getStatus() + "' is not eligible for refund"
                        ));
                    }

                    // Check for existing pending/processing refund
                    return refundRequestRepository.findByTicketId(ticketId)
                            .filter(rr -> rr.getStatus() == RefundRequestStatus.PENDING ||
                                          rr.getStatus() == RefundRequestStatus.APPROVED ||
                                          rr.getStatus() == RefundRequestStatus.PROCESSING)
                            .hasElements()
                            .flatMap(hasPendingRefund -> {
                                if (hasPendingRefund) {
                                    return Mono.just(RefundCalculation.ineligible(
                                            ticketId,
                                            ticket.getTicketNumber(),
                                            ticket.getEventId(),
                                            eventDate,
                                            ticket.getPrice(),
                                            "A refund request is already pending for this ticket"
                                    ));
                                }

                                return calculateRefundBreakdown(ticket, eventDate);
                            });
                });
    }

    private Mono<RefundCalculation> calculateRefundBreakdown(Ticket ticket, LocalDateTime eventDate) {
        LocalDateTime now = LocalDateTime.now();
        long daysBeforeEvent = ChronoUnit.DAYS.between(now, eventDate);
        long hoursBeforeEvent = ChronoUnit.HOURS.between(now, eventDate);

        PaymentProperties.Refund refundConfig = paymentProperties.getRefund();
        BigDecimal originalAmount = ticket.getPrice();

        // Check if event has passed
        if (daysBeforeEvent < 0) {
            if (!refundConfig.isAllowPostEventRefund()) {
                return Mono.just(RefundCalculation.ineligible(
                        ticket.getId(),
                        ticket.getTicketNumber(),
                        ticket.getEventId(),
                        eventDate,
                        originalAmount,
                        "Refunds are not allowed after the event has ended"
                ));
            }
        }

        // Check cutoff time
        if (hoursBeforeEvent >= 0 && hoursBeforeEvent < refundConfig.getCutoffHoursBeforeEvent()) {
            return Mono.just(RefundCalculation.ineligible(
                    ticket.getId(),
                    ticket.getTicketNumber(),
                    ticket.getEventId(),
                    eventDate,
                    originalAmount,
                    String.format("Refunds must be requested at least %d hours before the event",
                            refundConfig.getCutoffHoursBeforeEvent())
            ));
        }

        // Calculate refund amounts
        float refundPercentage = 100.0f; // Full refund if within policy
        BigDecimal processingFeeRate = refundConfig.getProcessingFeeRate();
        BigDecimal processingFee = originalAmount.multiply(processingFeeRate)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal refundAmount = originalAmount.subtract(processingFee);

        // Get commission information
        BigDecimal commissionAmount = ticket.getCommissionAmount() != null ?
                ticket.getCommissionAmount() : BigDecimal.ZERO;

        // Commission refund: If commission is still PENDING, it will be cancelled (not clawed back)
        // The commissionRefund represents what was collected as commission and will be cancelled
        BigDecimal commissionRefund = commissionAmount;

        // Platform retains the processing fee
        BigDecimal platformRetains = processingFee;

        // Build policy details string
        String policyDetails = buildPolicyDetails(daysBeforeEvent, refundConfig);

        return Mono.just(RefundCalculation.eligible(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getEventId(),
                eventDate,
                originalAmount,
                (int) Math.max(0, daysBeforeEvent),
                refundPercentage,
                refundAmount,
                commissionRefund,
                platformRetains,
                processingFee,
                policyDetails
        ));
    }

    private LocalDateTime parseEventDate(String eventDateStr) {
        if (eventDateStr == null || eventDateStr.isBlank()) {
            return null;
        }

        // Try multiple common date formats
        DateTimeFormatter[] formatters = {
                DateTimeFormatter.ISO_DATE_TIME,
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd")
        };

        for (DateTimeFormatter formatter : formatters) {
            try {
                if (formatter.equals(DateTimeFormatter.ofPattern("yyyy-MM-dd"))) {
                    // For date-only format, assume start of day
                    return LocalDateTime.parse(eventDateStr + "T00:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }
                return LocalDateTime.parse(eventDateStr, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next format
            }
        }

        log.warn("Unable to parse event date: {}", eventDateStr);
        return null;
    }

    // ========================================================================
    // ADMIN REFUND OPERATIONS
    // ========================================================================

    @Override
    @Transactional
    public Mono<RefundRequest> createAdminRefundRequest(
            String ticketId,
            String reason,
            String adminId,
            boolean bypassApproval
    ) {
        log.info("Admin creating refund request for ticket: {} by: {} (bypass: {})", ticketId, adminId, bypassApproval);

        return ticketRepository.findById(ticketId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Ticket not found: " + ticketId)))
                .flatMap(ticket -> {
                    if (!isRefundable(ticket)) {
                        return Mono.error(new IllegalStateException(
                                "Ticket is not eligible for refund. Status: " + ticket.getStatus()));
                    }

                    RefundRequest refundRequest = buildRefundRequest(ticket, ticket.getPrice(), reason, adminId);
                    refundRequest.setRequestType(RefundRequestType.ADMIN_INITIATED);

                    if (bypassApproval) {
                        // Auto-approve if bypass requested
                        refundRequest.setStatus(RefundRequestStatus.APPROVED);
                        refundRequest.setReviewedBy(adminId);
                        refundRequest.setReviewedAt(LocalDateTime.now());
                        refundRequest.setReviewComments("Admin bypass approval");
                    }

                    return refundRequestRepository.save(refundRequest)
                            .doOnSuccess(rr -> log.info("Admin refund request created: {} (status: {})",
                                    rr.getId(), rr.getStatus()));
                });
    }

    @Override
    @Transactional
    public Mono<RefundRequest> cancelRefundRequest(
            String refundRequestId,
            String cancelledBy,
            String reason
    ) {
        log.info("Cancelling refund request: {} by: {} reason: {}", refundRequestId, cancelledBy, reason);

        return refundRequestRepository.findById(refundRequestId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Refund request not found: " + refundRequestId)))
                .flatMap(refundRequest -> {
                    // Only PENDING or APPROVED (not yet processed) can be cancelled
                    if (refundRequest.getStatus() != RefundRequestStatus.PENDING &&
                        refundRequest.getStatus() != RefundRequestStatus.APPROVED) {
                        return Mono.error(new IllegalStateException(
                                "Cannot cancel refund request with status: " + refundRequest.getStatus()));
                    }

                    refundRequest.setStatus(RefundRequestStatus.CANCELLED);
                    refundRequest.setRejectionReason("Cancelled by admin: " + reason);
                    refundRequest.setReviewedBy(cancelledBy);
                    refundRequest.setReviewedAt(LocalDateTime.now());

                    return refundRequestRepository.save(refundRequest)
                            .doOnSuccess(rr -> log.info("Refund request cancelled: {}", rr.getId()));
                });
    }

    @Override
    @Transactional
    public Mono<BulkOperationResponse> bulkApproveRefunds(List<String> refundRequestIds, String reviewerId) {
        log.info("Bulk approving {} refund requests by: {}", refundRequestIds.size(), reviewerId);

        List<String> errors = new ArrayList<>();

        return Flux.fromIterable(refundRequestIds)
                .flatMap(refundRequestId ->
                    refundRequestRepository.findById(refundRequestId)
                        .flatMap(refundRequest -> {
                            if (refundRequest.getStatus() != RefundRequestStatus.PENDING) {
                                errors.add("Refund request " + refundRequestId +
                                        " cannot be approved (status: " + refundRequest.getStatus() + ")");
                                return Mono.just(false);
                            }

                            refundRequest.setStatus(RefundRequestStatus.APPROVED);
                            refundRequest.setReviewedBy(reviewerId);
                            refundRequest.setReviewedAt(LocalDateTime.now());
                            refundRequest.setReviewComments("Bulk approved");

                            return refundRequestRepository.save(refundRequest)
                                    .map(rr -> true)
                                    .onErrorResume(e -> {
                                        errors.add("Failed to approve refund request " + refundRequestId +
                                                ": " + e.getMessage());
                                        return Mono.just(false);
                                    });
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            errors.add("Refund request not found: " + refundRequestId);
                            return Mono.just(false);
                        }))
                )
                .collectList()
                .map(results -> {
                    int processedCount = (int) results.stream().filter(b -> b).count();
                    int failedCount = results.size() - processedCount;

                    String message = String.format("Bulk approve completed: %d approved, %d failed",
                            processedCount, failedCount);

                    log.info(message);

                    return BulkOperationResponse.partial(message, processedCount, failedCount, errors);
                });
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    private String buildPolicyDetails(long daysBeforeEvent, PaymentProperties.Refund refundConfig) {
        StringBuilder details = new StringBuilder();

        if (daysBeforeEvent > 7) {
            details.append("Full refund available (").append(daysBeforeEvent).append(" days before event). ");
        } else if (daysBeforeEvent > 0) {
            details.append("Refund available (").append(daysBeforeEvent).append(" days before event). ");
        } else {
            details.append("Post-event refund policy applies. ");
        }

        if (refundConfig.getProcessingFeeRate().compareTo(BigDecimal.ZERO) > 0) {
            details.append("Processing fee of ")
                    .append(refundConfig.getProcessingFeeRate().multiply(BigDecimal.valueOf(100)))
                    .append("% applies.");
        }

        if (refundConfig.isRequireApproval()) {
            details.append(" Refund requires approval.");
        }

        return details.toString().trim();
    }

    private boolean isRefundable(Ticket ticket) {
        return ticket.getStatus() == TicketStatus.PURCHASED ||
                ticket.getStatus() == TicketStatus.CONFIRMED ||
                ticket.getStatus() == TicketStatus.VALIDATED;
    }

    private RefundRequest buildRefundRequest(Ticket ticket, BigDecimal amount, String reason, String requestedBy) {
        return RefundRequest.builder()
                .ticketId(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .eventId(ticket.getEventId())
                .buyerId(ticket.getBuyerId())
                .requestId(generateRefundRequestId())
                .refundAmount(amount)
                .currency(ticket.getCurrency())
                .status(RefundRequestStatus.PENDING)
                .requestType(RefundRequestType.FULL)
                .requestReason(reason)
                .requestedBy(requestedBy)
                .requestedAt(Instant.now())
                .originalTicketPrice(ticket.getPrice())
                .originalPaymentTransactionId(ticket.getPaymentReference())
                .build();
    }

    private Mono<Void> handleCommissionAdjustment(RefundRequest refundRequest) {
        return commissionService.findByTicketId(refundRequest.getTicketId())
                .flatMap(commission -> {
                    if (commission.isPending()) {
                        // Commission not yet earned - just cancel it
                        return commissionService.cancelPendingCommission(
                                refundRequest.getTicketId(),
                                refundRequest.getId(),
                                "Refund: " + refundRequest.getRequestReason()
                        ).then();
                    } else if (commission.isEarned()) {
                        // Commission already earned - need to clawback (rare)
                        return commissionService.clawbackEarnedCommission(
                                refundRequest.getTicketId(),
                                refundRequest.getId(),
                                "Refund after event: " + refundRequest.getRequestReason()
                        ).then();
                    }
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> handleEscrowDebit(RefundRequest refundRequest) {
        // Debit the net amount (excluding commission) from escrow
        return commissionService.findByTicketId(refundRequest.getTicketId())
                .flatMap(commission -> {
                    BigDecimal netAmount = commission.getTicketPrice().subtract(commission.getAmount());
                    return escrowService.debitForRefund(
                            refundRequest.getEventId(),
                            netAmount,
                            refundRequest.getTicketId(),
                            refundRequest.getId(),
                            "Refund: " + refundRequest.getTicketNumber()
                    );
                })
                .then();
    }

    private Mono<RefundRequest> initiatePayaPayRefund(RefundRequest refundRequest) {
        String pawaPayRefundId = PawaPayClient.generateTransactionId();

        // Get the original deposit ID from the ticket's payment
        return ticketRepository.findById(refundRequest.getTicketId())
                .flatMap(ticket -> {
                    // The deposit ID should be stored in the payment info
                    String depositId = refundRequest.getPawaPayDepositId();
                    if (depositId == null && ticket.getPaymentInfo() != null) {
                        depositId = ticket.getPaymentInfo().getTransactionId();
                    }

                    if (depositId == null) {
                        return Mono.error(new IllegalStateException(
                                "Cannot process refund: original deposit ID not found"));
                    }

                    final String finalDepositId = depositId;
                    return pawaPayClient.initiateRefund(
                            pawaPayRefundId,
                            finalDepositId,
                            refundRequest.getRefundAmount(),
                            refundRequest.getCurrency(),
                            Map.of(
                                    "ticketId", refundRequest.getTicketId(),
                                    "refundRequestId", refundRequest.getId()
                            )
                    ).flatMap(response -> {
                        refundRequest.setPawaPayRefundId(pawaPayRefundId);
                        refundRequest.setPawaPayDepositId(finalDepositId);
                        refundRequest.setStatus(
                                response.isAccepted() ?
                                        RefundRequestStatus.PROCESSING :
                                        RefundRequestStatus.FAILED
                        );

                        if (!response.isAccepted() && response.failureReason() != null) {
                            refundRequest.setRejectionReason(response.failureReason().failureMessage());
                        }

                        return refundRequestRepository.save(refundRequest);
                    });
                });
    }

    private Mono<RefundRequest> updateTicketForCompletedRefund(RefundRequest refundRequest) {
        return ticketRepository.findById(refundRequest.getTicketId())
                .flatMap(ticket -> {
                    ticket.setStatus(TicketStatus.REFUNDED);
                    ticket.setRefundedAt(LocalDateTime.now());
                    ticket.setRefundReason(refundRequest.getRequestReason());

                    Ticket.RefundInfo refundInfo = Ticket.RefundInfo.builder()
                            .refundId(refundRequest.getId())
                            .refundAmount(refundRequest.getRefundAmount())
                            .reason(refundRequest.getRequestReason())
                            .status(com.pml.shared.constants.TicketRefundStatus.COMPLETED)
                            .refundDate(LocalDateTime.now())
                            .processedBy("SYSTEM")
                            .build();
                    ticket.setRefundInfo(refundInfo);

                    return ticketRepository.save(ticket);
                })
                .thenReturn(refundRequest);
    }

    private void publishRefundCompletedEvent(RefundRequest refundRequest) {
        BigDecimal processingFee = refundRequest.getProcessingFee() != null ?
                refundRequest.getProcessingFee() : BigDecimal.ZERO;
        BigDecimal netRefundAmount = refundRequest.getNetRefundAmount() != null ?
                refundRequest.getNetRefundAmount() : refundRequest.getRefundAmount();

        RefundCompletedEvent event = new RefundCompletedEvent(
                refundRequest.getId(),
                refundRequest.getTicketId(),
                refundRequest.getTicketNumber(),
                refundRequest.getEventId(),
                refundRequest.getBuyerId(),
                refundRequest.getOrganizerId(),
                refundRequest.getOriginalTicketPrice(),
                refundRequest.getRefundAmount(),
                processingFee,
                netRefundAmount,
                BigDecimal.ZERO, // commissionAmount - will be set by listener
                "PENDING", // commissionAction - determined by timing
                refundRequest.getRequestType() != null ? refundRequest.getRequestType().name() : "USER_REQUESTED",
                refundRequest.getRequestReason(),
                refundRequest.getProviderTransactionId()
        );
        eventPublisher.publishEvent(event);
        log.info("Published RefundCompletedEvent for ticket: {}", refundRequest.getTicketId());
    }

    private String generateRefundRequestId() {
        return "RFD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
