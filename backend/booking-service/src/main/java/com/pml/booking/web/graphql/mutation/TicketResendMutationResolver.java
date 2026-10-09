package com.pml.booking.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.service.TicketResendService;
import com.pml.booking.web.graphql.dto.ResendTicketResult;
import com.pml.shared.security.revocation.FailClosedOnRevocation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

@DgsComponent
@Validated
@FailClosedOnRevocation
@RequiredArgsConstructor
public class TicketResendMutationResolver {

    private final TicketResendService resends;

    /** Sends the ticket to its holder's verified contact; the holder, or an attendee-list reader of the event, may ask. */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<ResendTicketResult> resendTicket(@InputArgument String ticketId) {
        return resends.resend(ticketId);
    }
}
