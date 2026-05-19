package com.pml.booking.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.enums.ChargebackStatus;
import com.pml.booking.domain.enums.RecoveryStatus;
import com.pml.booking.domain.model.ChargebackRecord;
import com.pml.booking.service.ChargebackService;
import com.pml.booking.web.graphql.dto.ChargebackFilterInput;
import com.pml.booking.web.graphql.dto.ChargebackOffsetPage;
import com.pml.booking.web.graphql.dto.ChargebackStats;
import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import com.pml.booking.web.graphql.dto.PaginationInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

/**
 * GraphQL Query Resolver for Chargeback Operations.
 *
 * <p>Provides read access to chargeback records for financial administrators.
 * Chargebacks are involuntary refunds initiated by customers through their
 * payment provider.</p>
 *
 * <h2>Chargeback Lifecycle</h2>
 * <ul>
 *   <li>RECEIVED: Chargeback received from payment provider</li>
 *   <li>UNDER_REVIEW: Being reviewed by platform</li>
 *   <li>ACCEPTED: Platform accepts the chargeback</li>
 *   <li>DISPUTED: Platform disputes with evidence</li>
 *   <li>WON: Dispute won, funds retained</li>
 *   <li>LOST: Dispute lost, funds must be recovered</li>
 * </ul>
 *
 * <h2>Recovery Waterfall</h2>
 * <ol>
 *   <li>ORGANIZER_ESCROW: Deduct from organizer's current escrow</li>
 *   <li>ORGANIZER_FUTURE: Hold against future payouts</li>
 *   <li>PLATFORM_RESERVE: Use platform reserve funds</li>
 *   <li>WRITE_OFF: Write off as bad debt</li>
 * </ol>
 *
 * @since 1.0.0
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class ChargebackQueryResolver {

    private final ChargebackService chargebackService;

    // ========================================================================
    // SINGLE ENTITY QUERIES
    // ========================================================================

    /**
     * Get a chargeback record by ID.
     * Schema: chargeback(id: ID!): ChargebackRecord
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ChargebackRecord> chargeback(@InputArgument String id) {
        log.debug("GraphQL query: chargeback(id={})", id);
        Objects.requireNonNull(id, "Chargeback ID is required");
        return chargebackService.findById(id);
    }

    /**
     * Get a chargeback record by external chargeback ID.
     * Schema: chargebackByChargebackId(chargebackId: String!): ChargebackRecord
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ChargebackRecord> chargebackByChargebackId(@InputArgument String chargebackId) {
        log.debug("GraphQL query: chargebackByChargebackId({})", chargebackId);
        Objects.requireNonNull(chargebackId, "Chargeback ID is required");
        return chargebackService.findByChargebackId(chargebackId);
    }

    // ========================================================================
    // LIST QUERIES
    // ========================================================================

    /**
     * Get pending chargebacks (RECEIVED or UNDER_REVIEW).
     * Schema: pendingChargebacks: [ChargebackRecord!]!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<List<ChargebackRecord>> pendingChargebacks() {
        log.debug("GraphQL query: pendingChargebacks");
        return Flux.merge(
                chargebackService.findByStatus(ChargebackStatus.RECEIVED),
                chargebackService.findByStatus(ChargebackStatus.UNDER_REVIEW)
        ).collectList();
    }

    /**
     * Get chargebacks pending recovery.
     * Schema: chargebacksPendingRecovery: [ChargebackRecord!]!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<List<ChargebackRecord>> chargebacksPendingRecovery() {
        log.debug("GraphQL query: chargebacksPendingRecovery");
        return chargebackService.findPendingRecovery().collectList();
    }

    // ========================================================================
    // OFFSET PAGINATION QUERIES
    // ========================================================================

    /**
     * Get chargebacks with filtering and offset pagination.
     * Schema: chargebacksOffsetPagination(filter: ChargebackFilterInput, pagination: OffsetPaginationInput): ChargebackOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ChargebackOffsetPage> chargebacksOffsetPagination(
            @InputArgument ChargebackFilterInput filter,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: chargebacksOffsetPagination");

        Flux<ChargebackRecord> chargebackFlux = getFilteredChargebacks(filter);
        return chargebackFlux.collectList()
                .map(chargebacks -> buildOffsetPage(chargebacks, pagination));
    }

    /**
     * Get chargebacks by organizer with offset pagination.
     * Schema: chargebacksByOrganizer(organizerId: String!, pagination: OffsetPaginationInput): ChargebackOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ChargebackOffsetPage> chargebacksByOrganizer(
            @InputArgument String organizerId,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: chargebacksByOrganizer({})", organizerId);
        Objects.requireNonNull(organizerId, "Organizer ID is required");

        return chargebackService.findByOrganizerId(organizerId)
                .collectList()
                .map(chargebacks -> buildOffsetPage(chargebacks, pagination));
    }

    /**
     * Get chargebacks by event with offset pagination.
     * Schema: chargebacksByEvent(eventId: String!, pagination: OffsetPaginationInput): ChargebackOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ChargebackOffsetPage> chargebacksByEvent(
            @InputArgument String eventId,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: chargebacksByEvent({})", eventId);
        Objects.requireNonNull(eventId, "Event ID is required");

        return chargebackService.findByEventId(eventId)
                .collectList()
                .map(chargebacks -> buildOffsetPage(chargebacks, pagination));
    }

    // ========================================================================
    // STATISTICS QUERIES
    // ========================================================================

    /**
     * Get chargeback statistics.
     * Schema: chargebackStats(organizerId: String, eventId: String): ChargebackStats!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ChargebackService.ChargebackStats> chargebackStats(
            @InputArgument String organizerId,
            @InputArgument String eventId
    ) {
        log.debug("GraphQL query: chargebackStats(organizerId={}, eventId={})", organizerId, eventId);
        if (organizerId != null) {
            return chargebackService.getOrganizerStats(organizerId);
        }
        return chargebackService.getPlatformStats();
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Get filtered chargebacks based on filter input.
     */
    private Flux<ChargebackRecord> getFilteredChargebacks(ChargebackFilterInput filter) {
        if (filter == null || !filter.hasFilters()) {
            return chargebackService.findAll();
        }

        if (filter.organizerId() != null) {
            return chargebackService.findByOrganizerId(filter.organizerId());
        }

        if (filter.eventId() != null) {
            return chargebackService.findByEventId(filter.eventId());
        }

        if (filter.status() != null) {
            return chargebackService.findByStatus(filter.status());
        }

        return chargebackService.findAll();
    }

    /**
     * Build ChargebackOffsetPage from a list of chargebacks.
     */
    private ChargebackOffsetPage buildOffsetPage(
            List<ChargebackRecord> allChargebacks,
            OffsetPaginationInput pagination
    ) {
        OffsetPaginationInput p = pagination != null ? pagination : new OffsetPaginationInput(1, 20);
        int limit = p.getLimit();
        int offset = p.getOffset();

        int totalCount = allChargebacks.size();
        int totalPages = (int) Math.ceil((double) totalCount / limit);
        boolean hasNextPage = (offset + limit) < totalCount;
        boolean hasPreviousPage = p.page() > 1;

        List<ChargebackRecord> paginatedData = allChargebacks.stream()
                .skip(offset)
                .limit(limit)
                .toList();

        PaginationInfo paginationInfo = new PaginationInfo(
                totalCount,
                limit,
                p.page(),
                totalPages,
                hasNextPage,
                hasPreviousPage
        );

        return new ChargebackOffsetPage(paginatedData, paginationInfo);
    }
}
