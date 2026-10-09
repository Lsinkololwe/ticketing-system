package com.pml.booking;

import com.pml.shared.config.MongoSchemaValidationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Booking & Ticketing Service - Main Application
 *
 * Microservice Architecture: 2 of 3 services
 * Port: 8082
 * Bounded Context: Ticket Inventory & Orders
 *
 * Responsibilities:
 * - Ticket inventory management
 * - Ticket purchase transactions
 * - Payment processing
 * - QR code generation
 * - Ticket validation
 * - Refund processing
 *
 * Collections Owned:
 * - tickets, ticket_purchases, payments
 * - ticket_inventory, qr_codes
 *
 * Event Integration:
 * - A transactional outbox in MongoDB for domain event publication
 *   (com.pml.shared.event.Outbox — there is no Spring Modulith here)
 * - Azure Service Bus for cross-service messaging
 *
 * Scaling: 3 pods (highest traffic, transaction-heavy workload)
 *
 * Key Features:
 * - Optimistic locking for inventory management
 * - Distributed caching with Redis
 * - Idempotent payment processing
 * - QR code generation for ticket validation
 * - Real-time inventory updates
 */
@SpringBootApplication(scanBasePackages = {"com.pml.booking", "com.pml.shared"})
@EnableReactiveMongoRepositories
@EnableScheduling
@EnableConfigurationProperties(MongoSchemaValidationProperties.class)
public class BookingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}
