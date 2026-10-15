package com.pml.catalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * The signed receipt of an upload request: who asked, for which file, of what size and type, and until
 * when. Completing an upload presents it, so the file that is registered is the file that was granted,
 * for the caller who asked and nobody else.
 *
 * <p>The token carries no secret; it is an HMAC-SHA256 over its own body. A forged, altered or
 * expired token is indistinguishable from any other bad one.
 */
@Slf4j
@Component
public class MediaUploadTokens {

    /** What a token binds. {@code scope} is {@code org:<id>} for an organization or {@code stock}. */
    public record Claims(String fileKey, String scope, String userId, String fileName, String contentType,
                         long sizeBytes, long expiresAtEpochSecond) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] key;
    private final Clock clock;

    public MediaUploadTokens(@Value("${catalog.media.upload-signing-key:}") String configured,
                             @Value("${file-storage.type:local}") String storageType, Clock clock) {
        this.clock = clock;
        if (configured == null || configured.isBlank()) {
            if (!"local".equals(storageType)) {
                throw new IllegalStateException(
                        "catalog.media.upload-signing-key must be set when file-storage.type is " + storageType);
            }
            // Development: a key per start, so a token does not outlive the process that issued it.
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.key = random;
            log.warn("catalog.media.upload-signing-key is not set; upload tokens are valid until this service restarts");
        } else {
            this.key = configured.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String issue(String fileKey, String scope, String userId, String fileName, String contentType,
                        long sizeBytes, Duration validFor) {
        Claims claims = new Claims(fileKey, scope, userId, fileName, contentType, sizeBytes,
                clock.instant().plus(validFor).getEpochSecond());
        try {
            String body = ENCODER.encodeToString(JSON.writeValueAsBytes(claims));
            return body + "." + ENCODER.encodeToString(mac(body));
        } catch (Exception e) {
            throw new IllegalStateException("an upload token could not be issued", e);
        }
    }

    /** The claims of a genuine, unexpired token; empty for anything else. */
    public Optional<Claims> verify(String token) {
        if (token == null || token.length() > 2048) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return Optional.empty();
        }
        try {
            String body = token.substring(0, dot);
            byte[] given = DECODER.decode(token.substring(dot + 1));
            if (!MessageDigest.isEqual(mac(body), given)) {
                return Optional.empty();
            }
            Claims claims = JSON.readValue(DECODER.decode(body), Claims.class);
            Instant expires = Instant.ofEpochSecond(claims.expiresAtEpochSecond());
            return clock.instant().isBefore(expires) ? Optional.of(claims) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private byte[] mac(String body) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
    }
}
