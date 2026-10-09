package com.pml.booking.service;


import com.pml.booking.domain.model.PayoutRequest;
import com.pml.shared.constants.PayoutRequestStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Collection;

/**
 * Service for managing payout requests to organizers.
 *
 * <h2>Business Context</h2>
 * Payout requests represent pending transfers of event revenue to organizers.
 * They go through a review workflow before being processed by the payment gateway.
 *
 * <h2>Payout Lifecycle</h2>
 * <pre>
 * PENDING → UNDER_REVIEW → APPROVED → PROCESSING → COMPLETED
 *                ↓              ↓           ↓
 *            REJECTED      ON_HOLD      FAILED → (can retry)
 * </pre>
 *
 * <h2>Primary Users</h2>
 * <ul>
 *   <li><b>Organizers</b> - Request and track payouts for their events</li>
 *   <li><b>Finance Team</b> - Review, approve, and process payout requests</li>
 *   <li><b>System (Scheduled Jobs)</b> - Auto-generate payouts when thresholds met</li>
 * </ul>
 *
 * <h2>Admin Operations</h2>
 * <ul>
 *   <li><b>retryablePayoutRequests</b> - View failed payouts eligible for retry</li>
 *   <li><b>stuckPayoutRequests</b> - View payouts stuck in PROCESSING > 24 hours</li>
 *   <li><b>recentlyResolvedPayoutRequests</b> - Audit trail of completed/rejected payouts</li>
 * </ul>
 *
 * @author Booking Service Team
 * @since 1.0
 */
public interface PayoutRequestService {

    // ========================================================================
    // QUERY METHODS
    // ========================================================================

    /**
     * Retrieves a payout request by its database ID.
     *
     * @param id The payout request ID
     * @return Mono containing the payout request or empty if not found
     */
    Mono<PayoutRequest> findById(String id);

    /**
     * Retrieves a payout request by its business request ID.
     * Request IDs are generated with format: PAY-XXXXXXXX
     *
     * @param requestId The business request ID
     * @return Mono containing the payout request or empty if not found
     */
    Mono<PayoutRequest> findByRequestId(String requestId);

    /**
     * Find an existing request by its client-supplied idempotency key.
     *
     * <p>Lets a retried create return the ORIGINAL payout instead of producing
     * a second one for the same money.
     */
    Mono<PayoutRequest> findByIdempotencyKey(String idempotencyKey);

    /**
     * Retrieves all payout requests for an organizer.
     * Used in organizer earnings dashboard.
     *
     * @param organizerId The organizer's unique identifier
     * @return Flux of payout requests for the organizer
     */
    Flux<PayoutRequest> findByOrganizerId(String organizerId);

    /**
     * Retrieves payout requests by status for admin review queues.
     *
     * @param status The payout request status
     * @return Flux of payout requests with the specified status
     */
    Flux<PayoutRequest> findByStatus(PayoutRequestStatus status);

    /**
     * Retrieves payout requests for an event.
     * Used in event financial summary.
     *
     * @param eventId The event ID
     * @return Flux of payout requests for the event
     */
    Flux<PayoutRequest> findByEventId(String eventId);

    /**
     * Whether the event has a payout request that has not yet reached a terminal
     * state — the answer catalog's event cancellation refuses on until it resolves.
     *
     * @param eventId the event
     * @return true while any of the event's payout requests is not {@link PayoutRequestStatus#isFinal()}
     */
    Mono<Boolean> hasOpenPayoutRequest(String eventId);

    /**
     * Retrieves all payout requests.
     * Used for admin payout request list with filtering.
     *
     * @return Flux of all payout requests
     */
    Flux<PayoutRequest> findAll();

    /**
     * Retrieves failed payout requests eligible for retry.
     * A request is eligible if retry count is less than max retries (3).
     *
     * @param maxRetries Maximum retry count
     * @return Flux of retryable payout requests
     */
    Flux<PayoutRequest> findRetryable(int maxRetries);

    /**
     * Retrieves payout requests resolved after a given date.
     * Used for audit trail of recently completed/rejected payouts.
     *
     * @param since The date to filter from
     * @return Flux of recently resolved payout requests
     */
    Flux<PayoutRequest> findResolvedAfter(java.time.Instant since);

    /**
     * Counts payout requests by issue type.
     * Used for recovery dashboard statistics.
     *
     * @param issueType The issue type
     * @return Mono containing the count
     */
    Mono<Long> countByIssueType(String issueType);

    // ========================================================================
    // MUTATION METHODS
    // ========================================================================

    /**
     * Saves a payout request (create or update).
     *
     * @param payoutRequest The payout request to save
     * @return Mono containing the saved payout request
     */
    Mono<PayoutRequest> save(PayoutRequest payoutRequest);

    /**
     * Payout requests across a set of organizations — the caller's own.
     *
     * <p>An empty set matches nothing. That is the point: a caller with no memberships must not
     * fall through to an unfiltered query.
     */
    Flux<PayoutRequest> findByOrganizationIdIn(Collection<String> organizationIds);

    /** As above, narrowed to one status. */
    Flux<PayoutRequest> findByOrganizationIdInAndStatus(Collection<String> organizationIds, PayoutRequestStatus status);
}