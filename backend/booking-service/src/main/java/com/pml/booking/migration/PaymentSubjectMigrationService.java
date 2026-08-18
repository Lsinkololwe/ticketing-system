package com.pml.booking.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Re-points payments from the ticket they charged for to the reservation.
 *
 * <h2>Why the rename is not enough on its own</h2>
 * Under the old model a payment intent named a ticket that had been created in
 * {@code PENDING_PAYMENT} before the buyer was charged. Under ET-TKT-001 R7 no
 * such ticket exists — tickets are written inside the confirmation transaction —
 * so the field is renamed to {@code reservationId}.
 *
 * <p>But the values are still ticket ids, and no reservation has those ids. That
 * matters and is stated rather than hidden: a historical intent will not resolve
 * to a reservation, so the R8 recovery sweep will not find one for it. The rows
 * are kept for audit and reconciliation, which is what they are actually for
 * once their purchase is long settled.
 *
 * <p>The alternative — walking each old ticket to its {@code reservationId} and
 * rewriting the value — is only correct for tickets that had one. Pre-reservation
 * tickets did not, so it would fabricate links for some rows and leave others
 * dangling, which is worse than a uniformly honest "these are historical".
 *
 * <h2>In-flight payments</h2>
 * An intent still {@code PENDING} or {@code PROCESSING} when this runs is a
 * genuine problem: its buyer may be mid-checkout. Those are counted and logged
 * loudly so a deploy can be timed to avoid them, rather than discovered later as
 * a support ticket.
 *
 * @see <a href="file:../../../../../../../specs/ticketing/001-reservation-and-hold/spec.md">ET-TKT-001 R7</a>
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class PaymentSubjectMigrationService {

    private static final String INTENTS = "payment_intents";
    private static final String ATTEMPTS = "payment_attempts";
    private static final String OLD_FIELD = "ticketId";
    private static final String NEW_FIELD = "reservationId";

    private final ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider;

    public record Result(long intentsRenamed, long attemptsRenamed, long inFlight) {}

    public Mono<Result> migrate() {
        if (OLD_FIELD.equals(NEW_FIELD)) {
            return Mono.error(new IllegalStateException(
                    "Payment subject migration has identical source and target field names."));
        }

        ReactiveMongoTemplate mongo = mongoTemplateProvider.getObject();

        return countInFlight(mongo)
                .flatMap(inFlight -> rename(mongo, INTENTS)
                        .flatMap(intents -> rename(mongo, ATTEMPTS)
                                .map(attempts -> new Result(intents, attempts, inFlight))))
                .doOnSuccess(result -> log.info(
                        "Payment subject (ET-TKT-001 R7): {} intents and {} attempts re-pointed "
                                + "from {} to {}",
                        result.intentsRenamed(), result.attemptsRenamed(), OLD_FIELD, NEW_FIELD));
    }

    /**
     * Counts payments that were mid-flight when the migration ran.
     *
     * <p>Counted <em>before</em> the rename, because afterwards there is no way
     * to tell which rows were affected.
     */
    private Mono<Long> countInFlight(ReactiveMongoTemplate mongo) {
        return mongo.getMongoDatabase()
                .flatMap(db -> Mono.from(db.getCollection(INTENTS).countDocuments(
                        new Document("status", new Document("$in", java.util.List.of("PENDING", "PROCESSING")))
                                .append(OLD_FIELD, new Document("$exists", true)))))
                .doOnNext(count -> {
                    if (count > 0) {
                        log.error("{} payment intents were still PENDING or PROCESSING when the "
                                        + "ticket-to-reservation migration ran. Those buyers may be "
                                        + "mid-checkout and their payments will not resolve to a "
                                        + "reservation. Each needs checking by hand.",
                                count);
                    }
                });
    }

    private Mono<Long> rename(ReactiveMongoTemplate mongo, String collection) {
        return mongo.collectionExists(collection)
                .flatMap(exists -> exists
                        ? mongo.getMongoDatabase().flatMap(db -> Mono.from(
                                        db.getCollection(collection).updateMany(
                                                new Document(OLD_FIELD, new Document("$exists", true)),
                                                new Document("$rename", new Document(OLD_FIELD, NEW_FIELD)))))
                                .map(result -> result.getModifiedCount())
                        : Mono.just(0L))
                .doOnNext(renamed -> {
                    if (renamed > 0) {
                        log.warn("  {}: {} rows renamed. Their {} values are still ticket ids and "
                                        + "will not match any reservation — kept for audit, not for "
                                        + "recovery.",
                                collection, renamed, NEW_FIELD);
                    }
                });
    }
}
