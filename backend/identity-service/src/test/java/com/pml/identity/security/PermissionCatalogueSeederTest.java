package com.pml.identity.security;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.persistence.IdentityCollections;
import com.pml.shared.security.Permission;
import com.pml.shared.security.PermissionCatalogueFingerprint;
import com.pml.shared.testing.MongoReplicaSet;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;

import static org.assertj.core.api.Assertions.assertThat;

/** The catalogue projection on a real MongoDB: complete, idempotent, and free of retired codes. */
@Tag("L2")
@Tag("ET-PLT-013")
@DisplayName("ET-PLT-013 · the permission catalogue is seeded in Mongo and kept in step with the code")
class PermissionCatalogueSeederTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate template;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_catalogue_seed"));
        template.dropCollection(IdentityCollections.PERMISSION_CATALOGUE).block();
    }

    @AfterAll
    static void close() {
        client.close();
    }

    @Test
    @DisplayName("every permission has one row, with its roles, and a retired code is removed on the next start")
    void seededAndPruned() {
        template.insert(new Document("_id", "legacy:view"), IdentityCollections.PERMISSION_CATALOGUE).block();
        PermissionCatalogueSeeder seeder = new PermissionCatalogueSeeder(template);

        seeder.seed().block();
        seeder.seed().block();

        var rows = template.findAll(Document.class, IdentityCollections.PERMISSION_CATALOGUE).collectList().block();
        assertThat(rows).hasSize(Permission.values().length + 1);
        assertThat(rows).noneMatch(row -> "legacy:view".equals(row.get("_id")));
        Document refund = rows.stream().filter(r -> "ticket:refund".equals(r.get("_id"))).findFirst().orElseThrow();
        assertThat(refund.getList("platformRoles", String.class)).contains("SUPER_ADMIN", "FINANCE");
        assertThat(refund.getString("scope")).isEqualTo("EVENT");
        Document meta = rows.stream().filter(r -> "__meta".equals(r.get("_id"))).findFirst().orElseThrow();
        assertThat(meta.getString("fingerprint")).isEqualTo(PermissionCatalogueFingerprint.compute());
    }
}
