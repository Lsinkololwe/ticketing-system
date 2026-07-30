# ET-PLT-003 · Event contract — two tiers, the envelope, the outbox, idempotent consumers

> **Conformance** · PDI Phase 3 atomic outbox · PDI Phase 8 dead-letter queue and recovery

## 1. Capability

Three services have to agree about facts they cannot see directly. Booking must know that
an event was published before it can hold inventory for it; catalog must know a ticket was
sold before it can show a sales figure; identity must know a payout settled before it can
tell an organizer. None of them may read another's collections
([ET-PLT-002](../002-persistence-baseline/) R2), so every one of those agreements is
carried by a message, and the platform's consistency is exactly as good as its message
delivery.

This spec fixes how that delivery works. It declares the two tiers — Spring Modulith
inside a service, Azure Service Bus between services — and, more importantly, the line
between them, because the failure this platform cannot survive is a message that was sent
about a transaction that then rolled back. It declares the envelope every cross-service
message carries, the closed registry of every wire name the platform publishes, the
topics and subscriptions they travel on, the deduplication every consumer performs
because at-least-once delivery is real, and what happens to a message that cannot be
processed.

The central decision is that **Spring Modulith's event publication registry is the
outbox, for both tiers**. A write and its intent to publish commit together in one
PostgreSQL transaction; the listener that actually reaches Azure Service Bus runs after
that commit and is retried from the registry if the process dies mid-publish. That is the
transactional outbox pattern, already implemented, already running, already recovered on
restart — and it needs no change-data-capture pipeline to obtain.

It delivers no domain behaviour. Its success criterion is that a service killed between
its database commit and its bus publish republishes on restart, that the same message
delivered twice changes the consumer's state once, and that a message which can never be
processed lands somewhere an operator can see it.

## 2. Design decisions

**Two tiers, and the choice between them is not a preference.**

| | Intra-service | Cross-service |
|---|---|---|
| Transport | Spring Modulith over PostgreSQL | Azure Service Bus |
| Publish | `ApplicationEventPublisher.publishEvent` | `StreamBridge.send` |
| Consume | `@ApplicationModuleListener` | `@Bean Consumer<Message<…>>` |
| Guarantee | recorded in the publishing transaction, retried on restart | at-least-once, dead-lettered on repeated failure |
| Ordering | per publication, in order | **none**, unless a session key is set |
| Payload | the Java event type | the §4 envelope |

Use the first when publisher and consumer are in one service; the second when they are
not. A cross-service event that a module in the same service also needs is published
once, as a module event, and the bus publication is one of that module event's listeners.

**Spring Modulith's registry is the outbox, including for the bus.** The pattern is:
write the document and publish the module event inside one transaction; an
`@ApplicationModuleListener` — which by definition runs after that transaction commits —
is what calls `StreamBridge`. If the process dies before the listener completes, the row
is still `INCOMPLETE` in `event_publication` and republishes at startup. This is a
transactional outbox with recovery, obtained from a table the platform already runs.

**Change-data-capture is rejected.** PDI Phase 3 proposes an outbox collection in MongoDB
drained by Debezium into Kafka Connect. It would work. It also introduces a second
delivery pipeline, a connector to operate, a Kafka cluster the platform otherwise has no
use for, and a MongoDB oplog dependency — to obtain a guarantee Modulith already gives
over PostgreSQL. The cost is not the code; it is that there would then be two answers to
"how does a message get out of this service", and the wrong one would be used by someone
in a hurry.

**Never `StreamBridge.send` inside a transactional method.** The bus has no rollback. A
publish that succeeds inside a transaction that then fails has told three services about a
ticket that does not exist, and there is no compensating action because nobody knows to
take one. This is a lint rule, not a review note.

**Never let a delivery failure throw out of a listener.** A provider being down, a
consumer being slow, a downstream 503 — these are business outcomes. Throwing dead-letters
a message whose only problem is that somebody else's server is having a bad minute, and
the message that lands in the dead-letter queue is then indistinguishable from one that is
genuinely malformed. Record the failure as state or as a further event; let the retry
policy retry, and let the dead-letter queue mean *this message can never be processed*.

**Cross-service messages carry an envelope, not a bare payload.** `eventId` for
deduplication, `eventType` for routing without deserialising, `occurredAt` from the
`Clock`, `correlationId` and `causationId` so a support question about one ticket can be
answered by following one chain, and `schemaVersion` so the consumer can refuse a shape it
does not understand instead of silently binding a subset of it. The Java class name is
**not** the wire name: renaming a class must not break a consumer.

**Wire names are `{context}.{PastTense}` and are versioned by an integer counter.**
`booking.TicketPurchased` v`1`. Past tense because an event is a fact that has already
happened — if the name will not go into the past tense, it is a command and does not
belong on this bus. The version starts at `1` and increments only on a breaking change;
additive fields do not bump it, because a consumer binding a subset is exactly what the
envelope is for.

**Every consumer deduplicates on `eventId`.** At-least-once means every handler runs twice
eventually, and the second run is not detectable from the payload. The guard is
`SET evt:seen:{consumer}:{eventId} NX EX 604800` in Redis, taken *before* the work and
released only by expiry — plus, for any consumer whose effect is not naturally idempotent,
a durable marker in its own write. Redis alone is a fast path, not a guarantee: a Redis
eviction must not be able to cause a double credit.

**One topic per publishing service; one subscription per consuming service.**
`catalog-events`, `booking-events`, `identity-events`, and a subscription named for the
consumer. A consumer that wants two publishers' events subscribes twice. Filtering happens
at the subscription with a SQL filter on `eventType`, so a service is not woken by every
message its neighbour publishes.

**Ordering is not assumed, and where it is required it is bought explicitly.** Service Bus
delivers unordered across a topic. Handlers must be commutative wherever they can be —
counters use `$inc`, not assignment. Where order genuinely matters, the message sets a
session id (the aggregate's id) and the subscription is session-enabled, which serialises
that key and only that key.

**No personal data in a cross-service payload.** A payload carries identifiers; the
consumer resolves what it needs over the graph. A phone number in a message body is a
phone number in the bus's storage, in the dead-letter queue, and in whatever an operator
copies out of it while debugging — none of which
[ET-PLT-008](../008-data-protection/) can erase.

**Rejected alternatives**

- *One topic for the whole platform.* Every service woken by every message, and a subscription filter as the only thing standing between a catalog deploy and booking's throughput.
- *Kafka instead of Service Bus.* A better log, and a second broker to run for a platform whose cross-service volume is thousands per day, not millions per second.
- *CDC over a MongoDB outbox collection (PDI Phase 3).* See above — a second pipeline for a guarantee already held.
- *The Java FQN as the wire name.* Makes a package move a breaking change for three services.
- *Consumers deduplicating in Redis alone.* Correct until an eviction, and the failure mode is a double credit nobody notices for a month.
- *Semantic versioning on the wire (`2.1`).* Implies compatibility rules nobody encodes. A counter, plus an envelope consumers can bind a subset of, says the same thing honestly.
- *Publishing the full entity in the payload so consumers need no callback.* Every consumer then depends on the publisher's whole shape, and the payload is where PII leaks.

## 3. Requirements

### ET-PLT-003-R1 · A write and its publication commit together, and survive a crash

WHEN a service writes a document that other components must learn about, THE SYSTEM SHALL
record the intent to publish in the same transaction, and IF the process terminates before
the message is delivered, THEN THE SYSTEM SHALL republish it on restart.

**Acceptance**
- [ ] `spring-modulith-starter-jdbc` is on every service classpath and the `modulith_events` schema exists
- [ ] `spring.modulith.events.jdbc.schema-initialization.enabled` and `spring.modulith.republish-outstanding-events-on-restart=true` are set in every service
- [ ] Every domain write publishes its module event through `ApplicationEventPublisher` inside the same `@Transactional` method
- [ ] An integration test kills the service between the database commit and the bus publish, restarts it, and observes exactly one message on the topic
- [ ] `event_publication` rows are marked complete on success, and a completed-event retention window is configured rather than left unbounded

### ET-PLT-003-R2 · The bus is never reached from inside a transaction

THE SYSTEM SHALL publish to Azure Service Bus only from an `@ApplicationModuleListener` or
a scheduled drain, and SHALL NOT call `StreamBridge` from a transactional method.

**Acceptance**
- [ ] No method annotated `@Transactional`, and no method it calls, invokes `StreamBridge.send`
- [ ] Every cross-service publication happens in a class whose only role is publication, listening to a module event
- [ ] A test that forces a rollback after the module event is published observes no message on the topic
- [ ] `./scripts/spec-lint.sh --events` exits 0

### ET-PLT-003-R3 · Every cross-service message carries the envelope

THE SYSTEM SHALL wrap every cross-service payload in the §4 envelope, and IF a consumer
receives a `schemaVersion` it does not support, THEN THE SYSTEM SHALL dead-letter the
message rather than bind a partial payload.

**Acceptance**
- [ ] `EventEnvelope` is a record in `shared-library` with exactly the components of §4
- [ ] Every published message carries a non-null `eventId`, `eventType`, `occurredAt`, `correlationId` and `schemaVersion`
- [ ] `occurredAt` is read from the injected `Clock`, never inline
- [ ] `eventType` equals the wire name in the §4 registry — the Java class name appears nowhere on the wire
- [ ] `causationId` carries the `eventId` of the message that caused this one, and is null only for a message caused by a user action
- [ ] A consumer receiving an unsupported `schemaVersion` dead-letters with reason `UNSUPPORTED_SCHEMA_VERSION` and does not partially apply the payload
- [ ] No envelope payload contains a phone number, email address, physical address, national ID or full name

### ET-PLT-003-R4 · Every wire name is a row of the registry

THE SYSTEM SHALL publish only the wire names in §4, and each SHALL travel on the topic and
be consumed by the subscriptions that registry names.

**Acceptance**
- [ ] Every `StreamBridge.send` in the platform names a binding that maps to a §4 topic
- [ ] Every `eventType` published is a §4 registry row; no publisher invents one
- [ ] Every consumer named in a registry row exists, and every consumer that exists is named in a row
- [ ] Wire names are `{context}.{PastTense}` — a name that will not read as a completed fact fails review
- [ ] Adding an event changes this spec's §4 in the same commit as the publisher

### ET-PLT-003-R5 · Every consumer is idempotent under redelivery

WHILE the bus delivers at least once, THE SYSTEM SHALL apply each message's effect exactly
once per consumer.

**Acceptance**
- [ ] Every consumer takes `SET evt:seen:{consumer}:{eventId} NX EX 604800` before performing work and skips when the key exists
- [ ] Every consumer whose effect is not naturally idempotent additionally carries a durable marker — a unique index or an `upsert` keyed on `eventId` — so a Redis eviction cannot cause a second application
- [ ] Counter updates use `$inc`, never read-modify-write, so ordering does not change the result
- [ ] An integration test delivers the same message twice to each consumer and asserts the resulting state is identical to a single delivery
- [ ] An integration test delivers two causally related messages in reverse order and asserts the end state is correct

### ET-PLT-003-R6 · A failing consumer retries, and a poisonous one is visible

IF a consumer fails transiently, THEN THE SYSTEM SHALL retry with backoff, and IF it fails
past the retry budget, THEN THE SYSTEM SHALL dead-letter the message and raise its depth as
an alert.

**Acceptance**
- [ ] No `@ApplicationModuleListener` or bus consumer rethrows a transient failure — a provider outage is recorded as state, never as an exception escaping the handler
- [ ] Each subscription is configured with `maxDeliveryCount`, exponential backoff and a maximum backoff, all explicit rather than defaulted
- [ ] Dead-lettered messages carry a reason and the failing consumer's name
- [ ] Dead-letter depth per subscription is exported as a metric and alerts above zero for longer than the configured grace
- [ ] An operator can list, inspect and replay a dead-lettered message — the operation is specified by [ET-ADM-003](../../admin/003-transaction-recovery/) and this spec guarantees the message is still there to replay
- [ ] Replaying a dead-lettered message is safe, because R5 holds

### ET-PLT-003-R7 · Order is not assumed, and is bought where it is needed

THE SYSTEM SHALL treat cross-service delivery as unordered, and WHERE a sequence of
messages about one entity must be applied in order, THE SYSTEM SHALL set a session key.

**Acceptance**
- [ ] Every handler is commutative, or its registry row names the session key that serialises it
- [ ] Messages requiring order set the session id to the entity's id, and their subscription is session-enabled
- [ ] Session-enabled subscriptions are the exception and each is justified in the §4 registry's *Ordering* column
- [ ] A test interleaves two entities' message sequences and asserts neither is blocked by the other

## 4. Model

### The envelope

`com.pml.shared.event.EventEnvelope` in `shared-library`.

| Component | Type | Notes |
|---|---|---|
| `eventId` | `String` | UUID, minted by the publisher — the deduplication key |
| `eventType` | `String` | the §4 wire name, e.g. `booking.TicketPurchased` |
| `schemaVersion` | `int` | starts at `1`; bumped only on a breaking change |
| `occurredAt` | `Instant` | from the `Clock` bean |
| `correlationId` | `String` | constant across one user-initiated chain |
| `causationId` | `String` | the `eventId` that caused this one; null at the head of a chain |
| `sourceService` | `String` | `catalog`, `booking` or `identity` |
| `payload` | `Map<String,Object>` | identifiers and scalars only — never personal data |

```java
public record EventEnvelope(String eventId, String eventType, int schemaVersion,
                            Instant occurredAt, String correlationId, String causationId,
                            String sourceService, Map<String, Object> payload) {}
```

### Topics and subscriptions

| Topic | Publisher | Subscriptions | Binding |
|---|---|---|---|
| `catalog-events` | catalog-service | `booking-sub`, `identity-sub` | `catalogEvents-out-0` |
| `booking-events` | booking-service | `catalog-sub`, `identity-sub` | `bookingEvents-out-0` |
| `identity-events` | identity-service | `catalog-sub`, `booking-sub` | `identityEvents-out-0` |

Each subscription carries a SQL filter on `eventType` naming exactly the rows below that
it consumes.

### Cross-service event registry — closed

| Wire name | v | Payload identifiers | Consumers | Ordering |
|---|---|---|---|---|
| `catalog.EventPublished` | 1 | `eventId`, `organizationId`, `startsAt` | booking → open escrow; identity → notify | commutative |
| `catalog.TicketTierPublished` | 1 | `tierId`, `eventId`, `capacity`, `price`, `currency`, `salesStartAt`, `salesEndAt` | booking → create `booking_tier_inventory` | **session: `tierId`** |
| `catalog.TicketTierCapacityChanged` | 1 | `tierId`, `eventId`, `newCapacity`, `previousCapacity` | booking → adjust inventory | **session: `tierId`** |
| `catalog.EventRescheduled` | 1 | `eventId`, `previousStartsAt`, `newStartsAt` | booking → refund eligibility; identity → notify holders | commutative |
| `catalog.EventCancelled` | 1 | `eventId`, `reason` | booking → mass refund; identity → notify holders | commutative |
| `catalog.EventCompleted` | 1 | `eventId`, `completedAt` | booking → recognise commission, open payout window; identity → notify | commutative |
| `booking.TicketPurchased` | 1 | `ticketId`, `eventId`, `tierId`, `ownerId`, `quantity` | catalog → sold counters; identity → notify | commutative |
| `booking.TicketTransferred` | 1 | `ticketId`, `fromUserId`, `toUserId` | identity → notify both | **session: `ticketId`** |
| `booking.TicketValidated` | 1 | `ticketId`, `eventId`, `validatedBy` | catalog → check-in counters | commutative |
| `booking.PaymentCompleted` | 1 | `paymentIntentId`, `reservationId`, `userId`, `amount`, `currency` | identity → receipt | commutative |
| `booking.PaymentFailed` | 1 | `paymentIntentId`, `reservationId`, `userId`, `failureCode` | identity → notify | commutative |
| `booking.RefundCompleted` | 1 | `refundRequestId`, `ticketId`, `userId`, `amount`, `currency` | catalog → restore counters; identity → notify | commutative |
| `booking.PayoutCompleted` | 1 | `payoutRequestId`, `organizationId`, `netAmount`, `currency` | identity → notify organizer | commutative |
| `identity.OrganizationApproved` | 1 | `organizationId`, `ownerId`, `slug` | catalog → may publish events; booking → enable payouts | **session: `organizationId`** |
| `identity.OrganizationSuspended` | 1 | `organizationId`, `reason` | catalog → unpublish; booking → block payouts | **session: `organizationId`** |
| `identity.MemberRoleChanged` | 1 | `organizationId`, `userId`, `previousRole`, `newRole` | catalog, booking → invalidate permission cache | **session: `organizationId`** |
| `identity.MemberRemoved` | 1 | `organizationId`, `userId` | catalog, booking → invalidate permission cache | **session: `organizationId`** |
| `identity.EventAccessGranted` | 1 | `userId`, `eventId`, `eventRole` | booking → validation authorisation | **session: `eventId`** |
| `identity.EventAccessRevoked` | 1 | `userId`, `eventId` | booking → validation authorisation | **session: `eventId`** |

**19 wire names.** No other cross-service message exists.

Every payload component above is a `String` identifier except `capacity`,
`newCapacity`, `previousCapacity` and `quantity` (`int`); `price`, `amount`, `netAmount`
(`BigDecimal` serialised as a decimal string, never a float); `currency` (`String`);
`startsAt`, `salesStartAt`, `salesEndAt`, `newStartsAt`, `previousStartsAt`,
`completedAt`, `validatedAt` (`Instant`, ISO-8601); and `reason`, `failureCode`,
`eventRole`, `previousRole`, `newRole`, `slug` (`String` enum names or slugs).

### The publication shape

```java
// 1 · the write and the module event, in one transaction
@Transactional
public Mono<Ticket> issue(IssueTicket cmd) {
    return ticketRepository.save(Ticket.from(cmd, clock.instant()))
        .doOnSuccess(t -> publisher.publishEvent(new TicketPurchasedEvent(t)));
}

// 2 · the bus publication, after commit, retried from event_publication on restart
@Component
@RequiredArgsConstructor
class BookingEventBridge {

    private final StreamBridge bus;
    private final Clock clock;

    @ApplicationModuleListener
    void onTicketPurchased(TicketPurchasedEvent e) {
        bus.send("bookingEvents-out-0", EventEnvelopes.of(
            "booking.TicketPurchased", 1, clock.instant(), e.correlationId(), e.eventId(),
            "booking", Map.of("ticketId", e.ticketId(), "eventId", e.eventId(),
                              "tierId",   e.tierId(),   "ownerId", e.ownerId(),
                              "quantity", e.quantity())));
    }
}
```

The listener returns `void` and never throws. A `StreamBridge` failure is retried by
Modulith's registry, which still holds the row as incomplete.

### The consumption shape

```java
@Bean
Consumer<Message<EventEnvelope>> catalogEvents(TierInventoryService inventory,
                                               ConsumerGuard guard) {
    return msg -> {
        EventEnvelope e = msg.getPayload();
        guard.once("booking", e.eventId(), () -> switch (e.eventType()) {
            case "catalog.TicketTierPublished"      -> inventory.create(e);
            case "catalog.TicketTierCapacityChanged" -> inventory.adjust(e);
            default -> { /* filtered at the subscription; ignore */ }
        });
    };
}
```

`ConsumerGuard.once` performs the Redis `SET NX` and invokes the body only on a first
sighting. It never throws on a duplicate — a duplicate is a no-op, not an error.

### Redis and configuration

| Key | TTL | Purpose |
|---|---|---|
| `evt:seen:{consumer}:{eventId}` | 7 d | consumer deduplication (ET-PLT-002 §4) |

| Property | Value | Why |
|---|---|---|
| `spring.modulith.republish-outstanding-events-on-restart` | `true` | the crash-recovery guarantee of R1 |
| `spring.modulith.events.completion-mode` | `ARCHIVE` | completed rows retained for audit, not deleted |
| `spring.modulith.events.jdbc.schema-initialization.enabled` | `true` | the registry table exists before first publish |
| `…servicebus.bindings.*.consumer.max-delivery-count` | `5` | then dead-letter |
| `…servicebus.bindings.*.consumer.max-concurrent-calls` | tuned per subscription | |
| `…servicebus.processor.retry.exponential.max-retries` | `4` | with a bounded maximum backoff |

## 5. Tasks

- [ ] **T1 · Modulith registry on PostgreSQL in all three services, with republish-on-restart**
  - requirements: R1
  - files: `backend/*/src/main/java/com/pml/*/config/ModulithEventConfig.java`, `application.yml`
  - verify: kill-between-commit-and-publish test yields exactly one message
  - parallel-safe: yes — one service per agent
  - depends: —

- [ ] **T2 · `EventEnvelope` and `EventEnvelopes` in `shared-library`**
  - requirements: R3
  - files: `backend/shared-library/src/main/java/com/pml/shared/event/`
  - verify: a serialisation round-trip test over every §4 payload shape
  - parallel-safe: no — every service imports it
  - depends: —

- [ ] **T3 · One `*EventBridge` per service; move every `StreamBridge` call into it**
  - requirements: R2, R4
  - files: `backend/*/src/main/java/com/pml/*/event/bridge/`
  - verify: `./scripts/spec-lint.sh --events`; a rollback test observes no message
  - parallel-safe: yes — one service per agent
  - depends: T1, T2

- [ ] **T4 · Topics, subscriptions and `eventType` SQL filters**
  - requirements: R4
  - files: `../docker-resources/servicebus/`, `backend/*/src/main/resources/application.yml`
  - verify: each subscription receives only the rows its §4 filter names
  - parallel-safe: no — shared infrastructure, coordinate
  - depends: T2

- [ ] **T5 · `ConsumerGuard` and the durable marker for non-idempotent consumers**
  - requirements: R5
  - files: `backend/shared-library/.../event/ConsumerGuard.java`, each consumer
  - verify: double-delivery test per consumer; reverse-order test
  - parallel-safe: yes — one consumer per agent
  - depends: T2

- [ ] **T6 · Retry, backoff, dead-letter configuration and the depth metric**
  - requirements: R6
  - files: `backend/*/src/main/resources/application.yml`
  - verify: a consumer failing past its budget dead-letters with a reason; depth is exported
  - parallel-safe: yes
  - depends: T4

- [ ] **T7 · Session keys on the eight ordered rows; the interleaving test**
  - requirements: R7
  - files: the bridges publishing those rows, subscription configuration
  - verify: two entities' sequences interleave without blocking each other
  - parallel-safe: no — subscription configuration is shared
  - depends: T4

- [ ] **T8 · Assert no envelope payload carries personal data**
  - requirements: R3
  - files: `backend/shared-library/src/test/.../EnvelopePayloadTest.java`
  - verify: `mvn -q -f backend test -Dgroups=ET-PLT-003`
  - parallel-safe: yes
  - depends: T2

## 6. Out of scope

| Capability | Spec |
|---|---|
| The `Clock`, module boundaries, dependency baseline | [ET-PLT-001](../001-runtime-baseline/) |
| Collections, indexes, the PostgreSQL boundary, Redis registry | [ET-PLT-002](../002-persistence-baseline/) |
| What each event's consumer actually does | the spec that introduces the consumer |
| Error codes and how a refusal reaches a client | [ET-PLT-005](../005-error-contract/) |
| Dead-letter inspection, replay and bulk retry as an admin capability | [ET-ADM-003](../../admin/003-transaction-recovery/) |
| Lag and depth alerting thresholds, dashboards | [ET-ADM-005](../../admin/005-observability-and-health/) |
| Payload evolution beyond an integer counter | [ET-PLT-010](../010-schema-evolution/) |
| PII classification and erasure | [ET-PLT-008](../008-data-protection/) |

Deliberately never in scope: **change-data-capture over a MongoDB outbox** (a second
delivery pipeline for a guarantee already held), and **Kafka** (a better log the platform's
volume does not need).
