package com.pml.booking.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.service.ReservationService;
import com.pml.booking.web.graphql.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.GrantedAuthority;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * GraphQL Query Resolver for Ticket Reservations.
 *
 * Provides read-only queries for reservation data with both offset
 * and cursor-based pagination options.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class ReservationQueryResolver {

    private final ReservationService reservationService;

    // ========================================================================
    // SINGLE ENTITY QUERIES
    // ========================================================================

    /**
     * One reservation, if it is the caller's own.
     * Schema: {@code reservation(id: ID!): TicketReservation}
     *
     * <h2>Scoped to the buyer, because a reservation belongs to a person</h2>
     * OWASP A01:2021 · CWE-639. The id arrives from the client and a reservation carries the
     * buyer's identity, the tiers and quantities they chose, the promo code they used and the
     * exact money — {@code unitPrice}, {@code subtotal}, {@code discountAmount},
     * {@code totalAmount}. A lookup that answers on the id alone hands all of that to any
     * signed-in account holding one, which on this platform costs a phone number to obtain.
     *
     * <p>The two neighbouring operations already scope: {@code cancelReservation} matches the
     * caller against {@code userId}, and {@code myActiveReservations} compares the argument to
     * the token's subject. This is the third, and it takes the same rule.
     *
     * <h2>Subject-scoped, not tenant-scoped</h2>
     * A reservation is keyed to a buyer rather than an organization, so {@code TenantScope} is
     * the wrong instrument — a customer belongs to no organization and would be refused by it.
     * The comparison is against the token's subject. Support and finance read across buyers,
     * which is what {@code reservationsByEvent} exists for on the organizer side.
     *
     * <h2>Empty rather than a refusal</h2>
     * The field is nullable and has always answered {@code null} for an id that does not exist.
     * Answering the same for one that is not the caller's keeps the two indistinguishable, so the
     * query cannot be used to sort real reservation ids from invented ones.
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<TicketReservation> reservation(@InputArgument String id) {
        log.debug("GraphQL query: reservation(id={})", id);
        Objects.requireNonNull(id, "Reservation ID is required");

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(authentication -> reservationService.findById(id)
                        .filter(reservation -> visibleTo(authentication, reservation)));
    }

    /** The buyer themselves, or support and finance acting on their behalf. */
    private static boolean visibleTo(Authentication authentication, TicketReservation reservation) {
        boolean support = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(SUPPORT_AUTHORITIES::contains);
        return support || (reservation.getUserId() != null
                && reservation.getUserId().equals(authentication.getName()));
    }

    /** Roles that read across buyers: refunds, disputes and reconciliation all need it. */
    private static final Set<String> SUPPORT_AUTHORITIES =
            Set.of("ROLE_ADMIN", "ROLE_FINANCE", "ROLE_SUPER_ADMIN");

    /**
     * Get active reservations for a user.
     * Schema: myActiveReservations(userId: ID!): [TicketReservation!]!
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated() and (#userId == authentication.principal.subject or hasAnyRole('ADMIN', 'FINANCE'))")
    public Flux<TicketReservation> myActiveReservations(@InputArgument String userId) {
        log.debug("GraphQL query: myActiveReservations(userId={})", userId);
        Objects.requireNonNull(userId, "User ID is required");
        return reservationService.findActiveByUserId(userId);
    }

    // ========================================================================
    // OFFSET PAGINATION QUERIES (Admin Tables)
    // ========================================================================

    /**
     * Get reservations by event with offset pagination.
     * Schema: reservationsByEvent(eventId: ID!, pagination: OffsetPaginationInput): ReservationOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @eventSecurityService.isEventOrganizer(#eventId, authentication)")
    public Mono<ReservationOffsetPage> reservationsByEvent(
            @InputArgument String eventId,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: reservationsByEvent(eventId={})", eventId);
        Objects.requireNonNull(eventId, "Event ID is required");

        return buildOffsetPage(reservationService.findByEventId(eventId), pagination);
    }

    /**
     * Get expired reservations with offset pagination.
     * Schema: expiredReservations(eventId: ID, since: DateTime!, pagination: OffsetPaginationInput): ReservationOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<ReservationOffsetPage> expiredReservations(
            @InputArgument String eventId,
            @InputArgument OffsetDateTime since,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: expiredReservations(eventId={}, since={})", eventId, since);
        Objects.requireNonNull(since, "Since date is required");

        Instant sinceLocal = since.toInstant();
        Flux<TicketReservation> reservationFlux = eventId != null
                ? reservationService.findExpiredByEventId(eventId, sinceLocal)
                : reservationService.findExpiredSince(sinceLocal);

        return buildOffsetPage(reservationFlux, pagination);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private Mono<ReservationOffsetPage> buildOffsetPage(Flux<TicketReservation> reservationFlux, OffsetPaginationInput pagination) {
        OffsetPaginationInput p = pagination != null ? pagination : OffsetPaginationInput.defaults();
        int limit = p.getLimit();
        int offset = p.getOffset();

        return reservationFlux.collectList()
                .map(allReservations -> {
                    int totalCount = allReservations.size();
                    int totalPages = (int) Math.ceil((double) totalCount / limit);
                    boolean hasNextPage = (offset + limit) < totalCount;
                    boolean hasPreviousPage = p.page() > 1;

                    List<TicketReservation> paginatedData = allReservations.stream()
                            .skip(offset)
                            .limit(limit)
                            .toList();

                    PaginationInfo paginationInfo = new PaginationInfo(
                            totalCount, limit, p.page(), totalPages, hasNextPage, hasPreviousPage
                    );

                    return new ReservationOffsetPage(paginatedData, paginationInfo);
                });
    }
}
