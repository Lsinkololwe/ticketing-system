package com.pml.booking.workflow.transfer;

import com.pml.booking.domain.enums.TicketTransferStatus;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.domain.model.TicketTransfer;
import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.workflow.transfer.TicketTransferWorkflow.Begin;
import com.pml.booking.workflow.transfer.TicketTransferWorkflow.View;
import com.pml.shared.constants.TicketStatus;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.event.EventEnvelopes;
import com.pml.shared.event.EventType;
import com.pml.shared.event.Outbox;
import com.pml.shared.workflow.Refusals;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
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
import java.util.HashMap;
import java.util.Map;

/**
 * The writes of a ticket transfer, each a conditional update inside one transaction.
 *
 * <p>The ticket and the transfer move together or not at all: accepting changes the holder and closes
 * the offer in one commit, so there is no instant at which the ticket has two holders or none, and a
 * crash between the two writes leaves the offer open for the retry rather than half done.
 */
@Component
@ActivityImpl(taskQueues = TaskQueues.CHECKOUT)
public class TicketTransferActivitiesImpl implements TicketTransferActivities {

    private static final Duration AWAIT = Duration.ofSeconds(25);

    private final ReactiveMongoTemplate template;
    private final TransactionalOperator transaction;
    private final Outbox outbox;
    private final IdentityServiceClient identity;
    private final Clock clock;
    private final com.pml.booking.service.CurrentEventDetails events;

    public TicketTransferActivitiesImpl(ReactiveMongoTemplate template, TransactionalOperator transaction, Outbox outbox,
                                        IdentityServiceClient identity, Clock clock,
                                        com.pml.booking.service.CurrentEventDetails events) {
        this.template = template;
        this.events = events;
        this.transaction = transaction;
        this.outbox = outbox;
        this.identity = identity;
        this.clock = clock;
    }

    // ---- open ----------------------------------------------------------------------------------

    @Override
    public View open(Begin begin) {
        return await(template.findById(begin.transferId(), TicketTransfer.class)
                .map(TicketTransferActivitiesImpl::view)
                .switchIfEmpty(Mono.defer(() -> hold(begin).as(transaction::transactional))));
    }

    private Mono<View> hold(Begin begin) {
        Query holdable = Query.query(Criteria.where("_id").is(begin.ticketId())
                .and("buyerId").is(begin.fromUserId())
                .and("status").is(TicketStatus.ISSUED)
                .and("activeTransferId").is(null));
        return template.findAndModify(holdable, new Update().set("activeTransferId", begin.transferId()).inc("version", 1),
                        FindAndModifyOptions.options().returnNew(true), Ticket.class)
                .switchIfEmpty(Mono.defer(() -> template.findById(begin.ticketId(), Ticket.class)
                        .switchIfEmpty(Mono.error(new TranslatedRefusal(ErrorCode.TICKET_UNKNOWN, "no ticket " + begin.ticketId())))
                        .flatMap(ticket -> Mono.<Ticket>error(new TranslatedRefusal(ErrorCode.TICKET_STATE_INVALID,
                                "the ticket cannot be held for a transfer",
                                Map.of("currentStatus", ticket.getActiveTransferId() != null
                                        ? "TRANSFER_PENDING" : String.valueOf(ticket.getStatus())))))))
                .flatMap(ticket -> template.insert(TicketTransfer.builder()
                        .id(begin.transferId())
                        .ticketId(ticket.getId())
                        .ticketNumber(ticket.getTicketNumber())
                        .bookingId(ticket.getBookingId())
                        .bookingNumber(ticket.getBookingNumber())
                        .eventId(ticket.getEventId())
                        .eventTitle(ticket.getEventTitle())
                        .organizationId(ticket.getOrganizationId())
                        .fromUserId(begin.fromUserId())
                        .fromDisplayName(begin.fromDisplayName())
                        .toUserId(begin.toUserId())
                        .toDisplayName(begin.toDisplayName())
                        .recipientChannel(begin.recipientChannel())
                        .recipientMasked(begin.recipientMasked())
                        .note(begin.note())
                        .status(TicketTransferStatus.PENDING)
                        .expiresAt(Instant.ofEpochMilli(begin.expiresAtMillis()))
                        .createdAt(clock.instant())
                        .build()))
                .map(TicketTransferActivitiesImpl::view);
    }

    // ---- accept --------------------------------------------------------------------------------

    @Override
    public View accept(String transferId, String actorId) {
        return await(settle(transferId, actorId).as(transaction::transactional));
    }

    private Mono<View> settle(String transferId, String actorId) {
        Instant now = clock.instant();
        Query open = Query.query(Criteria.where("_id").is(transferId)
                .and("status").is(TicketTransferStatus.PENDING)
                .and("toUserId").is(actorId)
                .and("expiresAt").gt(now));
        Update closing = new Update().set("status", TicketTransferStatus.ACCEPTED).set("resolvedAt", now).set("resolvedBy", actorId);
        return template.findAndModify(open, closing, FindAndModifyOptions.options().returnNew(true), TicketTransfer.class)
                .flatMap(transfer -> handOver(transfer, now))
                .switchIfEmpty(Mono.defer(() -> template.findById(transferId, TicketTransfer.class)
                        .switchIfEmpty(Mono.error(new TranslatedRefusal(ErrorCode.TICKET_TRANSFER_UNKNOWN, "no transfer " + transferId)))
                        .flatMap(existing -> existing.getStatus() == TicketTransferStatus.ACCEPTED && actorId.equals(existing.getToUserId())
                                // The same recipient asking again: it is already theirs.
                                ? Mono.just(view(existing))
                                : Mono.<View>error(new TranslatedRefusal(ErrorCode.TRANSFER_NOT_PENDING,
                                        "the transfer is " + existing.getStatus())))));
    }

    /** The ticket changes holder in the same commit that closed the offer; if it cannot, neither happens. */
    private Mono<View> handOver(TicketTransfer transfer, Instant now) {
        Query held = Query.query(Criteria.where("_id").is(transfer.getTicketId())
                .and("activeTransferId").is(transfer.getId())
                .and("status").is(TicketStatus.ISSUED));
        Update toRecipient = new Update()
                .set("buyerId", transfer.getToUserId())
                .set("buyerName", transfer.getToDisplayName())
                // The cached contact belonged to the previous holder; the recipient's is not copied here.
                .unset("buyerEmail")
                .unset("buyerPhone")
                .set("transferredToId", transfer.getToUserId())
                .set("transferredAt", now)
                .set("transferReason", transfer.getNote())
                .unset("activeTransferId")
                .inc("transferCount", 1)
                .inc("version", 1)
                .set("updatedAt", now);
        return template.findAndModify(held, toRecipient, FindAndModifyOptions.options().returnNew(true), Ticket.class)
                .switchIfEmpty(Mono.error(new TranslatedRefusal(ErrorCode.TICKET_STATE_INVALID,
                        "the ticket can no longer be handed over", Map.of("currentStatus", "NOT_ISSUED"))))
                // The payer is recorded once, the first time the ticket changes hands, and never moves again.
                .flatMap(ticket -> ticket.getOriginalBuyerId() != null
                        ? Mono.just(ticket)
                        : template.findAndModify(Query.query(Criteria.where("_id").is(ticket.getId()).and("originalBuyerId").is(null)),
                                new Update().set("originalBuyerId", transfer.getFromUserId()).inc("version", 1),
                                FindAndModifyOptions.options().returnNew(true), Ticket.class).defaultIfEmpty(ticket))
                .flatMap(ticket -> outbox.stage(EventEnvelopes.of(EventType.BOOKING_TICKET_TRANSFERRED, now, ticket.getId(),
                        Map.of("ticketId", ticket.getId(), "fromUserId", transfer.getFromUserId(),
                                "toUserId", transfer.getToUserId()))))
                .thenReturn(view(transfer));
    }

    // ---- release -------------------------------------------------------------------------------

    @Override
    public View release(String transferId, TicketTransferStatus to, String actorId) {
        if (to == TicketTransferStatus.PENDING || to == TicketTransferStatus.ACCEPTED) {
            throw Refusals.refusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "a release ends an offer without handing the ticket over");
        }
        return await(giveBack(transferId, to, actorId).as(transaction::transactional));
    }

    private Mono<View> giveBack(String transferId, TicketTransferStatus to, String actorId) {
        Instant now = clock.instant();
        Criteria where = Criteria.where("_id").is(transferId).and("status").is(TicketTransferStatus.PENDING);
        // Only the recipient declines and only the sender cancels; the expiry belongs to the system.
        switch (to) {
            case DECLINED -> where = where.and("toUserId").is(actorId);
            case CANCELLED -> where = where.and("fromUserId").is(actorId);
            default -> { }
        }
        Query open = Query.query(where);
        Update closing = new Update().set("status", to).set("resolvedAt", now).set("resolvedBy", actorId);
        return template.findAndModify(open, closing, FindAndModifyOptions.options().returnNew(true), TicketTransfer.class)
                .flatMap(transfer -> template.updateFirst(
                                Query.query(Criteria.where("_id").is(transfer.getTicketId()).and("activeTransferId").is(transferId)),
                                new Update().unset("activeTransferId").set("updatedAt", now).inc("version", 1), Ticket.class)
                        .thenReturn(transfer))
                .switchIfEmpty(Mono.defer(() -> template.findById(transferId, TicketTransfer.class)
                        .switchIfEmpty(Mono.error(new TranslatedRefusal(ErrorCode.TICKET_TRANSFER_UNKNOWN, "no transfer " + transferId)))
                        // Already ended the way this call wants: say so. Ended any other way: refuse.
                        .flatMap(existing -> existing.getStatus() == to
                                ? Mono.just(existing)
                                : Mono.<TicketTransfer>error(new TranslatedRefusal(ErrorCode.TRANSFER_NOT_PENDING,
                                        "the transfer is " + existing.getStatus())))))
                .map(TicketTransferActivitiesImpl::view);
    }

    // ---- notifications -------------------------------------------------------------------------

    @Override
    public void notifyRecipient(String transferId) {
        await(template.findById(transferId, TicketTransfer.class)
                .filter(transfer -> transfer.getStatus() == TicketTransferStatus.PENDING)
                .flatMap(this::withCurrentEvent)
                .flatMap(transfer -> identity.notifyUser("ticket.transfer.offered", transferId + ":offered", transfer.getToUserId(),
                        details(transfer))));
    }

    @Override
    public void notifySender(String transferId) {
        await(template.findById(transferId, TicketTransfer.class)
                // A sender who withdrew the offer themselves needs no message about it.
                .filter(transfer -> transfer.getStatus() != TicketTransferStatus.CANCELLED && transfer.getStatus() != TicketTransferStatus.PENDING)
                .flatMap(this::withCurrentEvent)
                .flatMap(transfer -> identity.notifyUser("ticket.transfer." + transfer.getStatus().name().toLowerCase(),
                        transferId + ":" + transfer.getStatus().name().toLowerCase(), transfer.getFromUserId(), details(transfer))));
    }

    /** The transfer with its event's current name, in memory only: the message names the event as it is now. */
    private Mono<TicketTransfer> withCurrentEvent(TicketTransfer transfer) {
        return events.of(transfer.getEventId())
                .map(details -> {
                    if (details.title() != null && !details.title().isBlank()) {
                        transfer.setEventTitle(details.title());
                    }
                    return transfer;
                })
                .defaultIfEmpty(transfer);
    }

    private static Map<String, Object> details(TicketTransfer transfer) {
        Map<String, Object> params = new HashMap<>();
        params.put("transferId", transfer.getId());
        params.put("ticketNumber", transfer.getTicketNumber());
        params.put("eventId", transfer.getEventId());
        params.put("eventTitle", transfer.getEventTitle());
        params.put("fromDisplayName", transfer.getFromDisplayName());
        params.put("toDisplayName", transfer.getToDisplayName());
        params.put("note", transfer.getNote());
        params.put("expiresAt", transfer.getExpiresAt() == null ? null : transfer.getExpiresAt().toString());
        return params;
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static View view(TicketTransfer transfer) {
        return new View(transfer.getId(), transfer.getTicketId(), transfer.getFromUserId(), transfer.getToUserId(),
                transfer.getStatus(), transfer.getExpiresAt() == null ? 0L : transfer.getExpiresAt().toEpochMilli());
    }

    private static <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            if (error instanceof DuplicateKeyException) {
                throw Refusals.refusal(ErrorCode.TICKET_STATE_INVALID, "the transfer already exists");
            }
            throw Refusals.forActivity(error);
        }
    }
}
