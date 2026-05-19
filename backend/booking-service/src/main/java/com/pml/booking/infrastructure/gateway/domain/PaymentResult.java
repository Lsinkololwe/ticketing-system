package com.pml.booking.infrastructure.gateway.domain;

import java.time.Instant;

/**
 * Provider-agnostic payment result.
 * <p>
 * Unified response format across all payment providers.
 * Adapters are responsible for mapping provider-specific responses to this format.
 */
public record PaymentResult(
        /**
         * Correlation ID from the original request.
         */
        String correlationId,

        /**
         * Provider's transaction ID.
         * Used for status checks and reconciliation.
         */
        String providerTransactionId,

        /**
         * Unified payment status.
         */
        PaymentResultStatus status,

        /**
         * Human-readable status message.
         */
        String message,

        /**
         * Whether the payment is still in progress (PENDING/PROCESSING).
         * If true, caller should poll for final status.
         */
        boolean isPending,

        /**
         * URL to check payment status (if provided by provider).
         */
        String statusCheckUrl,

        /**
         * Error code (if failed).
         * Provider-agnostic error classification.
         */
        String errorCode,

        /**
         * Detailed error message (if failed).
         */
        String errorMessage,

        /**
         * Whether the error is retryable.
         * Network timeouts, rate limits are typically retryable.
         * Business errors (insufficient funds) are not.
         */
        boolean isRetryable,

        /**
         * Timestamp when the result was created.
         */
        Instant timestamp,

        /**
         * Provider identifier (for logging/metrics).
         * e.g., "pawapay", "flutterwave"
         */
        String providerId
) {

    /**
     * Check if payment succeeded or is in progress.
     * Used to determine if the operation can proceed.
     */
    public boolean isSuccessOrPending() {
        return status == PaymentResultStatus.SUCCESS ||
               status == PaymentResultStatus.PENDING ||
               status == PaymentResultStatus.PROCESSING;
    }

    /**
     * Check if payment completed successfully.
     */
    public boolean isSuccess() {
        return status == PaymentResultStatus.SUCCESS;
    }

    /**
     * Check if payment failed.
     */
    public boolean isFailed() {
        return status == PaymentResultStatus.FAILED ||
               status == PaymentResultStatus.REJECTED ||
               status == PaymentResultStatus.EXPIRED;
    }

    /**
     * Builder for PaymentResult.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a success result.
     */
    public static PaymentResult success(String correlationId, String providerTransactionId, String providerId) {
        return builder()
                .correlationId(correlationId)
                .providerTransactionId(providerTransactionId)
                .status(PaymentResultStatus.SUCCESS)
                .message("Payment completed successfully")
                .isPending(false)
                .isRetryable(false)
                .providerId(providerId)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a pending result.
     */
    public static PaymentResult pending(String correlationId, String providerTransactionId, String providerId) {
        return builder()
                .correlationId(correlationId)
                .providerTransactionId(providerTransactionId)
                .status(PaymentResultStatus.PENDING)
                .message("Payment pending user confirmation")
                .isPending(true)
                .isRetryable(false)
                .providerId(providerId)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a failed result.
     */
    public static PaymentResult failed(String correlationId, String errorCode, String errorMessage,
                                       boolean isRetryable, String providerId) {
        return builder()
                .correlationId(correlationId)
                .status(PaymentResultStatus.FAILED)
                .message("Payment failed")
                .isPending(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .isRetryable(isRetryable)
                .providerId(providerId)
                .timestamp(Instant.now())
                .build();
    }

    public static class Builder {
        private String correlationId;
        private String providerTransactionId;
        private PaymentResultStatus status;
        private String message;
        private boolean isPending;
        private String statusCheckUrl;
        private String errorCode;
        private String errorMessage;
        private boolean isRetryable;
        private Instant timestamp = Instant.now();
        private String providerId;

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder providerTransactionId(String providerTransactionId) {
            this.providerTransactionId = providerTransactionId;
            return this;
        }

        public Builder status(PaymentResultStatus status) {
            this.status = status;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder isPending(boolean isPending) {
            this.isPending = isPending;
            return this;
        }

        public Builder statusCheckUrl(String statusCheckUrl) {
            this.statusCheckUrl = statusCheckUrl;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder isRetryable(boolean isRetryable) {
            this.isRetryable = isRetryable;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder providerId(String providerId) {
            this.providerId = providerId;
            return this;
        }

        public PaymentResult build() {
            return new PaymentResult(
                    correlationId, providerTransactionId, status, message,
                    isPending, statusCheckUrl, errorCode, errorMessage,
                    isRetryable, timestamp, providerId
            );
        }
    }
}
