package com.pml.booking.exception;

/**
 * Exception thrown when business validation fails.
 *
 * <p>This exception is used for business rule violations such as:</p>
 * <ul>
 *   <li>Invalid ticket quantities</li>
 *   <li>Event not available for booking</li>
 *   <li>Payment amount mismatches</li>
 *   <li>User not eligible for action</li>
 * </ul>
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A04:2021 - Insecure Design: Generic error messages to prevent information disclosure</li>
 *   <li>A09:2021 - Security Logging: Error codes for internal tracing without exposing details</li>
 * </ul>
 *
 * <p>This exception is configured in Resilience4j circuit breaker as an ignored exception,
 * meaning business validation failures won't trip the circuit breaker (they're not
 * infrastructure failures).</p>
 *
 * @since 1.0.0
 */
public class BusinessValidationException extends RuntimeException {

    private final String errorCode;
    private final String field;

    /**
     * Creates a new BusinessValidationException with a message.
     *
     * @param message User-safe error message (should NOT contain sensitive data)
     */
    public BusinessValidationException(String message) {
        super(message);
        this.errorCode = "VALIDATION_ERROR";
        this.field = null;
    }

    /**
     * Creates a new BusinessValidationException with a message and error code.
     *
     * @param message User-safe error message
     * @param errorCode Internal error code for tracking (e.g., "TICKET_SOLD_OUT")
     */
    public BusinessValidationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.field = null;
    }

    /**
     * Creates a new BusinessValidationException with field-specific validation.
     *
     * @param message User-safe error message
     * @param errorCode Internal error code for tracking
     * @param field The field that failed validation (e.g., "quantity", "eventId")
     */
    public BusinessValidationException(String message, String errorCode, String field) {
        super(message);
        this.errorCode = errorCode;
        this.field = field;
    }

    /**
     * Gets the internal error code for logging and tracking.
     *
     * @return Error code (never null)
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Gets the field that failed validation, if applicable.
     *
     * @return Field name or null if not field-specific
     */
    public String getField() {
        return field;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // FACTORY METHODS FOR COMMON VALIDATION ERRORS
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Creates an exception for sold-out tickets.
     */
    public static BusinessValidationException ticketsSoldOut(String eventId) {
        return new BusinessValidationException(
            "Tickets are no longer available for this event",
            "TICKETS_SOLD_OUT",
            "eventId"
        );
    }

    /**
     * Creates an exception for invalid quantity.
     */
    public static BusinessValidationException invalidQuantity(int requested, int maximum) {
        return new BusinessValidationException(
            String.format("Requested quantity exceeds maximum allowed (%d)", maximum),
            "INVALID_QUANTITY",
            "quantity"
        );
    }

    /**
     * Creates an exception for event not bookable.
     */
    public static BusinessValidationException eventNotBookable(String reason) {
        return new BusinessValidationException(
            "This event is not available for booking: " + reason,
            "EVENT_NOT_BOOKABLE",
            "eventId"
        );
    }

    /**
     * Creates an exception for payment amount mismatch.
     */
    public static BusinessValidationException paymentAmountMismatch() {
        return new BusinessValidationException(
            "Payment amount does not match expected total",
            "PAYMENT_AMOUNT_MISMATCH",
            "amount"
        );
    }

    /**
     * Creates an exception for user not authorized for action.
     */
    public static BusinessValidationException notAuthorized(String action) {
        return new BusinessValidationException(
            "You are not authorized to perform this action",
            "NOT_AUTHORIZED"
        );
    }

    /**
     * Creates an exception for duplicate operation.
     */
    public static BusinessValidationException duplicateOperation(String operation) {
        return new BusinessValidationException(
            "This operation has already been performed",
            "DUPLICATE_OPERATION"
        );
    }
}
