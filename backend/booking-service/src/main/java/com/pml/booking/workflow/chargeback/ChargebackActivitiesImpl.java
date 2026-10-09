package com.pml.booking.workflow.chargeback;

import com.pml.booking.domain.TicketStateMachine;
import com.pml.booking.domain.enums.AlertPriority;
import com.pml.booking.domain.enums.ChargebackReason;
import com.pml.booking.domain.enums.NotificationType;
import com.pml.booking.domain.enums.RecoveryStatus;
import com.pml.booking.domain.model.ChargebackRecord;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.infrastructure.client.CatalogServiceClient;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.service.ChargebackService;
import com.pml.booking.service.FinanceEscalations;
import com.pml.booking.service.NotificationService;
import com.pml.shared.workflow.Refusals;
import com.pml.booking.workflow.chargeback.ChargebackWorkflow.Dispute;
import com.pml.booking.workflow.chargeback.ChargebackWorkflow.Receive;
import com.pml.booking.workflow.chargeback.ChargebackWorkflow.View;
import com.pml.booking.workflow.finance.EventFinanceProcess;
import com.pml.shared.constants.ChargebackStatus;
import com.pml.shared.constants.PlatformTime;
import com.pml.shared.constants.TicketStatus;
import com.pml.shared.error.ErrorCode;
import io.temporal.spring.boot.ActivityImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Chargeback activities over {@link ChargebackService}.
 *
 * <p>The escrow's dispute count moves only with the chargeback's own {@code disputeOpen} flag, both by
 * compare-and-set in one transaction, so a retried step counts or uncounts a chargeback once. Each
 * decision checks the stored status first and answers it unchanged when the step already happened.
 */
@Slf4j
@Component
@ActivityImpl(taskQueues = TaskQueues.FINANCE)
public class ChargebackActivitiesImpl implements ChargebackActivities {

    private static final Duration AWAIT = Duration.ofSeconds(50);

    private final ChargebackService chargebacks;
    private final ReactiveMongoTemplate template;
    private final TransactionalOperator transactionalOperator;
    private final NotificationService notifications;
    private final FinanceEscalations financeEscalations;
    private final CatalogServiceClient catalog;
    private final EventFinanceProcess eventFinance;
    private final Clock clock;

    public ChargebackActivitiesImpl(ChargebackService chargebacks, ReactiveMongoTemplate template,
                                    TransactionalOperator transactionalOperator, NotificationService notifications,
                                    FinanceEscalations financeEscalations, CatalogServiceClient catalog, EventFinanceProcess eventFinance, Clock clock) {
        this.chargebacks = chargebacks;
        this.template = template;
        this.transactionalOperator = transactionalOperator;
        this.notifications = notifications;
        this.financeEscalations = financeEscalations;
        this.catalog = catalog;
        this.eventFinance = eventFinance;
        this.clock = clock;
    }

    @Override
    public View receive(Receive command) {
        return await(chargebacks.receiveChargeback(command.chargebackId(), command.originalTransactionId(), command.ticketId(),
                        command.eventId(), command.organizerId(), command.organizationId(), command.customerId(),
                        command.originalAmount(), command.chargebackAmount(), command.chargebackFee(), command.currency(),
                        ChargebackReason.valueOf(command.reason()), Instant.ofEpochMilli(command.responseDeadlineMillis()),
                        Map.of())
                .map(ChargebackActivitiesImpl::view));
    }

    @Override
    public View openDispute(String recordId) {
        return await(record(recordId).flatMap(record -> countDispute(record, true)
                .flatMap(counted -> invalidateTicket(record).then(counted ? notifyReceived(record) : Mono.empty()))
                .then(record(recordId)))
                .map(ChargebackActivitiesImpl::view));
    }

    @Override
    public View startReview(String recordId, String actorId, String note) {
        return decide(recordId, ChargebackStatus.UNDER_REVIEW,
                () -> chargebacks.startReview(recordId, actorId, note != null ? note : "Review initiated"));
    }

    @Override
    public View accept(String recordId, String actorId, String note) {
        return decide(recordId, ChargebackStatus.ACCEPTED,
                () -> chargebacks.acceptChargeback(recordId, actorId, note != null ? note : "Accepted"));
    }

    @Override
    public View dispute(String recordId, Dispute command) {
        return decide(recordId, ChargebackStatus.DISPUTED, () -> chargebacks.disputeChargeback(recordId, command.actorId(),
                new ChargebackService.DisputeEvidence(command.ticketValidationProof(), command.customerCommunicationLog(),
                        command.deliveryConfirmation(), command.termsAcceptanceProof(), command.additionalDocuments(), Map.of()),
                command.notes()));
    }

    @Override
    public View recordWin(String recordId, String actorId, String notes) {
        return decide(recordId, ChargebackStatus.WON, () -> chargebacks.recordWin(recordId, clock.instant(), notes));
    }

    @Override
    public View recordLoss(String recordId, String actorId, String notes) {
        return decide(recordId, ChargebackStatus.LOST, () -> chargebacks.recordLoss(recordId, clock.instant(), notes));
    }

    @Override
    public View recover(String recordId) {
        return await(record(recordId).flatMap(record -> record.getRecoveryStatus() == null
                        || record.getRecoveryStatus() == RecoveryStatus.NOT_STARTED
                        ? chargebacks.startRecovery(recordId)
                        : Mono.just(record))
                .map(ChargebackActivitiesImpl::view));
    }

    @Override
    public void closeDispute(String recordId) {
        await(record(recordId).flatMap(record -> countDispute(record, false)
                        .flatMap(uncounted -> uncounted
                                ? notifyResolved(record).then(eventFinance.disputeClosed(record.getEventId(), record.getChargebackId()))
                                : Mono.empty()))
                .thenReturn(Boolean.TRUE));
    }

    @Override
    public void escalate(String recordId) {
        await(record(recordId)
                .filter(record -> record.getEscalatedAt() == null && ChargebackRules.canDecide(record.getStatus()))
                .flatMap(record -> financeEscalations.escalate(new FinanceEscalations.Escalation(AlertPriority.CRITICAL,
                                "Chargeback undecided: 24 hours to respond",
                                "Chargeback " + record.getChargebackId() + " for K" + record.getChargebackAmount() + " on event "
                                        + record.getEventId() + " has no decision. It is accepted automatically at its response deadline, "
                                        + record.getResponseDeadline() + ".",
                                "finance.chargeback-undecided", "chargeback:" + record.getChargebackId(), record.getId()))
                        .then(template.updateFirst(
                                Query.query(Criteria.where("_id").is(recordId).and("escalatedAt").is(null)),
                                new Update().set("escalatedAt", clock.instant()), ChargebackRecord.class)))
                .thenReturn(Boolean.TRUE));
    }

    /** Sets or clears {@code disputeOpen} and moves the escrow's count with it, in one transaction; answers whether it moved. */
    private Mono<Boolean> countDispute(ChargebackRecord record, boolean open) {
        return template.updateFirst(
                        Query.query(Criteria.where("_id").is(record.getId()).and("disputeOpen").ne(open)),
                        new Update().set("disputeOpen", open),
                        ChargebackRecord.class)
                .flatMap(result -> result.getModifiedCount() == 0
                        ? Mono.just(false)
                        : template.updateFirst(Query.query(Criteria.where("eventId").is(record.getEventId())),
                                        new Update().inc("openDisputeCount", open ? 1 : -1), EventEscrowAccount.class)
                                .thenReturn(true))
                .as(transactionalOperator::transactional);
    }

    /**
     * The ticket is settled as refunded before its seat is returned: a retry finds the ticket no longer
     * refundable and returns no second seat.
     */
    private Mono<Void> invalidateTicket(ChargebackRecord record) {
        Instant now = clock.instant();
        return template.findById(record.getTicketId(), Ticket.class)
                .filter(ticket -> ticket.getStatus().isRefundable())
                .flatMap(ticket -> {
                    TicketStatus pending = TicketStateMachine.require(ticket.getStatus(), TicketStateMachine.Action.REQUEST_REFUND);
                    ticket.setStatus(TicketStateMachine.require(pending, TicketStateMachine.Action.SETTLE_REFUND));
                    ticket.setRefundedAt(now);
                    ticket.setCancelledAt(now);
                    ticket.setCancellationReason("Chargeback: " + record.getReason() + " (ID: " + record.getChargebackId() + ")");
                    return template.save(ticket);
                })
                .flatMap(ticket -> ticket.getTicketTierId() == null
                        ? Mono.empty()
                        : catalog.restoreInventory(ticket.getTicketTierId(), 1, "CHARGEBACK")
                                .doOnNext(result -> {
                                    if (!result.success()) {
                                        log.warn("Seat of ticket {} not returned after chargeback {}: {}",
                                                ticket.getId(), record.getChargebackId(), result.errorMessage());
                                    }
                                })
                                .then());
    }

    private Mono<Void> notifyReceived(ChargebackRecord record) {
        Map<String, Object> context = Map.of(
                "chargebackId", record.getChargebackId(),
                "amount", String.valueOf(record.getChargebackAmount()),
                "reason", String.valueOf(record.getReason()),
                "deadline", String.valueOf(record.getResponseDeadline()),
                "eventId", String.valueOf(record.getEventId()),
                "ticketId", String.valueOf(record.getTicketId()));
        return notifications.sendOrganizerNotification(record.getOrganizerId(), NotificationType.CHARGEBACK_RECEIVED, context)
                .onErrorResume(error -> {
                    log.error("Organizer not notified of chargeback {}: {}", record.getChargebackId(), error.getMessage());
                    return Mono.empty();
                })
                .then(notifications.sendAdminAlert(AlertPriority.HIGH, "New Chargeback Received",
                                "Chargeback " + record.getChargebackId() + " for K" + record.getChargebackAmount()
                                        + " on event " + record.getEventId() + "; respond by " + record.getResponseDeadline())
                        .onErrorResume(error -> Mono.empty()));
    }

    private Mono<Void> notifyResolved(ChargebackRecord record) {
        return notifications.sendOrganizerNotification(record.getOrganizerId(), NotificationType.CHARGEBACK_RESOLVED,
                        Map.of("chargebackId", record.getChargebackId(), "outcome", String.valueOf(record.getStatus())))
                .onErrorResume(error -> {
                    log.error("Organizer not notified of chargeback {} resolution: {}", record.getChargebackId(), error.getMessage());
                    return Mono.empty();
                });
    }

    private View decide(String recordId, ChargebackStatus target, java.util.function.Supplier<Mono<ChargebackRecord>> step) {
        return await(record(recordId).flatMap(record -> record.getStatus() == target ? Mono.just(record) : step.get())
                .map(ChargebackActivitiesImpl::view));
    }

    private Mono<ChargebackRecord> record(String recordId) {
        return chargebacks.findById(recordId)
                .switchIfEmpty(Mono.error(Refusals.refusal(ErrorCode.RESOURCE_CONFLICT, "no chargeback record " + recordId)));
    }

    static View view(ChargebackRecord record) {
        long deadline = record.getResponseDeadline() == null ? 0L
                : record.getResponseDeadline().atStartOfDay(PlatformTime.ZONE).toInstant().toEpochMilli();
        return new View(record.getId(), record.getChargebackId(), record.getEventId(), record.getStatus(), deadline);
    }

    private static <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            throw Refusals.forActivity(error);
        }
    }
}
