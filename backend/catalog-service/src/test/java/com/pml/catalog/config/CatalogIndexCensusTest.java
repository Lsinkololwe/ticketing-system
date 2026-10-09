package com.pml.catalog.config;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.persistence.IndexEnsurer;
import com.pml.shared.testing.IndexCensus;
import com.pml.shared.testing.MongoReplicaSet;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every index this service had before its registry became the only source of indexes still exists
 * when the registry alone builds the database.
 *
 * <p>{@code src/test/resources/index-census/catalog.jsonl} lists, by shape rather than name, each index
 * the model annotations and start-up helpers used to create. A fresh database gets the registry and
 * nothing else; each listed shape must then be present, except the few dropped on purpose below.
 */
@Tag("L2")
@Tag("ET-PLT-002")
@DisplayName("The catalog registry builds every index the service had before, bar the ones dropped on purpose")
class CatalogIndexCensusTest {

    /** Indexes deliberately not carried over, each with the reason. */
    private static final Map<Document, String> DROPPED = Map.of();

    private static MongoClient client;
    private static ReactiveMongoTemplate template;

    @BeforeAll
    static void build() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "catalog_index_census"));
        template.getMongoDatabase().flatMap(database -> Mono.from(database.drop())).block();
        assertThat(new IndexEnsurer(template).ensure(CatalogIndexInitializer.specifications()).block().isClean()).isTrue();
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @Test
    @DisplayName("each index in the census exists, with the same keys and options, under any name")
    void registryCoversTheCensus() throws Exception {
        List<IndexCensus.Shape> before = IndexCensus.read(Path.of("src/test/resources/index-census/catalog.jsonl"));
        List<IndexCensus.Shape> kept = before.stream()
                .filter(shape -> DROPPED.keySet().stream().noneMatch(dropped -> matches(shape, dropped)))
                .toList();
        Map<IndexCensus.Shape, String> now = IndexCensus.live(template).block();

        assertThat(kept).as("the census file is empty or unreadable").isNotEmpty();
        assertThat(IndexCensus.missing(kept, now.keySet())).extracting(IndexCensus.Shape::describe).isEmpty();
    }

    @Test
    @DisplayName("each deliberate drop names an index that really was in the census")
    void dropsAreReal() throws Exception {
        List<IndexCensus.Shape> before = IndexCensus.read(Path.of("src/test/resources/index-census/catalog.jsonl"));
        for (Document dropped : DROPPED.keySet()) {
            assertThat(before).as("dropped %s", dropped.toJson()).anyMatch(shape -> matches(shape, dropped));
        }
    }

    private static Document index(String collection, Document key) {
        return new Document("collection", collection).append("key", key);
    }

    /** A plain, non-unique index on exactly these keys. */
    private static boolean matches(IndexCensus.Shape shape, Document dropped) {
        return shape.collection().equals(dropped.getString("collection"))
                && Document.parse(shape.key()).equals(dropped.get("key", Document.class))
                && !shape.unique() && shape.expireAfterSeconds() == null && shape.partialFilter() == null;
    }
}
