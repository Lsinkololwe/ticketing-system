package com.pml.catalog.service.impl;

import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.model.TicketTier;
import com.pml.catalog.domain.model.TicketTier.InventoryMovement;
import com.pml.catalog.repository.TicketTierRepository;
import com.pml.catalog.service.InventoryService;
import com.pml.catalog.web.rest.dto.InventoryOperationResult;
import com.pml.catalog.web.rest.dto.InventoryReservationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Tier inventory movements, each applied at most once per reservation.
 *
 * <h2>One conditional atomic update per movement</h2>
 * Every hold, commit and release is a single {@code findAndModify} on the tier document whose filter
 * is the whole precondition — enough seats free, the reservation's entry in the expected state — and
 * whose update moves the counters and the entry together. An empty result means the precondition was
 * false. There is no read-modify-write to race and no multi-document transaction to abort under
 * on-sale contention.
 *
 * <h2>The entry makes each call idempotent</h2>
 * Callers retry by design — a redelivered provider answer, a workflow activity after
 * a worker restart. Each reservation owns one entry in {@link TicketTier#getMovements()}:
 *
 * <pre>
 *   (none) --hold--> HELD --commit--> COMMITTED
 *    ^                |                  |
 *    +----release-----+------release-----+
 * </pre>
 *
 * <ul>
 *   <li>A hold filters on the reservation having no entry, so a replayed hold matches nothing and is
 *       answered from the entry already there.</li>
 *   <li>A commit filters on the entry being {@code HELD}; a replay finds it {@code COMMITTED} and
 *       changes nothing.</li>
 *   <li>A release with no entry changes nothing — it can never return seats another buyer holds.</li>
 *   <li>A release of a {@code COMMITTED} entry reverses the sale, so the purchase path can compensate a
 *       commit it could not finish.</li>
 * </ul>
 *
 * <p>When a movement's update matches nothing because another call for the same reservation settled
 * first, the tier is read again and the answer comes from the settled entry.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    static final String HELD = "HELD";
    static final String COMMITTED = "COMMITTED";

    /** Re-reads after losing a race to another movement of the same reservation. */
    private static final int MAX_ATTEMPTS = 3;

    private final ReactiveMongoTemplate mongoTemplate;
    private final TicketTierRepository ticketTierRepository;

    @Override
    public Mono<InventoryReservationResult> reserveInventory(String tierId, int quantity, String reservationId) {
        log.info("Reserving {} tickets for tier {} (reservation: {})", quantity, tierId, reservationId);
        if (reservationId == null || reservationId.isBlank()) {
            return Mono.just(InventoryReservationResult.failure(tierId,
                    "A reservation id is required to hold inventory"));
        }

        Query query = Query.query(
                Criteria.where("id").is(tierId)
                        .and("isActive").is(true)
                        .and("movements.reservationId").ne(reservationId)
                        .andOperator(Criteria.where("$expr").is(
                                new Document("$gte", Arrays.asList(
                                        new Document("$subtract", Arrays.asList("$availableQuantity", "$reservedQuantity")),
                                        quantity)))));

        Update update = new Update()
                .inc("reservedQuantity", quantity)
                .push("movements", new InventoryMovement(reservationId, quantity, HELD))
                .currentDate("updatedAt");

        return mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), TicketTier.class)
                .map(tier -> {
                    log.info("Held {} of tier {} for reservation {}: {} remain",
                            quantity, tierId, reservationId, tier.getTrueAvailableQuantity());
                    return InventoryReservationResult.success(tierId);
                })
                .switchIfEmpty(Mono.defer(() -> explainRefusedHold(tierId, quantity, reservationId)));
    }

    /** The hold matched nothing: the reservation already holds, or the tier cannot supply the seats. */
    private Mono<InventoryReservationResult> explainRefusedHold(String tierId, int quantity, String reservationId) {
        return tier(tierId)
                .map(tier -> {
                    Optional<InventoryMovement> existing = movementOf(tier, reservationId);
                    if (existing.isPresent()) {
                        int held = existing.get().getQuantity();
                        return held == quantity
                                ? InventoryReservationResult.success(tierId)
                                : InventoryReservationResult.failure(tierId,
                                        "This reservation already holds %d on the tier, not %d".formatted(held, quantity));
                    }
                    log.warn("Hold refused for tier {}: insufficient inventory or tier inactive. Requested: {}",
                            tierId, quantity);
                    return InventoryReservationResult.failure(tierId, !tier.isActive()
                            ? "Tier is not active"
                            : String.format("Insufficient inventory. Requested: %d, Available: %d",
                                    quantity, tier.getTrueAvailableQuantity()));
                })
                .defaultIfEmpty(InventoryReservationResult.failure(tierId, "Tier not found"));
    }

    @Override
    public Mono<InventoryOperationResult> releaseReservedInventory(String tierId, int quantity, String reservationId) {
        log.info("Releasing tier {} for reservation {}", tierId, reservationId);
        if (reservationId == null || reservationId.isBlank()) {
            return Mono.just(InventoryOperationResult.failure("RELEASE", tierId,
                    "A reservation id is required to release inventory"));
        }
        return release(tierId, reservationId, 1);
    }

    private Mono<InventoryOperationResult> release(String tierId, String reservationId, int attempt) {
        return tier(tierId)
                .flatMap(tier -> {
                    Optional<InventoryMovement> entry = movementOf(tier, reservationId);
                    if (entry.isEmpty()) {
                        // Nothing held for this reservation, so there is nothing to return.
                        return Mono.just(unchanged("RELEASE", tier));
                    }
                    InventoryMovement movement = entry.get();
                    int held = movement.getQuantity();
                    boolean wasCommitted = COMMITTED.equals(movement.getState());

                    Criteria precondition = Criteria.where("id").is(tierId)
                            .and("movements").elemMatch(Criteria.where("reservationId").is(reservationId)
                                    .and("state").is(movement.getState()));
                    Update update = wasCommitted
                            ? new Update().inc("soldQuantity", -held).inc("availableQuantity", held)
                            : new Update().inc("reservedQuantity", -held);
                    update.pull("movements", new Document("reservationId", reservationId)).currentDate("updatedAt");
                    Query query = Query.query(wasCommitted
                            ? precondition.and("soldQuantity").gte(held)
                            : precondition.and("reservedQuantity").gte(held));

                    BigDecimal gross = movement.getGrossAmount();
                    BigDecimal commission = movement.getCommissionAmount();
                    return mongoTemplate.findAndModify(query, update,
                                    FindAndModifyOptions.options().returnNew(true), TicketTier.class)
                            .flatMap(after -> (wasCommitted
                                    ? bumpEvent(after.getEventId(), -held, gross, commission)
                                    : Mono.<Void>empty())
                                    .thenReturn(success("RELEASE", tierId, held, after)))
                            .switchIfEmpty(Mono.defer(() -> attempt < MAX_ATTEMPTS
                                    ? release(tierId, reservationId, attempt + 1)
                                    : Mono.just(InventoryOperationResult.failure("RELEASE", tierId,
                                            "The tier's counters do not allow this release"))));
                })
                .defaultIfEmpty(InventoryOperationResult.failure("RELEASE", tierId, "Tier not found"));
    }

    @Override
    public Mono<InventoryOperationResult> commitReservedToSold(String tierId, int quantity, String reservationId) {
        return commitReservedToSold(tierId, quantity, reservationId, null, null);
    }

    @Override
    public Mono<InventoryOperationResult> commitReservedToSold(String tierId, int quantity, String reservationId,
                                                               BigDecimal grossAmount, BigDecimal commissionAmount) {
        log.info("Committing tier {} for reservation {}", tierId, reservationId);
        if (reservationId == null || reservationId.isBlank()) {
            return Mono.just(InventoryOperationResult.failure("COMMIT", tierId,
                    "A reservation id is required to commit inventory"));
        }
        return commit(tierId, reservationId, grossAmount, commissionAmount, 1);
    }

    private Mono<InventoryOperationResult> commit(String tierId, String reservationId, BigDecimal gross,
                                                  BigDecimal commission, int attempt) {
        return tier(tierId)
                .flatMap(tier -> {
                    Optional<InventoryMovement> entry = movementOf(tier, reservationId);
                    if (entry.isEmpty()) {
                        return Mono.just(InventoryOperationResult.failure("COMMIT", tierId,
                                "No hold exists for this reservation on the tier"));
                    }
                    if (COMMITTED.equals(entry.get().getState())) {
                        return Mono.just(unchanged("COMMIT", tier));
                    }
                    int held = entry.get().getQuantity();

                    Query query = Query.query(Criteria.where("id").is(tierId)
                            .and("movements").elemMatch(Criteria.where("reservationId").is(reservationId)
                                    .and("state").is(HELD))
                            .and("reservedQuantity").gte(held));
                    Update update = new Update()
                            .inc("reservedQuantity", -held)
                            .inc("availableQuantity", -held)
                            .inc("soldQuantity", held)
                            .set("movements.$.state", COMMITTED)
                            .set("movements.$.grossAmount", gross)
                            .set("movements.$.commissionAmount", commission)
                            .currentDate("updatedAt");

                    return mongoTemplate.findAndModify(query, update,
                                    FindAndModifyOptions.options().returnNew(true), TicketTier.class)
                            // Only the call that moved the tier moves the event, so a replay adds nothing.
                            .flatMap(after -> bumpEvent(after.getEventId(), held, gross, commission)
                                    .thenReturn(success("COMMIT", tierId, held, after)))
                            .switchIfEmpty(Mono.defer(() -> attempt < MAX_ATTEMPTS
                                    ? commit(tierId, reservationId, gross, commission, attempt + 1)
                                    : Mono.just(InventoryOperationResult.failure("COMMIT", tierId,
                                            "The tier's counters do not allow this commit"))));
                })
                .defaultIfEmpty(InventoryOperationResult.failure("COMMIT", tierId, "Tier not found"));
    }

    @Override
    public Mono<InventoryOperationResult> restoreSoldInventory(String tierId, int quantity, String reason) {
        return restoreSoldInventory(tierId, quantity, reason, null, null);
    }

    @Override
    public Mono<InventoryOperationResult> restoreSoldInventory(String tierId, int quantity, String reason,
                                                               BigDecimal grossAmount, BigDecimal commissionAmount) {
        log.info("Restoring {} sold tickets for tier {} (reason: {})", quantity, tierId, reason);

        Query query = Query.query(
                Criteria.where("id").is(tierId)
                        .and("soldQuantity").gte(quantity)
        );

        Update update = new Update()
                .inc("soldQuantity", -quantity)
                .inc("availableQuantity", quantity)
                .currentDate("updatedAt");

        return mongoTemplate.findAndModify(
                        query,
                        update,
                        FindAndModifyOptions.options().returnNew(true),
                        TicketTier.class
                )
                .flatMap(tier -> bumpEvent(tier.getEventId(), -quantity, grossAmount, commissionAmount)
                        .thenReturn(success("RESTORE", tierId, quantity, tier)))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Restore failed for tier {}: insufficient sold quantity", tierId);
                    return Mono.just(InventoryOperationResult.failure(
                            "RESTORE",
                            tierId,
                            "Insufficient sold quantity or tier not found"
                    ));
                }));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Moves the event's sales totals with the tier: {@code soldDelta} tickets sold (negative for a
     * reversal) and the money that went with them. One atomic update, and the version moves with it so
     * an organizer's edit loaded before this sale cannot save the old totals back over it.
     */
    private Mono<Void> bumpEvent(String eventId, int soldDelta, BigDecimal gross, BigDecimal commission) {
        if (eventId == null) {
            return Mono.empty();
        }
        Update update = new Update()
                .inc("soldTickets", soldDelta)
                .inc("availableTickets", -soldDelta)
                .inc("version", 1);
        if (gross != null && gross.signum() != 0) {
            update.inc("grossSales", soldDelta > 0 ? gross : gross.negate());
        }
        if (commission != null && commission.signum() != 0) {
            update.inc("commissionAmount", soldDelta > 0 ? commission : commission.negate());
        }
        return mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(eventId)), update, Event.class).then();
    }

    /**
     * The tier by id. The internal inventory surface is called service-to-service with a tier id taken
     * from booking's own reservation, so no caller's tenant is in play here.
     */
    private Mono<TicketTier> tier(String tierId) {
        return ticketTierRepository.findById(tierId);
    }

    private static Optional<InventoryMovement> movementOf(TicketTier tier, String reservationId) {
        List<InventoryMovement> movements = tier.getMovements();
        return movements == null
                ? Optional.empty()
                : movements.stream().filter(m -> reservationId.equals(m.getReservationId())).findFirst();
    }

    private static InventoryOperationResult unchanged(String operation, TicketTier tier) {
        return InventoryOperationResult.success(operation, tier.getId());
    }

    private static InventoryOperationResult success(String operation, String tierId, int quantity, TicketTier tier) {
        log.info("{} {} on tier {}: available={}, reserved={}, sold={}", operation, quantity, tierId,
                tier.getAvailableQuantity(), tier.getReservedQuantity(), tier.getSoldQuantity());
        return InventoryOperationResult.success(operation, tierId);
    }
}
