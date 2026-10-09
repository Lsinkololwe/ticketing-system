package com.pml.booking.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.SalesBuckets;
import com.pml.booking.domain.model.HolderMessage;
import com.pml.booking.service.HolderMessaging;
import com.pml.booking.service.SalesAnalytics;
import com.pml.booking.web.graphql.dto.MessageTicketHoldersInput;
import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import com.pml.booking.web.graphql.dto.PaginationInfo;
import com.pml.shared.security.revocation.FailClosedOnRevocation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

/**
 * What an organizer needs to run an event's sales: the series, when people buy, and a way to reach the
 * people holding tickets. Authorization is per event or organization inside the services; the
 * annotations here only require an organizer-capable role.
 */
@DgsComponent
@Validated
@FailClosedOnRevocation
@RequiredArgsConstructor
public class OrganizerOpsResolver {

    private final SalesAnalytics analytics;
    private final HolderMessaging messaging;

    public record HolderMessagePage(List<HolderMessage> data, PaginationInfo pagination) {
    }

    @DgsQuery
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN', 'FINANCE', 'SUPER_ADMIN')")
    public Mono<List<SalesAnalytics.SalesPoint>> salesOverTime(@InputArgument String eventId, @InputArgument Instant from,
                                                               @InputArgument Instant to, @InputArgument SalesBuckets.Bucket bucket) {
        return analytics.salesOverTime(eventId, from, to, bucket);
    }

    @DgsQuery
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN', 'FINANCE', 'SUPER_ADMIN')")
    public Mono<List<SalesAnalytics.HeatCell>> purchasesByDayAndHour(@InputArgument Instant from, @InputArgument Instant to,
                                                                     @InputArgument String eventId,
                                                                     @InputArgument String organizationId) {
        return analytics.purchasesByDayAndHour(from, to, eventId, organizationId);
    }

    @DgsQuery
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN', 'SUPER_ADMIN')")
    public Mono<Integer> ticketHolderAudience(@InputArgument String eventId, @InputArgument HolderMessage.Segment segment,
                                              @InputArgument String ticketTierId) {
        return messaging.audience(eventId, segment, ticketTierId);
    }

    @DgsQuery
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN', 'SUPER_ADMIN')")
    public Mono<HolderMessagePage> ticketHolderMessages(@InputArgument String eventId, @Valid @InputArgument OffsetPaginationInput pagination) {
        return messaging.history(eventId, pagination).map(slice -> new HolderMessagePage(slice.data(), slice.pagination()));
    }

    /**
     * Sends a message to the holders of an event's tickets through the notification service. Capped per event,
     * per sender and per organization each day; the organizer is told how many people it reached, never who.
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN', 'SUPER_ADMIN')")
    public Mono<HolderMessage> messageTicketHolders(@InputArgument String eventId, @Valid @InputArgument MessageTicketHoldersInput input) {
        return messaging.send(eventId, input.subject(), input.body(), input.segment(), input.ticketTierId());
    }
}
