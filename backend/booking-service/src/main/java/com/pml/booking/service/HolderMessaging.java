package com.pml.booking.service;

import com.pml.booking.domain.HolderMessageRules;
import com.pml.booking.domain.model.HolderMessage;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.booking.infrastructure.ratelimit.ActionRateLimiter;
import com.pml.booking.security.OrganizerAccess;
import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import com.pml.shared.constants.TicketStatus;
import com.pml.shared.dto.EventSummaryDto;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.error.ValidationRefusal;
import com.pml.shared.security.Permission;
import com.pml.shared.security.SecurityContextUtils;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * An organizer's message to the people holding tickets to their event.
 *
 * <p>Sent through identity, which owns contacts and delivery: this service sends account ids and the
 * text, identity resolves each account's verified contact. The organizer never sees a contact — only how
 * many people it went to. Capped per event, per sender and per organization a day, because a message to
 * a crowd is the easiest thing on the platform to abuse.
 */
@Service
public class HolderMessaging {

    static final int PER_EVENT_PER_DAY = 3;
    static final int PER_SENDER_PER_DAY = 10;
    static final int PER_ORGANIZATION_PER_DAY = 30;
    static final Duration DAY = Duration.ofDays(1);

    private static final Set<TicketStatus> HOLDING = Set.of(TicketStatus.ISSUED, TicketStatus.VALIDATED);

    private final ReactiveMongoTemplate template;
    private final OrganizerAccess access;
    private final IdentityServiceClient identity;
    private final ActionRateLimiter limiter;
    private final Clock clock;

    public HolderMessaging(ReactiveMongoTemplate template, OrganizerAccess access, IdentityServiceClient identity,
                           ActionRateLimiter limiter, Clock clock) {
        this.template = template;
        this.access = access;
        this.identity = identity;
        this.limiter = limiter;
        this.clock = clock;
    }

    /** How many people a message to this audience would reach. */
    public Mono<Integer> audience(String eventId, HolderMessage.Segment segment, String ticketTierId) {
        return access.requireEvent(eventId, Permission.EVENT_EDIT).flatMap(event -> holders(eventId, segment, ticketTierId))
                .map(Set::size);
    }

    public Mono<HolderMessage> send(String eventId, String subject, String body, HolderMessage.Segment segment, String ticketTierId) {
        var violations = HolderMessageRules.check(subject, body);
        if (!violations.isEmpty()) {
            return Mono.error(new ValidationRefusal(violations));
        }
        HolderMessage.Segment audience = segment == null ? HolderMessage.Segment.ALL : segment;
        return SecurityContextUtils.requireCurrentUserId().flatMap(sender -> access.requireEvent(eventId, Permission.EVENT_EDIT)
                .flatMap(event -> limiter.consume("holder-message-event", eventId, PER_EVENT_PER_DAY, DAY)
                        .then(limiter.consume("holder-message-sender", sender, PER_SENDER_PER_DAY, DAY))
                        .then(limiter.consume("holder-message-org", String.valueOf(event.getOrganizationId()), PER_ORGANIZATION_PER_DAY, DAY))
                        .then(holders(eventId, audience, ticketTierId))
                        .flatMap(recipients -> {
                            if (recipients.isEmpty()) {
                                return Mono.<HolderMessage>error(new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED,
                                        "no ticket holder matches this audience"));
                            }
                            if (recipients.size() > HolderMessageRules.MAX_RECIPIENTS) {
                                return Mono.<HolderMessage>error(new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED,
                                        "this audience is larger than " + HolderMessageRules.MAX_RECIPIENTS + "; narrow it by tier or group"));
                            }
                            return deliver(event, sender, HolderMessageRules.clean(subject), HolderMessageRules.clean(body),
                                    audience, ticketTierId, recipients);
                        })));
    }

    private Mono<HolderMessage> deliver(EventSummaryDto event, String sender, String subject, String body,
                                        HolderMessage.Segment segment, String ticketTierId, Set<String> recipients) {
        return template.insert(HolderMessage.builder()
                        .eventId(event.getId()).eventTitle(event.getTitle()).organizationId(event.getOrganizationId())
                        .sentBy(sender).subject(subject).body(body).segment(segment).ticketTierId(ticketTierId)
                        .recipientCount(recipients.size()).deliveredCount(0).status(HolderMessage.Status.SENDING)
                        .createdAt(clock.instant()).build())
                .flatMap(message -> {
                    List<String> ids = new ArrayList<>(recipients);
                    List<List<String>> batches = new ArrayList<>();
                    for (int from = 0; from < ids.size(); from += HolderMessageRules.BATCH) {
                        int to = from + HolderMessageRules.BATCH > ids.size() ? ids.size() : from + HolderMessageRules.BATCH;
                        batches.add(ids.subList(from, to));
                    }
                    Map<String, Object> params = new HashMap<>();
                    params.put("eventId", event.getId());
                    params.put("eventTitle", event.getTitle());
                    params.put("subject", subject);
                    params.put("message", body);
                    return Flux.range(0, batches.size())
                            .concatMap(index -> identity.notifyUsers("event.holders.message", message.getId() + ":" + index,
                                            batches.get(index), params)
                                    // What identity says it reached, not what was asked of it: a holder with no verified
                                    // contact is not delivered to. A repeat of a batch it already sent counts as sent.
                                    .map(receipt -> HolderMessageRules.delivered(receipt.status(), receipt.recipients(), batches.get(index).size()))
                                    .onErrorReturn(0))
                            .reduce(0, Integer::sum)
                            .flatMap(delivered -> {
                                HolderMessage.Status status = HolderMessageRules.statusOf(delivered, ids.size());
                                message.setDeliveredCount(delivered);
                                message.setStatus(status);
                                return template.updateFirst(Query.query(Criteria.where("_id").is(message.getId())),
                                                new Update().set("deliveredCount", delivered).set("status", status), HolderMessage.class)
                                        .thenReturn(message);
                            });
                });
    }

    /** The accounts holding a ticket that matches the audience, in a stable order. */
    private Mono<Set<String>> holders(String eventId, HolderMessage.Segment segment, String ticketTierId) {
        List<Criteria> all = new ArrayList<>();
        all.add(Criteria.where("eventId").is(eventId));
        all.add(Criteria.where("status").in(switch (segment == null ? HolderMessage.Segment.ALL : segment) {
            case ALL -> HOLDING;
            case ADMITTED -> Set.of(TicketStatus.VALIDATED);
            case NOT_ADMITTED -> Set.of(TicketStatus.ISSUED);
        }));
        // A ticket held by an open transfer is between holders; its current holder is told when the transfer settles.
        all.add(Criteria.where("activeTransferId").is(null));
        if (ticketTierId != null && !ticketTierId.isBlank()) {
            all.add(Criteria.where("ticketTierId").is(ticketTierId));
        }
        return template.findDistinct(new Query(new Criteria().andOperator(all)), "buyerId", Ticket.class, String.class)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new))
                .map(set -> (Set<String>) set);
    }

    /** Past messages for an event, newest first. */
    public Mono<Pages.Slice<HolderMessage>> history(String eventId, OffsetPaginationInput pagination) {
        return access.requireEvent(eventId, Permission.EVENT_EDIT)
                .flatMap(event -> Pages.offset(template, Criteria.where("eventId").is(eventId), pagination, HolderMessage.class,
                        Set.of("createdAt"), "createdAt"));
    }
}
