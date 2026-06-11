package com.pml.identity.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Field-level encryption service for sensitive financial data.
 *
 * SECURITY FEATURES:
 * - AES-256-GCM for authenticated encryption
 * - Unique IV (Initialization Vector) per encryption
 * - PCI-DSS compliant encryption at rest
 * - Non-blocking reactive API
 *
 * ALGORITHM DETAILS:
 * - Algorithm: AES-256-GCM (Galois/Counter Mode)
 * - Key size: 256 bits
 * - IV size: 12 bytes (96 bits, recommended for GCM)
 * - Authentication tag: 128 bits
 *
 * WHY AES-GCM:
 * - Provides both confidentiality AND integrity
 * - Authenticated encryption prevents tampering
 * - Industry standard for sensitive data (PCI-DSS approved)
 * - Better performance than AES-CBC with HMAC
 *
 * PCI-DSS COMPLIANCE:
 * - Requirement 3.4: Render PAN unreadable (encryption at rest)
 * - Requirement 3.5: Protect keys used for encryption
 * - Requirement 3.6: Fully document key-management processes
 *
 * USAGE EXAMPLE:
 * <pre>
 * {@code
 * // Encrypt bank account number
 * Mono<String> encrypted = encryptionService.encrypt("1234567890");
 *
 * // Decrypt for authorized access
 * Mono<String> decrypted = encryptionService.decrypt(encryptedValue);
 * }
 * </pre>
 *
 * SECURITY NOTES:
 * - Encryption key MUST be stored securely (AWS KMS, Azure Key Vault, HashiCorp Vault)
 * - Key rotation should be performed periodically (recommend: yearly)
 * - Never log or expose encrypted data in error messages
 * - Access to decryption should be audited
 *
 * @see <a href="https://csrc.nist.gov/publications/detail/sp/800-38d/final">NIST SP 800-38D (GCM Spec)</a>
 * @see <a href="https://www.pcisecuritystandards.org/documents/PCI_DSS_v3-2-1.pdf">PCI-DSS v3.2.1</a>
 */
@Service
@Slf4j
public class FieldEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits (recommended for GCM)
    private static final int GCM_TAG_LENGTH = 128; // 128 bits authentication tag

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    /**
     * Initialize encryption service with secret key.
     *
     * SECURITY: In production, load key from secure key management service:
     * - AWS KMS
     * - Azure Key Vault
     * - HashiCorp Vault
     * - Google Cloud KMS
     *
     * @param encryptionKeyBase64 Base64-encoded 256-bit encryption key
     */
    public FieldEncryptionService(
        @Value("${app.security.encryption.key}") String encryptionKeyBase64
    ) {
        // Decode base64 key
        byte[] decodedKey = Base64.getDecoder().decode(encryptionKeyBase64);

        if (decodedKey.length != 32) { // 256 bits = 32 bytes
            throw new IllegalArgumentException("Encryption key must be 256 bits (32 bytes)");
        }

        this.secretKey = new SecretKeySpec(decodedKey, "AES");
        this.secureRandom = new SecureRandom();

        log.info("Field encryption service initialized with AES-256-GCM");
    }

    /**
     * Encrypt plaintext using AES-256-GCM.
     *
     * FORMAT OF ENCRYPTED DATA (Base64-encoded):
     * [IV (12 bytes)][Encrypted Data][Authentication Tag (16 bytes)]
     *
     * @param plaintext the data to encrypt
     * @return Base64-encoded encrypted data with IV prepended
     */
    public Mono<String> encrypt(String plaintext) {
        return Mono.fromCallable(() -> {
            if (plaintext == null || plaintext.isBlank()) {
                log.warn("Attempted to encrypt null/blank value");
                return null;
            }

            try {
                // Generate random IV (MUST be unique for each encryption)
                byte[] iv = new byte[GCM_IV_LENGTH];
                secureRandom.nextBytes(iv);

                // Initialize cipher
                Cipher cipher = Cipher.getInstance(ALGORITHM);
                GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

                // Encrypt
                byte[] plainBytes = plaintext.getBytes(StandardCharsets.UTF_8);
                byte[] cipherBytes = cipher.doFinal(plainBytes);

                // Prepend IV to ciphertext for storage
                ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherBytes.length);
                byteBuffer.put(iv);
                byteBuffer.put(cipherBytes);

                // Encode as Base64 for storage
                String encrypted = Base64.getEncoder().encodeToString(byteBuffer.array());

                log.debug("Successfully encrypted data (output length: {} bytes)", encrypted.length());
                return encrypted;

            } catch (Exception e) {
                log.error("Encryption failed", e);
                throw new EncryptionException("Failed to encrypt data", e);
            }
        });
    }

    /**
     * Decrypt ciphertext using AES-256-GCM.
     *
     * SECURITY: This operation should be audited for PCI-DSS compliance.
     * Only authorized users should be able to decrypt sensitive data.
     *
     * @param ciphertext Base64-encoded encrypted data
     * @return decrypted plaintext
     */
    public Mono<String> decrypt(String ciphertext) {
        return Mono.fromCallable(() -> {
            if (ciphertext == null || ciphertext.isBlank()) {
                log.warn("Attempted to decrypt null/blank value");
                return null;
            }

            try {
                // Decode Base64
                byte[] cipherBytes = Base64.getDecoder().decode(ciphertext);

                // Extract IV (first 12 bytes)
                ByteBuffer byteBuffer = ByteBuffer.wrap(cipherBytes);
                byte[] iv = new byte[GCM_IV_LENGTH];
                byteBuffer.get(iv);

                // Extract ciphertext (remaining bytes)
                byte[] encryptedData = new byte[byteBuffer.remaining()];
                byteBuffer.get(encryptedData);

                // Initialize cipher for decryption
                Cipher cipher = Cipher.getInstance(ALGORITHM);
                GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
                cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

                // Decrypt
                byte[] plainBytes = cipher.doFinal(encryptedData);
                String decrypted = new String(plainBytes, StandardCharsets.UTF_8);

                log.debug("Successfully decrypted data");
                return decrypted;

            } catch (Exception e) {
                log.error("Decryption failed - data may be corrupted or tampered", e);
                throw new EncryptionException("Failed to decrypt data", e);
            }
        });
    }

    /**
     * Generate a new 256-bit encryption key for AES-GCM.
     *
     * USE THIS METHOD TO GENERATE INITIAL KEY:
     * <pre>
     * {@code
     * String key = FieldEncryptionService.generateKey();
     * System.out.println("Encryption Key (Base64): " + key);
     * // Store in secure key management service (AWS KMS, Azure Key Vault, etc.)
     * }
     * </pre>
     *
     * @return Base64-encoded 256-bit key
     */
    public static String generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(256); // 256-bit key
            SecretKey key = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            throw new EncryptionException("Failed to generate encryption key", e);
        }
    }

    /**
     * Custom exception for encryption/decryption failures.
     */
    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
