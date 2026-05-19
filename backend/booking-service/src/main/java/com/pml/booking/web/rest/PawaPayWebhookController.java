package com.pml.booking.web.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.booking.config.PawaPayProperties;
import com.pml.booking.dto.DepositCallbackPayload;
import com.pml.booking.dto.PayoutCallbackPayload;
import com.pml.booking.dto.RefundCallbackPayload;
import com.pml.booking.infrastructure.logging.PciDssLogger;
import com.pml.booking.infrastructure.metrics.PaymentMetrics;
import com.pml.booking.service.PaymentAttemptService;
import com.pml.booking.service.RefundService;
import com.pml.booking.service.WebhookDeduplicationService;
import com.pml.booking.service.WebhookSignatureVerificationService;
import io.micrometer.core.instrument.Timer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * PawaPay Webhook Controller
 *
 * <p>Handles asynchronous callbacks from PawaPay when mobile money transactions
 * reach a final state. This is the primary mechanism for confirming payments,
 * refunds, and payouts in the Zambian mobile money ecosystem.</p>
 *
 * <h2>Security Implementation (RFC-9421)</h2>
 * <ul>
 *   <li>These endpoints are publicly accessible (no OAuth required by design)</li>
 *   <li>PawaPay signs all callbacks using RFC-9421 HTTP Message Signatures</li>
 *   <li>Signature verification is MANDATORY for production deployment</li>
 *   <li>Invalid signatures are rejected with 401 Unauthorized</li>
 *   <li>All webhook data is logged for audit trail</li>
 * </ul>
 *
 * <h2>PawaPay Webhook Headers</h2>
 * <ul>
 *   <li>Content-Digest: SHA-512 hash of body</li>
 *   <li>Signature: ECDSA-P256-SHA256 signature</li>
 *   <li>Signature-Input: RFC-9421 signature parameters</li>
 *   <li>Signature-Date: Timestamp when signature was created</li>
 * </ul>
 *
 * @see <a href="https://docs.pawapay.io/v2/docs/signatures">PawaPay Signatures</a>
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks/payment")
@RequiredArgsConstructor
@Tag(name = "Payment Webhooks", description = "PawaPay callback endpoints for mobile money transaction status updates")
public class PawaPayWebhookController {

    private final PaymentAttemptService paymentAttemptService;
    private final RefundService refundService;
    private final WebhookSignatureVerificationService signatureVerificationService;
    private final WebhookDeduplicationService webhookDeduplicationService;
    private final ObjectMapper objectMapper;
    private final PawaPayProperties pawaPayProperties;
    private final PaymentMetrics paymentMetrics;
    private final PciDssLogger pciDssLogger;

    /**
     * Handle deposit callback from PawaPay.
     *
     * <p><b>Business Intent:</b> Process the final status of a ticket payment.
     * When a customer confirms payment on their phone, PawaPay notifies us here.
     * On success, we finalize the ticket purchase and credit the organizer's escrow account.</p>
     *
     * <p><b>Security (RFC-9421):</b></p>
     * <ul>
     *   <li>Verifies Content-Digest header (payload integrity)</li>
     *   <li>Verifies ECDSA-P256-SHA256 signature (authenticity)</li>
     *   <li>Validates signature timestamp (replay protection)</li>
     *   <li>Records verification status for audit</li>
     * </ul>
     *
     * @param rawBody        Raw request body
     * @param signature      RFC-9421 signature header
     * @param signatureInput RFC-9421 signature input parameters
     * @param contentDigest  Content digest for body verification
     * @param signatureDate  Signature creation timestamp
     * @param contentType    Content-Type header
     * @param request        The HTTP request (for extracting method, host, path, IP)
     * @return Response indicating callback was processed
     */
    @Operation(
            summary = "Handle deposit callback",
            description = "Receives callback from PawaPay when a deposit (ticket payment) reaches COMPLETED or FAILED status",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Callback processed successfully"),
                    @ApiResponse(responseCode = "401", description = "Invalid signature"),
                    @ApiResponse(responseCode = "400", description = "Invalid payload format")
            }
    )
    @PostMapping("/deposit")
    public Mono<ResponseEntity<Map<String, String>>> handleDepositCallback(
            @RequestBody String rawBody,
            @Parameter(description = "RFC-9421 signature")
            @RequestHeader(value = "Signature", required = false) String signature,
            @Parameter(description = "RFC-9421 signature input parameters")
            @RequestHeader(value = "Signature-Input", required = false) String signatureInput,
            @Parameter(description = "Content digest for body verification")
            @RequestHeader(value = "Content-Digest", required = false) String contentDigest,
            @Parameter(description = "Signature creation timestamp")
            @RequestHeader(value = "Signature-Date", required = false) String signatureDate,
            @Parameter(description = "Content-Type header")
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            ServerHttpRequest request
    ) {
        String sourceIp = extractSourceIp(request);
        String method = request.getMethod().name();
        String authority = request.getHeaders().getFirst("Host");
        String path = request.getPath().value();

        log.info("WEBHOOK: Deposit callback received from IP={}, path={}", sourceIp, path);
        Timer.Sample webhookTimer = paymentMetrics.startWebhookTimer();
        paymentMetrics.recordWebhookReceived("deposit");

        // OWASP Defense-in-Depth: Validate IP allowlist BEFORE signature verification
        if (!pawaPayProperties.getWebhook().isIpAllowed(sourceIp)) {
            log.error("SECURITY: Webhook IP not in allowlist. IP={}, path={}", sourceIp, path);
            // PCI DSS: Log IP rejection to security audit trail
            pciDssLogger.logIpRejected(path, sourceIp, "PawaPay webhook allowlist");
            paymentMetrics.recordWebhookIpRejected(sourceIp);
            paymentMetrics.stopWebhookTimer(webhookTimer, "deposit", "ip_rejected");
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "IP not allowed")));
        }

        // Verify webhook signature (CRITICAL for production security)
        boolean signatureValid = signatureVerificationService.verifyWebhookSignature(
                rawBody, signature, signatureInput, contentDigest, signatureDate,
                method, authority, path, contentType
        );

        if (!signatureValid) {
            log.error("SECURITY: Invalid webhook signature for deposit callback from IP={}", sourceIp);
            paymentMetrics.recordWebhookInvalidSignature("deposit");
            paymentMetrics.stopWebhookTimer(webhookTimer, "deposit", "invalid_signature");
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid signature")));
        }

        paymentMetrics.recordWebhookValid("deposit");

        // Parse the payload after signature verification
        DepositCallbackPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, DepositCallbackPayload.class);
        } catch (JsonProcessingException e) {
            log.error("WEBHOOK: Failed to parse deposit callback payload", e);
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid payload format")));
        }

        log.info("WEBHOOK: Processing deposit callback: depositId={}, status={}",
                payload.depositId(), payload.status());

        // PCI DSS: Log webhook received to audit trail
        pciDssLogger.logWebhookReceived("deposit", payload.depositId(), payload.status(), sourceIp, signatureValid);

        // Check for duplicate webhook (OWASP: Prevent replay/duplicate processing)
        return webhookDeduplicationService.tryMarkAsProcessed("deposit", payload.depositId(), payload.status())
                .flatMap(isFirstTime -> {
                    if (!isFirstTime) {
                        log.warn("WEBHOOK: Duplicate deposit callback detected: depositId={}, status={}",
                                payload.depositId(), payload.status());
                        paymentMetrics.stopWebhookTimer(webhookTimer, "deposit", "duplicate");
                        return Mono.just(ResponseEntity.ok(Map.of(
                                "message", "Callback already processed",
                                "duplicate", "true"
                        )));
                    }

                    // Process webhook via PaymentAttemptService
                    return paymentAttemptService.processWebhook(
                        payload.depositId(),
                        payload.status(),
                        payload.providerTransactionId(),
                        payload.failureReason() != null ? payload.failureReason().failureCode() : null,
                        payload.failureReason() != null ? payload.failureReason().failureMessage() : null,
                        rawBody,
                        sourceIp,
                        signatureValid
                )
                            .map(attempt -> {
                                log.info("WEBHOOK: Deposit callback processed: depositId={}, newStatus={}",
                                        attempt.getDepositId(), attempt.getStatus());
                                paymentMetrics.stopWebhookTimer(webhookTimer, "deposit", "success");
                                return ResponseEntity.ok(Map.of("message", "Callback processed successfully"));
                            })
                            .onErrorResume(error -> {
                                log.error("WEBHOOK: Failed to process deposit callback: depositId={}",
                                        payload.depositId(), error);
                                paymentMetrics.stopWebhookTimer(webhookTimer, "deposit", "error");
                                // Clear deduplication marker on error so webhook can be retried
                                webhookDeduplicationService.clearProcessingMarker("deposit", payload.depositId(), payload.status())
                                        .subscribe();
                                // Return 200 to prevent PawaPay from retrying excessively
                                // The error is logged for manual investigation
                                return Mono.just(ResponseEntity.ok(Map.of(
                                        "message", "Callback received",
                                        "warning", "Processing error occurred"
                                )));
                            });
                });
    }

    /**
     * Handle refund callback from PawaPay.
     *
     * <p><b>Business Intent:</b> Process the final status of a customer refund.
     * When a refund completes, we update the ticket and escrow records accordingly.</p>
     *
     * @param rawBody        Raw request body
     * @param signature      RFC-9421 signature header
     * @param signatureInput RFC-9421 signature input parameters
     * @param contentDigest  Content digest for body verification
     * @param signatureDate  Signature creation timestamp
     * @param contentType    Content-Type header
     * @param request        The HTTP request
     * @return Response indicating callback was processed
     */
    @Operation(
            summary = "Handle refund callback",
            description = "Receives callback from PawaPay when a refund reaches COMPLETED or FAILED status",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Callback processed successfully"),
                    @ApiResponse(responseCode = "401", description = "Invalid signature"),
                    @ApiResponse(responseCode = "400", description = "Invalid payload format")
            }
    )
    @PostMapping("/refund")
    public Mono<ResponseEntity<Map<String, String>>> handleRefundCallback(
            @RequestBody String rawBody,
            @RequestHeader(value = "Signature", required = false) String signature,
            @RequestHeader(value = "Signature-Input", required = false) String signatureInput,
            @RequestHeader(value = "Content-Digest", required = false) String contentDigest,
            @RequestHeader(value = "Signature-Date", required = false) String signatureDate,
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            ServerHttpRequest request
    ) {
        String sourceIp = extractSourceIp(request);
        String method = request.getMethod().name();
        String authority = request.getHeaders().getFirst("Host");
        String path = request.getPath().value();

        log.info("WEBHOOK: Refund callback received from IP={}", sourceIp);

        // OWASP Defense-in-Depth: Validate IP allowlist
        if (!pawaPayProperties.getWebhook().isIpAllowed(sourceIp)) {
            log.error("SECURITY: Webhook IP not in allowlist. IP={}, path={}", sourceIp, path);
            // PCI DSS: Log IP rejection to security audit trail
            pciDssLogger.logIpRejected(path, sourceIp, "PawaPay webhook allowlist");
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "IP not allowed")));
        }

        // Verify webhook signature
        boolean signatureValid = signatureVerificationService.verifyWebhookSignature(
                rawBody, signature, signatureInput, contentDigest, signatureDate,
                method, authority, path, contentType
        );

        if (!signatureValid) {
            log.error("SECURITY: Invalid webhook signature for refund callback from IP={}", sourceIp);
            // PCI DSS: Log invalid signature to security audit trail
            pciDssLogger.logWebhookReceived("refund", "unknown", "INVALID_SIGNATURE", sourceIp, false);
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid signature")));
        }

        RefundCallbackPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, RefundCallbackPayload.class);
        } catch (JsonProcessingException e) {
            log.error("WEBHOOK: Failed to parse refund callback payload", e);
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid payload format")));
        }

        log.info("WEBHOOK: Processing refund callback: refundId={}, status={}",
                payload.refundId(), payload.status());

        // PCI DSS: Log webhook received to audit trail
        pciDssLogger.logWebhookReceived("refund", payload.refundId(), payload.status(), sourceIp, signatureValid);

        return refundService.handleRefundCallback(
                        payload.refundId(),
                        payload.status(),
                        payload.providerTransactionId(),
                        payload.failureReason() != null ? payload.failureReason().failureCode() : null,
                        payload.failureReason() != null ? payload.failureReason().failureMessage() : null
                )
                .map(rr -> ResponseEntity.ok(Map.of("message", "Callback processed successfully")))
                .onErrorResume(error -> {
                    log.error("WEBHOOK: Failed to process refund callback: {}", payload.refundId(), error);
                    return Mono.just(ResponseEntity.ok(Map.of(
                            "message", "Callback received",
                            "warning", "Processing error occurred"
                    )));
                });
    }

    /**
     * Handle payout callback from PawaPay.
     *
     * <p><b>Business Intent:</b> Process the final status of an organizer payout.
     * When the escrow hold period ends and payout is triggered, this webhook confirms
     * the funds have reached the organizer's mobile money account.</p>
     *
     * @param rawBody        Raw request body
     * @param signature      RFC-9421 signature header
     * @param signatureInput RFC-9421 signature input parameters
     * @param contentDigest  Content digest for body verification
     * @param signatureDate  Signature creation timestamp
     * @param contentType    Content-Type header
     * @param request        The HTTP request
     * @return Response indicating callback was processed
     */
    @Operation(
            summary = "Handle payout callback",
            description = "Receives callback from PawaPay when a payout (organizer payout) reaches COMPLETED or FAILED status",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Callback processed successfully"),
                    @ApiResponse(responseCode = "401", description = "Invalid signature"),
                    @ApiResponse(responseCode = "400", description = "Invalid payload format")
            }
    )
    @PostMapping("/payout")
    public Mono<ResponseEntity<Map<String, String>>> handlePayoutCallback(
            @RequestBody String rawBody,
            @RequestHeader(value = "Signature", required = false) String signature,
            @RequestHeader(value = "Signature-Input", required = false) String signatureInput,
            @RequestHeader(value = "Content-Digest", required = false) String contentDigest,
            @RequestHeader(value = "Signature-Date", required = false) String signatureDate,
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            ServerHttpRequest request
    ) {
        String sourceIp = extractSourceIp(request);
        String method = request.getMethod().name();
        String authority = request.getHeaders().getFirst("Host");
        String path = request.getPath().value();

        log.info("WEBHOOK: Payout callback received from IP={}", sourceIp);

        // OWASP Defense-in-Depth: Validate IP allowlist
        if (!pawaPayProperties.getWebhook().isIpAllowed(sourceIp)) {
            log.error("SECURITY: Webhook IP not in allowlist. IP={}, path={}", sourceIp, path);
            // PCI DSS: Log IP rejection to security audit trail
            pciDssLogger.logIpRejected(path, sourceIp, "PawaPay webhook allowlist");
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "IP not allowed")));
        }

        // Verify webhook signature
        boolean signatureValid = signatureVerificationService.verifyWebhookSignature(
                rawBody, signature, signatureInput, contentDigest, signatureDate,
                method, authority, path, contentType
        );

        if (!signatureValid) {
            log.error("SECURITY: Invalid webhook signature for payout callback from IP={}", sourceIp);
            // PCI DSS: Log invalid signature to security audit trail
            pciDssLogger.logWebhookReceived("payout", "unknown", "INVALID_SIGNATURE", sourceIp, false);
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid signature")));
        }

        PayoutCallbackPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, PayoutCallbackPayload.class);
        } catch (JsonProcessingException e) {
            log.error("WEBHOOK: Failed to parse payout callback payload", e);
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid payload format")));
        }

        log.info("WEBHOOK: Processing payout callback: payoutId={}, status={}",
                payload.payoutId(), payload.status());

        // PCI DSS: Log webhook received to audit trail
        pciDssLogger.logWebhookReceived("payout", payload.payoutId(), payload.status(), sourceIp, signatureValid);

        // Payout completion is handled by identity service via event
        // We publish an event for cross-service communication
        // TODO: Publish PayoutCompletedEvent to Azure Service Bus
        return Mono.just(ResponseEntity.ok(Map.of("message", "Callback processed successfully")));
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Extract source IP address from request, handling proxy headers.
     *
     * <p>Checks headers in order:</p>
     * <ol>
     *   <li>X-Forwarded-For (first IP if multiple)</li>
     *   <li>X-Real-IP</li>
     *   <li>Remote address from connection</li>
     * </ol>
     *
     * @param request The HTTP request
     * @return The source IP address
     */
    private String extractSourceIp(ServerHttpRequest request) {
        // Check X-Forwarded-For header (may contain multiple IPs)
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            // Take the first IP (original client)
            String[] ips = forwardedFor.split(",");
            return ips[0].trim();
        }

        // Check X-Real-IP header
        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp.trim();
        }

        // Fall back to remote address
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }
}

