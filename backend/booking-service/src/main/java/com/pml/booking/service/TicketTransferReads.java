package com.pml.booking.service;

import com.pml.booking.domain.enums.TicketTransferStatus;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.domain.model.TicketTransfer;
import com.pml.booking.security.OrganizerAccess;
import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TenantBoundary;
import com.pml.shared.security.Permission;
import com.pml.shared.security.SecurityContextUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Transfers as the people in them, and the organizer of the event, may read them. */
@Service
public class TicketTransferReads {

    public enum Direction { INCOMING, OUTGOING }

    private static final Set<String> SORTABLE = Set.of("createdAt", "expiresAt", "status");

    private final ReactiveMongoTemplate template;
    private final OrganizerAccess access;

    public TicketTransferReads(ReactiveMongoTemplate template, OrganizerAccess access) {
        this.template = template;
        this.access = access;
    }

    /** The caller's own transfers: those they sent, those offered to them, or both. */
    public Mono<Pages.Slice<TicketTransfer>> mine(Direction direction, TicketTransferStatus status, OffsetPaginationInput pagination) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(me -> {
            List<Criteria> all = new ArrayList<>();
            all.add(direction == Direction.INCOMING ? Criteria.where("toUserId").is(me)
                    : direction == Direction.OUTGOING ? Criteria.where("fromUserId").is(me)
                    : new Criteria().orOperator(Criteria.where("toUserId").is(me), Criteria.where("fromUserId").is(me)));
            if (status != null) {
                all.add(Criteria.where("status").is(status));
            }
            return Pages.offset(template, new Criteria().andOperator(all), pagination, TicketTransfer.class, SORTABLE, "createdAt");
        });
    }

    /**
     * Every transfer of one ticket, oldest first: the holder sees their ticket's history, the event's
     * attendee-list readers see it for the gate, and anybody else is told there is no such ticket.
     */
    public Flux<TicketTransfer> chain(String ticketId) {
        return SecurityContextUtils.requireCurrentUserId().flatMapMany(caller -> template.findById(ticketId, Ticket.class)
                .switchIfEmpty(Mono.error(() -> TenantBoundary.refuse(ErrorCode.TICKET_UNKNOWN, "ticket " + ticketId)))
                .flatMap(ticket -> caller.equals(ticket.getBuyerId())
                        ? Mono.just(ticket)
                        : OrganizerAccess.platformGrants(Permission.ATTENDEE_VIEW).flatMap(platform -> platform
                                ? Mono.just(ticket)
                                : access.requireEvent(ticket.getEventId(), Permission.ATTENDEE_VIEW).thenReturn(ticket))
                        .onErrorMap(DomainRefusal.class, refusal -> TenantBoundary.refuse(ErrorCode.TICKET_UNKNOWN, "ticket " + ticketId)))
                .flatMapMany(ticket -> template.find(new Query(Criteria.where("ticketId").is(ticketId))
                        .with(Sort.by(Sort.Direction.ASC, "createdAt")), TicketTransfer.class)));
    }
}
