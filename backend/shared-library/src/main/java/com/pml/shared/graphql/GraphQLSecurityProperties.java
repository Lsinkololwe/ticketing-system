package com.pml.shared.graphql;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for GraphQL security limits.
 *
 * <h2>Industry Standards for Event Ticketing Platforms</h2>
 * <p>
 * Query depth and complexity limits prevent denial-of-service attacks
 * through deeply nested or excessively wide queries.
 * </p>
 *
 * <h3>Query Depth Analysis for PML Event Ticketing:</h3>
 * <table>
 *   <tr><th>Query Pattern</th><th>Depth</th></tr>
 *   <tr><td>event { ticketTiers }</td><td>2</td></tr>
 *   <tr><td>ticket { buyer { organization } }</td><td>3</td></tr>
 *   <tr><td>user { tickets { event { location } } }</td><td>4</td></tr>
 *   <tr><td>event { tickets { edges { node { buyer } } } }</td><td>5</td></tr>
 *   <tr><td>Deep admin/federation queries</td><td>8-10</td></tr>
 * </table>
 *
 * <h3>Recommended Values by Project Type:</h3>
 * <ul>
 *   <li>Simple APIs: 5-7</li>
 *   <li>E-commerce/Ticketing: 8-12 (our case)</li>
 *   <li>Social Networks: 10-15</li>
 *   <li>Enterprise: 12-20</li>
 * </ul>
 *
 * <h2>OWASP GraphQL Security Compliance</h2>
 * <p>
 * These limits address OWASP recommendations for:
 * </p>
 * <ul>
 *   <li>Denial of Service (DoS) Prevention</li>
 *   <li>Resource Exhaustion Protection</li>
 *   <li>Query Complexity Attacks</li>
 * </ul>
 *
 * @see <a href="https://cheatsheetseries.owasp.org/cheatsheets/GraphQL_Cheat_Sheet.html">OWASP GraphQL Cheat Sheet</a>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "graphql.security")
public class GraphQLSecurityProperties {

    /**
     * Enable GraphQL security instrumentation.
     * Default: true
     */
    private boolean enabled = true;

    /**
     * Maximum allowed query depth.
     *
     * <p>
     * For event ticketing platforms, a depth of 10 allows:
     * </p>
     * <ul>
     *   <li>Normal consumer queries (depth 3-5)</li>
     *   <li>Organizer dashboard queries (depth 5-7)</li>
     *   <li>Admin/Federation queries (depth 8-10)</li>
     * </ul>
     *
     * <p>
     * Queries exceeding this depth will be aborted with an error:
     * "maximum query depth exceeded X > {maxDepth}"
     * </p>
     *
     * Default: 10
     */
    private int maxDepth = 10;

    /**
     * Maximum allowed query complexity.
     *
     * <p>
     * Complexity is calculated as the total number of fields in the query.
     * This prevents excessively wide queries that request too many fields.
     * </p>
     *
     * <p>
     * For event ticketing platforms:
     * </p>
     * <ul>
     *   <li>Simple ticket lookup: ~10-20 fields</li>
     *   <li>Event with tickets list: ~50-80 fields</li>
     *   <li>Dashboard with stats: ~100-150 fields</li>
     *   <li>Complex admin reports: ~150-200 fields</li>
     * </ul>
     *
     * Default: 200
     */
    private int maxComplexity = 200;

    /**
     * Enable detailed logging of rejected queries.
     *
     * <p>
     * When enabled, queries that exceed depth or complexity limits
     * will be logged with full query details for debugging.
     * </p>
     *
     * Default: true (recommended for development/staging)
     */
    private boolean logRejectedQueries = true;

    /**
     * Custom message prefix for depth exceeded errors.
     * Default: "Query depth limit exceeded"
     */
    private String depthExceededMessage = "Query depth limit exceeded";

    /**
     * Custom message prefix for complexity exceeded errors.
     * Default: "Query complexity limit exceeded"
     */
    private String complexityExceededMessage = "Query complexity limit exceeded";
}
