package com.pml.booking.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.security.TicketHolderContactAccess;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * The holder's name, email and phone as recorded on the ticket, answered only to the holder, to
 * members of the event's organization and to platform administrators; null to everyone else.
 */
@DgsComponent
public class TicketHolderContactFields {

    @DgsData(parentType = "Ticket", field = "buyerName")
    public Mono<String> buyerName(DgsDataFetchingEnvironment dfe) {
        return forPermitted(dfe, Ticket::getBuyerName);
    }

    @DgsData(parentType = "Ticket", field = "buyerEmail")
    public Mono<String> buyerEmail(DgsDataFetchingEnvironment dfe) {
        return forPermitted(dfe, Ticket::getBuyerEmail);
    }

    @DgsData(parentType = "Ticket", field = "buyerPhone")
    public Mono<String> buyerPhone(DgsDataFetchingEnvironment dfe) {
        return forPermitted(dfe, Ticket::getBuyerPhone);
    }

    private static Mono<String> forPermitted(DgsDataFetchingEnvironment dfe, Function<Ticket, String> read) {
        Ticket ticket = dfe.getSource();
        return TicketHolderContactAccess.mayRead(ticket)
                .filter(permitted -> permitted)
                .mapNotNull(permitted -> read.apply(ticket));
    }
}
