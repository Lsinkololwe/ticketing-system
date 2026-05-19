package com.pml.catalog.config;

import com.mongodb.reactivestreams.client.MongoClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * MongoDB Configuration for Catalog Service
 *
 * Configures:
 * - Reactive MongoDB template
 * - Transaction manager for atomic operations
 * - Transactional operator for programmatic transactions
 * - Auditing support (createdAt, updatedAt fields)
 *
 * TRANSACTION REQUIREMENTS:
 * - MongoDB must be running as a replica set (required for transactions)
 * - Connection string must include replicaSet parameter
 * - Example: mongodb://localhost:27017/db?replicaSet=rs0
 *
 * EVENT PUBLICATION:
 * Uses Spring Modulith with MongoDB Event Publication Registry.
 * Events are published via ApplicationEventPublisher and stored
 * transactionally in MongoDB for reliable delivery.
 */
@Configuration
@EnableReactiveMongoAuditing
public class MongoConfig {

    /**
     * Reactive MongoDB Transaction Manager
     *
     * Enables ACID transactions in MongoDB (requires replica set).
     * Used by Spring's @Transactional annotation and TransactionalOperator.
     */
    @Bean
    public ReactiveTransactionManager transactionManager(ReactiveMongoDatabaseFactory factory) {
        return new ReactiveMongoTransactionManager(factory);
    }

    /**
     * Transactional Operator for Programmatic Transactions
     *
     * Preferred method for reactive transactions.
     * Use with .as(transactionalOperator::transactional)
     *
     * Example:
     * ```java
     * return eventRepo.save(event)
     *     .doOnSuccess(saved -> eventPublisher.publishEvent(domainEvent))
     *     .as(transactionalOperator::transactional);
     * ```
     */
    @Bean
    public TransactionalOperator transactionalOperator(ReactiveTransactionManager manager) {
        return TransactionalOperator.create(manager);
    }

    /**
     * Reactive MongoDB Template
     *
     * Provides low-level MongoDB operations when needed.
     * Most operations should use repositories instead.
     */
    @Bean
    public ReactiveMongoTemplate reactiveMongoTemplate(MongoClient mongoClient) {
        return new ReactiveMongoTemplate(mongoClient, "event_ticketing_prod");
    }
}
