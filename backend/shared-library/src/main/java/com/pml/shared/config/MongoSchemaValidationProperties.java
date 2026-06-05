package com.pml.shared.config;

import com.mongodb.client.model.ValidationAction;
import com.mongodb.client.model.ValidationLevel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for MongoDB schema validation.
 * <p>
 * Allows runtime configuration of schema validation behavior without code changes.
 * </p>
 *
 * @see com.pml.shared.config.MongoSchemaValidationConfig
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "mongodb.schema-validation")
public class MongoSchemaValidationProperties {

    /**
     * Enable or disable schema validation at application startup.
     * Default: true
     */
    private boolean enabled = true;

    /**
     * MongoDB validation action when documents fail validation.
     * <ul>
     *     <li>ERROR: Reject invalid documents (recommended for production)</li>
     *     <li>WARN: Log validation failures but allow writes (useful for migration)</li>
     * </ul>
     * Default: ERROR
     */
    private ValidationAction validationAction = ValidationAction.ERROR;

    /**
     * MongoDB validation level.
     * <ul>
     *     <li>STRICT: Validate all inserts and updates</li>
     *     <li>MODERATE: Skip validation for existing invalid documents (migration mode)</li>
     *     <li>OFF: Disable validation</li>
     * </ul>
     * Default: STRICT
     */
    private ValidationLevel validationLevel = ValidationLevel.STRICT;

    /**
     * Whether to fail application startup if schema validation cannot be applied.
     * <p>
     * PRODUCTION: Should be true to ensure data integrity (recommended).
     * DEVELOPMENT: Can be set to false for graceful degradation during schema development.
     * </p>
     * Default: true (production-safe default)
     */
    private boolean failOnValidationError = true;

    /**
     * Base path for JSON schema files in classpath resources.
     * Default: mongodb/schemas
     */
    private String schemaBasePath = "mongodb/schemas";

    /**
     * Timeout in milliseconds for schema application operations.
     * Default: 30000 (30 seconds)
     */
    private long operationTimeoutMs = 30000;
}
