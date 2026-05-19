package com.pml.booking.config;

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
 * MongoDB Configuration for Booking Service
 *
 * Enables ACID transactions for atomic ticket purchases and event publication.
 * Uses Spring Modulith's MongoDB Event Publication Registry for transactional outbox.
 */
@Configuration
@EnableReactiveMongoAuditing
public class MongoConfig {

    @Bean("reactiveTransactionManager")
    public ReactiveTransactionManager reactiveTransactionManager(ReactiveMongoDatabaseFactory factory) {
        return new ReactiveMongoTransactionManager(factory);
    }

    @Bean
    public TransactionalOperator transactionalOperator(
            @org.springframework.beans.factory.annotation.Qualifier("reactiveTransactionManager")
            ReactiveTransactionManager manager) {
        return TransactionalOperator.create(manager);
    }

    @Bean
    public ReactiveMongoTemplate reactiveMongoTemplate(MongoClient mongoClient) {
        return new ReactiveMongoTemplate(mongoClient, "event_ticketing_prod");
    }
}
