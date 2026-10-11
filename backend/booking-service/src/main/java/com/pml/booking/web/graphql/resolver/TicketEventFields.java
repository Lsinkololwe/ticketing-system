package com.pml.booking.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.domain.model.TicketTransfer;
import com.pml.booking.service.CurrentEventDetails;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * The event's name and date as a ticket, or a transfer of one, shows them: catalog's current values.
 *
 * <p>Issuance copies nothing from the event onto the ticket, and a copy would go stale at the first
 * rename or reschedule. A catalog outage answers the stored value when there is one, and "Unknown"
 * for the name when there is not, so one unreachable event never fails the list it appears in.
 */
@DgsComponent
@RequiredArgsConstructor
public class TicketEventFields {

    private static final String UNKNOWN = "Unknown";

    private final CurrentEventDetails events;

    @DgsData(parentType = "Ticket", field = "eventTitle")
    public Mono<String> eventTitle(DgsDataFetchingEnvironment dfe) {
        Ticket ticket = dfe.getSource();
        return events.current(ticket)
                .map(current -> present(current.getEventTitle()) ? current.getEventTitle() : UNKNOWN);
    }

    @DgsData(parentType = "Ticket", field = "eventDate")
    public Mono<String> eventDate(DgsDataFetchingEnvironment dfe) {
        Ticket ticket = dfe.getSource();
        return events.current(ticket).mapNotNull(Ticket::getEventDate);
    }

    @DgsData(parentType = "TicketTransfer", field = "eventTitle")
    public Mono<String> transferEventTitle(DgsDataFetchingEnvironment dfe) {
        TicketTransfer transfer = dfe.getSource();
        return events.of(transfer.getEventId())
                .mapNotNull(CurrentEventDetails.Details::title)
                .switchIfEmpty(Mono.justOrEmpty(transfer.getEventTitle()));
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
