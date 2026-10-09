package com.pml.booking.web.graphql.query;

import com.pml.shared.dto.EventSummaryDto;
import com.pml.booking.security.EventGateAccess;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.CheckIn;
import com.pml.booking.repository.TicketRepository;
import com.pml.booking.service.CheckInService;
import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import com.pml.booking.web.graphql.dto.checkin.CheckInConflictPage;
import com.pml.booking.web.graphql.dto.checkin.CheckInSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Gate reads: admissions, recent scans and scan conflicts for one event.
 *
 * <h2>Security</h2>
 * Each query needs {@code ticket:scan} on the event, checked by {@link EventGateAccess}. The
 * figures are then read for the event's own organizer, taken from catalog rather than from an
 * argument. A caller outside the event's organization is refused as if the event did not exist,
 * so a query cannot be used to learn whether someone else's event has attendance.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class CheckInQueryResolver {

    private final CheckInService checkInService;
    private final TicketRepository ticketRepository;
    private final EventGateAccess gates;

    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<CheckInSummary> checkInSummary(@InputArgument String eventId) {
        return gates.requireScan(eventId).map(EventSummaryDto::getOrganizerId)
                .flatMap(organizerId -> ticketRepository
                        .countByEventIdAndOrganizerId(eventId, organizerId)
                        .flatMap(issued -> checkInService.summary(eventId, organizerId, issued)))
                .map(CheckInSummary::from);
    }

    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Flux<CheckIn> recentCheckIns(@InputArgument String eventId,
                                        @InputArgument Integer limit) {
        return gates.requireScan(eventId).map(EventSummaryDto::getOrganizerId)
                .flatMapMany(organizerId -> checkInService.recentCheckIns(
                        eventId, organizerId, limit == null ? 25 : limit));
    }

    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<CheckInConflictPage> checkInConflicts(@InputArgument String eventId,
                                                      @InputArgument OffsetPaginationInput pagination) {
        OffsetPaginationInput page = pagination == null ? OffsetPaginationInput.defaults() : pagination;

        return gates.requireScan(eventId).map(EventSummaryDto::getOrganizerId)
                .flatMap(organizerId -> Mono.zip(
                        checkInService.conflicts(eventId, organizerId, page.page(), page.size())
                                .collectList(),
                        checkInService.countConflicts(eventId, organizerId)
                ).map(t -> new CheckInConflictPage(
                        t.getT1(), t.getT2().intValue(), page.page(), page.size())));
    }
}
