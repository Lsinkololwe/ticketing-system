package com.pml.shared.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

/**
 * Auto-configuration for token blacklist functionality.
 *
 * <h2>Purpose</h2>
 * <p>
 * Provides defense-in-depth token blacklist checking for microservices.
 * The API Gateway performs the primary check; microservices provide backup validation.
 * </p>
 *
 * <h2>Enabling</h2>
 * <pre>{@code
 * pml:
 *   security:
 *     token-blacklist:
 *       enabled: true
 * }</pre>
 *
 * <h2>Requirements</h2>
 * <ul>
 *   <li>Redis connection configured (spring.data.redis.*)</li>
 *   <li>Spring Security OAuth2 Resource Server</li>
 * </ul>
 *
 * <h2>What Gets Created</h2>
 * <ul>
 *   <li>{@link TokenBlacklistService} - checks Redis for blacklisted tokens</li>
 *   <li>{@link TokenBlacklistFilter} - WebFilter that rejects blacklisted tokens</li>
 * </ul>
 *
 * @see TokenBlacklistService
 * @see TokenBlacklistFilter
 */
@Slf4j
@Configuration
@ConditionalOnClass({ReactiveRedisTemplate.class})
@ConditionalOnProperty(prefix = "pml.security.token-blacklist", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TokenBlacklistProperties.class)
public class TokenBlacklistAutoConfiguration {

    public TokenBlacklistAutoConfiguration() {
        log.info("[TokenBlacklist] Auto-configuration enabled - defense-in-depth active");
    }

    /**
     * Creates the token blacklist service for checking Redis.
     */
    @Bean
    @ConditionalOnMissingBean
    public TokenBlacklistService tokenBlacklistService(
            ReactiveRedisTemplate<String, String> redisTemplate
    ) {
        log.info("[TokenBlacklist] Creating TokenBlacklistService with prefix: {}",
                TokenBlacklistConstants.BLACKLIST_PREFIX);
        return new TokenBlacklistService(redisTemplate);
    }

    /**
     * Creates the WebFilter that checks token blacklist on every authenticated request.
     */
    @Bean
    @ConditionalOnMissingBean
    public TokenBlacklistFilter tokenBlacklistFilter(
            TokenBlacklistService tokenBlacklistService
    ) {
        log.info("[TokenBlacklist] Creating TokenBlacklistFilter");
        return new TokenBlacklistFilter(tokenBlacklistService);
    }
}
