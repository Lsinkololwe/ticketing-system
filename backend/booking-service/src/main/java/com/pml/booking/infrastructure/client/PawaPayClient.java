package com.pml.booking.infrastructure.client;

import com.pml.booking.config.PawaPayProperties;
import com.pml.booking.infrastructure.logging.PciDssLogger;
import com.pml.booking.infrastructure.metrics.PaymentMetrics;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.core.instrument.Timer;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * pawaPay Mobile Money API Client
 *
 * Business Intent: Integrates with pawaPay for mobile money payments in Zambia.
 * Supports MTN Mobile Money, Airtel Money, and Zamtel Kwacha.
 * This is the primary payment mechanism for the Zambian market.
 *
 * API Documentation: https://docs.pawapay.io/v2
 *
 * Payment Flow:
 * 1. Create deposit request → ACCEPTED
 * 2. User receives USSD prompt on their phone
 * 3. User enters PIN to confirm payment
 * 4. pawaPay sends webhook callback → COMPLETED/FAILED
 *
 * Configuration: Uses PawaPayProperties for centralized configuration management.
 */
@Slf4j
@Component
public class PawaPayClient {

    private static final String CIRCUIT_BREAKER_NAME = "pawaPayService";

    private final WebClient webClient;
    private final PawaPayProperties pawaPayProperties;
    private final PaymentMetrics paymentMetrics;
    private final PciDssLogger pciDssLogger;

    public PawaPayClient(WebClient.Builder webClientBuilder, PawaPayProperties pawaPayProperties,
                         PaymentMetrics paymentMetrics, PciDssLogger pciDssLogger) {
        this.pawaPayProperties = pawaPayProperties;
        this.paymentMetrics = paymentMetrics;
        this.pciDssLogger = pciDssLogger;

        // Configure HttpClient with timeouts from properties
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) pawaPayProperties.getApi().getConnectTimeout().toMillis())
                .responseTimeout(pawaPayProperties.getApi().getReadTimeout());

        this.webClient = webClientBuilder
                .baseUrl(pawaPayProperties.getApi().getUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + pawaPayProperties.getApi().getToken())
                .build();

        log.info("PawaPayClient initialized with URL: {}", pawaPayProperties.getApi().getUrl());
    }

    /**
     * Initiate a deposit (collect money from customer).
     *
     * Business Intent: This is how customers pay for tickets using mobile money.
     * After calling this, pawaPay sends a USSD prompt to the customer's phone.
     * The customer enters their PIN to confirm, and we receive a webhook callback.
     *
     * @param depositId       Unique UUID for idempotency (prevents duplicate charges)
     * @param amount          Amount to collect
     * @param currency        Currency code (ZMW for Zambian Kwacha)
     * @param phoneNumber     Customer's phone number (E.164 format: 260XXXXXXXXX)
     * @param provider        Mobile money provider (auto-detected if null)
     * @param clientReference Our internal reference for reconciliation
     * @param customerMessage Message shown to customer on their phone (4-22 chars)
     * @param metadata        Additional metadata for tracking
     * @return Deposit response with acceptance status
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "initiateDepositFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    @TimeLimiter(name = CIRCUIT_BREAKER_NAME)
    public Mono<DepositResponse> initiateDeposit(
            String depositId,
            BigDecimal amount,
            String currency,
            String phoneNumber,
            String provider,
            String clientReference,
            String customerMessage,
            Map<String, Object> metadata
    ) {
        // PCI DSS Compliant Logging - mask phone number
        log.info("Initiating pawaPay deposit: {} for {} {} (phone={})",
                depositId, PciDssLogger.maskAmount(amount, currency), currency,
                PciDssLogger.maskPhoneNumber(phoneNumber));
        paymentMetrics.recordDepositInitiated();

        // Auto-detect provider from phone number if not specified
        String resolvedProvider = provider != null ? provider : pawaPayProperties.detectProvider(phoneNumber);
        long startTime = System.currentTimeMillis();

        // Audit log for payment initiation
        String correlationId = PciDssLogger.generateCorrelationId();
        pciDssLogger.logPaymentInitiated(correlationId, depositId, "DEPOSIT",
                phoneNumber, amount, currency, resolvedProvider, clientReference);

        DepositRequest request = new DepositRequest(
                depositId,
                new Payer("MMO", new AccountDetails(normalizePhoneNumber(phoneNumber), resolvedProvider)),
                null, // preAuthorisationCode
                clientReference,
                truncateMessage(customerMessage),
                amount.toPlainString(),
                currency != null ? currency : "ZMW",
                metadata != null ? List.of(metadata) : null
        );

        return webClient.post()
                .uri("/{version}/deposits", pawaPayProperties.getApi().getVersion())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(DepositResponse.class)
                .doOnSuccess(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    paymentMetrics.recordDepositLatency(Duration.ofMillis(duration));
                    if (response.isAccepted()) {
                        paymentMetrics.recordPaymentAmount("deposit", resolvedProvider, amount.doubleValue(),
                                currency != null ? currency : "ZMW");
                        // PCI DSS: Audit log for accepted deposit
                        pciDssLogger.logPaymentCompleted(correlationId, depositId, "DEPOSIT", response.status(), null);
                    } else if (response.failureReason() != null) {
                        // PCI DSS: Audit log for rejected deposit
                        pciDssLogger.logPaymentFailed(correlationId, depositId, "DEPOSIT",
                                response.failureReason().failureCode(), response.failureReason().failureMessage());
                    }
                    log.info("Deposit {} status: {} ({}ms)", depositId, response.status(), duration);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    paymentMetrics.recordDepositLatency(Duration.ofMillis(duration));
                    // PCI DSS: Audit log for deposit error
                    pciDssLogger.logPaymentFailed(correlationId, depositId, "DEPOSIT", "API_ERROR", error.getMessage());
                    log.error("Failed to initiate deposit: {} ({}ms)", depositId, duration, error);
                });
    }

    /**
     * Fallback for initiateDeposit when circuit breaker is open.
     */
    private Mono<DepositResponse> initiateDepositFallback(
            String depositId, BigDecimal amount, String currency, String phoneNumber,
            String provider, String clientReference, String customerMessage,
            Map<String, Object> metadata, Throwable throwable) {
        log.warn("Circuit breaker fallback for initiateDeposit({}): {}", depositId, throwable.getMessage());
        paymentMetrics.recordCircuitBreakerFallback("initiateDeposit");
        paymentMetrics.recordDepositFailed(provider != null ? provider : "unknown", "CIRCUIT_BREAKER_OPEN");
        return Mono.just(new DepositResponse(
                depositId,
                "REJECTED",
                Instant.now(),
                new FailureReason("CIRCUIT_BREAKER_OPEN", "Payment service temporarily unavailable: " + throwable.getMessage())
        ));
    }

    /**
     * Check deposit status by deposit ID.
     * Used for polling status when webhooks are delayed.
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getDepositStatusFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    @TimeLimiter(name = CIRCUIT_BREAKER_NAME)
    public Mono<DepositStatusResponse> getDepositStatus(String depositId) {
        log.debug("Checking deposit status: {}", depositId);

        return webClient.get()
                .uri("/{version}/deposits/{depositId}", pawaPayProperties.getApi().getVersion(), depositId)
                .retrieve()
                .bodyToMono(DepositStatusResponse.class)
                .doOnSuccess(response -> log.debug("Deposit {} status: {}", depositId, response.status()))
                .doOnError(error -> log.error("Failed to check deposit status: {}", depositId, error));
    }

    /**
     * Fallback for getDepositStatus when circuit breaker is open.
     */
    private Mono<DepositStatusResponse> getDepositStatusFallback(String depositId, Throwable throwable) {
        log.warn("Circuit breaker fallback for getDepositStatus({}): {}", depositId, throwable.getMessage());
        return Mono.just(new DepositStatusResponse(
                depositId, "UNKNOWN", null, null, null, null, null, null, null, null,
                new FailureReason("CIRCUIT_BREAKER_OPEN", "Status check unavailable: " + throwable.getMessage())
        ));
    }

    /**
     * Initiate a refund for a deposit.
     *
     * Business Intent: Refunds money back to the customer's mobile money account.
     * Used for cancelled tickets, event cancellations, or customer refund requests.
     *
     * @param refundId  Unique UUID for idempotency
     * @param depositId Original deposit ID to refund
     * @param amount    Amount to refund
     * @param currency  Currency code
     * @param metadata  Additional metadata for tracking
     * @return Refund response with acceptance status
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "initiateRefundFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    @TimeLimiter(name = CIRCUIT_BREAKER_NAME)
    public Mono<RefundResponse> initiateRefund(
            String refundId,
            String depositId,
            BigDecimal amount,
            String currency,
            Map<String, Object> metadata
    ) {
        // PCI DSS Compliant Logging - mask amount
        log.info("Initiating pawaPay refund: {} for deposit {} amount={}",
                refundId, depositId, PciDssLogger.maskAmount(amount, currency != null ? currency : "ZMW"));

        // Audit log for refund initiation
        String correlationId = PciDssLogger.generateCorrelationId();
        pciDssLogger.logPaymentInitiated(correlationId, refundId, "REFUND",
                null, amount, currency != null ? currency : "ZMW", "PAWAPAY", depositId);

        long startTime = System.currentTimeMillis();

        RefundRequest request = new RefundRequest(
                refundId,
                depositId,
                amount.toPlainString(),
                currency != null ? currency : "ZMW",
                metadata != null ? List.of(metadata) : null
        );

        return webClient.post()
                .uri("/{version}/refunds", pawaPayProperties.getApi().getVersion())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(RefundResponse.class)
                .doOnSuccess(response -> {
                    log.info("Refund {} status: {}", refundId, response.status());
                    if (response.isAccepted()) {
                        // PCI DSS: Audit log for accepted refund
                        pciDssLogger.logPaymentCompleted(correlationId, refundId, "REFUND", response.status(), null);
                    } else if (response.failureReason() != null) {
                        // PCI DSS: Audit log for rejected refund
                        pciDssLogger.logPaymentFailed(correlationId, refundId, "REFUND",
                                response.failureReason().failureCode(), response.failureReason().failureMessage());
                    }
                })
                .doOnError(error -> {
                    log.error("Failed to initiate refund: {}", refundId, error);
                    // PCI DSS: Audit log for refund error
                    pciDssLogger.logPaymentFailed(correlationId, refundId, "REFUND", "API_ERROR", error.getMessage());
                });
    }

    /**
     * Fallback for initiateRefund when circuit breaker is open.
     */
    private Mono<RefundResponse> initiateRefundFallback(
            String refundId, String depositId, BigDecimal amount, String currency,
            Map<String, Object> metadata, Throwable throwable) {
        log.warn("Circuit breaker fallback for initiateRefund({}): {}", refundId, throwable.getMessage());
        return Mono.just(new RefundResponse(
                refundId,
                "REJECTED",
                Instant.now(),
                new FailureReason("CIRCUIT_BREAKER_OPEN", "Refund service temporarily unavailable: " + throwable.getMessage())
        ));
    }

    /**
     * Check refund status.
     * Used for polling status when webhooks are delayed.
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getRefundStatusFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    @TimeLimiter(name = CIRCUIT_BREAKER_NAME)
    public Mono<RefundStatusResponse> getRefundStatus(String refundId) {
        log.debug("Checking refund status: {}", refundId);

        return webClient.get()
                .uri("/{version}/refunds/{refundId}", pawaPayProperties.getApi().getVersion(), refundId)
                .retrieve()
                .bodyToMono(RefundStatusResponse.class)
                .doOnSuccess(response -> log.debug("Refund {} status: {}", refundId, response.status()))
                .doOnError(error -> log.error("Failed to check refund status: {}", refundId, error));
    }

    /**
     * Fallback for getRefundStatus when circuit breaker is open.
     */
    private Mono<RefundStatusResponse> getRefundStatusFallback(String refundId, Throwable throwable) {
        log.warn("Circuit breaker fallback for getRefundStatus({}): {}", refundId, throwable.getMessage());
        return Mono.just(new RefundStatusResponse(
                refundId, null, "UNKNOWN", null, null, null, null,
                new FailureReason("CIRCUIT_BREAKER_OPEN", "Status check unavailable: " + throwable.getMessage())
        ));
    }

    /**
     * Initiate a payout (send money to organizer).
     *
     * Business Intent: Pay out event organizers from their escrow balance
     * after the post-event hold period expires. This transfers funds from
     * the platform's disbursement account to the organizer's mobile money.
     *
     * @param payoutId        Unique UUID for idempotency
     * @param amount          Amount to send
     * @param currency        Currency code
     * @param phoneNumber     Recipient's phone number
     * @param provider        Mobile money provider (auto-detected if null)
     * @param customerMessage Message shown to recipient
     * @return Payout response with acceptance status
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "initiatePayoutFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    @TimeLimiter(name = CIRCUIT_BREAKER_NAME)
    public Mono<PayoutResponse> initiatePayout(
            String payoutId,
            BigDecimal amount,
            String currency,
            String phoneNumber,
            String provider,
            String customerMessage
    ) {
        // PCI DSS Compliant Logging - mask phone number and amount
        log.info("Initiating pawaPay payout: {} for {} (phone={})",
                payoutId, PciDssLogger.maskAmount(amount, currency != null ? currency : "ZMW"),
                PciDssLogger.maskPhoneNumber(phoneNumber));

        String resolvedProvider = provider != null ? provider : pawaPayProperties.detectProvider(phoneNumber);

        // Audit log for payout initiation
        String correlationId = PciDssLogger.generateCorrelationId();
        pciDssLogger.logPaymentInitiated(correlationId, payoutId, "PAYOUT",
                phoneNumber, amount, currency != null ? currency : "ZMW", resolvedProvider, null);

        long startTime = System.currentTimeMillis();

        PayoutRequest request = new PayoutRequest(
                payoutId,
                amount.toPlainString(),
                currency != null ? currency : "ZMW",
                new Recipient("MMO", new AccountDetails(normalizePhoneNumber(phoneNumber), resolvedProvider))
        );

        return webClient.post()
                .uri("/{version}/payouts", pawaPayProperties.getApi().getVersion())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PayoutResponse.class)
                .doOnSuccess(response -> {
                    log.info("Payout {} status: {}", payoutId, response.status());
                    if (response.isAccepted()) {
                        // PCI DSS: Audit log for accepted payout
                        pciDssLogger.logPaymentCompleted(correlationId, payoutId, "PAYOUT", response.status(), null);
                    } else if (response.failureReason() != null) {
                        // PCI DSS: Audit log for rejected payout
                        pciDssLogger.logPaymentFailed(correlationId, payoutId, "PAYOUT",
                                response.failureReason().failureCode(), response.failureReason().failureMessage());
                    }
                })
                .doOnError(error -> {
                    log.error("Failed to initiate payout: {}", payoutId, error);
                    // PCI DSS: Audit log for payout error
                    pciDssLogger.logPaymentFailed(correlationId, payoutId, "PAYOUT", "API_ERROR", error.getMessage());
                });
    }

    /**
     * Fallback for initiatePayout when circuit breaker is open.
     */
    private Mono<PayoutResponse> initiatePayoutFallback(
            String payoutId, BigDecimal amount, String currency, String phoneNumber,
            String provider, String customerMessage, Throwable throwable) {
        log.warn("Circuit breaker fallback for initiatePayout({}): {}", payoutId, throwable.getMessage());
        return Mono.just(new PayoutResponse(
                payoutId,
                "REJECTED",
                Instant.now(),
                new FailureReason("CIRCUIT_BREAKER_OPEN", "Payout service temporarily unavailable: " + throwable.getMessage())
        ));
    }

    /**
     * Check payout status.
     * Used for polling status when webhooks are delayed.
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getPayoutStatusFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    @TimeLimiter(name = CIRCUIT_BREAKER_NAME)
    public Mono<PayoutStatusResponse> getPayoutStatus(String payoutId) {
        log.debug("Checking payout status: {}", payoutId);

        return webClient.get()
                .uri("/{version}/payouts/{payoutId}", pawaPayProperties.getApi().getVersion(), payoutId)
                .retrieve()
                .bodyToMono(PayoutStatusResponse.class)
                .doOnSuccess(response -> log.debug("Payout {} status: {}", payoutId, response.status()))
                .doOnError(error -> log.error("Failed to check payout status: {}", payoutId, error));
    }

    /**
     * Fallback for getPayoutStatus when circuit breaker is open.
     */
    private Mono<PayoutStatusResponse> getPayoutStatusFallback(String payoutId, Throwable throwable) {
        log.warn("Circuit breaker fallback for getPayoutStatus({}): {}", payoutId, throwable.getMessage());
        return Mono.just(new PayoutStatusResponse(
                payoutId, "UNKNOWN", null, null, null, null, null, null,
                new FailureReason("CIRCUIT_BREAKER_OPEN", "Status check unavailable: " + throwable.getMessage())
        ));
    }

    /**
     * Generate a unique deposit/payout/refund ID.
     * Uses UUID to ensure global uniqueness across all transactions.
     */
    public static String generateTransactionId() {
        return UUID.randomUUID().toString();
    }


    private static String normalizePhoneNumber(String phoneNumber) {
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        if (digits.startsWith("0") && digits.length() == 10) {
            return "26" + digits;
        } else if (digits.length() == 9) {
            return "260" + digits;
        }
        return digits;
    }

    private static String truncateMessage(String message) {
        if (message == null) return null;
        if (message.length() < 4) return message + "...";
        if (message.length() > 22) return message.substring(0, 22);
        return message;
    }

    // Request/Response DTOs

    public record DepositRequest(
            String depositId,
            Payer payer,
            String preAuthorisationCode,
            String clientReferenceId,
            String customerMessage,
            String amount,
            String currency,
            List<Map<String, Object>> metadata
    ) {}

    public record Payer(String type, AccountDetails accountDetails) {}

    public record AccountDetails(String phoneNumber, String provider) {}

    public record DepositResponse(
            String depositId,
            String status, // ACCEPTED, REJECTED, DUPLICATE_IGNORED
            Instant created,
            FailureReason failureReason
    ) {
        public boolean isAccepted() {
            return "ACCEPTED".equals(status);
        }
    }

    public record DepositStatusResponse(
            String depositId,
            String status, // ACCEPTED, PENDING, COMPLETED, FAILED
            String requestedAmount,
            String amount,
            String currency,
            String country,
            Payer payer,
            String customerMessage,
            Instant created,
            String providerTransactionId,
            FailureReason failureReason
    ) {
        public boolean isCompleted() {
            return "COMPLETED".equals(status);
        }

        public boolean isFailed() {
            return "FAILED".equals(status);
        }
    }

    public record RefundRequest(
            String refundId,
            String depositId,
            String amount,
            String currency,
            List<Map<String, Object>> metadata
    ) {}

    public record RefundResponse(
            String refundId,
            String status, // ACCEPTED, REJECTED, DUPLICATE_IGNORED
            Instant created,
            FailureReason failureReason
    ) {
        public boolean isAccepted() {
            return "ACCEPTED".equals(status);
        }
    }

    public record RefundStatusResponse(
            String refundId,
            String depositId,
            String status, // ACCEPTED, PENDING, COMPLETED, FAILED
            String amount,
            String currency,
            Instant created,
            Instant completedTimestamp,
            FailureReason failureReason
    ) {
        public boolean isCompleted() {
            return "COMPLETED".equals(status);
        }

        public boolean isFailed() {
            return "FAILED".equals(status);
        }
    }

    public record PayoutRequest(
            String payoutId,
            String amount,
            String currency,
            Recipient recipient
    ) {}

    public record Recipient(String type, AccountDetails accountDetails) {}

    public record PayoutResponse(
            String payoutId,
            String status, // ACCEPTED, REJECTED, DUPLICATE_IGNORED
            Instant created,
            FailureReason failureReason
    ) {
        public boolean isAccepted() {
            return "ACCEPTED".equals(status);
        }
    }

    public record PayoutStatusResponse(
            String payoutId,
            String status, // ACCEPTED, PENDING, COMPLETED, FAILED
            String amount,
            String currency,
            Recipient recipient,
            Instant created,
            Instant completedTimestamp,
            String providerTransactionId,
            FailureReason failureReason
    ) {
        public boolean isCompleted() {
            return "COMPLETED".equals(status);
        }

        public boolean isFailed() {
            return "FAILED".equals(status);
        }
    }

    public record FailureReason(String failureCode, String failureMessage) {}
}
