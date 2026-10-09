package com.pml.booking.domain.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Payment Attempt Status - Tracks the lifecycle of a payment operation.
 *
 * <h2>Lifecycle Flow</h2>
 * <pre>
 *                    ┌──────────────────────────────────────────────────────┐
 *                    │                                                      │
 *   CREATED ─────────┼──► PENDING_APPROVAL ──► PROCESSING ──► CONFIRMED ──► COMPLETED
 *      │             │           │                  │              │
 *      │             │           │                  │              │
 *      ▼             │           ▼                  ▼              ▼
 *   REJECTED ◄───────┘        EXPIRED           FAILED         FAILED
 *                                │                               (fulfillment error)
 *                                ▼
 *                            CANCELLED
 * </pre>
 *
 * <h2>Status Descriptions</h2>
 * <ul>
 *   <li><b>CREATED</b>: Record created, PawaPay API not yet called</li>
 *   <li><b>PENDING_APPROVAL</b>: PawaPay accepted, waiting for customer to approve on phone</li>
 *   <li><b>PROCESSING</b>: PawaPay is processing the payment</li>
 *   <li><b>CONFIRMED</b>: Payment confirmed by PawaPay, ready for fulfillment</li>
 *   <li><b>COMPLETED</b>: Fulfillment done (escrow credited, commission created, journal posted)</li>
 *   <li><b>FAILED</b>: Payment failed (customer declined, insufficient funds, etc.)</li>
 *   <li><b>REJECTED</b>: PawaPay rejected our request (invalid phone, unsupported provider, etc.)</li>
 *   <li><b>EXPIRED</b>: 15-minute timeout reached without confirmation</li>
 *   <li><b>CANCELLED</b>: Cancelled by user or system</li>
 * </ul>
 *
 * @since 1.0.0
 */
public enum PaymentAttemptStatus {

    /**
     * Record created, API not yet called.
     * This state survives crashes - depositId is stored before API call.
     */
    CREATED("Created", false),

    /**
     * API called, PawaPay returned ACCEPTED.
     * Waiting for customer to approve on phone (up to 15 minutes).
     */
    PENDING_APPROVAL("Pending Approval", false),

    /**
     * PawaPay is processing the payment.
     * Intermediate state between approval and completion.
     */
    PROCESSING("Processing", false),

    /**
     * Payment confirmed by PawaPay.
     * Ready for fulfillment (escrow, commission, journal).
     */
    CONFIRMED("Confirmed", false),

    /**
     * Fulfillment completed successfully.
     * Escrow credited, commission created, journal entry posted.
     */
    COMPLETED("Completed", true),

    /**
     * Payment failed.
     * Customer declined, insufficient funds, timeout on provider side, etc.
     */
    FAILED("Failed", true),

    /**
     * PawaPay rejected our request.
     * Invalid phone number, unsupported provider, invalid amount, etc.
     */
    REJECTED("Rejected", true),

    /**
     * 15-minute timeout reached without confirmation.
     * Customer did not approve the payment on their phone.
     */
    EXPIRED("Expired", true),

    /**
     * Cancelled by user or system.
     * User navigated away, session ended, or admin cancelled.
     */
    CANCELLED("Cancelled", true);

    private final String displayName;
    private final boolean isFinal;

    PaymentAttemptStatus(String displayName, boolean isFinal) {
        this.displayName = displayName;
        this.isFinal = isFinal;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Whether this is a terminal state (no further transitions possible).
     */
    public boolean isFinal() {
        return isFinal;
    }

    /**
     * Check if transition to target status is valid.
     */
    public boolean canTransitionTo(PaymentAttemptStatus target) {
        if (this.isFinal) {
            return false;
        }
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /**
     * Valid status transitions.
     */
    private static final java.util.Map<PaymentAttemptStatus, Set<PaymentAttemptStatus>> VALID_TRANSITIONS;

    static {
        VALID_TRANSITIONS = new java.util.EnumMap<>(PaymentAttemptStatus.class);

        // CREATED can go to: PENDING_APPROVAL (a buyer must approve a collection on their phone),
        // PROCESSING (a refund, payout or verification deposit the provider accepted), REJECTED,
        // FAILED, CANCELLED
        VALID_TRANSITIONS.put(CREATED, EnumSet.of(
                PENDING_APPROVAL, PROCESSING, REJECTED, FAILED, CANCELLED
        ));

        // PENDING_APPROVAL can go to: PROCESSING, CONFIRMED, FAILED, EXPIRED, CANCELLED
        VALID_TRANSITIONS.put(PENDING_APPROVAL, EnumSet.of(
                PROCESSING, CONFIRMED, FAILED, EXPIRED, CANCELLED
        ));

        // PROCESSING can go to: CONFIRMED, FAILED
        VALID_TRANSITIONS.put(PROCESSING, EnumSet.of(
                CONFIRMED, FAILED
        ));

        // CONFIRMED can go to: COMPLETED, FAILED (if fulfillment fails)
        VALID_TRANSITIONS.put(CONFIRMED, EnumSet.of(
                COMPLETED, FAILED
        ));
    }

    /**
     * Check if this status indicates the payment was successful.
     */
    public boolean isSuccessful() {
        return this == CONFIRMED || this == COMPLETED;
    }

    /**
     * Check if this status indicates the payment is still in progress.
     */
    public boolean isInProgress() {
        return this == CREATED || this == PENDING_APPROVAL || this == PROCESSING || this == CONFIRMED;
    }

    /**
     * Check if this status indicates the payment failed.
     */
    public boolean isUnsuccessful() {
        return this == FAILED || this == REJECTED || this == EXPIRED || this == CANCELLED;
    }

    /**
     * Check if this payment attempt can be retried.
     */
    public boolean canRetry() {
        return this == FAILED || this == EXPIRED;
    }

    /**
     * Check if this payment needs polling (webhook may have been missed).
     */
    public boolean needsPolling() {
        return this == PENDING_APPROVAL || this == PROCESSING;
    }
}
