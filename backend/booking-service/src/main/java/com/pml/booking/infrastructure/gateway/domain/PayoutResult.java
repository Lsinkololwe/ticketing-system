package com.pml.booking.infrastructure.gateway.domain;

import java.time.Instant;

/**
 * Provider-agnostic payout result.
 * <p>
 * Unified response format for organizer payouts across all providers.
 */
public record PayoutResult(
        /**
         * Correlation ID from the original request.
         */
        String correlationId,

        /**
         * Provider's payout transaction ID.
         */
        String providerTransactionId,

        /**
         * Unified payout status.
         */
        PaymentResultStatus status,

        /**
         * Human-readable status message.
         */
        String message,

        /**
         * Whether the payout is still in progress.
         */
        boolean isPending,

        /**
         * Error code (if failed).
         */
        String errorCode,

        /**
         * Detailed error message (if failed).
         */
        String errorMessage,

        /**
         * Whether the error is retryable.
         */
        boolean isRetryable,

        /**
         * Timestamp when the result was created.
         */
        Instant timestamp,

        /**
         * Provider identifier.
         */
        String providerId
) {

    public boolean isSuccessOrPending() {
        return status == PaymentResultStatus.SUCCESS ||
               status == PaymentResultStatus.PENDING ||
               status == PaymentResultStatus.PROCESSING;
    }

    public boolean isSuccess() {
        return status == PaymentResultStatus.SUCCESS;
    }

    public boolean isFailed() {
        return status == PaymentResultStatus.FAILED ||
               status == PaymentResultStatus.REJECTED;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PayoutResult success(String correlationId, String providerTransactionId, String providerId) {
        return builder()
                .correlationId(correlationId)
                .providerTransactionId(providerTransactionId)
                .status(PaymentResultStatus.SUCCESS)
                .message("Payout completed successfully")
                .isPending(false)
                .isRetryable(false)
                .providerId(providerId)
                .timestamp(Instant.now())
                .build();
    }

    public static PayoutResult pending(String correlationId, String providerTransactionId, String providerId) {
        return builder()
                .correlationId(correlationId)
                .providerTransactionId(providerTransactionId)
                .status(PaymentResultStatus.PENDING)
                .message("Payout pending processing")
                .isPending(true)
                .isRetryable(false)
                .providerId(providerId)
                .timestamp(Instant.now())
                .build();
    }

    public static PayoutResult failed(String correlationId, String errorCode, String errorMessage,
                                      boolean isRetryable, String providerId) {
        return builder()
                .correlationId(correlationId)
                .status(PaymentResultStatus.FAILED)
                .message("Payout failed")
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

        public PayoutResult build() {
            return new PayoutResult(
                    correlationId, providerTransactionId, status, message,
                    isPending, errorCode, errorMessage, isRetryable,
                    timestamp, providerId
            );
        }
    }
}
