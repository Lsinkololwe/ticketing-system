package com.pml.booking.infrastructure.gateway.domain;

import java.math.BigDecimal;

/**
 * Provider-agnostic payout request.
 * <p>
 * Used for sending money to organizers via mobile money.
 * No vendor-specific fields - those are handled by adapters.
 */
public record PayoutRequest(
        /**
         * Unique idempotency key to prevent duplicate payouts.
         */
        String correlationId,

        /**
         * Recipient phone number in E.164 format (+260XXXXXXXXX).
         */
        String phoneNumber,

        /**
         * Mobile network (auto-detected if null).
         */
        MobileNetwork network,

        /**
         * Payout amount.
         */
        BigDecimal amount,

        /**
         * Currency code (e.g., ZMW, USD).
         */
        String currency,

        /**
         * Human-readable payout description.
         * e.g., "Event payout: Concert XYZ"
         */
        String description,

        /**
         * Webhook URL for async payout notifications.
         */
        String callbackUrl,

        /**
         * Recipient's name (for records).
         */
        String recipientName,

        /**
         * Reference to the payout request in our system.
         */
        String payoutRequestId,

        /**
         * Reference to the event (for tracking).
         */
        String eventId,

        /**
         * Reference to the organizer (for tracking).
         */
        String organizerId
) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String correlationId;
        private String phoneNumber;
        private MobileNetwork network;
        private BigDecimal amount;
        private String currency = "ZMW";
        private String description;
        private String callbackUrl;
        private String recipientName;
        private String payoutRequestId;
        private String eventId;
        private String organizerId;

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder phoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
            return this;
        }

        public Builder network(MobileNetwork network) {
            this.network = network;
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder callbackUrl(String callbackUrl) {
            this.callbackUrl = callbackUrl;
            return this;
        }

        public Builder recipientName(String recipientName) {
            this.recipientName = recipientName;
            return this;
        }

        public Builder payoutRequestId(String payoutRequestId) {
            this.payoutRequestId = payoutRequestId;
            return this;
        }

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder organizerId(String organizerId) {
            this.organizerId = organizerId;
            return this;
        }

        public PayoutRequest build() {
            // Auto-detect network if not provided
            MobileNetwork detectedNetwork = network;
            if (detectedNetwork == null && phoneNumber != null) {
                detectedNetwork = MobileNetwork.fromPhoneNumber(phoneNumber);
            }

            return new PayoutRequest(
                    correlationId, phoneNumber, detectedNetwork, amount, currency,
                    description, callbackUrl, recipientName, payoutRequestId,
                    eventId, organizerId
            );
        }
    }
}
