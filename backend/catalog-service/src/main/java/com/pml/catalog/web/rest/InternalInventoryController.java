package com.pml.catalog.web.rest;

import com.pml.catalog.service.InventoryService;
import com.pml.catalog.web.rest.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Internal Inventory Controller
 *
 * <p>Exposes internal REST endpoints for inventory management operations.
 * These endpoints are called by booking-service to manage ticket inventory
 * during the reservation and purchase flow.</p>
 *
 * <h2>Security</h2>
 * <p>All endpoints require internal service scope or INTERNAL_SERVICE role.
 * Not accessible to regular users or external clients.</p>
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>POST /api/internal/inventory/tiers/{tierId}/reserve - Reserve inventory</li>
 *   <li>POST /api/internal/inventory/tiers/{tierId}/release - Release reserved</li>
 *   <li>POST /api/internal/inventory/tiers/{tierId}/commit - Commit to sold</li>
 *   <li>POST /api/internal/inventory/tiers/{tierId}/restore - Restore sold</li>
 *   <li>GET /api/internal/inventory/tiers/{tierId}/snapshot - Get current state</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/inventory/tiers")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('SCOPE_internal-read', 'SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")
public class InternalInventoryController {

    private final InventoryService inventoryService;

    /**
     * Reserve inventory for a pending purchase.
     *
     * <p>Called when a user starts the checkout process. Holds inventory
     * until payment completes or reservation expires.</p>
     *
     * @param tierId Ticket tier ID
     * @param request Reservation details
     * @return Reservation result with success/failure status
     */
    @PostMapping("/{tierId}/reserve")
    public Mono<ResponseEntity<InventoryReservationResult>> reserveInventory(
            @PathVariable String tierId,
            @Valid @RequestBody InventoryReservationRequest request) {

        log.info("Reserve inventory request: tier={}, qty={}, reservation={}",
                tierId, request.quantity(), request.reservationId());

        return inventoryService.reserveInventory(tierId, request.quantity(), request.reservationId())
                .map(result -> {
                    if (result.isSuccess()) {
                        return ResponseEntity.ok(result);
                    } else {
                        // Return 409 Conflict for insufficient inventory
                        return ResponseEntity.status(409).body(result);
                    }
                });
    }

    /**
     * Release reserved inventory back to available pool.
     *
     * <p>Called when a reservation expires or is cancelled before payment.</p>
     *
     * @param tierId Ticket tier ID
     * @param request Release details
     * @return Operation result
     */
    @PostMapping("/{tierId}/release")
    public Mono<ResponseEntity<InventoryOperationResult>> releaseInventory(
            @PathVariable String tierId,
            @Valid @RequestBody InventoryReleaseRequest request) {

        log.info("Release inventory request: tier={}, qty={}, reservation={}",
                tierId, request.quantity(), request.reservationId());

        return inventoryService.releaseReservedInventory(tierId, request.quantity(), request.reservationId())
                .map(result -> {
                    if (result.isSuccess()) {
                        return ResponseEntity.ok(result);
                    } else {
                        return ResponseEntity.badRequest().body(result);
                    }
                });
    }

    /**
     * Commit reserved inventory to sold state.
     *
     * <p>Called when payment succeeds. Moves inventory from reserved to sold.</p>
     *
     * @param tierId Ticket tier ID
     * @param request Commit details
     * @return Operation result
     */
    @PostMapping("/{tierId}/commit")
    public Mono<ResponseEntity<InventoryOperationResult>> commitInventory(
            @PathVariable String tierId,
            @Valid @RequestBody InventoryCommitRequest request) {

        log.info("Commit inventory request: tier={}, qty={}, reservation={}",
                tierId, request.quantity(), request.reservationId());

        return inventoryService.commitReservedToSold(tierId, request.quantity(), request.reservationId())
                .map(result -> {
                    if (result.isSuccess()) {
                        return ResponseEntity.ok(result);
                    } else {
                        return ResponseEntity.badRequest().body(result);
                    }
                });
    }

    /**
     * Restore sold inventory back to available pool.
     *
     * <p>Called on refunds or chargebacks to make tickets available again.</p>
     *
     * @param tierId Ticket tier ID
     * @param request Restore details
     * @return Operation result
     */
    @PostMapping("/{tierId}/restore")
    public Mono<ResponseEntity<InventoryOperationResult>> restoreInventory(
            @PathVariable String tierId,
            @Valid @RequestBody InventoryRestoreRequest request) {

        log.info("Restore inventory request: tier={}, qty={}, reason={}",
                tierId, request.quantity(), request.reason());

        return inventoryService.restoreSoldInventory(tierId, request.quantity(), request.reason())
                .map(result -> {
                    if (result.isSuccess()) {
                        return ResponseEntity.ok(result);
                    } else {
                        return ResponseEntity.badRequest().body(result);
                    }
                });
    }

    /**
     * Get current inventory snapshot.
     *
     * <p>Returns current state of inventory for monitoring/debugging.</p>
     *
     * @param tierId Ticket tier ID
     * @return Inventory snapshot
     */
    @GetMapping("/{tierId}/snapshot")
    public Mono<ResponseEntity<InventoryService.InventorySnapshot>> getInventorySnapshot(
            @PathVariable String tierId) {

        log.debug("Get inventory snapshot request: tier={}", tierId);

        return inventoryService.getInventorySnapshot(tierId)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }
}
