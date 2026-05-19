package com.pml.catalog.service.impl;

import com.pml.catalog.domain.model.TicketTier;
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

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * Inventory Service Implementation
 *
 * <p>Uses MongoDB atomic operations (findAndModify with $inc) to ensure
 * inventory consistency under concurrent access. No optimistic locking
 * retries needed - the query criteria ensures the operation only succeeds
 * if conditions are met.</p>
 *
 * <h2>Key Pattern: Atomic Reserve</h2>
 * <pre>
 * db.ticket_tiers.findAndModify({
 *   query: {
 *     _id: tierId,
 *     isActive: true,
 *     $expr: { $gte: [{ $subtract: ["$availableQuantity", "$reservedQuantity"] }, requestedQty] }
 *   },
 *   update: {
 *     $inc: { reservedQuantity: requestedQty }
 *   },
 *   new: true
 * })
 * </pre>
 *
 * <p>If document doesn't match criteria (insufficient inventory), null is returned
 * and we know the reservation failed atomically.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final ReactiveMongoTemplate mongoTemplate;
    private final TicketTierRepository ticketTierRepository;

    @Override
    public Mono<InventoryReservationResult> reserveInventory(String tierId, int quantity, String reservationId) {
        log.info("Reserving {} tickets for tier {} (reservation: {})", quantity, tierId, reservationId);

        // Atomic operation: Only update if availableQuantity - reservedQuantity >= quantity
        // This prevents overselling even under concurrent access
        Query query = Query.query(
                Criteria.where("id").is(tierId)
                        .and("isActive").is(true)
                        .andOperator(
                                // MongoDB $expr allows comparing computed values
                                Criteria.where("$expr").is(
                                        new Document("$gte", Arrays.asList(
                                                new Document("$subtract", Arrays.asList("$availableQuantity", "$reservedQuantity")),
                                                quantity
                                        ))
                                )
                        )
        );

        Update update = new Update()
                .inc("reservedQuantity", quantity)
                .currentDate("updatedAt");

        return mongoTemplate.findAndModify(
                        query,
                        update,
                        FindAndModifyOptions.options().returnNew(true),
                        TicketTier.class
                )
                .map(tier -> {
                    int trueAvailable = tier.getTrueAvailableQuantity();
                    log.info("Reservation successful for tier {}: reserved={}, remaining={}, reservationId={}",
                            tierId, quantity, trueAvailable, reservationId);
                    return InventoryReservationResult.success(tierId, quantity, trueAvailable);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Reservation failed for tier {}: insufficient inventory or tier inactive. Requested: {}",
                            tierId, quantity);
                    return ticketTierRepository.findById(tierId)
                            .map(tier -> {
                                String reason = !tier.isActive()
                                        ? "Tier is not active"
                                        : String.format("Insufficient inventory. Requested: %d, Available: %d",
                                        quantity, tier.getTrueAvailableQuantity());
                                return InventoryReservationResult.failure(tierId, reason);
                            })
                            .defaultIfEmpty(InventoryReservationResult.failure(tierId, "Tier not found"));
                }));
    }

    @Override
    public Mono<InventoryOperationResult> releaseReservedInventory(String tierId, int quantity, String reservationId) {
        log.info("Releasing {} reserved tickets for tier {} (reservation: {})", quantity, tierId, reservationId);

        // Only release if there's enough reserved
        Query query = Query.query(
                Criteria.where("id").is(tierId)
                        .and("reservedQuantity").gte(quantity)
        );

        Update update = new Update()
                .inc("reservedQuantity", -quantity)
                .currentDate("updatedAt");

        return mongoTemplate.findAndModify(
                        query,
                        update,
                        FindAndModifyOptions.options().returnNew(true),
                        TicketTier.class
                )
                .map(tier -> {
                    log.info("Released {} tickets for tier {}: available={}, reserved={}, sold={}",
                            quantity, tierId, tier.getAvailableQuantity(), tier.getReservedQuantity(), tier.getSoldQuantity());
                    return InventoryOperationResult.success(
                            "RELEASE",
                            tierId,
                            quantity,
                            tier.getAvailableQuantity(),
                            tier.getReservedQuantity(),
                            tier.getSoldQuantity()
                    );
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Release failed for tier {}: insufficient reserved quantity", tierId);
                    return Mono.just(InventoryOperationResult.failure(
                            "RELEASE",
                            tierId,
                            "Insufficient reserved quantity or tier not found"
                    ));
                }));
    }

    @Override
    public Mono<InventoryOperationResult> commitReservedToSold(String tierId, int quantity, String reservationId) {
        log.info("Committing {} reserved tickets to sold for tier {} (reservation: {})", quantity, tierId, reservationId);

        // Commit: decrement reserved, decrement available, increment sold
        // Only succeeds if reserved >= quantity
        Query query = Query.query(
                Criteria.where("id").is(tierId)
                        .and("reservedQuantity").gte(quantity)
        );

        Update update = new Update()
                .inc("reservedQuantity", -quantity)
                .inc("availableQuantity", -quantity)
                .inc("soldQuantity", quantity)
                .currentDate("updatedAt");

        return mongoTemplate.findAndModify(
                        query,
                        update,
                        FindAndModifyOptions.options().returnNew(true),
                        TicketTier.class
                )
                .map(tier -> {
                    log.info("Committed {} tickets to sold for tier {}: available={}, reserved={}, sold={}",
                            quantity, tierId, tier.getAvailableQuantity(), tier.getReservedQuantity(), tier.getSoldQuantity());
                    return InventoryOperationResult.success(
                            "COMMIT",
                            tierId,
                            quantity,
                            tier.getAvailableQuantity(),
                            tier.getReservedQuantity(),
                            tier.getSoldQuantity()
                    );
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Commit failed for tier {}: insufficient reserved quantity", tierId);
                    return Mono.just(InventoryOperationResult.failure(
                            "COMMIT",
                            tierId,
                            "Insufficient reserved quantity or tier not found"
                    ));
                }));
    }

    @Override
    public Mono<InventoryOperationResult> restoreSoldInventory(String tierId, int quantity, String reason) {
        log.info("Restoring {} sold tickets for tier {} (reason: {})", quantity, tierId, reason);

        // Restore: decrement sold, increment available
        // Only succeeds if sold >= quantity
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
                .map(tier -> {
                    log.info("Restored {} tickets for tier {} ({}): available={}, reserved={}, sold={}",
                            quantity, tierId, reason, tier.getAvailableQuantity(), tier.getReservedQuantity(), tier.getSoldQuantity());
                    return InventoryOperationResult.success(
                            "RESTORE",
                            tierId,
                            quantity,
                            tier.getAvailableQuantity(),
                            tier.getReservedQuantity(),
                            tier.getSoldQuantity()
                    );
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Restore failed for tier {}: insufficient sold quantity", tierId);
                    return Mono.just(InventoryOperationResult.failure(
                            "RESTORE",
                            tierId,
                            "Insufficient sold quantity or tier not found"
                    ));
                }));
    }

    @Override
    public Mono<InventorySnapshot> getInventorySnapshot(String tierId) {
        return ticketTierRepository.findById(tierId)
                .map(tier -> new InventorySnapshot(
                        tier.getId(),
                        tier.getQuantity(),
                        tier.getAvailableQuantity(),
                        tier.getReservedQuantity(),
                        tier.getSoldQuantity(),
                        tier.getTrueAvailableQuantity()
                ))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Tier not found: " + tierId)));
    }
}
