package com.pml.booking.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Component;

/**
 * The two indexes ET-TKT-001 requires on {@code booking_reservations}.
 *
 * <h2>Why these are not in {@link MongoIndexConfig}</h2>
 * That class exists to make aggregations fast, and it treats a failed index
 * creation as a warning — reasonably, because a missing performance index makes
 * a query slow, not wrong. One of the indexes here is different in kind: the
 * partial unique index is the <em>enforcement</em> of R5, not an optimisation of
 * it. If it silently fails to build, nothing is slow and everything looks
 * healthy, while a buyer with a flaky connection quietly accumulates four holds
 * on a tier and locks out four other buyers.
 *
 * <p>So this one fails startup. A booking service that cannot enforce one hold
 * per buyer per tier should not take traffic on an on-sale.
 *
 * @see <a href="file:../../../../../../specs/ticketing/001-reservation-and-hold/spec.md">ET-TKT-001 R4, R5</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationIndexInitializer {

    public static final String COLLECTION = "booking_reservations";
    public static final String ONE_HOLD_PER_TIER = "uniq_reservation_user_tier_held";
    public static final String SWEEP_CLAIM = "idx_reservation_status_expires";

    private final ReactiveMongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        ensureAll(mongoTemplate)
                .doOnSuccess(v -> log.info("Reservation indexes ensured ({} and {})",
                        ONE_HOLD_PER_TIER, SWEEP_CLAIM))
                .block();
    }

    /**
     * Exposed so integration tests build exactly the indexes production builds.
     *
     * <p>A test that hand-rolls its own index proves that <em>an</em> index
     * enforces uniqueness, not that the one shipping to production does — and
     * the interesting failures here are in the partial filter, which is easy to
     * get subtly wrong and impossible to notice without a real MongoDB.
     */
    public static Mono<Void> ensureAll(ReactiveMongoTemplate mongoTemplate) {
        return oneHoldPerBuyerPerTier(mongoTemplate)
                .then(sweepClaim(mongoTemplate))
                .then();
    }

    /**
     * R5, at the database rather than in a check.
     *
     * <p>{@code items.ticketTierId} is inside an array, so this is a multikey
     * index: a reservation spanning two tiers contributes one entry per tier,
     * and each is separately unique against the buyer. That is exactly the rule
     * wanted — a buyer may hold tier A and tier B at once, but not tier A twice.
     *
     * <p>The partial filter is what makes it usable at all. Without
     * {@code status = HELD} in the filter, a buyer could never reserve the same
     * tier a second time <em>ever</em> — their released reservation from last
     * month would still occupy the key. Scoping uniqueness to live holds is the
     * difference between a constraint and a permanent ban.
     */
    private static Mono<String> oneHoldPerBuyerPerTier(ReactiveMongoTemplate mongoTemplate) {
        Index index = new Index()
                .on("userId", Sort.Direction.ASC)
                .on("items.ticketTierId", Sort.Direction.ASC)
                .named(ONE_HOLD_PER_TIER)
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("status").is("HELD")));

        return mongoTemplate.indexOps(COLLECTION).ensureIndex(index);
    }

    /** R4's claim query: the sweep asks for {@code status = HELD AND expiresAt < now}. */
    private static Mono<String> sweepClaim(ReactiveMongoTemplate mongoTemplate) {
        Index index = new Index()
                .on("status", Sort.Direction.ASC)
                .on("expiresAt", Sort.Direction.ASC)
                .named(SWEEP_CLAIM)
                .background();

        return mongoTemplate.indexOps(COLLECTION)
                .ensureIndex(index)
                // This one is a performance index and may legitimately already
                // exist under another name. Slow is survivable; wrong is not.
                .onErrorResume(e -> {
                    log.warn("Sweep-claim index not created ({}). The expiry sweep will still be "
                            + "correct, but will scan.", e.getMessage());
                    return Mono.just(SWEEP_CLAIM);
                });
    }
}
