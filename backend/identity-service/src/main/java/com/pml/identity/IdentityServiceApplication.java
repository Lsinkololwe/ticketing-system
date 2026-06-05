package com.pml.identity;

import com.pml.shared.config.MongoSchemaValidationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;

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
 * - Spring Modulith for domain event publication
 * - MongoDB Event Publication Registry for transactional outbox
 * - Azure Service Bus for cross-service messaging
 */
@SpringBootApplication
@EnableReactiveMongoAuditing
@EnableConfigurationProperties(MongoSchemaValidationProperties.class)
@ComponentScan(basePackages = {"com.pml.identity", "com.pml.shared"})
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
