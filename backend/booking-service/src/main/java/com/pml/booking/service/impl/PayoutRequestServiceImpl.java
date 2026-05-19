package com.pml.booking.service.impl;

import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.repository.BankAccountRepository;
import com.pml.booking.repository.PayoutRequestRepository;
import com.pml.booking.service.AccountingService;
import com.pml.booking.service.PayoutRequestService;
import com.pml.booking.web.graphql.dto.CreatePayoutRequestInput;
import com.pml.shared.constants.PayoutRequestStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Implementation of {@link PayoutRequestService}.
 *
 * <p>Centralizes all payout request business logic previously scattered across
 * PayoutRequestQueryResolver and PayoutRequestMutationResolver.</p>
 *
 * @author Booking Service Team
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutRequestServiceImpl implements PayoutRequestService {

    private final PayoutRequestRepository payoutRequestRepository;
    private final BankAccountRepository bankAccountRepository;
    private final AccountingService accountingService;

    // ========================================================================
    // QUERY METHODS
    // ========================================================================

    @Override
    public Mono<PayoutRequest> findById(String id) {
        log.debug("Finding payout request by ID: {}", id);
        return payoutRequestRepository.findById(id);
    }

    @Override
    public Mono<PayoutRequest> findByRequestId(String requestId) {
        log.debug("Finding payout request by request ID: {}", requestId);
        return payoutRequestRepository.findByRequestId(requestId);
    }

    @Override
    public Flux<PayoutRequest> findByOrganizerId(String organizerId) {
        log.debug("Finding payout requests for organizer: {}", organizerId);
        return payoutRequestRepository.findByOrganizerId(organizerId);
    }

    @Override
    public Flux<PayoutRequest> findByStatus(PayoutRequestStatus status) {
        log.debug("Finding payout requests by status: {}", status);
        return payoutRequestRepository.findByStatus(status);
    }

    @Override
    public Flux<PayoutRequest> findByEventId(String eventId) {
        log.debug("Finding payout requests for event: {}", eventId);
        return payoutRequestRepository.findByEventId(eventId);
    }

    @Override
    public Flux<PayoutRequest> findAll() {
        log.debug("Finding all payout requests");
        return payoutRequestRepository.findAll();
    }

    @Override
    public Flux<PayoutRequest> findRetryable(int maxRetries) {
        log.debug("Finding retryable payout requests with max retries: {}", maxRetries);
        return payoutRequestRepository.findByStatusAndRetryCountLessThan(PayoutRequestStatus.FAILED, maxRetries);
    }

    @Override
    public Flux<PayoutRequest> findResolvedAfter(LocalDateTime since) {
        log.debug("Finding payout requests resolved after: {}", since);
        return payoutRequestRepository.findByResolvedAtAfter(since);
    }

    @Override
    public Mono<Long> countByIssueType(String issueType) {
        log.debug("Counting payout requests by issue type: {}", issueType);
        return payoutRequestRepository.countByIssueType(issueType);
    }

    // ========================================================================
    // MUTATION METHODS
    // ========================================================================

    @Override
    public Mono<PayoutRequest> create(CreatePayoutRequestInput input, String requestedBy) {
        log.info("Creating payout request for organizer: {} by: {}", input.organizerId(), requestedBy);

        return bankAccountRepository.findById(input.bankAccountId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Bank account not found: " + input.bankAccountId())))
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
                            .requestedBy(requestedBy)
                            .notes(input.notes())
                            .metadata(input.metadata())
                            .build();

                    return payoutRequestRepository.save(payoutRequest);
                })
                .doOnSuccess(saved -> log.info("Payout request created: {}", saved.getRequestId()));
    }

    @Override
    public Mono<PayoutRequest> approve(String id, String approverId, String notes) {
        log.info("Approving payout request: {} by: {}", id, approverId);

        return payoutRequestRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Payout request not found: " + id)))
                .flatMap(request -> {
                    if (request.getStatus() != PayoutRequestStatus.PENDING) {
                        return Mono.error(new IllegalStateException(
                                "Payout request is not in PENDING status. Current: " + request.getStatus()));
                    }

                    PayoutRequest updated = request.toBuilder()
                            .status(PayoutRequestStatus.APPROVED)
                            .approvedAt(LocalDateTime.now())
                            .approvedBy(approverId)
                            .notes(notes)
                            .build();

                    return payoutRequestRepository.save(updated);
                })
                .doOnSuccess(approved -> log.info("Payout request approved: {}", id));
    }

    @Override
    public Mono<PayoutRequest> reject(String id, String rejectedBy, String reason) {
        log.info("Rejecting payout request: {} by: {} reason: {}", id, rejectedBy, reason);

        return payoutRequestRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Payout request not found: " + id)))
                .flatMap(request -> {
                    if (request.getStatus().isFinal()) {
                        return Mono.error(new IllegalStateException(
                                "Payout request is already in a final status: " + request.getStatus()));
                    }

                    PayoutRequest updated = request.toBuilder()
                            .status(PayoutRequestStatus.REJECTED)
                            .rejectedAt(LocalDateTime.now())
                            .rejectedBy(rejectedBy)
                            .rejectionReason(reason)
                            .build();

                    return payoutRequestRepository.save(updated);
                })
                .doOnSuccess(rejected -> log.info("Payout request rejected: {}", id));
    }

    @Override
    public Mono<PayoutRequest> process(String id, String processedBy) {
        log.info("Processing payout request: {} by: {}", id, processedBy);

        return payoutRequestRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Payout request not found: " + id)))
                .flatMap(request -> {
                    if (request.getStatus() != PayoutRequestStatus.APPROVED) {
                        return Mono.error(new IllegalStateException(
                                "Payout request must be APPROVED before processing. Current: " + request.getStatus()));
                    }

                    PayoutRequest updated = request.toBuilder()
                            .status(PayoutRequestStatus.PROCESSING)
                            .processedBy(processedBy)
                            .build();

                    return payoutRequestRepository.save(updated);
                })
                .doOnSuccess(processing -> log.info("Payout request being processed: {}", id));
    }

    @Override
    public Mono<PayoutRequest> cancel(String id, String cancelledBy, String reason) {
        log.info("Cancelling payout request: {} by: {} reason: {}", id, cancelledBy, reason);

        return payoutRequestRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Payout request not found: " + id)))
                .flatMap(request -> {
                    if (!request.getStatus().canBeCancelled()) {
                        return Mono.error(new IllegalStateException(
                                "Payout request cannot be cancelled in " + request.getStatus() + " status"));
                    }

                    PayoutRequest updated = request.toBuilder()
                            .status(PayoutRequestStatus.CANCELLED)
                            .rejectedAt(LocalDateTime.now())
                            .rejectedBy(cancelledBy)
                            .rejectionReason(reason)
                            .build();

                    return payoutRequestRepository.save(updated);
                })
                .doOnSuccess(cancelled -> log.info("Payout request cancelled: {}", id));
    }

    @Override
    public Mono<PayoutRequest> save(PayoutRequest payoutRequest) {
        return payoutRequestRepository.save(payoutRequest);
    }

    @Override
    public Mono<PayoutRequest> complete(String id, String bankReference, String completedBy) {
        log.info("Completing payout request: {} with bank reference: {}", id, bankReference);

        return payoutRequestRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Payout request not found: " + id)))
                .flatMap(request -> {
                    if (request.getStatus() != PayoutRequestStatus.PROCESSING) {
                        return Mono.error(new IllegalStateException(
                                "Payout request must be PROCESSING to complete. Current: " + request.getStatus()));
                    }

                    // Calculate total fees (platform + processing)
                    BigDecimal platformFee = request.getPlatformFee() != null ?
                            request.getPlatformFee() : BigDecimal.ZERO;
                    BigDecimal processingFee = request.getProcessingFee() != null ?
                            request.getProcessingFee() : BigDecimal.ZERO;
                    BigDecimal totalFee = platformFee.add(processingFee);

                    // Record the payout in accounting (debit escrow, credit payables)
                    return accountingService.recordPayout(
                            request.getRequestId(),
                            request.getEventId(),
                            request.getOrganizerId(),
                            request.getNetPayoutAmount(),
                            totalFee,
                            request.getCurrency()
                    ).flatMap(journalEntry -> {
                        // Record the disbursement (debit payables, credit bank)
                        return accountingService.recordPayoutDisbursement(
                                request.getRequestId(),
                                request.getNetPayoutAmount(),
                                bankReference,
                                request.getCurrency()
                        );
                    }).then(Mono.defer(() -> {
                        PayoutRequest updated = request.toBuilder()
                                .status(PayoutRequestStatus.COMPLETED)
                                .actualPayoutDate(LocalDateTime.now())
                                .resolvedBy(completedBy)
                                .resolvedAt(LocalDateTime.now())
                                .paymentReference(bankReference)
                                .build();
                        return payoutRequestRepository.save(updated);
                    }));
                })
                .doOnSuccess(completed -> log.info("Payout request completed: {} with bank ref: {}",
                        id, bankReference));
    }
}
