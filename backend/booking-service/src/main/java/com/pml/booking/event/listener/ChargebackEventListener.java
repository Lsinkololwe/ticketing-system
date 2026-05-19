package com.pml.booking.event.listener;

import com.pml.booking.domain.enums.AlertPriority;
import com.pml.booking.domain.enums.NotificationType;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.event.domain.ChargebackReceivedEvent;
import com.pml.booking.event.domain.ChargebackResolvedEvent;
import com.pml.booking.infrastructure.client.CatalogServiceClient;
import com.pml.booking.repository.TicketRepository;
import com.pml.booking.service.NotificationService;
import com.pml.shared.constants.TicketStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Chargeback Event Listener
 *
 * <p>Handles chargeback domain events and orchestrates notifications and
 * follow-up actions for the financial team and organizers.</p>
 *
 * <h2>Events Handled</h2>
 * <ul>
 *   <li><b>ChargebackReceivedEvent</b>: New chargeback received from payment provider</li>
 *   <li><b>ChargebackResolvedEvent</b>: Chargeback has reached final state (won/lost/accepted)</li>
 * </ul>
 *
 * <h2>Notification Recipients</h2>
 * <ul>
 *   <li>Organizer - Owner of the event where chargeback occurred</li>
 *   <li>Finance Team - Platform administrators</li>
 * </ul>
 *
 * <h2>Notification Channels</h2>
 * <ul>
 *   <li>Email - Always sent for all chargeback events</li>
 *   <li>Slack - Sent for HIGH and CRITICAL priority alerts</li>
 *   <li>SMS - Sent for CRITICAL alerts (write-offs)</li>
 * </ul>
 *
 * <p>Uses Spring Modulith's @ApplicationModuleListener for reliable event processing
 * with automatic retry and dead-letter handling via Event Publication Registry.</p>
 *
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChargebackEventListener {

    private final NotificationService notificationService;
    private final StreamBridge streamBridge;
    private final TicketRepository ticketRepository;
    private final CatalogServiceClient catalogServiceClient;

    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Handles new chargeback received from payment provider.
     *
     * <h2>Actions Performed</h2>
     * <ol>
     *   <li><b>CRITICAL: Invalidate the ticket immediately</b></li>
     *   <li>Restore inventory to available pool</li>
     *   <li>Log the chargeback for audit trail</li>
     *   <li>Notify organizer of the chargeback via email</li>
     *   <li>Alert finance team via email and Slack</li>
     *   <li>Publish event to Azure Service Bus for cross-service integration</li>
     * </ol>
     *
     * @param event The chargeback received event
     */
    @ApplicationModuleListener
    public void onChargebackReceived(ChargebackReceivedEvent event) {
        log.info("Processing ChargebackReceivedEvent: chargeback={}, event={}, organizer={}, amount={}",
                event.getChargebackId(),
                event.getEventId(),
                event.getOrganizerId(),
                event.getChargebackAmount());

        // CRITICAL: Invalidate the ticket FIRST before any notifications
        // This ensures the ticket cannot be used at the event
        invalidateTicketForChargeback(event);

        // Log detailed audit information
        log.info("Chargeback Details - Record ID: {}, Reason: {}, Deadline: {}, Fee: {}",
                event.getChargebackRecordId(),
                event.getReason(),
                event.getResponseDeadline(),
                event.getChargebackFee());

        // Build notification context
        Map<String, Object> context = new HashMap<>();
        context.put("chargebackId", event.getChargebackId());
        context.put("amount", event.getChargebackAmount().toString());
        context.put("reason", event.getReason().getDisplayName());
        context.put("deadline", event.getResponseDeadline().toString());
        context.put("fee", event.getChargebackFee().toString());
        context.put("eventId", event.getEventId());
        context.put("ticketId", event.getTicketId());

        // Send notification to organizer
        notificationService.sendOrganizerNotification(
                event.getOrganizerId(),
                NotificationType.CHARGEBACK_RECEIVED,
                context
        ).subscribe(
                unused -> log.debug("Organizer notification sent for chargeback: {}", event.getChargebackId()),
                error -> log.error("Failed to send organizer notification for chargeback: {}", event.getChargebackId(), error)
        );

        // Alert finance team
        notificationService.sendAdminAlert(
                AlertPriority.HIGH,
                "New Chargeback Received",
                String.format("Chargeback %s for K%.2f - %s\n" +
                                "Event: %s\n" +
                                "Organizer: %s\n" +
                                "Response Deadline: %s",
                        event.getChargebackId(),
                        event.getChargebackAmount(),
                        event.getReason().getDisplayName(),
                        event.getEventId(),
                        event.getOrganizerId(),
                        event.getResponseDeadline())
        ).subscribe(
                unused -> log.debug("Admin alert sent for chargeback: {}", event.getChargebackId()),
                error -> log.error("Failed to send admin alert for chargeback: {}", event.getChargebackId(), error)
        );

        // Publish to Azure Service Bus for other services (catalog, identity)
        boolean sent = streamBridge.send("chargeback-events-out-0", event);
        if (sent) {
            log.debug("Chargeback event published to Service Bus: {}", event.getChargebackId());
        } else {
            log.warn("Failed to publish chargeback event to Service Bus: {}", event.getChargebackId());
        }

        log.info("ChargebackReceivedEvent processing completed for chargeback: {}",
                event.getChargebackId());
    }

    /**
     * Handles chargeback resolution (won, lost, or accepted).
     *
     * <h2>Actions Performed</h2>
     * <ol>
     *   <li>Log the resolution for audit trail</li>
     *   <li>Notify organizer of the outcome via email</li>
     *   <li>Update finance team on recovery status</li>
     *   <li>Send CRITICAL alert if write-off occurred</li>
     *   <li>Publish event to Azure Service Bus for analytics</li>
     * </ol>
     *
     * @param event The chargeback resolved event
     */
    @ApplicationModuleListener
    public void onChargebackResolved(ChargebackResolvedEvent event) {
        log.info("Processing ChargebackResolvedEvent: chargeback={}, status={}, disputeWon={}, recovery={}",
                event.getChargebackId(),
                event.getFinalStatus(),
                event.getDisputeWon(),
                event.getRecoveryStatus());

        // Log detailed resolution information
        log.info("Resolution Details - Record ID: {}, Total Impact: K{}, Recovered: K{}, Written Off: K{}",
                event.getChargebackRecordId(),
                event.getTotalAmount(),
                event.getRecoveredAmount(),
                event.getWrittenOffAmount());

        // If dispute was won, restore the ticket to valid status
        if (Boolean.TRUE.equals(event.getDisputeWon())) {
            restoreTicketAfterDisputeWin(event);
        }

        // Determine notification message based on outcome
        String outcomeMessage = determineOutcomeMessage(event);
        log.info("Chargeback Outcome: {}", outcomeMessage);

        // Build notification context
        Map<String, Object> context = new HashMap<>();
        context.put("chargebackId", event.getChargebackId());
        context.put("outcome", outcomeMessage);
        context.put("recoveredAmount", event.getRecoveredAmount() != null ? event.getRecoveredAmount().toString() : "0.00");
        context.put("fundSource", event.getFundSource() != null ? event.getFundSource().name() : "N/A");
        context.put("disputeWon", String.valueOf(Boolean.TRUE.equals(event.getDisputeWon())));
        context.put("totalAmount", event.getTotalAmount() != null ? event.getTotalAmount().toString() : "0.00");

        // Send notification to organizer about resolution
        notificationService.sendOrganizerNotification(
                event.getOrganizerId(),
                NotificationType.CHARGEBACK_RESOLVED,
                context
        ).subscribe(
                unused -> log.debug("Organizer resolution notification sent for chargeback: {}", event.getChargebackId()),
                error -> log.error("Failed to send organizer resolution notification: {}", event.getChargebackId(), error)
        );

        // Check if there was a write-off and send critical alert
        boolean hasWriteOff = event.getWrittenOffAmount() != null &&
                event.getWrittenOffAmount().compareTo(BigDecimal.ZERO) > 0;

        if (hasWriteOff) {
            log.warn("WRITE-OFF ALERT: Chargeback {} resulted in K{} write-off",
                    event.getChargebackId(),
                    event.getWrittenOffAmount());

            // Add write-off specific context
            Map<String, Object> writeOffContext = new HashMap<>(context);
            writeOffContext.put("writeOffAmount", event.getWrittenOffAmount().toString());

            // Send CRITICAL alert for write-offs (email + Slack + SMS)
            notificationService.sendAdminAlert(
                    AlertPriority.CRITICAL,
                    "Chargeback Write-Off",
                    String.format("UNRECOVERABLE LOSS\n\n" +
                                    "Chargeback: %s\n" +
                                    "Write-off Amount: K%.2f\n" +
                                    "Total Impact: K%s\n" +
                                    "Recovery Attempted: K%s\n" +
                                    "Organizer: %s\n\n" +
                                    "This amount has been recorded as bad debt expense (account 5040).",
                            event.getChargebackId(),
                            event.getWrittenOffAmount(),
                            event.getTotalAmount(),
                            event.getRecoveredAmount(),
                            event.getOrganizerId())
            ).subscribe(
                    unused -> log.debug("Write-off critical alert sent for chargeback: {}", event.getChargebackId()),
                    error -> log.error("Failed to send write-off alert: {}", event.getChargebackId(), error)
            );

            // Also send specific write-off notification type
            notificationService.sendAdminAlert(
                    NotificationType.CHARGEBACK_WRITE_OFF,
                    writeOffContext
            ).subscribe();
        } else {
            // Send standard resolution alert (non-critical)
            AlertPriority priority = Boolean.TRUE.equals(event.getDisputeWon())
                    ? AlertPriority.LOW
                    : AlertPriority.MEDIUM;

            notificationService.sendAdminAlert(
                    priority,
                    "Chargeback Resolved: " + event.getChargebackId(),
                    outcomeMessage
            ).subscribe(
                    unused -> log.debug("Resolution alert sent for chargeback: {}", event.getChargebackId()),
                    error -> log.error("Failed to send resolution alert: {}", event.getChargebackId(), error)
            );
        }

        // Publish to Azure Service Bus for analytics service
        boolean sent = streamBridge.send("chargeback-events-out-0", event);
        if (sent) {
            log.debug("Chargeback resolution event published to Service Bus: {}", event.getChargebackId());
        } else {
            log.warn("Failed to publish chargeback resolution event to Service Bus: {}", event.getChargebackId());
        }

        log.info("ChargebackResolvedEvent processing completed for chargeback: {}",
                event.getChargebackId());
    }

    /**
     * Determines the outcome message based on the chargeback resolution.
     *
     * @param event The chargeback resolved event
     * @return Human-readable outcome message
     */
    private String determineOutcomeMessage(ChargebackResolvedEvent event) {
        if (Boolean.TRUE.equals(event.getDisputeWon())) {
            return String.format("DISPUTE WON - Chargeback reversed, no loss to platform. " +
                    "Amount: K%s", event.getTotalAmount());
        } else if (Boolean.FALSE.equals(event.getDisputeWon())) {
            return String.format("DISPUTE LOST - Recovery from %s: K%s, Write-off: K%s",
                    event.getFundSource() != null ? event.getFundSource().name() : "MULTIPLE_SOURCES",
                    event.getRecoveredAmount(),
                    event.getWrittenOffAmount() != null ? event.getWrittenOffAmount() : "0.00");
        } else {
            // Not disputed, accepted directly
            return String.format("ACCEPTED - Recovery from %s: K%s, Write-off: K%s",
                    event.getFundSource() != null ? event.getFundSource().name() : "MULTIPLE_SOURCES",
                    event.getRecoveredAmount(),
                    event.getWrittenOffAmount() != null ? event.getWrittenOffAmount() : "0.00");
        }
    }

    // ========================================================================
    // TICKET INVALIDATION METHODS
    // ========================================================================

    /**
     * Invalidate ticket when chargeback is received.
     *
     * <p><b>CRITICAL</b>: This method MUST complete before notifications are sent.
     * A chargebacked ticket should never be usable at the event.</p>
     *
     * <p>Operations performed:</p>
     * <ol>
     *   <li>Set ticket status to CHARGEDBACK</li>
     *   <li>Record cancellation reason with chargeback details</li>
     *   <li>Restore inventory to available pool</li>
     * </ol>
     *
     * @param event Chargeback received event
     */
    private void invalidateTicketForChargeback(ChargebackReceivedEvent event) {
        try {
            Ticket ticket = ticketRepository.findById(event.getTicketId())
                    .flatMap(t -> {
                        // Only invalidate if ticket is in a chargebackable state
                        if (!t.getStatus().isChargebackEligible()) {
                            log.warn("Ticket {} is not eligible for chargeback invalidation. Current status: {}",
                                    t.getId(), t.getStatus());
                            return ticketRepository.save(t);
                        }

                        // Update ticket status to CHARGEDBACK
                        t.setStatus(TicketStatus.CHARGEDBACK);
                        t.setCancellationReason(String.format("Chargeback: %s (ID: %s)",
                                event.getReason().getDisplayName(),
                                event.getChargebackId()));
                        t.setCancelledAt(LocalDateTime.now());

                        // Restore inventory to available pool
                        String tierId = t.getTicketTierId();
                        if (tierId != null) {
                            return catalogServiceClient.restoreInventory(tierId, 1, "CHARGEBACK")
                                    .doOnSuccess(result -> {
                                        if (result.success()) {
                                            log.info("Inventory restored for chargebacked ticket: tier={}", tierId);
                                        } else {
                                            log.warn("Inventory restore failed for tier {}: {}",
                                                    tierId, result.errorMessage());
                                        }
                                    })
                                    .then(ticketRepository.save(t));
                        }
                        return ticketRepository.save(t);
                    })
                    .block(BLOCK_TIMEOUT);

            if (ticket != null) {
                log.info("Ticket {} invalidated due to chargeback {}. Status: {}",
                        ticket.getId(), event.getChargebackId(), ticket.getStatus());
            }
        } catch (Exception e) {
            log.error("CRITICAL: Failed to invalidate ticket {} for chargeback {}",
                    event.getTicketId(), event.getChargebackId(), e);
            // Re-throw to trigger Modulith retry - ticket invalidation is critical
            throw new RuntimeException("Ticket invalidation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Restore ticket after winning a chargeback dispute.
     *
     * <p>Called when a chargeback dispute is won. The ticket is restored to PURCHASED status,
     * but inventory is NOT restored (it was already restored when chargeback was received).</p>
     *
     * @param event Chargeback resolved event
     */
    private void restoreTicketAfterDisputeWin(ChargebackResolvedEvent event) {
        try {
            Ticket ticket = ticketRepository.findById(event.getTicketId())
                    .flatMap(t -> {
                        if (t.getStatus() != TicketStatus.CHARGEDBACK) {
                            log.warn("Ticket {} is not in CHARGEDBACK status, cannot restore. Current: {}",
                                    t.getId(), t.getStatus());
                            return ticketRepository.save(t);
                        }

                        // Restore ticket to PURCHASED status
                        t.setStatus(TicketStatus.PURCHASED);
                        t.setCancellationReason(null);
                        t.setCancelledAt(null);

                        // NOTE: We do NOT restore inventory here because:
                        // 1. Inventory was already restored when chargeback was received
                        // 2. To make ticket valid again, we need to DEDUCT from available (sell again)
                        String tierId = t.getTicketTierId();
                        String reservationId = t.getReservationId();
                        if (tierId != null && reservationId != null) {
                            // Re-sell the ticket by decrementing available and incrementing sold
                            return catalogServiceClient.commitInventoryToSold(tierId, 1, reservationId)
                                    .doOnSuccess(result -> {
                                        if (result.success()) {
                                            log.info("Inventory re-committed for restored ticket: tier={}", tierId);
                                        } else {
                                            // Log warning but don't fail - ticket restoration is more important
                                            log.warn("Inventory re-commit failed for tier {}: {}",
                                                    tierId, result.errorMessage());
                                        }
                                    })
                                    .onErrorResume(e -> {
                                        log.warn("Error re-committing inventory for tier {}: {}", tierId, e.getMessage());
                                        return reactor.core.publisher.Mono.empty();
                                    })
                                    .then(ticketRepository.save(t));
                        }
                        return ticketRepository.save(t);
                    })
                    .block(BLOCK_TIMEOUT);

            if (ticket != null) {
                log.info("Ticket {} restored after dispute win. Status: {}", ticket.getId(), ticket.getStatus());
            }
        } catch (Exception e) {
            log.error("Failed to restore ticket {} after dispute win", event.getTicketId(), e);
            // Don't re-throw - ticket restoration after dispute win is not as critical
            // The financial resolution is what matters most
        }
    }
}
