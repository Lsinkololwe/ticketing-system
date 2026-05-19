package com.pml.booking.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.RefundRequest;
import com.pml.booking.service.RefundService;
import com.pml.booking.service.TicketService;
import com.pml.booking.web.graphql.dto.*;
import com.pml.shared.constants.RefundRequestStatus;
import com.pml.shared.constants.RefundRequestType;
import com.pml.shared.constants.TicketStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * GraphQL Query Resolver for Refund Request Operations.
 *
 * Provides read-only queries for refund request data with both offset
 * and cursor-based pagination options.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class RefundRequestQueryResolver {

    private final RefundService refundService;
    private final TicketService ticketService;

    // ========================================================================
    // SINGLE ENTITY QUERIES
    // ========================================================================

    /**
     * Get a refund request by ID.
     * Schema: refundRequest(id: ID!): RefundRequest
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @refundSecurityService.isRefundRequestOwner(#id, authentication)")
    public Mono<RefundRequest> refundRequest(@InputArgument String id) {
        log.debug("GraphQL query: refundRequest(id={})", id);
        Objects.requireNonNull(id, "Refund request ID is required");
        return refundService.findById(id);
    }

    /**
     * Get a refund request by request ID.
     * Schema: refundRequestByRequestId(requestId: String!): RefundRequest
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @refundSecurityService.isRefundRequestOwnerByRequestId(#requestId, authentication)")
    public Mono<RefundRequest> refundRequestByRequestId(@InputArgument String requestId) {
        log.debug("GraphQL query: refundRequestByRequestId({})", requestId);
        Objects.requireNonNull(requestId, "Request ID is required");
        return refundService.findPendingRefunds()
                .filter(r -> requestId.equals(r.getRequestId()))
                .next();
    }

    /**
     * Get refund requests by ticket ID.
     * Schema: refundRequestsByTicket(ticketId: String!): [RefundRequest!]!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @ticketSecurityService.isTicketOwner(#ticketId, authentication)")
    public Flux<RefundRequest> refundRequestsByTicket(@InputArgument String ticketId) {
        log.debug("GraphQL query: refundRequestsByTicket({})", ticketId);
        Objects.requireNonNull(ticketId, "Ticket ID is required");
        return refundService.findByTicketId(ticketId).flux();
    }

    // ========================================================================
    // OFFSET PAGINATION QUERIES (Admin Tables)
    // ========================================================================

    /**
     * Search refund requests with offset pagination.
     * Schema: refundRequestsOffsetPagination(filter: RefundRequestFilterInput!, pagination: OffsetPaginationInput): RefundRequestOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<RefundRequestOffsetPage> refundRequestsOffsetPagination(
            @InputArgument RefundRequestFilterInput filter,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: refundRequestsOffsetPagination");
        Objects.requireNonNull(filter, "Filter is required");

        Flux<RefundRequest> refundFlux = applyFilters(refundService.findPendingRefunds(), filter);
        return buildOffsetPage(refundFlux, pagination);
    }

    /**
     * Get refund requests by buyer with offset pagination.
     * Schema: refundRequestsByBuyerOffsetPagination(buyerId: String!, pagination: OffsetPaginationInput): RefundRequestOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or #buyerId == authentication.principal.subject")
    public Mono<RefundRequestOffsetPage> refundRequestsByBuyerOffsetPagination(
            @InputArgument String buyerId,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: refundRequestsByBuyerOffsetPagination(buyerId={})", buyerId);
        Objects.requireNonNull(buyerId, "Buyer ID is required");

        return buildOffsetPage(refundService.findByBuyerId(buyerId), pagination);
    }

    /**
     * Get refund requests by event with offset pagination.
     * Schema: refundRequestsByEventOffsetPagination(eventId: String!, pagination: OffsetPaginationInput): RefundRequestOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @eventSecurityService.isEventOrganizer(#eventId, authentication)")
    public Mono<RefundRequestOffsetPage> refundRequestsByEventOffsetPagination(
            @InputArgument String eventId,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: refundRequestsByEventOffsetPagination(eventId={})", eventId);
        Objects.requireNonNull(eventId, "Event ID is required");

        return buildOffsetPage(refundService.findByEventId(eventId), pagination);
    }

    /**
     * Get pending refund requests with offset pagination.
     * Schema: pendingRefundRequestsOffsetPagination(pagination: OffsetPaginationInput): RefundRequestOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<RefundRequestOffsetPage> pendingRefundRequestsOffsetPagination(
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: pendingRefundRequestsOffsetPagination");
        return buildOffsetPage(refundService.findPendingRefunds(), pagination);
    }

    // ========================================================================
    // CURSOR PAGINATION QUERIES (Mobile/Infinite Scroll)
    // ========================================================================

    /**
     * Search refund requests with cursor pagination.
     * Schema: refundRequestsCursorPagination(filter: RefundRequestFilterInput!, pagination: CursorPaginationInput): RefundRequestConnection!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<RefundRequestConnection> refundRequestsCursorPagination(
            @InputArgument RefundRequestFilterInput filter,
            @InputArgument CursorPaginationInput pagination
    ) {
        log.debug("GraphQL query: refundRequestsCursorPagination");
        Objects.requireNonNull(filter, "Filter is required");

        Flux<RefundRequest> refundFlux = applyFilters(refundService.findPendingRefunds(), filter);
        return buildCursorConnection(refundFlux, pagination);
    }

    /**
     * Get refund requests by buyer with cursor pagination.
     * Schema: refundRequestsByBuyerCursorPagination(buyerId: String!, pagination: CursorPaginationInput): RefundRequestConnection!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or #buyerId == authentication.principal.subject")
    public Mono<RefundRequestConnection> refundRequestsByBuyerCursorPagination(
            @InputArgument String buyerId,
            @InputArgument CursorPaginationInput pagination
    ) {
        log.debug("GraphQL query: refundRequestsByBuyerCursorPagination(buyerId={})", buyerId);
        Objects.requireNonNull(buyerId, "Buyer ID is required");

        return buildCursorConnection(refundService.findByBuyerId(buyerId), pagination);
    }

    /**
     * Get refund requests by event with cursor pagination.
     * Schema: refundRequestsByEventCursorPagination(eventId: String!, pagination: CursorPaginationInput): RefundRequestConnection!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @eventSecurityService.isEventOrganizer(#eventId, authentication)")
    public Mono<RefundRequestConnection> refundRequestsByEventCursorPagination(
            @InputArgument String eventId,
            @InputArgument CursorPaginationInput pagination
    ) {
        log.debug("GraphQL query: refundRequestsByEventCursorPagination(eventId={})", eventId);
        Objects.requireNonNull(eventId, "Event ID is required");

        return buildCursorConnection(refundService.findByEventId(eventId), pagination);
    }

    /**
     * Get pending refund requests with cursor pagination.
     * Schema: pendingRefundRequestsCursorPagination(pagination: CursorPaginationInput): RefundRequestConnection!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<RefundRequestConnection> pendingRefundRequestsCursorPagination(
            @InputArgument CursorPaginationInput pagination
    ) {
        log.debug("GraphQL query: pendingRefundRequestsCursorPagination");
        return buildCursorConnection(refundService.findPendingRefunds(), pagination);
    }

    // ========================================================================
    // MOBILE CHECKOUT QUERIES
    // ========================================================================

    /**
     * Calculate refund amount for a ticket (mobile checkout preview).
     * Shows the customer what they'll receive before confirming a refund.
     * Schema: calculateRefundAmount(ticketId: String!): RefundCalculation! @tag(name: "mobile")
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated() and (@ticketSecurityService.isTicketOwner(#ticketId, authentication) or hasAnyRole('ADMIN', 'FINANCE'))")
    public Mono<RefundCalculation> calculateRefundAmount(@InputArgument String ticketId) {
        log.debug("GraphQL query: calculateRefundAmount({})", ticketId);
        Objects.requireNonNull(ticketId, "Ticket ID is required");
        return refundService.calculateRefundAmount(ticketId);
    }

    // ========================================================================
    // UTILITY QUERIES
    // ========================================================================

    /**
     * Check if a ticket is eligible for a refund.
     * Schema: isTicketEligibleForRefund(ticketId: String!): Boolean!
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated() and (@ticketSecurityService.isTicketOwner(#ticketId, authentication) or hasAnyRole('ADMIN', 'FINANCE'))")
    public Mono<Boolean> isTicketEligibleForRefund(@InputArgument String ticketId) {
        log.debug("GraphQL query: isTicketEligibleForRefund({})", ticketId);
        Objects.requireNonNull(ticketId, "Ticket ID is required");

        return ticketService.findById(ticketId)
                .flatMap(ticket -> {
                    if (!isRefundableStatus(ticket.getStatus())) {
                        return Mono.just(false);
                    }

                    return refundService.findByTicketId(ticketId)
                            .map(existingRequest -> {
                                RefundRequestStatus status = existingRequest.getStatus();
                                return status != RefundRequestStatus.PENDING &&
                                        status != RefundRequestStatus.APPROVED &&
                                        status != RefundRequestStatus.PROCESSING;
                            })
                            .defaultIfEmpty(true);
                })
                .defaultIfEmpty(false);
    }

    /**
     * Get a summary of refunds for an event.
     * Schema: eventRefundSummary(eventId: String!): RefundSummary
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @eventSecurityService.isEventOrganizer(#eventId, authentication)")
    public Mono<RefundSummary> eventRefundSummary(@InputArgument String eventId) {
        log.debug("GraphQL query: eventRefundSummary({})", eventId);
        Objects.requireNonNull(eventId, "Event ID is required");

        return refundService.findByEventId(eventId)
                .collectList()
                .map(this::buildRefundSummary);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private Mono<RefundRequestOffsetPage> buildOffsetPage(Flux<RefundRequest> refundFlux, OffsetPaginationInput pagination) {
        OffsetPaginationInput p = pagination != null ? pagination : new OffsetPaginationInput(1, 20);
        int limit = p.getLimit();
        int offset = p.getOffset();

        return refundFlux.collectList()
                .map(allRefunds -> {
                    int totalCount = allRefunds.size();
                    int totalPages = (int) Math.ceil((double) totalCount / limit);
                    boolean hasNextPage = (offset + limit) < totalCount;
                    boolean hasPreviousPage = p.page() > 1;

                    List<RefundRequest> paginatedData = allRefunds.stream()
                            .skip(offset)
                            .limit(limit)
                            .toList();

                    PaginationInfo paginationInfo = new PaginationInfo(
                            totalCount, limit, p.page(), totalPages, hasNextPage, hasPreviousPage
                    );

                    return new RefundRequestOffsetPage(paginatedData, paginationInfo);
                });
    }

    private Mono<RefundRequestConnection> buildCursorConnection(Flux<RefundRequest> refundFlux, CursorPaginationInput pagination) {
        CursorPaginationInput p = pagination != null ? pagination : new CursorPaginationInput(20, null, null, null);
        int limit = p.getLimit();

        return refundFlux.collectList()
                .map(allRefunds -> {
                    int totalCount = allRefunds.size();

                    int startIndex = 0;
                    if (p.after() != null) {
                        for (int i = 0; i < allRefunds.size(); i++) {
                            if (allRefunds.get(i).getId().equals(p.after())) {
                                startIndex = i + 1;
                                break;
                            }
                        }
                    }

                    List<RefundRequest> pageData = allRefunds.stream()
                            .skip(startIndex)
                            .limit(limit)
                            .toList();

                    if (pageData.isEmpty()) {
                        return RefundRequestConnection.empty();
                    }

                    List<RefundRequestEdge> edges = pageData.stream()
                            .map(RefundRequestEdge::of)
                            .toList();

                    boolean hasNextPage = (startIndex + limit) < totalCount;
                    boolean hasPreviousPage = startIndex > 0;
                    String startCursor = edges.get(0).cursor();
                    String endCursor = edges.get(edges.size() - 1).cursor();

                    PageInfo pageInfo = PageInfo.of(hasNextPage, hasPreviousPage, startCursor, endCursor, totalCount);

                    return new RefundRequestConnection(edges, pageInfo, totalCount);
                });
    }

    private Flux<RefundRequest> applyFilters(Flux<RefundRequest> refunds, RefundRequestFilterInput filter) {
        if (filter == null) {
            return refunds;
        }

        return refunds
                .filter(r -> filter.ticketId() == null || filter.ticketId().equals(r.getTicketId()))
                .filter(r -> filter.buyerId() == null || filter.buyerId().equals(r.getBuyerId()))
                .filter(r -> filter.eventId() == null || filter.eventId().equals(r.getEventId()))
                .filter(r -> filter.organizerId() == null || filter.organizerId().equals(r.getOrganizerId()))
                .filter(r -> filter.status() == null || filter.status().equals(r.getStatus()))
                .filter(r -> filter.requestType() == null || filter.requestType().equals(r.getRequestType()))
                .filter(r -> {
                    if (filter.startDate() == null) return true;
                    Instant requestedAt = r.getRequestedAt();
                    return requestedAt != null && !requestedAt.isBefore(filter.startDate());
                })
                .filter(r -> {
                    if (filter.endDate() == null) return true;
                    Instant requestedAt = r.getRequestedAt();
                    return requestedAt != null && !requestedAt.isAfter(filter.endDate());
                });
    }

    private RefundSummary buildRefundSummary(List<RefundRequest> refunds) {
        int totalRefunds = refunds.size();
        BigDecimal totalAmount = BigDecimal.ZERO;
        String currency = "ZMW";

        Map<RefundRequestStatus, RefundStatusSummary> statusMap = new HashMap<>();
        Map<RefundRequestType, RefundTypeSummary> typeMap = new HashMap<>();

        for (RefundRequest refund : refunds) {
            BigDecimal amount = refund.getRefundAmount() != null ? refund.getRefundAmount() : BigDecimal.ZERO;
            totalAmount = totalAmount.add(amount);

            if (refund.getCurrency() != null) {
                currency = refund.getCurrency();
            }

            RefundRequestStatus status = refund.getStatus();
            RefundStatusSummary statusSummary = statusMap.getOrDefault(status,
                    new RefundStatusSummary(status.name(), 0, BigDecimal.ZERO, 0.0));
            statusMap.put(status, new RefundStatusSummary(
                    status.name(),
                    statusSummary.count() + 1,
                    statusSummary.totalAmount().add(amount),
                    0.0
            ));

            RefundRequestType type = refund.getRequestType();
            if (type != null) {
                RefundTypeSummary typeSummary = typeMap.getOrDefault(type,
                        new RefundTypeSummary(type.name(), 0, BigDecimal.ZERO, 0.0));
                typeMap.put(type, new RefundTypeSummary(
                        type.name(),
                        typeSummary.count() + 1,
                        typeSummary.totalAmount().add(amount),
                        0.0
                ));
            }
        }

        List<RefundStatusSummary> refundsByStatus = statusMap.entrySet().stream()
                .map(entry -> {
                    RefundStatusSummary summary = entry.getValue();
                    double percentage = totalRefunds > 0 ? (summary.count() * 100.0) / totalRefunds : 0.0;
                    return new RefundStatusSummary(
                            summary.status(),
                            summary.count(),
                            summary.totalAmount().setScale(2, RoundingMode.HALF_UP),
                            Math.round(percentage * 100.0) / 100.0
                    );
                })
                .toList();

        List<RefundTypeSummary> refundsByType = typeMap.entrySet().stream()
                .map(entry -> {
                    RefundTypeSummary summary = entry.getValue();
                    double percentage = totalRefunds > 0 ? (summary.count() * 100.0) / totalRefunds : 0.0;
                    return new RefundTypeSummary(
                            summary.requestType(),
                            summary.count(),
                            summary.totalAmount().setScale(2, RoundingMode.HALF_UP),
                            Math.round(percentage * 100.0) / 100.0
                    );
                })
                .toList();

        return RefundSummary.create(
                totalRefunds,
                totalAmount.setScale(2, RoundingMode.HALF_UP),
                currency,
                refundsByStatus,
                refundsByType
        );
    }

    private boolean isRefundableStatus(TicketStatus status) {
        return status == TicketStatus.PURCHASED ||
                status == TicketStatus.CONFIRMED ||
                status == TicketStatus.VALIDATED;
    }
}
