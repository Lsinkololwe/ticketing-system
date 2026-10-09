package com.pml.booking.workflow.transfer;

import com.pml.booking.domain.DisplayNames;
import com.pml.booking.domain.TransferRules;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.domain.model.TicketTransfer;
import com.pml.booking.infrastructure.client.CatalogServiceClient;
import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.booking.infrastructure.ratelimit.ActionRateLimiter;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.infrastructure.temporal.WorkflowIds;
import com.pml.booking.web.graphql.dto.InitiateTicketTransferInput;
import com.pml.booking.workflow.transfer.TicketTransferWorkflow.Begin;
import com.pml.booking.workflow.transfer.TicketTransferWorkflow.Decision;
import com.pml.booking.workflow.transfer.TicketTransferWorkflow.Start;
import com.pml.booking.workflow.transfer.TicketTransferWorkflow.View;
import com.pml.shared.dto.EventSummaryDto;
import com.pml.booking.domain.enums.TicketTransferStatus;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.error.ValidationRefusal;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.shared.util.ContactMasking;
import com.pml.shared.util.Emails;
import com.pml.shared.util.PhoneNumbers;
import com.pml.shared.workflow.Refusals;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.UpdateOptions;
import io.temporal.client.WithStartWorkflowOperation;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowUpdateStage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Starts and drives ticket transfers. No mutation writes a transfer; each reaches its execution as an
 * update, and the execution decides.
 */
@Service
public class TicketTransferProcess {

    /** Lookups are an oracle for "is this number registered"; this is how often one caller may ask. */
    static final int LOOKUPS_PER_HOUR = 20;
    static final int OFFERS_PER_HOUR = 10;
    static final int OFFERS_PER_TICKET_PER_HOUR = 3;
    static final Duration HOUR = Duration.ofHours(1);
    static final int MESSAGE_MAX = 200;

    private final TemporalGateway temporal;
    private final ReactiveMongoTemplate template;
    private final CatalogServiceClient catalog;
    private final IdentityServiceClient identity;
    private final ActionRateLimiter limiter;
    private final Clock clock;
    private final TransferRules.Limits limits;

    public TicketTransferProcess(TemporalGateway temporal, ReactiveMongoTemplate template, CatalogServiceClient catalog,
                                 IdentityServiceClient identity, ActionRateLimiter limiter, Clock clock,
                                 @Value("${booking.transfer.ttl:PT48H}") Duration ttl,
                                 @Value("${booking.transfer.max-chain:5}") int maxChain,
                                 @Value("${booking.transfer.cutoff:PT2H}") Duration cutoff) {
        this.temporal = temporal;
        this.template = template;
        this.catalog = catalog;
        this.identity = identity;
        this.limiter = limiter;
        this.clock = clock;
        this.limits = new TransferRules.Limits(ttl, maxChain, cutoff);
    }

    /** What the sender sees of the person they are about to send a ticket to. */
    public record Recipient(String userId, String displayName, String maskedContact) {
    }

    public Mono<Recipient> lookup(String holder, String channel, String rawValue) {
        String normalized;
        try {
            normalized = normalize(channel, rawValue);
        } catch (ValidationRefusal malformed) {
            return Mono.error(malformed);
        }
        return limiter.consume("transfer-lookup", holder, LOOKUPS_PER_HOUR, HOUR)
                .then(Mono.defer(() -> identity.lookupByContact(channel, normalized)))
                .filter(found -> found.userId() != null)
                .map(found -> new Recipient(found.userId(), found.displayName(),
                        found.maskedContact() != null ? found.maskedContact() : ContactMasking.mask(channel, normalized)));
    }

    public Mono<TicketTransfer> initiate(InitiateTicketTransferInput input, String holder) {
        if (input.note() != null && input.note().length() > MESSAGE_MAX) {
            return Mono.error(new ValidationRefusal(List.of(new FieldViolation("input.note",
                    "must be at most " + MESSAGE_MAX + " characters"))));
        }
        String channel = input.channel().name();
        try {
            normalize(channel, input.recipient());
        } catch (ValidationRefusal malformed) {
            return Mono.error(malformed);
        }
        Instant now = clock.instant();

        return template.findById(input.ticketId(), Ticket.class)
                // Not the holder's, or not a ticket: the same answer, so the id confirms nothing.
                .filter(ticket -> holder.equals(ticket.getBuyerId()))
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.TICKET_UNKNOWN, "no ticket " + input.ticketId())))
                .flatMap(ticket -> limiter.consume("transfer-offer", holder, OFFERS_PER_HOUR, HOUR)
                        .then(limiter.consume("transfer-offer-ticket", ticket.getId(), OFFERS_PER_TICKET_PER_HOUR, HOUR))
                        .then(Mono.defer(() -> lookup(holder, channel, input.recipient())
                                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.TRANSFER_TARGET_INELIGIBLE,
                                        "that contact does not belong to a registered account",
                                        java.util.Map.of("requiredRole", "CUSTOMER"))))))
                        .zipWith(catalog.getEventById(ticket.getEventId()).map(java.util.Optional::of)
                                .onErrorResume(unavailable -> Mono.just(java.util.Optional.empty()))
                                .defaultIfEmpty(java.util.Optional.empty()))
                        .flatMap(pair -> {
                            Recipient recipient = pair.getT1();
                            EventSummaryDto event = pair.getT2().orElse(null);
                            var refusal = TransferRules.check(ticket, holder, recipient.userId(),
                                    event == null ? null : event.getStartDate(),
                                    event == null ? null : event.getStatus(), now, limits);
                            if (refusal != null) {
                                return Mono.<TicketTransfer>error(refusal);
                            }
                            String transferId = UUID.randomUUID().toString();
                            Instant expiresAt = TransferRules.expiryOf(now, event == null ? null : event.getStartDate(), limits);
                            Begin begin = new Begin(transferId, ticket.getId(), holder, recipient.userId(),
                                    DisplayNames.firstAndInitial(ticket.getBuyerName()), recipient.displayName(), channel,
                                    recipient.maskedContact(), input.note() == null || input.note().isBlank() ? null : input.note().trim(),
                                    expiresAt.toEpochMilli());
                            return start(begin, ticket.getEventId(), ticket.getOrganizationId());
                        }));
    }

    private Mono<TicketTransfer> start(Begin begin, String eventId, String organizationId) {
        return temporal.call(() -> {
                    TicketTransferWorkflow workflow = temporal.newWorkflow(TicketTransferWorkflow.class,
                            WorkflowIds.ticketTransfer(begin.transferId()), TaskQueues.CHECKOUT,
                            WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING,
                            ProcessSearchAttributes.of("TicketTransfer", begin.transferId()).eventId(eventId)
                                    .organizationId(organizationId).build());
                    return WorkflowClient.startUpdateWithStart(workflow::begin, begin,
                                    UpdateOptions.<View>newBuilder().setWaitForStage(WorkflowUpdateStage.COMPLETED).build(),
                                    new WithStartWorkflowOperation<>(workflow::run, new Start(begin.transferId())))
                            .getResult();
                })
                .onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.TICKET_STATE_INVALID))
                .flatMap(view -> template.findById(view.transferId(), TicketTransfer.class));
    }

    /** The sender withdraws. Only they can; anyone else is told there is no such transfer. */
    public Mono<TicketTransfer> cancel(String transferId, String actor) {
        return owned(transferId, actor, true)
                .flatMap(transfer -> update(transfer, workflow -> workflow.cancel(new Decision(transferId, actor)))
                        .onErrorResume(DomainRefusal.class, refused -> alreadyEnded(transferId, TicketTransferStatus.CANCELLED, refused)));
    }

    public Mono<TicketTransfer> decline(String transferId, String actor) {
        return owned(transferId, actor, false)
                .flatMap(transfer -> update(transfer, workflow -> workflow.decline(new Decision(transferId, actor)))
                        .onErrorResume(DomainRefusal.class, refused -> alreadyEnded(transferId, TicketTransferStatus.DECLINED, refused)));
    }

    /** The recipient accepts; the answer is the ticket, now theirs. Asking again, or racing oneself, gets the same answer. */
    public Mono<Ticket> accept(String transferId, String actor) {
        return owned(transferId, actor, false)
                .flatMap(transfer -> update(transfer, workflow -> workflow.accept(new Decision(transferId, actor)))
                        .onErrorResume(DomainRefusal.class, refused -> alreadyEnded(transferId, TicketTransferStatus.ACCEPTED, refused))
                        .then(template.findById(transfer.getTicketId(), Ticket.class)));
    }

    /**
     * A repeat of a call that already did its work is answered with its result, not a refusal: the execution
     * may have closed between the caller's two attempts, and a retried tap must not read as a failure.
     * Any other state is the original refusal.
     */
    private Mono<TicketTransfer> alreadyEnded(String transferId, TicketTransferStatus wanted, DomainRefusal refused) {
        return template.findById(transferId, TicketTransfer.class)
                .filter(current -> current.getStatus() == wanted)
                .switchIfEmpty(Mono.error(refused));
    }

    private Mono<TicketTransfer> update(TicketTransfer transfer,
                                        java.util.function.Function<TicketTransferWorkflow, View> change) {
        return temporal.call(() -> change.apply(temporal.existingWorkflow(TicketTransferWorkflow.class,
                        WorkflowIds.ticketTransfer(transfer.getId()))))
                .onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.TRANSFER_NOT_PENDING))
                .then(template.findById(transfer.getId(), TicketTransfer.class));
    }

    /** The transfer, if the caller is the sender ({@code asSender}) or the recipient of it. */
    private Mono<TicketTransfer> owned(String transferId, String actor, boolean asSender) {
        return template.findById(transferId, TicketTransfer.class)
                .filter(transfer -> actor != null && actor.equals(asSender ? transfer.getFromUserId() : transfer.getToUserId()))
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.TICKET_TRANSFER_UNKNOWN, "no transfer " + transferId)));
    }

    // ---- input ---------------------------------------------------------------------------------

    private static String normalize(String channel, String raw) {
        List<FieldViolation> violations = new ArrayList<>();
        String normalized = null;
        if ("EMAIL".equals(channel)) {
            normalized = Emails.normalize(raw).orElse(null);
            if (normalized == null) {
                violations.add(new FieldViolation("recipient", "must be a valid email address"));
            }
        } else {
            normalized = PhoneNumbers.parseMobile(raw, PhoneNumbers.DEFAULT_REGION).map(PhoneNumbers.Parsed::e164).orElse(null);
            if (normalized == null) {
                violations.add(new FieldViolation("recipient", "must be a valid mobile number"));
            }
        }
        if (!violations.isEmpty()) {
            throw new ValidationRefusal(violations);
        }
        return normalized;
    }
}
