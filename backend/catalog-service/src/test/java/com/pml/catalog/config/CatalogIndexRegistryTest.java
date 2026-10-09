package com.pml.catalog.config;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.persistence.CatalogCollections;
import com.pml.shared.persistence.IndexEnsurer;
import com.pml.shared.testing.IndexRegistryAssertions;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Catalog's index registry, on a real server.
 *
 * <h2>Discovery is the query that runs most</h2>
 * {@code catalog_events { status: 1, startsAt: 1 }} backs the public event list: every visitor,
 * every load, before anyone has signed in. It is the hottest index catalog has, and no {@code @Indexed}
 * annotation can express it — a compound index is outside what the annotation produces, so
 * without this declaration the platform's most-run query is a collection scan.
 */
@Tag("L2")
@Tag("ET-PLT-002")
@DisplayName("ET-PLT-002-R3 · catalog's declared indexes exist and are enforced")
class CatalogIndexRegistryTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static IndexRegistryAssertions indexes;

    @BeforeAll
    static void createIndexes() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "catalog_index_registry"));
        indexes = new IndexRegistryAssertions(template);

        // The container is reused between runs, so start from an empty database and measure
        // the indexes these declarations produce.
        template.getMongoDatabase().flatMap(database -> Mono.from(database.drop())).block();

        IndexEnsurer.Report report = new IndexEnsurer(template)
                .ensure(CatalogIndexInitializer.specifications())
                .block();

        assertThat(report).isNotNull();
        assertThat(report.isClean()).as("index creation reported conflicts: %s", report).isTrue();
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @Test
    @DisplayName("every §4 row for catalog exists with its declared keys, in order")
    void registryIsSatisfied() {
        indexes.assertAllPresent(CatalogIndexInitializer.specifications());
    }

    @Test
    @DisplayName("public discovery plans as an index scan")
    void discoveryUsesAnIndex() {
        indexes.assertUsesIndex(CatalogCollections.EVENTS,
                new Document("status", "PUBLISHED").append("startsAt", new Document("$gte", 0)));
    }

    @Test
    @DisplayName("the organizer dashboard plans as an index scan")
    void organizerDashboardUsesAnIndex() {
        indexes.assertUsesIndex(CatalogCollections.EVENTS,
                new Document("organizationId", "org-1").append("status", "PUBLISHED"));
    }

    @Test
    @DisplayName("no index exists that §4 does not declare")
    void noUndeclaredIndexes() {
        assertThat(indexes.undeclared(CatalogIndexInitializer.specifications()))
                .as("an index created outside the registry is drift nobody chose")
                .isEmpty();
    }

    @Test
    @DisplayName("the declarations still match ET-PLT-002 §4, row for row")
    void declarationsMatchTheSpecification() {
        // Compared against the index registry text, not against the server. Asserting that the
        // server carries what the code declared cannot catch the code drifting from the registry —
        // change a declaration and the server follows it, so both move together and stay consistent
        // with each other while agreeing with nothing.
        IndexRegistryAssertions.assertMatchesRegistry(CatalogIndexInitializer.specifications(), "catalog_");
    }
}
