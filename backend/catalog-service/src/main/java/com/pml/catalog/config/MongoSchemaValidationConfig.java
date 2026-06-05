package com.pml.catalog.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import java.util.Map;

/**
 * MongoDB Schema Validation Configuration for Catalog Service.
 * <p>
 * Applies JSON Schema validation to the following collections:
 * <ul>
 *     <li><b>events</b>: Validates event documents (when schema is added)</li>
 *     <li><b>locations</b>: Validates location/venue documents (when schema is added)</li>
 *     <li><b>categories</b>: Validates event category documents (when schema is added)</li>
 *     <li><b>ticket_tiers</b>: Validates ticket tier pricing documents (when schema is added)</li>
 * </ul>
 * </p>
 * <p>
 * Schema validation enforces:
 * <ul>
 *     <li>OWASP A01:2021 - Broken Access Control: organizationId required for events</li>
 *     <li>OWASP A03:2021 - Injection: URL pattern validation, string length limits</li>
 *     <li>OWASP A04:2021 - Insecure Design: Price non-negativity, capacity limits,
 *         date range validation</li>
 *     <li>Data integrity: Required fields, enum constraints, ObjectId patterns</li>
 * </ul>
 * </p>
 *
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class MongoSchemaValidationConfig extends com.pml.shared.config.MongoSchemaValidationConfig {

    public MongoSchemaValidationConfig(
            ReactiveMongoTemplate mongoTemplate,
            ResourceLoader resourceLoader,
            com.pml.shared.config.MongoSchemaValidationProperties properties) {
        super(mongoTemplate, resourceLoader, properties);
    }

    /**
     * Defines schema mappings for Catalog Service collections.
     * <p>
     * Schema files should be located at:
     * {@code src/main/resources/mongodb/schemas/}
     * </p>
     * <p>
     * OWASP Compliance:
     * <ul>
     *     <li>A01:2021 - Broken Access Control: organizationId required for tenant isolation</li>
     *     <li>A03:2021 - Injection: URL pattern validation, string length limits</li>
     *     <li>A04:2021 - Insecure Design: Price non-negativity, capacity limits</li>
     * </ul>
     * </p>
     *
     * @return Map of collection names to schema file paths
     */
    @Override
    protected Map<String, String> getSchemaDefinitions() {
        Map<String, String> schemas = newSchemaMap();

        // =========================================================================
        // EVENT COLLECTIONS
        // =========================================================================
        schemas.put("events", "events-schema.json");
        schemas.put("ticket_tiers", "ticket-tiers-schema.json");

        // =========================================================================
        // LOCATION & GEOGRAPHY COLLECTIONS
        // =========================================================================
        schemas.put("locations", "locations-schema.json");
        schemas.put("cities", "cities-schema.json");
        schemas.put("provinces", "provinces-schema.json");

        // =========================================================================
        // CATEGORIZATION COLLECTIONS
        // =========================================================================
        schemas.put("event_categories", "event-categories-schema.json");

        // =========================================================================
        // APPROVAL WORKFLOW COLLECTIONS
        // =========================================================================
        schemas.put("approval_timelines", "approval-timelines-schema.json");
        schemas.put("approval_escalations", "approval-escalations-schema.json");
        schemas.put("approval_notifications", "approval-notifications-schema.json");

        // =========================================================================
        // PLATFORM CONFIGURATION COLLECTIONS
        // =========================================================================
        schemas.put("platform_configuration", "platform-configuration-schema.json");

        log.info("Catalog Service: Configured {} collection schemas for validation", schemas.size());
        return schemas;
    }
}
