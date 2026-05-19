package com.pml.booking.infrastructure.logging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * PCI DSS Compliant Logger
 *
 * <p>Provides logging utilities that comply with PCI DSS (Payment Card Industry
 * Data Security Standard) requirements for payment system logging.</p>
 *
 * <h2>PCI DSS Logging Requirements</h2>
 * <ul>
 *   <li><b>Requirement 10.2</b>: Log all access to cardholder data</li>
 *   <li><b>Requirement 10.3</b>: Include who, what, when, where, result</li>
 *   <li><b>Requirement 3.4</b>: Mask PAN when displayed (first 6, last 4)</li>
 * </ul>
 *
 * <h2>Mobile Money Adaptation</h2>
 * <p>For mobile money systems, phone numbers are treated as sensitive identifiers
 * similar to PANs. This logger masks phone numbers to show only the network
 * prefix and last 4 digits (e.g., 26097****1234).</p>
 *
 * <h2>Audit Trail Fields</h2>
 * <ul>
 *   <li><b>correlationId</b>: Unique identifier for tracking across services</li>
 *   <li><b>userId</b>: Authenticated user (if applicable)</li>
 *   <li><b>action</b>: What operation was performed</li>
 *   <li><b>resourceType</b>: Type of resource affected</li>
 *   <li><b>resourceId</b>: Specific resource identifier</li>
 *   <li><b>result</b>: SUCCESS, FAILURE, REJECTED</li>
 *   <li><b>sourceIp</b>: Origin of request</li>
 * </ul>
 *
 * <h2>OWASP A09:2021 - Security Logging and Monitoring Failures</h2>
 * <p>This logger addresses the need for adequate logging of security-relevant events
 * while protecting sensitive data from exposure in logs.</p>
 *
 * @since 1.0.0
 */
@Slf4j
@Component
public class PciDssLogger {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUDIT");
    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("SECURITY");

    // Pattern for phone numbers: mask middle digits, keep first 5 and last 4
    private static final Pattern PHONE_PATTERN = Pattern.compile("(260\\d{2})(\\d+)(\\d{4})");

    // Pattern for any sequence of 9+ digits (potential sensitive data)
    private static final Pattern DIGIT_SEQUENCE_PATTERN = Pattern.compile("\\b(\\d{5})(\\d{4,})(\\d{4})\\b");

    /**
     * Mask a phone number for PCI DSS compliance.
     * Example: 260971234567 -> 26097****4567
     *
     * @param phoneNumber The phone number to mask
     * @return Masked phone number
     */
    public static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 9) {
            return phoneNumber;
        }

        // Remove any non-digit characters
        String digits = phoneNumber.replaceAll("[^0-9]", "");

        if (digits.length() < 9) {
            return "****";
        }

        // Keep first 5 digits (country code + network prefix) and last 4
        int maskLength = digits.length() - 9;
        String masked = digits.substring(0, 5) + "*".repeat(Math.max(4, maskLength + 4)) + digits.substring(digits.length() - 4);
        return masked;
    }

    /**
     * Mask any sensitive data in a message.
     * Masks phone numbers and long digit sequences.
     *
     * @param message The message to sanitize
     * @return Sanitized message with masked data
     */
    public static String sanitizeMessage(String message) {
        if (message == null) {
            return null;
        }

        // Mask phone-like patterns
        String sanitized = PHONE_PATTERN.matcher(message).replaceAll("$1****$3");

        // Mask any remaining long digit sequences
        sanitized = DIGIT_SEQUENCE_PATTERN.matcher(sanitized).replaceAll("$1****$3");

        return sanitized;
    }

    /**
     * Mask an amount for logging (show currency and magnitude only).
     * Example: 1234.56 -> 1,2XX.XX (shows order of magnitude)
     *
     * @param amount   The amount
     * @param currency The currency code
     * @return Masked amount string
     */
    public static String maskAmount(BigDecimal amount, String currency) {
        if (amount == null) {
            return currency + " ***";
        }

        // Show first digit and order of magnitude
        String amountStr = amount.toPlainString();
        if (amountStr.length() <= 1) {
            return currency + " " + amountStr;
        }

        // Show first 2 significant digits, mask rest
        int decimalPos = amountStr.indexOf('.');
        int significantDigits = decimalPos > 0 ? decimalPos : amountStr.length();

        if (significantDigits <= 2) {
            return currency + " " + amountStr;
        }

        String masked = amountStr.substring(0, 2) + "X".repeat(significantDigits - 2);
        if (decimalPos > 0) {
            masked += ".XX";
        }

        return currency + " " + masked;
    }

    // ========================================================================
    // AUDIT LOGGING METHODS
    // ========================================================================

    /**
     * Log a payment initiation event.
     */
    public void logPaymentInitiated(String correlationId, String transactionId, String transactionType,
                                     String phoneNumber, BigDecimal amount, String currency,
                                     String provider, String userId) {
        try (var ignored = MDC.putCloseable("correlationId", correlationId);
             var ignored2 = MDC.putCloseable("transactionId", transactionId)) {

            AUDIT_LOG.info("PAYMENT_INITIATED | type={} | txnId={} | phone={} | amount={} | provider={} | user={}",
                    transactionType,
                    transactionId,
                    maskPhoneNumber(phoneNumber),
                    maskAmount(amount, currency),
                    provider,
                    userId != null ? userId : "SYSTEM");
        }
    }

    /**
     * Log a payment completion event.
     */
    public void logPaymentCompleted(String correlationId, String transactionId, String transactionType,
                                     String status, String providerReference) {
        try (var ignored = MDC.putCloseable("correlationId", correlationId);
             var ignored2 = MDC.putCloseable("transactionId", transactionId)) {

            AUDIT_LOG.info("PAYMENT_COMPLETED | type={} | txnId={} | status={} | providerRef={}",
                    transactionType,
                    transactionId,
                    status,
                    providerReference != null ? providerReference : "N/A");
        }
    }

    /**
     * Log a payment failure event.
     */
    public void logPaymentFailed(String correlationId, String transactionId, String transactionType,
                                  String failureCode, String failureMessage) {
        try (var ignored = MDC.putCloseable("correlationId", correlationId);
             var ignored2 = MDC.putCloseable("transactionId", transactionId)) {

            AUDIT_LOG.warn("PAYMENT_FAILED | type={} | txnId={} | code={} | message={}",
                    transactionType,
                    transactionId,
                    failureCode,
                    sanitizeMessage(failureMessage));
        }
    }

    /**
     * Log a webhook received event.
     */
    public void logWebhookReceived(String webhookType, String transactionId, String status,
                                    String sourceIp, boolean signatureValid) {
        String correlationId = UUID.randomUUID().toString();

        try (var ignored = MDC.putCloseable("correlationId", correlationId);
             var ignored2 = MDC.putCloseable("transactionId", transactionId)) {

            if (signatureValid) {
                AUDIT_LOG.info("WEBHOOK_RECEIVED | type={} | txnId={} | status={} | sourceIp={} | sigValid=true",
                        webhookType, transactionId, status, sourceIp);
            } else {
                SECURITY_LOG.warn("WEBHOOK_INVALID_SIGNATURE | type={} | txnId={} | sourceIp={} | sigValid=false",
                        webhookType, transactionId, sourceIp);
            }
        }
    }

    /**
     * Log a security event (authentication, authorization, suspicious activity).
     */
    public void logSecurityEvent(String eventType, String description, String sourceIp,
                                  String userId, Map<String, String> additionalData) {
        String correlationId = UUID.randomUUID().toString();

        try (var ignored = MDC.putCloseable("correlationId", correlationId)) {

            StringBuilder logMessage = new StringBuilder();
            logMessage.append("SECURITY_EVENT | type=").append(eventType);
            logMessage.append(" | desc=").append(sanitizeMessage(description));
            logMessage.append(" | sourceIp=").append(sourceIp);
            logMessage.append(" | user=").append(userId != null ? userId : "ANONYMOUS");
            logMessage.append(" | timestamp=").append(Instant.now());

            if (additionalData != null) {
                additionalData.forEach((key, value) ->
                        logMessage.append(" | ").append(key).append("=").append(sanitizeMessage(value)));
            }

            SECURITY_LOG.warn(logMessage.toString());
        }
    }

    /**
     * Log an access denied event.
     */
    public void logAccessDenied(String resource, String action, String userId, String sourceIp, String reason) {
        try (var ignored = MDC.putCloseable("userId", userId != null ? userId : "ANONYMOUS")) {

            SECURITY_LOG.warn("ACCESS_DENIED | resource={} | action={} | user={} | sourceIp={} | reason={}",
                    resource, action, userId != null ? userId : "ANONYMOUS", sourceIp, reason);
        }
    }

    /**
     * Log an IP allowlist rejection.
     */
    public void logIpRejected(String endpoint, String sourceIp, String expectedAllowlist) {
        SECURITY_LOG.error("IP_REJECTED | endpoint={} | sourceIp={} | reason=not_in_allowlist | timestamp={}",
                endpoint, sourceIp, Instant.now());
    }

    /**
     * Log a circuit breaker state change.
     */
    public void logCircuitBreakerStateChange(String serviceName, String previousState, String newState) {
        AUDIT_LOG.warn("CIRCUIT_BREAKER | service={} | from={} | to={} | timestamp={}",
                serviceName, previousState, newState, Instant.now());
    }

    // ========================================================================
    // MDC CONTEXT MANAGEMENT
    // ========================================================================

    /**
     * Set correlation ID in MDC for request tracing.
     */
    public static void setCorrelationId(String correlationId) {
        MDC.put("correlationId", correlationId);
    }

    /**
     * Set transaction ID in MDC.
     */
    public static void setTransactionId(String transactionId) {
        MDC.put("transactionId", transactionId);
    }

    /**
     * Clear MDC context.
     */
    public static void clearContext() {
        MDC.clear();
    }

    /**
     * Generate a new correlation ID.
     */
    public static String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
