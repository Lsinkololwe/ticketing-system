package com.pml.booking.migration;

import com.pml.shared.constants.TicketStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Rewrites existing tickets onto ET-TKT-002 R7's seven states.
 *
 * <h2>The mappings, and what each one costs</h2>
 * <table>
 *   <tr><th>From</th><th>To</th><th>Why</th></tr>
 *   <tr><td>{@code PURCHASED}</td><td>{@code ISSUED}</td><td>rename — nothing branched on the difference</td></tr>
 *   <tr><td>{@code CONFIRMED}</td><td>{@code ISSUED}</td><td>rename — the pair appeared together at every call site</td></tr>
 *   <tr><td>{@code PENDING_VERIFICATION}</td><td>{@code ISSUED}</td><td>the purchase had completed; verification was a payment-side concern</td></tr>
 *   <tr><td>{@code USED}</td><td>{@code VALIDATED}</td><td>merge — see below</td></tr>
 *   <tr><td>{@code CHARGEDBACK}</td><td>{@code REFUNDED}</td><td>ET-FIN-004 R8 states this outcome directly</td></tr>
 *   <tr><td>{@code PENDING_PAYMENT}</td><td>{@code CANCELLED}</td><td>lossy — see below</td></tr>
 *   <tr><td>{@code PAYMENT_FAILED}</td><td>{@code CANCELLED}</td><td>lossy — see below</td></tr>
 * </table>
 *
 * <h2>The merge that looks dangerous and is not</h2>
 * {@code USED → VALIDATED} collapses what looked like a two-phase gate. It was
 * never one: {@code CheckInServiceImpl} admitted from {@code PURCHASED} and
 * {@code CONFIRMED} only, and treated {@code VALIDATED} and {@code USED}
 * identically as "already admitted". Every ticket in either state was already
 * unadmittable, and stays unadmittable. No gate decision changes.
 *
 * <h2>The two mappings that lose information, stated plainly</h2>
 * {@code PENDING_PAYMENT} and {@code PAYMENT_FAILED} describe a ticket that
 * exists without money behind it. ET-TKT-001 R7 made that unreachable — issuance
 * now happens inside the confirmation transaction — so no such row can be
 * created again, but historical ones exist and the seven have nowhere to put
 * them. They become {@code CANCELLED}: terminal, never admitted, never counted
 * as revenue.
 *
 * <p>If any of those rows <em>did</em> have money behind it and merely failed to
 * advance, this migration buries that. Which is why the count of every
 * reclassified row is written into the ledger summary rather than only logged:
 * an operator reading {@code booking_migrations} afterwards can see exactly how
 * many tickets were reclassified and from what, and go and look at the payment
 * intents behind them. A count of zero — which is what a platform that has
 * never issued a pre-payment ticket will see — says so unambiguously.
 *
 * <h2>Unknown statuses are left alone</h2>
 * The mapping is explicit rather than "anything not in the seven becomes
 * CANCELLED". A status this migration does not recognise is reported and left
 * untouched, because guessing at an unknown state is how a ticket somebody paid
 * for silently stops working.
 *
 * @see <a href="file:../../../../../../../specs/ticketing/002-ticket-issuance-and-qr/spec.md">ET-TKT-002 R7</a>
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class TicketStatusConformanceMigrationService {

    private static final String COLLECTION = "tickets";

    /** Old status → one of ET-TKT-002 R7's seven. Insertion-ordered for a readable summary. */
    private static final Map<String, TicketStatus> MAPPING = new LinkedHashMap<>();

    static {
        MAPPING.put("PURCHASED", TicketStatus.ISSUED);
        MAPPING.put("CONFIRMED", TicketStatus.ISSUED);
        MAPPING.put("PENDING_VERIFICATION", TicketStatus.ISSUED);
        MAPPING.put("USED", TicketStatus.VALIDATED);
        MAPPING.put("CHARGEDBACK", TicketStatus.REFUNDED);
        MAPPING.put("PENDING_PAYMENT", TicketStatus.CANCELLED);
        MAPPING.put("PAYMENT_FAILED", TicketStatus.CANCELLED);
    }

    private final ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider;

    /**
     * @param rewritten     total rows moved onto a conforming status
     * @param byOldStatus   how many came from each old status — the audit trail
     * @param moneyless     rows that had no money behind them and became CANCELLED
     * @param unrecognised  statuses this migration does not know, left untouched
     */
    public record Result(long rewritten,
                         Map<String, Long> byOldStatus,
                         long moneyless,
                         Map<String, Long> unrecognised) {

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("rewritten=" + rewritten);
            byOldStatus.forEach((from, n) -> sb.append(' ').append(from)
                    .append("->").append(MAPPING.get(from)).append('=').append(n));
            if (moneyless > 0) {
                sb.append(" MONEYLESS_CANCELLED=").append(moneyless)
                        .append(" (pre-payment tickets with nowhere to go under the seven —"
                                + " check their payment intents before assuming they were junk)");
            }
            if (!unrecognised.isEmpty()) {
                sb.append(" UNRECOGNISED=").append(unrecognised).append(" (left untouched)");
            }
            return sb.toString();
        }
    }

    public Mono<Result> migrate() {
        ReactiveMongoTemplate mongo = mongoTemplateProvider.getObject();

        return mongo.collectionExists(COLLECTION)
                .flatMap(exists -> exists ? rewriteAll(mongo) : Mono.just(empty()))
                .doOnSuccess(r -> {
                    log.info("Ticket status conformance (ET-TKT-002 R7): {}", r);
                    if (r.moneyless() > 0) {
                        // Loud, because it is the one outcome nobody can undo by
                        // re-running the migration.
                        log.warn("{} ticket(s) were in a pre-payment state and are now CANCELLED. "
                                        + "They can no longer be admitted. If any of them had a completed "
                                        + "payment intent, that ticket needs re-issuing by hand.",
                                r.moneyless());
                    }
                    if (!r.unrecognised().isEmpty()) {
                        log.error("Tickets carry status values this migration does not recognise and has "
                                + "left untouched: {}. They will not conform to ET-TKT-002 R7 and reads "
                                + "that deserialise them will fail.", r.unrecognised());
                    }
                });
    }

    private Mono<Result> rewriteAll(ReactiveMongoTemplate mongo) {
        return mongo.getMongoDatabase().flatMap(db ->
                // Count first, then rewrite. The counts are what the ledger row
                // carries, and after the update the evidence is gone.
                Flux.fromIterable(MAPPING.entrySet())
                        .concatMap(entry -> Mono.from(db.getCollection(COLLECTION)
                                        .countDocuments(new Document("status", entry.getKey())))
                                .map(n -> Map.entry(entry.getKey(), n)))
                        .filter(e -> e.getValue() > 0)
                        .collectList()
                        .flatMap(counts -> {
                            Map<String, Long> byOldStatus = new LinkedHashMap<>();
                            counts.forEach(e -> byOldStatus.put(e.getKey(), e.getValue()));
                            long moneyless = byOldStatus.getOrDefault("PENDING_PAYMENT", 0L)
                                    + byOldStatus.getOrDefault("PAYMENT_FAILED", 0L);

                            return Flux.fromIterable(byOldStatus.keySet())
                                    .concatMap(from -> Mono.from(db.getCollection(COLLECTION).updateMany(
                                            new Document("status", from),
                                            new Document("$set", new Document(
                                                    "status", MAPPING.get(from).name())))))
                                    .reduce(0L, (total, r) -> total + r.getModifiedCount())
                                    .flatMap(rewritten -> findUnrecognised(db)
                                            .map(unknown -> new Result(
                                                    rewritten, byOldStatus, moneyless, unknown)));
                        }));
    }

    /** Anything left that is not one of the seven. */
    private Mono<Map<String, Long>> findUnrecognised(com.mongodb.reactivestreams.client.MongoDatabase db) {
        return Flux.from(db.getCollection(COLLECTION).aggregate(java.util.List.of(
                        new Document("$group", new Document("_id", "$status")
                                .append("count", new Document("$sum", 1))))))
                .filter(doc -> {
                    String status = doc.getString("_id");
                    if (status == null) {
                        return false;
                    }
                    return java.util.Arrays.stream(TicketStatus.values())
                            .noneMatch(known -> known.name().equals(status));
                })
                .collect(
                        (java.util.function.Supplier<Map<String, Long>>) TreeMap::new,
                        (map, doc) -> map.put(doc.getString("_id"), ((Number) doc.get("count")).longValue()));
    }

    private Result empty() {
        return new Result(0, Map.of(), 0, Map.of());
    }
}
