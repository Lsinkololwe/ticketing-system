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
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The rename actually moves documents, and refuses when it would lose them.
 *
 * <h2>Against a container, not a mock</h2>
 * Everything interesting here is server behaviour: whether {@code renameCollection} carries
 * indexes across, what it does when the target exists, what {@code collectionExists} reports for
 * a collection created only by an index build. A stub would encode what I assume the server
 * does, and the assumption is the thing under test.
 *
 * <h2>The refusals are the point</h2>
 * A migration that moves data is easy to test and easy to get right. The cases worth writing
 * down are the ones where it must decline: a populated target means someone has already written
 * to the new name, and no automatic resolution is safe.
 */
@Tag("L2")
@Tag("ET-PLT-002")
@DisplayName("ET-PLT-002-R2 · collections move to their registry names, or refuse to")
class CollectionRenameMigrationTest {

    private static final String OLD = "rename_probe_old";
    private static final String NEW = "rename_probe_new";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static CollectionRenameMigration migration;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "rename_harness"));
        migration = new CollectionRenameMigration(template);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void reset() {
        Flux.just(OLD, NEW)
                .concatMap(name -> template.dropCollection(name))
                .then().block();
    }

    @Test
    @DisplayName("documents move to the new name and the old collection is gone")
    void documentsMove() {
        insert(OLD, "one", "two", "three");

        CollectionRenameMigration.Result result =
                migration.migrate(Map.of(OLD, NEW)).block();

        assertThat(result).isNotNull();
        assertThat(result.isClean()).as("%s", result).isTrue();
        assertThat(result.renamed()).isEqualTo(1);

        assertThat(names(NEW))
                .as("every document must arrive under the registry name")
                .containsExactlyInAnyOrder("one", "two", "three");
        assertThat(template.collectionExists(OLD).block())
                .as("leaving the source behind would let a stale reader keep writing to it")
                .isFalse();
    }

    @Test
    @DisplayName("indexes travel with the collection")
    void indexesSurvive() {
        insert(OLD, "one");
        template.indexOps(OLD).createIndex(
                new org.springframework.data.mongodb.core.index.Index().on("name", org.springframework.data.domain.Sort.Direction.ASC)
        ).block();

        migration.migrate(Map.of(OLD, NEW)).block();

        // The reason this migration renames rather than copying with $out: $out writes
        // documents and nothing else, so every index would have to be rebuilt afterwards — and
        // a missing index is not an error, it is a COLLSCAN nobody notices until load.
        List<String> indexNames = template.indexOps(NEW).getIndexInfo()
                .map(info -> info.getName()).collectList().block();
        assertThat(indexNames).contains("name_1");
    }

    @Test
    @DisplayName("an empty collection at the new name is replaced, not treated as a conflict")
    void emptyTargetIsNotAConflict() {
        insert(OLD, "one", "two");
        // What index or schema-validator setup leaves behind on a service that booted against
        // the renamed code before the migration ran. This is the ordinary case, not an edge:
        // refusing here would mean never migrating any environment that started the app first.
        template.createCollection(NEW).block();

        CollectionRenameMigration.Result result = migration.migrate(Map.of(OLD, NEW)).block();

        assertThat(result.isClean()).as("%s", result).isTrue();
        assertThat(names(NEW)).containsExactlyInAnyOrder("one", "two");
    }

    @Test
    @DisplayName("a populated target is refused rather than replaced")
    void populatedTargetIsRefused() {
        insert(OLD, "from-old");
        insert(NEW, "already-here");

        CollectionRenameMigration.Result result = migration.migrate(Map.of(OLD, NEW)).block();

        assertThat(result.isClean()).isFalse();
        assertThat(result.refusals()).singleElement()
                .satisfies(refusal -> assertThat(refusal.detail()).contains("already holds"));

        assertThat(names(NEW))
                .as("the documents at the new name must survive untouched — replacing them is "
                        + "the one outcome that cannot be undone")
                .containsExactly("already-here");
        assertThat(names(OLD))
                .as("and the source is left alone, so an operator can still reconcile the two")
                .containsExactly("from-old");
    }

    @Test
    @DisplayName("running twice is safe — the second run finds nothing to do")
    void isIdempotent() {
        insert(OLD, "one");

        migration.migrate(Map.of(OLD, NEW)).block();
        CollectionRenameMigration.Result second = migration.migrate(Map.of(OLD, NEW)).block();

        assertThat(second.isClean()).isTrue();
        assertThat(second.renamed()).isZero();
        assertThat(names(NEW)).containsExactly("one");
    }

    @Test
    @DisplayName("renaming an EMPTY collection onto itself is refused before anything runs")
    void selfRenameIsRefused() {
        // Empty on purpose, and that is the whole test.
        //
        // With documents in it, a self-rename is already stopped by the populated-target check
        // — so asserting on a populated collection proves that check works and says nothing
        // about this one. Empty, the populated-target branch waves it through to
        // `dropCollection(to)`, which drops the source, and the rename that follows has nothing
        // left to rename. The from.equals(to) guard is the only thing standing there.
        template.createCollection(OLD).block();

        CollectionRenameMigration.Result result = migration.migrate(Map.of(OLD, OLD)).block();

        assertThat(result.isClean()).isFalse();
        assertThat(template.collectionExists(OLD).block())
                .as("the collection must still exist — dropping it is what source==target does "
                        + "when nothing checks, and on a populated collection that is every "
                        + "document in it")
                .isTrue();
    }

    @Test
    @DisplayName("a duplicated source in the table is rejected when the table is built")
    void duplicateSourceIsRejected() {
        // Two entries claiming the same source would silently apply whichever came last, and
        // the losing target would stay empty. Better to fail at class-init than at 3am.
        assertThatThrownBy(() -> CollectionRenameMigration.table(
                "users", "identity_users",
                "users", "identity_people"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("users");
    }

    // --------------------------------------------------------------------- helpers

    private static void insert(String collection, String... names) {
        Flux.fromArray(names)
                .concatMap(name -> template.insert(new Document("name", name), collection))
                .then().block();
    }

    private static List<String> names(String collection) {
        return template.find(new Query(), Document.class, collection)
                .map(document -> document.getString("name"))
                .collectList()
                .blockOptional()
                .orElse(List.of());
    }
}
