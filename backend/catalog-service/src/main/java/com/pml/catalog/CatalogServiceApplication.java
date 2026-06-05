package com.pml.catalog;

import com.pml.shared.config.MongoSchemaValidationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;

/**
 * Event Catalog Service - Main Application
 *
 * Microservice Architecture: 1 of 3 services
 * Port: 8081
 * Bounded Context: Event Discovery & Management
 *
 * Responsibilities:
 * - Event CRUD operations
 * - Venue management
 * - Event categorization and search
 * - Event approval workflow
 * - Event publishing and lifecycle
 *
 * Collections Owned:
 * - events, venues, event_categories, event_templates
 * - event_reminders, approval_timelines
 *
 * Event Integration:
 * - Spring Modulith for domain event publication
 * - MongoDB Event Publication Registry for transactional outbox
 * - Azure Service Bus for cross-service messaging
 *
 * Scaling: 2 pods (moderate traffic, read-heavy workload)
 */
@SpringBootApplication
@EnableReactiveMongoRepositories
@EnableConfigurationProperties(MongoSchemaValidationProperties.class)
@ComponentScan(basePackages = {"com.pml.catalog", "com.pml.shared"})
public class CatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }
}
