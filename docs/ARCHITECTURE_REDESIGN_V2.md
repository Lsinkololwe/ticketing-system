# Event Ticketing System - Architecture Redesign V2

## Production-Grade Design for Data Integrity, No Double-Booking & Safe Payment Integration

**Version:** 2.0
**Date:** February 2026
**Stack:** Spring Boot 3.x, Spring WebFlux, Spring Modulith, MongoDB (Replica Set), Azure Service Bus

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Architecture Overview](#2-architecture-overview)
3. [Core Design Principles](#3-core-design-principles)
4. [MongoDB Collections Design](#4-mongodb-collections-design)
5. [Spring Modulith Configuration](#5-spring-modulith-configuration)
6. [Azure Service Bus Integration](#6-azure-service-bus-integration)
7. [Double-Booking Prevention](#7-double-booking-prevention)
8. [Payment Integration Flow](#8-payment-integration-flow)
9. [Event Flow & Guaranteed Delivery](#9-event-flow--guaranteed-delivery)
10. [Error Handling & Recovery](#10-error-handling--recovery)
11. [Implementation Phases](#11-implementation-phases)
12. [Testing Strategy](#12-testing-strategy)
13. [Monitoring & Observability](#13-monitoring--observability)

---

## 1. Executive Summary

### Problems Solved

| Problem | Solution |
|---------|----------|
| Double-booking | MongoDB atomic `findAndModify` with status condition |
| Lost events | Spring Modulith Event Publication Registry (persisted to MongoDB) |
| Payment inconsistency | Idempotency keys + Webhook + Polling dual strategy |
| CDC complexity | **Eliminated** - Spring Modulith replaces Debezium |
| Dual-write problem | Single atomic transaction + event publication table |
| External service failures | Dead Letter Queue + Retry with exponential backoff |

### Key Technology Decisions

| Component | Choice | Rationale |
|-----------|--------|-----------|
| **Event Handling** | Spring Modulith | Transactional outbox built-in, no Debezium needed |
| **Message Broker** | Azure Service Bus | Pay-per-use, managed, DLQ support |
| **Database** | MongoDB Replica Set | ACID transactions, atomic operations |
| **API Style** | Reactive (WebFlux) | Non-blocking I/O for payment polling |
| **Consistency** | Eventual (with guarantees) | At-least-once delivery, idempotent consumers |

---

## 2. Architecture Overview

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                           EVENT TICKETING SYSTEM V2                                  │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                      │
│  ┌─────────────┐     ┌─────────────────────────────────────────────────────────┐   │
│  │   Mobile    │     │                   API GATEWAY                            │   │
│  │    App      │────▶│  (Rate Limiting, Auth, Request Validation)              │   │
│  │  (Expo)     │     │                                                          │   │
│  └─────────────┘     └───────────────────────┬─────────────────────────────────┘   │
│                                               │                                      │
│  ┌─────────────┐                             │                                      │
│  │    Web      │─────────────────────────────┤                                      │
│  │  (Next.js)  │                             │                                      │
│  └─────────────┘                             ▼                                      │
│                      ┌───────────────────────────────────────────────────────────┐  │
│                      │              BOOKING SERVICE (Spring Modulith)            │  │
│                      │  ┌─────────────────────────────────────────────────────┐ │  │
│                      │  │                    MODULES                           │ │  │
│                      │  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐│ │  │
│                      │  │  │ Booking  │ │ Payment  │ │ Escrow   │ │Notific- ││ │  │
│                      │  │  │ Module   │ │ Module   │ │ Module   │ │ation    ││ │  │
│                      │  │  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬────┘│ │  │
│                      │  │       │            │            │            │      │ │  │
│                      │  │       └────────────┴────────────┴────────────┘      │ │  │
│                      │  │                         │                            │ │  │
│                      │  │              ApplicationEventPublisher               │ │  │
│                      │  │                         │                            │ │  │
│                      │  │              ┌──────────▼──────────┐                │ │  │
│                      │  │              │  Event Publication  │                │ │  │
│                      │  │              │     Registry        │                │ │  │
│                      │  │              │  (MongoDB Table)    │                │ │  │
│                      │  │              └──────────┬──────────┘                │ │  │
│                      │  └────────────────────────┼────────────────────────────┘ │  │
│                      └───────────────────────────┼──────────────────────────────┘  │
│                                                  │                                  │
│                                                  ▼                                  │
│                      ┌───────────────────────────────────────────────────────────┐  │
│                      │                 AZURE SERVICE BUS                         │  │
│                      │  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────┐ │  │
│                      │  │payment-events│ │ticket-events│ │ Dead Letter Queues │ │  │
│                      │  │   (Topic)   │ │   (Topic)   │ │   (Auto-created)   │ │  │
│                      │  └──────┬──────┘ └──────┬──────┘ └─────────────────────┘ │  │
│                      └─────────┼───────────────┼────────────────────────────────┘  │
│                                │               │                                    │
│                 ┌──────────────┴───┐     ┌─────┴──────────────┐                    │
│                 ▼                  ▼     ▼                    ▼                    │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐                 │
│  │ CATALOG SERVICE  │  │ IDENTITY SERVICE │  │ ANALYTICS SERVICE│                 │
│  │ (Event Consumer) │  │ (Event Consumer) │  │ (Event Consumer) │                 │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘                 │
│                                                                                      │
│  ┌──────────────────────────────────────────────────────────────────────────────┐  │
│  │                        EXTERNAL PAYMENT PROVIDERS                             │  │
│  │   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐                      │  │
│  │   │ MTN Mobile  │    │   Airtel    │    │   Zamtel    │                      │  │
│  │   │   Money     │    │   Money     │    │   Kwacha    │                      │  │
│  │   └──────┬──────┘    └──────┬──────┘    └──────┬──────┘                      │  │
│  │          │                  │                  │                              │  │
│  │          └──────────────────┴──────────────────┘                              │  │
│  │                             │                                                  │  │
│  │                    Webhooks │ (Signed)                                        │  │
│  │                             ▼                                                  │  │
│  │          ┌──────────────────────────────────┐                                 │  │
│  │          │  BOOKING SERVICE WEBHOOK HANDLER │                                 │  │
│  │          │  /webhooks/payments/{provider}   │                                 │  │
│  │          └──────────────────────────────────┘                                 │  │
│  └──────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                      │
│  ┌──────────────────────────────────────────────────────────────────────────────┐  │
│  │                           MONGODB REPLICA SET                                 │  │
│  │   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐                      │  │
│  │   │   mongo1    │    │   mongo2    │    │   mongo3    │                      │  │
│  │   │  (PRIMARY)  │◄──▶│ (SECONDARY) │◄──▶│ (SECONDARY) │                      │  │
│  │   └─────────────┘    └─────────────┘    └─────────────┘                      │  │
│  │                                                                               │  │
│  │   Collections:                                                                │  │
│  │   • tickets              • payment_intents       • event_publications        │  │
│  │   • events (catalog)     • escrow_accounts       • processed_events          │  │
│  │   • reservations         • financial_txns        • processed_webhooks        │  │
│  └──────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Why No Debezium?

Spring Modulith's Event Publication Registry eliminates the need for CDC:

```
BEFORE (Complex):                          AFTER (Simple):
┌─────────────────────┐                   ┌─────────────────────┐
│ Business Logic      │                   │ Business Logic      │
│   │                 │                   │   │                 │
│   ▼                 │                   │   ▼                 │
│ Save to MongoDB ────┼───┐               │ @Transactional      │
│   │                 │   │               │ Save + Publish Event│
│   ▼                 │   │               │   │                 │
│ Save OutboxEvent    │   │               │   ▼                 │
└─────────────────────┘   │               │ Event Publication   │
                          │               │ Registry (auto)     │
┌─────────────────────┐   │               └─────────────────────┘
│ Debezium CDC        │◄──┘                         │
│   │                 │                             ▼
│   ▼                 │               ┌─────────────────────────┐
│ Kafka Connect       │               │ @ApplicationModuleListener
│   │                 │               │ (internal modules)      │
│   ▼                 │               └─────────────────────────┘
│ Kafka Topic         │                             │
│   │                 │                             ▼
│   ▼                 │               ┌─────────────────────────┐
│ Consumer Service    │               │ @Externalized           │
└─────────────────────┘               │ (Azure Service Bus)     │
                                      └─────────────────────────┘

Complexity: HIGH                       Complexity: LOW
Components: 5                          Components: 2
Failure Points: Many                   Failure Points: Few
```

---

## 3. Core Design Principles

### 3.1 Consistency Guarantees

| Level | Guarantee | Mechanism |
|-------|-----------|-----------|
| **Ticket Reservation** | Strong (ACID) | MongoDB atomic `findAndModify` |
| **Event Publishing** | At-least-once | Spring Modulith Event Publication Registry |
| **Cross-Service** | Eventual | Idempotent consumers + processed_events table |
| **Payment** | Exactly-once (semantic) | Idempotency key + webhook deduplication |

### 3.2 Idempotency Rules

```
┌─────────────────────────────────────────────────────────────────┐
│                    IDEMPOTENCY LAYERS                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  LAYER 1: Client Request                                        │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ • Mobile app generates idempotencyKey per booking attempt   ││
│  │ • Stored in AsyncStorage until payment completes            ││
│  │ • Same key = same response (no duplicate charge)            ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  LAYER 2: Payment Provider                                      │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ • Our transactionRef sent to provider                       ││
│  │ • Provider uses it for their idempotency                    ││
│  │ • Duplicate call = same payment, no double charge           ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  LAYER 3: Webhook Processing                                    │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ • processed_webhooks collection tracks webhook IDs          ││
│  │ • Duplicate webhook = 200 OK, no reprocessing               ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  LAYER 4: Event Consumption                                     │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ • processed_events collection in each consumer service      ││
│  │ • Check before processing, insert after                     ││
│  │ • Duplicate event = skip processing                         ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. MongoDB Collections Design

### 4.1 Collection: `tickets`

```javascript
// tickets collection
{
  "_id": ObjectId("..."),
  "eventId": ObjectId("..."),           // Reference to events collection
  "ticketTypeId": ObjectId("..."),      // Reference to ticket_types

  // Ticket Details
  "ticketNumber": "EVT-2026-001-0042",  // Human-readable, unique
  "section": "VIP",
  "row": "A",
  "seat": "12",
  "price": NumberDecimal("250.00"),
  "currency": "ZMW",

  // Status Management (CRITICAL for double-booking prevention)
  "status": "AVAILABLE",                 // AVAILABLE | RESERVED | SOLD | CANCELLED
  "statusHistory": [
    {
      "status": "AVAILABLE",
      "timestamp": ISODate("2026-02-01T10:00:00Z"),
      "reason": "Initial creation"
    }
  ],

  // Reservation (when status = RESERVED)
  "reservation": {
    "userId": ObjectId("..."),
    "reservedAt": ISODate("..."),
    "expiresAt": ISODate("..."),         // 10 minutes from reservation
    "paymentIntentId": ObjectId("...")
  },

  // Sale (when status = SOLD)
  "sale": {
    "userId": ObjectId("..."),
    "soldAt": ISODate("..."),
    "paymentIntentId": ObjectId("..."),
    "finalPrice": NumberDecimal("250.00")
  },

  // Optimistic Locking
  "version": NumberLong(0),              // @Version - CRITICAL

  // Timestamps
  "createdAt": ISODate("..."),
  "updatedAt": ISODate("...")
}

// Indexes
db.tickets.createIndex({ "eventId": 1, "status": 1 })
db.tickets.createIndex({ "ticketNumber": 1 }, { unique: true })
db.tickets.createIndex({ "reservation.expiresAt": 1 }, { expireAfterSeconds: 0, partialFilterExpression: { status: "RESERVED" } })
db.tickets.createIndex({ "status": 1, "reservation.expiresAt": 1 })  // For cleanup job
```

### 4.2 Collection: `payment_intents`

```javascript
// payment_intents collection
{
  "_id": ObjectId("..."),

  // Idempotency (CRITICAL)
  "idempotencyKey": "mobile_user123_ticket456_1709123456789",  // Unique
  "transactionRef": "TXN-2026022812345678",                    // Our reference to provider
  "providerTransactionId": "MTN-ABC123XYZ",                    // Provider's reference

  // References
  "ticketId": ObjectId("..."),
  "userId": ObjectId("..."),
  "eventId": ObjectId("..."),

  // Amount
  "amount": NumberDecimal("250.00"),
  "currency": "ZMW",
  "fees": {
    "provider": NumberDecimal("5.00"),
    "platform": NumberDecimal("12.50"),
    "total": NumberDecimal("17.50")
  },

  // Provider
  "provider": "MTN_MOMO",               // MTN_MOMO | AIRTEL_MONEY | ZAMTEL_KWACHA
  "providerMetadata": {
    "phoneNumber": "+260971234567",
    "ussdCode": "*303*1*123456#"
  },

  // Status (State Machine)
  "status": "PENDING",                   // PENDING | PROCESSING | SUCCEEDED | FAILED | EXPIRED | REFUNDED
  "statusHistory": [
    {
      "status": "PENDING",
      "timestamp": ISODate("..."),
      "source": "SYSTEM"                 // SYSTEM | WEBHOOK | POLLING
    }
  ],
  "failureReason": null,
  "failureCode": null,

  // Tracking
  "webhookAttempts": 0,
  "pollAttempts": 0,
  "lastPolledAt": null,

  // Timing
  "createdAt": ISODate("..."),
  "expiresAt": ISODate("..."),           // 10 minutes
  "processedAt": null,

  // Optimistic Locking
  "version": NumberLong(0)
}

// Indexes
db.payment_intents.createIndex({ "idempotencyKey": 1 }, { unique: true })
db.payment_intents.createIndex({ "transactionRef": 1 }, { unique: true })
db.payment_intents.createIndex({ "ticketId": 1 })
db.payment_intents.createIndex({ "userId": 1 })
db.payment_intents.createIndex({ "status": 1, "createdAt": 1 })  // For polling job
db.payment_intents.createIndex({ "status": 1, "lastPolledAt": 1 })
```

### 4.3 Collection: `escrow_accounts`

```javascript
// escrow_accounts collection
{
  "_id": ObjectId("..."),
  "eventId": ObjectId("..."),
  "organizerId": ObjectId("..."),

  // Balances (with optimistic locking for safe updates)
  "pendingBalance": NumberDecimal("5000.00"),      // Awaiting event completion
  "availableBalance": NumberDecimal("0.00"),       // Ready for payout
  "totalReceived": NumberDecimal("5000.00"),
  "totalPaidOut": NumberDecimal("0.00"),
  "totalRefunded": NumberDecimal("0.00"),

  // Status
  "status": "ACTIVE",                               // ACTIVE | FROZEN | RELEASED | CLOSED

  // Double-entry ledger
  "transactions": [
    {
      "id": ObjectId("..."),
      "type": "CREDIT",                             // CREDIT | DEBIT
      "category": "TICKET_SALE",                    // TICKET_SALE | REFUND | PAYOUT | FEE
      "amount": NumberDecimal("250.00"),
      "paymentIntentId": ObjectId("..."),
      "ticketId": ObjectId("..."),
      "balanceAfter": NumberDecimal("5000.00"),
      "timestamp": ISODate("..."),
      "description": "Ticket sale: EVT-2026-001-0042"
    }
  ],

  // Optimistic Locking
  "version": NumberLong(0),

  "createdAt": ISODate("..."),
  "updatedAt": ISODate("...")
}

// Indexes
db.escrow_accounts.createIndex({ "eventId": 1 }, { unique: true })
db.escrow_accounts.createIndex({ "organizerId": 1 })
db.escrow_accounts.createIndex({ "status": 1 })
```

### 4.4 Collection: `event_publications` (Spring Modulith)

```javascript
// event_publications collection (auto-managed by Spring Modulith)
{
  "_id": ObjectId("..."),
  "event": {                              // Serialized event
    "_class": "com.ticketing.booking.events.PaymentSucceededEvent",
    "paymentIntentId": "...",
    "ticketId": "...",
    "userId": "...",
    "amount": 250.00
  },
  "listenerId": "com.ticketing.booking.listeners.NotificationListener.onPaymentSucceeded",
  "publicationDate": ISODate("..."),
  "completionDate": null,                 // Null until processed

  // Status tracking
  "status": "PENDING"                     // PENDING | COMPLETED | FAILED
}

// Indexes (created by Spring Modulith)
db.event_publications.createIndex({ "completionDate": 1 })
db.event_publications.createIndex({ "publicationDate": 1 })
```

### 4.5 Collection: `processed_webhooks`

```javascript
// processed_webhooks collection (idempotency for webhooks)
{
  "_id": "webhook_mtn_abc123xyz",         // webhookId from provider
  "provider": "MTN_MOMO",
  "transactionRef": "TXN-2026022812345678",
  "receivedAt": ISODate("..."),
  "processedAt": ISODate("..."),
  "outcome": "SUCCEEDED"
}

// TTL Index - auto-delete after 30 days
db.processed_webhooks.createIndex({ "processedAt": 1 }, { expireAfterSeconds: 2592000 })
```

### 4.6 Collection: `processed_events` (Consumer Idempotency)

```javascript
// processed_events collection (in each consumer service)
{
  "_id": "PaymentSucceededEvent_pi_123456",  // eventType + identifier
  "eventType": "PaymentSucceededEvent",
  "eventId": "pi_123456",
  "processedAt": ISODate("..."),
  "processingService": "catalog-service"
}

// TTL Index - auto-delete after 7 days
db.processed_events.createIndex({ "processedAt": 1 }, { expireAfterSeconds: 604800 })
```

---

## 5. Spring Modulith Configuration

### 5.1 Project Structure

```
booking-service/
├── src/main/java/com/ticketing/booking/
│   ├── BookingApplication.java
│   │
│   ├── booking/                          # Booking Module
│   │   ├── Booking.java
│   │   ├── BookingController.java
│   │   ├── BookingService.java
│   │   ├── BookingRepository.java
│   │   ├── events/
│   │   │   ├── TicketReservedEvent.java
│   │   │   └── TicketSoldEvent.java
│   │   └── package-info.java
│   │
│   ├── payment/                          # Payment Module
│   │   ├── PaymentIntent.java
│   │   ├── PaymentController.java
│   │   ├── PaymentService.java
│   │   ├── PaymentWebhookController.java
│   │   ├── providers/
│   │   │   ├── PaymentProvider.java
│   │   │   ├── MtnMomoProvider.java
│   │   │   ├── AirtelMoneyProvider.java
│   │   │   └── ZamtelKwachaProvider.java
│   │   ├── events/
│   │   │   ├── PaymentInitiatedEvent.java
│   │   │   ├── PaymentSucceededEvent.java
│   │   │   ├── PaymentFailedEvent.java
│   │   │   └── PaymentExpiredEvent.java
│   │   └── package-info.java
│   │
│   ├── escrow/                           # Escrow Module
│   │   ├── EscrowAccount.java
│   │   ├── EscrowService.java
│   │   ├── EscrowRepository.java
│   │   ├── listeners/
│   │   │   └── PaymentEventListener.java
│   │   └── package-info.java
│   │
│   ├── notification/                     # Notification Module
│   │   ├── NotificationService.java
│   │   ├── listeners/
│   │   │   └── PaymentNotificationListener.java
│   │   ├── sms/
│   │   │   └── SmsGateway.java
│   │   ├── push/
│   │   │   └── PushNotificationService.java
│   │   └── package-info.java
│   │
│   ├── external/                         # External Event Publishing
│   │   ├── ExternalEventPublisher.java
│   │   └── package-info.java
│   │
│   └── shared/                           # Shared Kernel
│       ├── Money.java
│       ├── IdempotencyKey.java
│       └── package-info.java
│
├── src/main/resources/
│   ├── application.yml
│   └── application-local.yml
│
└── pom.xml
```

### 5.2 Maven Dependencies

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.0</version>
    </parent>

    <groupId>com.ticketing</groupId>
    <artifactId>booking-service</artifactId>
    <version>2.0.0</version>

    <properties>
        <java.version>21</java.version>
        <spring-modulith.version>1.2.0</spring-modulith.version>
        <spring-cloud-azure.version>5.10.0</spring-cloud-azure.version>
    </properties>

    <dependencies>
        <!-- Spring Boot WebFlux (Reactive) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <!-- Spring Data MongoDB Reactive -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-mongodb-reactive</artifactId>
        </dependency>

        <!-- Spring Modulith -->
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-starter-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-starter-mongodb</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-events-api</artifactId>
        </dependency>

        <!-- Azure Service Bus (Spring Cloud Stream) -->
        <dependency>
            <groupId>com.azure.spring</groupId>
            <artifactId>spring-cloud-azure-stream-binder-servicebus</artifactId>
        </dependency>

        <!-- Spring Cloud Azure Starter -->
        <dependency>
            <groupId>com.azure.spring</groupId>
            <artifactId>spring-cloud-azure-starter</artifactId>
        </dependency>

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Observability -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-observability</artifactId>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.modulith</groupId>
                <artifactId>spring-modulith-bom</artifactId>
                <version>${spring-modulith.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>com.azure.spring</groupId>
                <artifactId>spring-cloud-azure-dependencies</artifactId>
                <version>${spring-cloud-azure.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

### 5.3 Application Configuration

```yaml
# application.yml
spring:
  application:
    name: booking-service

  # MongoDB Configuration
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://app_user:app_password@localhost:27017,localhost:27018,localhost:27019/ticketing_booking?replicaSet=rs0&authSource=admin}
      auto-index-creation: true

  # Spring Modulith Configuration
  modulith:
    events:
      # Enable event publication registry (persists to MongoDB)
      publication-registry:
        enabled: true
      # Republish incomplete events on restart
      republish-outstanding-events-on-restart: true
      # Retry incomplete publications every 60 seconds
      completion-mode: ASYNC
    # Event externalization (to Azure Service Bus)
    externalization:
      enabled: true

  # Azure Service Bus Configuration
  cloud:
    azure:
      servicebus:
        namespace: ${AZURE_SERVICEBUS_NAMESPACE}
        credential:
          managed-identity-enabled: ${AZURE_USE_MANAGED_IDENTITY:false}
        connection-string: ${AZURE_SERVICEBUS_CONNECTION_STRING:}

    stream:
      # Function bindings
      function:
        definition: paymentEventsConsumer;ticketEventsConsumer

      bindings:
        # Output: Payment events to Service Bus topic
        paymentEvents-out-0:
          destination: payment-events
          producer:
            partition-key-expression: headers['partitionKey']

        # Output: Ticket events to Service Bus topic
        ticketEvents-out-0:
          destination: ticket-events
          producer:
            partition-key-expression: headers['partitionKey']

        # Input: Consume payment events (for other services)
        paymentEventsConsumer-in-0:
          destination: payment-events
          group: booking-service

        # Input: Consume ticket events
        ticketEventsConsumer-in-0:
          destination: ticket-events
          group: booking-service

      servicebus:
        bindings:
          paymentEvents-out-0:
            producer:
              entity-type: topic
          ticketEvents-out-0:
            producer:
              entity-type: topic
          paymentEventsConsumer-in-0:
            consumer:
              auto-complete: false
              max-concurrent-calls: 5
          ticketEventsConsumer-in-0:
            consumer:
              auto-complete: false
              max-concurrent-calls: 5

# Payment Providers Configuration
payment:
  providers:
    mtn:
      api-url: ${MTN_MOMO_API_URL}
      api-key: ${MTN_MOMO_API_KEY}
      webhook-secret: ${MTN_MOMO_WEBHOOK_SECRET}
      callback-url: ${APP_BASE_URL}/webhooks/payments/mtn
    airtel:
      api-url: ${AIRTEL_MONEY_API_URL}
      api-key: ${AIRTEL_MONEY_API_KEY}
      webhook-secret: ${AIRTEL_MONEY_WEBHOOK_SECRET}
      callback-url: ${APP_BASE_URL}/webhooks/payments/airtel
    zamtel:
      api-url: ${ZAMTEL_KWACHA_API_URL}
      api-key: ${ZAMTEL_KWACHA_API_KEY}
      webhook-secret: ${ZAMTEL_KWACHA_WEBHOOK_SECRET}
      callback-url: ${APP_BASE_URL}/webhooks/payments/zamtel

# Reservation settings
reservation:
  timeout-minutes: 10
  cleanup-interval-seconds: 30

# Polling settings (fallback for missed webhooks)
polling:
  enabled: true
  interval-seconds: 30
  pending-threshold-seconds: 120
  max-attempts: 10

# Actuator & Observability
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,modulith
  metrics:
    tags:
      application: ${spring.application.name}
```

---

## 6. Azure Service Bus Integration

### 6.1 Service Bus Topics Structure

```
Azure Service Bus Namespace: ticketing-prod
├── Topics:
│   ├── payment-events
│   │   ├── Subscriptions:
│   │   │   ├── catalog-service    → Catalog Service (update inventory)
│   │   │   ├── identity-service   → Identity Service (update user purchase history)
│   │   │   ├── analytics-service  → Analytics Service (metrics)
│   │   │   └── notification-svc   → Notification Service (if separate)
│   │   └── Dead Letter Queue (auto)
│   │
│   ├── ticket-events
│   │   ├── Subscriptions:
│   │   │   ├── catalog-service
│   │   │   └── analytics-service
│   │   └── Dead Letter Queue (auto)
│   │
│   └── dlq-events (for manual DLQ processing)
│       └── Subscriptions:
│           └── dlq-processor
```

### 6.2 External Event Publisher

```java
package com.ticketing.booking.external;

import com.ticketing.booking.payment.events.*;
import com.ticketing.booking.booking.events.*;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * Publishes events to Azure Service Bus for external services.
 * Uses Spring Modulith's @ApplicationModuleListener for guaranteed delivery.
 */
@Component
public class ExternalEventPublisher {

    private final StreamBridge streamBridge;

    public ExternalEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    @ApplicationModuleListener
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        publishToServiceBus("paymentEvents-out-0", event, event.ticketId());
    }

    @ApplicationModuleListener
    public void onPaymentFailed(PaymentFailedEvent event) {
        publishToServiceBus("paymentEvents-out-0", event, event.ticketId());
    }

    @ApplicationModuleListener
    public void onTicketSold(TicketSoldEvent event) {
        publishToServiceBus("ticketEvents-out-0", event, event.ticketId());
    }

    @ApplicationModuleListener
    public void onTicketReserved(TicketReservedEvent event) {
        publishToServiceBus("ticketEvents-out-0", event, event.ticketId());
    }

    private void publishToServiceBus(String binding, Object event, String partitionKey) {
        streamBridge.send(binding,
            MessageBuilder.withPayload(event)
                .setHeader("partitionKey", partitionKey)
                .setHeader("eventType", event.getClass().getSimpleName())
                .setHeader("timestamp", System.currentTimeMillis())
                .build()
        );
    }
}
```

### 6.3 Event Consumer (for other microservices)

```java
package com.ticketing.catalog.events;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.function.Consumer;

@Configuration
public class EventConsumerConfig {

    private final CatalogService catalogService;
    private final ProcessedEventRepository processedEventRepo;

    @Bean
    public Consumer<PaymentSucceededEvent> paymentEventsConsumer() {
        return event -> {
            String eventKey = "PaymentSucceeded_" + event.paymentIntentId();

            // Idempotency check
            if (processedEventRepo.existsById(eventKey)) {
                log.info("Event {} already processed, skipping", eventKey);
                return;
            }

            // Process event
            catalogService.markTicketAsSold(event.ticketId());

            // Mark as processed
            processedEventRepo.save(new ProcessedEvent(
                eventKey,
                "PaymentSucceededEvent",
                Instant.now()
            ));
        };
    }
}
```

---

## 7. Double-Booking Prevention

### 7.1 The Atomic Reservation Pattern

```java
package com.ticketing.booking.booking;

import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class TicketReservationService {

    private final ReactiveMongoTemplate mongoTemplate;
    private final ApplicationEventPublisher events;

    /**
     * Atomically reserves a ticket.
     *
     * This operation is GUARANTEED to prevent double-booking because:
     * 1. findAndModify is atomic at the database level
     * 2. The query includes status=AVAILABLE as a condition
     * 3. Only ONE request can succeed - all others get null
     *
     * @return Mono<Ticket> - the reserved ticket, or empty if unavailable
     */
    @Transactional
    public Mono<Ticket> reserveTicket(String ticketId, String userId, String paymentIntentId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(600); // 10 minutes

        Query query = Query.query(
            Criteria.where("_id").is(ticketId)
                .and("status").is(TicketStatus.AVAILABLE)  // CRITICAL CONDITION
        );

        Update update = new Update()
            .set("status", TicketStatus.RESERVED)
            .set("reservation.userId", userId)
            .set("reservation.reservedAt", now)
            .set("reservation.expiresAt", expiresAt)
            .set("reservation.paymentIntentId", paymentIntentId)
            .push("statusHistory", new StatusChange(
                TicketStatus.RESERVED,
                now,
                "Reserved for payment"
            ))
            .inc("version", 1);  // Optimistic locking

        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                Ticket.class
            )
            .switchIfEmpty(Mono.error(new TicketNotAvailableException(
                "Ticket " + ticketId + " is not available for reservation"
            )))
            .doOnSuccess(ticket -> {
                // Publish event after successful reservation
                events.publishEvent(new TicketReservedEvent(
                    ticket.getId(),
                    userId,
                    paymentIntentId,
                    ticket.getEventId(),
                    ticket.getPrice()
                ));
            });
    }

    /**
     * Atomically marks a ticket as sold.
     *
     * Conditions:
     * - Ticket must be RESERVED
     * - Reservation must belong to the same user
     * - Reservation must not be expired
     */
    @Transactional
    public Mono<Ticket> confirmSale(String ticketId, String userId, String paymentIntentId) {
        Instant now = Instant.now();

        Query query = Query.query(
            Criteria.where("_id").is(ticketId)
                .and("status").is(TicketStatus.RESERVED)
                .and("reservation.userId").is(userId)
                .and("reservation.paymentIntentId").is(paymentIntentId)
                .and("reservation.expiresAt").gte(now)  // Not expired
        );

        Update update = new Update()
            .set("status", TicketStatus.SOLD)
            .set("sale.userId", userId)
            .set("sale.soldAt", now)
            .set("sale.paymentIntentId", paymentIntentId)
            .unset("reservation")
            .push("statusHistory", new StatusChange(
                TicketStatus.SOLD,
                now,
                "Payment confirmed"
            ))
            .inc("version", 1);

        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                Ticket.class
            )
            .switchIfEmpty(Mono.error(new InvalidTicketStateException(
                "Cannot confirm sale - ticket not in expected state"
            )))
            .doOnSuccess(ticket -> {
                events.publishEvent(new TicketSoldEvent(
                    ticket.getId(),
                    userId,
                    paymentIntentId,
                    ticket.getEventId(),
                    ticket.getPrice()
                ));
            });
    }

    /**
     * Releases a reservation back to available.
     * Used when payment fails or expires.
     */
    @Transactional
    public Mono<Ticket> releaseReservation(String ticketId, String userId, String reason) {
        Query query = Query.query(
            Criteria.where("_id").is(ticketId)
                .and("status").is(TicketStatus.RESERVED)
                .and("reservation.userId").is(userId)
        );

        Update update = new Update()
            .set("status", TicketStatus.AVAILABLE)
            .unset("reservation")
            .push("statusHistory", new StatusChange(
                TicketStatus.AVAILABLE,
                Instant.now(),
                "Released: " + reason
            ))
            .inc("version", 1);

        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                Ticket.class
            );
    }
}
```

### 7.2 Reservation Cleanup Job

```java
package com.ticketing.booking.booking;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cleans up expired reservations.
 * Runs every 30 seconds to release tickets that weren't paid for.
 */
@Component
public class ReservationCleanupJob {

    private final ReactiveMongoTemplate mongoTemplate;
    private final ApplicationEventPublisher events;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedRateString = "${reservation.cleanup-interval-seconds:30}000")
    public void cleanupExpiredReservations() {
        Instant now = Instant.now();

        Query query = Query.query(
            Criteria.where("status").is(TicketStatus.RESERVED)
                .and("reservation.expiresAt").lt(now)
        );

        Update update = new Update()
            .set("status", TicketStatus.AVAILABLE)
            .unset("reservation")
            .push("statusHistory", new StatusChange(
                TicketStatus.AVAILABLE,
                now,
                "Reservation expired - auto-released"
            ))
            .inc("version", 1);

        mongoTemplate.updateMulti(query, update, Ticket.class)
            .subscribe(result -> {
                if (result.getModifiedCount() > 0) {
                    log.info("Released {} expired reservations", result.getModifiedCount());
                    meterRegistry.counter("tickets.reservations.expired")
                        .increment(result.getModifiedCount());
                }
            });
    }
}
```

### 7.3 Visual: How Double-Booking is Prevented

```
Scenario: Two users try to book the same ticket simultaneously

     User A                    MongoDB                    User B
       │                          │                          │
       │ Reserve ticket-123       │       Reserve ticket-123 │
       │ (status=AVAILABLE)       │       (status=AVAILABLE) │
       │─────────────────────────▶│◀─────────────────────────│
       │                          │                          │
       │         findAndModify    │                          │
       │         ┌────────────────┴────────────────┐         │
       │         │ Lock document                   │         │
       │         │ Check: status == AVAILABLE? ✓  │         │
       │         │ Update: status = RESERVED      │         │
       │         │ Return: updated document       │         │
       │         └────────────────┬────────────────┘         │
       │                          │                          │
       │◀─────── Ticket (RESERVED)│                          │
       │         for User A       │                          │
       │                          │         findAndModify    │
       │                          │ ┌────────────────────────┤
       │                          │ │ Check: status ==       │
       │                          │ │        AVAILABLE?      │
       │                          │ │ FAILS! (status is now  │
       │                          │ │        RESERVED)       │
       │                          │ │ Return: null           │
       │                          │ └────────────────────────┤
       │                          │                          │
       │                          │        null (no match) ─▶│
       │                          │                          │
       │                          │        TicketNotAvailable│
       │                          │        Exception        ─▶│
       │                          │                          │
       ▼                          ▼                          ▼
   SUCCESS!                                               FAILED!
   Proceed to payment                             Show "Already taken"
```

---

## 8. Payment Integration Flow

### 8.1 Complete Payment Flow Sequence

```
┌────────────────────────────────────────────────────────────────────────────────────┐
│                         COMPLETE PAYMENT FLOW                                       │
├────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  PHASE 1: INITIATION (Atomic)                                                      │
│  ══════════════════════════                                                        │
│                                                                                     │
│  Mobile App                         Booking Service                    MongoDB     │
│      │                                    │                               │        │
│      │ POST /bookings                     │                               │        │
│      │ {ticketId, provider,               │                               │        │
│      │  phoneNumber, idempotencyKey}      │                               │        │
│      │───────────────────────────────────▶│                               │        │
│      │                                    │                               │        │
│      │                                    │ ┌───────────────────────────┐ │        │
│      │                                    │ │ TRANSACTION              │ │        │
│      │                                    │ │ 1. Check idempotencyKey  │ │        │
│      │                                    │ │ 2. Reserve ticket        │─┼───────▶│
│      │                                    │ │    (atomic findAndModify)│ │        │
│      │                                    │ │ 3. Create PaymentIntent  │─┼───────▶│
│      │                                    │ │ 4. Publish event         │ │        │
│      │                                    │ │    (EventPublicationReg) │ │        │
│      │                                    │ └───────────────────────────┘ │        │
│      │                                    │                               │        │
│      │◀───────────────────────────────────│                               │        │
│      │ {paymentIntentId, transactionRef,  │                               │        │
│      │  paymentUrl, ussdCode, expiresAt}  │                               │        │
│      │                                    │                               │        │
│                                                                                     │
│  PHASE 2: USER PAYMENT                                                             │
│  ═══════════════════                                                               │
│                                                                                     │
│  Mobile App                       Payment Provider                                  │
│      │                                    │                                         │
│      │ Open paymentUrl OR dial ussdCode   │                                         │
│      │───────────────────────────────────▶│                                         │
│      │                                    │                                         │
│      │         User enters PIN            │                                         │
│      │         Provider processes         │                                         │
│      │         payment...                 │                                         │
│      │                                    │                                         │
│                                                                                     │
│  PHASE 3A: WEBHOOK NOTIFICATION (Primary)                                          │
│  ════════════════════════════════════════                                          │
│                                                                                     │
│  Payment Provider                   Booking Service                    MongoDB     │
│      │                                    │                               │        │
│      │ POST /webhooks/payments/mtn        │                               │        │
│      │ {transactionRef, status,           │                               │        │
│      │  providerTxnId, signature}         │                               │        │
│      │───────────────────────────────────▶│                               │        │
│      │                                    │                               │        │
│      │                          ┌─────────┴─────────┐                     │        │
│      │                          │ 1. Verify HMAC    │                     │        │
│      │                          │    signature      │                     │        │
│      │                          │ 2. Check webhook  │                     │        │
│      │                          │    idempotency    │────────────────────▶│        │
│      │                          │ 3. Process        │                     │        │
│      │                          │    outcome        │                     │        │
│      │                          └─────────┬─────────┘                     │        │
│      │                                    │                               │        │
│      │                                    │ ┌───────────────────────────┐ │        │
│      │                                    │ │ TRANSACTION              │ │        │
│      │                                    │ │ If SUCCESS:              │ │        │
│      │                                    │ │   Update PaymentIntent   │─┼───────▶│
│      │                                    │ │   Mark ticket SOLD       │─┼───────▶│
│      │                                    │ │   Credit escrow          │─┼───────▶│
│      │                                    │ │   Publish events         │ │        │
│      │                                    │ │ If FAILED:               │ │        │
│      │                                    │ │   Update PaymentIntent   │─┼───────▶│
│      │                                    │ │   Release ticket         │─┼───────▶│
│      │                                    │ │   Publish events         │ │        │
│      │                                    │ └───────────────────────────┘ │        │
│      │                                    │                               │        │
│      │◀───────────────────────────────────│                               │        │
│      │            200 OK                  │                               │        │
│      │                                    │                               │        │
│                                                                                     │
│  PHASE 3B: POLLING (Fallback - if webhook missed)                                  │
│  ════════════════════════════════════════════════                                  │
│                                                                                     │
│  Scheduled Job (every 30s)          Payment Provider                    MongoDB    │
│      │                                    │                               │        │
│      │ Find PENDING payments              │                               │        │
│      │ older than 2 minutes               │                               │        │
│      │───────────────────────────────────────────────────────────────────▶│        │
│      │                                    │                               │        │
│      │◀──────────────────────────────────────────────────────── payments[]│        │
│      │                                    │                               │        │
│      │ For each payment:                  │                               │        │
│      │ GET /status?transactionRef=xxx     │                               │        │
│      │───────────────────────────────────▶│                               │        │
│      │                                    │                               │        │
│      │◀───────────────────────────────────│                               │        │
│      │        {status, providerTxnId}     │                               │        │
│      │                                    │                               │        │
│      │ If status changed:                 │                               │        │
│      │   Process outcome (same as webhook)│                               │        │
│      │                                    │                               │        │
│                                                                                     │
│  PHASE 4: EVENT PROPAGATION                                                        │
│  ══════════════════════════                                                        │
│                                                                                     │
│  Spring Modulith               Azure Service Bus              Other Services       │
│      │                               │                               │             │
│      │ @ApplicationModuleListener    │                               │             │
│      │ (internal modules)            │                               │             │
│      │───────────────────────────────│                               │             │
│      │ • NotificationModule          │                               │             │
│      │   → Send SMS/Push             │                               │             │
│      │ • EscrowModule                │                               │             │
│      │   → Update balance            │                               │             │
│      │                               │                               │             │
│      │ @Externalized                 │                               │             │
│      │ (external services)           │                               │             │
│      │──────────────────────────────▶│                               │             │
│      │   PaymentSucceededEvent       │                               │             │
│      │                               │──────────────────────────────▶│             │
│      │                               │    • CatalogService           │             │
│      │                               │    • IdentityService          │             │
│      │                               │    • AnalyticsService         │             │
│      │                               │                               │             │
└────────────────────────────────────────────────────────────────────────────────────┘
```

### 8.2 Payment Service Implementation

```java
package com.ticketing.booking.payment;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
public class PaymentService {

    private final ReactiveMongoTemplate mongoTemplate;
    private final PaymentIntentRepository paymentIntentRepo;
    private final TicketReservationService ticketService;
    private final PaymentProviderFactory providerFactory;
    private final ApplicationEventPublisher events;

    /**
     * Initiates a booking with payment.
     * This is the main entry point for the purchase flow.
     */
    @Transactional
    public Mono<BookingResponse> initiateBooking(BookingRequest request) {
        // 1. Check idempotency - return existing if duplicate request
        return paymentIntentRepo.findByIdempotencyKey(request.getIdempotencyKey())
            .flatMap(existing -> {
                // Return existing payment intent (idempotent response)
                return Mono.just(BookingResponse.fromExisting(existing));
            })
            .switchIfEmpty(
                // 2. Create new booking
                createNewBooking(request)
            );
    }

    private Mono<BookingResponse> createNewBooking(BookingRequest request) {
        String transactionRef = generateTransactionRef();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(600);

        // 1. Create payment intent first
        PaymentIntent paymentIntent = PaymentIntent.builder()
            .idempotencyKey(request.getIdempotencyKey())
            .transactionRef(transactionRef)
            .ticketId(request.getTicketId())
            .userId(request.getUserId())
            .provider(request.getProvider())
            .status(PaymentStatus.PENDING)
            .createdAt(now)
            .expiresAt(expiresAt)
            .build();

        return paymentIntentRepo.save(paymentIntent)
            .flatMap(savedIntent -> {
                // 2. Reserve the ticket atomically
                return ticketService.reserveTicket(
                        request.getTicketId(),
                        request.getUserId(),
                        savedIntent.getId()
                    )
                    .flatMap(ticket -> {
                        // 3. Update payment intent with ticket details
                        savedIntent.setAmount(ticket.getPrice());
                        savedIntent.setCurrency(ticket.getCurrency());
                        savedIntent.setEventId(ticket.getEventId());

                        return paymentIntentRepo.save(savedIntent);
                    })
                    .flatMap(updatedIntent -> {
                        // 4. Initiate payment with provider
                        PaymentProvider provider = providerFactory.getProvider(request.getProvider());

                        return provider.initiatePayment(
                            InitiatePaymentRequest.builder()
                                .transactionRef(transactionRef)
                                .amount(updatedIntent.getAmount())
                                .currency(updatedIntent.getCurrency())
                                .phoneNumber(request.getPhoneNumber())
                                .description("Event ticket purchase")
                                .callbackUrl(getCallbackUrl(request.getProvider()))
                                .build()
                        )
                        .map(providerResponse -> {
                            // 5. Publish event
                            events.publishEvent(new PaymentInitiatedEvent(
                                updatedIntent.getId(),
                                updatedIntent.getTicketId(),
                                updatedIntent.getUserId(),
                                updatedIntent.getAmount()
                            ));

                            // 6. Return response
                            return BookingResponse.builder()
                                .paymentIntentId(updatedIntent.getId())
                                .transactionRef(transactionRef)
                                .paymentUrl(providerResponse.getPaymentUrl())
                                .ussdCode(providerResponse.getUssdCode())
                                .expiresAt(expiresAt)
                                .status(PaymentStatus.PENDING)
                                .build();
                        });
                    });
            })
            .onErrorResume(TicketNotAvailableException.class, e -> {
                // Ticket was taken - delete the payment intent
                return paymentIntentRepo.deleteById(paymentIntent.getId())
                    .then(Mono.error(e));
            });
    }

    private String generateTransactionRef() {
        return "TXN-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) +
               "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
```

### 8.3 Webhook Handler

```java
package com.ticketing.booking.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/webhooks/payments")
public class PaymentWebhookController {

    private final WebhookSignatureVerifier signatureVerifier;
    private final ProcessedWebhookRepository processedWebhookRepo;
    private final PaymentOutcomeService outcomeService;
    private final MeterRegistry meterRegistry;

    @PostMapping("/{provider}")
    public Mono<ResponseEntity<String>> handleWebhook(
            @PathVariable String provider,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestHeader(value = "X-Webhook-Id", required = false) String webhookId,
            @RequestBody String rawPayload) {

        PaymentProvider paymentProvider = PaymentProvider.valueOf(provider.toUpperCase());

        // 1. Verify signature (CRITICAL for security)
        if (!signatureVerifier.verify(paymentProvider, rawPayload, signature)) {
            log.warn("Invalid webhook signature from {}", provider);
            meterRegistry.counter("webhooks.invalid_signature", "provider", provider).increment();
            return Mono.just(ResponseEntity.status(401).body("Invalid signature"));
        }

        // 2. Parse payload
        WebhookPayload payload = parsePayload(paymentProvider, rawPayload, webhookId);

        // 3. Idempotency check
        return processedWebhookRepo.existsById(payload.getWebhookId())
            .flatMap(exists -> {
                if (exists) {
                    log.info("Webhook {} already processed", payload.getWebhookId());
                    meterRegistry.counter("webhooks.duplicate", "provider", provider).increment();
                    return Mono.just(ResponseEntity.ok("Already processed"));
                }

                // 4. Process the outcome
                return outcomeService.processPaymentOutcome(
                        payload.getTransactionRef(),
                        payload.getProviderTransactionId(),
                        mapToStatus(payload.getStatus()),
                        payload.getFailureReason(),
                        "WEBHOOK"
                    )
                    .then(
                        // 5. Mark webhook as processed
                        processedWebhookRepo.save(new ProcessedWebhook(
                            payload.getWebhookId(),
                            paymentProvider,
                            payload.getTransactionRef(),
                            Instant.now()
                        ))
                    )
                    .then(Mono.just(ResponseEntity.ok("Processed")))
                    .doOnSuccess(r -> {
                        meterRegistry.counter("webhooks.processed",
                            "provider", provider,
                            "status", payload.getStatus()
                        ).increment();
                    });
            })
            .onErrorResume(PaymentNotFoundException.class, e -> {
                log.warn("Unknown transaction in webhook: {}", payload.getTransactionRef());
                return Mono.just(ResponseEntity.status(404).body("Unknown transaction"));
            })
            .onErrorResume(Exception.class, e -> {
                log.error("Webhook processing error: {}", e.getMessage(), e);
                meterRegistry.counter("webhooks.error", "provider", provider).increment();
                return Mono.just(ResponseEntity.status(500).body("Processing error"));
            });
    }
}
```

### 8.4 Payment Outcome Service

```java
package com.ticketing.booking.payment;

import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
public class PaymentOutcomeService {

    private final ReactiveMongoTemplate mongoTemplate;
    private final TicketReservationService ticketService;
    private final EscrowService escrowService;
    private final ApplicationEventPublisher events;

    @Transactional
    public Mono<Void> processPaymentOutcome(
            String transactionRef,
            String providerTransactionId,
            PaymentStatus newStatus,
            String failureReason,
            String source) {

        // 1. Update PaymentIntent atomically (with optimistic locking)
        Query query = Query.query(
            Criteria.where("transactionRef").is(transactionRef)
                .and("status").in(PaymentStatus.PENDING, PaymentStatus.PROCESSING)
        );

        Update update = new Update()
            .set("status", newStatus)
            .set("providerTransactionId", providerTransactionId)
            .set("failureReason", failureReason)
            .set("processedAt", Instant.now())
            .push("statusHistory", new StatusChange(newStatus, Instant.now(), source))
            .inc("version", 1);

        if (source.equals("WEBHOOK")) {
            update.inc("webhookAttempts", 1);
        } else if (source.equals("POLLING")) {
            update.inc("pollAttempts", 1);
        }

        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                PaymentIntent.class
            )
            .switchIfEmpty(Mono.error(new PaymentNotFoundException(transactionRef)))
            .flatMap(payment -> {
                if (newStatus == PaymentStatus.SUCCEEDED) {
                    return processSuccessfulPayment(payment);
                } else if (newStatus == PaymentStatus.FAILED || newStatus == PaymentStatus.EXPIRED) {
                    return processFailedPayment(payment, failureReason);
                }
                return Mono.empty();
            });
    }

    private Mono<Void> processSuccessfulPayment(PaymentIntent payment) {
        // 1. Mark ticket as SOLD
        return ticketService.confirmSale(
                payment.getTicketId(),
                payment.getUserId(),
                payment.getId()
            )
            .flatMap(ticket -> {
                // 2. Credit escrow account
                return escrowService.creditSale(
                    payment.getEventId(),
                    payment.getAmount(),
                    payment.getId(),
                    payment.getTicketId()
                );
            })
            .doOnSuccess(escrow -> {
                // 3. Publish success event
                events.publishEvent(new PaymentSucceededEvent(
                    payment.getId(),
                    payment.getTicketId(),
                    payment.getUserId(),
                    payment.getEventId(),
                    payment.getAmount(),
                    payment.getProvider()
                ));
            })
            .then();
    }

    private Mono<Void> processFailedPayment(PaymentIntent payment, String reason) {
        // 1. Release the ticket
        return ticketService.releaseReservation(
                payment.getTicketId(),
                payment.getUserId(),
                reason
            )
            .doOnSuccess(ticket -> {
                // 2. Publish failure event
                events.publishEvent(new PaymentFailedEvent(
                    payment.getId(),
                    payment.getTicketId(),
                    payment.getUserId(),
                    reason
                ));
            })
            .then();
    }
}
```

### 8.5 Payment Status Polling Job

```java
package com.ticketing.booking.payment;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@ConditionalOnProperty(name = "polling.enabled", havingValue = "true")
public class PaymentStatusPollingJob {

    private final PaymentIntentRepository paymentIntentRepo;
    private final PaymentProviderFactory providerFactory;
    private final PaymentOutcomeService outcomeService;
    private final MeterRegistry meterRegistry;

    @Value("${polling.pending-threshold-seconds:120}")
    private int pendingThresholdSeconds;

    @Value("${polling.max-attempts:10}")
    private int maxPollAttempts;

    @Scheduled(fixedRateString = "${polling.interval-seconds:30}000")
    public void pollPendingPayments() {
        Instant threshold = Instant.now().minusSeconds(pendingThresholdSeconds);
        Instant pollCooldown = Instant.now().minusSeconds(30);

        paymentIntentRepo.findPendingPaymentsForPolling(
                List.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING),
                threshold,
                pollCooldown,
                maxPollAttempts
            )
            .flatMap(this::pollPaymentStatus)
            .subscribe(
                result -> log.debug("Polled payment: {}", result),
                error -> log.error("Polling error: {}", error.getMessage())
            );
    }

    private Mono<PaymentIntent> pollPaymentStatus(PaymentIntent payment) {
        PaymentProvider provider = providerFactory.getProvider(payment.getProvider());

        return provider.checkStatus(payment.getTransactionRef())
            .flatMap(status -> {
                // Update last polled time
                payment.setLastPolledAt(Instant.now());
                payment.setPollAttempts(payment.getPollAttempts() + 1);

                return paymentIntentRepo.save(payment)
                    .flatMap(updated -> {
                        // If status changed, process it
                        if (status.getStatus() != PaymentStatus.PENDING &&
                            status.getStatus() != PaymentStatus.PROCESSING) {

                            return outcomeService.processPaymentOutcome(
                                    payment.getTransactionRef(),
                                    status.getProviderTransactionId(),
                                    status.getStatus(),
                                    status.getFailureReason(),
                                    "POLLING"
                                )
                                .thenReturn(updated);
                        }

                        // Check for expiration
                        if (payment.getExpiresAt().isBefore(Instant.now()) &&
                            payment.getPollAttempts() >= 3) {

                            return outcomeService.processPaymentOutcome(
                                    payment.getTransactionRef(),
                                    null,
                                    PaymentStatus.EXPIRED,
                                    "Payment timeout - no response from provider",
                                    "POLLING"
                                )
                                .thenReturn(updated);
                        }

                        return Mono.just(updated);
                    });
            })
            .doOnSuccess(p -> {
                meterRegistry.counter("polling.checked", "provider", payment.getProvider().name()).increment();
            })
            .onErrorResume(e -> {
                log.warn("Failed to poll payment {}: {}", payment.getId(), e.getMessage());
                meterRegistry.counter("polling.errors", "provider", payment.getProvider().name()).increment();
                return Mono.just(payment);
            });
    }
}
```

---

## 9. Event Flow & Guaranteed Delivery

### 9.1 Event Definitions

```java
package com.ticketing.booking.payment.events;

import java.math.BigDecimal;
import java.time.Instant;

// All events are records (immutable)
public sealed interface PaymentEvent permits
    PaymentInitiatedEvent,
    PaymentSucceededEvent,
    PaymentFailedEvent,
    PaymentExpiredEvent,
    PaymentRefundedEvent {

    String paymentIntentId();
    String ticketId();
    String userId();
    Instant timestamp();
}

public record PaymentInitiatedEvent(
    String paymentIntentId,
    String ticketId,
    String userId,
    String eventId,
    BigDecimal amount,
    String provider,
    Instant timestamp
) implements PaymentEvent {
    public PaymentInitiatedEvent {
        timestamp = timestamp != null ? timestamp : Instant.now();
    }
}

public record PaymentSucceededEvent(
    String paymentIntentId,
    String ticketId,
    String userId,
    String eventId,
    BigDecimal amount,
    String provider,
    String providerTransactionId,
    Instant timestamp
) implements PaymentEvent {
    public PaymentSucceededEvent {
        timestamp = timestamp != null ? timestamp : Instant.now();
    }
}

public record PaymentFailedEvent(
    String paymentIntentId,
    String ticketId,
    String userId,
    String reason,
    String failureCode,
    Instant timestamp
) implements PaymentEvent {
    public PaymentFailedEvent {
        timestamp = timestamp != null ? timestamp : Instant.now();
    }
}
```

### 9.2 Internal Event Listeners (Spring Modulith)

```java
package com.ticketing.booking.notification.listeners;

import com.ticketing.booking.payment.events.*;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Notification listeners.
 *
 * Uses @ApplicationModuleListener for guaranteed delivery:
 * - Events are tracked in event_publications collection
 * - If processing fails, events are retried on restart
 * - Events are only marked complete after successful processing
 */
@Component
class PaymentNotificationListener {

    private final SmsGateway smsGateway;
    private final PushNotificationService pushService;
    private final UserRepository userRepo;

    @ApplicationModuleListener
    void onPaymentSucceeded(PaymentSucceededEvent event) {
        userRepo.findById(event.userId())
            .ifPresent(user -> {
                // Send SMS confirmation
                smsGateway.send(
                    user.getPhoneNumber(),
                    String.format(
                        "Payment confirmed! Your ticket %s has been purchased. " +
                        "Amount: ZMW %.2f. Ref: %s",
                        event.ticketId(),
                        event.amount(),
                        event.paymentIntentId()
                    )
                );

                // Send push notification
                if (user.getPushToken() != null) {
                    pushService.send(
                        user.getPushToken(),
                        "Payment Successful",
                        "Your ticket purchase was successful!"
                    );
                }
            });
    }

    @ApplicationModuleListener
    void onPaymentFailed(PaymentFailedEvent event) {
        userRepo.findById(event.userId())
            .ifPresent(user -> {
                pushService.send(
                    user.getPushToken(),
                    "Payment Failed",
                    "Your payment could not be processed. Please try again."
                );
            });
    }
}
```

### 9.3 Escrow Event Listener

```java
package com.ticketing.booking.escrow.listeners;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class PaymentEventListener {

    private final EscrowService escrowService;

    /**
     * Credits escrow account when payment succeeds.
     * This is idempotent - the escrow service checks if already credited.
     */
    @ApplicationModuleListener
    void onPaymentSucceeded(PaymentSucceededEvent event) {
        escrowService.creditSale(
            event.eventId(),
            event.amount(),
            event.paymentIntentId(),
            event.ticketId()
        ).subscribe();
    }

    /**
     * Process refund - debit from escrow.
     */
    @ApplicationModuleListener
    void onPaymentRefunded(PaymentRefundedEvent event) {
        escrowService.debitRefund(
            event.eventId(),
            event.amount(),
            event.paymentIntentId(),
            event.reason()
        ).subscribe();
    }
}
```

### 9.4 Event Publication Monitoring

```java
package com.ticketing.booking.shared;

import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public class EventPublicationMonitor {

    private final EventPublicationRegistry registry;
    private final MeterRegistry meterRegistry;
    private final AlertService alertService;

    @Scheduled(fixedRate = 60000) // Every minute
    public void monitorIncompletePublications() {
        var incomplete = registry.findIncompletePublications();
        var stale = registry.findIncompletePublicationsOlderThan(Duration.ofMinutes(5));

        // Update metrics
        meterRegistry.gauge("events.publications.incomplete", incomplete.size());
        meterRegistry.gauge("events.publications.stale", stale.size());

        // Alert on stale publications
        if (!stale.isEmpty()) {
            log.warn("Found {} stale event publications", stale.size());

            stale.forEach(pub -> {
                log.warn("Stale publication: id={}, event={}, listener={}, publishedAt={}",
                    pub.getIdentifier(),
                    pub.getEvent().getClass().getSimpleName(),
                    pub.getTargetIdentifier(),
                    pub.getPublicationDate()
                );
            });

            if (stale.size() > 10) {
                alertService.sendAlert(
                    "HIGH",
                    "Stale Event Publications",
                    String.format("%d event publications are stuck for over 5 minutes", stale.size())
                );
            }
        }
    }
}
```

---

## 10. Error Handling & Recovery

### 10.1 Dead Letter Queue Processing

```java
package com.ticketing.booking.external;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.function.Consumer;

@Configuration
public class DlqProcessorConfig {

    private final DlqEventRepository dlqEventRepo;
    private final AlertService alertService;

    /**
     * Processes events from the Dead Letter Queue.
     * These are events that failed processing multiple times.
     */
    @Bean
    public Consumer<Message<String>> dlqProcessor() {
        return message -> {
            String eventType = message.getHeaders().get("eventType", String.class);
            String originalDestination = message.getHeaders().get("x-original-destination", String.class);
            String errorMessage = message.getHeaders().get("x-exception-message", String.class);
            int deliveryCount = message.getHeaders().get("x-delivery-count", Integer.class);

            log.error("DLQ Event received: type={}, destination={}, error={}, deliveryCount={}",
                eventType, originalDestination, errorMessage, deliveryCount);

            // Store for manual review
            DlqEvent dlqEvent = DlqEvent.builder()
                .eventType(eventType)
                .payload(message.getPayload())
                .originalDestination(originalDestination)
                .errorMessage(errorMessage)
                .deliveryCount(deliveryCount)
                .receivedAt(Instant.now())
                .status("PENDING_REVIEW")
                .build();

            dlqEventRepo.save(dlqEvent);

            // Alert operations team
            alertService.sendAlert(
                "MEDIUM",
                "Event in Dead Letter Queue",
                String.format("Event type: %s, Error: %s", eventType, errorMessage)
            );
        };
    }
}
```

### 10.2 Retry Configuration

```yaml
# application.yml - retry settings
spring:
  cloud:
    stream:
      servicebus:
        bindings:
          paymentEventsConsumer-in-0:
            consumer:
              max-concurrent-calls: 5
              # Retry configuration
              max-auto-lock-renew-duration: 5m
              # After max retries, message goes to DLQ automatically

      # Global consumer retry settings
      default:
        consumer:
          max-attempts: 3
          back-off-initial-interval: 1000
          back-off-max-interval: 10000
          back-off-multiplier: 2.0
```

### 10.3 Circuit Breaker for Payment Providers

```java
package com.ticketing.booking.payment.providers;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class MtnMomoProvider implements PaymentProvider {

    private final WebClient webClient;

    @Override
    @CircuitBreaker(name = "mtnMomo", fallbackMethod = "initiatePaymentFallback")
    @Retry(name = "mtnMomo")
    public Mono<PaymentInitiationResponse> initiatePayment(InitiatePaymentRequest request) {
        return webClient.post()
            .uri("/collection/v1_0/requesttopay")
            .header("X-Reference-Id", request.getTransactionRef())
            .header("X-Target-Environment", environment)
            .bodyValue(MtnPaymentRequest.from(request))
            .retrieve()
            .bodyToMono(MtnPaymentResponse.class)
            .map(this::toPaymentInitiationResponse);
    }

    private Mono<PaymentInitiationResponse> initiatePaymentFallback(
            InitiatePaymentRequest request, Throwable t) {
        log.error("MTN MoMo circuit breaker open: {}", t.getMessage());
        return Mono.error(new PaymentProviderUnavailableException(
            "MTN Mobile Money is temporarily unavailable. Please try again later."
        ));
    }

    @Override
    @CircuitBreaker(name = "mtnMomo")
    @Retry(name = "mtnMomo")
    public Mono<PaymentStatusResponse> checkStatus(String transactionRef) {
        return webClient.get()
            .uri("/collection/v1_0/requesttopay/{referenceId}", transactionRef)
            .retrieve()
            .bodyToMono(MtnStatusResponse.class)
            .map(this::toPaymentStatusResponse);
    }
}
```

```yaml
# application.yml - Resilience4j configuration
resilience4j:
  circuitbreaker:
    instances:
      mtnMomo:
        registerHealthIndicator: true
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
        waitDurationInOpenState: 30s
        failureRateThreshold: 50
        eventConsumerBufferSize: 10
      airtelMoney:
        # Same config
      zamtelKwacha:
        # Same config

  retry:
    instances:
      mtnMomo:
        maxAttempts: 3
        waitDuration: 1s
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
          - java.net.SocketTimeoutException
        ignoreExceptions:
          - com.ticketing.booking.payment.PaymentDeclinedException
```

---

## 11. Implementation Phases

### Phase 1: Foundation (Week 1-2)

```
┌─────────────────────────────────────────────────────────────────┐
│  PHASE 1: FOUNDATION                                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  □ 1.1 MongoDB Replica Set Setup                                │
│     □ Use existing docker/mongodb-replica-set/                  │
│     □ Run setup.sh and docker-compose up                        │
│     □ Verify transactions work                                  │
│                                                                  │
│  □ 1.2 Update Maven Dependencies                                │
│     □ Add Spring Modulith dependencies                          │
│     □ Add Spring Cloud Azure Service Bus                        │
│     □ Add Resilience4j                                          │
│     □ Remove Debezium dependencies                              │
│                                                                  │
│  □ 1.3 Create New MongoDB Collections                           │
│     □ Update tickets collection schema                          │
│     □ Create payment_intents collection                         │
│     □ Create escrow_accounts collection                         │
│     □ Create processed_webhooks collection                      │
│     □ Add all indexes                                           │
│                                                                  │
│  □ 1.4 Configure Spring Modulith                                │
│     □ Set up project structure with modules                     │
│     □ Configure event publication registry for MongoDB          │
│     □ Enable event externalization                              │
│                                                                  │
│  □ 1.5 Azure Service Bus Setup                                  │
│     □ Create Service Bus namespace                              │
│     □ Create topics (payment-events, ticket-events)             │
│     □ Create subscriptions for each consumer service            │
│     □ Configure Dead Letter Queues                              │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Phase 2: Core Booking Logic (Week 2-3)

```
┌─────────────────────────────────────────────────────────────────┐
│  PHASE 2: CORE BOOKING LOGIC                                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  □ 2.1 Implement Atomic Ticket Reservation                      │
│     □ Create TicketReservationService                           │
│     □ Implement reserveTicket with findAndModify                │
│     □ Implement confirmSale                                     │
│     □ Implement releaseReservation                              │
│     □ Write unit tests for concurrent access                    │
│                                                                  │
│  □ 2.2 Implement Reservation Cleanup Job                        │
│     □ Create scheduled job for expired reservations             │
│     □ Add metrics for released reservations                     │
│     □ Test with various expiration scenarios                    │
│                                                                  │
│  □ 2.3 Implement PaymentIntent Management                       │
│     □ Create PaymentIntent model with all fields                │
│     □ Implement idempotency checking                            │
│     □ Create PaymentIntentRepository                            │
│                                                                  │
│  □ 2.4 Define Domain Events                                     │
│     □ Create all event records                                  │
│     □ Ensure events are serializable                            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Phase 3: Payment Provider Integration (Week 3-4)

```
┌─────────────────────────────────────────────────────────────────┐
│  PHASE 3: PAYMENT PROVIDER INTEGRATION                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  □ 3.1 Create Payment Provider Abstraction                      │
│     □ Define PaymentProvider interface                          │
│     □ Create PaymentProviderFactory                             │
│                                                                  │
│  □ 3.2 Implement MTN MoMo Provider                              │
│     □ Implement initiatePayment                                 │
│     □ Implement checkStatus                                     │
│     □ Implement webhook signature verification                  │
│     □ Add circuit breaker                                       │
│                                                                  │
│  □ 3.3 Implement Airtel Money Provider                          │
│     □ Same as MTN                                               │
│                                                                  │
│  □ 3.4 Implement Zamtel Kwacha Provider                         │
│     □ Same as MTN                                               │
│                                                                  │
│  □ 3.5 Implement Webhook Handler                                │
│     □ Create PaymentWebhookController                           │
│     □ Implement signature verification                          │
│     □ Implement idempotency checking                            │
│     □ Wire up to PaymentOutcomeService                          │
│                                                                  │
│  □ 3.6 Implement Polling Job                                    │
│     □ Create PaymentStatusPollingJob                            │
│     □ Configure polling intervals                               │
│     □ Handle timeouts and expirations                           │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Phase 4: Event Handling & External Publishing (Week 4-5)

```
┌─────────────────────────────────────────────────────────────────┐
│  PHASE 4: EVENT HANDLING                                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  □ 4.1 Implement Internal Event Listeners                       │
│     □ Create NotificationListener                               │
│     □ Create EscrowEventListener                                │
│     □ Create AnalyticsListener                                  │
│                                                                  │
│  □ 4.2 Implement External Event Publisher                       │
│     □ Create ExternalEventPublisher                             │
│     □ Configure StreamBridge for Azure Service Bus              │
│     □ Test event delivery                                       │
│                                                                  │
│  □ 4.3 Implement Event Consumers in Other Services              │
│     □ Update Catalog Service consumer                           │
│     □ Update Identity Service consumer                          │
│     □ Implement idempotent processing                           │
│                                                                  │
│  □ 4.4 Implement DLQ Processing                                 │
│     □ Create DLQ processor                                      │
│     □ Create admin API for DLQ management                       │
│     □ Set up alerts                                             │
│                                                                  │
│  □ 4.5 Implement Event Monitoring                               │
│     □ Create EventPublicationMonitor                            │
│     □ Add Prometheus metrics                                    │
│     □ Create Grafana dashboards                                 │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Phase 5: Escrow & Financial (Week 5-6)

```
┌─────────────────────────────────────────────────────────────────┐
│  PHASE 5: ESCROW & FINANCIAL                                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  □ 5.1 Implement Escrow Service                                 │
│     □ Create EscrowAccount model                                │
│     □ Implement creditSale with optimistic locking              │
│     □ Implement debitRefund                                     │
│     □ Implement releaseFunds (for event completion)             │
│                                                                  │
│  □ 5.2 Implement Double-Entry Ledger                            │
│     □ Create transaction recording                              │
│     □ Implement balance reconciliation                          │
│     □ Create audit trail                                        │
│                                                                  │
│  □ 5.3 Implement Refund Flow                                    │
│     □ Create RefundService                                      │
│     □ Implement provider refund calls                           │
│     □ Handle partial refunds                                    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Phase 6: Mobile App Updates (Week 6-7)

```
┌─────────────────────────────────────────────────────────────────┐
│  PHASE 6: MOBILE APP UPDATES                                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  □ 6.1 Update Payment Flow                                      │
│     □ Generate idempotencyKey on booking attempt                │
│     □ Store in AsyncStorage until completion                    │
│     □ Handle redirect to payment provider                       │
│                                                                  │
│  □ 6.2 Implement Status Polling                                 │
│     □ Poll payment status after redirect                        │
│     □ Handle different outcomes (success/failure/timeout)       │
│     □ Show appropriate UI feedback                              │
│                                                                  │
│  □ 6.3 Handle Push Notifications                                │
│     □ Register for push notifications                           │
│     □ Handle payment confirmation notifications                 │
│     □ Deep link to ticket details                               │
│                                                                  │
│  □ 6.4 Error Handling                                           │
│     □ Handle network failures gracefully                        │
│     □ Retry logic with idempotency                              │
│     □ User-friendly error messages                              │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Phase 7: Testing & Hardening (Week 7-8)

```
┌─────────────────────────────────────────────────────────────────┐
│  PHASE 7: TESTING & HARDENING                                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  □ 7.1 Unit Tests                                               │
│     □ Test atomic operations                                    │
│     □ Test event publishing                                     │
│     □ Test idempotency                                          │
│                                                                  │
│  □ 7.2 Integration Tests                                        │
│     □ Test full booking flow                                    │
│     □ Test webhook processing                                   │
│     □ Test event propagation                                    │
│     □ Spring Modulith module tests                              │
│                                                                  │
│  □ 7.3 Load Testing                                             │
│     □ Simulate concurrent bookings                              │
│     □ Verify no double-booking under load                       │
│     □ Measure latency and throughput                            │
│                                                                  │
│  □ 7.4 Chaos Testing                                            │
│     □ Kill MongoDB primary                                      │
│     □ Kill service during transaction                           │
│     □ Simulate webhook failures                                 │
│     □ Verify recovery                                           │
│                                                                  │
│  □ 7.5 Security Testing                                         │
│     □ Test webhook signature verification                       │
│     □ Test authentication                                       │
│     □ Penetration testing                                       │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 12. Testing Strategy

### 12.1 Spring Modulith Module Tests

```java
package com.ticketing.booking;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;

@ApplicationModuleTest
class BookingModuleIntegrationTests {

    @Test
    void shouldReserveTicketAndPublishEvent(Scenario scenario) {
        scenario.stimulate(() -> bookingService.initiateBooking(request))
            .andWaitForEventOfType(TicketReservedEvent.class)
            .matching(event -> event.ticketId().equals(ticketId))
            .toArriveAndVerify(event -> {
                assertThat(event.userId()).isEqualTo(userId);
                assertThat(event.paymentIntentId()).isNotNull();
            });
    }

    @Test
    void shouldProcessPaymentAndPublishEvents(Scenario scenario) {
        // Given: A reserved ticket with pending payment
        setupReservedTicket();

        scenario.stimulate(() -> outcomeService.processPaymentOutcome(
                transactionRef, providerTxnId, PaymentStatus.SUCCEEDED, null, "WEBHOOK"))
            .andWaitForEventOfType(PaymentSucceededEvent.class)
            .toArriveAndVerify(event -> {
                assertThat(event.paymentIntentId()).isNotNull();
            })
            .andWaitForEventOfType(TicketSoldEvent.class)
            .toArrive();
    }
}
```

### 12.2 Concurrent Booking Test

```java
@Test
void shouldPreventDoubleBookingUnderConcurrentLoad() throws Exception {
    String ticketId = createAvailableTicket();
    int concurrentRequests = 100;
    CountDownLatch latch = new CountDownLatch(concurrentRequests);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);

    ExecutorService executor = Executors.newFixedThreadPool(20);

    for (int i = 0; i < concurrentRequests; i++) {
        final String idempotencyKey = "user" + i + "_" + ticketId + "_" + System.currentTimeMillis();
        executor.submit(() -> {
            try {
                bookingService.initiateBooking(BookingRequest.builder()
                    .ticketId(ticketId)
                    .userId("user" + Thread.currentThread().getId())
                    .idempotencyKey(idempotencyKey)
                    .build()
                ).block();
                successCount.incrementAndGet();
            } catch (TicketNotAvailableException e) {
                failureCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await(30, TimeUnit.SECONDS);

    // EXACTLY ONE should succeed
    assertThat(successCount.get()).isEqualTo(1);
    assertThat(failureCount.get()).isEqualTo(concurrentRequests - 1);

    // Verify ticket state
    Ticket ticket = ticketRepository.findById(ticketId).block();
    assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESERVED);
}
```

---

## 13. Monitoring & Observability

### 13.1 Key Metrics

```yaml
# Prometheus metrics to track:

# Business Metrics
tickets_reserved_total{event_id, status}
tickets_sold_total{event_id, payment_provider}
tickets_released_total{event_id, reason}
payments_initiated_total{provider}
payments_succeeded_total{provider}
payments_failed_total{provider, failure_code}

# Technical Metrics
booking_reservation_duration_seconds{quantile}
payment_webhook_processing_duration_seconds{provider}
payment_polling_duration_seconds{provider}
event_publication_incomplete_total
event_publication_stale_total
mongodb_transaction_duration_seconds

# Error Metrics
webhooks_invalid_signature_total{provider}
webhooks_duplicate_total{provider}
webhooks_error_total{provider}
dlq_events_total{event_type}
circuit_breaker_state{provider}
```

### 13.2 Grafana Dashboard

```json
{
  "title": "Ticketing System - Payment Health",
  "panels": [
    {
      "title": "Payment Success Rate",
      "type": "stat",
      "targets": [
        {
          "expr": "sum(rate(payments_succeeded_total[5m])) / sum(rate(payments_initiated_total[5m])) * 100"
        }
      ]
    },
    {
      "title": "Double-Booking Attempts (Should be 0)",
      "type": "stat",
      "targets": [
        {
          "expr": "sum(tickets_double_booking_prevented_total)"
        }
      ]
    },
    {
      "title": "Stale Event Publications",
      "type": "gauge",
      "targets": [
        {
          "expr": "event_publication_stale_total"
        }
      ],
      "thresholds": [0, 5, 10]
    },
    {
      "title": "Payment Flow Duration (p95)",
      "type": "graph",
      "targets": [
        {
          "expr": "histogram_quantile(0.95, booking_reservation_duration_seconds_bucket)"
        }
      ]
    }
  ]
}
```

### 13.3 Alerts

```yaml
# Prometheus AlertManager rules

groups:
  - name: ticketing-payments
    rules:
      - alert: HighPaymentFailureRate
        expr: |
          sum(rate(payments_failed_total[5m])) /
          sum(rate(payments_initiated_total[5m])) > 0.1
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Payment failure rate above 10%"

      - alert: StaleEventPublications
        expr: event_publication_stale_total > 10
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "{{ $value }} event publications are stuck"

      - alert: WebhookSignatureFailures
        expr: rate(webhooks_invalid_signature_total[5m]) > 0.1
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Potential webhook forgery attack"

      - alert: PaymentProviderCircuitOpen
        expr: circuit_breaker_state{state="open"} == 1
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "Circuit breaker open for {{ $labels.provider }}"
```

---

## Summary

This architecture provides:

1. **No Double-Booking**: MongoDB atomic `findAndModify` with status conditions
2. **Guaranteed Event Delivery**: Spring Modulith Event Publication Registry
3. **Safe Payment Integration**: Idempotency keys + Webhook + Polling
4. **Simplified Architecture**: No Debezium, no Kafka (just Azure Service Bus)
5. **Cost-Effective**: Pay-per-use Azure Service Bus instead of always-on infrastructure
6. **Observable**: Comprehensive metrics, logging, and alerting
7. **Recoverable**: DLQ handling, retry policies, circuit breakers

Follow the implementation phases in order, testing thoroughly at each step before proceeding.
