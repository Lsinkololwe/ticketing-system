package com.pml.catalog.migration;

import com.pml.catalog.persistence.CatalogCollections;
import org.bson.Document;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Removes the organizer's personal and business contact details copied onto events.
 *
 * <p>Identity owns them and the graph reaches them through the federated {@code organization} and
 * {@code organizer} references, so a copy here is a second place an erasure or a correction would
 * have to find. The service no longer writes them; the rows written before keep them until this runs.
 */
public final class EventOrganizerContactStrip {

    static final List<String> FIELDS = List.of("organizerFirstName", "organizerLastName", "organizerCompanyName",
            "organizerEmail", "organizerPhone", "organizerBusinessEmail", "organizerBusinessPhone");

    private final ReactiveMongoTemplate mongoTemplate;

    public EventOrganizerContactStrip(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** @return how many events lost a contact field */
    public Mono<Long> strip() {
        Document unset = new Document();
        FIELDS.forEach(field -> unset.append(field, ""));
        Document anyPresent = new Document("$or", FIELDS.stream()
                .map(field -> new Document(field, new Document("$exists", true)))
                .toList());
        return mongoTemplate.getCollection(CatalogCollections.EVENTS)
                .flatMap(events -> Mono.from(events.updateMany(anyPresent, new Document("$unset", unset))))
                .map(result -> result.getModifiedCount());
    }
}
