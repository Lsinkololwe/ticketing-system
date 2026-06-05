package com.pml.identity.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import java.util.Map;

/**
 * MongoDB Schema Validation Configuration for Identity Service.
 * <p>
 * Applies JSON Schema validation to the following collections:
 * <ul>
 *     <li><b>users</b>: Validates user documents (when schema is added)</li>
 *     <li><b>organizers</b>: Validates organizer documents (when schema is added)</li>
 *     <li><b>roles</b>: Validates role documents (when schema is added)</li>
 *     <li><b>permissions</b>: Validates permission documents (when schema is added)</li>
 * </ul>
 * </p>
 * <p>
 * Schema validation enforces:
 * <ul>
 *     <li>OWASP A03:2021 - Injection: Email/phone pattern validation</li>
 *     <li>OWASP A07:2021 - Identification and Authentication Failures:
 *         Phone number verification, email verification status</li>
 *     <li>Data integrity: Required fields, string length limits, enum constraints</li>
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
     * Defines schema mappings for Identity Service collections.
     * <p>
     * Schema files should be located at:
     * {@code src/main/resources/mongodb/schemas/}
     * </p>
     * <p>
     * OWASP Compliance:
     * <ul>
     *     <li>A01:2021 - Broken Access Control: organizationId required for tenant isolation</li>
     *     <li>A03:2021 - Injection: Email/phone pattern validation</li>
     *     <li>A07:2021 - Identification and Authentication: Phone/email verification status</li>
     * </ul>
     * </p>
     *
     * @return Map of collection names to schema file paths
     */
    @Override
    protected Map<String, String> getSchemaDefinitions() {
        Map<String, String> schemas = newSchemaMap();

        // =========================================================================
        // USER & ORGANIZATION COLLECTIONS
        // =========================================================================
        schemas.put("users", "users-schema.json");
        schemas.put("organizations", "organizations-schema.json");
        schemas.put("organization_members", "organization-members-schema.json");
        schemas.put("organizer_profiles", "organizer-profiles-schema.json");

        // =========================================================================
        // PERMISSIONS & RBAC COLLECTIONS
        // =========================================================================
        schemas.put("permissions", "permissions-schema.json");
        schemas.put("role_permissions", "role-permissions-schema.json");

        // =========================================================================
        // TEAM & COLLABORATION COLLECTIONS
        // =========================================================================
        schemas.put("team_invitations", "team-invitations-schema.json");
        schemas.put("ownership_transfers", "ownership-transfers-schema.json");
        schemas.put("event_access_grants", "event-access-grants-schema.json");

        // =========================================================================
        // VERIFICATION & DOCUMENTS COLLECTIONS
        // =========================================================================
        schemas.put("verification_documents", "verification-documents-schema.json");

        // =========================================================================
        // NOTIFICATION COLLECTIONS
        // =========================================================================
        schemas.put("notifications", "notifications-schema.json");
        schemas.put("notification_preferences", "notification-preferences-schema.json");
        schemas.put("event_reminders", "event-reminders-schema.json");

        // =========================================================================
        // DEVICE & SESSION COLLECTIONS
        // =========================================================================
        schemas.put("user_devices", "user-devices-schema.json");

        log.info("Identity Service: Configured {} collection schemas for validation", schemas.size());
        return schemas;
    }
}
