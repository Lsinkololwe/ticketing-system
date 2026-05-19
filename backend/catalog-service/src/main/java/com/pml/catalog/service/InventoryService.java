package com.pml.catalog.service;

import com.pml.catalog.web.rest.dto.InventoryOperationResult;
import com.pml.catalog.web.rest.dto.InventoryReservationResult;
import reactor.core.publisher.Mono;

/**
 * Inventory Service
 *
 * <p>Manages ticket tier inventory with atomic operations to prevent overselling.
 * All operations use MongoDB's atomic primitives (findAndModify with $inc) to
 * ensure consistency under concurrent access.</p>
 *
 * <h2>Inventory Lifecycle</h2>
 * <pre>
 * AVAILABLE → RESERVED (reserveInventory) → SOLD (commitReservedToSold)
 *                 ↓ (releaseReservedInventory)
 *               RESTORED → AVAILABLE
 *
 * SOLD → AVAILABLE (restoreSoldInventory - on refund/chargeback)
 * </pre>
 *
 * <h2>Atomic Guarantees</h2>
 * <ul>
 *   <li>Reserve: Only succeeds if sufficient available - reserved &gt;= requested</li>
 *   <li>Release: Only succeeds if reserved &gt;= quantity to release</li>
 *   <li>Commit: Only succeeds if reserved &gt;= quantity to commit</li>
 *   <li>Restore: Always succeeds if tier exists (adds back to available)</li>
 * </ul>
 *
 * @since 1.0.0
 */
public interface InventoryService {

    /**
     * Reserve inventory for a pending purchase.
     *
     * <p>Atomically decrements available quantity and increments reserved quantity.
     * Uses MongoDB findAndModify with criteria check to prevent overselling.</p>
     *
     * @param tierId Ticket tier ID
     * @param quantity Number of tickets to reserve
     * @param reservationId Unique reservation identifier for idempotency and audit
     * @return Reservation result with success/failure status
     */
    Mono<InventoryReservationResult> reserveInventory(String tierId, int quantity, String reservationId);

    /**
     * Release reserved inventory back to available pool.
     *
     * <p>Called when a reservation expires or is cancelled before payment.
     * Atomically decrements reserved and increments available.</p>
     *
     * @param tierId Ticket tier ID
     * @param quantity Number of tickets to release
     * @param reservationId Original reservation identifier for audit
     * @return Operation result
     */
    Mono<InventoryOperationResult> releaseReservedInventory(String tierId, int quantity, String reservationId);

    /**
     * Commit reserved inventory to sold state.
     *
     * <p>Called when payment succeeds. Atomically decrements reserved,
     * decrements available, and increments sold.</p>
     *
     * @param tierId Ticket tier ID
     * @param quantity Number of tickets to commit
     * @param reservationId Original reservation identifier
     * @return Operation result
     */
    Mono<InventoryOperationResult> commitReservedToSold(String tierId, int quantity, String reservationId);

    /**
     * Restore sold inventory back to available pool.
     *
     * <p>Called on refunds or chargebacks. Atomically decrements sold
     * and increments available.</p>
     *
     * @param tierId Ticket tier ID
     * @param quantity Number of tickets to restore
     * @param reason Reason for restoration (REFUND, CHARGEBACK)
     * @return Operation result
     */
    Mono<InventoryOperationResult> restoreSoldInventory(String tierId, int quantity, String reason);

    /**
     * Get current inventory snapshot for a tier.
     *
     * @param tierId Ticket tier ID
     * @return Inventory snapshot with available, reserved, sold quantities
     */
    Mono<InventorySnapshot> getInventorySnapshot(String tierId);

    /**
     * Inventory snapshot DTO.
     */
    record InventorySnapshot(
            String tierId,
            int totalQuantity,
            int availableQuantity,
            int reservedQuantity,
            int soldQuantity,
            int trueAvailable  // available - reserved
    ) {}
}
