package com.pml.booking.service;

import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.web.graphql.dto.CreatePayoutRequestInput;
import com.pml.shared.constants.PayoutRequestStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    Flux<PayoutRequest> findResolvedAfter(java.time.LocalDateTime since);

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
     * Creates a new payout request for an organizer.
     * Validates bank account exists and calculates platform/processing fees.
     *
     * @param input The payout request creation input
     * @param requestedBy The user ID who requested the payout
     * @return Mono containing the created payout request
     */
    Mono<PayoutRequest> create(CreatePayoutRequestInput input, String requestedBy);

    /**
     * Approves a pending payout request.
     * Changes status from PENDING to APPROVED.
     *
     * @param id The payout request ID
     * @param approverId The admin user who approved
     * @param notes Optional approval notes
     * @return Mono containing the approved payout request
     */
    Mono<PayoutRequest> approve(String id, String approverId, String notes);

    /**
     * Rejects a payout request.
     * Changes status to REJECTED and records reason.
     *
     * @param id The payout request ID
     * @param rejectedBy The admin user who rejected
     * @param reason The rejection reason
     * @return Mono containing the rejected payout request
     */
    Mono<PayoutRequest> reject(String id, String rejectedBy, String reason);

    /**
     * Starts processing an approved payout request.
     * Changes status from APPROVED to PROCESSING.
     *
     * @param id The payout request ID
     * @param processedBy The admin user who initiated processing
     * @return Mono containing the payout request being processed
     */
    Mono<PayoutRequest> process(String id, String processedBy);

    /**
     * Completes a payout request after successful bank transfer.
     * Records accounting entries (payout and disbursement) and changes status to COMPLETED.
     *
     * <p>Accounting entries created:</p>
     * <ul>
     *   <li>DR Event Escrow, CR Organizer Payouts Payable (payout recorded)</li>
     *   <li>DR Organizer Payouts Payable, CR Bank Account (disbursement)</li>
     * </ul>
     *
     * @param id The payout request ID
     * @param bankReference The bank transaction reference
     * @param completedBy The admin user who completed the payout
     * @return Mono containing the completed payout request
     */
    Mono<PayoutRequest> complete(String id, String bankReference, String completedBy);

    /**
     * Cancels a payout request.
     * Can only cancel requests in PENDING or APPROVED status.
     *
     * @param id The payout request ID
     * @param cancelledBy The user who cancelled
     * @param reason The cancellation reason
     * @return Mono containing the cancelled payout request
     */
    Mono<PayoutRequest> cancel(String id, String cancelledBy, String reason);

    /**
     * Saves a payout request (create or update).
     *
     * @param payoutRequest The payout request to save
     * @return Mono containing the saved payout request
     */
    Mono<PayoutRequest> save(PayoutRequest payoutRequest);
}
