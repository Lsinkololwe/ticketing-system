package com.pml.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

@Configuration
@EnableReactiveMongoAuditing
public class MongoConfig {

    @Bean("reactiveTransactionManager")
    public ReactiveTransactionManager reactiveTransactionManager(ReactiveMongoDatabaseFactory factory) {
        return new ReactiveMongoTransactionManager(factory);
    }

    @Bean
    public TransactionalOperator transactionalOperator(ReactiveTransactionManager manager) {
        return TransactionalOperator.create(manager);
    }

    /**
     * Built with the application's {@link MappingMongoConverter} rather than the
     * one {@code new ReactiveMongoTemplate(dbFactory)} creates for itself.
     *
     * <p>That single-argument constructor quietly builds a default converter,
     * which does NOT pick up {@code MongoCustomConversions}. With it, the
     * BigDecimal-to-Decimal128 converters in
     * {@code com.pml.shared.persistence.MoneyConversions} are registered as
     * beans, appear correct in every review, and never run — money keeps being
     * written as a string and every {@code $sum} keeps returning zero.
     */
    @Bean
    public ReactiveMongoTemplate reactiveMongoTemplate(ReactiveMongoDatabaseFactory dbFactory,
                                                       MappingMongoConverter converter) {
        return new ReactiveMongoTemplate(dbFactory, converter);
    }
}
