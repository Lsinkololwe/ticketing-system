package com.pml.booking.service.impl;

import com.pml.booking.event.domain.PaymentCompletedEvent;
import com.pml.booking.event.domain.PaymentFailedEvent;
import com.pml.booking.domain.model.PaymentAttempt;
import com.pml.booking.domain.model.PaymentIntent;
import com.pml.booking.domain.model.PaymentIntent.PaymentProvider;
import com.pml.booking.domain.model.PaymentIntent.PaymentStatus;
import com.pml.booking.infrastructure.gateway.MobileMoneyGateway;
import com.pml.booking.infrastructure.gateway.MobileMoneyGatewayFactory;
import com.pml.booking.infrastructure.gateway.domain.MobileMoneyRequest;
import com.pml.booking.infrastructure.gateway.domain.MobileNetwork;
import com.pml.booking.infrastructure.gateway.domain.PaymentResult;
import com.pml.booking.infrastructure.gateway.domain.PaymentResultStatus;
import com.pml.booking.repository.PaymentAttemptRepository;
import com.pml.booking.repository.PaymentIntentRepository;
import com.pml.booking.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Payment Service Implementation
 *
 * <p>Handles payment processing via the provider-agnostic MobileMoneyGateway abstraction.
 * Supports multiple mobile money providers (PawaPay, Flutterwave, etc.) with automatic failover.
 * Publishes domain events on payment completion/failure for downstream processing.</p>
 *
 * <h2>Architecture Note</h2>
 * <p>This service uses both PaymentIntent and PaymentAttempt:</p>
 * <ul>
 *   <li><b>PaymentIntent</b>: High-level payment record per ticket (what user wants to pay)</li>
 *   <li><b>PaymentAttempt</b>: Low-level gateway interaction record (each API call to PawaPay)</li>
 * </ul>
 *
 * <p>The PaymentAttempt provides OWASP-compliant tracking with webhook signature verification,
 * while PaymentIntent provides backward compatibility and simplified event publishing.</p>
 *
 * @see MobileMoneyGateway
 * @see MobileMoneyGatewayFactory
 * @see PaymentAttempt
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final MobileMoneyGatewayFactory gatewayFactory;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${payment.timeout.minutes:15}")
    private int paymentTimeoutMinutes;

    @Override
    @Transactional
    public Mono<PaymentIntent> createPaymentIntent(
            String ticketId,
            String eventId,
            String userId,
            BigDecimal amount,
            String currency,
            String phoneNumber
    ) {
        log.info("Creating payment intent for ticket: {}, amount: {} {}", ticketId, amount, currency);

        // Generate idempotency key to prevent duplicate payments
        String idempotencyKey = String.format("%s_%s_%d", userId, ticketId, System.currentTimeMillis());
        String transactionRef = PaymentIntent.generateTransactionRef();

        // Detect network from phone number using provider-agnostic enum
        MobileNetwork network = MobileNetwork.fromPhoneNumber(phoneNumber);
        PaymentProvider provider = mapNetworkToProvider(network);
        String correspondent = mapNetworkToCorrespondent(network);

        PaymentIntent paymentIntent = PaymentIntent.builder()
                .idempotencyKey(idempotencyKey)
                .transactionRef(transactionRef)
                .ticketId(ticketId)
                .eventId(eventId)
                .userId(userId)
                .amount(amount)
                .currency(currency != null ? currency : "ZMW")
                .provider(provider)
                .correspondent(correspondent)
                .phoneNumber(phoneNumber)
                .status(PaymentStatus.PENDING)
                .expiresAt(Instant.now().plus(Duration.ofMinutes(paymentTimeoutMinutes)))
                .build();

        return paymentIntentRepository.save(paymentIntent)
                .doOnSuccess(pi -> log.info("Payment intent created: {}", pi.getTransactionRef()));
    }

    @Override
    @Transactional
    public Mono<PaymentIntent> initiatePayment(String paymentIntentId) {
        log.info("Initiating payment for intent: {}", paymentIntentId);

        return paymentIntentRepository.findById(paymentIntentId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Payment intent not found: " + paymentIntentId)))
                .flatMap(paymentIntent -> {
                    if (paymentIntent.getStatus() != PaymentStatus.PENDING) {
                        return Mono.error(new IllegalStateException(
                                "Cannot initiate payment. Current status: " + paymentIntent.getStatus()));
                    }

                    if (paymentIntent.isExpired()) {
                        paymentIntent.markExpired();
                        return paymentIntentRepository.save(paymentIntent)
                                .flatMap(pi -> Mono.error(new IllegalStateException("Payment intent expired")));
                    }

                    // Build provider-agnostic payment request
                    String correlationId = UUID.randomUUID().toString();
                    MobileMoneyRequest request = MobileMoneyRequest.builder()
                            .correlationId(correlationId)
                            .phoneNumber(paymentIntent.getPhoneNumber())
                            .amount(paymentIntent.getAmount())
                            .currency(paymentIntent.getCurrency())
                            .description("Ticket Purchase: " + paymentIntent.getTicketId())
                            .build();

                    // Use gateway factory to select appropriate provider
                    return gatewayFactory.getGatewayForPhone(paymentIntent.getPhoneNumber())
                            .flatMap(gateway -> gateway.initiatePayment(request))
                            .flatMap(result -> handlePaymentResult(paymentIntent, result));
                });
    }

    /**
     * Handle the result from the payment gateway.
     * Maps the provider-agnostic PaymentResult to our PaymentIntent model.
     */
    private Mono<PaymentIntent> handlePaymentResult(PaymentIntent paymentIntent, PaymentResult result) {
        if (result.isSuccessOrPending()) {
            // Payment accepted by provider - mark as processing
            paymentIntent.markProcessing(result.providerTransactionId());
            return paymentIntentRepository.save(paymentIntent)
                    .doOnSuccess(pi -> log.info("Payment processing started via {}: {}",
                            result.providerId(), pi.getTransactionRef()));
        } else {
            // Payment rejected by provider
            paymentIntent.markFailed(
                    result.errorMessage() != null ? result.errorMessage() : "Payment rejected",
                    result.errorCode() != null ? result.errorCode() : "REJECTED"
            );
            return paymentIntentRepository.save(paymentIntent)
                    .doOnSuccess(this::publishPaymentFailed);
        }
    }

    /**
     * Handles payment callback from payment gateway.
     *
     * <p><b>Note:</b> The primary webhook handler is now in PawaPayWebhookController
     * which uses PaymentAttemptService. This method provides backward compatibility
     * for the PaymentIntent model.</p>
     *
     * <p>When a callback is received via the webhook controller:</p>
     * <ol>
     *   <li>PaymentAttemptService.processWebhook() is called first (OWASP-compliant)</li>
     *   <li>This method updates the PaymentIntent for event publishing</li>
     * </ol>
     */
    @Override
    @Transactional
    public Mono<PaymentIntent> handlePaymentCallback(
            String depositId,
            String status,
            String providerTransactionId,
            String failureCode,
            String failureMessage
    ) {
        log.info("Handling payment callback for deposit: {}, status: {}", depositId, status);

        // Try to find PaymentIntent via PaymentAttempt first (new model), then fall back to direct lookup
        return paymentAttemptRepository.findByDepositId(depositId)
                .flatMap(attempt -> paymentIntentRepository.findByTicketId(attempt.getTicketId()))
                .switchIfEmpty(paymentIntentRepository.findByProviderTransactionId(depositId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Payment intent not found for deposit: " + depositId)))
                .flatMap(paymentIntent -> processPaymentCallback(paymentIntent, status, failureCode, failureMessage));
    }

    private Mono<PaymentIntent> processPaymentCallback(
            PaymentIntent paymentIntent,
            String status,
            String failureCode,
            String failureMessage
    ) {
        paymentIntent.recordWebhook();

        if (paymentIntent.isTerminal()) {
            log.warn("Payment already in terminal state: {}", paymentIntent.getStatus());
            return Mono.just(paymentIntent);
        }

        if ("COMPLETED".equals(status)) {
            paymentIntent.markSucceeded();
            return paymentIntentRepository.save(paymentIntent)
                    .doOnSuccess(this::publishPaymentCompleted);
        } else if ("FAILED".equals(status)) {
            paymentIntent.markFailed(failureMessage, failureCode);
            return paymentIntentRepository.save(paymentIntent)
                    .doOnSuccess(this::publishPaymentFailed);
        } else {
            log.debug("Ignoring non-terminal status: {}", status);
            return paymentIntentRepository.save(paymentIntent);
        }
    }

    @Override
    @Transactional
    public Mono<PaymentIntent> checkPaymentStatus(String paymentIntentId) {
        log.debug("Checking payment status for: {}", paymentIntentId);

        return paymentIntentRepository.findById(paymentIntentId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Payment intent not found: " + paymentIntentId)))
                .flatMap(paymentIntent -> {
                    if (paymentIntent.isTerminal()) {
                        return Mono.just(paymentIntent);
                    }

                    if (paymentIntent.getProviderTransactionId() == null) {
                        return Mono.just(paymentIntent);
                    }

                    paymentIntent.recordPoll();

                    // Use gateway factory to get the appropriate provider for status check
                    return gatewayFactory.getGatewayForPhone(paymentIntent.getPhoneNumber())
                            .flatMap(gateway -> gateway.checkPaymentStatus(paymentIntent.getProviderTransactionId()))
                            .flatMap(result -> handleStatusCheckResult(paymentIntent, result));
                });
    }

    /**
     * Handle the result from a payment status check.
     * Updates PaymentIntent based on the provider-agnostic PaymentResult.
     */
    private Mono<PaymentIntent> handleStatusCheckResult(PaymentIntent paymentIntent, PaymentResult result) {
        if (result.status() == PaymentResultStatus.SUCCESS) {
            paymentIntent.markSucceeded();
            return paymentIntentRepository.save(paymentIntent)
                    .doOnSuccess(this::publishPaymentCompleted);
        } else if (result.isFailed()) {
            String failureCode = result.errorCode() != null ? result.errorCode() : "UNKNOWN";
            String failureMessage = result.errorMessage() != null ? result.errorMessage() : "Payment failed";
            paymentIntent.markFailed(failureMessage, failureCode);
            return paymentIntentRepository.save(paymentIntent)
                    .doOnSuccess(this::publishPaymentFailed);
        } else {
            // Still pending/processing - just save the poll record
            return paymentIntentRepository.save(paymentIntent);
        }
    }

    @Override
    @Transactional
    public Mono<PaymentIntent> cancelPayment(String paymentIntentId) {
        log.info("Cancelling payment: {}", paymentIntentId);

        return paymentIntentRepository.findById(paymentIntentId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Payment intent not found: " + paymentIntentId)))
                .flatMap(paymentIntent -> {
                    if (paymentIntent.isTerminal()) {
                        return Mono.error(new IllegalStateException(
                                "Cannot cancel payment in terminal state: " + paymentIntent.getStatus()));
                    }

                    paymentIntent.setStatus(PaymentStatus.CANCELLED);
                    paymentIntent.setProcessedAt(Instant.now());
                    paymentIntent.setFailureReason("Cancelled by user");

                    return paymentIntentRepository.save(paymentIntent)
                            .doOnSuccess(pi -> log.info("Payment cancelled: {}", pi.getTransactionRef()));
                });
    }

    @Override
    public Mono<PaymentIntent> findById(String id) {
        return paymentIntentRepository.findById(id);
    }

    @Override
    public Mono<PaymentIntent> findByTicketId(String ticketId) {
        return paymentIntentRepository.findByTicketId(ticketId);
    }

    @Override
    public Mono<PaymentIntent> findByIdempotencyKey(String idempotencyKey) {
        return paymentIntentRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public Flux<PaymentIntent> findByUserId(String userId) {
        return paymentIntentRepository.findByUserId(userId);
    }

    @Override
    public Flux<PaymentIntent> findByEventId(String eventId) {
        return paymentIntentRepository.findByEventId(eventId);
    }

    @Override
    public Flux<PaymentIntent> findExpiredPayments() {
        return paymentIntentRepository.findByStatusInAndExpiresAtBefore(
                java.util.List.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING),
                Instant.now()
        );
    }

    @Override
    @Transactional
    public Mono<Long> processExpiredPayments() {
        log.info("Processing expired payments");

        return findExpiredPayments()
                .flatMap(paymentIntent -> {
                    paymentIntent.markExpired();
                    return paymentIntentRepository.save(paymentIntent)
                            .doOnSuccess(this::publishPaymentFailed);
                })
                .count()
                .doOnSuccess(count -> log.info("Processed {} expired payments", count));
    }

    private void publishPaymentCompleted(PaymentIntent paymentIntent) {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                paymentIntent.getId(),
                paymentIntent.getTicketId(),
                paymentIntent.getEventId(),
                paymentIntent.getUserId(),
                paymentIntent.getAmount(),
                paymentIntent.getCurrency(),
                paymentIntent.getProvider().name(),
                paymentIntent.getCorrespondent(),
                paymentIntent.getProviderTransactionId(),
                paymentIntent.getPhoneNumber(),
                Instant.now()
        );
        eventPublisher.publishEvent(event);
        log.info("Published PaymentCompletedEvent for ticket: {}", paymentIntent.getTicketId());
    }

    private void publishPaymentFailed(PaymentIntent paymentIntent) {
        PaymentFailedEvent event = new PaymentFailedEvent(
                paymentIntent.getId(),
                paymentIntent.getTicketId(),
                paymentIntent.getEventId(),
                paymentIntent.getUserId(),
                paymentIntent.getAmount(),
                paymentIntent.getCurrency(),
                paymentIntent.getProvider().name(),
                paymentIntent.getFailureReason(),
                paymentIntent.getFailureCode()
        );
        eventPublisher.publishEvent(event);
        log.info("Published PaymentFailedEvent for ticket: {}", paymentIntent.getTicketId());
    }

    /**
     * Map provider-agnostic MobileNetwork to PaymentProvider enum.
     */
    private PaymentProvider mapNetworkToProvider(MobileNetwork network) {
        if (network == null) return PaymentProvider.PAWAPAY;
        return switch (network) {
            case MTN -> PaymentProvider.MTN_MOMO_ZMB;
            case AIRTEL -> PaymentProvider.AIRTEL_ZMB;
            case ZAMTEL -> PaymentProvider.ZAMTEL_ZMB;
        };
    }

    /**
     * Map provider-agnostic MobileNetwork to correspondent code.
     * This is the provider-specific code used by payment gateways.
     */
    private String mapNetworkToCorrespondent(MobileNetwork network) {
        if (network == null) return "UNKNOWN";
        return switch (network) {
            case MTN -> "MTN_MOMO_ZMB";
            case AIRTEL -> "AIRTEL_ZMB";
            case ZAMTEL -> "ZAMTEL_ZMB";
        };
    }
}
