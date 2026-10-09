package com.pml.booking.service;

import com.pml.booking.domain.PayoutEligibility;
import com.pml.booking.domain.model.BankAccount;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.repository.ChargebackRecordRepository;
import com.pml.booking.workflow.payout.PayoutRules;
import com.pml.booking.workflow.payout.PayoutWorkflow.Decision;
import com.pml.booking.workflow.payout.PayoutWorkflow.Submit;
import com.pml.booking.workflow.payout.PayoutWorkflow.View;
import com.pml.shared.constants.ChargebackStatus;
import com.pml.shared.constants.EscrowStatus;
import com.pml.shared.constants.PayoutMethod;
import com.pml.shared.constants.PayoutRequestStatus;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.event.EventEnvelopes;
import com.pml.shared.event.EventType;
import com.pml.shared.event.Outbox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The MongoDB half of a payout: every state change, each in one transaction.
 *
 * <p>The payout workflow calls these through its activities and owns the sequence; this class owns
 * the rules each step checks and the writes it makes. Every method is idempotent — run twice, the
 * second finds the request already where the first left it — because an activity can complete its
 * write and lose its worker before reporting.
 *
 * <p>Status moves by optimistic lock on {@code @Version}: two writers that both read PENDING cannot
 * both save, and the loser's activity retries and finds the winner's result.
 */
@Slf4j
@Service
public class PayoutSettlementService {

    private static final Set<ChargebackStatus> OPEN_DISPUTES = EnumSet.of(
            ChargebackStatus.RECEIVED, ChargebackStatus.UNDER_REVIEW, ChargebackStatus.DISPUTED);

    private static final List<PayoutRequestStatus> OPEN_REQUESTS = List.of(
            PayoutRequestStatus.PENDING, PayoutRequestStatus.APPROVED, PayoutRequestStatus.PROCESSING);

    private final ReactiveMongoTemplate template;
    private final TransactionalOperator transactionalOperator;
    private final Outbox outbox;
    private final AccountingService accounting;
    private final ChargebackRecordRepository chargebacks;
    private final BigDecimal minimumPayout;
    private final Clock clock;

    public PayoutSettlementService(ReactiveMongoTemplate template,
                                   TransactionalOperator transactionalOperator,
                                   Outbox outbox,
                                   AccountingService accounting,
                                   ChargebackRecordRepository chargebacks,
                                   @Value("${payment.escrow.minimum-payout-amount:10.00}") BigDecimal minimumPayout,
                                   Clock clock) {
        this.template = template;
        this.transactionalOperator = transactionalOperator;
        this.outbox = outbox;
        this.accounting = accounting;
        this.chargebacks = chargebacks;
        this.minimumPayout = minimumPayout;
        this.clock = clock;
    }

    // ---- request and decision ------------------------------------------------------------------

    /** An eligible, full-balance request to a verified account, or a typed refusal. */
    public Mono<View> createRequest(Submit command) {
        return template.findById(command.payoutRequestId(), PayoutRequest.class)
                .switchIfEmpty(Mono.defer(() -> create(command)))
                .map(PayoutSettlementService::view);
    }

    private Mono<PayoutRequest> create(Submit command) {
        return template.findById(command.escrowAccountId(), EventEscrowAccount.class)
                .filter(escrow -> command.organizerId() != null && command.organizerId().equals(escrow.getOrganizerId()))
                .switchIfEmpty(refuse(ErrorCode.ESCROW_ACCOUNT_UNKNOWN, "no escrow account of this organizer matches the request"))
                .flatMap(escrow -> eligibility(escrow, command.payoutRequestId()).flatMap(eligibility -> {
                    if (!eligibility.eligible()) {
                        return refuseIneligible(eligibility);
                    }
                    BigDecimal available = escrow.getCurrentBalance();
                    if (command.requestedAmount() != null && command.requestedAmount().compareTo(available) != 0) {
                        return refuse(ErrorCode.COMMAND_NOT_WELL_FORMED,
                                "a payout is for the full available balance of " + available);
                    }
                    return verifiedAccount(command.bankAccountId(), command.organizerId())
                            .flatMap(bank -> insert(command, escrow, bank, available));
                }));
    }

    private Mono<PayoutRequest> insert(Submit command, EventEscrowAccount escrow, BankAccount bank, BigDecimal amount) {
        Instant now = clock.instant();
        PayoutRequest request = PayoutRequest.builder()
                .id(command.payoutRequestId())
                .requestId("PAY-" + command.payoutRequestId().replace("-", "").substring(0, 8).toUpperCase())
                .idempotencyKey(blankToNull(command.idempotencyKey()))
                .organizerId(command.organizerId())
                .organizationId(escrow.getOrganizationId())
                .eventId(escrow.getEventId())
                .eventTitle(escrow.getEventTitle())
                .escrowAccountId(escrow.getId())
                .bankAccountId(bank.getId())
                .bankAccountName(bank.getAccountHolderName())
                .bankName(bank.getBankName())
                .accountNumber(masked(bank.getAccountNumber()))
                .requestedAmount(amount)
                .settledAmount(amount)
                .currency(command.currency() != null ? command.currency() : escrow.getCurrency())
                .status(PayoutRequestStatus.PENDING)
                .payoutMethod(command.payoutMethod() != null ? command.payoutMethod() : PayoutMethod.BANK_TRANSFER)
                .requestedAt(now)
                .requestedById(command.requestedById())
                .notes(command.notes())
                .metadata(command.metadata())
                .build();

        return template.insert(request)
                .onErrorResume(DuplicateKeyException.class, duplicate -> request.getIdempotencyKey() == null
                        ? Mono.error(duplicate)
                        : template.findOne(Query.query(Criteria.where("idempotencyKey").is(request.getIdempotencyKey())),
                                PayoutRequest.class));
    }

    /** The approver is not the requester; eligibility and the amount are recomputed now. */
    public Mono<View> approve(Decision decision) {
        return load(decision.payoutRequestId()).flatMap(request -> {
            if (request.getStatus() == PayoutRequestStatus.APPROVED && decision.actorId().equals(request.getApprovedBy())) {
                return Mono.just(request);
            }
            if (request.getStatus() != PayoutRequestStatus.PENDING) {
                return refuse(ErrorCode.PAYOUT_STATE_INVALID, "the payout request is " + request.getStatus());
            }
            if (request.isOnHold()) {
                return refuse(ErrorCode.PAYOUT_STATE_INVALID, "the payout request is on hold");
            }
            if (decision.actorId().equals(request.getRequestedById())) {
                return refuse(ErrorCode.ACTOR_NOT_PERMITTED, "the requester of a payout cannot approve it");
            }
            return escrowOf(request).flatMap(escrow -> eligibility(escrow, request.getId()).flatMap(eligibility -> {
                if (!eligibility.eligible()) {
                    return refuseIneligible(eligibility);
                }
                BigDecimal amount = escrow.getCurrentBalance();
                Instant now = clock.instant();
                request.setStatus(PayoutRequestStatus.APPROVED);
                request.setApprovedBy(decision.actorId());
                request.setApprovedAt(now);
                if (decision.note() != null && !decision.note().isBlank()) {
                    request.setNotes(decision.note());
                }
                request.setRequestedAmount(amount);
                request.setSettledAmount(amount);
                return template.save(request);
            }));
        }).map(PayoutSettlementService::view);
    }

    /** Places a hold. Only before money is in flight, and with a reason; a second hold is a no-op. */
    public Mono<View> hold(Decision decision) {
        return load(decision.payoutRequestId()).flatMap(request -> {
            if (request.isOnHold()) {
                return Mono.just(request);
            }
            if (request.getStatus() != PayoutRequestStatus.PENDING && request.getStatus() != PayoutRequestStatus.APPROVED
                    && request.getStatus() != PayoutRequestStatus.FAILED) {
                return refuse(ErrorCode.PAYOUT_STATE_INVALID, "a payout request that is " + request.getStatus() + " cannot be held");
            }
            if (decision.note() == null || decision.note().isBlank()) {
                return refuse(ErrorCode.COMMAND_NOT_WELL_FORMED, "a hold needs a reason");
            }
            Instant now = clock.instant();
            request.setOnHold(true);
            request.setHoldReason(decision.note().trim());
            request.setHeldBy(decision.actorId());
            request.setHeldAt(now);
            appendHistory(request, "HELD", decision.actorId(), decision.note(), now);
            return template.save(request);
        }).map(PayoutSettlementService::view);
    }

    /** Lifts a hold. Releasing a request that is not held is a no-op. */
    public Mono<View> release(Decision decision) {
        return load(decision.payoutRequestId()).flatMap(request -> {
            if (!request.isOnHold()) {
                return Mono.just(request);
            }
            Instant now = clock.instant();
            request.setOnHold(false);
            request.setReleasedBy(decision.actorId());
            request.setReleasedAt(now);
            appendHistory(request, "RELEASED", decision.actorId(), decision.note(), now);
            return template.save(request);
        }).map(PayoutSettlementService::view);
    }

    private static void appendHistory(PayoutRequest request, String action, String actor, String note, Instant at) {
        if (request.getHistory() == null) {
            request.setHistory(new java.util.ArrayList<>());
        }
        request.getHistory().add(PayoutRequest.PayoutRequestHistory.builder()
                .action(action).performedBy(actor).performedAt(at).comments(note)
                .previousStatus(request.getStatus()).newStatus(request.getStatus()).build());
    }

    public Mono<View> reject(Decision decision) {
        return close(decision, PayoutRequestStatus.REJECTED);
    }

    public Mono<View> cancel(Decision decision) {
        return close(decision, PayoutRequestStatus.CANCELLED);
    }

    private Mono<View> close(Decision decision, PayoutRequestStatus terminal) {
        return load(decision.payoutRequestId()).flatMap(request -> {
            if (request.getStatus() == terminal) {
                return Mono.just(request);
            }
            if (request.getStatus() != PayoutRequestStatus.PENDING) {
                return refuse(ErrorCode.PAYOUT_STATE_INVALID, "the payout request is " + request.getStatus());
            }
            request.setStatus(terminal);
            request.setRejectedBy(decision.actorId());
            request.setRejectedAt(clock.instant());
            request.setRejectionReason(decision.note());
            return template.save(request);
        }).map(PayoutSettlementService::view);
    }

    // ---- settlement ----------------------------------------------------------------------------

    /** The escrow debit, its journal entry and PROCESSING commit together, before any transfer. */
    public Mono<View> beginSettlement(String payoutRequestId, String providerPayoutId) {
        return load(payoutRequestId).flatMap(request -> {
            if (request.getStatus() == PayoutRequestStatus.PROCESSING
                    && providerPayoutId.equals(request.getPawaPayPayoutId())) {
                return Mono.just(request);
            }
            if (request.getStatus() != PayoutRequestStatus.APPROVED && request.getStatus() != PayoutRequestStatus.FAILED) {
                return refuse(ErrorCode.PAYOUT_STATE_INVALID, "settlement cannot begin from " + request.getStatus());
            }
            if (request.isOnHold()) {
                // The last gate before money moves: a hold placed after the workflow last looked still stops it.
                return refuse(ErrorCode.PAYOUT_STATE_INVALID, "the payout request is on hold");
            }
            return escrowOf(request).flatMap(escrow -> {
                Instant now = clock.instant();
                if (escrow.getStatus() == EscrowStatus.HOLD && escrow.isHoldPeriodPassed(now)) {
                    escrow.markPayoutEligible();
                }
                if (escrow.getStatus() != EscrowStatus.PAYOUT_ELIGIBLE) {
                    return refuse(ErrorCode.ESCROW_NOT_ACTIVE, "the escrow account is " + escrow.getStatus());
                }
                if (!escrow.hasSufficientBalance(request.getRequestedAmount())) {
                    return refuse(ErrorCode.ESCROW_INSUFFICIENT_BALANCE,
                            "the escrow holds less than the approved " + request.getRequestedAmount());
                }
                int attempt = request.getRetryCount() + 1;
                escrow.debitForPayout(request.getRequestedAmount(), request.getId(), "Payout " + request.getRequestId(), now);
                return template.save(escrow)
                        .then(accounting.recordPayout(request.getId() + ":attempt-" + attempt, request.getEventId(),
                                request.getOrganizerId(), request.getSettledAmount(), BigDecimal.ZERO, request.getCurrency()))
                        .flatMap(entry -> {
                            request.setStatus(PayoutRequestStatus.PROCESSING);
                            request.setRetryCount(attempt);
                            request.setPawaPayPayoutId(providerPayoutId);
                            request.setProcessedAt(now);
                            request.setLastRetryAt(now);
                            request.setJournalEntryId(entry.getId());
                            request.setFailureCategory(null);
                            request.setStuck(false);
                            return template.save(request);
                        });
            });
        }).as(this::transactionally).map(PayoutSettlementService::view);
    }

    /** A verified success: the disbursement entry, COMPLETED and {@code booking.PayoutCompleted}, together. */
    public Mono<View> completeSettlement(String payoutRequestId, String reference) {
        return load(payoutRequestId).flatMap(request -> {
            if (request.getStatus() == PayoutRequestStatus.COMPLETED) {
                return Mono.just(request);
            }
            if (request.getStatus() != PayoutRequestStatus.PROCESSING) {
                return refuse(ErrorCode.PAYOUT_STATE_INVALID, "a payout completes from PROCESSING, not " + request.getStatus());
            }
            Instant now = clock.instant();
            return accounting.recordPayoutDisbursement(request.getId(), request.getSettledAmount(), reference, request.getCurrency())
                    .flatMap(entry -> {
                        request.setStatus(PayoutRequestStatus.COMPLETED);
                        request.setPaymentReference(reference);
                        request.setActualPayoutDate(now);
                        request.setResolvedAt(now);
                        request.setStuck(false);
                        return template.save(request);
                    })
                    .flatMap(saved -> outbox.stage(EventEnvelopes.of(EventType.BOOKING_PAYOUT_COMPLETED, now, saved.getId(),
                            Map.of("payoutRequestId", saved.getId(),
                                    "organizationId", saved.getOrganizationId() != null ? saved.getOrganizationId() : saved.getOrganizerId(),
                                    "netAmount", saved.getSettledAmount(),
                                    "currency", saved.getCurrency() != null ? saved.getCurrency() : "ZMW")))
                            .thenReturn(saved));
        }).as(this::transactionally).map(PayoutSettlementService::view);
    }

    /** A verified failure: the escrow restored exactly, a reversing entry, FAILED, together. */
    public Mono<View> failSettlement(String payoutRequestId, String failureCode, String reason) {
        return load(payoutRequestId).flatMap(request -> {
            if (request.getStatus() == PayoutRequestStatus.FAILED) {
                return Mono.just(request);
            }
            if (request.getStatus() != PayoutRequestStatus.PROCESSING) {
                return refuse(ErrorCode.PAYOUT_STATE_INVALID, "a payout fails from PROCESSING, not " + request.getStatus());
            }
            return escrowOf(request).flatMap(escrow -> {
                Instant now = clock.instant();
                escrow.reverseDebitForPayout(request.getRequestedAmount(), request.getId(), now);
                return template.save(escrow)
                        .then(accounting.recordPayoutReversal(request.getId() + ":reversal-" + request.getRetryCount(),
                                request.getEventId(), request.getOrganizerId(), request.getSettledAmount(),
                                BigDecimal.ZERO, request.getCurrency()))
                        .flatMap(entry -> {
                            request.setStatus(PayoutRequestStatus.FAILED);
                            request.setReversalEntryId(entry.getId());
                            request.setFailureCategory(failureCode);
                            request.setLastError(reason);
                            request.setStuck(false);
                            return template.save(request);
                        });
            });
        }).as(this::transactionally).map(PayoutSettlementService::view);
    }

    /** No verified answer for three days: visible to the recovery queue, money unchanged. */
    public Mono<Void> markUnconfirmed(String payoutRequestId) {
        return load(payoutRequestId)
                .filter(request -> request.getStatus() == PayoutRequestStatus.PROCESSING && !request.isStuck())
                .flatMap(request -> {
                    request.markAsStuck("PAYOUT_UNCONFIRMED: no verified transfer answer", clock.instant());
                    return template.save(request);
                })
                .then();
    }

    /** Bad details: the account needs verification again before it receives money. */
    public Mono<Void> flagBankAccount(String bankAccountId, String failureCode) {
        return template.findById(bankAccountId, BankAccount.class)
                .flatMap(bank -> {
                    bank.setVerified(false);
                    bank.setVerificationStatus(BankAccount.VerificationStatus.VERIFICATION_FAILED);
                    log.warn("Bank account {} marked VERIFICATION_FAILED after transfer failure {}", bankAccountId, failureCode);
                    return template.save(bank);
                })
                .then();
    }

    // ---- helpers -------------------------------------------------------------------------------

    private Mono<PayoutEligibility> eligibility(EventEscrowAccount escrow, String excludingRequestId) {
        Query otherOpen = Query.query(Criteria.where("escrowAccountId").is(escrow.getId())
                .and("status").in(OPEN_REQUESTS)
                .and("_id").ne(excludingRequestId));
        return Mono.zip(
                chargebacks.countByEventIdAndStatusIn(escrow.getEventId(), OPEN_DISPUTES).defaultIfEmpty(0L),
                template.exists(otherOpen, PayoutRequest.class)
        ).map(t -> PayoutEligibility.evaluate(escrow.getStatus(), escrow.getHoldUntil(), escrow.getCurrentBalance(),
                t.getT1(), t.getT2(), minimumPayout, clock.instant()));
    }

    private Mono<BankAccount> verifiedAccount(String bankAccountId, String organizerId) {
        return template.findById(bankAccountId, BankAccount.class)
                .filter(bank -> organizerId.equals(bank.getOrganizerId()) && !"DELETED".equals(bank.getStatus()))
                .switchIfEmpty(refuse(ErrorCode.BANK_ACCOUNT_UNKNOWN, "no bank account of this organizer matches the request"))
                .flatMap(bank -> bank.isVerified()
                        ? Mono.just(bank)
                        : refuse(ErrorCode.BANK_ACCOUNT_NOT_VERIFIED, "the bank account has not completed verification"));
    }

    private Mono<PayoutRequest> load(String payoutRequestId) {
        return template.findById(payoutRequestId, PayoutRequest.class)
                .switchIfEmpty(refuse(ErrorCode.PAYOUT_STATE_INVALID, "no payout request " + payoutRequestId));
    }

    private Mono<EventEscrowAccount> escrowOf(PayoutRequest request) {
        return template.findById(request.getEscrowAccountId(), EventEscrowAccount.class)
                .switchIfEmpty(refuse(ErrorCode.ESCROW_ACCOUNT_UNKNOWN, "no escrow account " + request.getEscrowAccountId()));
    }

    private <T> Mono<T> transactionally(Mono<T> work) {
        return work.as(transactionalOperator::transactional)
                .retryWhen(Retry.backoff(5, Duration.ofMillis(10)).maxBackoff(Duration.ofMillis(200))
                        .filter(PaymentOutcomeService::isTransientTransactionError));
    }

    private static <T> Mono<T> refuseIneligible(PayoutEligibility eligibility) {
        return refuse(PayoutRules.codeFor(eligibility.reasons().get(0)), eligibility.describeFirstFailure());
    }

    private static <T> Mono<T> refuse(ErrorCode code, String message) {
        return Mono.error(new TranslatedRefusal(code, message));
    }

    static String masked(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public static View view(PayoutRequest request) {
        return new View(request.getId(), request.getStatus(), request.getRetryCount(), request.getPayoutMethod(),
                request.getRequestedById(), request.getBankAccountId());
    }
}
