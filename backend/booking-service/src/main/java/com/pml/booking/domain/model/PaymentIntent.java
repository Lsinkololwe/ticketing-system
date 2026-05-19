package com.pml.booking.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * PaymentIntent Model
 *
 * Represents a payment attempt through pawaPay mobile money integration.
 * Supports MTN, Airtel, and Zamtel mobile money in Zambia.
 *
 * Payment Flow:
 * 1. PENDING - Created, awaiting user to initiate
 * 2. PROCESSING - Payment prompt sent to user's phone
 * 3. SUCCEEDED - Payment confirmed by pawaPay webhook
 * 4. FAILED - Payment failed (insufficient funds, timeout, etc.)
 * 5. EXPIRED - Payment not completed within timeout
 * 6. CANCELLED - User cancelled payment
 */
@Document(collection = "payment_intents")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PaymentIntent {

    @Id
    private String id;

    /**
     * Client-generated idempotency key to prevent duplicate payments.
     * Format: userId_eventId_timestamp
     */
    @NotBlank(message = "Idempotency key is required")
    @Indexed(unique = true)
    private String idempotencyKey;

    /**
     * Our transaction reference for tracking.
     * Format: TXN-YYYYMMDD-XXXXXXXX
     */
    @NotBlank(message = "Transaction reference is required")
    @Indexed(unique = true)
    private String transactionRef;

    /**
     * pawaPay's deposit/transaction ID returned after creation.
     */
    @Indexed
    private String providerTransactionId;

    // References
    @NotBlank(message = "Ticket ID is required")
    @Indexed
    private String ticketId;

    @NotBlank(message = "Event ID is required")
    @Indexed
    private String eventId;

    @NotBlank(message = "User ID is required")
    @Indexed
    private String userId;

    // Amount
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @Builder.Default
    private String currency = "ZMW";

    // Provider Details
    @NotNull(message = "Payment provider is required")
    private PaymentProvider provider;

    /**
     * The mobile money correspondent (network).
     * Values: MTN_MOMO_ZMB, AIRTEL_ZMB, ZAMTEL_ZMB
     */
    private String correspondent;

    /**
     * Customer's phone number in E.164 format.
     * Example: +260971234567
     */
    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    // Status
    @NotNull(message = "Status is required")
    @Indexed
    private PaymentStatus status;

    private String failureReason;
    private String failureCode;

    // Tracking
    @Builder.Default
    private int webhookAttempts = 0;

    private Instant lastWebhookAt;

    @Builder.Default
    private int pollAttempts = 0;

    private Instant lastPolledAt;

    // Timing
    @CreatedDate
    private Instant createdAt;

    @NotNull(message = "Expiry time is required")
    private Instant expiresAt;

    private Instant processedAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;

    /**
     * Payment providers supported by pawaPay in Zambia
     */
    public enum PaymentProvider {
        PAWAPAY,        // Generic pawaPay
        MTN_MOMO_ZMB,   // MTN Mobile Money
        AIRTEL_ZMB,     // Airtel Money
        ZAMTEL_ZMB      // Zamtel Kwacha
    }

    /**
     * Payment status lifecycle
     */
    public enum PaymentStatus {
        PENDING,        // Created, not yet sent to provider
        PROCESSING,     // Sent to provider, awaiting user action
        SUCCEEDED,      // Payment confirmed
        FAILED,         // Payment failed
        EXPIRED,        // Timeout expired
        CANCELLED,      // User/system cancelled
        REFUNDED        // Full refund processed
    }

    // Utility methods

    public boolean isTerminal() {
        return status == PaymentStatus.SUCCEEDED ||
               status == PaymentStatus.FAILED ||
               status == PaymentStatus.EXPIRED ||
               status == PaymentStatus.CANCELLED ||
               status == PaymentStatus.REFUNDED;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean canRetry() {
        return status == PaymentStatus.FAILED && pollAttempts < 3;
    }

    public void markProcessing(String providerTxnId) {
        this.providerTransactionId = providerTxnId;
        this.status = PaymentStatus.PROCESSING;
    }

    public void markSucceeded() {
        this.status = PaymentStatus.SUCCEEDED;
        this.processedAt = Instant.now();
    }

    public void markFailed(String reason, String code) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
        this.failureCode = code;
        this.processedAt = Instant.now();
    }

    public void markExpired() {
        this.status = PaymentStatus.EXPIRED;
        this.failureReason = "Payment timeout expired";
        this.processedAt = Instant.now();
    }

    public void recordWebhook() {
        this.webhookAttempts++;
        this.lastWebhookAt = Instant.now();
    }

    public void recordPoll() {
        this.pollAttempts++;
        this.lastPolledAt = Instant.now();
    }

    /**
     * Generate a unique transaction reference.
     */
    public static String generateTransactionRef() {
        String date = java.time.LocalDate.now().toString().replace("-", "");
        String random = java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "TXN-" + date + "-" + random;
    }
}
