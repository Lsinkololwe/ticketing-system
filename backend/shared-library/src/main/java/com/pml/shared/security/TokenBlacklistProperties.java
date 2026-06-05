package com.pml.shared.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for token blacklist functionality.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * pml:
 *   security:
 *     token-blacklist:
 *       enabled: true
 * }</pre>
 *
 * @see TokenBlacklistAutoConfiguration
 */
@Data
@ConfigurationProperties(prefix = "pml.security.token-blacklist")
public class TokenBlacklistProperties {

    /**
     * Enable token blacklist checking.
     * Default: false (opt-in for defense-in-depth)
     */
    private boolean enabled = false;
}
