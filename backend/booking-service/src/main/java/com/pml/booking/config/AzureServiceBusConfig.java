package com.pml.booking.config;

import com.azure.spring.messaging.checkpoint.Checkpointer;
import com.pml.booking.event.listener.CatalogEventListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Consumer;

import static com.azure.spring.messaging.AzureHeaders.CHECKPOINTER;

/**
 * ============================================================================
 * AZURE SERVICE BUS CONFIGURATION - Production Ready
 * ============================================================================
 *
 * Defines Spring Cloud Stream function bindings for consuming and producing
 * events via Azure Service Bus topics.
 *
 * TOPICS CONSUMED:
 * - catalog-events: Events from Catalog Service (published, completed, cancelled)
 *
 * TOPICS PRODUCED:
 * - booking-events: Ticket and payment events for other services
 *
 * PRODUCTION FEATURES:
 * - Manual checkpointing for reliable message delivery
 * - Dead Letter Queue (DLQ) support for failed messages
 * - Proper error handling and logging
 * - StreamBridge for dynamic message publishing
 *
 * ARCHITECTURE:
 * - Spring Modulith handles internal (intra-service) events
 * - Azure Service Bus handles external (cross-service) communication
 *
 * ============================================================================
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AzureServiceBusConfig {

    private final CatalogEventListener catalogEventListener;

    /**
     * ========================================================================
     * UNIFIED CATALOG EVENT CONSUMER
     * ========================================================================
     *
     * Single consumer that handles all event types from Catalog Service.
     * Uses event type routing to dispatch to appropriate handlers.
     *
     * MESSAGE ACKNOWLEDGMENT:
     * - Uses manual checkpointing (auto-complete: false in YAML config)
     * - Messages are only acknowledged after successful processing
     * - Failed messages are NOT acknowledged, enabling retry/DLQ
     *
     * ERROR HANDLING:
     * - Exceptions during processing prevent checkpointing
     * - Azure Service Bus retries based on max-delivery-count
     * - After max retries, message is moved to DLQ
     *
     * @return Consumer function for all catalog events
     */
    @Bean
    public Consumer<Message<CatalogEventMessage>> catalogEventConsumer() {
        return message -> {
            CatalogEventMessage event = message.getPayload();
            Checkpointer checkpointer = (Checkpointer) message.getHeaders().get(CHECKPOINTER);

            String correlationId = (String) message.getHeaders().getOrDefault("X-Correlation-ID", "unknown");

            log.info("Received catalog event: type={}, eventId={}, correlationId={}",
                    event.eventType(), event.eventId(), correlationId);

            try {
                // Route to appropriate handler based on event type
                switch (event.eventType()) {
                    case "EventPublished" -> handleEventPublished(event, checkpointer);
                    case "EventCompleted" -> handleEventCompleted(event, checkpointer);
                    case "EventCancelled" -> handleEventCancelled(event, checkpointer);
                    case "EventRescheduled" -> handleEventRescheduled(event, checkpointer);
                    default -> {
                        log.warn("Unknown event type: {}. Acknowledging to prevent infinite redelivery.",
                                event.eventType());
                        checkpoint(checkpointer, event.eventType(), event.eventId());
                    }
                }
            } catch (Exception e) {
                log.error("Exception processing catalog event: type={}, eventId={}, error={}",
                        event.eventType(), event.eventId(), e.getMessage(), e);
                // Don't checkpoint - let Service Bus retry or send to DLQ
            }
        };
    }

    /**
     * Handle EventPublished - Create escrow account for the event.
     */
    private void handleEventPublished(CatalogEventMessage event, Checkpointer checkpointer) {
        log.info("Processing EventPublished: eventId={}, title={}", event.eventId(), event.eventTitle());

        catalogEventListener.onEventPublished(
                        event.eventId(),
                        event.eventTitle(),
                        event.organizerId(),
                        event.organizerName(),
                        event.eventDateTime()
                )
                .doOnSuccess(v -> {
                    log.info("Successfully processed EventPublished: eventId={}", event.eventId());
                    checkpoint(checkpointer, "EventPublished", event.eventId());
                })
                .doOnError(e -> log.error("Failed to process EventPublished: eventId={}, error={}",
                        event.eventId(), e.getMessage()))
                .subscribe();
    }

    /**
     * Handle EventCompleted - Lock escrow and start hold period.
     */
    private void handleEventCompleted(CatalogEventMessage event, Checkpointer checkpointer) {
        log.info("Processing EventCompleted: eventId={}, title={}", event.eventId(), event.eventTitle());

        catalogEventListener.onEventCompleted(event.eventId())
                .doOnSuccess(v -> {
                    log.info("Successfully processed EventCompleted: eventId={}", event.eventId());
                    checkpoint(checkpointer, "EventCompleted", event.eventId());
                })
                .doOnError(e -> log.error("Failed to process EventCompleted: eventId={}, error={}",
                        event.eventId(), e.getMessage()))
                .subscribe();
    }

    /**
     * Handle EventCancelled - Initiate automatic refunds.
     */
    private void handleEventCancelled(CatalogEventMessage event, Checkpointer checkpointer) {
        log.info("Processing EventCancelled: eventId={}, reason={}",
                event.eventId(), event.cancellationReason());

        catalogEventListener.onEventCancelled(event.eventId(), event.cancellationReason())
                .doOnSuccess(v -> {
                    log.info("Successfully processed EventCancelled: eventId={}", event.eventId());
                    checkpoint(checkpointer, "EventCancelled", event.eventId());
                })
                .doOnError(e -> log.error("Failed to process EventCancelled: eventId={}, error={}",
                        event.eventId(), e.getMessage()))
                .subscribe();
    }

    /**
     * Handle EventRescheduled - Update escrow dates and open refund window.
     */
    private void handleEventRescheduled(CatalogEventMessage event, Checkpointer checkpointer) {
        log.info("Processing EventRescheduled: eventId={}, from {} to {}",
                event.eventId(), event.originalDateTime(), event.newDateTime());

        catalogEventListener.onEventRescheduled(
                        event.eventId(),
                        event.originalDateTime(),
                        event.newDateTime()
                )
                .doOnSuccess(v -> {
                    log.info("Successfully processed EventRescheduled: eventId={}", event.eventId());
                    checkpoint(checkpointer, "EventRescheduled", event.eventId());
                })
                .doOnError(e -> log.error("Failed to process EventRescheduled: eventId={}, error={}",
                        event.eventId(), e.getMessage()))
                .subscribe();
    }

    /**
     * Checkpoint (acknowledge) a successfully processed message.
     */
    private void checkpoint(Checkpointer checkpointer, String eventType, String eventId) {
        if (checkpointer != null) {
            checkpointer.success()
                    .doOnSuccess(v -> log.debug("Checkpointed {}: eventId={}", eventType, eventId))
                    .doOnError(e -> log.error("Failed to checkpoint {}: eventId={}, error={}",
                            eventType, eventId, e.getMessage()))
                    .subscribe();
        } else {
            log.warn("No checkpointer available for {}: eventId={}", eventType, eventId);
        }
    }

    /**
     * ========================================================================
     * TICKET OUTPUT PRODUCER FUNCTION
     * ========================================================================
     *
     * Placeholder for ticket event publishing. In practice, use StreamBridge
     * to publish events dynamically:
     *
     *   streamBridge.send("ticketOutput-out-0", message);
     *
     * This bean exists to satisfy the function definition in application.yml.
     */
    @Bean
    public Consumer<TicketEventMessage> ticketOutput() {
        return event -> {
            log.debug("Ticket output function invoked: {}", event);
            // This is typically not called directly - use StreamBridge instead
        };
    }

    // ========================================================================
    // MESSAGE DTOS
    // ========================================================================

    /**
     * Unified message format for catalog events.
     * All event types use this structure with eventType discriminator.
     */
    public record CatalogEventMessage(
            String eventType,
            String eventId,
            String eventTitle,
            String organizerId,
            String organizerName,
            LocalDateTime eventDateTime,
            String locationId,
            String locationName,
            String cancellationReason,
            LocalDateTime originalDateTime,
            LocalDateTime newDateTime,
            String rescheduleReason,
            int totalTicketsSold,
            String timestamp
    ) {}

    /**
     * Message format for ticket events published by this service.
     */
    public record TicketEventMessage(
            String eventType,
            String ticketId,
            String ticketNumber,
            String eventId,
            String buyerId,
            String status,
            java.math.BigDecimal price,
            String currency,
            String timestamp
    ) {}
}
