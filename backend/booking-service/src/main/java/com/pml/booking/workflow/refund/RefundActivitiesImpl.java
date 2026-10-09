package com.pml.booking.workflow.refund;

import com.pml.booking.domain.enums.AlertPriority;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.RefundRequest;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.infrastructure.gateway.MobileMoneyGateway;
import com.pml.booking.infrastructure.gateway.MobileMoneyGatewayFactory;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.repository.RefundRequestRepository;
import com.pml.booking.service.CommissionService;
import com.pml.booking.service.EscrowService;
import com.pml.booking.service.FinanceEscalations;
import com.pml.booking.service.PaymentOutcomeService;
import com.pml.booking.service.RefundService;
import com.pml.shared.workflow.Refusals;
import com.pml.booking.workflow.refund.RefundWorkflow.Answer;
import com.pml.booking.workflow.refund.RefundWorkflow.Decision;
import com.pml.booking.workflow.refund.RefundWorkflow.Submit;
import com.pml.booking.workflow.refund.RefundWorkflow.View;
import com.pml.shared.constants.RefundRequestStatus;
import com.pml.shared.error.ErrorCode;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

/**
 * Refund activities over {@link RefundService}. Each checks the stored status first, so a
 * retried activity answers what the first attempt did instead of moving money again.
 */
@Component
@ActivityImpl(taskQueues = {TaskQueues.FINANCE, TaskQueues.PROVIDER})
public class RefundActivitiesImpl implements RefundActivities {

    private static final Duration AWAIT = Duration.ofSeconds(50);
    private static final String PROVIDER = "pawapay";
    /** A request in one of these states is over: it is not the open refund of its ticket and does not stand in the way of another. */
    private static final Set<RefundRequestStatus> CLOSED = EnumSet.of(
            RefundRequestStatus.REJECTED, RefundRequestStatus.CANCELLED, RefundRequestStatus.FAILED, RefundRequestStatus.COMPLETED);

    private final RefundService refunds;
    private final RefundRequestRepository requests;
    private final CommissionService commissions;
    private final EscrowService escrows;
    private final MobileMoneyGatewayFactory gateways;
    private final ReactiveMongoTemplate template;
    private final FinanceEscalations financeEscalations;
    private final Clock clock;

    public RefundActivitiesImpl(RefundService refunds, RefundRequestRepository requests, CommissionService commissions,
                                EscrowService escrows, MobileMoneyGatewayFactory gateways, ReactiveMongoTemplate template,
                                FinanceEscalations financeEscalations, Clock clock) {
        this.refunds = refunds;
        this.requests = requests;
        this.commissions = commissions;
        this.escrows = escrows;
        this.gateways = gateways;
        this.template = template;
        this.financeEscalations = financeEscalations;
        this.clock = clock;
    }

    @Override
    public View submit(Submit command) {
        return await(openFor(command.ticketId())
                // The same ask again (a double click, a retried call) is answered with the request already open. A different
                // amount is a different ask: it must not be quietly answered with a request for another sum.
                .flatMap(open -> RefundRules.asksForDifferentAmount(command.partialAmount(), open.getRefundAmount())
                        ? Mono.<RefundRequest>error(Refusals.refusal(ErrorCode.REFUND_NOT_PERMITTED,
                                RefundRules.openRefundMessage(open.getRefundAmount())))
                        : Mono.just(open))
                .switchIfEmpty(Mono.defer(() -> switch (command.kind()) {
            case BUYER -> refunds.requestRefund(command.ticketId(), command.reason(), command.actorId());
            case PARTIAL -> refunds.requestPartialRefund(command.ticketId(), command.partialAmount(), command.reason(), command.actorId());
            case ADMIN -> refunds.createAdminRefundRequest(command.ticketId(), command.reason(), command.actorId(),
                    command.bypassApproval(), command.partialAmount());
        })).map(RefundActivitiesImpl::view));
    }

    @Override
    public View createAutomatic(String ticketId, String reason) {
        return await(openFor(ticketId).switchIfEmpty(Mono.defer(() -> template.findById(ticketId, Ticket.class)
                .switchIfEmpty(Mono.error(Refusals.refusal(ErrorCode.TICKET_UNKNOWN, "no ticket " + ticketId)))
                // A seat whose whole price already came back has nothing left to refund: answer with the refund that did it.
                .flatMap(ticket -> com.pml.booking.domain.RefundEligibility.remaining(ticket).signum() <= 0
                        ? requests.findByTicketId(ticketId).filter(request -> request.getStatus() == RefundRequestStatus.COMPLETED).next()
                                .switchIfEmpty(Mono.defer(() -> automaticRequest(ticket, reason)))
                        : automaticRequest(ticket, reason)))).map(RefundActivitiesImpl::view));
    }

    private Mono<RefundRequest> automaticRequest(Ticket ticket, String reason) {
        return requests.save(RefundRequest.builder()
                .ticketId(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .eventId(ticket.getEventId())
                .organizerId(ticket.getOrganizerId())
                .organizationId(ticket.getOrganizationId())
                .buyerId(ticket.getOriginalBuyerId() != null ? ticket.getOriginalBuyerId() : ticket.getBuyerId())
                .requestReason("EVENT_CANCELLED: " + reason)
                .refundAmount(com.pml.booking.domain.RefundEligibility.remaining(ticket))
                .originalTicketPrice(ticket.getPrice())
                .originalPaymentTransactionId(ticket.getPaymentReference())
                .currency(ticket.getCurrency())
                .status(RefundRequestStatus.PENDING)
                .isAutomatic(true)
                .requestedAt(clock.instant())
                .build());
    }

    @Override
    public View approve(Decision decision) {
        return await(stored(decision.refundRequestId()).flatMap(request -> request.getStatus() != RefundRequestStatus.PENDING
                ? Mono.just(request)
                : refunds.approveRefund(request.getId(), decision.actorId(), decision.note())).map(RefundActivitiesImpl::view));
    }

    @Override
    public View reject(Decision decision) {
        return await(stored(decision.refundRequestId()).flatMap(request -> request.getStatus() != RefundRequestStatus.PENDING
                ? Mono.just(request)
                : refunds.rejectRefund(request.getId(), decision.actorId(), decision.note())).map(RefundActivitiesImpl::view));
    }

    @Override
    public View cancel(Decision decision) {
        return await(stored(decision.refundRequestId()).flatMap(request -> request.getStatus() == RefundRequestStatus.CANCELLED
                ? Mono.just(request)
                : refunds.cancelRefundRequest(request.getId(), decision.actorId(), decision.note())).map(RefundActivitiesImpl::view));
    }

    @Override
    public View process(String refundRequestId) {
        return await(stored(refundRequestId).flatMap(request -> request.getStatus() != RefundRequestStatus.APPROVED
                ? Mono.just(request)
                : refunds.processRefund(refundRequestId)).map(RefundActivitiesImpl::view));
    }

    @Override
    public Answer providerStatus(String refundRequestId) {
        return await(stored(refundRequestId).flatMap(request -> request.getPawaPayRefundId() == null
                ? Mono.just(Answer.pending())
                : gateway().flatMap(gateway -> gateway.checkRefundStatus(request.getPawaPayRefundId()))
                        .map(result -> switch (PaymentOutcomeService.verdictOf(result)) {
                            case SUCCEEDED -> Answer.completed(result.providerTransactionId() != null
                                    ? result.providerTransactionId() : request.getPawaPayRefundId());
                            case FAILED -> Answer.failed(result.errorCode() != null ? result.errorCode() : "REFUND_FAILED",
                                    result.errorMessage());
                            case PENDING -> Answer.pending();
                        })));
    }

    @Override
    public View complete(String refundRequestId, String reference) {
        return await(stored(refundRequestId).flatMap(request -> request.getStatus() == RefundRequestStatus.COMPLETED
                ? Mono.just(request)
                : refunds.handleRefundCallback(request.getPawaPayRefundId(), "COMPLETED", reference, null, null))
                .map(RefundActivitiesImpl::view));
    }

    @Override
    public View fail(String refundRequestId, String failureCode, String reason) {
        return await(stored(refundRequestId).flatMap(request -> {
            if (request.getStatus() == RefundRequestStatus.FAILED) {
                return Mono.just(request);
            }
            if (request.getPawaPayRefundId() != null) {
                return refunds.handleRefundCallback(request.getPawaPayRefundId(), "FAILED", null, failureCode, reason);
            }
            return template.updateFirst(Query.query(Criteria.where("_id").is(refundRequestId)),
                            new Update().set("status", RefundRequestStatus.FAILED).set("rejectionReason", reason),
                            RefundRequest.class)
                    .then(stored(refundRequestId));
        }).map(RefundActivitiesImpl::view));
    }

    @Override
    public void escalateReview(String refundRequestId, int level) {
        String action = "REVIEW_ESCALATED_" + level;
        Duration waited = RefundRules.reviewEscalation(level);
        await(stored(refundRequestId)
                .filter(request -> request.getStatus() == RefundRequestStatus.PENDING && !escalated(request, action))
                .flatMap(request -> financeEscalations.escalate(new FinanceEscalations.Escalation(
                                level > 1 ? AlertPriority.CRITICAL : AlertPriority.HIGH,
                                "Refund awaiting approval for " + waited.toDays() + " days",
                                "Refund request " + request.getId() + " for K" + request.getRefundAmount() + " (ticket "
                                        + request.getTicketNumber() + ") is still waiting for a person to approve or reject it.",
                                "finance.refund-waiting", request.getId() + ":" + level, request.getId()))
                        .then(template.updateFirst(
                                Query.query(Criteria.where("_id").is(request.getId()).and("history.action").ne(action)),
                                new Update().push("history", RefundRequest.RefundRequestHistory.builder()
                                        .action(action)
                                        .performedBy(RefundRules.SYSTEM_ACTOR)
                                        .performedAt(clock.instant())
                                        .previousStatus(RefundRequestStatus.PENDING.name())
                                        .newStatus(RefundRequestStatus.PENDING.name())
                                        .comments("escalated to finance after " + waited.toDays() + " days")
                                        .build()),
                                RefundRequest.class)))
                .thenReturn(Boolean.TRUE));
    }

    private static boolean escalated(RefundRequest request, String action) {
        return request.getHistory() != null
                && request.getHistory().stream().anyMatch(entry -> action.equals(entry.getAction()));
    }

    /**
     * The escrow debit of a refund is the ticket price less its commission; the re-credit names the
     * refund in its description, so a second call finds it and changes nothing. A ticket with no
     * commission record had no debit to restore.
     */
    @Override
    public void restoreEscrow(String refundRequestId) {
        await(stored(refundRequestId).flatMap(request -> {
            String marker = "Refund reversed: " + request.getId();
            return escrows.findByEventId(request.getEventId())
                    .filter(escrow -> !alreadyRestored(escrow, marker))
                    .flatMap(escrow -> request.getEscrowDebit() != null
                            // What this refund took, as it was fixed when it was processed.
                            ? escrows.creditEscrow(request.getEventId(), request.getEscrowDebit(),
                                    request.getTicketId(), request.getId(), marker)
                            : commissions.findByTicketId(request.getTicketId())
                                    .flatMap(commission -> escrows.creditEscrow(request.getEventId(),
                                            commission.getTicketPrice().subtract(commission.getAmount()),
                                            request.getTicketId(), request.getId(), marker)));
        }).thenReturn(Boolean.TRUE));
    }

    private static boolean alreadyRestored(EventEscrowAccount escrow, String marker) {
        return escrow.getTransactions() != null
                && escrow.getTransactions().stream().anyMatch(row -> marker.equals(row.getDescription()));
    }

    /**
     * The commission cancellation at initiation and this reversal are both keyed off the refund
     * request id, so a retried activity — or one running against a commission a different refund
     * already reinstated — finds nothing left to do.
     */
    @Override
    public void reinstateCommission(String refundRequestId) {
        await(stored(refundRequestId)
                .flatMap(request -> commissions.reinstatePendingCommission(request.getTicketId(), refundRequestId))
                .thenReturn(Boolean.TRUE));
    }

    private Mono<RefundRequest> openFor(String ticketId) {
        return requests.findByTicketId(ticketId).filter(request -> !CLOSED.contains(request.getStatus())).next();
    }

    private Mono<RefundRequest> stored(String refundRequestId) {
        return refunds.findById(refundRequestId)
                .switchIfEmpty(Mono.error(Refusals.refusal(ErrorCode.REFUND_NOT_PERMITTED, "no refund request " + refundRequestId)));
    }

    private Mono<MobileMoneyGateway> gateway() {
        return gateways.getGatewayByProvider(PROVIDER)
                .switchIfEmpty(Mono.defer(() -> Mono.justOrEmpty(gateways.getAllGateways().stream().findFirst())))
                .switchIfEmpty(Mono.error(new IllegalStateException("Timeout on blocking read: no refund provider is configured")));
    }

    static View view(RefundRequest request) {
        return new View(request.getId(), request.getTicketId(), request.getStatus(), request.getRefundAmount());
    }

    private static <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            throw Refusals.forActivity(error);
        }
    }
}
