package com.pml.booking.ledger;

import com.mongodb.MongoWriteException;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.config.ChartOfAccountsSeedRunner;
import com.pml.booking.config.MongoSchemaValidationConfig;
import com.pml.booking.domain.enums.AccountSubType;
import com.pml.booking.domain.enums.AccountType;
import com.pml.booking.domain.model.ChartOfAccountsEntry;
import com.pml.booking.persistence.BookingCollections;
import com.pml.shared.config.MongoSchemaValidationProperties;
import com.pml.shared.migration.MigrationRunner;
import com.pml.shared.testing.MongoReplicaSet;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The chart of accounts is written against the collection's live validator, and the validator is
 * current before anything writes.
 *
 * <p>The validator listed five account sub-types while the model had twenty-seven, and it was applied
 * only after the startup runners had run: seeding account 5050 was refused, the seed failed, and the
 * service stopped before the newer validator could ever be applied.
 */
@Tag("L2")
@Tag("ET-FIN-001")
@DisplayName("The chart of accounts can hold every sub-type the model defines")
class ChartOfAccountsValidatorTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate template;

    @BeforeAll
    static void validatedCollection() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "booking_coa_validator"));
        template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();
        // The service's own validators, applied the way the service applies them at startup.
        new MongoSchemaValidationConfig(template, new DefaultResourceLoader(), new MongoSchemaValidationProperties())
                .run(null);
    }

    @AfterAll
    static void close() {
        client.close();
    }

    @Test
    @DisplayName("an account of every sub-type is accepted")
    void everySubTypeIsAccepted() {
        Long written = Flux.fromArray(AccountSubType.values())
                .concatMap(subType -> template.insert(ChartOfAccountsEntry.builder()
                        .accountCode("9000-" + subType.ordinal())
                        .accountName(subType.name())
                        .accountType(AccountType.EXPENSE)
                        .subType(subType)
                        .currency("ZMW")
                        .isActive(true)
                        .createdAt(Instant.parse("2026-09-19T00:00:00Z"))
                        .build()))
                .count()
                .block();

        assertThat(written).isEqualTo(AccountSubType.values().length);
    }

    @Test
    @DisplayName("the validator is live: a sub-type the model does not define is refused")
    void unknownSubTypeIsRefused() {
        Document bogus = new Document("accountCode", "9999").append("accountName", "bogus")
                .append("accountType", "EXPENSE").append("subType", "NOT_A_SUB_TYPE")
                .append("currency", "ZMW").append("isActive", true).append("createdAt", new java.util.Date());

        assertThatThrownBy(() -> template.getCollection(BookingCollections.CHART_OF_ACCOUNTS)
                .flatMap(collection -> Mono.from(collection.insertOne(bogus))).block())
                .isInstanceOf(MongoWriteException.class)
                .hasMessageContaining("Document failed validation");
    }

    @Test
    @DisplayName("startup order: migrations, then validators, then the seed")
    void startupOrder() {
        int seed = ChartOfAccountsSeedRunner.class.getAnnotation(Order.class).value();
        assertThat(MigrationRunner.ORDER).isLessThan(MongoSchemaValidationConfig.ORDER);
        assertThat(MongoSchemaValidationConfig.ORDER).isLessThan(seed);
    }
}
