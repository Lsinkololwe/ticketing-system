package com.pml.catalog.migration;

import com.pml.catalog.persistence.CatalogCollections;
import com.pml.catalog.service.ReferenceMetadataValidator;
import org.bson.Document;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Mono;

/**
 * Removes presentation keys — colours, icons, images — from every reference data row.
 *
 * <p>Reference data says what a value is; how it is drawn belongs to each application's design.
 * The seed no longer carries colours and the service refuses them, but the rows seeded before
 * that keep theirs until this runs.
 */
public final class ReferencePresentationStrip {

    private final ReactiveMongoTemplate mongoTemplate;

    public ReferencePresentationStrip(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** @return how many rows lost a presentation key */
    public Mono<Long> strip() {
        Document unset = new Document();
        ReferenceMetadataValidator.PRESENTATION_KEYS.forEach(key -> unset.append("metadata." + key, ""));
        Document anyPresent = new Document("$or", ReferenceMetadataValidator.PRESENTATION_KEYS.stream()
                .map(key -> new Document("metadata." + key, new Document("$exists", true)))
                .toList());
        return mongoTemplate.getCollection(CatalogCollections.REFERENCE_DATA)
                .flatMap(rows -> Mono.from(rows.updateMany(anyPresent, new Document("$unset", unset))))
                .map(result -> result.getModifiedCount());
    }
}
