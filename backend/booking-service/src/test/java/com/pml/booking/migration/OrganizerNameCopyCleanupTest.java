package com.pml.booking.migration;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.persistence.BookingCollections;
import com.pml.shared.testing.MongoReplicaSet;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The organizer name copied onto escrow accounts and payouts is removed; nothing else on the record is touched. */
@Tag("L2")
@Tag("ET-PLT-007")
@DisplayName("Escrow accounts and payouts no longer hold a copy of the organizer's name")
class OrganizerNameCopyCleanupTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate template;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "booking_name_cleanup"));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    private static Document only(String collection, String id) {
        return Mono.from(template.getCollection(collection).block().find(new Document("_id", id)).first()).block();
    }

    @Test
    @DisplayName("the field is unset in both collections, the organization id stays, and a second run changes nothing")
    void copiesAreRemoved() {
        var escrow = template.getCollection(BookingCollections.ESCROW_ACCOUNTS).block();
        var payouts = template.getCollection(BookingCollections.PAYOUT_REQUESTS).block();
        Mono.from(escrow.insertOne(new Document("_id", "esc-1").append("organizationId", "org-1")
                .append("organizerName", "Kabwe Collective"))).block();
        Mono.from(payouts.insertOne(new Document("_id", "po-1").append("organizationId", "org-1")
                .append("organizerName", "Kabwe Collective"))).block();
        Mono.from(payouts.insertOne(new Document("_id", "po-2").append("organizationId", "org-2"))).block();

        var migration = new OrganizerNameCopyCleanupMigrationService(
                new StaticListableBeanFactory(Map.of("template", template)).getBeanProvider(ReactiveMongoTemplate.class));

        assertThat(migration.migrate().block()).isEqualTo(2L);
        assertThat(only(BookingCollections.ESCROW_ACCOUNTS, "esc-1")).doesNotContainKey("organizerName")
                .containsEntry("organizationId", "org-1");
        assertThat(only(BookingCollections.PAYOUT_REQUESTS, "po-1")).doesNotContainKey("organizerName")
                .containsEntry("organizationId", "org-1");
        assertThat(migration.migrate().block()).as("nothing left to clear").isEqualTo(0L);
    }
}
