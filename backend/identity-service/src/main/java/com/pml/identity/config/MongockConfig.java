package com.pml.identity.config;

import com.mongodb.reactivestreams.client.MongoClient;
import io.mongock.driver.mongodb.reactive.driver.MongoReactiveDriver;
import io.mongock.runner.springboot.EnableMongock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mongock Configuration for MongoDB Migrations.
 *
 * Mongock provides versioned, auditable database migrations for MongoDB.
 * This configuration uses the reactive driver for compatibility with
 * our reactive Spring Boot application.
 *
 * Note: Mongock migrations are inherently synchronous (changes run in order),
 * but we use the reactive driver to avoid importing the sync MongoDB driver.
 *
 * @see <a href="https://docs.mongock.io">Mongock Documentation</a>
 */
@Configuration
@EnableMongock
public class MongockConfig {

    @Value("${spring.data.mongodb.database:identity-db}")
    private String databaseName;

    /**
     * Configure Mongock's MongoDB Reactive Driver.
     *
     * Uses default lock settings which are suitable for most applications:
     * - Lock acquired for: 1 minute
     * - Lock quit trying after: 3 minutes
     * - Lock try frequency: 1 second
     *
     * Transactions are enabled when using MongoDB replica set.
     * Set MONGOCK_TRANSACTIONAL=false for standalone MongoDB.
     */
    @Bean
    public MongoReactiveDriver mongockDriver(MongoClient mongoClient,
            @Value("${mongock.transactional:true}") boolean transactional) {
        MongoReactiveDriver driver = MongoReactiveDriver.withDefaultLock(mongoClient, databaseName);
        if (!transactional) {
            driver.disableTransaction();
        }
        return driver;
    }
}
