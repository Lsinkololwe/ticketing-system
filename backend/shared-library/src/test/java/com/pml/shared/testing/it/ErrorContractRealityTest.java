package com.pml.shared.testing.it;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.DuplicateKeys;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.GraphQlErrors;
import com.pml.shared.error.PlatformDataFetcherExceptionHandler;
import com.pml.shared.error.PlatformRefusalTranslator;
import com.pml.shared.error.RefusalResolution;
import com.pml.shared.error.RefusalTranslator;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.testing.Harness;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.Persistence;
import graphql.GraphQLError;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The error contract against a real database.
 *
 * <h2>What only a container can prove</h2>
 * The idempotency mapping decides whether a duplicate key is a retryable
 * {@code RESOURCE_CONFLICT} or a never-retryable {@code IDEMPOTENCY_KEY_REUSED}
 * by reading the index name out of the driver's message. Every unit test for it
 * builds that message by hand — so those tests prove the parser matches the
 * string the test author wrote, which is exactly where the belief is untested.
 *
 * <p>If MongoDB phrases {@code E11000} differently from the assumption, the
 * detection silently falls through to the retryable branch and the platform
 * starts advising clients to repeat a charge the database refused. Nothing fails;
 * a payment is taken twice. This raises the error from a real unique index and
 * asserts the classification from that.
 */
@Tag("L2")
@Tag("ET-PLT-005")
@DisplayName("ET-PLT-005-R7 · the idempotency guard classified from a real driver error")
class ErrorContractRealityTest {

    private static final String COLLECTION = "booking_payment_intents";
    private static final String INDEX = "idx_idempotencyKey";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;

    /** The chain as wired in booking: index-aware first, platform as fallback. */
    private static final RefusalTranslator IDEMPOTENCY_AWARE = throwable ->
            DuplicateKeys.isDuplicateKey(throwable)
                    && DuplicateKeys.describe(throwable).contains(INDEX)
                    ? Optional.of(new TranslatedRefusal(
                            ErrorCode.IDEMPOTENCY_KEY_REUSED, "idempotency key already used"))
                    : Optional.empty();

    private final PlatformDataFetcherExceptionHandler handler =
            new PlatformDataFetcherExceptionHandler(
                    List.of(IDEMPOTENCY_AWARE, new PlatformRefusalTranslator()));

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "harness_error_contract"));
    }

    @AfterAll
    static void disconnect() {
        Harness.unbind();
        if (client != null) {
            client.close();
        }
    }

    @BeforeEach
    void freshCollection() {
        Harness.bind(template);
        template.dropCollection(COLLECTION).onErrorResume(ignored -> Mono.empty()).block();
        template.createCollection(COLLECTION).block();
        // Built through the driver rather than through IndexSpec, so what the
        // classification is read from is a real MongoDB index with the real
        // name — not an abstraction that could agree with the translator while
        // the database disagrees with both.
        Mono.from(template.getCollection(COLLECTION)
                        .flatMapMany(collection -> collection.createIndex(
                                new Document("idempotencyKey", 1),
                                new com.mongodb.client.model.IndexOptions()
                                        .name(INDEX)
                                        .unique(true)
                                        .partialFilterExpression(new Document(
                                                "idempotencyKey",
                                                new Document("$type", "string")))))
                        .next())
                .block();
    }

    /** Writes a payment intent, letting the real index decide the outcome. */
    private Throwable writeExpectingFailure(String idempotencyKey) {
        Document intent = new Document("idempotencyKey", idempotencyKey)
                .append("amount", 250);
        try {
            template.insert(intent, COLLECTION).block();
            return null;
        } catch (RuntimeException thrown) {
            return thrown;
        }
    }

    @Test
    @DisplayName("a real duplicate key on the idempotency index is never advertised as retryable")
    void realDuplicateKeyIsNotRetryable() {
        assertThat(writeExpectingFailure("key-abc"))
                .as("the first write must succeed, or the second proves nothing")
                .isNull();

        Throwable thrown = writeExpectingFailure("key-abc");
        assertThat(thrown)
                .as("the unique index did not reject the replay — the guard is not in place")
                .isNotNull();

        GraphQLError error = handler
                .handleException(DataFetcherExceptionHandlerParameters
                        .newExceptionParameters().exception(thrown).build())
                .join()
                .getErrors()
                .get(0);

        assertThat(error.getExtensions())
                .as("""
                    MongoDB's E11000 text is the only signal carrying the index name. If it does \
                    not match what the translator looks for, this falls through to the retryable \
                    branch and the platform advises repeating a charge the database refused.""")
                .containsEntry(GraphQlErrors.ERROR_CODE, ErrorCode.IDEMPOTENCY_KEY_REUSED.name())
                .containsEntry(GraphQlErrors.RETRYABLE, false);
    }

    @Test
    @DisplayName("the driver's message really does name the index")
    void theDriverNamesTheIndex() {
        writeExpectingFailure("key-named");
        Throwable thrown = writeExpectingFailure("key-named");

        // Stated separately from the classification so a failure says which of
        // the two assumptions broke: the message shape, or the mapping built on it.
        assertThat(DuplicateKeys.describe(RefusalResolution.unwrap(thrown)))
                .as("the index name is what distinguishes an idempotency replay from any "
                        + "other duplicate key")
                .contains(INDEX)
                .contains("E11000");
    }

    @Test
    @DisplayName("a refused write leaves the collection exactly as it was")
    void aRefusedWritePersistsNothing() {
        writeExpectingFailure("key-once");
        writeExpectingFailure("key-once");

        // A refused operation persists nothing. The
        // interesting half is that the *first* write is still there — a rollback
        // that took the original with it would also satisfy "the replay wrote
        // nothing", and would be far worse.
        Persistence.assertExactly(template, COLLECTION, 1);
    }

    @Test
    @DisplayName("a duplicate on some other index stays retryable")
    void unrelatedDuplicateKeyRemainsRetryable() {
        template.indexOps(COLLECTION)
                .ensureIndex(new org.springframework.data.mongodb.core.index.Index()
                        .on("amount", org.springframework.data.domain.Sort.Direction.ASC)
                        .named("idx_amount")
                        .unique())
                .block();

        template.insert(new Document("amount", 999), COLLECTION).block();

        Throwable thrown = null;
        try {
            template.insert(new Document("amount", 999), COLLECTION).block();
        } catch (RuntimeException duplicate) {
            thrown = duplicate;
        }
        assertThat(thrown).isNotNull();

        Optional<DomainRefusal> refusal = RefusalResolution.resolve(
                thrown, List.of(IDEMPOTENCY_AWARE, new PlatformRefusalTranslator()));

        assertThat(refusal).isPresent();
        assertThat(refusal.get().errorCode())
                .as("guessing 'idempotency' for an unknown index would tell a caller a key was "
                        + "reused when they never sent one")
                .isEqualTo(ErrorCode.RESOURCE_CONFLICT);
        assertThat(refusal.get().retryable()).isTrue();
    }
}
