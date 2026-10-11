package com.pml.booking.security;

import com.pml.booking.domain.model.Ticket;
import com.pml.shared.security.SecurityContextUtils;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import reactor.core.publisher.Mono;

/**
 * Who may read the contact details recorded on a ticket: its holder, the members of the
 * organization the event belongs to, and platform administrators.
 *
 * <p>A ticket is reached through many queries, each with its own gate on the <em>operation</em>.
 * This is the gate on the <em>data</em>: name, email and phone are personal data of a person who is
 * not necessarily the caller, so they are decided on every field read, from the ticket's own holder
 * and organization and never from anything the caller supplied. A caller who fails the check gets
 * null, as if no contact were recorded, so a probe cannot tell the two apart (OWASP A01, A02).
 */
public final class TicketHolderContactAccess {

    private TicketHolderContactAccess() {
    }

    /** Emits {@code true} once when the caller may read this ticket's holder contact; otherwise {@code false}. */
    public static Mono<Boolean> mayRead(Ticket ticket) {
        if (ticket == null) {
            return Mono.just(false);
        }
        return SecurityContextUtils.getCurrentUserId()
                .map(caller -> ticket.getBuyerId() != null && ticket.getBuyerId().equals(caller))
                .defaultIfEmpty(false)
                .flatMap(holder -> holder
                        ? Mono.just(true)
                        : CurrentTenantScope.get()
                                .map(scope -> scope.permits(ticket.getOrganizationId()))
                                // No scope in the context is a refusal, not a pass.
                                .onErrorReturn(false)
                                .defaultIfEmpty(false));
    }
}
