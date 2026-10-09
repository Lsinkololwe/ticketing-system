package com.pml.keycloak.it;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 time-based codes (HMAC-SHA1, 6 digits, 30 s) as Keycloak's default OTP policy issues them. */
final class Totp {

    static final long PERIOD_SECONDS = 30;

    private Totp() {
    }

    static long currentStep() {
        return System.currentTimeMillis() / 1000 / PERIOD_SECONDS;
    }

    /** Keycloak shows the secret to the user base32-encoded but keys the HMAC with the raw string's bytes. */
    static String code(String rawSecret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(rawSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
