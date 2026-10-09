package com.pml.shared.migration;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.testing.MongoReplicaSet;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A pre-registry collection is removed only once it is provably redundant.
 *
 * <h2>What makes this worth testing rather than eyeballing</h2>
 * The drop is irreversible and the input is a live database, so the interesting case is not the
 * happy one. It is the collection that <em>looks</em> redundant — same name shape, same era,
 * target has more documents — and holds one row the target does not. "The target is newer, so
 * the source is stale" is a plausible reading and it silently loses that row.
 *
 * <p>So the tests below are weighted towards refusal: a single unmatched document has to be
 * enough to keep the whole collection.</p>
 */
@Tag("L2")
@Tag("ET-PLT-002")
@DisplayName("ET-PLT-002 · a redundant source collection is dropped, a divergent one is kept")
class RedundantSourceDropTest {

    private static final String SOURCE = "drop_harness_source";
    private static final String TARGET = "drop_harness_target";

    private static MongoClient mongoClient;
    private static ReactiveMongoTemplate mongo;
    private static RedundantSourceDrop drop;

    @BeforeAll
    static void connect() {
        mongoClient = MongoClients.create(MongoReplicaSet.connectionString());
        mongo = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(mongoClient, "redundant_drop_harness"));
        drop = new RedundantSourceDrop(mongo);
    }

    @AfterAll
    static void disconnect() {
        mongoClient.close();
    }

    @BeforeEach
    void clean() {
        Flux.just(SOURCE, TARGET)
                .concatMap(collection -> mongo.getMongoDatabase()
                        .flatMap(database -> Mono.from(database.getCollection(collection).drop()))
                        .onErrorResume(absent -> Mono.empty()))
                .then().block();
    }

    @Test
    @DisplayName("a source whose documents are all in the target is dropped")
    void fullyRedundantSourceIsDropped() {
        insert(SOURCE, "a", "b");
        insert(TARGET, "a", "b", "c");

        String report = drop.dropIfRedundant(SOURCE, TARGET).block();

        assertThat(report).contains("dropped");
        assertThat(collections()).doesNotContain(SOURCE).contains(TARGET);
        assertThat(count(TARGET)).as("the target is untouched").isEqualTo(3);
    }

    @Test
    @DisplayName("one unmatched document keeps the whole collection")
    void oneDivergentDocumentIsEnoughToKeepIt() {
        insert(SOURCE, "a", "b", "only-here");
        insert(TARGET, "a", "b");

        String report = drop.dropIfRedundant(SOURCE, TARGET).block();

        assertThat(report)
                .as("""
                    'The target is newer so the source is stale' is a plausible reading and it \
                    loses this row. The count is what turns the decision back over to a human.""")
                .contains("KEPT")
                .contains("1 document(s)")
                .contains("only-here");
        assertThat(collections()).contains(SOURCE);
        assertThat(count(SOURCE)).isEqualTo(3);
    }

    @Test
    @DisplayName("a large divergence is reported as 'at least', not as an exact count")
    void aCappedCountSaysSo() {
        String[] many = new String[40];
        for (int i = 0; i < many.length; i++) {
            many[i] = "doc-" + i;
        }
        insert(SOURCE, many);
        insert(TARGET, "doc-0");

        assertThat(drop.dropIfRedundant(SOURCE, TARGET).block())
                .as("""
                    The scan stops at a sample limit, so printing its size as an exact count \
                    turns 39 — or 39,000 — into "25". A number that reads as precise and is not \
                    is worse than no number: it makes the difference look surveyable.""")
                .contains("at least 25")
                .contains("KEPT");
    }

    @Test
    @DisplayName("an empty source is dropped; it can hold nothing the target lacks")
    void emptySourceIsDropped() {
        mongo.getMongoDatabase()
                .flatMap(database -> Mono.from(database.createCollection(SOURCE))).block();
        insert(TARGET, "a");

        assertThat(drop.dropIfRedundant(SOURCE, TARGET).block()).contains("dropped");
        assertThat(collections()).doesNotContain(SOURCE);
    }

    @Test
    @DisplayName("a source that no longer exists is reported, not an error")
    void absentSourceIsFine() {
        insert(TARGET, "a");

        assertThat(drop.dropIfRedundant(SOURCE, TARGET).block()).contains("already absent");
    }

    @Test
    @DisplayName("an empty target keeps a non-empty source")
    void emptyTargetKeepsEverything() {
        insert(SOURCE, "a", "b");

        assertThat(drop.dropIfRedundant(SOURCE, TARGET).block()).contains("KEPT");
        assertThat(count(SOURCE)).isEqualTo(2);
    }

    @Test
    @DisplayName("running it twice is safe")
    void isIdempotent() {
        insert(SOURCE, "a");
        insert(TARGET, "a");

        assertThat(drop.dropIfRedundant(SOURCE, TARGET).block()).contains("dropped");
        assertThat(drop.dropIfRedundant(SOURCE, TARGET).block()).contains("already absent");
    }

    // --------------------------------------------------------------------- helpers

    private void insert(String collection, String... ids) {
        Flux.fromArray(ids)
                .concatMap(id -> mongo.getMongoDatabase().flatMap(database -> Mono.from(
                        database.getCollection(collection)
                                .insertOne(new Document("_id", id).append("payload", id)))))
                .then().block();
    }

    private long count(String collection) {
        return mongo.getMongoDatabase()
                .flatMap(database -> Mono.from(database.getCollection(collection).countDocuments()))
                .block();
    }

    private List<String> collections() {
        return mongo.getMongoDatabase()
                .flatMapMany(database -> database.listCollectionNames())
                .collectList().block();
    }
}
