package com.pml.booking.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.service.PayoutRecoveryService;
import com.pml.booking.service.PayoutRequestService;
import com.pml.booking.web.graphql.dto.*;
import com.pml.booking.web.graphql.dto.stats.PayoutRecoverySummary;
import com.pml.booking.web.graphql.dto.stats.PayoutRequestStats;
import com.pml.shared.constants.PayoutRequestStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * GraphQL Query Resolver for Payout Request Operations.
 *
 * <h2>Business Intent</h2>
 * Provides read-only queries for payout request data with both offset
 * and cursor-based pagination options.
 *
 * <h2>Architecture</h2>
 * This resolver delegates business logic to {@link PayoutRequestService},
 * following the Controller → Service → Repository layered architecture pattern.
 * Recovery operations are delegated to {@link PayoutRecoveryService}.
 *
 * @see PayoutRequestService
 * @see PayoutRecoveryService
 * @author Booking Service Team
 * @since 1.0
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class PayoutRequestQueryResolver {

    private final PayoutRequestService payoutRequestService;
    private final PayoutRecoveryService payoutRecoveryService;

    // ========================================================================
    // SINGLE ENTITY QUERIES
    // ========================================================================

    /**
     * Get a payout request by ID.
     * Schema: payoutRequest(id: ID!): PayoutRequest
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @payoutSecurityService.isPayoutRequestOwner(#id, authentication)")
    public Mono<PayoutRequest> payoutRequest(@InputArgument String id) {
        log.debug("GraphQL query: payoutRequest(id={})", id);
        Objects.requireNonNull(id, "Payout request ID is required");
        return payoutRequestService.findById(id);
    }

    /**
     * Get a payout request by request ID.
     * Schema: payoutRequestByRequestId(requestId: String!): PayoutRequest
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @payoutSecurityService.isPayoutRequestOwnerByRequestId(#requestId, authentication)")
    public Mono<PayoutRequest> payoutRequestByRequestId(@InputArgument String requestId) {
        log.debug("GraphQL query: payoutRequestByRequestId({})", requestId);
        Objects.requireNonNull(requestId, "Request ID is required");
        return payoutRequestService.findByRequestId(requestId);
    }

    // ========================================================================
    // OFFSET PAGINATION QUERIES (Admin Tables)
    // ========================================================================

    /**
     * Search payout requests with offset pagination.
     * Schema: payoutRequestsOffsetPagination(filter: PayoutRequestFilterInput!, pagination: OffsetPaginationInput): PayoutRequestOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestOffsetPage> payoutRequestsOffsetPagination(
            @InputArgument PayoutRequestFilterInput filter,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: payoutRequestsOffsetPagination");
        Objects.requireNonNull(filter, "Filter is required");

        Flux<PayoutRequest> payoutFlux = applyFilters(payoutRequestService.findAll(), filter);
        return buildOffsetPage(payoutFlux, pagination);
    }

    /**
     * Get payout requests by organizer with offset pagination.
     * Schema: payoutRequestsByOrganizerOffsetPagination(organizerId: String!, pagination: OffsetPaginationInput): PayoutRequestOffsetPage!
     *
     * <p>OWASP A01:2021 Compliance: Uses OrganizationSecurityService for multi-tenant isolation.</p>
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @organizationSecurityService.canViewFinancialData(#organizerId, authentication)")
    public Mono<PayoutRequestOffsetPage> payoutRequestsByOrganizerOffsetPagination(
            @InputArgument String organizerId,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: payoutRequestsByOrganizerOffsetPagination(organizerId={})", organizerId);
        Objects.requireNonNull(organizerId, "Organizer ID is required");

        return buildOffsetPage(payoutRequestService.findByOrganizerId(organizerId), pagination);
    }

    /**
     * Get payout requests by event with offset pagination.
     * Schema: payoutRequestsByEventOffsetPagination(eventId: String!, pagination: OffsetPaginationInput): PayoutRequestOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @eventSecurityService.isEventOrganizer(#eventId, authentication)")
    public Mono<PayoutRequestOffsetPage> payoutRequestsByEventOffsetPagination(
            @InputArgument String eventId,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: payoutRequestsByEventOffsetPagination(eventId={})", eventId);
        Objects.requireNonNull(eventId, "Event ID is required");

        return buildOffsetPage(payoutRequestService.findByEventId(eventId), pagination);
    }

    /**
     * Get pending payout requests with offset pagination.
     * Schema: pendingPayoutRequestsOffsetPagination(pagination: OffsetPaginationInput): PayoutRequestOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestOffsetPage> pendingPayoutRequestsOffsetPagination(
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: pendingPayoutRequestsOffsetPagination");
        return buildOffsetPage(payoutRequestService.findByStatus(PayoutRequestStatus.PENDING), pagination);
    }

    /**
     * Get failed payout requests with offset pagination.
     * Schema: failedPayoutRequestsOffsetPagination(pagination: OffsetPaginationInput): PayoutRequestOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestOffsetPage> failedPayoutRequestsOffsetPagination(
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: failedPayoutRequestsOffsetPagination");
        return buildOffsetPage(payoutRequestService.findByStatus(PayoutRequestStatus.FAILED), pagination);
    }

    // ========================================================================
    // CURSOR PAGINATION QUERIES (Mobile/Infinite Scroll)
    // ========================================================================

    /**
     * Search payout requests with cursor pagination.
     * Schema: payoutRequestsCursorPagination(filter: PayoutRequestFilterInput!, pagination: CursorPaginationInput): PayoutRequestConnection!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestConnection> payoutRequestsCursorPagination(
            @InputArgument PayoutRequestFilterInput filter,
            @InputArgument CursorPaginationInput pagination
    ) {
        log.debug("GraphQL query: payoutRequestsCursorPagination");
        Objects.requireNonNull(filter, "Filter is required");

        Flux<PayoutRequest> payoutFlux = applyFilters(payoutRequestService.findAll(), filter);
        return buildCursorConnection(payoutFlux, pagination);
    }

    /**
     * Get payout requests by organizer with cursor pagination.
     * Schema: payoutRequestsByOrganizerCursorPagination(organizerId: String!, pagination: CursorPaginationInput): PayoutRequestConnection!
     *
     * <p>OWASP A01:2021 Compliance: Uses OrganizationSecurityService for multi-tenant isolation.</p>
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @organizationSecurityService.canViewFinancialData(#organizerId, authentication)")
    public Mono<PayoutRequestConnection> payoutRequestsByOrganizerCursorPagination(
            @InputArgument String organizerId,
            @InputArgument CursorPaginationInput pagination
    ) {
        log.debug("GraphQL query: payoutRequestsByOrganizerCursorPagination(organizerId={})", organizerId);
        Objects.requireNonNull(organizerId, "Organizer ID is required");

        return buildCursorConnection(payoutRequestService.findByOrganizerId(organizerId), pagination);
    }

    /**
     * Get payout requests by event with cursor pagination.
     * Schema: payoutRequestsByEventCursorPagination(eventId: String!, pagination: CursorPaginationInput): PayoutRequestConnection!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @eventSecurityService.isEventOrganizer(#eventId, authentication)")
    public Mono<PayoutRequestConnection> payoutRequestsByEventCursorPagination(
            @InputArgument String eventId,
            @InputArgument CursorPaginationInput pagination
    ) {
        log.debug("GraphQL query: payoutRequestsByEventCursorPagination(eventId={})", eventId);
        Objects.requireNonNull(eventId, "Event ID is required");

        return buildCursorConnection(payoutRequestService.findByEventId(eventId), pagination);
    }

    /**
     * Get pending payout requests with cursor pagination.
     * Schema: pendingPayoutRequestsCursorPagination(pagination: CursorPaginationInput): PayoutRequestConnection!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestConnection> pendingPayoutRequestsCursorPagination(
            @InputArgument CursorPaginationInput pagination
    ) {
        log.debug("GraphQL query: pendingPayoutRequestsCursorPagination");
        return buildCursorConnection(payoutRequestService.findByStatus(PayoutRequestStatus.PENDING), pagination);
    }

    /**
     * Get failed payout requests with cursor pagination.
     * Schema: failedPayoutRequestsCursorPagination(pagination: CursorPaginationInput): PayoutRequestConnection!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestConnection> failedPayoutRequestsCursorPagination(
            @InputArgument CursorPaginationInput pagination
    ) {
        log.debug("GraphQL query: failedPayoutRequestsCursorPagination");
        return buildCursorConnection(payoutRequestService.findByStatus(PayoutRequestStatus.FAILED), pagination);
    }

    // ========================================================================
    // STATISTICS QUERY
    // ========================================================================

    /**
     * Get payout request statistics.
     * Schema: payoutRequestStats(organizerId: ID): PayoutRequestStats!
     *
     * <p>OWASP A01:2021 Compliance: Uses OrganizationSecurityService for multi-tenant isolation.</p>
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or (#organizerId != null and @organizationSecurityService.canViewFinancialData(#organizerId, authentication))")
    public Mono<PayoutRequestStats> payoutRequestStats(@InputArgument String organizerId) {
        log.debug("GraphQL query: payoutRequestStats(organizerId={})", organizerId);

        Flux<PayoutRequest> payoutFlux = organizerId != null
                ? payoutRequestService.findByOrganizerId(organizerId)
                : payoutRequestService.findAll();

        return payoutFlux.collectList()
                .map(payouts -> {
                    int total = payouts.size();
                    int pending = (int) payouts.stream().filter(p -> p.getStatus() == PayoutRequestStatus.PENDING).count();
                    int approved = (int) payouts.stream().filter(p -> p.getStatus() == PayoutRequestStatus.APPROVED).count();
                    int processing = (int) payouts.stream().filter(p -> p.getStatus() == PayoutRequestStatus.PROCESSING).count();
                    int completed = (int) payouts.stream().filter(p -> p.getStatus() == PayoutRequestStatus.COMPLETED).count();
                    int failed = (int) payouts.stream().filter(p -> p.getStatus() == PayoutRequestStatus.FAILED).count();

                    // Calculate total payout amount (all requests)
                    BigDecimal totalPayoutAmount = payouts.stream()
                            .map(PayoutRequest::getRequestedAmount)
                            .filter(java.util.Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Calculate pending payout amount (only pending requests)
                    BigDecimal pendingPayoutAmount = payouts.stream()
                            .filter(p -> p.getStatus() == PayoutRequestStatus.PENDING)
                            .map(PayoutRequest::getRequestedAmount)
                            .filter(java.util.Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    return new PayoutRequestStats(
                            total, pending, approved, processing, completed, failed,
                            totalPayoutAmount, pendingPayoutAmount
                    );
                });
    }

    // ========================================================================
    // PAYOUT RECOVERY QUERIES (Admin Dashboard)
    // ========================================================================

    /**
     * Get payout requests that need review with offset pagination.
     * Schema: payoutRequestsForReviewOffsetPagination(reviewStatus: PayoutReviewStatus, pagination: OffsetPaginationInput): PayoutRequestOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestOffsetPage> payoutRequestsForReviewOffsetPagination(
            @InputArgument String reviewStatus,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: payoutRequestsForReviewOffsetPagination(reviewStatus={})", reviewStatus);
        int page = pagination != null ? pagination.page() : 1;
        int size = pagination != null ? pagination.getLimit() : 20;
        int offset = (page - 1) * size;

        Flux<PayoutRequest> payoutFlux = payoutRecoveryService.getPayoutRequestsForReview(reviewStatus, offset / size, size);
        return buildOffsetPageWithTotal(payoutFlux, pagination,
                payoutRecoveryService.countPayoutRequestsForReview(reviewStatus));
    }

    /**
     * Get stuck payout requests with offset pagination.
     * Schema: stuckPayoutRequestsOffsetPagination(pagination: OffsetPaginationInput): PayoutRequestOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestOffsetPage> stuckPayoutRequestsOffsetPagination(
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: stuckPayoutRequestsOffsetPagination");
        int page = pagination != null ? pagination.page() : 1;
        int size = pagination != null ? pagination.getLimit() : 20;
        int offset = (page - 1) * size;

        Flux<PayoutRequest> payoutFlux = payoutRecoveryService.getStuckPayoutRequests(offset / size, size);
        return buildOffsetPageWithTotal(payoutFlux, pagination,
                payoutRecoveryService.countStuckPayoutRequests());
    }

    /**
     * Get payout requests for review with cursor pagination.
     * Schema: payoutRequestsForReviewCursorPagination(reviewStatus: PayoutReviewStatus, pagination: CursorPaginationInput): PayoutRequestConnection!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestConnection> payoutRequestsForReviewCursorPagination(
            @InputArgument String reviewStatus,
            @InputArgument CursorPaginationInput pagination
    ) {
        log.debug("GraphQL query: payoutRequestsForReviewCursorPagination(reviewStatus={})", reviewStatus);
        // Get all payout requests for review (no pagination at service level for cursor pagination)
        Flux<PayoutRequest> payoutFlux = payoutRecoveryService.getPayoutRequestsForReview(reviewStatus, 0, Integer.MAX_VALUE);
        return buildCursorConnection(payoutFlux, pagination);
    }

    /**
     * Get stuck payout requests with cursor pagination.
     * Schema: stuckPayoutRequestsCursorPagination(pagination: CursorPaginationInput): PayoutRequestConnection!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestConnection> stuckPayoutRequestsCursorPagination(
            @InputArgument CursorPaginationInput pagination
    ) {
        log.debug("GraphQL query: stuckPayoutRequestsCursorPagination");
        // Get all stuck payout requests (no pagination at service level for cursor pagination)
        Flux<PayoutRequest> payoutFlux = payoutRecoveryService.getStuckPayoutRequests(0, Integer.MAX_VALUE);
        return buildCursorConnection(payoutFlux, pagination);
    }

    /**
     * Get payout requests by issue type with offset pagination.
     * Schema: payoutRequestsByIssueTypeOffsetPagination(issueType: PayoutIssueType!, pagination: OffsetPaginationInput): PayoutRequestOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestOffsetPage> payoutRequestsByIssueTypeOffsetPagination(
            @InputArgument String issueType,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: payoutRequestsByIssueTypeOffsetPagination(issueType={})", issueType);
        Objects.requireNonNull(issueType, "Issue type is required");
        int page = pagination != null ? pagination.page() : 1;
        int size = pagination != null ? pagination.getLimit() : 20;
        int offset = (page - 1) * size;

        Flux<PayoutRequest> payoutFlux = payoutRecoveryService.getPayoutRequestsByIssueType(issueType, offset / size, size);
        return buildOffsetPageWithTotal(payoutFlux, pagination,
                payoutRequestService.countByIssueType(issueType));
    }

    /**
     * Get payout recovery summary for dashboard.
     * Schema: payoutRecoverySummary: PayoutRecoverySummary!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRecoverySummary> payoutRecoverySummary() {
        log.debug("GraphQL query: payoutRecoverySummary");
        return payoutRecoveryService.getRecoverySummary();
    }

    /**
     * Get retryable failed payout requests with offset pagination.
     * Schema: retryablePayoutRequestsOffsetPagination(pagination: OffsetPaginationInput): PayoutRequestOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestOffsetPage> retryablePayoutRequestsOffsetPagination(
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: retryablePayoutRequestsOffsetPagination");
        return buildOffsetPage(
                payoutRequestService.findRetryable(3),
                pagination
        );
    }

    /**
     * Get recently resolved payout requests with offset pagination.
     * Schema: recentlyResolvedPayoutRequestsOffsetPagination(pagination: OffsetPaginationInput): PayoutRequestOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestOffsetPage> recentlyResolvedPayoutRequestsOffsetPagination(
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: recentlyResolvedPayoutRequestsOffsetPagination");
        java.time.LocalDateTime sevenDaysAgo = java.time.LocalDateTime.now().minusDays(7);
        return buildOffsetPage(
                payoutRequestService.findResolvedAfter(sevenDaysAgo),
                pagination
        );
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private Mono<PayoutRequestOffsetPage> buildOffsetPageWithTotal(
            Flux<PayoutRequest> payoutFlux,
            OffsetPaginationInput pagination,
            Mono<Long> totalCountMono
    ) {
        OffsetPaginationInput p = pagination != null ? pagination : new OffsetPaginationInput(1, 20);
        int limit = p.getLimit();

        return Mono.zip(payoutFlux.collectList(), totalCountMono)
                .map(tuple -> {
                    List<PayoutRequest> paginatedData = tuple.getT1();
                    int totalCount = tuple.getT2().intValue();
                    int totalPages = (int) Math.ceil((double) totalCount / limit);
                    boolean hasNextPage = (p.page() * limit) < totalCount;
                    boolean hasPreviousPage = p.page() > 1;

                    PaginationInfo paginationInfo = new PaginationInfo(
                            totalCount, limit, p.page(), totalPages, hasNextPage, hasPreviousPage
                    );

                    return new PayoutRequestOffsetPage(paginatedData, paginationInfo);
                });
    }

    private Mono<PayoutRequestOffsetPage> buildOffsetPage(Flux<PayoutRequest> payoutFlux, OffsetPaginationInput pagination) {
        OffsetPaginationInput p = pagination != null ? pagination : new OffsetPaginationInput(1, 20);
        int limit = p.getLimit();
        int offset = p.getOffset();

        return payoutFlux.collectList()
                .map(allPayouts -> {
                    int totalCount = allPayouts.size();
                    int totalPages = (int) Math.ceil((double) totalCount / limit);
                    boolean hasNextPage = (offset + limit) < totalCount;
                    boolean hasPreviousPage = p.page() > 1;

                    List<PayoutRequest> paginatedData = allPayouts.stream()
                            .skip(offset)
                            .limit(limit)
                            .toList();

                    PaginationInfo paginationInfo = new PaginationInfo(
                            totalCount, limit, p.page(), totalPages, hasNextPage, hasPreviousPage
                    );

                    return new PayoutRequestOffsetPage(paginatedData, paginationInfo);
                });
    }

    private Mono<PayoutRequestConnection> buildCursorConnection(Flux<PayoutRequest> payoutFlux, CursorPaginationInput pagination) {
        CursorPaginationInput p = pagination != null ? pagination : new CursorPaginationInput(20, null, null, null);
        int limit = p.getLimit();

        return payoutFlux.collectList()
                .map(allPayouts -> {
                    int totalCount = allPayouts.size();

                    int startIndex = 0;
                    if (p.after() != null) {
                        for (int i = 0; i < allPayouts.size(); i++) {
                            if (allPayouts.get(i).getId().equals(p.after())) {
                                startIndex = i + 1;
                                break;
                            }
                        }
                    }

                    List<PayoutRequest> pageData = allPayouts.stream()
                            .skip(startIndex)
                            .limit(limit)
                            .toList();

                    if (pageData.isEmpty()) {
                        return PayoutRequestConnection.empty();
                    }

                    List<PayoutRequestEdge> edges = pageData.stream()
                            .map(PayoutRequestEdge::of)
                            .toList();

                    boolean hasNextPage = (startIndex + limit) < totalCount;
                    boolean hasPreviousPage = startIndex > 0;
                    String startCursor = edges.get(0).cursor();
                    String endCursor = edges.get(edges.size() - 1).cursor();

                    PageInfo pageInfo = PageInfo.of(hasNextPage, hasPreviousPage, startCursor, endCursor, totalCount);

                    return new PayoutRequestConnection(edges, pageInfo, totalCount);
                });
    }

    private Flux<PayoutRequest> applyFilters(Flux<PayoutRequest> payouts, PayoutRequestFilterInput filter) {
        if (filter == null) {
            return payouts;
        }

        return payouts
                .filter(p -> filter.organizerId() == null || filter.organizerId().equals(p.getOrganizerId()))
                .filter(p -> filter.eventId() == null || filter.eventId().equals(p.getEventId()))
                .filter(p -> filter.status() == null || filter.status().equals(p.getStatus()))
                .filter(p -> {
                    if (filter.startDate() == null) return true;
                    return p.getRequestedAt() != null && !p.getRequestedAt().isBefore(filter.startDate().toLocalDateTime());
                })
                .filter(p -> {
                    if (filter.endDate() == null) return true;
                    return p.getRequestedAt() != null && !p.getRequestedAt().isAfter(filter.endDate().toLocalDateTime());
                });
    }
}
