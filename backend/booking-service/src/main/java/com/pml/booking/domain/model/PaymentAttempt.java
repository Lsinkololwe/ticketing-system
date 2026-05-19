package com.pml.booking.domain.model;

import com.pml.booking.domain.enums.PaymentAttemptStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Payment Attempt - Tracks the complete lifecycle of a payment operation.
 *
 * <p>This is the OPERATIONAL layer that tracks HOW a payment progresses through
 * the payment gateway (PawaPay), distinct from {@link JournalEntry} which records
 * THAT money moved in the accounting ledger.</p>
 *
 * <h2>Key Responsibilities</h2>
 * <ul>
 *   <li>Store depositId BEFORE calling PawaPay (enables crash recovery)</li>
 *   <li>Track external API interactions (call time, response, errors)</li>
 *   <li>Record webhook receipt and processing</li>
 *   <li>Enable status polling for missed webhooks</li>
 *   <li>Support idempotency via unique depositId</li>
 *   <li>Provide audit trail for support investigations</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * <pre>
 * 1. User initiates payment → CREATE PaymentAttempt (status: CREATED)
 * 2. Call PawaPay API → UPDATE (status: PENDING_APPROVAL or REJECTED)
 * 3. Wait for webhook/poll → UPDATE (status: CONFIRMED or FAILED)
 * 4. Fulfill order → UPDATE (status: COMPLETED)
 * </pre>
 *
 * <h2>Relationship to Other Models</h2>
 * <ul>
 *   <li>{@link JournalEntry}: Created AFTER payment is CONFIRMED</li>
 *   <li>{@link EventEscrowAccount}: Credited AFTER payment is CONFIRMED</li>
 *   <li>{@link Ticket}: Updated to PURCHASED AFTER payment is CONFIRMED</li>
 * </ul>
 *
 * @see PaymentAttemptStatus
 * @see JournalEntry
 * @since 1.0.0
 */
@Document(collection = "payment_attempts")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    @CompoundIndex(name = "status_created_idx", def = "{'status': 1, 'createdAt': 1}"),
    @CompoundIndex(name = "status_expires_idx", def = "{'status': 1, 'expiresAt': 1}"),
    @CompoundIndex(name = "ticket_status_idx", def = "{'ticketId': 1, 'status': 1}"),
    @CompoundIndex(name = "event_status_idx", def = "{'eventId': 1, 'status': 1}"),
    @CompoundIndex(name = "buyer_created_idx", def = "{'buyerId': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "provider_txn_idx", def = "{'providerTransactionId': 1}")
})
public class PaymentAttempt {

    // ════════════════════════════════════════════════════════════════════════
    // IDENTITY & CORRELATION
    // ════════════════════════════════════════════════════════════════════════

    /**
     * MongoDB document ID.
     */
    @Id
    private String id;

    /**
     * UUID sent to PawaPay as depositId.
     * <p>
     * Generated BEFORE API call for crash recovery. This is the IDEMPOTENCY KEY.
     * If the system crashes after generating this but before calling PawaPay,
     * the same depositId can be used to retry safely.
     * </p>
     * <p>Format: UUIDv4, e.g., "f4401bd2-1568-4140-bf2d-eb77d2b2b639"</p>
     */
    @NotBlank(message = "Deposit ID is required")
    @Indexed(unique = true)
    private String depositId;

    /**
     * Human-readable payment attempt number.
     * <p>Format: PAY-{YYYYMMDD}-{XXXXX}</p>
     * <p>Example: PAY-20240115-00001</p>
     */
    @Indexed(unique = true)
    private String attemptNumber;

    /**
     * Correlation ID for tracing related operations.
     * <p>Links this payment to its ticket reservation, webhook, fulfillment, etc.</p>
     */
    @Indexed
    private String correlationId;

    // ════════════════════════════════════════════════════════════════════════
    // BUSINESS ENTITY REFERENCES
    // ════════════════════════════════════════════════════════════════════════

    /**
     * The ticket being purchased.
     */
    @NotBlank(message = "Ticket ID is required")
    @Indexed
    private String ticketId;

    /**
     * The event the ticket is for.
     */
    @Indexed
    private String eventId;

    /**
     * The organizer of the event.
     */
    @Indexed
    private String organizerId;

    /**
     * Organization ID for multi-tenant payment tracking.
     * Critical for:
     * - Organization-level payment reports
     * - Financial reconciliation by organization
     * - Revenue analytics across organization's events
     *
     * OWASP A01:2021 Compliance: Used for tenant isolation in authorization.
     */
    @Indexed
    private String organizationId;

    /**
     * The user making the payment.
     */
    @NotBlank(message = "Buyer ID is required")
    @Indexed
    private String buyerId;

    /**
     * Client reference sent to PawaPay for reconciliation.
     * <p>Typically the ticket number, e.g., "TKT-ABC123"</p>
     */
    private String clientReferenceId;

    // ════════════════════════════════════════════════════════════════════════
    // PAYMENT DETAILS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Payment amount.
     */
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;

    /**
     * Currency code (ISO 4217).
     */
    @NotBlank(message = "Currency is required")
    @Builder.Default
    private String currency = "ZMW";

    /**
     * Mobile money provider code.
     * <p>Examples: MTN_MOMO_ZMB, AIRTEL_OAPI_ZMB, ZAMTEL_ZMB</p>
     *
     * @see <a href="https://docs.pawapay.io/providers">PawaPay Providers</a>
     */
    @NotBlank(message = "Provider is required")
    private String provider;

    /**
     * Payer phone number in E.164 format.
     * <p>Example: +260763456789</p>
     */
    @NotBlank(message = "Payer phone is required")
    private String payerPhone;

    /**
     * Customer message sent to PawaPay (4-22 characters).
     * <p>Appears on customer's mobile money statement.</p>
     */
    private String customerMessage;

    // ════════════════════════════════════════════════════════════════════════
    // STATUS TRACKING
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Current status of this payment attempt.
     */
    @NotNull(message = "Status is required")
    @Indexed
    @Builder.Default
    private PaymentAttemptStatus status = PaymentAttemptStatus.CREATED;

    /**
     * PawaPay's status from their response/webhook.
     * <p>Values: ACCEPTED, PROCESSING, COMPLETED, FAILED, IN_RECONCILIATION</p>
     */
    private String providerStatus;

    /**
     * PawaPay's transaction ID (set after COMPLETED).
     * <p>This is their internal reference for the completed transaction.</p>
     */
    @Indexed
    private String providerTransactionId;

    /**
     * Country code where payment was processed.
     * <p>Example: ZMB</p>
     */
    private String country;

    // ════════════════════════════════════════════════════════════════════════
    // FAILURE TRACKING
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Failure code from PawaPay.
     * <p>Examples: PAYER_LIMIT_REACHED, INSUFFICIENT_BALANCE, TRANSACTION_TIMEOUT</p>
     */
    private String failureCode;

    /**
     * Human-readable failure message from PawaPay.
     * <p>Intended for internal use, not customer-facing.</p>
     */
    private String failureMessage;

    /**
     * Customer-friendly error message.
     */
    private String customerErrorMessage;

    // ════════════════════════════════════════════════════════════════════════
    // API INTERACTION TRACKING
    // ════════════════════════════════════════════════════════════════════════

    /**
     * When we called POST /v2/deposits.
     */
    private Instant apiCalledAt;

    /**
     * When PawaPay responded to our API call.
     */
    private Instant apiRespondedAt;

    /**
     * HTTP status code from PawaPay API call.
     */
    private Integer apiHttpStatus;

    /**
     * Raw API response body (for debugging).
     * <p>Stored as JSON string, truncated if too large.</p>
     */
    private String apiResponseBody;

    /**
     * API request duration in milliseconds.
     */
    private Long apiDurationMs;

    // ════════════════════════════════════════════════════════════════════════
    // WEBHOOK TRACKING
    // ════════════════════════════════════════════════════════════════════════

    /**
     * When we received the webhook callback from PawaPay.
     */
    private Instant webhookReceivedAt;

    /**
     * Raw webhook payload (for audit trail).
     */
    private String webhookPayload;

    /**
     * Whether webhook was successfully processed.
     */
    @Builder.Default
    private boolean webhookProcessed = false;

    /**
     * Whether webhook signature was valid.
     */
    private Boolean webhookSignatureValid;

    /**
     * Source IP of webhook request.
     */
    private String webhookSourceIp;

    // ════════════════════════════════════════════════════════════════════════
    // POLLING & RECOVERY
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Number of times we polled GET /v2/deposits/{id}.
     */
    @Builder.Default
    private int pollCount = 0;

    /**
     * When we last polled the status.
     */
    private Instant lastPolledAt;

    /**
     * Result of last poll (FOUND, NOT_FOUND, ERROR).
     */
    private String lastPollResult;

    /**
     * Status from last poll (if FOUND).
     */
    private String lastPollStatus;

    /**
     * Number of retry attempts for failed API calls.
     */
    @Builder.Default
    private int retryCount = 0;

    /**
     * When eligible for next retry.
     */
    private Instant nextRetryAt;

    /**
     * Last error encountered during processing.
     */
    private String lastError;

    // ════════════════════════════════════════════════════════════════════════
    // FULFILLMENT TRACKING
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Whether fulfillment (escrow, commission, journal) has been completed.
     */
    @Builder.Default
    private boolean fulfilled = false;

    /**
     * When fulfillment completed.
     */
    private Instant fulfilledAt;

    /**
     * Journal entry ID created for this payment.
     */
    private String journalEntryId;

    /**
     * Commission ID created for this payment.
     */
    private String commissionId;

    /**
     * Escrow transaction ID created for this payment.
     */
    private String escrowTransactionId;

    // ════════════════════════════════════════════════════════════════════════
    // VERIFICATION FLAGS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Whether we verified the payment with PawaPay API before fulfillment.
     * <p>OWASP requirement: Never trust webhook alone.</p>
     */
    @Builder.Default
    private boolean verifiedBeforeFulfillment = false;

    /**
     * When verification was performed.
     */
    private Instant verifiedAt;

    /**
     * Whether amount was verified to match expected.
     */
    @Builder.Default
    private boolean amountVerified = false;

    // ════════════════════════════════════════════════════════════════════════
    // SECURITY & AUDIT (OWASP)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * IP address of user initiating payment.
     */
    private String clientIpAddress;

    /**
     * User agent string from client.
     */
    private String clientUserAgent;

    /**
     * Session ID for tracing.
     */
    private String sessionId;

    /**
     * Request ID for distributed tracing.
     */
    private String requestId;

    /**
     * Device fingerprint (if available).
     */
    private String deviceFingerprint;

    // ════════════════════════════════════════════════════════════════════════
    // METADATA & NOTES
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Additional metadata sent to PawaPay.
     */
    private Map<String, Object> metadata;

    /**
     * Internal notes (timestamped audit log).
     */
    private String notes;

    /**
     * Review status for manual investigation.
     */
    private String reviewStatus;

    /**
     * Who reviewed this payment attempt.
     */
    private String reviewedBy;

    /**
     * When review was performed.
     */
    private Instant reviewedAt;

    /**
     * Review notes.
     */
    private String reviewNotes;

    // ════════════════════════════════════════════════════════════════════════
    // TIMESTAMPS & VERSIONING
    // ════════════════════════════════════════════════════════════════════════

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /**
     * When this attempt expires (15 minutes after creation for PawaPay).
     */
    @Indexed
    private Instant expiresAt;

    /**
     * Version for optimistic locking (TOCTOU prevention).
     */
    @Version
    private Long version;

    // ════════════════════════════════════════════════════════════════════════
    // LIFECYCLE METHODS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Transition to a new status with validation.
     *
     * @param newStatus Target status
     * @throws IllegalStateException if transition is not valid
     */
    public void transitionTo(PaymentAttemptStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(String.format(
                    "Invalid status transition: %s → %s for payment attempt %s",
                    this.status, newStatus, this.depositId
            ));
        }
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    /**
     * Mark as submitted to PawaPay API.
     */
    public void markApiCalled(int httpStatus, String responseBody) {
        this.apiCalledAt = Instant.now();
        this.apiHttpStatus = httpStatus;
        this.apiResponseBody = truncate(responseBody, 4000);
    }

    /**
     * Mark API response received.
     */
    public void markApiResponded(String providerStatus) {
        this.apiRespondedAt = Instant.now();
        this.providerStatus = providerStatus;
        if (this.apiCalledAt != null) {
            this.apiDurationMs = java.time.Duration.between(apiCalledAt, apiRespondedAt).toMillis();
        }
    }

    /**
     * Record webhook receipt.
     */
    public void recordWebhook(String payload, String sourceIp, boolean signatureValid) {
        this.webhookReceivedAt = Instant.now();
        this.webhookPayload = truncate(payload, 8000);
        this.webhookSourceIp = sourceIp;
        this.webhookSignatureValid = signatureValid;
    }

    /**
     * Mark webhook as processed.
     */
    public void markWebhookProcessed() {
        this.webhookProcessed = true;
        this.updatedAt = Instant.now();
    }

    /**
     * Record a poll attempt.
     */
    public void recordPoll(String result, String status) {
        this.pollCount++;
        this.lastPolledAt = Instant.now();
        this.lastPollResult = result;
        this.lastPollStatus = status;
        this.updatedAt = Instant.now();
    }

    /**
     * Mark as confirmed by PawaPay.
     */
    public void markConfirmed(String providerTransactionId) {
        this.providerTransactionId = providerTransactionId;
        this.providerStatus = "COMPLETED";
        transitionTo(PaymentAttemptStatus.CONFIRMED);
    }

    /**
     * Mark as failed.
     */
    public void markFailed(String failureCode, String failureMessage) {
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.providerStatus = "FAILED";
        if (this.status.canTransitionTo(PaymentAttemptStatus.FAILED)) {
            transitionTo(PaymentAttemptStatus.FAILED);
        }
    }

    /**
     * Mark as rejected by PawaPay.
     */
    public void markRejected(String failureCode, String failureMessage) {
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.providerStatus = "REJECTED";
        transitionTo(PaymentAttemptStatus.REJECTED);
    }

    /**
     * Mark as expired (15-minute timeout).
     */
    public void markExpired() {
        this.failureCode = "TIMEOUT";
        this.failureMessage = "Customer did not approve payment within 15 minutes";
        transitionTo(PaymentAttemptStatus.EXPIRED);
    }

    /**
     * Mark verification completed before fulfillment.
     */
    public void markVerified(boolean amountMatches) {
        this.verifiedBeforeFulfillment = true;
        this.verifiedAt = Instant.now();
        this.amountVerified = amountMatches;
    }

    /**
     * Mark fulfillment completed.
     */
    public void markFulfilled(String journalEntryId, String commissionId, String escrowTransactionId) {
        this.fulfilled = true;
        this.fulfilledAt = Instant.now();
        this.journalEntryId = journalEntryId;
        this.commissionId = commissionId;
        this.escrowTransactionId = escrowTransactionId;
        transitionTo(PaymentAttemptStatus.COMPLETED);
    }

    /**
     * Schedule a retry.
     */
    public void scheduleRetry(int delayMinutes) {
        this.retryCount++;
        this.nextRetryAt = Instant.now().plusSeconds(delayMinutes * 60L);
        this.updatedAt = Instant.now();
    }

    /**
     * Add a timestamped note.
     */
    public void addNote(String author, String noteText) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String formattedNote = String.format("[%s] %s: %s", timestamp, author, noteText);

        if (this.notes == null || this.notes.isBlank()) {
            this.notes = formattedNote;
        } else {
            this.notes = this.notes + "\n" + formattedNote;
        }
        this.updatedAt = Instant.now();
    }

    /**
     * Get notes as list.
     */
    public List<String> getNotesAsList() {
        if (this.notes == null || this.notes.isBlank()) {
            return new ArrayList<>();
        }
        return List.of(this.notes.split("\n"));
    }

    // ════════════════════════════════════════════════════════════════════════
    // QUERY HELPERS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Check if this payment is still in progress.
     */
    public boolean isInProgress() {
        return status.isInProgress();
    }

    /**
     * Check if this payment was successful.
     */
    public boolean isSuccessful() {
        return status.isSuccessful();
    }

    /**
     * Check if this payment can be retried.
     */
    public boolean canRetry() {
        return status.canRetry() && retryCount < 3;
    }

    /**
     * Check if this payment needs polling (webhook may have been missed).
     */
    public boolean needsPolling() {
        return status.needsPolling() && webhookReceivedAt == null;
    }

    /**
     * Check if this payment has expired.
     */
    public boolean hasExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    // ════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ════════════════════════════════════════════════════════════════════════

    private static String truncate(String value, int maxLength) {
        if (value == null) return null;
        if (value.length() <= maxLength) return value;
        return value.substring(0, maxLength - 3) + "...";
    }

    /**
     * Create a new payment attempt with required fields.
     */
    public static PaymentAttempt create(
            String depositId,
            String ticketId,
            String eventId,
            String organizerId,
            String organizationId,
            String buyerId,
            BigDecimal amount,
            String currency,
            String provider,
            String payerPhone
    ) {
        Instant now = Instant.now();
        return PaymentAttempt.builder()
                .depositId(depositId)
                .ticketId(ticketId)
                .eventId(eventId)
                .organizerId(organizerId)
                .organizationId(organizationId)
                .buyerId(buyerId)
                .amount(amount)
                .currency(currency)
                .provider(provider)
                .payerPhone(payerPhone)
                .status(PaymentAttemptStatus.CREATED)
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(now.plusSeconds(15 * 60)) // 15 minutes
                .build();
    }
}
