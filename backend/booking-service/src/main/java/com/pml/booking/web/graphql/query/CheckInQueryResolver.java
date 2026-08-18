package com.pml.booking.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.CheckIn;
import com.pml.booking.repository.TicketRepository;
import com.pml.booking.service.CheckInService;
import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import com.pml.booking.web.graphql.dto.checkin.CheckInConflictPage;
import com.pml.booking.web.graphql.dto.checkin.CheckInSummary;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Gate reads for organizers.
 *
 * <h2>Security</h2>
 * Every query is scoped to the organizer id taken from the JWT, never from an
 * argument. An organizer asking about an event they do not own gets an empty
 * result rather than a refusal — the difference between "no attendance" and
 * "not yours" is itself information about someone else's event.
 *
 * <p>This is the coarse gate. ET-TKT-003 also requires a per-event
 * {@code ticket:scan} grant from ET-ORG-003, which does not exist yet; until it
 * does, any of an organization's organizers can read any of its gates.
 *
 * @see <a href="file:../../../../../../../specs/ticketing/003-validation-and-checkin/spec.md">ET-TKT-003</a>
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class CheckInQueryResolver {

    private final CheckInService checkInService;
    private final TicketRepository ticketRepository;

    @DgsQuery
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Mono<CheckInSummary> checkInSummary(@InputArgument String eventId) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(organizerId -> ticketRepository
                        .countByEventIdAndOrganizerId(eventId, organizerId)
                        .flatMap(issued -> checkInService.summary(eventId, organizerId, issued)))
                .map(CheckInSummary::from);
    }

    @DgsQuery
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Flux<CheckIn> recentCheckIns(@InputArgument String eventId,
                                        @InputArgument Integer limit) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMapMany(organizerId -> checkInService.recentCheckIns(
                        eventId, organizerId, limit == null ? 25 : limit));
    }

    @DgsQuery
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Mono<CheckInConflictPage> checkInConflicts(@InputArgument String eventId,
                                                      @InputArgument OffsetPaginationInput pagination) {
        OffsetPaginationInput page = pagination == null ? OffsetPaginationInput.defaults() : pagination;

        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(organizerId -> Mono.zip(
                        checkInService.conflicts(eventId, organizerId, page.page(), page.size())
                                .collectList(),
                        checkInService.countConflicts(eventId, organizerId)
                ).map(t -> new CheckInConflictPage(
                        t.getT1(), t.getT2().intValue(), page.page(), page.size())));
    }
}
