package com.pml.identity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** {@code identity.challenge.*}: one-time code challenges (CONTRACT 6, 7). */
@Data
@Component
@ConfigurationProperties(prefix = "identity.challenge")
public class IdentityChallengeProperties {

    private int codeLength = 6;
    private Duration ttl = Duration.ofMinutes(5);
    private int maxAttempts = 5;
    private Duration lock = Duration.ofMinutes(15);
    private Duration cooldown = Duration.ofSeconds(60);

    /** Secret pepper for the HMAC of codes held in Redis. No default outside local and test. */
    private String hmacPepper;
}
