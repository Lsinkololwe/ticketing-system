package com.pml.identity.migration;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.persistence.IdentityCollections;
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

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dev database that refused {@code idx_username} ({@code E11000 ... username: "admin"}): two accounts
 * sharing a username, a blank one, an absent one and a null one. After the step every username is
 * unique, the oldest keeps a shared name, and the registry index exists and is the partial unique one.
 */
@Tag("L2")
@Tag("ET-IDN-004")
@DisplayName("identity_users usernames are made unique, deterministically, and idx_username is built")
class UserUsernameNormalizationTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private final UserUsernameNormalizationMigrationService step = new UserUsernameNormalizationMigrationService(template());

    private static ReactiveMongoTemplate template() {
        return template;
    }

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_username_norm"));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();
        insert("39c7ac7a-bf6f-4b55-a21f-2d6802adbb86", "admin", "2026-01-01T00:00:00Z", "kc-1");
        insert("a4aacdd8-e6f3-4429-a577-2ca9f9868b0a", "admin", "2026-02-01T00:00:00Z", "a4aacdd8-e6f3-4429-a577-2ca9f9868b0a");
        insert("0000-third", "admin", "2026-03-01T00:00:00Z", null);
        insert("blank-1", "", "2026-01-05T00:00:00Z", "kc-blank");
        template.insert(new Document("_id", "absent-1").append("createdAt", Date.from(Instant.parse("2026-01-06T00:00:00Z"))),
                IdentityCollections.USERS).block();
        template.insert(new Document("_id", "null-1").append("username", null).append("keycloakUserId", "kc-null")
                .append("createdAt", Date.from(Instant.parse("2026-01-07T00:00:00Z"))), IdentityCollections.USERS).block();
    }

    private static void insert(String id, String username, String created, String keycloakUserId) {
        Document doc = new Document("_id", id).append("username", username).append("createdAt", Date.from(Instant.parse(created)));
        if (keycloakUserId != null) {
            doc.append("keycloakUserId", keycloakUserId);
        }
        template.insert(doc, IdentityCollections.USERS).block();
    }

    private static List<Document> users() {
        return template.getCollection(IdentityCollections.USERS).flatMapMany(c -> Flux.from(c.find())).collectList().block();
    }

    private static String username(String id) {
        return users().stream().filter(u -> id.equals(u.getString("_id"))).findFirst().orElseThrow().getString("username");
    }

    @Test
    @DisplayName("the oldest account keeps the shared name, the others get a stable id-derived one, blanks are backfilled")
    void normalizes() {
        String result = step.migrate().block();

        assertThat(username("39c7ac7a-bf6f-4b55-a21f-2d6802adbb86")).isEqualTo("admin");
        assertThat(username("a4aacdd8-e6f3-4429-a577-2ca9f9868b0a")).isEqualTo("admin-a4aacdd8");
        assertThat(username("0000-third")).isEqualTo("admin-0000thir");
        assertThat(username("blank-1")).isEqualTo("kc-blank");
        assertThat(username("absent-1")).isEqualTo("absent-1");
        assertThat(username("null-1")).isEqualTo("kc-null");
        assertThat(users().stream().map(u -> u.getString("username")).distinct()).hasSize(users().size());
        assertThat(result).contains("idx_username");
        assertThat(usernameIndex()).isNotNull();
        assertThat(usernameIndex().getBoolean("unique")).isTrue();
        assertThat(usernameIndex().get("partialFilterExpression")).isNotNull();
    }

    @Test
    @DisplayName("a second run changes nothing")
    void idempotent() {
        step.migrate().block();
        List<Document> first = users();
        String again = step.migrate().block();
        assertThat(users()).isEqualTo(first);
        assertThat(again).startsWith("backfilled 0, renamed 0");
    }

    @Test
    @DisplayName("renamed usernames stay within the 50-character limit and never collide with an existing one")
    void boundedAndFresh() {
        String longName = "x".repeat(50);
        insert("aaaaaaaa-1111", longName, "2026-01-01T00:00:00Z", "k1");
        insert("bbbbbbbb-2222", longName, "2026-02-01T00:00:00Z", "k2");
        insert("cccccccc", "admin-a4aacdd8", "2025-01-01T00:00:00Z", "k3");
        step.migrate().block();
        users().forEach(u -> assertThat(u.getString("username").length()).isLessThanOrEqualTo(50));
        assertThat(users().stream().map(u -> u.getString("username")).distinct()).hasSize(users().size());
        assertThat(UserUsernameNormalizationMigrationService.fresh("admin", "a4aacdd8-e6f3", Set.of("admin-a4aacdd8")))
                .isEqualTo("admin-a4aacdd8e6f3");
    }

    private static Document usernameIndex() {
        return template.getCollection(IdentityCollections.USERS)
                .flatMapMany(c -> Flux.from(c.listIndexes())).filter(i -> "idx_username".equals(i.getString("name")))
                .next().block();
    }
}
