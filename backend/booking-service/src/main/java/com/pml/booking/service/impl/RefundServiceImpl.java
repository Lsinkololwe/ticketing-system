package com.pml.booking.service.impl;

import com.pml.shared.constants.PlatformTime;
import com.pml.booking.domain.RefundEligibility;
import com.pml.booking.service.BookingStore;
import com.pml.booking.domain.RefundSplit;
import com.pml.booking.domain.model.CommissionRecord;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;

import com.pml.booking.config.PaymentProperties;
import com.pml.booking.infrastructure.client.PawaPayClient;
import com.pml.booking.domain.model.RefundRequest;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.exception.ProviderUnavailableException;
import com.pml.booking.repository.RefundRequestRepository;
import com.pml.booking.repository.TicketRepository;
import com.pml.booking.service.AccountingService;
import com.pml.booking.service.CommissionService;
import com.pml.booking.service.EscrowService;
import com.pml.booking.service.PaymentAttemptRecorder;
import com.pml.booking.service.PaymentAttemptRecorder.CallOutcome;
import com.pml.booking.service.PaymentAttemptRecorder.ProviderCall;
import com.pml.booking.domain.enums.PaymentAttemptType;
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
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
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

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;
    private final TicketRepository ticketRepository;
    private final PawaPayClient pawaPayClient;
    private final CommissionService commissionService;
    private final EscrowService escrowService;
    private final AccountingService accountingService;
    private final PaymentProperties paymentProperties;
    private final TransactionalOperator transactionalOperator;
    private final ReactiveMongoTemplate mongoTemplate;
    private final PaymentAttemptRecorder attempts;

    /** The failure code {@link PawaPayClient}'s circuit-breaker fallback reports instead of an answer. */
    private static final String PROVIDER_UNREACHABLE = "CIRCUIT_BREAKER_OPEN";

    @Override
    @Transactional
    public Mono<RefundRequest> requestRefund(String ticketId, String reason, String requestedBy) {
        log.info("Processing refund request for ticket: {}", ticketId);

        return ticketRepository.findById(ticketId)
                // A buyer asks about their own seat. Anyone else, and the buyer of a seat since given
                // away, is told the ticket does not exist: the refund goes to whoever paid.
                .filter(ticket -> RefundEligibility.isHolder(ticket, requestedBy))
                .switchIfEmpty(Mono.error(new TranslatedRefusal(ErrorCode.TICKET_UNKNOWN, "no ticket " + ticketId)))
                .flatMap(ticket -> {
                    RefundEligibility.Verdict verdict = RefundEligibility.forHolder(ticket);
                    if (!verdict.eligible()) {
                        return Mono.error(verdict.refusal());
                    }

                    RefundRequest refundRequest = buildRefundRequest(ticket, verdict.remaining(), reason, requestedBy);
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
                    RefundEligibility.Verdict verdict = RefundEligibility.of(ticket);
                    if (!verdict.eligible()) {
                        return Mono.error(verdict.refusal());
                    }
                    Throwable invalid = RefundEligibility.checkAmount(amount, verdict.remaining());
                    if (invalid != null) {
                        return Mono.error(invalid);
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
                    refundRequest.setReviewedAt(clock.instant());
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
                    refundRequest.setReviewedAt(clock.instant());
                    refundRequest.setRejectionReason(reason);

                    return refundRequestRepository.save(refundRequest)
                            .doOnSuccess(rr -> log.info("Refund rejected: {}", rr.getId()));
                });
    }

    /**
     * The provider is called from outside this method's transaction. Commission and
     * escrow are the platform's own books, adjusted and committed first; only then is PawaPay
     * asked to move money, so a slow or failed call never holds that commit open and never rolls
     * back an adjustment PawaPay may already be acting on.
     */
    @Override
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

                    // Step 1: the split, the commission adjustment and the escrow debit commit together.
                    return settleInternally(refundRequest)
                            .as(transactionalOperator::transactional)
                            // Step 2: only after that commit does the provider get called.
                            .then(Mono.defer(() -> mongoTemplate.findOne(
                                    org.springframework.data.mongodb.core.query.Query.query(
                                            org.springframework.data.mongodb.core.query.Criteria.where("_id").is(refundRequestId)
                                                    .and("organizationId").is(refundRequest.getOrganizationId())),
                                    RefundRequest.class)))
                            .flatMap(this::initiatePayaPayRefund);
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
                    if ("COMPLETED".equals(status) && refundRequest.getStatus() == RefundRequestStatus.COMPLETED) {
                        // Already applied. The callback and the poll both report a completion, and the
                        // ticket and the booking must only be credited with it once.
                        return Mono.just(refundRequest);
                    }
                    refundRequest.setProviderTransactionId(providerTransactionId);

                    if ("COMPLETED".equals(status)) {
                        refundRequest.setStatus(RefundRequestStatus.COMPLETED);
                        refundRequest.setProcessedAt(clock.instant());

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
                                // The share this refund took, fixed when it was processed; a record that
                                // has since been reduced or closed would give a different answer.
                                .map(commission -> refundRequest.getCommissionShare() != null
                                        ? refundRequest.getCommissionShare() : commission.getAmount())
                                .defaultIfEmpty(BigDecimal.ZERO)
                                .flatMap(commissionClawback -> {

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
                                .flatMap(this::updateTicketForCompletedRefund);
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
    public Flux<RefundRequest> findAllByTicketId(String ticketId) {
        return refundRequestRepository.findByTicketId(ticketId)
                .sort(java.util.Comparator.comparing(RefundRequest::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
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
    public Mono<RefundRequest> findByRequestId(String requestId) {
        return refundRequestRepository.findByRequestId(requestId);
    }

    @Override
    public Flux<RefundRequest> findAll() {
        return refundRequestRepository.findAll();
    }

    @Override
    public Flux<RefundRequest> findPendingRefunds() {
        return refundRequestRepository.findByStatus(RefundRequestStatus.PENDING);
    }

    @Override
    public Mono<RefundCalculation> calculateRefundAmount(String ticketId) {
        log.info("Calculating refund amount for ticket: {}", ticketId);

        return ticketRepository.findById(ticketId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Ticket not found: " + ticketId)))
                .flatMap(ticket -> {
                    // Parse event date
                    Instant eventDate = parseEventDate(ticket.getEventDate());
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

    private Mono<RefundCalculation> calculateRefundBreakdown(Ticket ticket, Instant eventDate) {
        Instant now = clock.instant();
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
                policyDetails
        ));
    }

    private Instant parseEventDate(String eventDateStr) {
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
                    return PlatformTime.parseLocal(eventDateStr + "T00:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }
                return PlatformTime.parseLocal(eventDateStr, formatter);
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
        return createAdminRefundRequest(ticketId, reason, adminId, bypassApproval, null);
    }

    @Override
    @Transactional
    public Mono<RefundRequest> createAdminRefundRequest(
            String ticketId,
            String reason,
            String adminId,
            boolean bypassApproval,
            BigDecimal amount
    ) {
        log.info("Operator creating refund request for ticket: {} by: {} (bypass: {}, amount: {})",
                ticketId, adminId, bypassApproval, amount);

        return ticketRepository.findById(ticketId)
                .switchIfEmpty(Mono.error(new TranslatedRefusal(ErrorCode.TICKET_UNKNOWN, "no ticket " + ticketId)))
                .flatMap(ticket -> {
                    RefundEligibility.Verdict verdict = RefundEligibility.of(ticket);
                    if (!verdict.eligible()) {
                        return Mono.error(verdict.refusal());
                    }
                    BigDecimal refundAmount = amount == null ? verdict.remaining() : amount;
                    Throwable invalid = RefundEligibility.checkAmount(refundAmount, verdict.remaining());
                    if (invalid != null) {
                        return Mono.error(invalid);
                    }

                    RefundRequest refundRequest = buildRefundRequest(ticket, refundAmount, reason, adminId);
                    refundRequest.setRequestType(RefundRequestType.ADMIN_INITIATED);

                    if (bypassApproval) {
                        // Approved by the person raising it: the operator is the decision.
                        refundRequest.setStatus(RefundRequestStatus.APPROVED);
                        refundRequest.setReviewedBy(adminId);
                        refundRequest.setReviewedAt(clock.instant());
                        refundRequest.setReviewComments("Approved by the operator who raised it");
                    }

                    return refundRequestRepository.save(refundRequest)
                            .doOnSuccess(rr -> log.info("Operator refund request created: {} (status: {})",
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
                    refundRequest.setRejectionReason((cancelledBy != null && cancelledBy.equals(refundRequest.getRequestedBy())
                            ? "Cancelled by the requester: " : "Cancelled by staff: ") + reason);
                    refundRequest.setReviewedBy(cancelledBy);
                    refundRequest.setReviewedAt(clock.instant());

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
                            refundRequest.setReviewedAt(clock.instant());
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
        return RefundEligibility.of(ticket).eligible();
    }

    private RefundRequest buildRefundRequest(Ticket ticket, BigDecimal amount, String reason, String requestedBy) {
        return RefundRequest.builder()
                .ticketId(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .eventId(ticket.getEventId())
                .organizerId(ticket.getOrganizerId())
                .organizationId(ticket.getOrganizationId())
                // The refund returns to whoever paid, which after a transfer is not the holder.
                .buyerId(ticket.getOriginalBuyerId() != null ? ticket.getOriginalBuyerId() : ticket.getBuyerId())
                .requestId(generateRefundRequestId())
                .refundAmount(amount)
                .currency(ticket.getCurrency())
                .status(RefundRequestStatus.PENDING)
                .requestType(RefundRequestType.FULL)
                .requestReason(reason)
                .requestedBy(requestedBy)
                .requestedAt(clock.instant())
                .originalTicketPrice(ticket.getPrice())
                .originalPaymentTransactionId(ticket.getPaymentReference())
                .build();
    }

    /**
     * Fixes how this refund divides between commission and escrow — once, the first time through —
     * then applies it. A retry finds the split already stored and the adjustments already marked with
     * this refund's id, so it changes nothing a second time.
     */
    private Mono<Void> settleInternally(RefundRequest refundRequest) {
        if (refundRequest.getEscrowDebit() != null) {
            // The split is stored in the same transaction as the adjustments it describes, so a stored
            // split means they are applied. Recomputing it here would read a commission record the first
            // attempt already adjusted.
            return Mono.empty();
        }
        return commissionService.findByTicketId(refundRequest.getTicketId())
                .flatMap(commission -> {
                    RefundSplit split = RefundSplit.of(commission.getTicketPrice(), commission.getAmount(),
                            refundRequest.getRefundAmount());
                    return refundRequestRepository.save(withSplit(refundRequest, split))
                            .then(split.whole()
                                    ? handleCommissionAdjustment(refundRequest, commission)
                                    : commissionService.reduceForPartialRefund(refundRequest.getTicketId(),
                                            refundRequest.getId(), refundRequest.getRefundAmount(), split.commissionShare()).then())
                            .then(escrowService.debitForRefund(
                                    refundRequest.getEventId(),
                                    split.escrowDebit(),
                                    refundRequest.getTicketId(),
                                    refundRequest.getId(),
                                    "Refund: " + refundRequest.getTicketNumber()))
                            .then();
                });
    }

    private static RefundRequest withSplit(RefundRequest refundRequest, RefundSplit split) {
        refundRequest.setCommissionShare(split.commissionShare());
        refundRequest.setEscrowDebit(split.escrowDebit());
        return refundRequest;
    }

    private Mono<Void> handleCommissionAdjustment(RefundRequest refundRequest, CommissionRecord commission) {
        if (commission.isPending()) {
            // Commission not yet earned - just cancel it
            return commissionService.cancelPendingCommission(
                    refundRequest.getTicketId(),
                    refundRequest.getId(),
                    "Refund: " + refundRequest.getRequestReason()).then();
        }
        if (commission.isEarned()) {
            // Commission already earned - need to clawback (rare)
            return commissionService.clawbackEarnedCommission(
                    refundRequest.getTicketId(),
                    refundRequest.getId(),
                    "Refund after event: " + refundRequest.getRequestReason()).then();
        }
        return Mono.empty();
    }

    /**
     * Sends the refund to PawaPay under a refund id that is minted once and stored before the call.
     *
     * <p>Storing the id first means a retry after a crash, a timeout or a failed save sends the same
     * id again, and PawaPay treats the repeat as the refund it already has instead of paying twice.
     * When PawaPay cannot be reached at all the call fails with {@link ProviderUnavailableException},
     * so the caller retries with the same id rather than recording a refusal PawaPay never gave.
     */
    private Mono<RefundRequest> initiatePayaPayRefund(RefundRequest refundRequest) {
        return ticketRepository.findById(refundRequest.getTicketId())
                .flatMap(ticket -> {
                    String depositId = refundRequest.getPawaPayDepositId();
                    if (depositId == null && ticket.getPaymentInfo() != null) {
                        depositId = ticket.getPaymentInfo().getTransactionId();
                    }
                    if (depositId == null) {
                        return Mono.error(new IllegalStateException(
                                "Cannot process refund: original deposit ID not found"));
                    }
                    return withRefundId(refundRequest.getId(), depositId)
                            .flatMap(stored -> attempts.beforeCall(new ProviderCall(PaymentAttemptType.REFUND,
                                            stored.getPawaPayRefundId(), stored.getRefundAmount(), stored.getCurrency(),
                                            stored.getEventId(), stored.getOrganizationId(), stored.getId(), null, null))
                                    .then(pawaPayClient.initiateRefund(
                                            stored.getPawaPayRefundId(),
                                            stored.getPawaPayDepositId(),
                                            stored.getRefundAmount(),
                                            stored.getCurrency(),
                                            Map.of("ticketId", stored.getTicketId(), "refundRequestId", stored.getId())))
                                    .flatMap(response -> recordRefundResponse(stored, response)));
                });
    }

    /**
     * Gives the refund its PawaPay id if it has none, and returns the stored request, in one atomic
     * update: {@code $ifNull} keeps an id already written, so concurrent callers all read back the
     * first id stored and never overwrite it.
     */
    private Mono<RefundRequest> withRefundId(String refundRequestId, String depositId) {
        AggregationUpdate assignOnce = AggregationUpdate.update()
                .set("pawaPayRefundId").toValue(
                        ConditionalOperators.ifNull("pawaPayRefundId").then(PawaPayClient.generateTransactionId()))
                .set("pawaPayDepositId").toValue(ConditionalOperators.ifNull("pawaPayDepositId").then(depositId));
        return mongoTemplate.findAndModify(Query.query(Criteria.where("_id").is(refundRequestId)), assignOnce,
                FindAndModifyOptions.options().returnNew(true), RefundRequest.class);
    }

    /**
     * Applies PawaPay's answer. ACCEPTED, and DUPLICATE_IGNORED for an id PawaPay already holds, both
     * mean the refund is with PawaPay; REJECTED fails it. A circuit-breaker fallback is not an answer
     * from PawaPay and is raised as {@link ProviderUnavailableException}.
     */
    private Mono<RefundRequest> recordRefundResponse(RefundRequest refund, PawaPayClient.RefundResponse response) {
        if (response.failureReason() != null && PROVIDER_UNREACHABLE.equals(response.failureReason().failureCode())) {
            return Mono.error(new ProviderUnavailableException(
                    "PawaPay could not be reached for refund " + refund.getId() + "; it is sent again with the same id"));
        }
        boolean withProvider = response.isAccepted() || "DUPLICATE_IGNORED".equals(response.status());
        refund.setStatus(withProvider ? RefundRequestStatus.PROCESSING : RefundRequestStatus.FAILED);
        String failureCode = response.failureReason() != null ? response.failureReason().failureCode() : null;
        String failureMessage = response.failureReason() != null ? response.failureReason().failureMessage() : null;
        if (!withProvider) {
            refund.setRejectionReason(failureMessage);
        }
        return attempts.afterCall(refund.getPawaPayRefundId(), withProvider ? CallOutcome.ACCEPTED : CallOutcome.REFUSED,
                        failureCode, failureMessage)
                .then(refundRequestRepository.save(refund));
    }

    /**
     * Credits the seat and its booking with a refund that has completed.
     *
     * <p>A seat is {@code REFUNDED} only once the refunds that completed add up to its price; a part
     * refund leaves it as it was — still admissible, still the holder's — with the sum recorded. The
     * booking is told the same amount so its status ({@code PARTIALLY_REFUNDED}, {@code REFUNDED})
     * is read from money that really came back.
     */
    private Mono<RefundRequest> updateTicketForCompletedRefund(RefundRequest refundRequest) {
        return ticketRepository.findById(refundRequest.getTicketId())
                .flatMap(ticket -> {
                    BigDecimal previous = ticket.getRefundedAmount() == null ? BigDecimal.ZERO : ticket.getRefundedAmount();
                    BigDecimal refunded = previous.add(refundRequest.getRefundAmount());
                    ticket.setRefundedAmount(refunded);
                    if (refunded.compareTo(ticket.getPrice()) >= 0) {
                        ticket.setStatus(TicketStatus.REFUNDED);
                        ticket.setRefundedAt(clock.instant());
                        ticket.setRefundReason(refundRequest.getRequestReason());
                    }

                    Ticket.RefundInfo refundInfo = Ticket.RefundInfo.builder()
                            .refundId(refundRequest.getId())
                            .refundAmount(refundRequest.getRefundAmount())
                            .reason(refundRequest.getRequestReason())
                            .status(com.pml.shared.constants.TicketRefundStatus.COMPLETED)
                            .refundDate(clock.instant())
                            .processedBy(refundRequest.getReviewedBy() != null ? refundRequest.getReviewedBy() : "SYSTEM")
                            .build();
                    ticket.setRefundInfo(refundInfo);

                    return ticketRepository.save(ticket)
                            .then(BookingStore.refundCompleted(mongoTemplate, ticket.getReservationId(),
                                    refundRequest.getRefundAmount()));
                })
                .thenReturn(refundRequest);
    }

    private String generateRefundRequestId() {
        return "RFD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
