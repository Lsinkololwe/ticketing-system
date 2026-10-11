package com.pml.catalog.migration;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.persistence.CatalogCollections;
import com.pml.catalog.testing.CatalogWiring;
import com.pml.shared.testing.MongoReplicaSet;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Contact details copied onto events are removed; everything else on the event is left alone. */
@Tag("L2")
@Tag("ET-PLT-007")
@DisplayName("Events no longer hold the organizer's contact details")
class EventOrganizerContactStripTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate template;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = CatalogWiring.platformTemplate(client, "catalog_contact_strip");
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @Test
    @DisplayName("contact fields are unset, the name and the owning ids stay, and a second run changes nothing")
    void contactFieldsAreUnset() {
        var events = template.getCollection(CatalogCollections.EVENTS).block();
        reactor.core.publisher.Mono.from(events.insertOne(new Document("_id", "strip-1")
                .append("organizerId", "user-1").append("organizerName", "Kabwe Collective")
                .append("organizerEmail", "private@organizer.example").append("organizerPhone", "+260970000000")
                .append("organizerBusinessEmail", "b@organizer.example"))).block();
        reactor.core.publisher.Mono.from(events.insertOne(new Document("_id", "strip-2")
                .append("organizerId", "user-2").append("organizerName", "Clean"))).block();

        var strip = new EventOrganizerContactStrip(template);
        assertThat(strip.strip().block()).isEqualTo(1L);

        Document cleaned = reactor.core.publisher.Mono.from(events.find(new Document("_id", "strip-1")).first()).block();
        assertThat(cleaned).doesNotContainKeys("organizerEmail", "organizerPhone", "organizerBusinessEmail");
        assertThat(cleaned.getString("organizerName")).isEqualTo("Kabwe Collective");
        assertThat(cleaned.getString("organizerId")).isEqualTo("user-1");
        assertThat(strip.strip().block()).as("nothing left to clear").isEqualTo(0L);
    }
}
