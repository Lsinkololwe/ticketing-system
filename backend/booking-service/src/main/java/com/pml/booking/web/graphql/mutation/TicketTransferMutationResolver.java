package com.pml.booking.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.domain.model.TicketTransfer;
import com.pml.booking.web.graphql.dto.InitiateTicketTransferInput;
import com.pml.booking.workflow.transfer.TicketTransferProcess;
import com.pml.shared.security.SecurityContextUtils;
import com.pml.shared.security.revocation.FailClosedOnRevocation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Mono;

/**
 * Ticket transfer. Every actor id is the token's subject; the client never names who is sending or
 * receiving, so a transfer cannot be started, withdrawn or accepted on someone else's behalf.
 */
@DgsComponent
@FailClosedOnRevocation
@Validated
@RequiredArgsConstructor
public class TicketTransferMutationResolver {

    private final TicketTransferProcess process;

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<TicketTransfer> initiateTicketTransfer(@Valid @InputArgument InitiateTicketTransferInput input) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(holder -> process.initiate(input, holder));
    }

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<TicketTransfer> cancelTicketTransfer(@InputArgument String transferId) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(sender -> process.cancel(transferId, sender));
    }

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Ticket> acceptTicketTransfer(@InputArgument String transferId) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(recipient -> process.accept(transferId, recipient));
    }

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<TicketTransfer> declineTicketTransfer(@InputArgument String transferId) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(recipient -> process.decline(transferId, recipient));
    }
}
