package com.pml.shared.migration;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.persistence.IndexSpec;
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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An index the database already has is brought to what the index registry declares.
 *
 * <h2>The defect this reproduces</h2>
 * Found on the live database, in two collections: a <b>plain</b> index on {@code expiresAt} where
 * the registry declares a <b>TTL</b> index. MongoDB refuses to create the declared one because
 * the keys collide, so nothing ever expires — and the index is present, correctly named in the
 * old scheme, and visibly fine unless someone reads {@code expireAfterSeconds}.
 *
 * <p>So the assertion is not "an index called {@code idx_expiresAt_ttl} exists". It is that the
 * index which exists <b>actually expires things</b>. Asserting the name would have passed against
 * the broken database.</p>
 */
@Tag("L2")
@Tag("ET-PLT-002")
@DisplayName("ET-PLT-002-R3 · a pre-registry index is replaced by the declared one")
class ConflictingIndexRepairTest {

    private static final String COLLECTION = "repair_harness";

    private static MongoClient mongoClient;
    private static ReactiveMongoTemplate mongo;
    private static ConflictingIndexRepair repair;

    @BeforeAll
    static void connect() {
        mongoClient = MongoClients.create(MongoReplicaSet.connectionString());
        mongo = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(mongoClient, "index_repair_harness"));
        repair = new ConflictingIndexRepair(mongo);
    }

    @AfterAll
    static void disconnect() {
        mongoClient.close();
    }

    @BeforeEach
    void freshCollection() {
        mongo.getMongoDatabase()
                .flatMap(database -> Mono.from(database.getCollection(COLLECTION).drop()))
                .onErrorResume(absent -> Mono.empty())
                .then(mongo.getMongoDatabase().flatMap(database ->
                        Mono.from(database.createCollection(COLLECTION))))
                .block();
    }

    @Test
    @DisplayName("a plain index where a TTL is declared is replaced, and the replacement expires")
    void plainIndexBecomesATtlIndex() {
        createRaw("expiresAt", new IndexOptions().name("expiresAt"));

        assertThat(ttlSecondsOf("expiresAt"))
                .as("the starting state, reproduced from the live database")
                .isNull();

        IndexSpec declared = IndexSpec.on(COLLECTION, "idx_expiresAt_ttl")
                .asc("expiresAt")
                .expireAfter(Duration.ZERO)
                .build();

        String report = repair.repair(List.of(declared)).block();

        assertThat(report).contains("replaced").contains("expiresAt");
        assertThat(indexNames()).contains("idx_expiresAt_ttl").doesNotContain("expiresAt");
        assertThat(ttlSecondsOf("idx_expiresAt_ttl"))
                .as("""
                    The whole defect: an index with the right keys and the wrong options. \
                    Asserting only that the declared name exists would pass against a database \
                    where nothing expires.""")
                .isEqualTo(0L);
    }

    @Test
    @DisplayName("uniqueness declared by the registry survives the replacement")
    void uniquenessIsCarriedOver() {
        createRaw("idempotencyKey", new IndexOptions().name("idempotencyKey").unique(true));

        IndexSpec declared = IndexSpec.on(COLLECTION, "idx_idempotencyKey")
                .asc("idempotencyKey")
                .unique()
                .build();

        repair.repair(List.of(declared)).block();

        Document replacement = indexNamed("idx_idempotencyKey");
        assertThat(replacement).isNotNull();
        assertThat(replacement.getBoolean("unique"))
                .as("dropping a unique index and recreating it without unique() would remove a "
                        + "guarantee while reporting success")
                .isTrue();
    }

    /**
     * The right name and the wrong constraint. The server refuses this with error <b>86</b>
     * rather than 85, and a repair that stops at the name reports "already declared" while the
     * index on disk enforces something else — which is the state that reads as correct in every
     * listing and admits the duplicate the registry meant to forbid.
     */
    @Test
    @DisplayName("an index with the declared name but the wrong options is rebuilt")
    void sameNameWrongOptionsIsRebuilt() {
        createRaw("email", new IndexOptions().name("idx_email").unique(true).sparse(true));

        IndexSpec declared = IndexSpec.on(COLLECTION, "idx_email")
                .asc("email")
                .unique()
                .partialWhereTypeIs("email", "string")
                .build();

        assertThat(repair.repair(List.of(declared)).block()).contains("replaced");

        Document rebuilt = indexNamed("idx_email");
        assertThat(rebuilt.getBoolean("sparse"))
                .as("sparse still indexes a stored null, so under unique the second one collides")
                .isNull();
        assertThat(rebuilt.get("partialFilterExpression", Document.class))
                .as("the partial filter is the thing that makes the constraint correct")
                .isNotNull();
        assertThat(rebuilt.getBoolean("unique")).isTrue();
    }

    @Test
    @DisplayName("an index already carrying the declared name is left alone")
    void alreadyCorrectIsUntouched() {
        IndexSpec declared = IndexSpec.on(COLLECTION, "idx_status")
                .asc("status")
                .build();
        createRaw("status", new IndexOptions().name("idx_status"));

        assertThat(repair.repair(List.of(declared)).block())
                .isEqualTo("no index needed repair");
        assertThat(indexNames()).contains("idx_status");
    }

    @Test
    @DisplayName("running it twice changes nothing the second time")
    void isIdempotent() {
        createRaw("expiresAt", new IndexOptions().name("expiresAt"));
        IndexSpec declared = IndexSpec.on(COLLECTION, "idx_expiresAt_ttl")
                .asc("expiresAt")
                .expireAfter(Duration.ZERO)
                .build();

        repair.repair(List.of(declared)).block();

        assertThat(repair.repair(List.of(declared)).block())
                .as("the ledger already makes this one-shot; the step being a no-op on its own "
                        + "means a cleared ledger row cannot cause a needless rebuild")
                .isEqualTo("no index needed repair");
        assertThat(ttlSecondsOf("idx_expiresAt_ttl")).isEqualTo(0L);
    }

    /**
     * The case that slipped through on the live database, and the log said so if you read it:
     * the repair reported "no index needed repair" while {@code IndexEnsurer} in the same boot
     * reported "1 conflicting". Two components disagreeing about the same collection, with
     * nothing to say which was right.
     */
    @Test
    @DisplayName("a text index under another name is replaced, despite storing different keys")
    void textIndexIsMatchedDespiteItsStoredKeys() {
        mongo.getMongoDatabase()
                .flatMap(database -> Mono.from(database.getCollection(COLLECTION)
                        .createIndex(new Document("title", "text").append("description", "text"),
                                new IndexOptions().name("Event_TextIndex"))))
                .block();

        assertThat(indexNamed("Event_TextIndex").get("key", Document.class))
                .as("""
                    The server rewrites a text index's keys to _fts/_ftsx and moves the field \
                    names into `weights`. Comparing the declared key document to this one never \
                    matches, so a repair keyed on equality silently does nothing.""")
                .containsKey("_fts");

        IndexSpec declared = IndexSpec.on(COLLECTION, "idx_text_search")
                .text("title", "description")
                .build();

        assertThat(repair.repair(List.of(declared)).block())
                .contains("replaced").contains("Event_TextIndex");
        assertThat(indexNames()).contains("idx_text_search").doesNotContain("Event_TextIndex");
    }

    @Test
    @DisplayName("a text index whose weights changed is rebuilt with the declared weights")
    void reweightedTextIndexIsRebuilt() {
        mongo.getMongoDatabase()
                .flatMap(database -> Mono.from(database.getCollection(COLLECTION)
                        .createIndex(new Document("title", "text").append("description", "text"),
                                new IndexOptions().name("idx_text_search"))))
                .block();
        IndexSpec declared = IndexSpec.on(COLLECTION, "idx_text_search")
                .text("title", 5)
                .text("description", 1)
                .build();

        assertThat(repair.repair(List.of(declared)).block()).contains("replaced");
        assertThat(indexNamed("idx_text_search").get("weights", Document.class))
                .containsEntry("title", 5).containsEntry("description", 1);
        assertThat(repair.repair(List.of(declared)).block())
                .as("once the weights match there is nothing left to repair")
                .isEqualTo("no index needed repair");
    }

    @Test
    @DisplayName("a declared index nothing conflicts with is left to the ensurer")
    void absentIndexIsNotThisStepsJob() {
        IndexSpec declared = IndexSpec.on(COLLECTION, "idx_untouched").asc("nothing").build();

        assertThat(repair.repair(List.of(declared)).block()).isEqualTo("no index needed repair");
        assertThat(indexNames()).doesNotContain("idx_untouched");
    }

    // --------------------------------------------------------------------- helpers

    private void createRaw(String field, IndexOptions options) {
        mongo.getMongoDatabase()
                .flatMap(database -> Mono.from(database.getCollection(COLLECTION)
                        .createIndex(new Document(field, 1), options)))
                .block();
    }

    private List<String> indexNames() {
        return listIndexes().stream().map(index -> index.getString("name")).toList();
    }

    private Document indexNamed(String name) {
        return listIndexes().stream()
                .filter(index -> name.equals(index.getString("name")))
                .findFirst().orElse(null);
    }

    /** {@code null} when the index has no TTL — which is the broken state being reproduced. */
    private Long ttlSecondsOf(String name) {
        Document index = indexNamed(name);
        if (index == null) {
            return null;
        }
        Number seconds = index.get("expireAfterSeconds", Number.class);
        return seconds == null ? null : seconds.longValue();
    }

    private List<Document> listIndexes() {
        return mongo.getMongoDatabase()
                .flatMapMany(database -> database.getCollection(COLLECTION).listIndexes())
                .collectList()
                .block();
    }
}
