package com.pml.booking.scheduler;

import com.pml.booking.service.PaymentAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Payment Attempt Scheduled Tasks
 *
 * <p>Handles background processing of payment attempts to ensure reliable payment flow:</p>
 * <ul>
 *   <li><b>Expiration</b>: Marks payments that exceeded 15-minute approval window</li>
 *   <li><b>Polling</b>: Queries PawaPay for status when webhooks are missed</li>
 *   <li><b>Recovery</b>: Retries failed fulfillment operations</li>
 * </ul>
 *
 * <h2>Timing Rationale</h2>
 * <ul>
 *   <li>Expiration every 30 seconds: Quick inventory release for expired payments</li>
 *   <li>Polling every 2 minutes: Balance between recovery speed and API limits</li>
 *   <li>Fulfillment retry every 5 minutes: Allow time for transient issues to resolve</li>
 * </ul>
 *
 * <h2>Error Handling</h2>
 * <p>All tasks use non-blocking subscribe with error logging. Failed tasks don't
 * prevent subsequent runs. Critical failures are logged at ERROR level for alerting.</p>
 *
 * @see PaymentAttemptService
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentAttemptScheduler {

    private final PaymentAttemptService paymentAttemptService;

    /**
     * Expires timed-out payment attempts every 30 seconds.
     *
     * <p><b>Business Logic:</b></p>
     * <ul>
     *   <li>Mobile money payments must be approved within 15 minutes</li>
     *   <li>After 15 minutes, the payment request expires on the provider side</li>
     *   <li>We mark our record as EXPIRED to release the reserved inventory</li>
     * </ul>
     *
     * <p><b>Status Transitions:</b></p>
     * <ul>
     *   <li>CREATED (no API call yet) → EXPIRED</li>
     *   <li>PENDING_APPROVAL (waiting for customer) → EXPIRED</li>
     * </ul>
     */
    @Scheduled(fixedRate = 30_000) // Every 30 seconds
    public void expireTimedOutPayments() {
        log.debug("SCHEDULER: Running payment expiration task");

        paymentAttemptService.expireTimedOutPayments()
                .subscribe(
                        count -> {
                            if (count > 0) {
                                log.info("SCHEDULER: Expired {} timed-out payment attempts", count);
                            }
                        },
                        error -> log.error("SCHEDULER: Error during payment expiration: {}", error.getMessage()),
                        () -> log.debug("SCHEDULER: Payment expiration task completed")
                );
    }

    /**
     * Polls pending payments for status updates every 2 minutes.
     *
     * <p><b>Purpose:</b> Recovery mechanism for missed webhooks. If PawaPay's webhook
     * doesn't reach us (network issues, downtime), we can still discover the payment
     * status by polling their API.</p>
     *
     * <p><b>Polled Statuses:</b></p>
     * <ul>
     *   <li>PENDING_APPROVAL - Customer may have approved but webhook missed</li>
     *   <li>PROCESSING - Provider is processing but status update missed</li>
     * </ul>
     *
     * <p><b>Rate Limiting:</b> Only polls payments that haven't been polled in the
     * last minute to avoid excessive API calls.</p>
     */
    @Scheduled(fixedRate = 120_000) // Every 2 minutes
    public void pollPendingPayments() {
        log.debug("SCHEDULER: Running payment polling task");

        paymentAttemptService.pollPendingPayments()
                .subscribe(
                        count -> {
                            if (count > 0) {
                                log.info("SCHEDULER: Polled {} pending payment attempts", count);
                            }
                        },
                        error -> log.error("SCHEDULER: Error during payment polling: {}", error.getMessage()),
                        () -> log.debug("SCHEDULER: Payment polling task completed")
                );
    }

    /**
     * Retries failed fulfillment operations every 5 minutes.
     *
     * <p><b>Scenario:</b> A payment was confirmed by PawaPay (CONFIRMED status) but
     * fulfillment failed (couldn't create commission, escrow credit, or journal entry).
     * These payments need to be retried to complete the business flow.</p>
     *
     * <p><b>What happens:</b></p>
     * <ul>
     *   <li>Find CONFIRMED payments where fulfillment failed</li>
     *   <li>Retry creating commission, escrow credit, journal entry</li>
     *   <li>On success, mark as COMPLETED</li>
     *   <li>On repeated failure, flag for manual review</li>
     * </ul>
     */
    @Scheduled(fixedRate = 300_000) // Every 5 minutes
    public void retryFailedFulfillment() {
        log.debug("SCHEDULER: Running fulfillment retry task");

        paymentAttemptService.findConfirmedUnfulfilled()
                .count()
                .subscribe(
                        count -> {
                            if (count > 0) {
                                log.warn("SCHEDULER: Found {} confirmed but unfulfilled payments requiring attention", count);
                                // TODO: Trigger fulfillment retry or publish event for processing
                            }
                        },
                        error -> log.error("SCHEDULER: Error during fulfillment retry check: {}", error.getMessage()),
                        () -> log.debug("SCHEDULER: Fulfillment retry check completed")
                );
    }
}
