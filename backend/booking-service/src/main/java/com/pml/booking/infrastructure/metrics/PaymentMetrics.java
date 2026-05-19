package com.pml.booking.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Payment Metrics Service
 *
 * <p>Provides comprehensive metrics for payment operations using Micrometer.
 * These metrics are exposed via Prometheus for monitoring and alerting.</p>
 *
 * <h2>Metric Categories</h2>
 * <ul>
 *   <li><b>payment.requests</b> - Payment initiation requests (success/failure)</li>
 *   <li><b>payment.latency</b> - Payment processing latency</li>
 *   <li><b>webhook.received</b> - Webhook callbacks received</li>
 *   <li><b>webhook.latency</b> - Webhook processing latency</li>
 *   <li><b>payment.amount</b> - Payment amounts for financial tracking</li>
 * </ul>
 *
 * <h2>OWASP Compliance</h2>
 * <p>A09:2021 Security Logging and Monitoring Failures - These metrics enable
 * detection of anomalous payment patterns (e.g., spike in failures, unusual latency).</p>
 *
 * @since 1.0.0
 */
@Slf4j
@Component
public class PaymentMetrics {

    private static final String METRIC_PREFIX = "pawapay";

    private final MeterRegistry meterRegistry;

    // Payment request counters
    private final Counter depositInitiatedCounter;
    private final Counter depositSuccessCounter;
    private final Counter depositFailedCounter;
    private final Counter refundInitiatedCounter;
    private final Counter refundSuccessCounter;
    private final Counter refundFailedCounter;
    private final Counter payoutInitiatedCounter;
    private final Counter payoutSuccessCounter;
    private final Counter payoutFailedCounter;

    // Webhook counters
    private final Counter webhookReceivedCounter;
    private final Counter webhookValidCounter;
    private final Counter webhookInvalidSignatureCounter;
    private final Counter webhookIpRejectedCounter;

    // Circuit breaker counters
    private final Counter circuitBreakerOpenCounter;
    private final Counter circuitBreakerFallbackCounter;

    // Timers
    private final Timer depositLatencyTimer;
    private final Timer refundLatencyTimer;
    private final Timer payoutLatencyTimer;
    private final Timer webhookProcessingTimer;
    private final Timer statusCheckTimer;

    public PaymentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Payment request counters
        this.depositInitiatedCounter = Counter.builder(METRIC_PREFIX + ".deposit.initiated")
                .description("Number of deposit (payment) requests initiated")
                .register(meterRegistry);

        this.depositSuccessCounter = Counter.builder(METRIC_PREFIX + ".deposit.success")
                .description("Number of successful deposits")
                .register(meterRegistry);

        this.depositFailedCounter = Counter.builder(METRIC_PREFIX + ".deposit.failed")
                .description("Number of failed deposits")
                .register(meterRegistry);

        this.refundInitiatedCounter = Counter.builder(METRIC_PREFIX + ".refund.initiated")
                .description("Number of refund requests initiated")
                .register(meterRegistry);

        this.refundSuccessCounter = Counter.builder(METRIC_PREFIX + ".refund.success")
                .description("Number of successful refunds")
                .register(meterRegistry);

        this.refundFailedCounter = Counter.builder(METRIC_PREFIX + ".refund.failed")
                .description("Number of failed refunds")
                .register(meterRegistry);

        this.payoutInitiatedCounter = Counter.builder(METRIC_PREFIX + ".payout.initiated")
                .description("Number of payout requests initiated")
                .register(meterRegistry);

        this.payoutSuccessCounter = Counter.builder(METRIC_PREFIX + ".payout.success")
                .description("Number of successful payouts")
                .register(meterRegistry);

        this.payoutFailedCounter = Counter.builder(METRIC_PREFIX + ".payout.failed")
                .description("Number of failed payouts")
                .register(meterRegistry);

        // Webhook counters
        this.webhookReceivedCounter = Counter.builder(METRIC_PREFIX + ".webhook.received")
                .description("Total webhooks received")
                .register(meterRegistry);

        this.webhookValidCounter = Counter.builder(METRIC_PREFIX + ".webhook.valid")
                .description("Webhooks with valid signature")
                .register(meterRegistry);

        this.webhookInvalidSignatureCounter = Counter.builder(METRIC_PREFIX + ".webhook.invalid_signature")
                .description("Webhooks with invalid signature (potential attack)")
                .register(meterRegistry);

        this.webhookIpRejectedCounter = Counter.builder(METRIC_PREFIX + ".webhook.ip_rejected")
                .description("Webhooks rejected due to IP not in allowlist")
                .register(meterRegistry);

        // Circuit breaker counters
        this.circuitBreakerOpenCounter = Counter.builder(METRIC_PREFIX + ".circuitbreaker.open")
                .description("Times circuit breaker opened")
                .register(meterRegistry);

        this.circuitBreakerFallbackCounter = Counter.builder(METRIC_PREFIX + ".circuitbreaker.fallback")
                .description("Times fallback was invoked")
                .register(meterRegistry);

        // Latency timers with percentiles for SLO monitoring
        this.depositLatencyTimer = Timer.builder(METRIC_PREFIX + ".deposit.latency")
                .description("Deposit API call latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .serviceLevelObjectives(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofSeconds(5))
                .register(meterRegistry);

        this.refundLatencyTimer = Timer.builder(METRIC_PREFIX + ".refund.latency")
                .description("Refund API call latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);

        this.payoutLatencyTimer = Timer.builder(METRIC_PREFIX + ".payout.latency")
                .description("Payout API call latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);

        this.webhookProcessingTimer = Timer.builder(METRIC_PREFIX + ".webhook.processing_time")
                .description("Webhook processing latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .serviceLevelObjectives(Duration.ofMillis(100), Duration.ofMillis(500))
                .register(meterRegistry);

        this.statusCheckTimer = Timer.builder(METRIC_PREFIX + ".status_check.latency")
                .description("Status check API call latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        log.info("PaymentMetrics initialized with {} metrics",
                "deposit, refund, payout, webhook, circuit breaker");
    }

    // ========================================================================
    // DEPOSIT METRICS
    // ========================================================================

    public void recordDepositInitiated() {
        depositInitiatedCounter.increment();
    }

    public void recordDepositSuccess(String provider) {
        depositSuccessCounter.increment();
        meterRegistry.counter(METRIC_PREFIX + ".deposit.success.by_provider", "provider", provider).increment();
    }

    public void recordDepositFailed(String provider, String failureCode) {
        depositFailedCounter.increment();
        meterRegistry.counter(METRIC_PREFIX + ".deposit.failed.by_reason",
                Tags.of("provider", provider, "failure_code", failureCode != null ? failureCode : "unknown"))
                .increment();
    }

    public <T> T recordDepositLatency(Supplier<T> operation) {
        return depositLatencyTimer.record(operation);
    }

    public void recordDepositLatency(Duration duration) {
        depositLatencyTimer.record(duration);
    }

    // ========================================================================
    // REFUND METRICS
    // ========================================================================

    public void recordRefundInitiated() {
        refundInitiatedCounter.increment();
    }

    public void recordRefundSuccess() {
        refundSuccessCounter.increment();
    }

    public void recordRefundFailed(String failureCode) {
        refundFailedCounter.increment();
        meterRegistry.counter(METRIC_PREFIX + ".refund.failed.by_reason",
                "failure_code", failureCode != null ? failureCode : "unknown").increment();
    }

    public <T> T recordRefundLatency(Supplier<T> operation) {
        return refundLatencyTimer.record(operation);
    }

    // ========================================================================
    // PAYOUT METRICS
    // ========================================================================

    public void recordPayoutInitiated() {
        payoutInitiatedCounter.increment();
    }

    public void recordPayoutSuccess(String provider) {
        payoutSuccessCounter.increment();
        meterRegistry.counter(METRIC_PREFIX + ".payout.success.by_provider", "provider", provider).increment();
    }

    public void recordPayoutFailed(String provider, String failureCode) {
        payoutFailedCounter.increment();
        meterRegistry.counter(METRIC_PREFIX + ".payout.failed.by_reason",
                Tags.of("provider", provider, "failure_code", failureCode != null ? failureCode : "unknown"))
                .increment();
    }

    public <T> T recordPayoutLatency(Supplier<T> operation) {
        return payoutLatencyTimer.record(operation);
    }

    // ========================================================================
    // WEBHOOK METRICS
    // ========================================================================

    public void recordWebhookReceived(String type) {
        webhookReceivedCounter.increment();
        meterRegistry.counter(METRIC_PREFIX + ".webhook.received.by_type", "type", type).increment();
    }

    public void recordWebhookValid(String type) {
        webhookValidCounter.increment();
        meterRegistry.counter(METRIC_PREFIX + ".webhook.valid.by_type", "type", type).increment();
    }

    public void recordWebhookInvalidSignature(String type) {
        webhookInvalidSignatureCounter.increment();
        meterRegistry.counter(METRIC_PREFIX + ".webhook.invalid_signature.by_type", "type", type).increment();
        log.warn("SECURITY METRIC: Invalid webhook signature for type={}", type);
    }

    public void recordWebhookIpRejected(String sourceIp) {
        webhookIpRejectedCounter.increment();
        log.warn("SECURITY METRIC: Webhook IP rejected. IP={}", sourceIp);
    }

    public Timer.Sample startWebhookTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopWebhookTimer(Timer.Sample sample, String type, String status) {
        sample.stop(meterRegistry.timer(METRIC_PREFIX + ".webhook.processing_time",
                Tags.of("type", type, "status", status)));
    }

    // ========================================================================
    // CIRCUIT BREAKER METRICS
    // ========================================================================

    public void recordCircuitBreakerOpen(String operation) {
        circuitBreakerOpenCounter.increment();
        meterRegistry.counter(METRIC_PREFIX + ".circuitbreaker.open.by_operation", "operation", operation).increment();
        log.warn("CIRCUIT BREAKER: Opened for operation={}", operation);
    }

    public void recordCircuitBreakerFallback(String operation) {
        circuitBreakerFallbackCounter.increment();
        meterRegistry.counter(METRIC_PREFIX + ".circuitbreaker.fallback.by_operation", "operation", operation).increment();
    }

    // ========================================================================
    // STATUS CHECK METRICS
    // ========================================================================

    public <T> T recordStatusCheckLatency(String type, Supplier<T> operation) {
        return statusCheckTimer.record(operation);
    }

    public void recordStatusCheck(String type, boolean found) {
        meterRegistry.counter(METRIC_PREFIX + ".status_check",
                Tags.of("type", type, "found", String.valueOf(found))).increment();
    }

    // ========================================================================
    // FINANCIAL METRICS (for dashboards)
    // ========================================================================

    public void recordPaymentAmount(String type, String provider, double amount, String currency) {
        meterRegistry.counter(METRIC_PREFIX + ".amount.total",
                Tags.of("type", type, "provider", provider, "currency", currency))
                .increment(amount);
    }
}
