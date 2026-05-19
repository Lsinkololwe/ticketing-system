package com.pml.booking.exception;

import com.pml.booking.domain.enums.ChargebackStatus;

/**
 * Exception thrown when chargeback processing fails.
 *
 * <p>This exception covers various failures during chargeback handling,
 * including receiving, reviewing, disputing, and resolving chargebacks.</p>
 *
 * <h2>Common Failure Points</h2>
 * <ul>
 *   <li><b>Receiving</b>: Failed to record incoming chargeback notification</li>
 *   <li><b>Dispute Submission</b>: Failed to submit evidence to gateway</li>
 *   <li><b>Status Transition</b>: Invalid state transition attempted</li>
 *   <li><b>Recovery</b>: Failed to recover funds from organizer</li>
 * </ul>
 *
 * <h2>Recovery Actions</h2>
 * <ul>
 *   <li>Check gateway connectivity for submission failures</li>
 *   <li>Verify chargeback status before transitions</li>
 *   <li>Review escrow balance before recovery attempts</li>
 *   <li>Manual intervention may be required</li>
 * </ul>
 *
 * @see com.pml.booking.domain.model.ChargebackRecord
 * @since 1.0.0
 */
public class ChargebackProcessingException extends RuntimeException {

    /**
     * The chargeback ID being processed.
     */
    private final String chargebackId;

    /**
     * The current status of the chargeback.
     */
    private final ChargebackStatus currentStatus;

    /**
     * The attempted operation that failed.
     */
    private final String operation;

    /**
     * Creates a new ChargebackProcessingException with basic message.
     *
     * @param message Error message
     */
    public ChargebackProcessingException(String message) {
        super(message);
        this.chargebackId = null;
        this.currentStatus = null;
        this.operation = null;
    }

    /**
     * Creates a new ChargebackProcessingException with chargeback context.
     *
     * @param chargebackId The chargeback ID
     * @param operation The operation that failed
     * @param message Detailed error message
     */
    public ChargebackProcessingException(
            String chargebackId,
            String operation,
            String message
    ) {
        super(String.format(
                "Chargeback processing failed for %s during %s: %s",
                chargebackId,
                operation,
                message
        ));
        this.chargebackId = chargebackId;
        this.currentStatus = null;
        this.operation = operation;
    }

    /**
     * Creates a new ChargebackProcessingException with full context.
     *
     * @param chargebackId The chargeback ID
     * @param currentStatus Current chargeback status
     * @param operation The operation that failed
     * @param message Detailed error message
     */
    public ChargebackProcessingException(
            String chargebackId,
            ChargebackStatus currentStatus,
            String operation,
            String message
    ) {
        super(String.format(
                "Chargeback %s (status: %s) failed during %s: %s",
                chargebackId,
                currentStatus,
                operation,
                message
        ));
        this.chargebackId = chargebackId;
        this.currentStatus = currentStatus;
        this.operation = operation;
    }

    /**
     * Creates a new ChargebackProcessingException with cause.
     *
     * @param message Error message
     * @param cause Underlying cause
     */
    public ChargebackProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.chargebackId = null;
        this.currentStatus = null;
        this.operation = null;
    }

    /**
     * Creates a new ChargebackProcessingException with full context and cause.
     *
     * @param chargebackId The chargeback ID
     * @param operation The operation that failed
     * @param cause Underlying cause
     */
    public ChargebackProcessingException(
            String chargebackId,
            String operation,
            Throwable cause
    ) {
        super(String.format(
                "Chargeback processing failed for %s during %s",
                chargebackId,
                operation
        ), cause);
        this.chargebackId = chargebackId;
        this.currentStatus = null;
        this.operation = operation;
    }

    /**
     * Static factory for invalid state transition.
     *
     * @param chargebackId The chargeback ID
     * @param currentStatus Current status
     * @param targetStatus Attempted target status
     * @return New ChargebackProcessingException
     */
    public static ChargebackProcessingException invalidTransition(
            String chargebackId,
            ChargebackStatus currentStatus,
            ChargebackStatus targetStatus
    ) {
        return new ChargebackProcessingException(
                chargebackId,
                currentStatus,
                "status_transition",
                String.format("Cannot transition from %s to %s", currentStatus, targetStatus)
        );
    }

    // Getters

    public String getChargebackId() {
        return chargebackId;
    }

    public ChargebackStatus getCurrentStatus() {
        return currentStatus;
    }

    public String getOperation() {
        return operation;
    }
}
