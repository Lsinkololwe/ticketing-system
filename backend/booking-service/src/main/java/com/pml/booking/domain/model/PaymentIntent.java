package com.pml.booking.domain.model;

import java.time.LocalDate;
import com.pml.booking.persistence.BookingCollections;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
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
@Document(collection = BookingCollections.PAYMENT_INTENTS)
@TypeAlias("payment_intents")
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
    private String idempotencyKey;

    /**
     * Our transaction reference for tracking.
     * Format: TXN-YYYYMMDD-XXXXXXXX
     */
    @NotBlank(message = "Transaction reference is required")
    private String transactionRef;

    /**
     * pawaPay's deposit/transaction ID returned after creation.
     */
    private String providerTransactionId;

    /**
     * The platform's own reference, sent to the provider as its deposit id.
     *
     * <p>Minted when the intent is created, so it exists — and is committed — before any provider
     * call. A callback names this id, which is what lets the platform find the
     * intent the money belongs to; a reference generated at call time and not stored would leave
     * every callback uncorrelatable.</p>
     *
     * <p>Unique through {@code BookingIndexInitializer} (partial on {@code $type: string}), not
     * through an annotation: {@code BookingIndexInitializer} is the only index authority.</p>
     */
    private String depositId;

    // References

    /**
     * The reservation this intent is paying for.
     *
     * <p>A payment's subject is a reservation, not a ticket: no
     * ticket exists until the confirmation transaction runs, after the money has
     * arrived. A ticket written before payment would be one the platform has not
     * been paid for and cannot account for.
     *
     * <p>Unique, not merely indexed: a reservation has at most one intent, and
     * a retried purchase must produce one charge rather than a second. Enforcing that here means a duplicate cannot be written
     * even if the application-level guard is bypassed.
     */
    @NotBlank(message = "Reservation ID is required")
    private String reservationId;

    @NotBlank(message = "Event ID is required")
    private String eventId;

    @NotBlank(message = "User ID is required")
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

    /**
     * The provider refund id of late money (ROADMAP D-22), minted once with the first refund
     * request and sent unchanged on every retry, so the provider treats a repeat as the refund it
     * already holds. Null unless the payment confirmed after its reservation lapsed.
     */
    private String lateRefundId;

    /** REQUESTED, PROCESSING, COMPLETED or FAILED; moved only by compare-and-set. */
    private String lateRefundStatus;

    /** Why the automatic refund of late money stopped, when it did. */
    private String lateRefundFailure;

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

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    public boolean canRetry() {
        return status == PaymentStatus.FAILED && pollAttempts < 3;
    }

    public void recordPoll(Instant now) {
        this.pollAttempts++;
        this.lastPolledAt = now;
    }

    /**
     * Generate a unique transaction reference.
     */
    public static String generateTransactionRef(LocalDate today) {
        String date = today.toString().replace("-", "");
        String random = java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "TXN-" + date + "-" + random;
    }
}
