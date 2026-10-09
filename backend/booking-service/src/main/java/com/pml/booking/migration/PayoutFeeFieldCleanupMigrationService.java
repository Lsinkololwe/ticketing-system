package com.pml.booking.migration;

import com.pml.booking.persistence.BookingCollections;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Drops the retired {@code platformFee}/{@code processingFee} fields from
 * {@code booking_payout_requests}.
 *
 * <h2>Why the fields are gone</h2>
 * A payout carries no fee of its own — the platform's income is the per-ticket
 * commission, taken before the money ever reaches escrow. {@code PayoutRequest.settledAmount} is
 * therefore always the full requested amount, recomputed at approval, and the
 * {@code PayoutRequest} model no longer declares either field.
 *
 * <h2>Historic rows keep their fee amounts; this only removes the fields</h2>
 * A row approved while payouts carried their own fee may still hold {@code platformFee}/{@code processingFee}
 * alongside a {@code settledAmount} that was net of them at the time. Rewriting
 * {@code settledAmount} on those rows would silently change a figure that has already been paid
 * out and reported on — the historic amount is a fact, not a bug. This migration only removes the
 * two fields the model can no longer read, so the collection matches the {@code additionalProperties:
 * false} validator; it does not restate history.
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class PayoutFeeFieldCleanupMigrationService {

    private final ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider;

    public Mono<Long> migrate() {
        ReactiveMongoTemplate mongo = mongoTemplateProvider.getObject();
        Document filter = new Document("$or",
                java.util.List.of(
                        new Document("platformFee", new Document("$exists", true)),
                        new Document("processingFee", new Document("$exists", true))));
        Document unset = new Document("$unset", new Document("platformFee", "").append("processingFee", ""));

        return mongo.getMongoDatabase()
                .flatMap(db -> Mono.from(db.getCollection(BookingCollections.PAYOUT_REQUESTS)
                        .updateMany(filter, unset)))
                .map(result -> result.getModifiedCount())
                .doOnNext(n -> log.info("Payout fee field cleanup (F-035, ROADMAP D-23): "
                        + "removed platformFee/processingFee from {} document(s)", n))
                .defaultIfEmpty(0L);
    }
}
