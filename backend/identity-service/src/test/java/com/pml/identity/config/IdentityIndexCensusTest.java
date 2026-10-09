package com.pml.identity.config;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.persistence.IdentityCollections;
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
 * <p>{@code src/test/resources/index-census/identity.jsonl} lists, by shape rather than name, each index
 * the model annotations and start-up helpers used to create. A fresh database gets the registry and
 * nothing else; each listed shape must then be present, except the few dropped on purpose below.
 */
@Tag("L2")
@Tag("ET-PLT-002")
@DisplayName("The identity registry builds every index the service had before, bar the ones dropped on purpose")
class IdentityIndexCensusTest {

    /** An index deliberately not carried over: its shape, and why. */
    private record Dropped(String collection, Document key, boolean unique, boolean partial, String reason) {
    }

    /** Indexes deliberately not carried over, each with the reason. */
    private static final List<Dropped> DROPPED = List.of(
            new Dropped(IdentityCollections.TEAM_INVITATIONS, new Document("expiresAt", 1), false, false,
                    "a TTL index on the same field replaces it, so invitations expire"),
            new Dropped(IdentityCollections.USERS, new Document("email", 1), true, false,
                    "ET-IDN-004: a PLAIN unique email index refuses the second account without an email, which is every "
                            + "phone-only account. The partial unique idx_email stays, and migration "
                            + "users-legacy-indexes-drop removes the live one."));

    private static MongoClient client;
    private static ReactiveMongoTemplate template;

    @BeforeAll
    static void build() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_index_census"));
        template.getMongoDatabase().flatMap(database -> Mono.from(database.drop())).block();
        assertThat(new IndexEnsurer(template).ensure(IdentityIndexInitializer.specifications()).block().isClean()).isTrue();
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @Test
    @DisplayName("each index in the census exists, with the same keys and options, under any name")
    void registryCoversTheCensus() throws Exception {
        List<IndexCensus.Shape> before = IndexCensus.read(Path.of("src/test/resources/index-census/identity.jsonl"));
        List<IndexCensus.Shape> kept = before.stream()
                .filter(shape -> DROPPED.stream().noneMatch(dropped -> matches(shape, dropped)))
                .toList();
        Map<IndexCensus.Shape, String> now = IndexCensus.live(template).block();

        assertThat(kept).as("the census file is empty or unreadable").isNotEmpty();
        assertThat(IndexCensus.missing(kept, now.keySet())).extracting(IndexCensus.Shape::describe).isEmpty();
    }

    @Test
    @DisplayName("each deliberate drop names an index that really was in the census")
    void dropsAreReal() throws Exception {
        List<IndexCensus.Shape> before = IndexCensus.read(Path.of("src/test/resources/index-census/identity.jsonl"));
        for (Dropped dropped : DROPPED) {
            assertThat(before).as("dropped %s %s", dropped.collection(), dropped.key().toJson())
                    .anyMatch(shape -> matches(shape, dropped));
        }
    }

    /** A non-partial, non-TTL index on exactly these keys, with the dropped index's uniqueness. */
    private static boolean matches(IndexCensus.Shape shape, Dropped dropped) {
        return shape.collection().equals(dropped.collection())
                && Document.parse(shape.key()).equals(dropped.key())
                && shape.unique() == dropped.unique()
                && shape.expireAfterSeconds() == null && shape.partialFilter() == null;
    }

    @Test
    @DisplayName("the plain unique email index is gone from the registry, the partial one is not")
    void plainEmailIsNotRecreated() {
        Map<IndexCensus.Shape, String> now = IndexCensus.live(template).block();
        assertThat(now.keySet()).noneMatch(shape -> shape.collection().equals(IdentityCollections.USERS)
                && Document.parse(shape.key()).equals(new Document("email", 1)) && shape.partialFilter() == null);
        assertThat(now.keySet()).anyMatch(shape -> shape.collection().equals(IdentityCollections.USERS)
                && Document.parse(shape.key()).equals(new Document("email", 1)) && shape.unique() && shape.partialFilter() != null);
    }
}
