package com.pml.booking.service;

import com.pml.booking.config.PawaPayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * pawaPay Webhook Signature Verification Service
 *
 * <p>Ensures webhook callbacks are authentically from pawaPay and haven't been tampered with.
 * This is critical for production security to prevent malicious actors from spoofing
 * payment confirmations.</p>
 *
 * <h2>RFC-9421 HTTP Message Signatures</h2>
 * <p>Implements RFC-9421 HTTP Message Signatures verification as specified by pawaPay:</p>
 * <ul>
 *   <li>Content-Digest verification (SHA-256/SHA-512)</li>
 *   <li>ECDSA-P256-SHA256 signature verification</li>
 *   <li>Covered components: @method, @authority, @path, signature-date, content-digest, content-type</li>
 *   <li>Replay attack prevention via created/expires timestamps</li>
 * </ul>
 *
 * <h2>PawaPay Signature Format</h2>
 * <pre>
 * Signature: sig-pp=:base64signature:
 * Signature-Input: sig-pp=("@method" "@authority" "@path" "signature-date" "content-digest" "content-type");
 *                  alg="ecdsa-p256-sha256";keyid="...";created=...;expires=...
 * </pre>
 *
 * @see <a href="https://docs.pawapay.io/v2/docs/signatures">PawaPay Signatures Documentation</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9421">RFC 9421 - HTTP Message Signatures</a>
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookSignatureVerificationService {

    private final PawaPayProperties pawaPayProperties;

    private static final String PAWAPAY_SIGNATURE_LABEL = "sig-pp";

    /**
     * Verify the webhook signature according to RFC-9421.
     *
     * <p><b>Production Security:</b> This method performs comprehensive verification:</p>
     * <ol>
     *   <li>Content-Digest validation (ensures body integrity)</li>
     *   <li>Signature-Input parsing (extracts covered components and parameters)</li>
     *   <li>Timestamp validation (prevents replay attacks)</li>
     *   <li>Key ID verification (ensures correct public key)</li>
     *   <li>Algorithm verification (ensures supported algorithm)</li>
     *   <li>Signature verification (ECDSA-P256-SHA256)</li>
     * </ol>
     *
     * @param body            Raw request body
     * @param signatureHeader Signature header value (e.g., "sig-pp=:base64:")
     * @param signatureInput  Signature-Input header value
     * @param contentDigest   Content-Digest header value (e.g., "sha-512=:base64:")
     * @param signatureDate   Signature-Date header value
     * @param method          HTTP method (POST)
     * @param authority       Host header (e.g., "api.yourdomain.com")
     * @param path            Request path (e.g., "/api/webhooks/payment/deposit")
     * @param contentType     Content-Type header
     * @return true if signature is valid
     */
    public boolean verifyWebhookSignature(
            String body,
            String signatureHeader,
            String signatureInput,
            String contentDigest,
            String signatureDate,
            String method,
            String authority,
            String path,
            String contentType
    ) {
        PawaPayProperties.Webhook webhookConfig = pawaPayProperties.getWebhook();

        // CRITICAL: Signature verification must be enabled in production
        if (!webhookConfig.isVerifySignature()) {
            log.error("SECURITY ALERT: Webhook signature verification is DISABLED. " +
                    "This MUST be enabled in production environments.");
            return false;
        }

        try {
            // Step 1: Verify Content-Digest (body integrity)
            if (!verifyContentDigest(body, contentDigest)) {
                log.error("SECURITY: Content-Digest verification failed - payload may be tampered");
                return false;
            }

            // Step 2: Parse Signature-Input to get parameters
            SignatureInputParams params = parseSignatureInput(signatureInput);
            if (params == null) {
                log.error("SECURITY: Failed to parse Signature-Input header");
                return false;
            }

            // Step 3: Verify timestamp is within acceptable window (replay protection)
            if (!verifyTimestamp(params.created, params.expires, webhookConfig.getSignatureMaxAge())) {
                log.error("SECURITY: Signature timestamp outside acceptable window - possible replay attack");
                return false;
            }

            // Step 4: Verify key ID matches our configured key
            String expectedKeyId = webhookConfig.getPublicKeyId();
            if (expectedKeyId != null && !expectedKeyId.isEmpty() && !expectedKeyId.equals(params.keyId)) {
                log.error("SECURITY: Key ID mismatch: expected={}, received={}",
                        expectedKeyId, params.keyId);
                return false;
            }

            // Step 5: Verify signature algorithm is supported
            if (!webhookConfig.getSupportedSignatureAlgorithms().contains(params.algorithm)) {
                log.error("SECURITY: Unsupported signature algorithm: {}", params.algorithm);
                return false;
            }

            // Step 6: Build signature base per RFC-9421
            String signatureBase = buildSignatureBase(
                    params, method, authority, path, signatureDate, contentDigest, contentType
            );

            // Step 7: Verify signature
            return verifySignature(signatureBase, signatureHeader, params.algorithm);

        } catch (Exception e) {
            log.error("SECURITY: Webhook signature verification error", e);
            return false;
        }
    }

    /**
     * Convenience method for verifying webhook from ServerHttpRequest.
     * Extracts all required headers automatically.
     *
     * @param body    Raw request body
     * @param request The incoming HTTP request
     * @return true if signature is valid
     */
    public boolean verifyWebhookSignature(String body, ServerHttpRequest request) {
        String signature = getHeader(request, "Signature");
        String signatureInput = getHeader(request, "Signature-Input");
        String contentDigest = getHeader(request, "Content-Digest");
        String signatureDate = getHeader(request, "Signature-Date");
        String contentType = getHeader(request, "Content-Type");
        String host = getHeader(request, "Host");
        String method = request.getMethod().name();
        String path = request.getPath().value();

        return verifyWebhookSignature(
                body, signature, signatureInput, contentDigest, signatureDate,
                method, host, path, contentType
        );
    }

    private String getHeader(ServerHttpRequest request, String headerName) {
        return request.getHeaders().getFirst(headerName);
    }

    /**
     * Verify the Content-Digest header matches the body hash.
     *
     * <p>PawaPay uses format: {@code sha-512=:base64hash:}</p>
     *
     * @param body          Raw request body
     * @param contentDigest Content-Digest header value
     * @return true if digest matches
     */
    private boolean verifyContentDigest(String body, String contentDigest) {
        if (contentDigest == null || contentDigest.isEmpty()) {
            log.error("SECURITY: Content-Digest header is missing");
            return false;
        }

        try {
            // Parse digest format: algorithm=:base64-hash:
            // PawaPay example: sha-512=:0ki7QBS/0MA424uwOq3k5HnJnL5SRkPjit12m0YMpd4JgWiMvm9+yNT3FunkpDaTSsKhTkliQwJlRw9bgsos9w==:
            Pattern pattern = Pattern.compile("(sha-256|sha-512)=:([^:]+):");
            Matcher matcher = pattern.matcher(contentDigest);

            if (!matcher.find()) {
                log.error("SECURITY: Invalid Content-Digest format: {}", contentDigest);
                return false;
            }

            String algorithm = matcher.group(1);
            String expectedHash = matcher.group(2);

            // Check if algorithm is supported
            if (!pawaPayProperties.getWebhook().getSupportedDigestAlgorithms().contains(algorithm)) {
                log.error("SECURITY: Unsupported digest algorithm: {}", algorithm);
                return false;
            }

            // Compute actual hash
            String javaAlgorithm = "sha-256".equals(algorithm) ? "SHA-256" : "SHA-512";
            MessageDigest digest = MessageDigest.getInstance(javaAlgorithm);
            byte[] hash = digest.digest(body.getBytes(StandardCharsets.UTF_8));
            String actualHash = Base64.getEncoder().encodeToString(hash);

            // Timing-safe comparison
            if (!MessageDigest.isEqual(
                    expectedHash.getBytes(StandardCharsets.UTF_8),
                    actualHash.getBytes(StandardCharsets.UTF_8))) {
                log.error("SECURITY: Content-Digest mismatch - payload integrity violated");
                return false;
            }

            log.debug("Content-Digest verified successfully using {}", algorithm);
            return true;

        } catch (Exception e) {
            log.error("SECURITY: Content-Digest verification error", e);
            return false;
        }
    }

    /**
     * Parse the Signature-Input header to extract parameters.
     *
     * <p>PawaPay format:</p>
     * <pre>
     * sig-pp=("@method" "@authority" "@path" "signature-date" "content-digest" "content-type");
     *        alg="ecdsa-p256-sha256";keyid="CUSTOMER_TEST_KEY";created=1714657551;expires=1714657611
     * </pre>
     *
     * @param signatureInput The Signature-Input header value
     * @return Parsed parameters or null if invalid
     */
    private SignatureInputParams parseSignatureInput(String signatureInput) {
        if (signatureInput == null || signatureInput.isEmpty()) {
            log.error("SECURITY: Signature-Input header is missing");
            return null;
        }

        try {
            SignatureInputParams params = new SignatureInputParams();

            // Remove the label prefix (e.g., "sig-pp=")
            String inputValue = signatureInput;
            if (signatureInput.contains("=")) {
                int labelEnd = signatureInput.indexOf("=(");
                if (labelEnd > 0) {
                    params.label = signatureInput.substring(0, labelEnd);
                    inputValue = signatureInput.substring(labelEnd + 1);
                }
            }

            // Extract covered components
            // Format: ("@method" "@authority" "@path" "signature-date" "content-digest" "content-type")
            Pattern componentsPattern = Pattern.compile("\\(([^)]+)\\)");
            Matcher componentsMatcher = componentsPattern.matcher(inputValue);
            if (componentsMatcher.find()) {
                String componentsStr = componentsMatcher.group(1);
                // Parse quoted strings
                Pattern quotedPattern = Pattern.compile("\"([^\"]+)\"");
                Matcher quotedMatcher = quotedPattern.matcher(componentsStr);
                java.util.List<String> components = new java.util.ArrayList<>();
                while (quotedMatcher.find()) {
                    components.add(quotedMatcher.group(1));
                }
                params.coveredComponents = components.toArray(new String[0]);
            }

            // Extract parameters after the parentheses
            String paramsStr = inputValue.substring(inputValue.indexOf(")") + 1);

            // Extract algorithm
            Pattern algPattern = Pattern.compile("alg=\"([^\"]+)\"");
            Matcher algMatcher = algPattern.matcher(paramsStr);
            if (algMatcher.find()) {
                params.algorithm = algMatcher.group(1);
            }

            // Extract key ID
            Pattern keyIdPattern = Pattern.compile("keyid=\"([^\"]+)\"");
            Matcher keyIdMatcher = keyIdPattern.matcher(paramsStr);
            if (keyIdMatcher.find()) {
                params.keyId = keyIdMatcher.group(1);
            }

            // Extract created timestamp
            Pattern createdPattern = Pattern.compile("created=(\\d+)");
            Matcher createdMatcher = createdPattern.matcher(paramsStr);
            if (createdMatcher.find()) {
                params.created = Instant.ofEpochSecond(Long.parseLong(createdMatcher.group(1)));
            }

            // Extract expires timestamp
            Pattern expiresPattern = Pattern.compile("expires=(\\d+)");
            Matcher expiresMatcher = expiresPattern.matcher(paramsStr);
            if (expiresMatcher.find()) {
                params.expires = Instant.ofEpochSecond(Long.parseLong(expiresMatcher.group(1)));
            }

            // Extract nonce if present
            Pattern noncePattern = Pattern.compile("nonce=\"([^\"]+)\"");
            Matcher nonceMatcher = noncePattern.matcher(paramsStr);
            if (nonceMatcher.find()) {
                params.nonce = nonceMatcher.group(1);
            }

            // Store raw signature params for signature base
            int paramsStart = inputValue.indexOf("(");
            if (paramsStart >= 0) {
                params.rawSignatureParams = inputValue.substring(paramsStart);
            }

            log.debug("Parsed Signature-Input: components={}, alg={}, keyid={}, created={}, expires={}",
                    params.coveredComponents, params.algorithm, params.keyId, params.created, params.expires);

            return params;

        } catch (Exception e) {
            log.error("SECURITY: Failed to parse Signature-Input", e);
            return null;
        }
    }

    /**
     * Verify the signature timestamp is within the acceptable window.
     *
     * <p>Checks:</p>
     * <ul>
     *   <li>created timestamp exists</li>
     *   <li>created is not too far in the future (clock skew protection)</li>
     *   <li>expires (if present) has not passed</li>
     *   <li>signature age does not exceed max age</li>
     * </ul>
     *
     * @param created The signature creation timestamp
     * @param expires The signature expiration timestamp (may be null)
     * @param maxAge  Maximum allowed age for signatures
     * @return true if timestamp is valid
     */
    private boolean verifyTimestamp(Instant created, Instant expires, Duration maxAge) {
        if (created == null) {
            log.error("SECURITY: Signature has no created timestamp");
            return false;
        }

        Instant now = Instant.now();
        Duration age = Duration.between(created, now);

        // Allow small clock skew (2 minutes)
        Duration clockSkew = Duration.ofMinutes(2);

        // Check if signature is from the future (beyond clock skew)
        if (age.isNegative() && age.abs().compareTo(clockSkew) > 0) {
            log.error("SECURITY: Signature is from the future: created={}, now={}", created, now);
            return false;
        }

        // Check if signature has expired (if expires is provided)
        if (expires != null && now.isAfter(expires)) {
            log.error("SECURITY: Signature has expired: expires={}, now={}", expires, now);
            return false;
        }

        // Check if signature is too old (beyond max age, with clock skew allowance)
        if (!age.isNegative() && age.compareTo(maxAge.plus(clockSkew)) > 0) {
            log.error("SECURITY: Signature is too old: age={}, maxAge={}", age, maxAge);
            return false;
        }

        log.debug("Timestamp verified: created={}, expires={}, age={}", created, expires, age);
        return true;
    }

    /**
     * Build the signature base string per RFC-9421 and PawaPay format.
     *
     * <p>PawaPay signature base format:</p>
     * <pre>
     * "@method": POST
     * "@authority": api.yourdomain.com
     * "@path": /callback
     * "signature-date": 2024-05-02T16:45:51.131905Z
     * "content-digest": sha-512=:base64hash:
     * "content-type": application/json; charset=UTF-8
     * "@signature-params": ("@method" "@authority" "@path" "signature-date" "content-digest" "content-type");alg="...";keyid="...";created=...;expires=...
     * </pre>
     */
    private String buildSignatureBase(
            SignatureInputParams params,
            String method,
            String authority,
            String path,
            String signatureDate,
            String contentDigest,
            String contentType
    ) {
        // Map component names to their values
        Map<String, String> componentValues = new LinkedHashMap<>();
        componentValues.put("@method", method);
        componentValues.put("@authority", authority);
        componentValues.put("@path", path);
        componentValues.put("signature-date", signatureDate);
        componentValues.put("content-digest", contentDigest);
        componentValues.put("content-type", contentType);

        StringBuilder base = new StringBuilder();

        // Add each covered component
        for (String component : params.coveredComponents) {
            String value = componentValues.get(component);
            if (value != null) {
                base.append("\"").append(component).append("\": ").append(value).append("\n");
            } else {
                log.warn("Unknown covered component: {}", component);
            }
        }

        // Add @signature-params (the raw Signature-Input value after the label)
        base.append("\"@signature-params\": ").append(params.rawSignatureParams);

        String signatureBase = base.toString();
        log.debug("Signature base constructed:\n{}", signatureBase);

        return signatureBase;
    }

    /**
     * Verify the signature using the configured public key.
     *
     * <p>PawaPay signature header format: {@code sig-pp=:base64signature:}</p>
     *
     * @param signatureBase   The constructed signature base string
     * @param signatureHeader The Signature header value
     * @param algorithm       The signature algorithm (e.g., "ecdsa-p256-sha256")
     * @return true if signature is valid
     */
    private boolean verifySignature(String signatureBase, String signatureHeader, String algorithm) {
        try {
            if (signatureHeader == null || signatureHeader.isEmpty()) {
                log.error("SECURITY: Signature header is missing");
                return false;
            }

            // Extract signature value from header
            // PawaPay format: sig-pp=:MEQCIHFvGCUgyxmmowMufO4Yk20pBs3JHRax81si2QZVi9ByAiBPpg1WBhQjZ6fmi3a/gKcWiQ73Qm9Ol35On3c4K/flew==:
            Pattern pattern = Pattern.compile("sig[-a-zA-Z0-9]*=:([^:]+):");
            Matcher matcher = pattern.matcher(signatureHeader);
            if (!matcher.find()) {
                log.error("SECURITY: Invalid Signature header format: {}", signatureHeader);
                return false;
            }

            String signatureB64 = matcher.group(1);
            byte[] signatureBytes = Base64.getDecoder().decode(signatureB64);

            // Load public key
            PublicKey publicKey = loadPublicKey();
            if (publicKey == null) {
                log.error("SECURITY: Failed to load public key - cannot verify signature");
                return false;
            }

            // Select signature algorithm
            String javaAlgorithm = mapToJavaAlgorithm(algorithm);
            Signature signature = Signature.getInstance(javaAlgorithm);
            signature.initVerify(publicKey);
            signature.update(signatureBase.getBytes(StandardCharsets.UTF_8));

            boolean valid = signature.verify(signatureBytes);

            if (valid) {
                log.debug("Signature verified successfully using {}", algorithm);
            } else {
                log.error("SECURITY: Signature verification failed - signature does not match");
            }

            return valid;

        } catch (Exception e) {
            log.error("SECURITY: Signature verification error", e);
            return false;
        }
    }

    /**
     * Load the public key from configuration.
     *
     * <p>Supports both EC (ECDSA) and RSA public keys in PEM format.</p>
     *
     * @return The loaded public key, or null if not configured or invalid
     */
    private PublicKey loadPublicKey() {
        try {
            String publicKeyPem = pawaPayProperties.getWebhook().getPublicKey();
            if (publicKeyPem == null || publicKeyPem.isEmpty()) {
                log.error("SECURITY: PawaPay webhook public key not configured");
                return null;
            }

            // Remove PEM headers and whitespace
            String publicKeyB64 = publicKeyPem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] keyBytes = Base64.getDecoder().decode(publicKeyB64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);

            // PawaPay primarily uses EC (ECDSA-P256), but also supports RSA
            try {
                KeyFactory keyFactory = KeyFactory.getInstance("EC");
                return keyFactory.generatePublic(spec);
            } catch (Exception e) {
                log.debug("Key is not EC, trying RSA");
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                return keyFactory.generatePublic(spec);
            }

        } catch (Exception e) {
            log.error("SECURITY: Failed to load public key", e);
            return null;
        }
    }

    /**
     * Map PawaPay algorithm names to Java Security algorithm names.
     *
     * <p>Supported algorithms per PawaPay documentation:</p>
     * <ul>
     *   <li>ecdsa-p256-sha256 → SHA256withECDSA (primary)</li>
     *   <li>ecdsa-p384-sha384 → SHA384withECDSA</li>
     *   <li>rsa-pss-sha512 → SHA512withRSA/PSS</li>
     *   <li>rsa-v1_5-sha256 → SHA256withRSA</li>
     * </ul>
     *
     * @param pawaPayAlgorithm The algorithm name from Signature-Input
     * @return The Java Security algorithm name
     */
    private String mapToJavaAlgorithm(String pawaPayAlgorithm) {
        return switch (pawaPayAlgorithm) {
            case "ecdsa-p256-sha256" -> "SHA256withECDSA";
            case "ecdsa-p384-sha384" -> "SHA384withECDSA";
            case "rsa-pss-sha512" -> "SHA512withRSA/PSS";
            case "rsa-v1_5-sha256" -> "SHA256withRSA";
            default -> {
                log.error("SECURITY: Unsupported signature algorithm: {}", pawaPayAlgorithm);
                throw new IllegalArgumentException("Unsupported algorithm: " + pawaPayAlgorithm);
            }
        };
    }

    /**
     * Parameters extracted from Signature-Input header.
     *
     * <p>Example header value:</p>
     * <pre>
     * sig-pp=("@method" "@authority" "@path" "signature-date" "content-digest" "content-type");
     *        alg="ecdsa-p256-sha256";keyid="CUSTOMER_TEST_KEY";created=1714657551;expires=1714657611
     * </pre>
     */
    private static class SignatureInputParams {
        /** The signature label (e.g., "sig-pp") */
        String label = PAWAPAY_SIGNATURE_LABEL;

        /** Covered components in order (e.g., "@method", "@authority", etc.) */
        String[] coveredComponents = new String[0];

        /** Signature creation timestamp */
        Instant created;

        /** Signature expiration timestamp */
        Instant expires;

        /** Key ID for public key lookup */
        String keyId;

        /** Signature algorithm (e.g., "ecdsa-p256-sha256") */
        String algorithm;

        /** Optional nonce for additional replay protection */
        String nonce;

        /** Raw signature params string for signature base construction */
        String rawSignatureParams;
    }
}
