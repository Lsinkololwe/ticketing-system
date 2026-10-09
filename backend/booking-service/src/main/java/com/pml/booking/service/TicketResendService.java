package com.pml.booking.service;

import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.booking.infrastructure.client.dto.NotificationReceipt;
import com.pml.booking.infrastructure.ratelimit.ActionRateLimiter;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import com.pml.booking.security.OrganizerAccess;
import com.pml.booking.web.graphql.dto.ResendTicketResult;
import com.pml.shared.constants.TicketStatus;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TenantBoundary;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.security.Permission;
import com.pml.shared.security.SecurityContextUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Sends a ticket's holder their ticket again, to a contact identity has verified.
 *
 * <p>The request carries the ticket's number and event, never the QR payload: the code is a
 * credential, and a notification is the wrong place to put one. The message points the holder at
 * their tickets, where the code is shown behind their sign-in. The holder is the ticket's current
 * owner, so a ticket that has been transferred goes to its new holder.
 */
@Service
public class TicketResendService {

    /** A ticket can be re-sent three times an hour, and one caller can trigger thirty re-sends an hour. */
    static final int PER_TICKET = 3;
    static final int PER_CALLER = 30;
    static final Duration WINDOW = Duration.ofHours(1);
    /** Taps inside one bucket are one send: the discriminator identity deduplicates on. */
    static final Duration DEDUPE_BUCKET = Duration.ofMinutes(5);

    private final ReactiveMongoTemplate tickets;
    private final OrganizerAccess access;
    private final IdentityServiceClient identity;
    private final ActionRateLimiter limiter;
    private final Clock clock;

    public TicketResendService(ReactiveMongoTemplate tickets, OrganizerAccess access, IdentityServiceClient identity,
                               ActionRateLimiter limiter, Clock clock) {
        this.tickets = tickets;
        this.access = access;
        this.identity = identity;
        this.limiter = limiter;
        this.clock = clock;
    }

    public Mono<ResendTicketResult> resend(String ticketId) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(caller -> tickets.findById(ticketId, com.pml.booking.domain.model.Ticket.class)
                .switchIfEmpty(Mono.error(() -> TenantBoundary.refuse(ErrorCode.TICKET_UNKNOWN, "ticket " + ticketId)))
                // The holder, or somebody who may see the event's attendees; anybody else is told the ticket is unknown.
                .flatMap(ticket -> caller.equals(ticket.getBuyerId())
                        ? Mono.just(ticket)
                        : OrganizerAccess.platformGrants(Permission.ATTENDEE_VIEW).flatMap(platform -> platform
                                ? Mono.just(ticket)
                                : access.requireEvent(ticket.getEventId(), Permission.ATTENDEE_VIEW).thenReturn(ticket))
                        .onErrorMap(DomainRefusal.class,
                                refusal -> TenantBoundary.refuse(ErrorCode.TICKET_UNKNOWN, "ticket " + ticketId)))
                .flatMap(ticket -> {
                    if (ticket.getStatus() != TicketStatus.ISSUED) {
                        return Mono.error(new TranslatedRefusal(ErrorCode.TICKET_STATE_INVALID,
                                "only an issued ticket can be sent again", Map.of("currentStatus", ticket.getStatus().name())));
                    }
                    return limiter.consume("resend-ticket", ticket.getId(), PER_TICKET, WINDOW)
                            .then(limiter.consume("resend-ticket-caller", caller, PER_CALLER, WINDOW))
                            .then(Mono.defer(() -> send(ticket)));
                }));
    }

    private Mono<ResendTicketResult> send(com.pml.booking.domain.model.Ticket ticket) {
        Map<String, Object> params = new HashMap<>();
        params.put("ticketNumber", ticket.getTicketNumber());
        params.put("eventId", ticket.getEventId());
        params.put("eventTitle", ticket.getEventTitle());
        params.put("eventDate", ticket.getEventDate());
        params.put("bookingNumber", ticket.getBookingNumber());
        long bucket = clock.instant().toEpochMilli() / DEDUPE_BUCKET.toMillis();
        return identity.notifyUser("ticket.resend", ticket.getId() + ":" + bucket, ticket.getBuyerId(), params)
                .onErrorMap(failure -> new TranslatedRefusal(ErrorCode.NOTIFICATION_CHANNEL_UNAVAILABLE,
                        "the notification service could not take the message"))
                .map(receipt -> result(ticket, receipt));
    }

    static ResendTicketResult result(com.pml.booking.domain.model.Ticket ticket, NotificationReceipt receipt) {
        return new ResendTicketResult(ticket.getId(), ticket.getTicketNumber(), receipt.status(), receipt.channel(),
                receipt.destination());
    }
}
