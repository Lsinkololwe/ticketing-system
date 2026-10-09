package com.pml.booking.web.graphql.query;

import com.pml.booking.security.TenantReads;
import java.time.Duration;

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
import com.pml.booking.security.CallerScope;
import com.pml.shared.error.ErrorCode;

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
    private final TenantReads tenantReads;

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;
    private final PayoutRecoveryService payoutRecoveryService;

    // ========================================================================
    // SINGLE ENTITY QUERIES
    // ========================================================================

    /**
     * Get a payout request by ID.
     * Schema: payoutRequest(id: ID!): PayoutRequest
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'ORGANIZER')")
    public Mono<PayoutRequest> payoutRequest(@InputArgument String id) {
        log.debug("GraphQL query: payoutRequest(id={})", id);
        Objects.requireNonNull(id, "Payout request ID is required");
        return tenantReads.payoutRequestForCaller(id);
    }

    /**
     * Get a payout request by request ID.
     * Schema: payoutRequestByRequestId(requestId: String!): PayoutRequest
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'ORGANIZER')")
    public Mono<PayoutRequest> payoutRequestByRequestId(@InputArgument String requestId) {
        log.debug("GraphQL query: payoutRequestByRequestId({})", requestId);
        Objects.requireNonNull(requestId, "Request ID is required");
        return tenantReads.payoutRequestByRequestIdForCaller(requestId);
    }

    // ========================================================================
    // OFFSET PAGINATION QUERIES (Admin Tables)
    // ========================================================================

    /**
     * Search payout requests with offset pagination.
     * Schema: payoutRequests(filter: PayoutRequestFilterInput!, pagination: OffsetPaginationInput): PayoutRequestOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestOffsetPage> payoutRequests(
            @InputArgument PayoutRequestFilterInput filter,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: payoutRequests");
        Objects.requireNonNull(filter, "Filter is required");

        Flux<PayoutRequest> payoutFlux = applyFilters(payoutRequestService.findAll(), filter);
        return buildOffsetPage(payoutFlux, pagination);
    }

    /**
     * Get payout requests by organizer with offset pagination.
     * Schema: payoutRequestsByOrganizer(organizerId: String!, pagination: OffsetPaginationInput): PayoutRequestOffsetPage!
     *
     * <p>OWASP A01:2021 Compliance: Uses OrganizationSecurityService for multi-tenant isolation.</p>
     */
    @DgsQuery
    @PreAuthorize("@organizationSecurityService.rolesOrFinancialView(authentication, 'ADMIN,FINANCE', #organizerId)")
    public Mono<PayoutRequestOffsetPage> payoutRequestsByOrganizer(
            @InputArgument String organizerId,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: payoutRequestsByOrganizer(organizerId={})", organizerId);
        Objects.requireNonNull(organizerId, "Organizer ID is required");

        return buildOffsetPage(payoutRequestService.findByOrganizerId(organizerId), pagination);
    }

    /**
     * Get payout requests by event with offset pagination.
     * Schema: payoutRequestsByEvent(eventId: String!, pagination: OffsetPaginationInput): PayoutRequestOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @eventSecurityService.isEventOrganizer(#eventId, authentication)")
    public Mono<PayoutRequestOffsetPage> payoutRequestsByEvent(
            @InputArgument String eventId,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: payoutRequestsByEvent(eventId={})", eventId);
        Objects.requireNonNull(eventId, "Event ID is required");

        return buildOffsetPage(payoutRequestService.findByEventId(eventId), pagination);
    }

    /**
     * Get pending payout requests with offset pagination.
     * Schema: pendingPayoutRequests(pagination: OffsetPaginationInput): PayoutRequestOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestOffsetPage> pendingPayoutRequests(
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: pendingPayoutRequests");
        return buildOffsetPage(payoutRequestService.findByStatus(PayoutRequestStatus.PENDING), pagination);
    }

    /**
     * Get failed payout requests with offset pagination.
     * Schema: failedPayoutRequests(pagination: OffsetPaginationInput): PayoutRequestOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestOffsetPage> failedPayoutRequests(
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: failedPayoutRequests");
        return buildOffsetPage(payoutRequestService.findByStatus(PayoutRequestStatus.FAILED), pagination);
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
    @PreAuthorize("@organizationSecurityService.rolesOrFinancialView(authentication, 'ADMIN,FINANCE', #organizerId)")
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
     * Schema: payoutRequestsForReview(reviewStatus: PayoutReviewStatus, pagination: OffsetPaginationInput): PayoutRequestOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestOffsetPage> payoutRequestsForReview(
            @InputArgument String reviewStatus,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: payoutRequestsForReview(reviewStatus={})", reviewStatus);
        // 0-based, like OffsetPaginationInput documents ("page 0, 1, 2, 3..."),
        // like its own getOffset(), and like every sibling resolver that goes
        // through buildOffsetPage. This block used to read `: 1` and
        // `(page - 1) * size`, so the input type's own default of page=0 gave
        // offset -20 and the query answered
        // "Page index must not be less than zero" — it could only be called by
        // a client that ignored the schema's contract.
        int size = pagination != null ? pagination.getLimit() : 20;
        int pageIndex = pagination != null ? pagination.page() : 0;

        Flux<PayoutRequest> payoutFlux = payoutRecoveryService.getPayoutRequestsForReview(reviewStatus, pageIndex, size);
        return buildOffsetPageWithTotal(payoutFlux, pagination,
                payoutRecoveryService.countPayoutRequestsForReview(reviewStatus));
    }

    /**
     * Get stuck payout requests with offset pagination.
     * Schema: stuckPayoutRequests(pagination: OffsetPaginationInput): PayoutRequestOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestOffsetPage> stuckPayoutRequests(
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: stuckPayoutRequests");
        // 0-based, like OffsetPaginationInput documents ("page 0, 1, 2, 3..."),
        // like its own getOffset(), and like every sibling resolver that goes
        // through buildOffsetPage. This block used to read `: 1` and
        // `(page - 1) * size`, so the input type's own default of page=0 gave
        // offset -20 and the query answered
        // "Page index must not be less than zero" — it could only be called by
        // a client that ignored the schema's contract.
        int size = pagination != null ? pagination.getLimit() : 20;
        int pageIndex = pagination != null ? pagination.page() : 0;

        Flux<PayoutRequest> payoutFlux = payoutRecoveryService.getStuckPayoutRequests(pageIndex, size);
        return buildOffsetPageWithTotal(payoutFlux, pagination,
                payoutRecoveryService.countStuckPayoutRequests());
    }

    /**
     * Get payout requests by issue type with offset pagination.
     * Schema: payoutRequestsByIssueType(issueType: PayoutIssueType!, pagination: OffsetPaginationInput): PayoutRequestOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestOffsetPage> payoutRequestsByIssueType(
            @InputArgument String issueType,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: payoutRequestsByIssueType(issueType={})", issueType);
        Objects.requireNonNull(issueType, "Issue type is required");
        // 0-based, like OffsetPaginationInput documents ("page 0, 1, 2, 3..."),
        // like its own getOffset(), and like every sibling resolver that goes
        // through buildOffsetPage. This block used to read `: 1` and
        // `(page - 1) * size`, so the input type's own default of page=0 gave
        // offset -20 and the query answered
        // "Page index must not be less than zero" — it could only be called by
        // a client that ignored the schema's contract.
        int size = pagination != null ? pagination.getLimit() : 20;
        int pageIndex = pagination != null ? pagination.page() : 0;

        Flux<PayoutRequest> payoutFlux = payoutRecoveryService.getPayoutRequestsByIssueType(issueType, pageIndex, size);
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
     * Schema: retryablePayoutRequests(pagination: OffsetPaginationInput): PayoutRequestOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestOffsetPage> retryablePayoutRequests(
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: retryablePayoutRequests");
        return buildOffsetPage(
                payoutRequestService.findRetryable(3),
                pagination
        );
    }

    /**
     * Get recently resolved payout requests with offset pagination.
     * Schema: recentlyResolvedPayoutRequests(pagination: OffsetPaginationInput): PayoutRequestOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PayoutRequestOffsetPage> recentlyResolvedPayoutRequests(
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: recentlyResolvedPayoutRequests");
        java.time.Instant sevenDaysAgo = clock.instant().minus(Duration.ofDays(7));
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
        OffsetPaginationInput p = pagination != null ? pagination : OffsetPaginationInput.defaults();
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
        OffsetPaginationInput p = pagination != null ? pagination : OffsetPaginationInput.defaults();
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
    private Flux<PayoutRequest> applyFilters(Flux<PayoutRequest> payouts, PayoutRequestFilterInput filter) {
        if (filter == null) {
            return payouts;
        }

        return payouts
                .filter(p -> filter.organizerId() == null || filter.organizerId().equals(p.getOrganizerId()))
                .filter(p -> filter.eventId() == null || filter.eventId().equals(p.getEventId()))
                .filter(p -> filter.escrowAccountId() == null || filter.escrowAccountId().equals(p.getEscrowAccountId()))
                .filter(p -> filter.status() == null || filter.status().equals(com.pml.booking.domain.PayoutStatusView.of(p)))
                .filter(p -> filter.payoutMethod() == null || filter.payoutMethod() == p.getPayoutMethod())
                .filter(p -> {
                    if (filter.startDate() == null) return true;
                    return p.getRequestedAt() != null && !p.getRequestedAt().isBefore(filter.startDate().toInstant());
                })
                .filter(p -> {
                    if (filter.endDate() == null) return true;
                    return p.getRequestedAt() != null && !p.getRequestedAt().isAfter(filter.endDate().toInstant());
                });
    }

    /**
     * The caller's own payout requests. OWASP A01:2021.
     *
     * <p>{@code organizationId} narrows to one of the caller's organizations; omitted, it means
     * all of them. It cannot widen: {@link CallerScope#organizationIds} resolves it against the
     * memberships the token established, and a selector naming another tenant refuses with
     * {@code ORGANIZATION_UNKNOWN} — the code an organization that was never issued produces, so
     * the two are indistinguishable.
     *
     * <p>{@code payoutRequestsByOrganizer} is not a substitute — there the client-supplied id
     * decides whose data comes back, which is the CWE-639 shape this operation avoids.
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<PayoutRequestOffsetPage> myPayoutRequests(
            @InputArgument String organizationId,
            @InputArgument PayoutRequestStatus status,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: myPayoutRequests(organizationId={}, status={})", organizationId, status);

        return CallerScope.organizationIds(organizationId, ErrorCode.ORGANIZATION_UNKNOWN)
                .flatMap(ids -> buildOffsetPage(
                        status == null
                                ? payoutRequestService.findByOrganizationIdIn(ids)
                                : payoutRequestService.findByOrganizationIdInAndStatus(ids, status),
                        pagination));
    }
}