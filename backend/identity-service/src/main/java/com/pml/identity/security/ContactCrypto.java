package com.pml.identity.security;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Encrypts a normalised contact for storage in {@code valueEncrypted} (AES-256-GCM via
 * {@link FieldEncryptionService}). The stored form is {@code <keyId>.<base64>} so a later key
 * rotation can tell which key wrote a value; today only the current key id is accepted.
 */
@Component
public class ContactCrypto {

    private final FieldEncryptionService encryption;

    public ContactCrypto(FieldEncryptionService encryption) {
        this.encryption = encryption;
    }

    public Mono<String> encrypt(String normalized) {
        return encryption.encrypt(normalized).map(cipher -> encryption.keyId() + "." + cipher);
    }

    public Mono<String> decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return Mono.empty();
        }
        int dot = stored.indexOf('.');
        if (dot <= 0) {
            return Mono.error(new FieldEncryptionService.EncryptionException("Unrecognised ciphertext format", null));
        }
        String keyId = stored.substring(0, dot);
        if (!keyId.equals(encryption.keyId())) {
            return Mono.error(new FieldEncryptionService.EncryptionException(
                    "Ciphertext written under key id '" + keyId + "', service holds '" + encryption.keyId() + "'", null));
        }
        return encryption.decrypt(stored.substring(dot + 1));
    }
}
