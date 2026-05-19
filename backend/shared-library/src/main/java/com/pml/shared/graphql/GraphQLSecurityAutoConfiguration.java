package com.pml.shared.graphql;

import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.AbortExecutionException;
import graphql.execution.instrumentation.Instrumentation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for GraphQL security instrumentation.
 *
 * <h2>Overview</h2>
 * <p>
 * This configuration provides defense-in-depth security for GraphQL APIs by limiting:
 * </p>
 * <ul>
 *   <li><b>Query Depth</b>: Prevents deeply nested queries that could cause stack overflow or slow execution</li>
 *   <li><b>Query Complexity</b>: Prevents excessively wide queries that request too many fields</li>
 * </ul>
 *
 * <h2>Defense in Depth</h2>
 * <p>
 * These limits work alongside Apollo Router limits. Even if the router is misconfigured
 * or bypassed, these service-level limits provide protection.
 * </p>
 *
 * <h2>Usage</h2>
 * <p>
 * This configuration is automatically applied when:
 * </p>
 * <ul>
 *   <li>graphql-java is on the classpath</li>
 *   <li>graphql.security.enabled=true (default)</li>
 * </ul>
 *
 * <h2>Configuration Properties</h2>
 * <pre>
 * graphql:
 *   security:
 *     enabled: true           # Enable/disable security instrumentation
 *     max-depth: 10           # Maximum query depth (default: 10)
 *     max-complexity: 200     # Maximum query complexity (default: 200)
 *     log-rejected-queries: true  # Log queries that exceed limits
 * </pre>
 *
 * <h2>Error Response Format</h2>
 * <p>
 * When a query exceeds limits, the response follows GraphQL error format:
 * </p>
 * <pre>
 * {
 *   "errors": [{
 *     "message": "maximum query depth exceeded 12 > 10",
 *     "extensions": {
 *       "classification": "ExecutionAborted"
 *     }
 *   }]
 * }
 * </pre>
 *
 * @see GraphQLSecurityProperties
 * @see <a href="https://www.graphql-java.com/documentation/instrumentation">graphql-java Instrumentation</a>
 * @see <a href="https://cheatsheetseries.owasp.org/cheatsheets/GraphQL_Cheat_Sheet.html">OWASP GraphQL Cheat Sheet</a>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(Instrumentation.class)
@ConditionalOnProperty(prefix = "graphql.security", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(GraphQLSecurityProperties.class)
public class GraphQLSecurityAutoConfiguration {

    /**
     * Creates a MaxQueryDepthInstrumentation bean to limit query nesting depth.
     *
     * <p>
     * This instrumentation aborts query execution if the depth exceeds the configured limit.
     * The depth is calculated as the maximum nesting level of fields in the query.
     * </p>
     *
     * <h3>Example Query Depths:</h3>
     * <pre>
     * # Depth 1
     * query { event(id: "1") { id } }
     *
     * # Depth 2
     * query { event(id: "1") { ticketTiers { id } } }
     *
     * # Depth 3
     * query { event(id: "1") { tickets { buyer { id } } } }
     * </pre>
     *
     * @param properties The GraphQL security configuration properties
     * @return MaxQueryDepthInstrumentation configured with the max depth limit
     */
    @Bean
    @ConditionalOnMissingBean(MaxQueryDepthInstrumentation.class)
    public MaxQueryDepthInstrumentation maxQueryDepthInstrumentation(GraphQLSecurityProperties properties) {
        int maxDepth = properties.getMaxDepth();

        log.info("GraphQL Security: Configuring MaxQueryDepthInstrumentation with maxDepth={}", maxDepth);

        // Create instrumentation with custom error handling
        return new MaxQueryDepthInstrumentation(maxDepth) {
            @Override
            protected AbortExecutionException mkAbortException(int depth, int maxDepth) {
                String message = String.format(
                        "%s: depth %d exceeds maximum allowed depth of %d",
                        properties.getDepthExceededMessage(),
                        depth,
                        maxDepth
                );

                if (properties.isLogRejectedQueries()) {
                    log.warn("GraphQL query rejected - {}", message);
                }

                return new AbortExecutionException(message);
            }
        };
    }

    /**
     * Creates a MaxQueryComplexityInstrumentation bean to limit query complexity.
     *
     * <p>
     * This instrumentation aborts query execution if the total field count exceeds the configured limit.
     * Complexity is calculated as the sum of all field selections in the query.
     * </p>
     *
     * <h3>Complexity Calculation:</h3>
     * <pre>
     * # Complexity: 3 (event + id + title)
     * query { event(id: "1") { id title } }
     *
     * # Complexity: 8 (event + tickets + 5 fields per ticket assuming 1 ticket)
     * query { event(id: "1") { tickets { id number status price currency } } }
     * </pre>
     *
     * @param properties The GraphQL security configuration properties
     * @return MaxQueryComplexityInstrumentation configured with the max complexity limit
     */
    @Bean
    @ConditionalOnMissingBean(MaxQueryComplexityInstrumentation.class)
    public MaxQueryComplexityInstrumentation maxQueryComplexityInstrumentation(GraphQLSecurityProperties properties) {
        int maxComplexity = properties.getMaxComplexity();

        log.info("GraphQL Security: Configuring MaxQueryComplexityInstrumentation with maxComplexity={}", maxComplexity);

        // Create instrumentation with custom error handling
        return new MaxQueryComplexityInstrumentation(maxComplexity) {
            @Override
            protected AbortExecutionException mkAbortException(int totalComplexity, int maxComplexity) {
                String message = String.format(
                        "%s: complexity %d exceeds maximum allowed complexity of %d",
                        properties.getComplexityExceededMessage(),
                        totalComplexity,
                        maxComplexity
                );

                if (properties.isLogRejectedQueries()) {
                    log.warn("GraphQL query rejected - {}", message);
                }

                return new AbortExecutionException(message);
            }
        };
    }
}
