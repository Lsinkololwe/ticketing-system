package com.pml.booking.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.enums.TicketTransferStatus;
import com.pml.booking.domain.model.TicketTransfer;
import com.pml.booking.service.TicketTransferReads;
import com.pml.booking.web.graphql.dto.InitiateTicketTransferInput.TransferChannel;
import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import com.pml.booking.web.graphql.dto.PaginationInfo;
import com.pml.booking.workflow.transfer.TicketTransferProcess;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@DgsComponent
@RequiredArgsConstructor
public class TicketTransferQueryResolver {

    private final TicketTransferProcess process;
    private final TicketTransferReads reads;

    public record TicketTransferPage(List<TicketTransfer> data, PaginationInfo pagination) {
    }

    /** Who a ticket would go to. Rate limited: it answers whether a contact is registered. */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<Map<String, Object>> transferRecipient(@InputArgument TransferChannel channel, @InputArgument String value) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(me -> process.lookup(me, channel.name(), value))
                .map(found -> {
                    Map<String, Object> recipient = new java.util.HashMap<>();
                    recipient.put("displayName", found.displayName());
                    recipient.put("maskedContact", found.maskedContact());
                    return recipient;
                });
    }

    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<TicketTransferPage> myTicketTransfers(@InputArgument TicketTransferReads.Direction direction,
                                                      @InputArgument TicketTransferStatus status,
                                                      @InputArgument OffsetPaginationInput pagination) {
        return reads.mine(direction, status, pagination).map(slice -> new TicketTransferPage(slice.data(), slice.pagination()));
    }

    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Flux<TicketTransfer> ticketTransferChain(@InputArgument String ticketId) {
        return reads.chain(ticketId);
    }

    /** INCOMING or OUTGOING, from the point of view of whoever is asking. */
    @DgsData(parentType = "TicketTransfer", field = "direction")
    public Mono<TicketTransferReads.Direction> direction(DgsDataFetchingEnvironment dfe) {
        TicketTransfer transfer = dfe.getSource();
        return SecurityContextUtils.getCurrentUserId()
                .map(me -> me.equals(transfer.getToUserId()) ? TicketTransferReads.Direction.INCOMING
                        : me.equals(transfer.getFromUserId()) ? TicketTransferReads.Direction.OUTGOING : null);
    }
}
