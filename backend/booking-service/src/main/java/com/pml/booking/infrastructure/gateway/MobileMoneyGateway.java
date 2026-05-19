package com.pml.booking.infrastructure.gateway;

import com.pml.booking.infrastructure.gateway.domain.*;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Provider-agnostic mobile money gateway interface.
 * <p>
 * Defines the contract for all mobile money payment providers.
 * Implementations (adapters) translate between this interface and provider-specific APIs.
 * <p>
 * <strong>Naming Convention:</strong> No provider names in method signatures.
 * Use generic terms: "initiate", "check", "payout".
 * <p>
 * <strong>Pattern:</strong> Strategy Pattern - multiple implementations can be swapped
 * at runtime without changing business logic.
 *
 * @see com.pml.booking.infrastructure.gateway.adapter.PawaPayGatewayAdapter
 */
public interface MobileMoneyGateway {

    // ==================== Payment Collection ====================

    /**
     * Initiate a collection (charge customer's mobile wallet).
     * <p>
     * This is an async operation - mobile money payments require user confirmation
     * via USSD prompt on their phone. The result will typically be PENDING or PROCESSING
     * until the user responds.
     *
     * @param request Payment request with phone number, amount, etc.
     * @return Payment result with provider transaction ID and initial status
     */
    Mono<PaymentResult> initiatePayment(MobileMoneyRequest request);

    /**
     * Check payment status (for async payments).
     * <p>
     * Call this to poll for final status when payment is PENDING/PROCESSING.
     *
     * @param providerTransactionId Transaction ID returned by initiatePayment
     * @return Current payment status
     */
    Mono<PaymentResult> checkPaymentStatus(String providerTransactionId);

    /**
     * Cancel a pending payment (if supported by provider).
     * <p>
     * Not all providers support cancellation. Check provider documentation.
     *
     * @param providerTransactionId Transaction ID to cancel
     * @return Cancellation result
     */
    default Mono<PaymentResult> cancelPayment(String providerTransactionId) {
        return Mono.error(new UnsupportedOperationException(
                "Payment cancellation not supported by this provider"));
    }

    // ==================== Payouts ====================

    /**
     * Initiate a payout (send money to mobile wallet).
     * <p>
     * Used for organizer payouts after successful events.
     *
     * @param request Payout request with recipient phone, amount, etc.
     * @return Payout result with provider transaction ID
     */
    Mono<PayoutResult> initiatePayout(PayoutRequest request);

    /**
     * Check payout status.
     *
     * @param providerTransactionId Transaction ID returned by initiatePayout
     * @return Current payout status
     */
    Mono<PayoutResult> checkPayoutStatus(String providerTransactionId);

    // ==================== Refunds ====================

    /**
     * Initiate a refund to customer's mobile wallet.
     *
     * @param originalTransactionId Original payment's provider transaction ID
     * @param correlationId Unique ID for this refund operation
     * @param reason Refund reason (for records)
     * @return Refund result
     */
    Mono<PaymentResult> initiateRefund(String originalTransactionId, String correlationId, String reason);

    /**
     * Check refund status.
     *
     * @param refundTransactionId Refund transaction ID
     * @return Current refund status
     */
    Mono<PaymentResult> checkRefundStatus(String refundTransactionId);

    // ==================== Provider Info ====================

    /**
     * Get mobile networks supported by this provider.
     * <p>
     * Used by factory to select appropriate provider for a phone number.
     *
     * @return Set of supported networks (e.g., MTN, AIRTEL, ZAMTEL)
     */
    Set<MobileNetwork> getSupportedNetworks();

    /**
     * Check if provider supports a specific network.
     *
     * @param network Network to check
     * @return true if network is supported
     */
    default boolean supportsNetwork(MobileNetwork network) {
        return getSupportedNetworks().contains(network);
    }

    /**
     * Provider health check.
     * <p>
     * Used by factory to determine if provider is available.
     *
     * @return true if provider API is reachable and operational
     */
    Mono<Boolean> isAvailable();

    /**
     * Get unique provider identifier.
     * <p>
     * Used for logging, metrics, and provider selection.
     * Examples: "pawapay", "flutterwave", "dpo"
     *
     * @return Provider ID (lowercase, no spaces)
     */
    String getProviderId();

    /**
     * Get provider display name.
     * <p>
     * Human-readable name for UI and logs.
     * Examples: "PawaPay", "Flutterwave", "DPO Group"
     *
     * @return Display name
     */
    default String getProviderDisplayName() {
        return getProviderId();
    }

    /**
     * Get provider priority for load balancing.
     * <p>
     * Higher values = higher priority. Default is 100.
     * Used when multiple providers support the same network.
     *
     * @return Priority value (0-1000)
     */
    default int getPriority() {
        return 100;
    }
}
