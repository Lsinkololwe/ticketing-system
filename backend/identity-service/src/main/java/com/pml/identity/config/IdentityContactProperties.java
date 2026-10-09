package com.pml.identity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** {@code identity.contact.*}: keys for contact hashing and encryption. */
@Data
@Component
@ConfigurationProperties(prefix = "identity.contact")
public class IdentityContactProperties {

    /** Secret key of the contact-key HMAC. Changing it orphans every stored contact hash. */
    private String hashKey;

    /** Identifies the encryption key ({@code app.security.encryption.key}) used for {@code valueEncrypted}. */
    private String encryptionKeyId = "v1";

    /**
     * How long a released contact stays unclaimable by any other account (ET-IDN-004 R3). Zero switches
     * the quarantine off, which only the test profile does.
     */
    private java.time.Duration quarantine = java.time.Duration.ofDays(30);

    /**
     * How long after a code was sent the person may ask for it again (decided 2026-10-04: "after five minutes").
     * Zero switches the wait off, which only the test profile does.
     */
    private java.time.Duration resendAfter = java.time.Duration.ofMinutes(5);

    /** How long a request waits for an open change to finish before answering "applying"; the change carries on. */
    private java.time.Duration changeWait = java.time.Duration.ofSeconds(20);
}
