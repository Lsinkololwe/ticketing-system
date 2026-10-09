package com.pml.shared.testing.it;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.MongoStandalone;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that the harness's transaction guarantee is real, by showing where it is not.
 *
 * <p>The check is deliberately two-sided: a transaction test passes on the container and
 * fails against a standalone {@code mongod}.
 * Only the first half is usually written, and on its own it proves nothing — a
 * test that never fails cannot distinguish a working transaction from an absent
 * one. That is precisely the trap: against a standalone
 * {@code mongod}, {@code @Transactional} on a reactive method is
 * <strong>silently inert</strong>.
 *
 * <p>So this class runs the same two-document rollback twice and asserts the
 * outcomes differ.
 */
@Tag("L2")
@Tag("ET-PLT-006")
@DisplayName("ET-PLT-006 · a transaction is only real on a replica set")
class TransactionRealityTest {

    private static final String COLLECTION = "harness_tx_probe";

    /** The failure the business logic raises after both writes. */
    private static final class ForcedRollback extends RuntimeException {
        ForcedRollback() {
            super("forced, to trigger the rollback");
        }
    }

    @Test
    @DisplayName("ET-PLT-006-R2 · on a replica set, a rollback leaves neither document")
    void rollbackOnReplicaSetLeavesNothing() {
        try (MongoClient client = MongoClients.create(MongoReplicaSet.connectionString())) {
            ReactiveMongoDatabaseFactory factory = new SimpleReactiveMongoDatabaseFactory(client, "harness");
            ReactiveMongoTemplate template = new ReactiveMongoTemplate(factory);
            TransactionalOperator transaction =
                    TransactionalOperator.create(new ReactiveMongoTransactionManager(factory));

            reset(template);

            Mono<Void> twoWritesThenFail = write(template, "first")
                    .then(write(template, "second"))
                    .then(Mono.error(new ForcedRollback()))
                    .then()
                    .as(transaction::transactional);

            assertThat(catchFailure(twoWritesThenFail))
                    .as("the chain must fail with OUR error, meaning both writes were reached")
                    .isInstanceOf(ForcedRollback.class);

            assertThat(count(template))
                    .as("both writes must be rolled back — this is the guarantee the platform is built on")
                    .isZero();
        }
    }

    @Test
    @DisplayName("ET-PLT-006-R2 · on a standalone mongod the same guarantee does not exist")
    void standaloneCannotTransact() {
        try (MongoClient client = MongoClients.create(MongoStandalone.connectionString())) {
            ReactiveMongoDatabaseFactory factory = new SimpleReactiveMongoDatabaseFactory(client, "harness");
            ReactiveMongoTemplate template = new ReactiveMongoTemplate(factory);
            TransactionalOperator transaction =
                    TransactionalOperator.create(new ReactiveMongoTransactionManager(factory));

            reset(template);

            // A standalone mongod has no oplog, so it cannot open a transaction
            // at all. It refuses for a reason of its own, never reaching the
            // ForcedRollback the replica-set run fails with.
            Throwable failure = catchFailure(write(template, "first")
                    .then(write(template, "second"))
                    .then(Mono.error(new ForcedRollback()))
                    .then()
                    .as(transaction::transactional));

            assertThat(failure)
                    .as("a standalone mongod must refuse the transaction outright")
                    .isNotInstanceOf(ForcedRollback.class);

            // And the actual danger: with no transaction in play —
            // which is what @Transactional degrades to here — the first write
            // survives the failure. The operation was never atomic.
            reset(template);
            assertThat(catchFailure(write(template, "first")
                    .then(Mono.error(new ForcedRollback()))
                    .then()))
                    .isInstanceOf(ForcedRollback.class);

            assertThat(count(template))
                    .as("""
                        the first write survived a failure — on a standalone mongod the \
                        platform's multi-document writes are not atomic, and nothing errors \
                        to tell you so""")
                    .isOne();
        }
    }

    // ---------------------------------------------------------------- helpers

    private static Mono<Document> write(ReactiveMongoTemplate template, String id) {
        return template.save(new Document("_id", id).append("probe", true), COLLECTION);
    }

    private static long count(ReactiveMongoTemplate template) {
        return template.count(new Query(), COLLECTION).block();
    }

    /**
     * Creates the collection up front. MongoDB will not create one inside a
     * transaction on every supported topology, and a test that fails on
     * collection creation would look like a transaction failure.
     */
    private static void reset(ReactiveMongoTemplate template) {
        template.dropCollection(COLLECTION).then(template.createCollection(COLLECTION)).block();
    }

    private static Throwable catchFailure(Mono<Void> chain) {
        try {
            chain.block();
            throw new AssertionError("expected the chain to fail, and it succeeded");
        } catch (AssertionError rethrow) {
            throw rethrow;
        } catch (Throwable actual) {
            return actual;
        }
    }
}
