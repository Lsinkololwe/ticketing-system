package com.pml.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Configuration for Security Debug Filter.
 *
 * <p>Enable this configuration by setting:</p>
 * <pre>
 * security:
 *   debug:
 *     enabled: true
 * </pre>
 *
 * <p><b>WARNING:</b> Only enable in development/debugging environments.
 * This logs sensitive JWT token information.</p>
 */
@Configuration
@ConditionalOnProperty(name = "security.debug.enabled", havingValue = "true", matchIfMissing = false)
public class SecurityDebugConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityDebugConfiguration.class);

    @Bean
    @Order(-100)  // Run early in the filter chain
    public SecurityDebugFilter securityDebugFilter() {
        log.warn("╔══════════════════════════════════════════════════════════════════════");
        log.warn("║ SECURITY DEBUG MODE ENABLED - DO NOT USE IN PRODUCTION!");
        log.warn("║ JWT tokens and sensitive information will be logged.");
        log.warn("╚══════════════════════════════════════════════════════════════════════");
        return new SecurityDebugFilter();
    }
}
