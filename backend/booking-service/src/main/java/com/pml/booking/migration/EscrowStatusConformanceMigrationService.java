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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Moves stored escrow statuses onto the current five.
 *
 * <h2>Why the rows cannot simply be left</h2>
 * {@code EscrowStatus} no longer has constants for {@code CREATED},
 * {@code LOCKED}, {@code PROCESSING_PAYOUT} or {@code CANCELLED}. Spring Data
 * deserialises an unknown enum value by throwing, so an untouched row does not
 * degrade — the escrow becomes unreadable, and a payout against it fails with a
 * mapping error rather than anything an operator could act on.
 *
 * <h2>The mapping, and the one lossy step</h2>
 * <ul>
 *   <li>{@code CREATED → ACTIVE} — the account is open either way; a zero
 *       balance already says no money has arrived.</li>
 *   <li>{@code LOCKED → HOLD} — a rename.</li>
 *   <li>{@code PROCESSING_PAYOUT → PAYOUT_ELIGIBLE} — the in-flight fact lives
 *       on the payout request, which still holds it. Nothing is lost; the
 *       duplicate is.</li>
 *   <li>{@code CANCELLED → CLOSED} — <b>lossy.</b> A cancelled event's escrow
 *       and a fully-paid-out one become indistinguishable by status. The journal
 *       still tells them apart (refund entries versus payout entries), so the
 *       fact survives; the shortcut to it does not. Called out here because a
 *       future report that groups by status alone will be wrong about it.</li>
 * </ul>
 *
 * <h2>statusSemantic moves with the code</h2>
 * A row stamped {@code INITIAL} for {@code CREATED} must not keep that semantic
 * once it reads {@code ACTIVE} — {@code ACTIVE} means {@code IN_PROGRESS}. The
 * update rewrites both fields together, because a row whose code and meaning
 * disagree is worse than one carrying neither.
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class EscrowStatusConformanceMigrationService {

    private static final String COLLECTION = BookingCollections.ESCROW_ACCOUNTS;

    /** Retired code → its replacement, and the semantic that replacement carries. */
    private static final Map<String, Replacement> MAPPING = new LinkedHashMap<>(Map.of(
            "CREATED", new Replacement("ACTIVE", "IN_PROGRESS"),
            "LOCKED", new Replacement("HOLD", "IN_PROGRESS"),
            "PROCESSING_PAYOUT", new Replacement("PAYOUT_ELIGIBLE", "PENDING"),
            "CANCELLED", new Replacement("CLOSED", "SUCCEEDED")));

    private record Replacement(String code, String semantic) {}

    private final ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider;

    /** @param migrated documents rewritten, @param byCode how many per retired code */
    public record Result(long migrated, Map<String, Long> byCode) {}

    public Mono<Result> migrate() {
        return Flux.fromIterable(MAPPING.entrySet())
                .concatMap(entry -> rewrite(entry.getKey(), entry.getValue()))
                .reduce(new Result(0, new LinkedHashMap<>()), (acc, one) -> {
                    Map<String, Long> merged = new LinkedHashMap<>(acc.byCode());
                    merged.putAll(one.byCode());
                    return new Result(acc.migrated() + one.migrated(), merged);
                })
                .doOnSuccess(r -> log.info(
                        "Escrow status conformance (ET-FIN-001 R4): {} documents migrated {}",
                        r.migrated(), r.byCode()));
    }

    private Mono<Result> rewrite(String retired, Replacement replacement) {
        Document filter = new Document("status", retired);
        Document update = new Document("$set", new Document("status", replacement.code())
                .append("statusSemantic", replacement.semantic()));

        return mongoTemplateProvider.getObject().getMongoDatabase()
                .flatMap(db -> Mono.from(db.getCollection(COLLECTION).updateMany(filter, update)))
                .map(result -> {
                    long n = result.getModifiedCount();
                    Map<String, Long> counts = new LinkedHashMap<>();
                    if (n > 0) {
                        counts.put(retired + "->" + replacement.code(), n);
                        log.info("  {} -> {}: {} escrow accounts",
                                retired, replacement.code(), n);
                        if ("CANCELLED".equals(retired)) {
                            log.warn("  {} accounts were CANCELLED and are now CLOSED — "
                                    + "cancellation is no longer distinguishable by status alone; "
                                    + "the journal's refund entries are the remaining record", n);
                        }
                    }
                    return new Result(n, counts);
                });
    }
}
