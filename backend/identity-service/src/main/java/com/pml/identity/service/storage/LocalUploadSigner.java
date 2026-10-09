package com.pml.identity.service.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;

/**
 * Signs and verifies the URLs {@link LocalFileStorageService} hands out in place of S3 presigned URLs.
 *
 * <p>Development only (active with {@code file-storage.type=local}). A URL carries an expiry and an
 * HMAC over {@code key + expiry}, so a browser can PUT a document directly without a bearer token,
 * exactly as it would against a presigned S3 URL, and cannot write anywhere else.</p>
 */
@Component
@ConditionalOnProperty(name = "file-storage.type", havingValue = "local", matchIfMissing = true)
public class LocalUploadSigner {

    private final byte[] secret;
    private final Clock clock;

    public LocalUploadSigner(@Value("${file-storage.local.signing-key:}") String configured, Clock clock) {
        this.clock = clock;
        if (configured == null || configured.isBlank()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.secret = random;
        } else {
            this.secret = configured.getBytes(StandardCharsets.UTF_8);
        }
    }

    /** Expiry (epoch seconds) for a URL valid for the given minutes from now. */
    public long expiryAfterMinutes(int minutes) {
        return clock.instant().plusSeconds(minutes * 60L).getEpochSecond();
    }

    public String sign(String fileKey, long expiresAtEpochSeconds) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal((fileKey + "\n" + expiresAtEpochSeconds).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    public boolean isValid(String fileKey, long expiresAtEpochSeconds, String signature) {
        if (signature == null || expiresAtEpochSeconds < clock.instant().getEpochSecond()) {
            return false;
        }
        return MessageDigest.isEqual(
                sign(fileKey, expiresAtEpochSeconds).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }
}
