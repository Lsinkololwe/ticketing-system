package com.pml.shared.persistence;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveDataAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * Multi-document transactions for every service: the outbox row commits with its business write.
 *
 * <p>Spring Boot configures a reactive Mongo template but no transaction manager. The template
 * itself stays Boot's, which is built with the application's {@code MappingMongoConverter} and so
 * with the {@code MoneyConversions} that store money as {@code Decimal128}.
 */
@AutoConfiguration(after = MongoReactiveDataAutoConfiguration.class)
@ConditionalOnClass({ReactiveMongoTransactionManager.class, TransactionalOperator.class})
@ConditionalOnBean(ReactiveMongoDatabaseFactory.class)
public class PlatformMongoTransactionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ReactiveTransactionManager.class)
    public ReactiveMongoTransactionManager reactiveTransactionManager(ReactiveMongoDatabaseFactory factory) {
        return new ReactiveMongoTransactionManager(factory);
    }

    @Bean
    @ConditionalOnMissingBean
    public TransactionalOperator transactionalOperator(ReactiveTransactionManager reactiveTransactionManager) {
        return TransactionalOperator.create(reactiveTransactionManager);
    }
}
