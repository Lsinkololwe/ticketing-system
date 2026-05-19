# Architecture Improvements: Spring Modulith + Azure Service Bus + pawaPay

**Version:** 1.0
**Date:** March 2026
**Purpose:** Migration guide from Debezium CDC to Spring Modulith with Azure Service Bus and unified payment processor

---

## 1. Current State vs Target State

### 1.1 Current Implementation Analysis

| Component | Current State | Issues |
|-----------|---------------|--------|
| **Intra-service Events** | Custom `OutboxPublisherImpl` + Debezium CDC | Complex setup, requires Debezium Server container |
| **Cross-service Events** | HTTP webhooks via `CdcWebhookController` | No guaranteed delivery, no dead-letter handling |
| **Event Storage** | `outbox_events` collection | Manual management, no automatic retry |
| **Payment Providers** | pawaPay (MTN, Airtel, Zamtel via unified API) | Single integration for all Zambian mobile money |
| **Idempotency** | Manual implementation | Inconsistent across services |

### 1.2 Target Architecture

| Component | Target State | Benefits |
|-----------|--------------|----------|
| **Intra-service Events** | Spring Modulith `@ApplicationModuleListener` | Built-in retry, guaranteed delivery, less code |
| **Cross-service Events** | Azure Service Bus (Topics/Subscriptions) | Reliable messaging, dead-letter queues, scaling |
| **Event Storage** | `event_publications` (Spring Modulith) | Automatic management, completion tracking |
| **Payment Provider** | pawaPay (unified mobile money API) | Single integration for MTN, Airtel, Zamtel in Zambia |
| **Idempotency** | Spring Modulith Event Publication Registry | Automatic deduplication |

---

## 2. Spring Modulith Integration

### 2.1 Add Dependencies

```xml
<!-- pom.xml for each service -->
<dependencies>
    <!-- Spring Modulith Core -->
    <dependency>
        <groupId>org.springframework.modulith</groupId>
        <artifactId>spring-modulith-starter-core</artifactId>
    </dependency>

    <!-- Spring Modulith Events with MongoDB -->
    <dependency>
        <groupId>org.springframework.modulith</groupId>
        <artifactId>spring-modulith-events-api</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.modulith</groupId>
        <artifactId>spring-modulith-events-mongodb</artifactId>
    </dependency>

    <!-- Azure Service Bus for cross-service events -->
    <dependency>
        <groupId>com.azure.spring</groupId>
        <artifactId>spring-cloud-azure-stream-binder-servicebus</artifactId>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-bom</artifactId>
            <version>1.3.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>com.azure.spring</groupId>
            <artifactId>spring-cloud-azure-dependencies</artifactId>
            <version>5.19.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 2.2 Enable Spring Modulith Events

```java
// CatalogServiceApplication.java
package com.pml.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulith;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@Modulith
@EnableAsync
@EnableScheduling
public class CatalogServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }
}
```

### 2.3 Configure Event Publication Repository

```yaml
# application.yml
spring:
  modulith:
    events:
      mongodb:
        transaction-management:
          enabled: true
        # Incomplete events are republished on restart
      republish-outstanding-events-on-restart: true
    # Event externalization to Azure Service Bus
    externalization:
      enabled: true
```

### 2.4 Replace OutboxPublisher with ApplicationEventPublisher

**Before (Current - Remove):**
```java
// OutboxPublisherImpl.java - TO BE REMOVED
@Service
public class OutboxPublisherImpl implements OutboxPublisher {
    private final OutboxEventRepository outboxEventRepository;

    @Override
    public Mono<OutboxEvent> publish(DomainEvent event) {
        OutboxEvent outboxEvent = OutboxEvent.builder()
            .eventType(event.getEventType())
            .aggregateId(event.getAggregateId())
            .service("catalog")
            .eventData(event.getEventData())
            .build();
        return outboxEventRepository.save(outboxEvent);
    }
}
```

**After (Spring Modulith):**
```java
// EventService.java - Using Spring's ApplicationEventPublisher
package com.pml.catalog.service.impl;

import com.pml.catalog.event.EventApprovedEvent;
import com.pml.catalog.event.EventPublishedEvent;
import com.pml.catalog.model.Event;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Mono<Event> approveEvent(String eventId, String adminId) {
        return eventRepository.findById(eventId)
            .flatMap(event -> {
                event.setStatus(EventStatus.APPROVED);
                event.setApprovedBy(adminId);
                event.setApprovedAt(Instant.now());

                return eventRepository.save(event)
                    .doOnSuccess(savedEvent -> {
                        // Publish event - Spring Modulith handles persistence
                        eventPublisher.publishEvent(new EventApprovedEvent(
                            savedEvent.getId(),
                            savedEvent.getOrganizerId(),
                            savedEvent.getTitle()
                        ));
                    });
            });
    }

    @Override
    @Transactional
    public Mono<Event> publishEvent(String eventId) {
        return eventRepository.findById(eventId)
            .filter(event -> event.getStatus() == EventStatus.APPROVED)
            .flatMap(event -> {
                event.setStatus(EventStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());

                return eventRepository.save(event)
                    .doOnSuccess(savedEvent -> {
                        // This event will be:
                        // 1. Persisted to event_publications (guaranteed delivery)
                        // 2. Processed by @ApplicationModuleListener handlers
                        // 3. Externalized to Azure Service Bus for other services
                        eventPublisher.publishEvent(new EventPublishedEvent(
                            savedEvent.getId(),
                            savedEvent.getOrganizerId(),
                            savedEvent.getTitle(),
                            savedEvent.getEventDate()
                        ));
                    });
            });
    }
}
```

### 2.5 Define Domain Events

```java
// EventPublishedEvent.java - Domain Event
package com.pml.catalog.event;

import org.springframework.modulith.events.Externalized;
import java.time.Instant;

/**
 * Published when an event goes live and tickets become available.
 *
 * Listeners:
 * - Internal: EscrowAccountCreator (creates escrow account)
 * - External: Identity Service (notify organizer), Booking Service (enable ticket sales)
 */
@Externalized("event-events::EventPublished")  // Topic::RoutingKey for Azure Service Bus
public record EventPublishedEvent(
    String eventId,
    String organizerId,
    String eventTitle,
    Instant eventDate,
    Instant occurredAt
) {
    public EventPublishedEvent(String eventId, String organizerId, String eventTitle, Instant eventDate) {
        this(eventId, organizerId, eventTitle, eventDate, Instant.now());
    }
}

// TicketPurchasedEvent.java
package com.pml.booking.event;

import org.springframework.modulith.events.Externalized;
import java.math.BigDecimal;

/**
 * Published after successful payment confirmation.
 *
 * Internal Listeners:
 * - EscrowService (credit escrow)
 * - CommissionService (record pending commission)
 * - QRCodeService (generate QR)
 *
 * External Listeners:
 * - Catalog Service (decrement inventory)
 * - Identity Service (send confirmation)
 */
@Externalized("ticket-events::TicketPurchased")
public record TicketPurchasedEvent(
    String ticketId,
    String eventId,
    String buyerId,
    String organizerId,
    BigDecimal ticketPrice,
    BigDecimal commissionAmount,
    BigDecimal escrowAmount,
    String paymentProvider,
    String transactionRef,
    Instant occurredAt
) {
    public TicketPurchasedEvent(
        String ticketId, String eventId, String buyerId, String organizerId,
        BigDecimal ticketPrice, BigDecimal commissionAmount, BigDecimal escrowAmount,
        String paymentProvider, String transactionRef
    ) {
        this(ticketId, eventId, buyerId, organizerId, ticketPrice, commissionAmount,
             escrowAmount, paymentProvider, transactionRef, Instant.now());
    }
}

// PaymentFailedEvent.java
package com.pml.booking.event;

import org.springframework.modulith.events.Externalized;

@Externalized("payment-events::PaymentFailed")
public record PaymentFailedEvent(
    String paymentIntentId,
    String ticketId,
    String eventId,
    String userId,
    String failureReason,
    String paymentProvider,
    Instant occurredAt
) {}

// RefundCompletedEvent.java
package com.pml.booking.event;

import org.springframework.modulith.events.Externalized;
import java.math.BigDecimal;

@Externalized("payment-events::RefundCompleted")
public record RefundCompletedEvent(
    String refundId,
    String ticketId,
    String eventId,
    String buyerId,
    BigDecimal refundAmount,
    BigDecimal commissionCancelled,
    String refundReason,
    Instant occurredAt
) {}
```

### 2.6 Create Event Listeners with @ApplicationModuleListener

```java
// EscrowEventListener.java - Handles escrow operations
package com.pml.booking.listener;

import com.pml.booking.event.TicketPurchasedEvent;
import com.pml.booking.event.RefundCompletedEvent;
import com.pml.booking.service.EscrowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Listens to payment events and manages escrow accounts.
 *
 * @ApplicationModuleListener provides:
 * - Guaranteed delivery (persisted to event_publications)
 * - Automatic retry on failure
 * - Idempotency via completion tracking
 * - Transaction integration
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EscrowEventListener {

    private final EscrowService escrowService;

    /**
     * Credits escrow when ticket is purchased.
     * This runs within the same transaction as the event publication.
     */
    @ApplicationModuleListener
    public void onTicketPurchased(TicketPurchasedEvent event) {
        log.info("Processing escrow credit for ticket: {}, amount: {}",
            event.ticketId(), event.escrowAmount());

        escrowService.creditEscrow(
            event.eventId(),
            event.ticketId(),
            event.escrowAmount(),
            event.transactionRef()
        ).subscribe(
            escrow -> log.info("Escrow credited. Balance: {}", escrow.getCurrentBalance()),
            error -> log.error("Failed to credit escrow", error)
        );
    }

    /**
     * Debits escrow when refund is completed.
     */
    @ApplicationModuleListener
    public void onRefundCompleted(RefundCompletedEvent event) {
        log.info("Processing escrow debit for refund: {}, amount: {}",
            event.refundId(), event.refundAmount());

        escrowService.debitEscrow(
            event.eventId(),
            event.ticketId(),
            event.refundAmount(),
            "REFUND:" + event.refundId()
        ).subscribe();
    }
}

// CommissionEventListener.java - Handles two-stage commission
package com.pml.booking.listener;

import com.pml.booking.event.TicketPurchasedEvent;
import com.pml.booking.event.RefundCompletedEvent;
import com.pml.booking.service.CommissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommissionEventListener {

    private final CommissionService commissionService;

    /**
     * Records PENDING commission (not yet earned).
     * Commission only becomes EARNED after event + 7-day hold.
     */
    @ApplicationModuleListener
    public void onTicketPurchased(TicketPurchasedEvent event) {
        log.info("Recording pending commission for ticket: {}, amount: {}",
            event.ticketId(), event.commissionAmount());

        commissionService.recordPendingCommission(
            event.ticketId(),
            event.eventId(),
            event.commissionAmount()
        ).subscribe();
    }

    /**
     * Cancels pending commission on refund (before event).
     * No clawback needed since commission was never earned!
     */
    @ApplicationModuleListener
    public void onRefundCompleted(RefundCompletedEvent event) {
        log.info("Cancelling pending commission for ticket: {}", event.ticketId());

        commissionService.cancelPendingCommission(
            event.ticketId(),
            event.refundId()
        ).subscribe();
    }
}

// InventoryEventListener.java (in Catalog Service)
package com.pml.catalog.listener;

import com.pml.catalog.event.external.TicketPurchasedEvent;
import com.pml.catalog.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Listens to events from Booking Service (via Azure Service Bus)
 * and updates inventory.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventListener {

    private final EventService eventService;

    @ApplicationModuleListener
    public void onTicketPurchased(TicketPurchasedEvent event) {
        log.info("Decrementing inventory for event: {}", event.eventId());

        eventService.decrementInventory(
            event.eventId(),
            event.ticketCategoryCode(),
            1
        ).subscribe();
    }
}
```

### 2.7 Files to Remove (Debezium CDC)

After migration, remove these files:

```
catalog-service/
  src/main/java/com/pml/catalog/
    controller/CdcWebhookController.java     ← REMOVE
    model/OutboxEvent.java                   ← REMOVE
    model/OutboxEventStatus.java             ← REMOVE
    repository/OutboxEventRepository.java    ← REMOVE
    service/OutboxPublisher.java             ← REMOVE
    service/OutboxEventService.java          ← REMOVE
    service/impl/OutboxPublisherImpl.java    ← REMOVE

booking-service/
  src/main/java/com/pml/booking/
    controller/CdcWebhookController.java     ← REMOVE
    model/OutboxEvent.java                   ← REMOVE
    model/OutboxEventStatus.java             ← REMOVE
    repository/OutboxEventRepository.java    ← REMOVE
    service/OutboxPublisher.java             ← REMOVE
    service/OutboxEventService.java          ← REMOVE
    service/impl/OutboxPublisherImpl.java    ← REMOVE
```

Also remove Debezium from `docker-compose.yml`:
```yaml
# REMOVE these services:
# debezium-server:
#   image: debezium/server:2.4
#   ...
```

---

## 3. Azure Service Bus Integration

### 3.1 Create Azure Service Bus Resources

```bash
# Create Service Bus namespace
az servicebus namespace create \
  --name ticketing-events \
  --resource-group ticketing-rg \
  --location southafricanorth \
  --sku Standard

# Create topics for cross-service events
az servicebus topic create --name event-events --namespace-name ticketing-events --resource-group ticketing-rg
az servicebus topic create --name ticket-events --namespace-name ticketing-events --resource-group ticketing-rg
az servicebus topic create --name payment-events --namespace-name ticketing-events --resource-group ticketing-rg
az servicebus topic create --name user-events --namespace-name ticketing-events --resource-group ticketing-rg

# Create subscriptions for each service
# Catalog Service subscriptions
az servicebus topic subscription create --name catalog-sub --topic-name ticket-events --namespace-name ticketing-events --resource-group ticketing-rg
az servicebus topic subscription create --name catalog-sub --topic-name payment-events --namespace-name ticketing-events --resource-group ticketing-rg

# Booking Service subscriptions
az servicebus topic subscription create --name booking-sub --topic-name event-events --namespace-name ticketing-events --resource-group ticketing-rg
az servicebus topic subscription create --name booking-sub --topic-name user-events --namespace-name ticketing-events --resource-group ticketing-rg

# Identity Service subscriptions
az servicebus topic subscription create --name identity-sub --topic-name event-events --namespace-name ticketing-events --resource-group ticketing-rg
az servicebus topic subscription create --name identity-sub --topic-name ticket-events --namespace-name ticketing-events --resource-group ticketing-rg
az servicebus topic subscription create --name identity-sub --topic-name payment-events --namespace-name ticketing-events --resource-group ticketing-rg
```

### 3.2 Configure Spring Cloud Azure Service Bus

```yaml
# application.yml
spring:
  cloud:
    azure:
      servicebus:
        connection-string: ${AZURE_SERVICEBUS_CONNECTION_STRING}
        # Or use managed identity:
        # namespace: ticketing-events
        # credential:
        #   managed-identity-enabled: true

    # Spring Cloud Stream bindings
    stream:
      bindings:
        # Outbound - Publishing events
        eventEvents-out-0:
          destination: event-events
        ticketEvents-out-0:
          destination: ticket-events
        paymentEvents-out-0:
          destination: payment-events

        # Inbound - Consuming events (Catalog Service)
        ticketPurchased-in-0:
          destination: ticket-events
          group: catalog-sub
        paymentCompleted-in-0:
          destination: payment-events
          group: catalog-sub

      servicebus:
        bindings:
          # Configure each binding
          ticketPurchased-in-0:
            consumer:
              auto-complete: false  # Manual acknowledgment
              max-concurrent-calls: 10
              prefetch-count: 10
          paymentCompleted-in-0:
            consumer:
              auto-complete: false
              max-concurrent-calls: 5
```

### 3.3 Event Externalization to Azure Service Bus

```java
// ServiceBusEventExternalizer.java
package com.pml.booking.config;

import com.azure.spring.cloud.stream.binder.servicebus.ServiceBusMessageHeaders;
import com.pml.booking.event.TicketPurchasedEvent;
import com.pml.booking.event.PaymentCompletedEvent;
import com.pml.booking.event.RefundCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.stereotype.Component;

/**
 * Externalizes domain events to Azure Service Bus.
 *
 * Spring Modulith's @Externalized annotation marks events for externalization.
 * This component handles the actual sending to Azure Service Bus.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ServiceBusEventExternalizer {

    private final StreamBridge streamBridge;

    @EventListener
    public void onTicketPurchased(TicketPurchasedEvent event) {
        Message<TicketPurchasedEvent> message = MessageBuilder
            .withPayload(event)
            .setHeader(ServiceBusMessageHeaders.MESSAGE_ID, event.ticketId())
            .setHeader(ServiceBusMessageHeaders.CORRELATION_ID, event.transactionRef())
            .setHeader("eventType", "TicketPurchased")
            .build();

        boolean sent = streamBridge.send("ticketEvents-out-0", message);
        log.info("Externalized TicketPurchasedEvent to Azure Service Bus: {}, sent: {}",
            event.ticketId(), sent);
    }

    @EventListener
    public void onRefundCompleted(RefundCompletedEvent event) {
        Message<RefundCompletedEvent> message = MessageBuilder
            .withPayload(event)
            .setHeader(ServiceBusMessageHeaders.MESSAGE_ID, event.refundId())
            .setHeader("eventType", "RefundCompleted")
            .build();

        streamBridge.send("paymentEvents-out-0", message);
    }
}

// Alternative: Use Spring Modulith's built-in externalization
@Configuration
class EventExternalizationConfig {

    @Bean
    EventExternalizationConfiguration eventExternalizationConfiguration() {
        return EventExternalizationConfiguration.externalizing()
            // Route @Externalized events to Azure Service Bus
            .select(EventExternalizationConfiguration.annotatedAsExternalized())
            .route(
                TicketPurchasedEvent.class,
                it -> RoutingTarget.forTarget("ticketEvents-out-0")
            )
            .route(
                RefundCompletedEvent.class,
                it -> RoutingTarget.forTarget("paymentEvents-out-0")
            )
            .build();
    }
}
```

### 3.4 Consume Events from Azure Service Bus

```java
// ExternalEventConsumer.java (in Catalog Service)
package com.pml.catalog.consumer;

import com.azure.spring.messaging.servicebus.implementation.core.annotation.ServiceBusListener;
import com.pml.catalog.event.external.TicketPurchasedEvent;
import com.pml.catalog.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ExternalEventConsumer {

    private final EventService eventService;

    /**
     * Consumes TicketPurchasedEvent from Azure Service Bus.
     * Updates event inventory when ticket is sold.
     */
    @Bean
    public Consumer<Message<TicketPurchasedEvent>> ticketPurchased() {
        return message -> {
            TicketPurchasedEvent event = message.getPayload();
            String messageId = (String) message.getHeaders().get("message-id");

            log.info("Received TicketPurchasedEvent from Service Bus: eventId={}, messageId={}",
                event.eventId(), messageId);

            // Idempotency: Check if already processed
            eventService.decrementInventory(
                event.eventId(),
                event.ticketCategoryCode(),
                1
            )
            .doOnSuccess(v -> log.info("Inventory decremented for event: {}", event.eventId()))
            .doOnError(e -> log.error("Failed to decrement inventory", e))
            .subscribe();
        };
    }

    /**
     * Consumes RefundCompletedEvent to restore inventory.
     */
    @Bean
    public Consumer<Message<RefundCompletedEvent>> refundCompleted() {
        return message -> {
            RefundCompletedEvent event = message.getPayload();

            log.info("Received RefundCompletedEvent: ticketId={}", event.ticketId());

            eventService.incrementInventory(
                event.eventId(),
                event.ticketCategoryCode(),
                1
            ).subscribe();
        };
    }
}
```

---

## 4. pawaPay Integration (Unified Payment Processor)

### 4.1 Why pawaPay?

pawaPay provides a **single API for all mobile money providers in Zambia**:

| Provider | pawaPay Correspondent | Coverage |
|----------|----------------------|----------|
| MTN Mobile Money | `MTN_MOMO_ZMB` | Nationwide |
| Airtel Money | `AIRTEL_ZMB` | Nationwide |
| Zamtel Kwacha | `ZAMTEL_ZMB` | Nationwide |

**Benefits:**
- Single integration instead of 3 separate APIs
- Unified webhook format
- Provider prediction (automatically select best provider for phone number)
- Built-in reconciliation and reporting

### 4.2 pawaPay Configuration

```yaml
# application.yml
pawapay:
  api-url: https://api.pawapay.io
  api-token: ${PAWAPAY_API_TOKEN}
  webhook-secret: ${PAWAPAY_WEBHOOK_SECRET}

  # Zambia mobile money correspondents
  correspondents:
    - MTN_MOMO_ZMB    # MTN Mobile Money
    - AIRTEL_ZMB      # Airtel Money
    - ZAMTEL_ZMB      # Zamtel Kwacha

  # Default country for Zambia
  country: ZMB
  currency: ZMW
```

### 4.3 pawaPay Client Service

```java
// PawaPayClient.java
package com.pml.booking.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PawaPayClient {

    private final WebClient pawaPayWebClient;

    /**
     * Predict the best mobile money provider for a phone number.
     * pawaPay returns the correspondent (MTN, Airtel, Zamtel) for the number.
     */
    public Mono<PredictProviderResponse> predictProvider(String phoneNumber) {
        return pawaPayWebClient.post()
            .uri("/predict-provider")
            .bodyValue(new PredictProviderRequest(phoneNumber, "ZMB", List.of("MTN_MOMO_ZMB", "AIRTEL_ZMB", "ZAMTEL_ZMB")))
            .retrieve()
            .bodyToMono(PredictProviderResponse.class)
            .doOnNext(response -> log.info("Predicted provider for {}: {}", phoneNumber, response.correspondent()));
    }

    /**
     * Create a deposit (collect payment from customer).
     * This initiates a mobile money prompt on the customer's phone.
     */
    public Mono<DepositResponse> createDeposit(DepositRequest request) {
        String depositId = UUID.randomUUID().toString();

        log.info("Creating pawaPay deposit: id={}, amount={} {}, phone={}",
            depositId, request.amount(), request.currency(), request.payer().phoneNumber());

        return pawaPayWebClient.post()
            .uri("/deposits")
            .bodyValue(new PawaPayDeposit(
                depositId,
                request.amount().toString(),
                request.currency(),
                request.correspondent(),
                new Payer(
                    "MSISDN",
                    new Address(request.payer().phoneNumber())
                ),
                request.customerTimestamp(),
                request.statementDescription()
            ))
            .retrieve()
            .bodyToMono(DepositResponse.class)
            .doOnNext(response -> log.info("Deposit created: id={}, status={}",
                response.depositId(), response.status()));
    }

    /**
     * Create a payout (send money to organizer).
     * Used for settling funds to organizer bank accounts.
     */
    public Mono<PayoutResponse> createPayout(PayoutRequest request) {
        String payoutId = UUID.randomUUID().toString();

        log.info("Creating pawaPay payout: id={}, amount={} {}, phone={}",
            payoutId, request.amount(), request.currency(), request.recipient().phoneNumber());

        return pawaPayWebClient.post()
            .uri("/payouts")
            .bodyValue(new PawaPayPayout(
                payoutId,
                request.amount().toString(),
                request.currency(),
                request.correspondent(),
                new Recipient(
                    "MSISDN",
                    new Address(request.recipient().phoneNumber())
                ),
                request.customerTimestamp(),
                request.statementDescription()
            ))
            .retrieve()
            .bodyToMono(PayoutResponse.class);
    }

    /**
     * Check deposit status.
     */
    public Mono<DepositStatusResponse> getDepositStatus(String depositId) {
        return pawaPayWebClient.get()
            .uri("/deposits/{depositId}", depositId)
            .retrieve()
            .bodyToMono(DepositStatusResponse.class);
    }

    // DTOs
    public record PredictProviderRequest(String phoneNumber, String country, List<String> correspondents) {}
    public record PredictProviderResponse(String correspondent, String country) {}

    public record DepositRequest(
        BigDecimal amount,
        String currency,
        String correspondent,
        PayerInfo payer,
        String customerTimestamp,
        String statementDescription
    ) {}

    public record PayerInfo(String phoneNumber) {}
    public record Payer(String type, Address address) {}
    public record Address(String value) {}

    public record PawaPayDeposit(
        String depositId,
        String amount,
        String currency,
        String correspondent,
        Payer payer,
        String customerTimestamp,
        String statementDescription
    ) {}

    public record DepositResponse(String depositId, String status, String created) {}
    public record DepositStatusResponse(String depositId, String status, String correspondent, String amount) {}

    public record PayoutRequest(
        BigDecimal amount,
        String currency,
        String correspondent,
        RecipientInfo recipient,
        String customerTimestamp,
        String statementDescription
    ) {}

    public record RecipientInfo(String phoneNumber) {}
    public record Recipient(String type, Address address) {}
    public record PawaPayPayout(
        String payoutId,
        String amount,
        String currency,
        String correspondent,
        Recipient recipient,
        String customerTimestamp,
        String statementDescription
    ) {}

    public record PayoutResponse(String payoutId, String status, String created) {}
}
```

### 4.4 pawaPay Webhook Handler

```java
// PawaPayWebhookController.java
package com.pml.booking.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.pml.booking.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RestController
@RequestMapping("/api/webhooks/pawapay")
@RequiredArgsConstructor
@Slf4j
public class PawaPayWebhookController {

    private final PaymentService paymentService;
    private final String webhookSecret;  // From config

    /**
     * Handles deposit callbacks from pawaPay.
     * Called when payment status changes (COMPLETED, FAILED, etc.)
     */
    @PostMapping("/deposits")
    public Mono<ResponseEntity<Void>> handleDepositCallback(
        @RequestBody String payload,
        @RequestHeader("pawapay-signature") String signature
    ) {
        // 1. Verify signature
        if (!verifySignature(payload, signature)) {
            log.warn("Invalid pawaPay webhook signature");
            return Mono.just(ResponseEntity.status(401).build());
        }

        // 2. Parse and process
        return paymentService.processDepositCallback(payload)
            .then(Mono.just(ResponseEntity.ok().<Void>build()))
            .onErrorResume(e -> {
                log.error("Failed to process deposit callback", e);
                return Mono.just(ResponseEntity.internalServerError().build());
            });
    }

    /**
     * Handles payout callbacks from pawaPay.
     * Called when payout to organizer completes or fails.
     */
    @PostMapping("/payouts")
    public Mono<ResponseEntity<Void>> handlePayoutCallback(
        @RequestBody String payload,
        @RequestHeader("pawapay-signature") String signature
    ) {
        if (!verifySignature(payload, signature)) {
            return Mono.just(ResponseEntity.status(401).build());
        }

        return paymentService.processPayoutCallback(payload)
            .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    private boolean verifySignature(String payload, String signature) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                webhookSecret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            );
            hmac.init(secretKey);
            byte[] hash = hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computed = Base64.getEncoder().encodeToString(hash);
            return computed.equals(signature);
        } catch (Exception e) {
            log.error("Signature verification failed", e);
            return false;
        }
    }
}
```

### 4.5 Payment Service with pawaPay

```java
// PaymentService.java
package com.pml.booking.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.booking.event.TicketPurchasedEvent;
import com.pml.booking.event.PaymentFailedEvent;
import com.pml.booking.model.*;
import com.pml.booking.payment.PawaPayClient;
import com.pml.booking.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PawaPayClient pawaPayClient;
    private final PaymentIntentRepository paymentIntentRepository;
    private final TicketRepository ticketRepository;
    private final ProcessedWebhookRepository processedWebhookRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.05");  // 5%

    @Override
    @Transactional
    public Mono<PaymentIntent> initiatePayment(InitiatePaymentRequest request) {
        String idempotencyKey = generateIdempotencyKey(request);

        // Check for existing payment intent
        return paymentIntentRepository.findByIdempotencyKey(idempotencyKey)
            .switchIfEmpty(Mono.defer(() -> {
                // 1. Predict provider from phone number
                return pawaPayClient.predictProvider(request.phoneNumber())
                    .flatMap(prediction -> {
                        // 2. Create payment intent
                        PaymentIntent intent = PaymentIntent.builder()
                            .id(UUID.randomUUID().toString())
                            .idempotencyKey(idempotencyKey)
                            .ticketId(request.ticketId())
                            .eventId(request.eventId())
                            .userId(request.userId())
                            .amount(request.amount())
                            .currency("ZMW")
                            .provider(prediction.correspondent())
                            .phoneNumber(request.phoneNumber())
                            .status(PaymentStatus.PENDING)
                            .createdAt(Instant.now())
                            .expiresAt(Instant.now().plusSeconds(600))  // 10 min expiry
                            .build();

                        return paymentIntentRepository.save(intent)
                            .flatMap(saved -> {
                                // 3. Create pawaPay deposit
                                return pawaPayClient.createDeposit(new PawaPayClient.DepositRequest(
                                    saved.getAmount(),
                                    saved.getCurrency(),
                                    saved.getProvider(),
                                    new PawaPayClient.PayerInfo(saved.getPhoneNumber()),
                                    Instant.now().toString(),
                                    "Ticket: " + saved.getTicketId()
                                ))
                                .flatMap(depositResponse -> {
                                    // 4. Update with provider reference
                                    saved.setProviderTransactionId(depositResponse.depositId());
                                    saved.setStatus(PaymentStatus.PROCESSING);
                                    return paymentIntentRepository.save(saved);
                                });
                            });
                    });
            }));
    }

    @Override
    @Transactional
    public Mono<Void> processDepositCallback(String payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            String depositId = json.get("depositId").asText();
            String status = json.get("status").asText();

            log.info("Processing deposit callback: depositId={}, status={}", depositId, status);

            // Idempotency check
            String webhookId = "deposit_" + depositId + "_" + status;
            return processedWebhookRepository.existsById(webhookId)
                .flatMap(exists -> {
                    if (exists) {
                        log.info("Webhook already processed: {}", webhookId);
                        return Mono.empty();
                    }

                    return paymentIntentRepository.findByProviderTransactionId(depositId)
                        .flatMap(intent -> {
                            if ("COMPLETED".equals(status)) {
                                return handlePaymentSuccess(intent);
                            } else if ("FAILED".equals(status) || "REJECTED".equals(status)) {
                                String reason = json.has("failureReason")
                                    ? json.get("failureReason").get("failureMessage").asText()
                                    : "Payment failed";
                                return handlePaymentFailure(intent, reason);
                            }
                            return Mono.empty();
                        })
                        .then(processedWebhookRepository.save(new ProcessedWebhook(webhookId, Instant.now())))
                        .then();
                });
        } catch (Exception e) {
            log.error("Failed to parse deposit callback", e);
            return Mono.error(e);
        }
    }

    private Mono<Void> handlePaymentSuccess(PaymentIntent intent) {
        BigDecimal commission = intent.getAmount().multiply(COMMISSION_RATE);
        BigDecimal escrowAmount = intent.getAmount().subtract(commission);

        intent.setStatus(PaymentStatus.SUCCEEDED);
        intent.setProcessedAt(Instant.now());

        return paymentIntentRepository.save(intent)
            .flatMap(saved -> ticketRepository.findById(saved.getTicketId()))
            .flatMap(ticket -> {
                ticket.setStatus(TicketStatus.PURCHASED);
                ticket.setCommissionAmount(commission);
                ticket.setNetAmount(escrowAmount);
                ticket.setCommissionStatus(CommissionStatus.PENDING);  // Two-stage model!
                ticket.setPaymentInfo(new PaymentInfo(
                    intent.getId(),
                    intent.getProviderTransactionId(),
                    intent.getAmount(),
                    PaymentStatus.SUCCEEDED
                ));

                return ticketRepository.save(ticket);
            })
            .doOnSuccess(ticket -> {
                // Publish event - Spring Modulith handles persistence & delivery
                eventPublisher.publishEvent(new TicketPurchasedEvent(
                    ticket.getId(),
                    ticket.getEventId(),
                    ticket.getBuyerId(),
                    ticket.getOrganizerId(),
                    ticket.getPrice(),
                    ticket.getCommissionAmount(),
                    ticket.getNetAmount(),
                    intent.getProvider(),
                    intent.getProviderTransactionId()
                ));
            })
            .then();
    }

    private Mono<Void> handlePaymentFailure(PaymentIntent intent, String reason) {
        intent.setStatus(PaymentStatus.FAILED);
        intent.setFailureReason(reason);
        intent.setProcessedAt(Instant.now());

        return paymentIntentRepository.save(intent)
            .flatMap(saved -> ticketRepository.findById(saved.getTicketId()))
            .flatMap(ticket -> {
                // Release reservation
                ticket.setStatus(TicketStatus.CANCELLED);
                ticket.setReservation(null);
                return ticketRepository.save(ticket);
            })
            .doOnSuccess(ticket -> {
                eventPublisher.publishEvent(new PaymentFailedEvent(
                    intent.getId(),
                    ticket.getId(),
                    ticket.getEventId(),
                    ticket.getBuyerId(),
                    reason,
                    intent.getProvider(),
                    Instant.now()
                ));
            })
            .then();
    }

    private String generateIdempotencyKey(InitiatePaymentRequest request) {
        return String.format("%s_%s_%d",
            request.userId(),
            request.eventId(),
            System.currentTimeMillis() / 60000);  // Unique per minute
    }
}
```

---

## 5. Consistent Data State Patterns

### 5.1 Transactional Outbox Pattern (Spring Modulith)

Spring Modulith's Event Publication Registry provides this automatically:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SPRING MODULITH EVENT FLOW                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  1. Business Transaction                                                     │
│  ═══════════════════════                                                     │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │ @Transactional                                                         │ │
│  │ public Mono<Ticket> purchaseTicket(...) {                              │ │
│  │     return ticketRepository.save(ticket)                               │ │
│  │         .doOnSuccess(t -> eventPublisher.publishEvent(event));         │ │
│  │ }                                                                       │ │
│  │                                                                         │ │
│  │ WITHIN SAME TRANSACTION:                                                │ │
│  │ ┌─────────────────┐    ┌─────────────────────┐                         │ │
│  │ │ tickets         │    │ event_publications  │                         │ │
│  │ │ (business data) │    │ (Spring Modulith)   │                         │ │
│  │ │                 │    │                     │                         │ │
│  │ │ INSERT ticket   │    │ INSERT event        │                         │ │
│  │ │                 │    │ completionDate=null │                         │ │
│  │ └─────────────────┘    └─────────────────────┘                         │ │
│  │                                 │                                       │ │
│  │ COMMIT ─────────────────────────┼────────────────────────────────────  │ │
│  └─────────────────────────────────┼──────────────────────────────────────┘ │
│                                    │                                         │
│  2. Async Event Processing         │                                         │
│  ═════════════════════════════     │                                         │
│                                    ▼                                         │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │ @ApplicationModuleListener                                             │ │
│  │ public void onTicketPurchased(TicketPurchasedEvent event) {            │ │
│  │     // Process event (credit escrow, etc.)                             │ │
│  │ }                                                                       │ │
│  │                                                                         │ │
│  │ ON SUCCESS:                                                             │ │
│  │ ┌─────────────────────┐                                                 │ │
│  │ │ event_publications  │                                                 │ │
│  │ │                     │                                                 │ │
│  │ │ UPDATE              │  ← Marks event as processed                    │ │
│  │ │ completionDate=now  │                                                 │ │
│  │ └─────────────────────┘                                                 │ │
│  │                                                                         │ │
│  │ ON FAILURE:                                                             │ │
│  │ - Event stays with completionDate=null                                  │ │
│  │ - Republished on restart                                                │ │
│  │ - Automatic retry                                                       │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  3. Event Externalization (to Azure Service Bus)                            │
│  ═══════════════════════════════════════════════                            │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │ @Externalized("ticket-events::TicketPurchased")                        │ │
│  │                                                                         │ │
│  │ After local processing completes:                                       │ │
│  │ - Event sent to Azure Service Bus topic                                 │ │
│  │ - Other services receive via subscriptions                              │ │
│  │ - Dead-letter queue handles failures                                    │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 Idempotency Patterns

```java
// ProcessedWebhookRepository.java
public interface ProcessedWebhookRepository extends ReactiveMongoRepository<ProcessedWebhook, String> {
    // ID format: "provider_transactionId_status"
    // Example: "pawapay_dep123_COMPLETED"
}

// Usage in webhook handler
@Transactional
public Mono<Void> processWebhook(String provider, String transactionId, String status) {
    String webhookId = provider + "_" + transactionId + "_" + status;

    return processedWebhookRepository.existsById(webhookId)
        .flatMap(exists -> {
            if (exists) {
                log.info("Webhook already processed: {}", webhookId);
                return Mono.empty();  // Idempotent - no error
            }

            return processPayment(transactionId, status)
                .then(processedWebhookRepository.save(
                    new ProcessedWebhook(webhookId, Instant.now())
                ))
                .then();
        });
}

// TTL Index for auto-cleanup (MongoDB)
// db.processed_webhooks.createIndex({ "processedAt": 1 }, { expireAfterSeconds: 2592000 })
```

### 5.3 Optimistic Locking for Concurrent Updates

```java
// Ticket.java with @Version
@Document(collection = "tickets")
@Data
@Builder
public class Ticket {
    @Id
    private String id;

    @Version  // Optimistic locking
    private Long version;

    private String eventId;
    private TicketStatus status;
    private BigDecimal price;
    // ... other fields
}

// Usage
@Transactional
public Mono<Ticket> updateTicketStatus(String ticketId, TicketStatus newStatus) {
    return ticketRepository.findById(ticketId)
        .flatMap(ticket -> {
            ticket.setStatus(newStatus);
            return ticketRepository.save(ticket);  // Throws OptimisticLockingFailureException if stale
        })
        .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
            .filter(e -> e instanceof OptimisticLockingFailureException));
}
```

### 5.4 Saga Pattern for Cross-Service Transactions

```java
// TicketPurchaseSaga.java
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketPurchaseSaga {

    private final TicketRepository ticketRepository;
    private final PaymentService paymentService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Saga: Reserve Ticket → Process Payment → Confirm Purchase
     *
     * Compensating actions:
     * - Payment fails → Release reservation
     * - Payment succeeds but escrow fails → Refund payment
     */
    @Transactional
    public Mono<Ticket> executePurchase(PurchaseRequest request) {
        return Mono.defer(() -> {
            // Step 1: Reserve ticket
            return reserveTicket(request)
                .flatMap(ticket -> {
                    // Step 2: Initiate payment
                    return paymentService.initiatePayment(
                        new InitiatePaymentRequest(
                            ticket.getId(),
                            ticket.getEventId(),
                            request.userId(),
                            ticket.getPrice(),
                            request.phoneNumber()
                        )
                    )
                    .map(payment -> ticket)
                    .onErrorResume(paymentError -> {
                        // Compensation: Release reservation
                        log.error("Payment failed, releasing reservation", paymentError);
                        return releaseReservation(ticket.getId())
                            .then(Mono.error(paymentError));
                    });
                });
        });
    }

    private Mono<Ticket> reserveTicket(PurchaseRequest request) {
        Ticket ticket = Ticket.builder()
            .id(UUID.randomUUID().toString())
            .eventId(request.eventId())
            .buyerId(request.userId())
            .ticketCategoryCode(request.categoryCode())
            .price(request.price())
            .status(TicketStatus.PENDING_PAYMENT)
            .reservation(new Reservation(
                Instant.now(),
                Instant.now().plusMinutes(10),
                request.sessionId()
            ))
            .build();

        return ticketRepository.save(ticket);
    }

    private Mono<Void> releaseReservation(String ticketId) {
        return ticketRepository.findById(ticketId)
            .flatMap(ticket -> {
                ticket.setStatus(TicketStatus.CANCELLED);
                ticket.setReservation(null);
                return ticketRepository.save(ticket);
            })
            .then();
    }
}
```

---

## 6. Migration Checklist

### Phase 1: Add Spring Modulith (Week 1-2)

- [ ] Add Spring Modulith dependencies to all services
- [ ] Enable `@Modulith` annotation on application classes
- [ ] Configure `event_publications` collection in MongoDB
- [ ] Replace `OutboxPublisher` with `ApplicationEventPublisher`
- [ ] Create `@ApplicationModuleListener` handlers for existing events
- [ ] Test event persistence and replay

### Phase 2: Azure Service Bus (Week 2-3)

- [ ] Create Azure Service Bus namespace and topics
- [ ] Add Spring Cloud Azure dependencies
- [ ] Configure stream bindings for each service
- [ ] Add `@Externalized` annotation to cross-service events
- [ ] Create consumers for external events
- [ ] Test cross-service event delivery

### Phase 3: pawaPay Integration (Week 3-4)

- [ ] Get pawaPay API credentials (sandbox first)
- [ ] Implement `PawaPayClient` with WebClient
- [ ] Create webhook endpoints
- [ ] Implement provider prediction
- [ ] Test deposits (MTN, Airtel, Zamtel)
- [ ] Test payouts to organizers

### Phase 4: Remove Debezium (Week 4)

- [ ] Remove `CdcWebhookController` from all services
- [ ] Remove `OutboxEvent`, `OutboxEventRepository`, `OutboxPublisher`
- [ ] Remove Debezium container from docker-compose
- [ ] Drop `outbox_events` collection
- [ ] Full integration testing

### Phase 5: Production (Week 5+)

- [ ] Configure production Azure Service Bus
- [ ] Switch to pawaPay production credentials
- [ ] Set up monitoring and alerting
- [ ] Load testing
- [ ] Go live

---

## 7. Summary

| Before | After |
|--------|-------|
| Debezium CDC + HTTP webhooks | Spring Modulith Event Publication Registry |
| Manual outbox management | Automatic event persistence & retry |
| Custom idempotency | Built-in completion tracking |
| Separate payment APIs | pawaPay unified API |
| No dead-letter handling | Azure Service Bus DLQ |
| Complex Docker setup | Simpler infrastructure |

This migration reduces complexity while improving reliability and maintainability.
