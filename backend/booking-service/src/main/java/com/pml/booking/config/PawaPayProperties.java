package com.pml.booking.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * pawaPay Configuration Properties
 *
 * Business Intent: Centralizes all pawaPay integration settings to enable easy
 * configuration across environments (dev, staging, production) without code changes.
 * All sensitive values should be externalized to environment variables or secrets management.
 *
 * Configuration Hierarchy:
 * 1. Environment variables (highest priority)
 * 2. application-{profile}.yml
 * 3. application.yml (defaults)
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "pawapay")
public class PawaPayProperties {

    /**
     * API configuration for pawaPay endpoints.
     */
    private Api api = new Api();

    /**
     * Webhook configuration for callback handling.
     */
    private Webhook webhook = new Webhook();

    /**
     * Timeout and retry configuration.
     */
    private Retry retry = new Retry();

    /**
     * Supported mobile money providers in Zambia.
     */
    private Providers providers = new Providers();

    @Data
    public static class Api {
        /**
         * pawaPay API base URL.
         * Sandbox: https://api.sandbox.pawapay.io
         * Production: https://api.pawapay.io
         */
        @NotBlank(message = "pawaPay API URL is required")
        private String url = "https://api.sandbox.pawapay.io";

        /**
         * Bearer token for API authentication.
         * MUST be stored securely (env variable or secrets manager).
         */
        @NotBlank(message = "pawaPay API token is required")
        private String token;

        /**
         * API version for request paths.
         */
        private String version = "v2";

        /**
         * Connection timeout for API calls.
         */
        private Duration connectTimeout = Duration.ofSeconds(10);

        /**
         * Read timeout for API responses.
         */
        private Duration readTimeout = Duration.ofSeconds(30);
    }

    @Data
    public static class Webhook {
        /**
         * Enable/disable webhook signature verification.
         * MUST be enabled in production for security.
         */
        private boolean verifySignature = false;

        /**
         * Public key ID for signature verification.
         * Obtained from pawaPay Dashboard.
         */
        private String publicKeyId;

        /**
         * Public key content for ECDSA signature verification.
         * Can be loaded from file or environment variable.
         */
        private String publicKey;

        /**
         * Maximum age of signature (prevents replay attacks).
         */
        private Duration signatureMaxAge = Duration.ofMinutes(5);

        /**
         * Supported digest algorithms for Content-Digest verification.
         */
        private List<String> supportedDigestAlgorithms = List.of("sha-256", "sha-512");

        /**
         * Supported signature algorithms.
         */
        private List<String> supportedSignatureAlgorithms = List.of(
                "ecdsa-p256-sha256", "rsa-pss-sha512", "rsa-v1_5-sha256"
        );

        /**
         * Enable IP allowlist validation for webhooks.
         * OWASP Defense-in-Depth: Additional security layer beyond signature verification.
         */
        private boolean ipAllowlistEnabled = false;

        /**
         * List of allowed IP addresses for webhook sources.
         * Configure with PawaPay's published webhook IP addresses.
         */
        private List<String> allowedIps = new ArrayList<>();

        /**
         * List of allowed CIDR ranges for webhook sources.
         * Example: "10.0.0.0/8", "192.168.1.0/24"
         */
        private List<String> allowedCidrs = new ArrayList<>();

        /**
         * Check if the given IP address is allowed to send webhooks.
         *
         * @param ipAddress The source IP address
         * @return true if IP is allowed or allowlist is disabled
         */
        public boolean isIpAllowed(String ipAddress) {
            if (!ipAllowlistEnabled) {
                return true; // Allowlist disabled, allow all
            }

            if (ipAddress == null || ipAddress.isEmpty() || "unknown".equals(ipAddress)) {
                return false; // Cannot validate unknown IPs when allowlist is enabled
            }

            // Check exact IP match
            for (String allowedIp : allowedIps) {
                if (allowedIp != null && !allowedIp.isEmpty() && allowedIp.equals(ipAddress)) {
                    return true;
                }
            }

            // Check CIDR ranges
            for (String cidr : allowedCidrs) {
                if (cidr != null && !cidr.isEmpty() && isIpInCidr(ipAddress, cidr)) {
                    return true;
                }
            }

            return false;
        }

        /**
         * Check if an IP address is within a CIDR range.
         */
        private boolean isIpInCidr(String ipAddress, String cidr) {
            try {
                String[] parts = cidr.split("/");
                if (parts.length != 2) {
                    return false;
                }

                InetAddress cidrAddress = InetAddress.getByName(parts[0]);
                InetAddress checkAddress = InetAddress.getByName(ipAddress);

                int prefixLength = Integer.parseInt(parts[1]);
                byte[] cidrBytes = cidrAddress.getAddress();
                byte[] checkBytes = checkAddress.getAddress();

                if (cidrBytes.length != checkBytes.length) {
                    return false; // IPv4 vs IPv6 mismatch
                }

                int fullBytes = prefixLength / 8;
                int remainingBits = prefixLength % 8;

                // Check full bytes
                for (int i = 0; i < fullBytes; i++) {
                    if (cidrBytes[i] != checkBytes[i]) {
                        return false;
                    }
                }

                // Check remaining bits
                if (remainingBits > 0 && fullBytes < cidrBytes.length) {
                    int mask = 0xFF << (8 - remainingBits);
                    if ((cidrBytes[fullBytes] & mask) != (checkBytes[fullBytes] & mask)) {
                        return false;
                    }
                }

                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    @Data
    public static class Retry {
        /**
         * Maximum retry attempts for failed API calls.
         */
        @Positive
        private int maxAttempts = 3;

        /**
         * Initial delay between retries.
         */
        private Duration initialDelay = Duration.ofSeconds(1);

        /**
         * Maximum delay between retries.
         */
        private Duration maxDelay = Duration.ofSeconds(30);

        /**
         * Multiplier for exponential backoff.
         */
        private double multiplier = 2.0;

        /**
         * HTTP status codes that should trigger retry.
         */
        private List<Integer> retryableStatusCodes = List.of(408, 429, 500, 502, 503, 504);
    }

    @Data
    public static class Providers {
        /**
         * MTN Mobile Money configuration.
         */
        private MobileMoneyProvider mtn = new MobileMoneyProvider(
                "MTN_MOMO_ZMB", "MTN Mobile Money", List.of("26097", "26096")
        );

        /**
         * Airtel Money configuration.
         */
        private MobileMoneyProvider airtel = new MobileMoneyProvider(
                "AIRTEL_ZMB", "Airtel Money", List.of("26077", "26076")
        );

        /**
         * Zamtel Kwacha configuration.
         */
        private MobileMoneyProvider zamtel = new MobileMoneyProvider(
                "ZAMTEL_ZMB", "Zamtel Kwacha", List.of("26095")
        );
    }

    @Data
    public static class MobileMoneyProvider {
        private String code;
        private String displayName;
        private List<String> prefixes;
        private boolean enabled = true;

        public MobileMoneyProvider() {}

        public MobileMoneyProvider(String code, String displayName, List<String> prefixes) {
            this.code = code;
            this.displayName = displayName;
            this.prefixes = prefixes;
        }
    }

    /**
     * Detect provider from phone number based on configured prefixes.
     */
    public String detectProvider(String phoneNumber) {
        String normalized = normalizePhoneNumber(phoneNumber);

        if (providers.getMtn().isEnabled()) {
            for (String prefix : providers.getMtn().getPrefixes()) {
                if (normalized.startsWith(prefix)) {
                    return providers.getMtn().getCode();
                }
            }
        }

        if (providers.getAirtel().isEnabled()) {
            for (String prefix : providers.getAirtel().getPrefixes()) {
                if (normalized.startsWith(prefix)) {
                    return providers.getAirtel().getCode();
                }
            }
        }

        if (providers.getZamtel().isEnabled()) {
            for (String prefix : providers.getZamtel().getPrefixes()) {
                if (normalized.startsWith(prefix)) {
                    return providers.getZamtel().getCode();
                }
            }
        }

        // Default to MTN if no match
        return providers.getMtn().getCode();
    }

    private String normalizePhoneNumber(String phoneNumber) {
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        if (digits.startsWith("0") && digits.length() == 10) {
            return "26" + digits;
        } else if (digits.length() == 9) {
            return "260" + digits;
        }
        return digits;
    }
}
