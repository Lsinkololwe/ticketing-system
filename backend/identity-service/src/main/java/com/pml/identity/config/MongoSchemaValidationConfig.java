package com.pml.identity.config;

import com.pml.identity.persistence.IdentityCollections;

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
        schemas.put(IdentityCollections.USERS, "users-schema.json");
        schemas.put(IdentityCollections.ORGANIZATIONS, "organizations-schema.json");
        schemas.put(IdentityCollections.ORGANIZATION_MEMBERS, "organization-members-schema.json");

        // =========================================================================
        // ACCOUNTS, CONTACTS & AUDIT (ET-IDN-004)
        // =========================================================================
        schemas.put(IdentityCollections.CONTACTS, "contacts-schema.json");
        schemas.put(IdentityCollections.CONSENTS, "consents-schema.json");
        schemas.put(IdentityCollections.ACCOUNT_EVENTS, "account-events-schema.json");
        schemas.put(IdentityCollections.AUDIT_LOGS, "audit-logs-schema.json");
        schemas.put(IdentityCollections.SYSTEM_ALERTS, "system-alerts-schema.json");
        schemas.put(IdentityCollections.ANNOUNCEMENTS, "announcements-schema.json");
        schemas.put(IdentityCollections.TOKEN_REVOCATIONS, "token-revocations-schema.json");

        // =========================================================================
        // PERMISSIONS & RBAC COLLECTIONS
        // =========================================================================

        // =========================================================================
        // TEAM & COLLABORATION COLLECTIONS
        // =========================================================================
        schemas.put(IdentityCollections.TEAM_INVITATIONS, "team-invitations-schema.json");
        schemas.put(IdentityCollections.OWNERSHIP_TRANSFERS, "ownership-transfers-schema.json");
        schemas.put(IdentityCollections.EVENT_ACCESS_GRANTS, "event-access-grants-schema.json");

        // =========================================================================
        // VERIFICATION & DOCUMENTS COLLECTIONS
        // =========================================================================
        schemas.put(IdentityCollections.VERIFICATION_DOCUMENTS, "verification-documents-schema.json");

        // =========================================================================
        // NOTIFICATION COLLECTIONS
        // =========================================================================
        schemas.put(IdentityCollections.NOTIFICATIONS, "notifications-schema.json");
        schemas.put(IdentityCollections.NOTIFICATION_PREFERENCES, "notification-preferences-schema.json");
        schemas.put(IdentityCollections.EVENT_REMINDERS, "event-reminders-schema.json");

        // =========================================================================
        // DEVICE & SESSION COLLECTIONS
        // =========================================================================
        schemas.put(IdentityCollections.USER_DEVICES, "user-devices-schema.json");

        log.info("Identity Service: Configured {} collection schemas for validation", schemas.size());
        return schemas;
    }
}
