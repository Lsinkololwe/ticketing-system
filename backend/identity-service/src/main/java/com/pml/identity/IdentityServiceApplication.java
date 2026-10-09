package com.pml.identity;

import com.pml.shared.config.MongoSchemaValidationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Identity and Platform Service Application
 *
 * Microservice 3 of 3: User Management & Platform Operations
 *
 * Responsibilities:
 * - User authentication and authorization (via Keycloak)
 * - User profile management
 * - Permission and role management (RBAC)
 * - Organizer payout management
 * - Platform configuration
 * - Notification delivery (email, SMS, WhatsApp)
 * - File storage coordination (S3)
 * - Audit logging and compliance
 *
 * Port: 8083
 *
 * Event Integration:
 * - A transactional outbox in MongoDB for domain event publication
 *   (com.pml.shared.event.Outbox — there is no Spring Modulith here)
 * - MongoDB Event Publication Registry for transactional outbox
 * - Azure Service Bus for cross-service messaging
 */
@SpringBootApplication(scanBasePackages = {"com.pml.identity", "com.pml.shared"})
@EnableConfigurationProperties(MongoSchemaValidationProperties.class)
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
