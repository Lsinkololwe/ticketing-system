package com.pml.shared.security;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.ObservationTextPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.observation.SecurityObservationSettings;

/**
 * Native Spring Security Observability Configuration.
 *
 * <p>This is the official Spring Security way to enable detailed debugging of:
 * <ul>
 *   <li>Security filter chain execution (before/after each filter)</li>
 *   <li>Authentication attempts and results</li>
 *   <li>Authorization decisions</li>
 * </ul>
 *
 * <p>Enable by setting {@code security.debug.enabled=true} in application.yml
 *
 * <h3>Output Example:</h3>
 * <pre>
 * START - name='spring.security.http.chains', contextualName='spring.security.http.chains.before'
 *   filter.section='before', chain.size='14', request.line='/graphql'
 * STOP - name='spring.security.http.chains'
 *   START - name='spring.security.authentications'
 *     authentication.method='JwtAuthenticationProvider', authentication.request.type='BearerTokenAuthenticationToken'
 *   STOP - name='spring.security.authentications'
 *     authentication.result.type='JwtAuthenticationToken'
 *   START - name='spring.security.authorizations'
 *     object.type='ServerWebExchange'
 *   STOP - name='spring.security.authorizations'
 *     authorization.decision='true'
 * </pre>
 *
 * @see <a href="https://docs.spring.io/spring-security/reference/reactive/integrations/observability.html">
 *      Spring Security Observability</a>
 */
@Configuration
@ConditionalOnProperty(name = "security.debug.enabled", havingValue = "true")
@ConditionalOnClass(ObservationRegistry.class)
public class SecurityObservabilityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityObservabilityConfig.class);

    /**
     * Enables ALL Spring Security observations including filter chains.
     *
     * <p>By default, Spring Security may disable filter chain observations
     * for performance. This bean explicitly enables them for debugging.
     *
     * @return SecurityObservationSettings with all observations enabled
     */
    @Bean
    public SecurityObservationSettings securityObservationSettings() {
        log.info("Enabling Spring Security filter chain observations for debugging");
        // Use withDefaults() which enables all observations, then build()
        return SecurityObservationSettings.withDefaults().build();
    }

    /**
     * Custom ObservationHandler that logs security observations with detailed formatting.
     *
     * <p>This handler intercepts all Micrometer observations and logs them
     * with a readable format showing:
     * <ul>
     *   <li>Observation name and context</li>
     *   <li>Low cardinality tags (filter section, chain size, etc.)</li>
     *   <li>High cardinality tags (request line, etc.)</li>
     *   <li>Start/Stop events with timing</li>
     * </ul>
     *
     * @return ObservationHandler for security event logging
     */
    @Bean
    public ObservationHandler<Observation.Context> securityObservationHandler() {
        return new ObservationHandler<>() {

            private static final String SECURITY_PREFIX = "spring.security";

            @Override
            public boolean supportsContext(Observation.Context context) {
                // Only handle Spring Security observations
                String name = context.getName();
                return name != null && name.startsWith(SECURITY_PREFIX);
            }

            @Override
            public void onStart(Observation.Context context) {
                if (log.isTraceEnabled()) {
                    log.trace("SECURITY START - name='{}', context='{}'",
                            context.getName(),
                            context.getContextualName());
                    logTags(context, "  ");
                }
            }

            @Override
            public void onStop(Observation.Context context) {
                if (log.isTraceEnabled()) {
                    log.trace("SECURITY STOP  - name='{}', context='{}'",
                            context.getName(),
                            context.getContextualName());
                    logTags(context, "  ");
                }
            }

            @Override
            public void onError(Observation.Context context) {
                if (log.isDebugEnabled()) {
                    Throwable error = context.getError();
                    log.debug("SECURITY ERROR - name='{}', error='{}'",
                            context.getName(),
                            error != null ? error.getMessage() : "unknown");
                }
            }

            private void logTags(Observation.Context context, String indent) {
                // Log low cardinality key values (good for metrics)
                context.getLowCardinalityKeyValues().forEach(kv ->
                        log.trace("{}[LOW]  {}={}", indent, kv.getKey(), kv.getValue()));

                // Log high cardinality key values (good for traces)
                context.getHighCardinalityKeyValues().forEach(kv ->
                        log.trace("{}[HIGH] {}={}", indent, kv.getKey(), kv.getValue()));
            }
        };
    }

    /**
     * Alternative: Use Micrometer's built-in ObservationTextPublisher for simple console output.
     *
     * <p>This publishes ALL observations (not just security) to the console.
     * Useful for full observability debugging but can be noisy.
     *
     * <p>Disabled by default - enable via {@code security.debug.observation-text-publisher=true}
     *
     * @return ObservationTextPublisher that prints to System.out
     */
    @Bean
    @ConditionalOnProperty(name = "security.debug.observation-text-publisher", havingValue = "true")
    public ObservationTextPublisher observationTextPublisher() {
        log.info("Enabling Micrometer ObservationTextPublisher for all observations");
        return new ObservationTextPublisher();
    }
}
