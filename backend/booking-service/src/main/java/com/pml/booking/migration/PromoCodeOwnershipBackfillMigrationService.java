package com.pml.booking.migration;

import com.pml.booking.persistence.BookingCollections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Gives every promo code without an owning organization the organization of the event it discounts.
 *
 * <p>Promo-code reads and changes are limited to the caller's organizations by {@code organizationId},
 * so a code without one could only be managed by a platform administrator. The event's organization
 * is taken from its escrow account, which carries both {@code eventId} and {@code organizationId}.
 * A code whose event has no escrow account is left unchanged and counted in the log. Running the
 * migration again changes nothing, because it only selects codes that still lack an organization.
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class PromoCodeOwnershipBackfillMigrationService {

    private final ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider;

    public Mono<Long> migrate() {
        ReactiveMongoTemplate mongo = mongoTemplateProvider.getObject();
        Document unowned = new Document("$or", List.of(
                new Document("organizationId", new Document("$exists", false)),
                new Document("organizationId", null)))
                .append("eventId", new Document("$type", "string"));

        return mongo.getMongoDatabase().flatMap(db -> Flux.from(db.getCollection(BookingCollections.PROMO_CODES).find(unowned))
                .concatMap(code -> Mono.from(db.getCollection(BookingCollections.ESCROW_ACCOUNTS)
                                .find(new Document("eventId", code.getString("eventId"))
                                        .append("organizationId", new Document("$type", "string")))
                                .first())
                        .flatMap(escrow -> Mono.from(db.getCollection(BookingCollections.PROMO_CODES).updateOne(
                                new Document("_id", code.get("_id")).append("$or", unowned.get("$or")),
                                new Document("$set", new Document("organizationId", escrow.getString("organizationId"))))))
                        .map(result -> result.getModifiedCount())
                        .defaultIfEmpty(0L))
                .reduce(0L, Long::sum))
                .doOnNext(n -> log.info("Promo code ownership backfill: set organizationId on {} code(s)", n))
                .defaultIfEmpty(0L);
    }
}
