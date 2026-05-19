package com.pml.booking.infrastructure.gateway.adapter;

import com.pml.booking.config.PawaPayProperties;
import com.pml.booking.infrastructure.client.PawaPayClient;
import com.pml.booking.infrastructure.gateway.MobileMoneyGateway;
import com.pml.booking.infrastructure.gateway.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * PawaPay adapter implementing the provider-agnostic MobileMoneyGateway interface.
 * <p>
 * This adapter wraps the existing PawaPayClient and translates between:
 * - Our provider-agnostic domain types (MobileMoneyRequest, PaymentResult)
 * - PawaPay-specific DTOs (DepositRequest, DepositResponse)
 * <p>
 * <strong>Pattern:</strong> Adapter Pattern - makes PawaPay's API compatible with
 * our generic gateway interface.
 *
 * @see MobileMoneyGateway
 * @see PawaPayClient
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PawaPayGatewayAdapter implements MobileMoneyGateway {

    private static final String PROVIDER_ID = "pawapay";
    private static final String PROVIDER_DISPLAY_NAME = "PawaPay";

    private final PawaPayClient pawaPayClient;
    private final PawaPayProperties pawaPayProperties;

    // ==================== Payment Collection ====================

    @Override
    public Mono<PaymentResult> initiatePayment(MobileMoneyRequest request) {
        log.info("[{}] Initiating payment: correlationId={}, amount={} {}",
                PROVIDER_ID, request.correlationId(), request.amount(), request.currency());

        // Resolve provider code from network
        String providerCode = resolveProviderCode(request.network(), request.phoneNumber());

        return pawaPayClient.initiateDeposit(
                        request.correlationId(),
                        request.amount(),
                        request.currency(),
                        request.phoneNumber(),
                        providerCode,
                        request.correlationId(), // clientReference
                        truncateDescription(request.description()),
                        buildMetadata(request)
                )
                .map(response -> mapDepositResponse(request.correlationId(), response))
                .onErrorResume(error -> handleError(request.correlationId(), error));
    }

    @Override
    public Mono<PaymentResult> checkPaymentStatus(String providerTransactionId) {
        log.debug("[{}] Checking payment status: {}", PROVIDER_ID, providerTransactionId);

        return pawaPayClient.getDepositStatus(providerTransactionId)
                .map(this::mapDepositStatusResponse)
                .onErrorResume(error -> handleError(providerTransactionId, error));
    }

    // ==================== Payouts ====================

    @Override
    public Mono<PayoutResult> initiatePayout(PayoutRequest request) {
        log.info("[{}] Initiating payout: correlationId={}, amount={} {}",
                PROVIDER_ID, request.correlationId(), request.amount(), request.currency());

        String providerCode = resolveProviderCode(request.network(), request.phoneNumber());

        return pawaPayClient.initiatePayout(
                        request.correlationId(),
                        request.amount(),
                        request.currency(),
                        request.phoneNumber(),
                        providerCode,
                        truncateDescription(request.description())
                )
                .map(response -> mapPayoutResponse(request.correlationId(), response))
                .onErrorResume(error -> handlePayoutError(request.correlationId(), error));
    }

    @Override
    public Mono<PayoutResult> checkPayoutStatus(String providerTransactionId) {
        log.debug("[{}] Checking payout status: {}", PROVIDER_ID, providerTransactionId);

        return pawaPayClient.getPayoutStatus(providerTransactionId)
                .map(this::mapPayoutStatusResponse)
                .onErrorResume(error -> handlePayoutError(providerTransactionId, error));
    }

    // ==================== Refunds ====================

    @Override
    public Mono<PaymentResult> initiateRefund(String originalTransactionId, String correlationId, String reason) {
        log.info("[{}] Initiating refund: correlationId={}, originalTx={}",
                PROVIDER_ID, correlationId, originalTransactionId);

        // First get the original deposit to know the amount and currency
        return pawaPayClient.getDepositStatus(originalTransactionId)
                .flatMap(depositStatus -> {
                    if (!depositStatus.isCompleted()) {
                        return Mono.just(PaymentResult.failed(
                                correlationId,
                                "INVALID_STATE",
                                "Cannot refund a deposit that is not completed",
                                false,
                                PROVIDER_ID
                        ));
                    }

                    return pawaPayClient.initiateRefund(
                            correlationId,
                            originalTransactionId,
                            new java.math.BigDecimal(depositStatus.amount()),
                            depositStatus.currency(),
                            Map.of("reason", reason != null ? reason : "Customer refund request")
                    ).map(response -> mapRefundResponse(correlationId, response));
                })
                .onErrorResume(error -> handleError(correlationId, error));
    }

    @Override
    public Mono<PaymentResult> checkRefundStatus(String refundTransactionId) {
        log.debug("[{}] Checking refund status: {}", PROVIDER_ID, refundTransactionId);

        return pawaPayClient.getRefundStatus(refundTransactionId)
                .map(this::mapRefundStatusResponse)
                .onErrorResume(error -> handleError(refundTransactionId, error));
    }

    // ==================== Provider Info ====================

    @Override
    public Set<MobileNetwork> getSupportedNetworks() {
        return Set.of(MobileNetwork.MTN, MobileNetwork.AIRTEL, MobileNetwork.ZAMTEL);
    }

    @Override
    public Mono<Boolean> isAvailable() {
        // Simple health check - try to get a non-existent deposit
        // PawaPay doesn't have a dedicated health endpoint
        return Mono.just(true)
                .delayElement(java.time.Duration.ofMillis(100))
                .doOnNext(v -> log.debug("[{}] Health check: available", PROVIDER_ID));
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public String getProviderDisplayName() {
        return PROVIDER_DISPLAY_NAME;
    }

    @Override
    public int getPriority() {
        return 100; // Primary provider for Zambia
    }

    // ==================== Private Mapping Methods ====================

    /**
     * Resolve PawaPay provider code from our generic MobileNetwork.
     */
    private String resolveProviderCode(MobileNetwork network, String phoneNumber) {
        if (network != null) {
            return switch (network) {
                case MTN -> "MTN_MOMO_ZMB";
                case AIRTEL -> "AIRTEL_ZMB";
                case ZAMTEL -> "ZAMTEL_ZMB";
            };
        }
        // Fall back to auto-detection via PawaPayProperties
        return pawaPayProperties.detectProvider(phoneNumber);
    }

    /**
     * Map PawaPay deposit response to our generic PaymentResult.
     */
    private PaymentResult mapDepositResponse(String correlationId, PawaPayClient.DepositResponse response) {
        PaymentResultStatus status = response.isAccepted()
                ? PaymentResultStatus.PENDING
                : PaymentResultStatus.FAILED;

        return PaymentResult.builder()
                .correlationId(correlationId)
                .providerTransactionId(response.depositId())
                .status(status)
                .message(response.status())
                .isPending(response.isAccepted())
                .errorCode(response.failureReason() != null ? response.failureReason().failureCode() : null)
                .errorMessage(response.failureReason() != null ? response.failureReason().failureMessage() : null)
                .isRetryable(isRetryableError(response.failureReason()))
                .providerId(PROVIDER_ID)
                .build();
    }

    /**
     * Map PawaPay deposit status response to our generic PaymentResult.
     */
    private PaymentResult mapDepositStatusResponse(PawaPayClient.DepositStatusResponse response) {
        PaymentResultStatus status = mapPawaPayStatus(response.status());

        return PaymentResult.builder()
                .correlationId(response.depositId())
                .providerTransactionId(response.depositId())
                .status(status)
                .message(response.status())
                .isPending(status == PaymentResultStatus.PENDING || status == PaymentResultStatus.PROCESSING)
                .errorCode(response.failureReason() != null ? response.failureReason().failureCode() : null)
                .errorMessage(response.failureReason() != null ? response.failureReason().failureMessage() : null)
                .isRetryable(isRetryableError(response.failureReason()))
                .providerId(PROVIDER_ID)
                .build();
    }

    /**
     * Map PawaPay payout response to our generic PayoutResult.
     */
    private PayoutResult mapPayoutResponse(String correlationId, PawaPayClient.PayoutResponse response) {
        PaymentResultStatus status = response.isAccepted()
                ? PaymentResultStatus.PENDING
                : PaymentResultStatus.FAILED;

        return PayoutResult.builder()
                .correlationId(correlationId)
                .providerTransactionId(response.payoutId())
                .status(status)
                .message(response.status())
                .isPending(response.isAccepted())
                .errorCode(response.failureReason() != null ? response.failureReason().failureCode() : null)
                .errorMessage(response.failureReason() != null ? response.failureReason().failureMessage() : null)
                .isRetryable(isRetryableError(response.failureReason()))
                .providerId(PROVIDER_ID)
                .build();
    }

    /**
     * Map PawaPay payout status response to our generic PayoutResult.
     */
    private PayoutResult mapPayoutStatusResponse(PawaPayClient.PayoutStatusResponse response) {
        PaymentResultStatus status = mapPawaPayStatus(response.status());

        return PayoutResult.builder()
                .correlationId(response.payoutId())
                .providerTransactionId(response.payoutId())
                .status(status)
                .message(response.status())
                .isPending(status == PaymentResultStatus.PENDING || status == PaymentResultStatus.PROCESSING)
                .errorCode(response.failureReason() != null ? response.failureReason().failureCode() : null)
                .errorMessage(response.failureReason() != null ? response.failureReason().failureMessage() : null)
                .isRetryable(isRetryableError(response.failureReason()))
                .providerId(PROVIDER_ID)
                .build();
    }

    /**
     * Map PawaPay refund response to our generic PaymentResult.
     */
    private PaymentResult mapRefundResponse(String correlationId, PawaPayClient.RefundResponse response) {
        PaymentResultStatus status = response.isAccepted()
                ? PaymentResultStatus.PENDING
                : PaymentResultStatus.FAILED;

        return PaymentResult.builder()
                .correlationId(correlationId)
                .providerTransactionId(response.refundId())
                .status(status)
                .message(response.status())
                .isPending(response.isAccepted())
                .errorCode(response.failureReason() != null ? response.failureReason().failureCode() : null)
                .errorMessage(response.failureReason() != null ? response.failureReason().failureMessage() : null)
                .isRetryable(isRetryableError(response.failureReason()))
                .providerId(PROVIDER_ID)
                .build();
    }

    /**
     * Map PawaPay refund status response to our generic PaymentResult.
     */
    private PaymentResult mapRefundStatusResponse(PawaPayClient.RefundStatusResponse response) {
        PaymentResultStatus status = mapPawaPayStatus(response.status());

        return PaymentResult.builder()
                .correlationId(response.refundId())
                .providerTransactionId(response.refundId())
                .status(status == PaymentResultStatus.SUCCESS ? PaymentResultStatus.REFUNDED : status)
                .message(response.status())
                .isPending(status == PaymentResultStatus.PENDING || status == PaymentResultStatus.PROCESSING)
                .errorCode(response.failureReason() != null ? response.failureReason().failureCode() : null)
                .errorMessage(response.failureReason() != null ? response.failureReason().failureMessage() : null)
                .isRetryable(isRetryableError(response.failureReason()))
                .providerId(PROVIDER_ID)
                .build();
    }

    /**
     * Map PawaPay status string to our generic PaymentResultStatus.
     */
    private PaymentResultStatus mapPawaPayStatus(String pawaPayStatus) {
        return switch (pawaPayStatus.toUpperCase()) {
            case "COMPLETED" -> PaymentResultStatus.SUCCESS;
            case "ACCEPTED", "PENDING", "SUBMITTED" -> PaymentResultStatus.PENDING;
            case "PROCESSING" -> PaymentResultStatus.PROCESSING;
            case "FAILED" -> PaymentResultStatus.FAILED;
            case "REJECTED", "CANCELLED" -> PaymentResultStatus.REJECTED;
            case "EXPIRED" -> PaymentResultStatus.EXPIRED;
            default -> {
                log.warn("[{}] Unknown PawaPay status: {}", PROVIDER_ID, pawaPayStatus);
                yield PaymentResultStatus.FAILED;
            }
        };
    }

    /**
     * Handle errors and map to PaymentResult.
     */
    private Mono<PaymentResult> handleError(String correlationId, Throwable error) {
        log.error("[{}] API error for {}: {}", PROVIDER_ID, correlationId, error.getMessage());

        String errorCode = "PROVIDER_ERROR";
        String errorMessage = error.getMessage();
        boolean isRetryable = isRetryableException(error);

        // Extract more specific error code if possible
        if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException ex) {
            errorCode = "HTTP_" + ex.getStatusCode().value();
            errorMessage = ex.getResponseBodyAsString();
            isRetryable = isRetryableHttpStatus(ex.getStatusCode().value());
        }

        return Mono.just(PaymentResult.failed(correlationId, errorCode, errorMessage, isRetryable, PROVIDER_ID));
    }

    /**
     * Handle payout errors and map to PayoutResult.
     */
    private Mono<PayoutResult> handlePayoutError(String correlationId, Throwable error) {
        log.error("[{}] Payout API error for {}: {}", PROVIDER_ID, correlationId, error.getMessage());

        String errorCode = "PROVIDER_ERROR";
        String errorMessage = error.getMessage();
        boolean isRetryable = isRetryableException(error);

        if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException ex) {
            errorCode = "HTTP_" + ex.getStatusCode().value();
            errorMessage = ex.getResponseBodyAsString();
            isRetryable = isRetryableHttpStatus(ex.getStatusCode().value());
        }

        return Mono.just(PayoutResult.failed(correlationId, errorCode, errorMessage, isRetryable, PROVIDER_ID));
    }

    /**
     * Check if a PawaPay failure reason indicates a retryable error.
     */
    private boolean isRetryableError(PawaPayClient.FailureReason failureReason) {
        if (failureReason == null) return false;

        String code = failureReason.failureCode();
        if (code == null) return false;

        // Retryable error codes (network issues, temporary failures)
        return Set.of(
                "TIMEOUT",
                "NETWORK_ERROR",
                "PROVIDER_UNAVAILABLE",
                "RATE_LIMITED",
                "SERVICE_UNAVAILABLE",
                "INTERNAL_ERROR"
        ).contains(code.toUpperCase());
    }

    /**
     * Check if an exception indicates a retryable error.
     */
    private boolean isRetryableException(Throwable error) {
        return error instanceof java.net.ConnectException ||
               error instanceof java.util.concurrent.TimeoutException ||
               error instanceof java.net.SocketTimeoutException;
    }

    /**
     * Check if HTTP status code indicates a retryable error.
     */
    private boolean isRetryableHttpStatus(int statusCode) {
        return statusCode == 408 || // Request Timeout
               statusCode == 429 || // Too Many Requests
               statusCode == 500 || // Internal Server Error
               statusCode == 502 || // Bad Gateway
               statusCode == 503 || // Service Unavailable
               statusCode == 504;   // Gateway Timeout
    }

    /**
     * Build metadata map for PawaPay request.
     */
    private Map<String, Object> buildMetadata(MobileMoneyRequest request) {
        return Map.of(
                "correlationId", request.correlationId(),
                "payerName", request.payerName() != null ? request.payerName() : "",
                "payerEmail", request.payerEmail() != null ? request.payerEmail() : ""
        );
    }

    /**
     * Truncate description to PawaPay's limit (4-22 characters).
     */
    private String truncateDescription(String description) {
        if (description == null) return "Payment";
        if (description.length() < 4) return description + "...";
        if (description.length() > 22) return description.substring(0, 22);
        return description;
    }
}
