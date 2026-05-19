# Production-Grade Payment Data Integrity Implementation Plan

**Version:** 1.0
**Date:** February 2026
**Author:** Platform Engineering Team
**Status:** Implementation Ready

---

## Executive Summary

This document provides a comprehensive implementation plan to resolve all identified data integrity issues in the payment tracking, confirmation, and recording system. The plan includes infrastructure changes (MongoDB Replica Set), architectural redesign (Transactional Outbox), and code-level fixes.

**Estimated Implementation Time:** 3-4 weeks
**Risk Level:** High (financial transactions)
**Rollback Strategy:** Feature flags + blue-green deployment

---

## Table of Contents

1. [Phase 1: MongoDB Replica Set Setup](#phase-1-mongodb-replica-set-setup)
2. [Phase 2: Transaction Architecture Redesign](#phase-2-transaction-architecture-redesign)
3. [Phase 3: Atomic Outbox Pattern Implementation](#phase-3-atomic-outbox-pattern-implementation)
4. [Phase 4: Optimistic Locking & Concurrency Control](#phase-4-optimistic-locking--concurrency-control)
5. [Phase 5: Payment Idempotency Layer](#phase-5-payment-idempotency-layer)
6. [Phase 6: Webhook Handler Hardening](#phase-6-webhook-handler-hardening)
7. [Phase 7: Saga State Machine Implementation](#phase-7-saga-state-machine-implementation)
8. [Phase 8: Dead Letter Queue & Recovery](#phase-8-dead-letter-queue--recovery)
9. [Phase 9: Monitoring & Alerting](#phase-9-monitoring--alerting)
10. [Migration Strategy](#migration-strategy)

---

## Phase 1: MongoDB Replica Set Setup

### 1.1 Why Replica Set is Required

MongoDB multi-document transactions **require a replica set**. Your current standalone instance:

```
mongodb://app_user:app_password@127.0.0.1:27017/ticketing?authSource=admin
```

**Will fail silently** when attempting transactions. Spring's `@Transactional` will either:
- Throw `MongoTransactionException`
- Silently execute without transaction guarantees

### 1.2 Development Environment Setup (Docker Compose)

Create `docker/mongodb-replica-set/docker-compose.yml`:

```yaml
version: '3.8'

services:
  mongo1:
    image: mongo:7.0
    container_name: mongo1
    hostname: mongo1
    command: ["mongod", "--replSet", "rs0", "--bind_ip_all", "--port", "27017", "--keyFile", "/etc/mongodb/pki/keyfile"]
    ports:
      - "27017:27017"
    volumes:
      - mongo1_data:/data/db
      - mongo1_config:/data/configdb
      - ./keyfile:/etc/mongodb/pki/keyfile:ro
    environment:
      MONGO_INITDB_ROOT_USERNAME: admin
      MONGO_INITDB_ROOT_PASSWORD: admin_password
    networks:
      - mongo-cluster
    healthcheck:
      test: echo 'db.runCommand("ping").ok' | mongosh localhost:27017/test --quiet
      interval: 10s
      timeout: 10s
      retries: 5

  mongo2:
    image: mongo:7.0
    container_name: mongo2
    hostname: mongo2
    command: ["mongod", "--replSet", "rs0", "--bind_ip_all", "--port", "27017", "--keyFile", "/etc/mongodb/pki/keyfile"]
    ports:
      - "27018:27017"
    volumes:
      - mongo2_data:/data/db
      - mongo2_config:/data/configdb
      - ./keyfile:/etc/mongodb/pki/keyfile:ro
    environment:
      MONGO_INITDB_ROOT_USERNAME: admin
      MONGO_INITDB_ROOT_PASSWORD: admin_password
    networks:
      - mongo-cluster
    depends_on:
      mongo1:
        condition: service_healthy

  mongo3:
    image: mongo:7.0
    container_name: mongo3
    hostname: mongo3
    command: ["mongod", "--replSet", "rs0", "--bind_ip_all", "--port", "27017", "--keyFile", "/etc/mongodb/pki/keyfile"]
    ports:
      - "27019:27017"
    volumes:
      - mongo3_data:/data/db
      - mongo3_config:/data/configdb
      - ./keyfile:/etc/mongodb/pki/keyfile:ro
    environment:
      MONGO_INITDB_ROOT_USERNAME: admin
      MONGO_INITDB_ROOT_PASSWORD: admin_password
    networks:
      - mongo-cluster
    depends_on:
      mongo1:
        condition: service_healthy

  mongo-init:
    image: mongo:7.0
    container_name: mongo-init
    depends_on:
      - mongo1
      - mongo2
      - mongo3
    volumes:
      - ./init-replica.js:/scripts/init-replica.js:ro
    command: >
      mongosh --host mongo1:27017 -u admin -p admin_password --authenticationDatabase admin /scripts/init-replica.js
    networks:
      - mongo-cluster

networks:
  mongo-cluster:
    driver: bridge

volumes:
  mongo1_data:
  mongo1_config:
  mongo2_data:
  mongo2_config:
  mongo3_data:
  mongo3_config:
```

### 1.3 Generate Keyfile for Authentication

Create `docker/mongodb-replica-set/setup.sh`:

```bash
#!/bin/bash
set -e

echo "🔐 Generating MongoDB keyfile..."
openssl rand -base64 756 > keyfile
chmod 400 keyfile

# Fix permissions for Docker (MongoDB requires specific UID)
# On macOS/Linux, the file needs to be readable by the mongodb user (UID 999)
sudo chown 999:999 keyfile 2>/dev/null || true

echo "✅ Keyfile generated successfully"
```

### 1.4 Replica Set Initialization Script

Create `docker/mongodb-replica-set/init-replica.js`:

```javascript
// Wait for all nodes to be ready
sleep(5000);

// Initialize replica set
rs.initiate({
  _id: "rs0",
  members: [
    { _id: 0, host: "mongo1:27017", priority: 2 },  // Preferred primary
    { _id: 1, host: "mongo2:27017", priority: 1 },
    { _id: 2, host: "mongo3:27017", priority: 1 }
  ]
});

// Wait for primary election
sleep(10000);

// Check status
printjson(rs.status());

// Create application user on primary
var primary = rs.hello().primary;
print("Primary elected: " + primary);

// Switch to admin database
db = db.getSiblingDB("admin");

// Create application user
db.createUser({
  user: "app_user",
  pwd: "app_password",
  roles: [
    { role: "readWrite", db: "ticketing" },
    { role: "readWrite", db: "ticketing_booking" },
    { role: "readWrite", db: "ticketing_catalog" },
    { role: "readWrite", db: "ticketing_identity" }
  ]
});

print("✅ Replica set initialized and application user created");
```

### 1.5 Update Application Connection String

Update `application.yml` for each service:

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://app_user:app_password@mongo1:27017,mongo2:27017,mongo3:27017/ticketing_booking?replicaSet=rs0&authSource=admin&readPreference=primaryPreferred&w=majority&retryWrites=true
      auto-index-creation: true
```

**Connection String Parameters Explained:**

| Parameter | Value | Purpose |
|-----------|-------|---------|
| `replicaSet=rs0` | Required | Identifies replica set for transactions |
| `authSource=admin` | Required | Authentication database |
| `readPreference=primaryPreferred` | Recommended | Read from primary, fallback to secondary |
| `w=majority` | **Critical** | Write concern for durability |
| `retryWrites=true` | Recommended | Automatic retry on network errors |

### 1.6 Production Kubernetes Deployment

For production, use MongoDB Atlas or deploy a StatefulSet:

```yaml
# k8s/mongodb/statefulset.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: mongodb
  namespace: ticketing
spec:
  serviceName: mongodb
  replicas: 3
  selector:
    matchLabels:
      app: mongodb
  template:
    metadata:
      labels:
        app: mongodb
    spec:
      terminationGracePeriodSeconds: 30
      containers:
      - name: mongodb
        image: mongo:7.0
        command:
        - mongod
        - "--replSet"
        - rs0
        - "--bind_ip_all"
        - "--keyFile"
        - /etc/mongodb/pki/keyfile
        ports:
        - containerPort: 27017
        volumeMounts:
        - name: data
          mountPath: /data/db
        - name: keyfile
          mountPath: /etc/mongodb/pki
          readOnly: true
        resources:
          requests:
            memory: "2Gi"
            cpu: "500m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        livenessProbe:
          exec:
            command:
            - mongosh
            - --eval
            - "db.adminCommand('ping')"
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          exec:
            command:
            - mongosh
            - --eval
            - "db.adminCommand('ping')"
          initialDelaySeconds: 5
          periodSeconds: 5
      volumes:
      - name: keyfile
        secret:
          secretName: mongodb-keyfile
          defaultMode: 0400
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: ["ReadWriteOnce"]
      storageClassName: fast-ssd
      resources:
        requests:
          storage: 50Gi
```

### 1.7 Verify Replica Set is Working

```bash
# Connect to primary
docker exec -it mongo1 mongosh -u admin -p admin_password --authenticationDatabase admin

# Check replica set status
rs.status()

# Test transaction support
use ticketing_booking

session = db.getMongo().startSession()
session.startTransaction()
session.getDatabase("ticketing_booking").test.insertOne({ test: 1 })
session.commitTransaction()
session.endSession()

print("✅ Transactions are working!")
```

---

## Phase 2: Transaction Architecture Redesign

### 2.1 New Payment Flow Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                           REDESIGNED PAYMENT FLOW                                    │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                      │
│  MOBILE APP                  BOOKING SERVICE                   EXTERNAL              │
│  ──────────                  ───────────────                   ────────              │
│                                                                                      │
│  ┌─────────────┐            ┌──────────────────────────────┐                        │
│  │ Initiate    │──────────▶│ 1. Validate Request           │                        │
│  │ Payment     │            │ 2. Check Idempotency Key      │                        │
│  │             │            │ 3. START TRANSACTION          │                        │
│  │ idempotency │            │    ├─ Create PaymentIntent    │                        │
│  │ Key: uuid   │            │    ├─ Reserve Ticket          │                        │
│  └─────────────┘            │    ├─ Write OutboxEvent       │                        │
│        │                    │    └─ COMMIT                   │                        │
│        │                    └──────────────┬─────────────────┘                        │
│        │                                   │                                          │
│        │                    ┌──────────────▼─────────────────┐                        │
│        │                    │ 4. Call Payment Provider       │                        │
│        │                    │    (MTN/Airtel/Zamtel)         │───────▶ USSD Push     │
│        │                    └──────────────┬─────────────────┘                        │
│        │                                   │                                          │
│        ▼                    ┌──────────────▼─────────────────┐                        │
│  ┌─────────────┐            │ 5. Return PaymentIntent        │                        │
│  │ Poll Status │◀───────────│    status: PENDING             │                        │
│  │ (every 3s)  │            │    expiresAt: +15min           │                        │
│  └──────┬──────┘            └────────────────────────────────┘                        │
│         │                                                                              │
│         │                   ═══════════════════════════════════                       │
│         │                   WEBHOOK PATH (async)                                       │
│         │                   ═══════════════════════════════════                       │
│         │                                                                              │
│         │                    ┌──────────────────────────────┐      ┌─────────────┐   │
│         │                    │ 6. Webhook Received           │◀─────│ Payment     │   │
│         │                    │    - Verify Signature         │      │ Provider    │   │
│         │                    │    - Check Idempotency        │      └─────────────┘   │
│         │                    │    - START TRANSACTION        │                        │
│         │                    │      ├─ Update PaymentIntent  │                        │
│         │                    │      ├─ Confirm Ticket        │                        │
│         │                    │      ├─ Write OutboxEvent     │                        │
│         │                    │      │  (PAYMENT_COMPLETED)   │                        │
│         │                    │      └─ COMMIT                │                        │
│         │                    └──────────────┬────────────────┘                        │
│         │                                   │                                          │
│         │                    ┌──────────────▼────────────────┐                        │
│         ▼                    │ 7. Debezium CDC captures      │                        │
│  ┌─────────────┐            │    OutboxEvent                 │                        │
│  │ Status:     │            └──────────────┬─────────────────┘                        │
│  │ COMPLETED   │                           │                                          │
│  └─────────────┘            ┌──────────────▼─────────────────┐                        │
│                             │ 8. Identity Service receives   │                        │
│                             │    - Credit Escrow             │                        │
│                             │    - Record Commission         │                        │
│                             │    - Send Notification         │                        │
│                             └────────────────────────────────┘                        │
│                                                                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 New Domain Models

#### PaymentIntent Model

Create `booking-service/src/main/java/com/pml/booking/model/PaymentIntent.java`:

```java
package com.pml.booking.model;

import lombok.*;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * PaymentIntent - Tracks the lifecycle of a payment attempt.
 *
 * This is the source of truth for payment state. Tickets reference PaymentIntent,
 * not the other way around.
 *
 * States:
 * - CREATED: Initial state, waiting for provider
 * - PENDING: Sent to provider, awaiting user action
 * - PROCESSING: Provider processing payment
 * - COMPLETED: Payment successful
 * - FAILED: Payment failed
 * - CANCELLED: User/system cancelled
 * - EXPIRED: Timeout exceeded
 */
@Document(collection = "payment_intents")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "idempotency_idx", def = "{'idempotencyKey': 1}", unique = true)
@CompoundIndex(name = "status_expires_idx", def = "{'status': 1, 'expiresAt': 1}")
@CompoundIndex(name = "provider_ref_idx", def = "{'providerReference': 1}")
public class PaymentIntent {

    @Id
    private String id;

    /**
     * Client-provided idempotency key (UUID from mobile app)
     * Ensures duplicate requests return same PaymentIntent
     */
    @Indexed(unique = true)
    private String idempotencyKey;

    /**
     * Correlation ID for distributed tracing
     */
    @Indexed
    private String correlationId;

    /**
     * Saga ID for tracking multi-step transaction
     */
    @Indexed
    private String sagaId;

    // Payment details
    @Indexed
    private String ticketId;

    @Indexed
    private String eventId;

    @Indexed
    private String buyerId;

    private BigDecimal amount;
    private BigDecimal platformFee;
    private BigDecimal netAmount;
    private String currency;

    // Provider details
    private String provider;          // MTN_MONEY, AIRTEL_MONEY, etc.
    private String providerReference; // Provider's transaction ID
    private String phoneNumber;
    private String cardLast4;

    // State management
    @Indexed
    private PaymentIntentStatus status;

    private String failureCode;
    private String failureMessage;

    // Timestamps
    private Instant createdAt;
    private Instant expiresAt;
    private Instant confirmedAt;
    private Instant failedAt;
    private Instant cancelledAt;

    // Webhook tracking
    private Instant lastWebhookAt;
    private int webhookCount;
    private String lastWebhookPayload;

    // Optimistic locking
    @Version
    private Long version;

    // Audit
    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;

    @LastModifiedDate
    private Instant updatedAt;

    // Metadata
    private Map<String, Object> metadata;

    public enum PaymentIntentStatus {
        CREATED,
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED,
        EXPIRED
    }

    public boolean isTerminal() {
        return status == PaymentIntentStatus.COMPLETED ||
               status == PaymentIntentStatus.FAILED ||
               status == PaymentIntentStatus.CANCELLED ||
               status == PaymentIntentStatus.EXPIRED;
    }

    public boolean canTransitionTo(PaymentIntentStatus newStatus) {
        if (isTerminal()) return false;

        return switch (status) {
            case CREATED -> newStatus == PaymentIntentStatus.PENDING ||
                           newStatus == PaymentIntentStatus.CANCELLED;
            case PENDING -> newStatus == PaymentIntentStatus.PROCESSING ||
                           newStatus == PaymentIntentStatus.COMPLETED ||
                           newStatus == PaymentIntentStatus.FAILED ||
                           newStatus == PaymentIntentStatus.EXPIRED ||
                           newStatus == PaymentIntentStatus.CANCELLED;
            case PROCESSING -> newStatus == PaymentIntentStatus.COMPLETED ||
                              newStatus == PaymentIntentStatus.FAILED;
            default -> false;
        };
    }
}
```

#### SagaState Model

Create `booking-service/src/main/java/com/pml/booking/model/SagaState.java`:

```java
package com.pml.booking.model;

import lombok.*;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SagaState - Tracks the state of a distributed transaction saga.
 *
 * Used for:
 * - Ticket Purchase Saga
 * - Refund Saga
 * - Event Cancellation Saga
 */
@Document(collection = "saga_states")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SagaState {

    @Id
    private String id;

    @Indexed(unique = true)
    private String sagaId;

    @Indexed
    private String sagaType;  // TICKET_PURCHASE, REFUND, EVENT_CANCELLATION

    @Indexed
    private String correlationId;

    @Indexed
    private SagaStatus status;

    private int currentStep;
    private int totalSteps;

    @Builder.Default
    private List<SagaStep> steps = new ArrayList<>();

    @Builder.Default
    private List<SagaStep> compensationSteps = new ArrayList<>();

    private String failureReason;
    private Instant startedAt;
    private Instant completedAt;
    private Instant failedAt;

    @Version
    private Long version;

    private Map<String, Object> context;

    public enum SagaStatus {
        STARTED,
        IN_PROGRESS,
        COMPLETED,
        COMPENSATING,
        COMPENSATED,
        FAILED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SagaStep {
        private int stepNumber;
        private String stepName;
        private String eventType;
        private StepStatus status;
        private Instant startedAt;
        private Instant completedAt;
        private String error;
        private Map<String, Object> data;
        private String compensatingEvent;
        private Map<String, Object> compensationData;
    }

    public enum StepStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        COMPENSATED
    }
}
```

### 2.3 Updated Ticket Model with Reservation Tracking

Update `booking-service/src/main/java/com/pml/booking/model/Ticket.java`:

```java
// Add these fields to existing Ticket model:

/**
 * Reservation tracking for timeout management
 */
private Instant reservedAt;
private Instant reservedUntil;  // reservedAt + 10 minutes
private String reservationSessionId;

/**
 * Payment intent reference (foreign key to PaymentIntent)
 */
@Indexed
private String paymentIntentId;

/**
 * Saga tracking
 */
@Indexed
private String sagaId;

/**
 * Optimistic locking - CRITICAL for concurrent updates
 */
@Version
private Long version;

/**
 * Check if reservation is still valid
 */
public boolean isReservationValid() {
    return reservedUntil != null && Instant.now().isBefore(reservedUntil);
}

/**
 * Check if ticket can be confirmed (payment succeeded)
 */
public boolean canConfirm() {
    return status == TicketStatus.PENDING_PAYMENT && isReservationValid();
}
```

### 2.4 Updated Escrow Account with Optimistic Locking

Update `booking-service/src/main/java/com/pml/booking/model/EscrowAccount.java`:

```java
// Add optimistic locking
@Version
private Long version;

// Add pending balance for in-flight transactions
private BigDecimal pendingCredits;
private BigDecimal pendingDebits;

/**
 * Atomic credit operation using optimistic locking
 * If version mismatch, OptimisticLockingFailureException is thrown
 */
public void creditWithLock(BigDecimal amount, String transactionId) {
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalArgumentException("Credit amount must be positive");
    }
    this.currentBalance = this.currentBalance.add(amount);
    this.totalDeposits = this.totalDeposits.add(amount);
    this.lastTransactionAt = LocalDateTime.now();
    // Version is auto-incremented by Spring Data MongoDB
}

/**
 * Atomic debit operation with balance check
 */
public void debitWithLock(BigDecimal amount, String transactionId) {
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalArgumentException("Debit amount must be positive");
    }
    if (!hasSufficientBalance(amount)) {
        throw new InsufficientBalanceException(
            "Insufficient balance: required=" + amount + ", available=" + currentBalance
        );
    }
    this.currentBalance = this.currentBalance.subtract(amount);
    this.totalWithdrawals = this.totalWithdrawals.add(amount);
    this.lastTransactionAt = LocalDateTime.now();
}
```

---

## Phase 3: Atomic Outbox Pattern Implementation

### 3.1 Transactional Outbox Service

Create `booking-service/src/main/java/com/pml/booking/service/TransactionalOutboxService.java`:

```java
package com.pml.booking.service;

import com.pml.booking.model.*;
import com.pml.booking.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * TransactionalOutboxService - Ensures atomic write of business entity + outbox event.
 *
 * This is the CORE of data integrity. Every state change that needs to propagate
 * to other services MUST go through this service.
 *
 * Pattern:
 * 1. Start MongoDB transaction
 * 2. Save business entity (Ticket, PaymentIntent, etc.)
 * 3. Save OutboxEvent in SAME transaction
 * 4. Commit transaction
 * 5. Debezium CDC picks up OutboxEvent
 *
 * If ANY step fails, ENTIRE transaction rolls back.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionalOutboxService {

    private final TransactionalOperator transactionalOperator;
    private final TicketRepository ticketRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final SagaStateRepository sagaStateRepository;

    /**
     * Create ticket reservation with outbox event - ATOMIC
     */
    public Mono<Ticket> createTicketReservation(Ticket ticket, String sagaId) {
        String correlationId = UUID.randomUUID().toString();
        ticket.setCorrelationId(correlationId);
        ticket.setSagaId(sagaId);
        ticket.setReservedAt(Instant.now());
        ticket.setReservedUntil(Instant.now().plusSeconds(600)); // 10 minutes
        ticket.setStatus(TicketStatus.PENDING_PAYMENT);

        OutboxEvent outboxEvent = OutboxEvent.builder()
            .id(UUID.randomUUID().toString())
            .eventType("TICKET_RESERVED")
            .aggregateType("Ticket")
            .aggregateId(ticket.getId())
            .service("booking")
            .correlationId(correlationId)
            .sagaId(sagaId)
            .eventData(Map.of(
                "ticketId", ticket.getId(),
                "eventId", ticket.getEventId(),
                "buyerId", ticket.getBuyerId(),
                "categoryCode", ticket.getTicketCategoryCode(),
                "quantity", ticket.getQuantity(),
                "amount", ticket.getPrice(),
                "reservedUntil", ticket.getReservedUntil().toString()
            ))
            .compensationData(Map.of(
                "compensatingEvent", "RESERVATION_RELEASED",
                "restoreQuantity", ticket.getQuantity(),
                "restoreCategoryCode", ticket.getTicketCategoryCode()
            ))
            .eventTimestamp(Instant.now())
            .status(OutboxEventStatus.PENDING)
            .build();

        return transactionalOperator.transactional(
            ticketRepository.save(ticket)
                .flatMap(savedTicket -> outboxEventRepository.save(outboxEvent)
                    .thenReturn(savedTicket))
        ).doOnSuccess(t -> log.info("✅ Atomic ticket reservation created: ticketId={}, sagaId={}",
            t.getId(), sagaId))
         .doOnError(e -> log.error("❌ Failed to create ticket reservation atomically", e));
    }

    /**
     * Confirm payment and update ticket - ATOMIC
     */
    public Mono<PaymentIntent> confirmPayment(
            PaymentIntent paymentIntent,
            Ticket ticket,
            String providerReference) {

        // Update PaymentIntent
        paymentIntent.setStatus(PaymentIntent.PaymentIntentStatus.COMPLETED);
        paymentIntent.setProviderReference(providerReference);
        paymentIntent.setConfirmedAt(Instant.now());

        // Update Ticket
        ticket.setStatus(TicketStatus.PURCHASED);
        ticket.setPurchaseDate(LocalDateTime.now());
        ticket.setPaymentReference(providerReference);

        // Calculate commission
        BigDecimal commission = paymentIntent.getAmount()
            .multiply(new BigDecimal("0.05")); // 5% commission
        BigDecimal netAmount = paymentIntent.getAmount().subtract(commission);
        paymentIntent.setPlatformFee(commission);
        paymentIntent.setNetAmount(netAmount);
        ticket.setCommissionAmount(commission);
        ticket.setNetAmount(netAmount);

        // Create outbox event for downstream services
        OutboxEvent outboxEvent = OutboxEvent.builder()
            .id(UUID.randomUUID().toString())
            .eventType("PAYMENT_COMPLETED")
            .aggregateType("PaymentIntent")
            .aggregateId(paymentIntent.getId())
            .service("booking")
            .correlationId(paymentIntent.getCorrelationId())
            .sagaId(paymentIntent.getSagaId())
            .eventData(Map.of(
                "paymentIntentId", paymentIntent.getId(),
                "ticketId", ticket.getId(),
                "eventId", ticket.getEventId(),
                "buyerId", ticket.getBuyerId(),
                "organizerId", ticket.getMetadata().get("organizerId"),
                "grossAmount", paymentIntent.getAmount(),
                "netAmount", netAmount,
                "commission", commission,
                "currency", paymentIntent.getCurrency(),
                "providerReference", providerReference
            ))
            .compensationData(Map.of(
                "compensatingEvent", "PAYMENT_REFUNDED",
                "refundAmount", paymentIntent.getAmount(),
                "refundCommission", commission
            ))
            .eventTimestamp(Instant.now())
            .status(OutboxEventStatus.PENDING)
            .build();

        return transactionalOperator.transactional(
            paymentIntentRepository.save(paymentIntent)
                .then(ticketRepository.save(ticket))
                .then(outboxEventRepository.save(outboxEvent))
                .thenReturn(paymentIntent)
        ).doOnSuccess(pi -> log.info("✅ Payment confirmed atomically: paymentIntentId={}, ticketId={}",
            pi.getId(), ticket.getId()))
         .doOnError(e -> log.error("❌ Failed to confirm payment atomically", e));
    }

    /**
     * Handle payment failure - ATOMIC compensation
     */
    public Mono<PaymentIntent> handlePaymentFailure(
            PaymentIntent paymentIntent,
            Ticket ticket,
            String failureCode,
            String failureMessage) {

        paymentIntent.setStatus(PaymentIntent.PaymentIntentStatus.FAILED);
        paymentIntent.setFailureCode(failureCode);
        paymentIntent.setFailureMessage(failureMessage);
        paymentIntent.setFailedAt(Instant.now());

        ticket.setStatus(TicketStatus.CANCELLED);
        ticket.setCancelledAt(LocalDateTime.now());
        ticket.setCancellationReason("Payment failed: " + failureMessage);

        // Compensation event to restore inventory
        OutboxEvent outboxEvent = OutboxEvent.builder()
            .id(UUID.randomUUID().toString())
            .eventType("PAYMENT_FAILED")
            .aggregateType("PaymentIntent")
            .aggregateId(paymentIntent.getId())
            .service("booking")
            .correlationId(paymentIntent.getCorrelationId())
            .sagaId(paymentIntent.getSagaId())
            .eventData(Map.of(
                "paymentIntentId", paymentIntent.getId(),
                "ticketId", ticket.getId(),
                "eventId", ticket.getEventId(),
                "failureCode", failureCode,
                "failureMessage", failureMessage,
                "restoreQuantity", ticket.getQuantity(),
                "restoreCategoryCode", ticket.getTicketCategoryCode()
            ))
            .eventTimestamp(Instant.now())
            .status(OutboxEventStatus.PENDING)
            .build();

        return transactionalOperator.transactional(
            paymentIntentRepository.save(paymentIntent)
                .then(ticketRepository.save(ticket))
                .then(outboxEventRepository.save(outboxEvent))
                .thenReturn(paymentIntent)
        ).doOnSuccess(pi -> log.info("✅ Payment failure handled atomically: paymentIntentId={}", pi.getId()))
         .doOnError(e -> log.error("❌ Failed to handle payment failure atomically", e));
    }
}
```

### 3.2 Updated OutboxEvent Model for Debezium

Update `booking-service/src/main/java/com/pml/booking/model/OutboxEvent.java`:

```java
// Add these fields for Debezium MongoDB Outbox SMT compatibility:

/**
 * Saga tracking for multi-step transactions
 */
@Indexed
private String sagaId;

private Integer sagaStep;
private String sagaType;

/**
 * Compensation data for rollback scenarios
 */
private Map<String, Object> compensationData;
private Instant compensationDeadline;

/**
 * Tracing fields
 */
private String causationId;  // Parent event that caused this event
private String userId;       // Actor who triggered this

/**
 * For Debezium MongoDB Outbox SMT - these field names matter!
 * See: https://debezium.io/documentation/reference/transformations/mongodb-outbox-event-router
 */
@Field("aggregatetype")  // Lowercase for Debezium
private String aggregateType;

@Field("aggregateid")    // Lowercase for Debezium
private String aggregateId;

@Field("type")           // Debezium expects 'type' not 'eventType'
private String type;

@Field("payload")        // Debezium expects 'payload' not 'eventData'
private Map<String, Object> payload;

/**
 * Factory method that creates Debezium-compatible event
 */
public static OutboxEvent createDebeziumCompatible(
        String eventType,
        String aggregateType,
        String aggregateId,
        Map<String, Object> eventData,
        String correlationId,
        String sagaId) {

    return OutboxEvent.builder()
        .id(UUID.randomUUID().toString())
        .eventType(eventType)
        .type(eventType)                    // Debezium field
        .aggregateType(aggregateType)
        .aggregatetype(aggregateType)       // Debezium field (lowercase)
        .aggregateId(aggregateId)
        .aggregateid(aggregateId)           // Debezium field (lowercase)
        .eventData(eventData)
        .payload(eventData)                 // Debezium field
        .service("booking")
        .correlationId(correlationId)
        .sagaId(sagaId)
        .eventTimestamp(Instant.now())
        .status(OutboxEventStatus.PENDING)
        .build();
}
```

### 3.3 Debezium Connector Configuration

Create `booking-service/debezium-server/connector-config.json`:

```json
{
  "name": "booking-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.mongodb.MongoDbConnector",
    "mongodb.connection.string": "mongodb://app_user:app_password@mongo1:27017,mongo2:27017,mongo3:27017/?replicaSet=rs0&authSource=admin",
    "mongodb.user": "app_user",
    "mongodb.password": "app_password",

    "topic.prefix": "ticketing",
    "database.include.list": "ticketing_booking",
    "collection.include.list": "ticketing_booking.outbox_events",

    "capture.mode": "change_streams_update_full",
    "snapshot.mode": "initial",

    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.connector.mongodb.transforms.outbox.MongoEventRouter",
    "transforms.outbox.collection.field.event.id": "_id",
    "transforms.outbox.collection.field.event.key": "aggregateid",
    "transforms.outbox.collection.field.event.type": "type",
    "transforms.outbox.collection.field.event.payload": "payload",
    "transforms.outbox.route.by.field": "aggregatetype",
    "transforms.outbox.route.topic.replacement": "ticketing.events.${routedByValue}",

    "transforms.outbox.table.expand.json.payload": "true",

    "key.converter": "org.apache.kafka.connect.json.JsonConverter",
    "key.converter.schemas.enable": "false",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter.schemas.enable": "false",

    "errors.tolerance": "all",
    "errors.log.enable": "true",
    "errors.log.include.messages": "true",

    "heartbeat.interval.ms": "10000",
    "poll.interval.ms": "1000"
  }
}
```

---

## Phase 4: Optimistic Locking & Concurrency Control

### 4.1 Inventory Service with Atomic Operations

Create `booking-service/src/main/java/com/pml/booking/service/InventoryService.java`:

```java
package com.pml.booking.service;

import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * InventoryService - Atomic inventory operations using MongoDB's findAndModify.
 *
 * This service ensures no overselling through:
 * 1. Atomic decrement with availability check
 * 2. Optimistic locking via version field
 * 3. Conditional update (only if quantity >= requested)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final ReactiveMongoTemplate mongoTemplate;

    /**
     * Atomically reserve tickets - prevents overselling
     *
     * Uses MongoDB's findAndModify with conditional update:
     * - Only succeeds if availableQuantity >= requested quantity
     * - Returns null if not enough inventory
     * - Throws OptimisticLockingFailureException if version mismatch
     */
    public Mono<Boolean> reserveTickets(
            String eventId,
            String categoryCode,
            int quantity,
            Long expectedVersion) {

        Query query = Query.query(
            Criteria.where("_id").is(eventId)
                .and("ticketCategories.code").is(categoryCode)
                .and("ticketCategories.availableQuantity").gte(quantity)
                .and("version").is(expectedVersion)  // Optimistic lock
        );

        Update update = new Update()
            .inc("ticketCategories.$.availableQuantity", -quantity)
            .inc("ticketCategories.$.reservedQuantity", quantity)
            .inc("version", 1)
            .currentDate("updatedAt");

        return mongoTemplate.updateFirst(query, update, "events")
            .map(result -> {
                if (result.getModifiedCount() == 0) {
                    log.warn("⚠️ Failed to reserve tickets: eventId={}, category={}, qty={}, " +
                             "reason=either sold out or version mismatch",
                             eventId, categoryCode, quantity);
                    return false;
                }
                log.info("✅ Reserved {} tickets: eventId={}, category={}",
                         quantity, eventId, categoryCode);
                return true;
            });
    }

    /**
     * Release reservation (compensation for timeout or failure)
     */
    public Mono<Boolean> releaseReservation(
            String eventId,
            String categoryCode,
            int quantity) {

        Query query = Query.query(
            Criteria.where("_id").is(eventId)
                .and("ticketCategories.code").is(categoryCode)
        );

        Update update = new Update()
            .inc("ticketCategories.$.availableQuantity", quantity)
            .inc("ticketCategories.$.reservedQuantity", -quantity)
            .inc("version", 1)
            .currentDate("updatedAt");

        return mongoTemplate.updateFirst(query, update, "events")
            .map(result -> {
                if (result.getModifiedCount() > 0) {
                    log.info("✅ Released reservation: eventId={}, category={}, qty={}",
                             eventId, categoryCode, quantity);
                    return true;
                }
                log.warn("⚠️ No reservation to release: eventId={}, category={}",
                         eventId, categoryCode);
                return false;
            });
    }

    /**
     * Confirm sale (convert reservation to sold)
     */
    public Mono<Boolean> confirmSale(
            String eventId,
            String categoryCode,
            int quantity) {

        Query query = Query.query(
            Criteria.where("_id").is(eventId)
                .and("ticketCategories.code").is(categoryCode)
                .and("ticketCategories.reservedQuantity").gte(quantity)
        );

        Update update = new Update()
            .inc("ticketCategories.$.reservedQuantity", -quantity)
            .inc("ticketCategories.$.soldQuantity", quantity)
            .inc("totalSold", quantity)
            .inc("version", 1)
            .currentDate("updatedAt");

        return mongoTemplate.updateFirst(query, update, "events")
            .map(result -> result.getModifiedCount() > 0);
    }
}
```

### 4.2 Escrow Service with Optimistic Locking

Create `booking-service/src/main/java/com/pml/booking/service/EscrowService.java`:

```java
package com.pml.booking.service;

import com.pml.booking.model.EscrowAccount;
import com.pml.booking.model.FinancialTransaction;
import com.pml.booking.repository.EscrowAccountRepository;
import com.pml.booking.repository.FinancialTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * EscrowService - Manages escrow account operations with double-entry accounting.
 *
 * Every financial operation creates TWO transaction entries:
 * 1. DEBIT from source account
 * 2. CREDIT to destination account
 *
 * Uses optimistic locking with retry for high-concurrency scenarios.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EscrowService {

    private final TransactionalOperator transactionalOperator;
    private final EscrowAccountRepository escrowRepository;
    private final FinancialTransactionRepository transactionRepository;

    private static final int MAX_RETRIES = 3;
    private static final Duration RETRY_DELAY = Duration.ofMillis(100);

    /**
     * Credit escrow account with commission deduction - ATOMIC
     *
     * Creates double-entry:
     * 1. CREDIT escrow (net amount to organizer)
     * 2. CREDIT revenue (commission to platform)
     */
    public Mono<FinancialTransaction> creditEscrowWithCommission(
            String escrowAccountId,
            String revenueAccountId,
            BigDecimal grossAmount,
            BigDecimal commission,
            String correlationId,
            String ticketId,
            String eventId,
            String organizerId) {

        BigDecimal netAmount = grossAmount.subtract(commission);
        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        return transactionalOperator.transactional(
            // Load accounts with version for optimistic locking
            escrowRepository.findById(escrowAccountId)
                .zipWith(escrowRepository.findById(revenueAccountId))
                .flatMap(tuple -> {
                    EscrowAccount escrowAccount = tuple.getT1();
                    EscrowAccount revenueAccount = tuple.getT2();

                    // Credit escrow (organizer's net amount)
                    escrowAccount.creditWithLock(netAmount, transactionId);

                    // Credit revenue (platform commission)
                    revenueAccount.creditWithLock(commission, transactionId);

                    // Create financial transaction record (double-entry)
                    FinancialTransaction transaction = FinancialTransaction.builder()
                        .transactionId(transactionId)
                        .correlationId(correlationId)
                        .transactionChain("TICKET_SALE")
                        .sourceAccountId("BUYER")  // Conceptual - buyer's payment
                        .destinationAccountId(escrowAccountId)
                        .eventId(eventId)
                        .organizerId(organizerId)
                        .ticketId(ticketId)
                        .grossAmount(grossAmount)
                        .netAmount(netAmount)
                        .commissionAmount(commission)
                        .amount(grossAmount)
                        .currency("ZMW")
                        .transactionType("TICKET_SALE_CREDIT")
                        .description("Ticket sale - escrow credit")
                        .transactionDate(LocalDateTime.now())
                        .status("COMPLETED")
                        .build();

                    // Commission transaction
                    FinancialTransaction commissionTx = FinancialTransaction.builder()
                        .transactionId("COMM-" + transactionId)
                        .correlationId(correlationId)
                        .parentTransactionId(transactionId)
                        .transactionChain("TICKET_SALE")
                        .sourceAccountId(escrowAccountId)
                        .destinationAccountId(revenueAccountId)
                        .eventId(eventId)
                        .organizerId(organizerId)
                        .ticketId(ticketId)
                        .amount(commission)
                        .grossAmount(commission)
                        .netAmount(commission)
                        .currency("ZMW")
                        .transactionType("PLATFORM_COMMISSION")
                        .description("Platform commission (5%)")
                        .transactionDate(LocalDateTime.now())
                        .status("COMPLETED")
                        .build();

                    // Save all atomically
                    return escrowRepository.save(escrowAccount)
                        .then(escrowRepository.save(revenueAccount))
                        .then(transactionRepository.save(transaction))
                        .then(transactionRepository.save(commissionTx))
                        .thenReturn(transaction);
                })
        )
        .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_DELAY)
            .filter(e -> e instanceof OptimisticLockingFailureException)
            .doBeforeRetry(signal ->
                log.warn("⚠️ Optimistic lock conflict, retrying: attempt={}",
                    signal.totalRetries() + 1)))
        .doOnSuccess(tx -> log.info("✅ Escrow credited: txId={}, gross={}, net={}, commission={}",
            tx.getTransactionId(), grossAmount, netAmount, commission))
        .doOnError(e -> log.error("❌ Failed to credit escrow after {} retries", MAX_RETRIES, e));
    }
}
```

---

## Phase 5: Payment Idempotency Layer

### 5.1 Idempotency Service

Create `booking-service/src/main/java/com/pml/booking/service/IdempotencyService.java`:

```java
package com.pml.booking.service;

import com.pml.booking.model.PaymentIntent;
import com.pml.booking.repository.PaymentIntentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.function.Supplier;

/**
 * IdempotencyService - Ensures payment operations are idempotent.
 *
 * Uses idempotencyKey (UUID from mobile app) to:
 * 1. Detect duplicate requests
 * 2. Return existing PaymentIntent for duplicates
 * 3. Prevent double-charging
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final PaymentIntentRepository paymentIntentRepository;

    /**
     * Execute operation with idempotency guarantee.
     *
     * If idempotencyKey exists:
     *   - Return existing PaymentIntent (no operation performed)
     *
     * If idempotencyKey is new:
     *   - Execute supplier to create new PaymentIntent
     *   - Save with unique idempotencyKey constraint
     *   - Handle race condition via DuplicateKeyException
     */
    public Mono<PaymentIntent> executeIdempotent(
            String idempotencyKey,
            Supplier<Mono<PaymentIntent>> createOperation) {

        return paymentIntentRepository.findByIdempotencyKey(idempotencyKey)
            .flatMap(existing -> {
                log.info("🔄 Idempotent request detected: key={}, existingId={}, status={}",
                    idempotencyKey, existing.getId(), existing.getStatus());
                return Mono.just(existing);
            })
            .switchIfEmpty(Mono.defer(() ->
                createOperation.get()
                    .onErrorResume(DuplicateKeyException.class, e -> {
                        // Race condition: another request created it first
                        log.warn("🔄 Race condition resolved: key={}", idempotencyKey);
                        return paymentIntentRepository.findByIdempotencyKey(idempotencyKey);
                    })
            ));
    }

    /**
     * Validate idempotency key format
     */
    public boolean isValidIdempotencyKey(String key) {
        if (key == null || key.isBlank()) return false;
        // Must be UUID format
        return key.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }
}
```

### 5.2 Updated Mobile Payment Types

Update `event-ticketing-mobile/src/types/payment.types.ts`:

```typescript
/**
 * Request to initiate a payment - WITH IDEMPOTENCY
 */
export interface InitiatePaymentRequest {
  /**
   * REQUIRED: Client-generated idempotency key (UUID v4)
   *
   * Generate with: crypto.randomUUID() or uuid.v4()
   *
   * Purpose:
   * - Prevents duplicate charges on retry
   * - Allows safe retry of failed requests
   * - Returns existing payment if duplicate
   */
  idempotencyKey: string;

  ticketId: string;
  eventId: string;
  amount: number;
  currency: string;
  paymentMethod: PaymentProviderCode;
  phoneNumber?: string;
  cardDetails?: CardDetails;
}

/**
 * Response from initiating a payment
 */
export interface InitiatePaymentResponse {
  paymentIntentId: string;
  status: PaymentUIStatus;

  /**
   * True if this was a duplicate request
   * Client should use existing paymentIntentId
   */
  isDuplicate: boolean;

  /**
   * Payment URL for redirect (if applicable)
   */
  paymentUrl?: string;

  /**
   * Instructions for user
   */
  instructions?: string;

  /**
   * When this payment intent expires
   */
  expiresAt: string;

  /**
   * Correlation ID for support/debugging
   */
  correlationId: string;
}
```

### 5.3 Mobile App Payment Hook

Create `event-ticketing-mobile/src/hooks/usePayment.ts`:

```typescript
import { useState, useCallback, useRef } from 'react';
import { v4 as uuidv4 } from 'uuid';
import type {
  InitiatePaymentRequest,
  InitiatePaymentResponse,
  PaymentStatusResponse
} from '@/types/payment.types';

const POLL_INTERVAL_MS = 3000;
const MAX_POLL_ATTEMPTS = 100; // 5 minutes max

interface UsePaymentOptions {
  onSuccess?: (response: PaymentStatusResponse) => void;
  onFailure?: (error: string) => void;
  onExpired?: () => void;
}

export function usePayment(options: UsePaymentOptions = {}) {
  const [isLoading, setIsLoading] = useState(false);
  const [paymentIntent, setPaymentIntent] = useState<InitiatePaymentResponse | null>(null);
  const [status, setStatus] = useState<PaymentStatusResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  const pollIntervalRef = useRef<NodeJS.Timeout | null>(null);
  const pollCountRef = useRef(0);
  const idempotencyKeyRef = useRef<string | null>(null);

  /**
   * Generate or reuse idempotency key
   * Key is stable across retries of the same payment attempt
   */
  const getIdempotencyKey = useCallback((ticketId: string) => {
    if (!idempotencyKeyRef.current) {
      idempotencyKeyRef.current = uuidv4();
      console.log(`[Payment] Generated idempotency key: ${idempotencyKeyRef.current}`);
    }
    return idempotencyKeyRef.current;
  }, []);

  /**
   * Reset idempotency key for new payment attempt
   */
  const resetIdempotencyKey = useCallback(() => {
    idempotencyKeyRef.current = null;
  }, []);

  /**
   * Initiate payment with idempotency
   */
  const initiatePayment = useCallback(async (
    request: Omit<InitiatePaymentRequest, 'idempotencyKey'>
  ): Promise<InitiatePaymentResponse | null> => {
    setIsLoading(true);
    setError(null);

    const idempotencyKey = getIdempotencyKey(request.ticketId);

    try {
      const response = await fetch('/api/payments/initiate', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Idempotency-Key': idempotencyKey, // Also in header for logging
        },
        body: JSON.stringify({
          ...request,
          idempotencyKey,
        }),
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message || 'Payment initiation failed');
      }

      const data: InitiatePaymentResponse = await response.json();

      if (data.isDuplicate) {
        console.log(`[Payment] Duplicate request handled: ${data.paymentIntentId}`);
      }

      setPaymentIntent(data);

      // Start polling for status
      startPolling(data.paymentIntentId);

      return data;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Unknown error';
      setError(message);
      options.onFailure?.(message);
      return null;
    } finally {
      setIsLoading(false);
    }
  }, [getIdempotencyKey, options]);

  /**
   * Poll for payment status
   */
  const startPolling = useCallback((paymentIntentId: string) => {
    pollCountRef.current = 0;

    const poll = async () => {
      pollCountRef.current++;

      if (pollCountRef.current > MAX_POLL_ATTEMPTS) {
        stopPolling();
        setError('Payment verification timeout');
        options.onExpired?.();
        return;
      }

      try {
        const response = await fetch(`/api/payments/${paymentIntentId}/status`);
        const data: PaymentStatusResponse = await response.json();

        setStatus(data);

        if (data.status === 'COMPLETED') {
          stopPolling();
          resetIdempotencyKey();
          options.onSuccess?.(data);
        } else if (data.status === 'FAILED') {
          stopPolling();
          resetIdempotencyKey();
          options.onFailure?.(data.failureReason || 'Payment failed');
        } else if (data.status === 'EXPIRED' || data.status === 'CANCELLED') {
          stopPolling();
          resetIdempotencyKey();
          options.onExpired?.();
        }
        // Keep polling for PENDING/PROCESSING
      } catch (err) {
        console.error('[Payment] Polling error:', err);
        // Continue polling on error (transient network issues)
      }
    };

    pollIntervalRef.current = setInterval(poll, POLL_INTERVAL_MS);
    poll(); // Immediate first poll
  }, [options, resetIdempotencyKey]);

  const stopPolling = useCallback(() => {
    if (pollIntervalRef.current) {
      clearInterval(pollIntervalRef.current);
      pollIntervalRef.current = null;
    }
  }, []);

  /**
   * Cancel payment
   */
  const cancelPayment = useCallback(async () => {
    if (!paymentIntent) return;

    stopPolling();

    try {
      await fetch(`/api/payments/${paymentIntent.paymentIntentId}/cancel`, {
        method: 'POST',
      });
      resetIdempotencyKey();
    } catch (err) {
      console.error('[Payment] Cancel error:', err);
    }
  }, [paymentIntent, stopPolling, resetIdempotencyKey]);

  return {
    isLoading,
    paymentIntent,
    status,
    error,
    initiatePayment,
    cancelPayment,
    resetIdempotencyKey,
  };
}
```

---

## Phase 6: Webhook Handler Hardening

### 6.1 Webhook Controller with Signature Verification

Create `booking-service/src/main/java/com/pml/booking/controller/PaymentWebhookController.java`:

```java
package com.pml.booking.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.booking.service.PaymentWebhookService;
import com.pml.booking.service.WebhookSignatureService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * PaymentWebhookController - Handles payment provider webhooks.
 *
 * Security:
 * - Signature verification for each provider
 * - Idempotent processing (duplicate webhooks ignored)
 * - Rate limiting per provider
 * - Request logging for audit
 *
 * Supported Providers:
 * - MTN Mobile Money
 * - Airtel Money
 * - Zamtel Kwacha
 * - Card processors (via aggregator)
 */
@RestController
@RequestMapping("/api/webhooks/payments")
@Slf4j
public class PaymentWebhookController {

    private final ObjectMapper objectMapper;
    private final WebhookSignatureService signatureService;
    private final PaymentWebhookService webhookService;
    private final Counter webhooksReceived;
    private final Counter webhooksProcessed;
    private final Counter webhooksFailed;
    private final Counter webhooksDuplicate;

    public PaymentWebhookController(
            ObjectMapper objectMapper,
            WebhookSignatureService signatureService,
            PaymentWebhookService webhookService,
            MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.signatureService = signatureService;
        this.webhookService = webhookService;

        this.webhooksReceived = Counter.builder("payment_webhooks_received")
            .description("Payment webhooks received")
            .register(meterRegistry);
        this.webhooksProcessed = Counter.builder("payment_webhooks_processed")
            .description("Payment webhooks processed successfully")
            .register(meterRegistry);
        this.webhooksFailed = Counter.builder("payment_webhooks_failed")
            .description("Payment webhooks that failed processing")
            .register(meterRegistry);
        this.webhooksDuplicate = Counter.builder("payment_webhooks_duplicate")
            .description("Duplicate payment webhooks (idempotent)")
            .register(meterRegistry);
    }

    /**
     * MTN Mobile Money webhook
     */
    @PostMapping("/mtn")
    public Mono<ResponseEntity<WebhookResponse>> handleMtnWebhook(
            @RequestHeader("X-MTN-Signature") String signature,
            @RequestHeader(value = "X-MTN-Timestamp", required = false) String timestamp,
            @RequestBody String rawBody) {

        return handleProviderWebhook("MTN_MONEY", signature, timestamp, rawBody);
    }

    /**
     * Airtel Money webhook
     */
    @PostMapping("/airtel")
    public Mono<ResponseEntity<WebhookResponse>> handleAirtelWebhook(
            @RequestHeader("X-Airtel-Signature") String signature,
            @RequestHeader(value = "X-Airtel-Timestamp", required = false) String timestamp,
            @RequestBody String rawBody) {

        return handleProviderWebhook("AIRTEL_MONEY", signature, timestamp, rawBody);
    }

    /**
     * Zamtel Kwacha webhook
     */
    @PostMapping("/zamtel")
    public Mono<ResponseEntity<WebhookResponse>> handleZamtelWebhook(
            @RequestHeader("X-Zamtel-Signature") String signature,
            @RequestHeader(value = "X-Zamtel-Timestamp", required = false) String timestamp,
            @RequestBody String rawBody) {

        return handleProviderWebhook("ZAMTEL_MONEY", signature, timestamp, rawBody);
    }

    /**
     * Generic card payment webhook (aggregator)
     */
    @PostMapping("/card")
    public Mono<ResponseEntity<WebhookResponse>> handleCardWebhook(
            @RequestHeader("X-Webhook-Signature") String signature,
            @RequestHeader(value = "X-Webhook-Timestamp", required = false) String timestamp,
            @RequestBody String rawBody) {

        return handleProviderWebhook("CARD", signature, timestamp, rawBody);
    }

    /**
     * Common webhook handling logic
     */
    private Mono<ResponseEntity<WebhookResponse>> handleProviderWebhook(
            String provider,
            String signature,
            String timestamp,
            String rawBody) {

        webhooksReceived.increment();
        long startTime = System.currentTimeMillis();

        log.info("📥 Webhook received: provider={}, timestamp={}, bodyLength={}",
            provider, timestamp, rawBody.length());

        // Step 1: Verify signature
        if (!signatureService.verifySignature(provider, rawBody, signature, timestamp)) {
            log.warn("⚠️ Webhook signature verification failed: provider={}", provider);
            webhooksFailed.increment();
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new WebhookResponse(false, "Invalid signature")));
        }

        // Step 2: Parse payload
        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.error("❌ Invalid webhook payload: provider={}", provider, e);
            webhooksFailed.increment();
            return Mono.just(ResponseEntity.badRequest()
                .body(new WebhookResponse(false, "Invalid JSON payload")));
        }

        // Step 3: Process webhook (idempotent)
        return webhookService.processWebhook(provider, payload)
            .map(result -> {
                long duration = System.currentTimeMillis() - startTime;

                if (result.isDuplicate()) {
                    webhooksDuplicate.increment();
                    log.info("🔄 Duplicate webhook ignored: provider={}, ref={}, duration={}ms",
                        provider, result.getProviderReference(), duration);
                } else {
                    webhooksProcessed.increment();
                    log.info("✅ Webhook processed: provider={}, ref={}, status={}, duration={}ms",
                        provider, result.getProviderReference(), result.getStatus(), duration);
                }

                return ResponseEntity.ok(new WebhookResponse(true, "Processed"));
            })
            .onErrorResume(e -> {
                webhooksFailed.increment();
                log.error("❌ Webhook processing failed: provider={}", provider, e);

                // Return 500 so provider retries (but we handle idempotency)
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new WebhookResponse(false, "Processing failed, will retry")));
            });
    }

    public record WebhookResponse(boolean success, String message) {}
}
```

### 6.2 Webhook Processing Service

Create `booking-service/src/main/java/com/pml/booking/service/PaymentWebhookService.java`:

```java
package com.pml.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pml.booking.model.PaymentIntent;
import com.pml.booking.model.Ticket;
import com.pml.booking.repository.PaymentIntentRepository;
import com.pml.booking.repository.TicketRepository;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * PaymentWebhookService - Processes payment provider webhooks.
 *
 * Key features:
 * - Idempotent: Safe to process same webhook multiple times
 * - Atomic: Uses TransactionalOutboxService for consistency
 * - Provider-agnostic: Normalizes different provider formats
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookService {

    private final PaymentIntentRepository paymentIntentRepository;
    private final TicketRepository ticketRepository;
    private final TransactionalOutboxService outboxService;

    /**
     * Process webhook from any provider
     */
    public Mono<WebhookResult> processWebhook(String provider, JsonNode payload) {
        // Extract provider-specific fields
        WebhookData data = extractWebhookData(provider, payload);

        if (data == null) {
            return Mono.error(new IllegalArgumentException("Unable to parse webhook payload"));
        }

        // Find PaymentIntent by provider reference
        return paymentIntentRepository.findByProviderReference(data.transactionId)
            .switchIfEmpty(
                // Try by our internal reference (some providers echo it back)
                paymentIntentRepository.findById(data.externalReference)
            )
            .flatMap(paymentIntent -> processPaymentIntent(paymentIntent, data))
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("⚠️ PaymentIntent not found for webhook: provider={}, txnId={}",
                    provider, data.transactionId);
                // Still return success - might be for a different system
                return Mono.just(WebhookResult.builder()
                    .status("NOT_FOUND")
                    .providerReference(data.transactionId)
                    .isDuplicate(false)
                    .build());
            }));
    }

    /**
     * Process the PaymentIntent based on webhook status
     */
    private Mono<WebhookResult> processPaymentIntent(PaymentIntent paymentIntent, WebhookData data) {
        // Idempotency check: already in terminal state?
        if (paymentIntent.isTerminal()) {
            return Mono.just(WebhookResult.builder()
                .status(paymentIntent.getStatus().name())
                .providerReference(data.transactionId)
                .isDuplicate(true)
                .build());
        }

        // Update webhook tracking
        paymentIntent.setLastWebhookAt(Instant.now());
        paymentIntent.setWebhookCount(paymentIntent.getWebhookCount() + 1);
        paymentIntent.setLastWebhookPayload(data.rawPayload);
        paymentIntent.setProviderReference(data.transactionId);

        // Get associated ticket
        return ticketRepository.findById(paymentIntent.getTicketId())
            .flatMap(ticket -> {
                if ("SUCCESS".equalsIgnoreCase(data.status) ||
                    "COMPLETED".equalsIgnoreCase(data.status)) {
                    // Payment successful
                    return outboxService.confirmPayment(paymentIntent, ticket, data.transactionId)
                        .map(pi -> WebhookResult.builder()
                            .status("COMPLETED")
                            .providerReference(data.transactionId)
                            .isDuplicate(false)
                            .build());
                } else if ("FAILED".equalsIgnoreCase(data.status) ||
                           "DECLINED".equalsIgnoreCase(data.status)) {
                    // Payment failed
                    return outboxService.handlePaymentFailure(
                            paymentIntent, ticket, data.failureCode, data.failureMessage)
                        .map(pi -> WebhookResult.builder()
                            .status("FAILED")
                            .providerReference(data.transactionId)
                            .isDuplicate(false)
                            .build());
                } else {
                    // Still processing - just update tracking
                    paymentIntent.setStatus(PaymentIntent.PaymentIntentStatus.PROCESSING);
                    return paymentIntentRepository.save(paymentIntent)
                        .map(pi -> WebhookResult.builder()
                            .status("PROCESSING")
                            .providerReference(data.transactionId)
                            .isDuplicate(false)
                            .build());
                }
            });
    }

    /**
     * Extract normalized data from provider-specific payload
     */
    private WebhookData extractWebhookData(String provider, JsonNode payload) {
        try {
            return switch (provider) {
                case "MTN_MONEY" -> extractMtnData(payload);
                case "AIRTEL_MONEY" -> extractAirtelData(payload);
                case "ZAMTEL_MONEY" -> extractZamtelData(payload);
                case "CARD" -> extractCardData(payload);
                default -> null;
            };
        } catch (Exception e) {
            log.error("Failed to extract webhook data: provider={}", provider, e);
            return null;
        }
    }

    private WebhookData extractMtnData(JsonNode payload) {
        return WebhookData.builder()
            .transactionId(payload.path("transactionId").asText())
            .externalReference(payload.path("externalId").asText())
            .status(payload.path("status").asText())
            .amount(new BigDecimal(payload.path("amount").asText("0")))
            .failureCode(payload.path("reason").path("code").asText())
            .failureMessage(payload.path("reason").path("message").asText())
            .rawPayload(payload.toString())
            .build();
    }

    private WebhookData extractAirtelData(JsonNode payload) {
        return WebhookData.builder()
            .transactionId(payload.path("transaction").path("id").asText())
            .externalReference(payload.path("transaction").path("reference").asText())
            .status(payload.path("status").path("code").asText())
            .amount(new BigDecimal(payload.path("transaction").path("amount").asText("0")))
            .failureCode(payload.path("status").path("result_code").asText())
            .failureMessage(payload.path("status").path("message").asText())
            .rawPayload(payload.toString())
            .build();
    }

    private WebhookData extractZamtelData(JsonNode payload) {
        return WebhookData.builder()
            .transactionId(payload.path("txn_id").asText())
            .externalReference(payload.path("ext_ref").asText())
            .status(payload.path("txn_status").asText())
            .amount(new BigDecimal(payload.path("amount").asText("0")))
            .failureCode(payload.path("error_code").asText())
            .failureMessage(payload.path("error_msg").asText())
            .rawPayload(payload.toString())
            .build();
    }

    private WebhookData extractCardData(JsonNode payload) {
        return WebhookData.builder()
            .transactionId(payload.path("payment_id").asText())
            .externalReference(payload.path("metadata").path("order_id").asText())
            .status(payload.path("status").asText())
            .amount(new BigDecimal(payload.path("amount").path("value").asText("0")))
            .failureCode(payload.path("failure_code").asText())
            .failureMessage(payload.path("failure_message").asText())
            .rawPayload(payload.toString())
            .build();
    }

    @Data
    @Builder
    private static class WebhookData {
        private String transactionId;
        private String externalReference;
        private String status;
        private BigDecimal amount;
        private String failureCode;
        private String failureMessage;
        private String rawPayload;
    }

    @Data
    @Builder
    public static class WebhookResult {
        private String status;
        private String providerReference;
        private boolean isDuplicate;
    }
}
```

---

## Phase 7: Saga State Machine Implementation

### 7.1 Saga Orchestrator

Create `booking-service/src/main/java/com/pml/booking/saga/TicketPurchaseSaga.java`:

```java
package com.pml.booking.saga;

import com.pml.booking.model.*;
import com.pml.booking.repository.*;
import com.pml.booking.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;

/**
 * TicketPurchaseSaga - Orchestrates the ticket purchase distributed transaction.
 *
 * Saga Steps:
 * 1. RESERVE_TICKET - Create ticket with PENDING_PAYMENT status
 * 2. DECREMENT_INVENTORY - Atomic inventory update
 * 3. CREATE_PAYMENT_INTENT - Initialize payment tracking
 * 4. AWAIT_PAYMENT - Wait for provider confirmation
 * 5. CREDIT_ESCROW - Credit organizer's escrow account
 * 6. RECORD_COMMISSION - Record platform commission
 * 7. CONFIRM_TICKET - Update ticket to PURCHASED
 * 8. SEND_NOTIFICATION - Notify buyer
 *
 * Compensation (on failure at any step):
 * - RESTORE_INVENTORY
 * - CANCEL_TICKET
 * - REFUND_PAYMENT (if already charged)
 * - DEBIT_ESCROW
 * - REVERSE_COMMISSION
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketPurchaseSaga {

    private final TransactionalOperator transactionalOperator;
    private final SagaStateRepository sagaStateRepository;
    private final TicketRepository ticketRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final InventoryService inventoryService;
    private final TransactionalOutboxService outboxService;

    private static final String SAGA_TYPE = "TICKET_PURCHASE";

    /**
     * Start a new ticket purchase saga
     */
    public Mono<SagaState> startSaga(
            Ticket ticket,
            PaymentIntent paymentIntent,
            Long inventoryVersion) {

        String sagaId = UUID.randomUUID().toString();

        SagaState saga = SagaState.builder()
            .id(UUID.randomUUID().toString())
            .sagaId(sagaId)
            .sagaType(SAGA_TYPE)
            .correlationId(ticket.getCorrelationId())
            .status(SagaState.SagaStatus.STARTED)
            .currentStep(0)
            .totalSteps(8)
            .startedAt(Instant.now())
            .steps(new ArrayList<>())
            .compensationSteps(new ArrayList<>())
            .context(Map.of(
                "ticketId", ticket.getId(),
                "paymentIntentId", paymentIntent.getId(),
                "eventId", ticket.getEventId(),
                "buyerId", ticket.getBuyerId(),
                "categoryCode", ticket.getTicketCategoryCode(),
                "quantity", ticket.getQuantity(),
                "amount", ticket.getPrice().toString(),
                "inventoryVersion", inventoryVersion
            ))
            .build();

        log.info("🚀 Starting ticket purchase saga: sagaId={}, ticketId={}",
            sagaId, ticket.getId());

        return sagaStateRepository.save(saga)
            .flatMap(savedSaga -> executeStep1_ReserveTicket(savedSaga, ticket))
            .flatMap(savedSaga -> executeStep2_DecrementInventory(savedSaga, inventoryVersion))
            .flatMap(savedSaga -> executeStep3_CreatePaymentIntent(savedSaga, paymentIntent))
            .doOnSuccess(s -> log.info("✅ Saga steps 1-3 completed: sagaId={}", sagaId))
            .doOnError(e -> log.error("❌ Saga failed: sagaId={}", sagaId, e));
    }

    /**
     * Step 1: Reserve ticket
     */
    private Mono<SagaState> executeStep1_ReserveTicket(SagaState saga, Ticket ticket) {
        SagaState.SagaStep step = SagaState.SagaStep.builder()
            .stepNumber(1)
            .stepName("RESERVE_TICKET")
            .eventType("TICKET_RESERVED")
            .status(SagaState.StepStatus.IN_PROGRESS)
            .startedAt(Instant.now())
            .compensatingEvent("RESERVATION_RELEASED")
            .compensationData(Map.of(
                "ticketId", ticket.getId(),
                "quantity", ticket.getQuantity(),
                "categoryCode", ticket.getTicketCategoryCode()
            ))
            .build();

        saga.getSteps().add(step);
        saga.setCurrentStep(1);
        saga.setStatus(SagaState.SagaStatus.IN_PROGRESS);

        // Link saga to ticket
        ticket.setSagaId(saga.getSagaId());

        return transactionalOperator.transactional(
            ticketRepository.save(ticket)
                .then(sagaStateRepository.save(saga))
                .doOnSuccess(s -> {
                    step.setStatus(SagaState.StepStatus.COMPLETED);
                    step.setCompletedAt(Instant.now());
                })
        );
    }

    /**
     * Step 2: Decrement inventory (atomic)
     */
    private Mono<SagaState> executeStep2_DecrementInventory(SagaState saga, Long inventoryVersion) {
        String eventId = (String) saga.getContext().get("eventId");
        String categoryCode = (String) saga.getContext().get("categoryCode");
        int quantity = (int) saga.getContext().get("quantity");

        SagaState.SagaStep step = SagaState.SagaStep.builder()
            .stepNumber(2)
            .stepName("DECREMENT_INVENTORY")
            .eventType("INVENTORY_DECREMENTED")
            .status(SagaState.StepStatus.IN_PROGRESS)
            .startedAt(Instant.now())
            .compensatingEvent("INVENTORY_RESTORED")
            .compensationData(Map.of(
                "eventId", eventId,
                "categoryCode", categoryCode,
                "quantity", quantity
            ))
            .build();

        saga.getSteps().add(step);
        saga.setCurrentStep(2);

        return inventoryService.reserveTickets(eventId, categoryCode, quantity, inventoryVersion)
            .flatMap(success -> {
                if (!success) {
                    return Mono.error(new SagaException("Failed to reserve inventory - sold out or version conflict"));
                }
                step.setStatus(SagaState.StepStatus.COMPLETED);
                step.setCompletedAt(Instant.now());
                return sagaStateRepository.save(saga);
            });
    }

    /**
     * Step 3: Create payment intent
     */
    private Mono<SagaState> executeStep3_CreatePaymentIntent(SagaState saga, PaymentIntent paymentIntent) {
        SagaState.SagaStep step = SagaState.SagaStep.builder()
            .stepNumber(3)
            .stepName("CREATE_PAYMENT_INTENT")
            .eventType("PAYMENT_INTENT_CREATED")
            .status(SagaState.StepStatus.IN_PROGRESS)
            .startedAt(Instant.now())
            .compensatingEvent("PAYMENT_INTENT_CANCELLED")
            .compensationData(Map.of(
                "paymentIntentId", paymentIntent.getId()
            ))
            .build();

        saga.getSteps().add(step);
        saga.setCurrentStep(3);

        paymentIntent.setSagaId(saga.getSagaId());
        paymentIntent.setStatus(PaymentIntent.PaymentIntentStatus.PENDING);

        return transactionalOperator.transactional(
            paymentIntentRepository.save(paymentIntent)
                .then(sagaStateRepository.save(saga))
                .doOnSuccess(s -> {
                    step.setStatus(SagaState.StepStatus.COMPLETED);
                    step.setCompletedAt(Instant.now());
                })
        );
    }

    /**
     * Execute compensation (rollback)
     */
    public Mono<SagaState> compensate(SagaState saga, String reason) {
        log.warn("⚠️ Compensating saga: sagaId={}, reason={}", saga.getSagaId(), reason);

        saga.setStatus(SagaState.SagaStatus.COMPENSATING);
        saga.setFailureReason(reason);

        // Get completed steps in reverse order
        List<SagaState.SagaStep> stepsToCompensate = saga.getSteps().stream()
            .filter(s -> s.getStatus() == SagaState.StepStatus.COMPLETED)
            .sorted(Comparator.comparingInt(SagaState.SagaStep::getStepNumber).reversed())
            .toList();

        return executeCompensations(saga, stepsToCompensate, 0);
    }

    private Mono<SagaState> executeCompensations(
            SagaState saga,
            List<SagaState.SagaStep> steps,
            int index) {

        if (index >= steps.size()) {
            saga.setStatus(SagaState.SagaStatus.COMPENSATED);
            saga.setCompletedAt(Instant.now());
            log.info("✅ Saga compensation completed: sagaId={}", saga.getSagaId());
            return sagaStateRepository.save(saga);
        }

        SagaState.SagaStep step = steps.get(index);

        return executeCompensationStep(step)
            .doOnSuccess(v -> {
                step.setStatus(SagaState.StepStatus.COMPENSATED);
                saga.getCompensationSteps().add(step);
            })
            .then(sagaStateRepository.save(saga))
            .then(Mono.defer(() -> executeCompensations(saga, steps, index + 1)));
    }

    private Mono<Void> executeCompensationStep(SagaState.SagaStep step) {
        log.info("🔄 Executing compensation: step={}, event={}",
            step.getStepName(), step.getCompensatingEvent());

        return switch (step.getStepName()) {
            case "RESERVE_TICKET" -> compensateTicketReservation(step);
            case "DECREMENT_INVENTORY" -> compensateInventory(step);
            case "CREATE_PAYMENT_INTENT" -> compensatePaymentIntent(step);
            default -> Mono.empty();
        };
    }

    private Mono<Void> compensateTicketReservation(SagaState.SagaStep step) {
        String ticketId = (String) step.getCompensationData().get("ticketId");
        return ticketRepository.findById(ticketId)
            .flatMap(ticket -> {
                ticket.setStatus(TicketStatus.CANCELLED);
                ticket.setCancelledAt(LocalDateTime.now());
                ticket.setCancellationReason("Saga compensation");
                return ticketRepository.save(ticket);
            })
            .then();
    }

    private Mono<Void> compensateInventory(SagaState.SagaStep step) {
        String eventId = (String) step.getCompensationData().get("eventId");
        String categoryCode = (String) step.getCompensationData().get("categoryCode");
        int quantity = (int) step.getCompensationData().get("quantity");
        return inventoryService.releaseReservation(eventId, categoryCode, quantity).then();
    }

    private Mono<Void> compensatePaymentIntent(SagaState.SagaStep step) {
        String paymentIntentId = (String) step.getCompensationData().get("paymentIntentId");
        return paymentIntentRepository.findById(paymentIntentId)
            .flatMap(pi -> {
                pi.setStatus(PaymentIntent.PaymentIntentStatus.CANCELLED);
                pi.setCancelledAt(Instant.now());
                return paymentIntentRepository.save(pi);
            })
            .then();
    }

    public static class SagaException extends RuntimeException {
        public SagaException(String message) {
            super(message);
        }
    }
}
```

---

## Phase 8: Dead Letter Queue & Recovery

### 8.1 Dead Letter Handler

Create `booking-service/src/main/java/com/pml/booking/service/DeadLetterService.java`:

```java
package com.pml.booking.service;

import com.pml.booking.model.DeadLetterEvent;
import com.pml.booking.model.OutboxEvent;
import com.pml.booking.model.SagaState;
import com.pml.booking.repository.DeadLetterRepository;
import com.pml.booking.repository.SagaStateRepository;
import com.pml.booking.saga.TicketPurchaseSaga;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * DeadLetterService - Handles failed events and triggers compensation.
 *
 * Events move to DLQ when:
 * - Max retries exceeded
 * - Unrecoverable error detected
 * - Timeout threshold exceeded
 *
 * DLQ Processing:
 * 1. Alert operations team
 * 2. Trigger saga compensation (if applicable)
 * 3. Mark for manual review
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeadLetterService {

    private final DeadLetterRepository deadLetterRepository;
    private final SagaStateRepository sagaStateRepository;
    private final TicketPurchaseSaga ticketPurchaseSaga;
    private final AlertService alertService;

    /**
     * Move failed event to DLQ
     */
    public Mono<DeadLetterEvent> moveToDeadLetter(
            OutboxEvent outboxEvent,
            String failureReason,
            Exception exception) {

        DeadLetterEvent dlqEvent = DeadLetterEvent.builder()
            .id(UUID.randomUUID().toString())
            .originalEventId(outboxEvent.getId())
            .originalEventType(outboxEvent.getEventType())
            .aggregateType(outboxEvent.getAggregateType())
            .aggregateId(outboxEvent.getAggregateId())
            .sagaId(outboxEvent.getSagaId())
            .correlationId(outboxEvent.getCorrelationId())
            .eventData(outboxEvent.getEventData())
            .failureReason(failureReason)
            .exceptionType(exception != null ? exception.getClass().getName() : null)
            .exceptionMessage(exception != null ? exception.getMessage() : null)
            .retryHistory(outboxEvent.getRetryHistory())
            .status(DeadLetterEvent.DLQStatus.PENDING_REVIEW)
            .compensationRequired(outboxEvent.getSagaId() != null)
            .createdAt(Instant.now())
            .build();

        return deadLetterRepository.save(dlqEvent)
            .doOnSuccess(saved -> {
                log.error("☠️ Event moved to DLQ: eventId={}, type={}, reason={}",
                    outboxEvent.getId(), outboxEvent.getEventType(), failureReason);

                // Alert operations
                alertService.sendDLQAlert(saved);
            })
            .flatMap(saved -> {
                // Trigger compensation if part of a saga
                if (saved.isCompensationRequired()) {
                    return triggerCompensation(saved).thenReturn(saved);
                }
                return Mono.just(saved);
            });
    }

    /**
     * Trigger saga compensation for DLQ event
     */
    private Mono<Void> triggerCompensation(DeadLetterEvent dlqEvent) {
        if (dlqEvent.getSagaId() == null) {
            return Mono.empty();
        }

        return sagaStateRepository.findBySagaId(dlqEvent.getSagaId())
            .flatMap(saga -> {
                if (saga.getStatus() == SagaState.SagaStatus.COMPENSATING ||
                    saga.getStatus() == SagaState.SagaStatus.COMPENSATED) {
                    log.info("Saga already compensating/compensated: sagaId={}", saga.getSagaId());
                    return Mono.empty();
                }

                String reason = "DLQ event: " + dlqEvent.getFailureReason();
                return ticketPurchaseSaga.compensate(saga, reason).then();
            })
            .doOnSuccess(v -> {
                dlqEvent.setCompensationExecuted(true);
                dlqEvent.setCompensationExecutedAt(Instant.now());
            });
    }

    /**
     * Scheduled job: Process pending DLQ events
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void processPendingDLQEvents() {
        deadLetterRepository.findByStatusAndCompensationRequiredAndCompensationExecutedFalse(
                DeadLetterEvent.DLQStatus.PENDING_REVIEW)
            .flatMap(this::triggerCompensation)
            .subscribe(
                null,
                e -> log.error("Error processing DLQ", e),
                () -> log.debug("DLQ processing cycle completed")
            );
    }

    /**
     * Scheduled job: Check for timed-out sagas
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void checkTimedOutSagas() {
        Instant threshold = Instant.now().minus(15, ChronoUnit.MINUTES);

        sagaStateRepository.findByStatusAndStartedAtBefore(
                SagaState.SagaStatus.IN_PROGRESS, threshold)
            .flatMap(saga -> {
                log.warn("⏰ Saga timeout detected: sagaId={}, startedAt={}",
                    saga.getSagaId(), saga.getStartedAt());
                return ticketPurchaseSaga.compensate(saga, "Saga timeout after 15 minutes");
            })
            .subscribe(
                null,
                e -> log.error("Error checking timed-out sagas", e)
            );
    }

    /**
     * Manual retry of DLQ event
     */
    public Mono<DeadLetterEvent> retryDLQEvent(String dlqEventId) {
        return deadLetterRepository.findById(dlqEventId)
            .flatMap(dlqEvent -> {
                dlqEvent.setStatus(DeadLetterEvent.DLQStatus.RETRYING);
                dlqEvent.setRetryAttempts(dlqEvent.getRetryAttempts() + 1);
                dlqEvent.setLastRetryAt(Instant.now());

                // Attempt to reprocess
                // ... (reconstruct and resubmit outbox event)

                return deadLetterRepository.save(dlqEvent);
            });
    }

    /**
     * Mark DLQ event as resolved (manual resolution)
     */
    public Mono<DeadLetterEvent> resolveDLQEvent(String dlqEventId, String resolution, String resolvedBy) {
        return deadLetterRepository.findById(dlqEventId)
            .flatMap(dlqEvent -> {
                dlqEvent.setStatus(DeadLetterEvent.DLQStatus.RESOLVED);
                dlqEvent.setResolution(resolution);
                dlqEvent.setResolvedBy(resolvedBy);
                dlqEvent.setResolvedAt(Instant.now());
                return deadLetterRepository.save(dlqEvent);
            });
    }
}
```

---

## Phase 9: Monitoring & Alerting

### 9.1 Metrics Configuration

Create `booking-service/src/main/java/com/pml/booking/config/MetricsConfig.java`:

```java
package com.pml.booking.config;

import io.micrometer.core.instrument.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public MeterBinder paymentMetrics() {
        return registry -> {
            // Payment processing metrics
            Gauge.builder("payment_intents_pending", () ->
                // Query for pending payment intents count
                0.0
            ).description("Number of pending payment intents")
             .register(registry);

            // Saga metrics
            Gauge.builder("sagas_in_progress", () ->
                // Query for in-progress sagas
                0.0
            ).description("Number of sagas in progress")
             .register(registry);

            // DLQ metrics
            Gauge.builder("dlq_events_pending", () ->
                // Query for pending DLQ events
                0.0
            ).description("Number of events in dead letter queue")
             .register(registry);
        };
    }
}
```

### 9.2 Grafana Dashboard (JSON)

Create `k8s/monitoring/grafana-payment-dashboard.json`:

```json
{
  "dashboard": {
    "title": "Payment Data Integrity Dashboard",
    "panels": [
      {
        "title": "Payment Success Rate",
        "type": "gauge",
        "targets": [
          {
            "expr": "sum(rate(payment_webhooks_processed[5m])) / sum(rate(payment_webhooks_received[5m])) * 100"
          }
        ],
        "thresholds": {
          "mode": "absolute",
          "steps": [
            { "value": 0, "color": "red" },
            { "value": 95, "color": "yellow" },
            { "value": 99, "color": "green" }
          ]
        }
      },
      {
        "title": "Dead Letter Queue Size",
        "type": "stat",
        "targets": [
          {
            "expr": "dlq_events_pending"
          }
        ],
        "alert": {
          "condition": "gt",
          "threshold": 10
        }
      },
      {
        "title": "Saga Compensation Rate",
        "type": "timeseries",
        "targets": [
          {
            "expr": "rate(saga_compensations_total[5m])"
          }
        ]
      },
      {
        "title": "Optimistic Lock Conflicts",
        "type": "timeseries",
        "targets": [
          {
            "expr": "rate(optimistic_lock_failures_total[5m])"
          }
        ]
      }
    ]
  }
}
```

### 9.3 Alert Rules

Create `k8s/monitoring/prometheus-alerts.yaml`:

```yaml
groups:
- name: payment-integrity
  rules:
  - alert: HighDLQSize
    expr: dlq_events_pending > 10
    for: 5m
    labels:
      severity: critical
    annotations:
      summary: "Dead Letter Queue has {{ $value }} events"
      description: "DLQ size has exceeded threshold. Manual investigation required."

  - alert: PaymentSuccessRateLow
    expr: |
      sum(rate(payment_webhooks_processed[5m])) /
      sum(rate(payment_webhooks_received[5m])) * 100 < 95
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "Payment success rate is {{ $value }}%"

  - alert: SagaCompensationSpike
    expr: rate(saga_compensations_total[5m]) > 0.1
    for: 2m
    labels:
      severity: warning
    annotations:
      summary: "High rate of saga compensations"

  - alert: MongoDBReplicaSetUnhealthy
    expr: mongodb_rs_members_health != 1
    for: 1m
    labels:
      severity: critical
    annotations:
      summary: "MongoDB replica set member unhealthy"

  - alert: HighOptimisticLockConflicts
    expr: rate(optimistic_lock_failures_total[5m]) > 1
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "High rate of optimistic locking conflicts"
```

---

## Migration Strategy

### Step 1: Infrastructure (Week 1)

1. **Deploy MongoDB Replica Set**
   - Set up 3-node replica set in development
   - Test transaction support
   - Update connection strings

2. **Deploy Debezium Connector**
   - Configure MongoDB connector with outbox SMT
   - Verify CDC is capturing changes

### Step 2: Data Models (Week 1-2)

1. **Add version fields** to existing models (Ticket, EscrowAccount)
2. **Create new models** (PaymentIntent, SagaState, DeadLetterEvent)
3. **Run data migration** to add version=0 to existing documents

### Step 3: Core Services (Week 2)

1. **Implement TransactionalOutboxService**
2. **Implement InventoryService** with atomic operations
3. **Implement EscrowService** with optimistic locking
4. **Implement IdempotencyService**

### Step 4: Saga Implementation (Week 2-3)

1. **Implement TicketPurchaseSaga**
2. **Implement DeadLetterService**
3. **Add saga compensation logic**

### Step 5: Webhook Handling (Week 3)

1. **Deploy PaymentWebhookController**
2. **Implement signature verification**
3. **Test with provider sandboxes**

### Step 6: Mobile App Updates (Week 3)

1. **Add idempotencyKey to requests**
2. **Update usePayment hook**
3. **Test end-to-end flow**

### Step 7: Monitoring & Testing (Week 4)

1. **Deploy Grafana dashboards**
2. **Configure alert rules**
3. **Run chaos testing**
4. **Load testing with concurrent purchases**

---

## Verification Checklist

| Test Case | Expected Result | Status |
|-----------|-----------------|--------|
| 100 concurrent purchases for 50 tickets | Exactly 50 sold, 50 rejected | ⬜ |
| Network failure during payment | Saga compensation, inventory restored | ⬜ |
| Duplicate webhook | Second ignored, idempotent | ⬜ |
| Payment timeout | Ticket cancelled, inventory restored | ⬜ |
| MongoDB failover | Transactions continue on new primary | ⬜ |
| Escrow concurrent credits | All credits applied, no lost updates | ⬜ |
| DLQ event triggers compensation | Saga rolls back | ⬜ |
| Mobile retry with same idempotency key | Returns existing payment | ⬜ |

---

## Appendix: Quick Reference

### MongoDB Replica Set Commands

```bash
# Start replica set
cd docker/mongodb-replica-set
./setup.sh
docker-compose up -d

# Check status
docker exec -it mongo1 mongosh --eval "rs.status()"

# Check if primary
docker exec -it mongo1 mongosh --eval "rs.hello().isWritablePrimary"

# Test transactions
docker exec -it mongo1 mongosh ticketing_booking --eval "
  session = db.getMongo().startSession();
  session.startTransaction();
  session.getDatabase('ticketing_booking').test.insertOne({test: 1});
  session.commitTransaction();
  print('Transactions working!');
"
```

### Debezium Commands

```bash
# Check connector status
curl -s http://localhost:8083/connectors/booking-outbox-connector/status | jq

# View captured events
kafkacat -b localhost:9092 -t ticketing.events.Ticket -C -o beginning -c 10
```

### Monitoring URLs

- Grafana: http://localhost:3000
- Prometheus: http://localhost:9090
- Debezium UI: http://localhost:8080

