# ET-PLT-003 · Event contract — facts between services, the envelope, the outbox, idempotent consumers

> **Conformance** · PDI Phase 3 atomic outbox · PDI Phase 8 dead-letter queue and recovery

## 1. Capability

Three services have to agree about facts they cannot see directly. Booking must know that
an event was published before it can hold inventory for it; catalog must know a ticket was
sold before it can show a sales figure; identity must know a payout settled before it can
tell an organizer. None of them may read another's collections
([ET-PLT-002](../002-persistence-baseline/) R2), so every one of those agreements is
carried by a message, and the platform's consistency is exactly as good as its message
delivery.

This spec fixes how that delivery works. It draws the line between a service's own steps —
a transaction, or an activity of a Temporal workflow ([ET-PLT-015](../015-durable-execution/))
— and the **facts** it tells other services over Azure Service Bus, because the failure this
platform cannot survive is a message that was sent about a transaction that then rolled back.
It declares the envelope every cross-service message carries, the closed registry of every
wire name the platform publishes, the topics and subscriptions they travel on, the
deduplication every consumer performs because at-least-once delivery is real, and what
happens to a message that cannot be processed.

The central decision is that **the outbox is a MongoDB collection written by the same
reactive transaction as the business document**. A write and its intent to publish commit
together, in one transaction, in one store — which is the only arrangement in which the
two cannot disagree. A drain then reaches Azure Service Bus after that commit, and a message
the drain never managed to send is still pending when the process restarts. That is the
transactional outbox pattern with the atomicity actually held rather than asserted, and it
needs no change-data-capture pipeline to obtain.

It delivers no domain behaviour. Its success criterion is that a service killed between
its database commit and its bus publish republishes on restart, that the same message
delivered twice changes the consumer's state once, and that a message which can never be
processed lands somewhere an operator can see it.

## 2. Design decisions

**Two kinds of hand-off, and the choice between them is not a preference.**

| | Within a service | Between services |
|---|---|---|
| What moves | the next step of a process | a fact that has already happened |
| Mechanism | the workflow's next activity, or the rest of one transaction | stage in the outbox, drain to Service Bus |
| Durability | Temporal's history; the transaction | the outbox row, then the broker |
| Guarantee | exactly the workflow's sequence, retried until it lands | at-least-once, dead-lettered on repeated failure |
| Ordering | the workflow's order | **none**, unless a session key is set |
| Payload | the activity's typed arguments — ids | the §4 envelope |

A service never tells itself something through an in-memory event: a listener that runs
after commit has no durability of its own, and a chain of them is an implicit process nothing
can resume. When one step of a process follows another, it is the workflow's next activity.
When another service needs to know, the step that made the fact true stages it in the outbox.

**The outbox is a MongoDB collection, written by the same transaction as the document.**
The pattern is: inside one reactive transaction — a service method, or the body of an
activity — save the business document and insert the outbox row through the same
`ReactiveMongoTemplate` session. They commit together or neither commits — one store, one
transaction, no window. The outbox drain, the one scheduled task a service runs, then claims
`PENDING` rows by compare-and-set, sends each through `EventBridge`, and marks it `SENT`. If
the process dies before the drain runs, or mid-send, the row is still `PENDING` or its claim
expires, and the next drain picks it up.

This is the only arrangement that actually delivers the guarantee. **A second store cannot
give it.** An outbox in PostgreSQL while the business write is in reactive MongoDB is two
transactions across two transaction managers that cannot enlist together: the document can
commit and the outbox row fail, and the platform then has a ticket nobody was told about
— silently, permanently, and invisibly to every passing test.

**A consumer's only job is to turn a fact into one transaction or one workflow signal.** It
guards on `eventId`, then either applies a single idempotent write or starts or signals the
workflow that owns the consequence, carrying the envelope's `eventId` so the workflow drops a
redelivery ([ET-PLT-015](../015-durable-execution/) R6). A consumer that runs a multi-step
process inline is a process with no history and no resume.

**Service Bus carries facts; Temporal carries processes.** A Temporal signal is addressed to
one execution, so using signals for facts would make every publisher hold a list of recipients
and every new consumer a change to every publisher. Topic fan-out is what a fact needs. Within
a process, the reverse holds: a bus message is the wrong way for one step to call the next.

**Change-data-capture is still rejected.** PDI Phase 3 proposes draining the outbox with
Debezium into Kafka Connect. The collection is right; the drain is not. Debezium adds a
connector to operate, a Kafka cluster the platform has no other use for, and a dependency
on the MongoDB oplog — where a poll over an indexed `status` field needs none of them at this
volume. The cost is not the code; it is that there would then be two answers to "how does a
message get out of this service", and the wrong one would be used by someone in a hurry.

**Never `StreamBridge.send` outside the drain.** The bus has no rollback. A publish from inside a
transaction that then fails has told three services about a ticket that does not exist; a
publish from an activity is repeated whenever the activity is retried, and is lost whenever the
worker dies between the write and the send. This is a lint rule, not a review note.

**Never let a delivery failure throw out of a consumer.** A provider being down, a
consumer being slow, a downstream 503 — these are business outcomes. Throwing dead-letters
a message whose only problem is that somebody else's server is having a bad minute, and
the message that lands in the dead-letter queue is then indistinguishable from one that is
genuinely malformed. Record the failure as state, or hand it to the workflow that retries it;
let the retry policy retry, and let the dead-letter queue mean *this message can never be
processed*.

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
`SET evt:seen:{consumer}:{eventId} NX EX 604800` in Redis as the fast path — plus, for any
consumer whose effect is not naturally idempotent, a durable marker in its own write, or the
workflow's own record of the originating id. Redis alone is a fast path, not a guarantee: a
Redis eviction must not be able to cause a double credit.

**One topic per publishing service; one subscription per consuming service.**
`catalog-events`, `booking-events`, `identity-events`, and a subscription named for the
consumer. A consumer that wants two publishers' events subscribes twice. Filtering happens
at the subscription with a SQL filter on `eventType`, so a service is not woken by every
message its neighbour publishes.

**Ordering is not assumed, and where it is required it is bought explicitly.** Service Bus
delivers unordered across a topic. Handlers must be commutative wherever they can be —
counters use `$inc`, not assignment. Where order genuinely matters, the message sets a
session id (the aggregate's id) and the subscription is session-enabled, which serialises
that key and only that key. A workflow receiving facts as signals applies them in its own
order and needs no session.

**No personal data in a cross-service payload.** A payload carries identifiers; the
consumer resolves what it needs over the graph. A phone number in a message body is a
phone number in the bus's storage, in the dead-letter queue, and in whatever an operator
copies out of it while debugging — none of which
[ET-PLT-008](../008-data-protection/) can erase.

**Rejected alternatives**

- *In-process events between steps of one service.* No durability of their own; a chain of after-commit listeners is a process nobody can resume or inspect.
- *Temporal signals as the cross-service bus.* Point-to-point by construction; a new consumer becomes a change to every publisher.
- *One topic for the whole platform.* Every service woken by every message, and a subscription filter as the only thing standing between a catalog deploy and booking's throughput.
- *Kafka instead of Service Bus.* A better log, and a second broker to run for a platform whose cross-service volume is thousands per day, not millions per second.
- *CDC over a MongoDB outbox collection (PDI Phase 3).* See above — a second pipeline for a guarantee already held.
- *The Java FQN as the wire name.* Makes a package move a breaking change for three services.
- *Consumers deduplicating in Redis alone.* Correct until an eviction, and the failure mode is a double credit nobody notices for a month.
- *Semantic versioning on the wire (`2.1`).* Implies compatibility rules nobody encodes. A counter, plus an envelope consumers can bind a subset of, says the same thing honestly.
- *Publishing the full entity in the payload so consumers need no callback.* Every consumer then depends on the publisher's whole shape, and the payload is where PII leaks.

## 3. Requirements

### ET-PLT-003-R1 · A write and its publication commit together, and survive a crash

WHEN a service writes a document that other services must learn about, THE SYSTEM SHALL
record the intent to publish in the same transaction, and IF the process terminates before
the message is delivered, THEN THE SYSTEM SHALL republish it on restart.

**Acceptance**
- [ ] Each service owns a `{service}_outbox` collection, written through the **same** `ReactiveMongoTemplate` session as the business document, inside one transaction — a service method or an activity body
- [ ] No second datastore participates: there is no JDBC datasource, no `event_publication` table and no PostgreSQL dependency in any service
- [ ] A test rolls the transaction back and asserts **neither** the document **nor** the outbox row exists — the two cannot disagree
- [ ] An integration test kills the service between the database commit and the bus publish, restarts it, and observes exactly one message on the topic
- [ ] A `PENDING` row that has never been sent is picked up by the next drain after restart, with no manual step
- [ ] Rows reaching `SENT` are retained for an audit window and then removed by a TTL index, rather than growing without bound

### ET-PLT-003-R2 · Only the drain reaches the bus

THE SYSTEM SHALL publish to Azure Service Bus only from the outbox drain, through `EventBridge`,
and SHALL NOT call `StreamBridge` from a transaction, a workflow, an activity or a request path.

**Acceptance**
- [ ] `StreamBridge.send` appears in each service only inside `EventBridge`; `EventPublicationLintTest` holds every service's budget at zero
- [ ] No `@ActivityImpl` class references `StreamBridge` — `WorkflowDeterminismLintTest` R4
- [ ] No `ApplicationEventPublisher`, `@EventListener` or `@TransactionalEventListener` carries a business step between components; a service's next step is a workflow activity
- [ ] A test that forces a rollback after the outbox row is staged observes no message on the topic

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
- [ ] Every binding `EventBridge` publishes on maps to a §4 topic
- [ ] Every `eventType` published is a §4 registry row; no publisher invents one
- [ ] Every consumer named in a registry row exists, and every consumer that exists is named in a row
- [ ] Wire names are `{context}.{PastTense}` — a name that will not read as a completed fact fails review
- [ ] Adding an event changes this spec's §4 in the same commit as the publisher

### ET-PLT-003-R5 · Every consumer is idempotent under redelivery

WHILE the bus delivers at least once, THE SYSTEM SHALL apply each message's effect exactly
once per consumer.

**Acceptance**
- [ ] Every consumer passes through `ConsumerGuard` on `evt:seen:{consumer}:{eventId}` and skips a key already seen
- [ ] Every consumer whose effect is not naturally idempotent carries a durable marker — a unique index, an `upsert` keyed on `eventId`, or a workflow that records the originating id and drops a repeat — so a Redis eviction cannot cause a second application
- [ ] A consumer that signals a workflow carries the envelope's `eventId` in the signal, and the workflow applies one id once
- [ ] Counter updates use `$inc`, never read-modify-write, so ordering does not change the result
- [ ] An integration test delivers the same message twice to each consumer and asserts the resulting state is identical to a single delivery
- [ ] An integration test delivers two causally related messages in reverse order and asserts the end state is correct

### ET-PLT-003-R6 · A failing consumer retries, and a poisonous one is visible

IF a consumer fails transiently, THEN THE SYSTEM SHALL retry with backoff, and IF it fails
past the retry budget, THEN THE SYSTEM SHALL dead-letter the message and raise its depth as
an alert.

**Acceptance**
- [ ] No bus consumer rethrows a transient failure — a provider outage is recorded as state or left to the workflow that retries it, never an exception escaping the handler
- [ ] A consumer whose signal to Temporal cannot be recorded retries within its budget and does not checkpoint the message
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
it consumes. Each service declares exactly its one output binding.

### Cross-service event registry — closed

| Wire name | v | Payload identifiers | Consumers | Ordering |
|---|---|---|---|---|
| `catalog.EventPublished` | 1 | `eventId`, `organizationId`, `startsAt` | booking → `EventFinanceWorkflow` signal-with-start (open escrow); identity → notify | commutative |
| `catalog.TicketTierPublished` | 1 | `tierId`, `eventId`, `capacity`, `price`, `currency`, `salesStartAt`, `salesEndAt` | booking → create `booking_tier_inventory` | **session: `tierId`** |
| `catalog.TicketTierCapacityChanged` | 1 | `tierId`, `eventId`, `newCapacity`, `previousCapacity` | booking → adjust inventory | **session: `tierId`** |
| `catalog.EventRescheduled` | 1 | `eventId`, `previousStartsAt`, `newStartsAt` | booking → `EventFinanceWorkflow` signal (refund window, hold moved); identity → notify holders | commutative |
| `catalog.EventCancelled` | 1 | `eventId`, `reason` | booking → `EventFinanceWorkflow` signal (mass refund); identity → notify holders | commutative |
| `catalog.EventCompleted` | 1 | `eventId`, `completedAt` | booking → `EventFinanceWorkflow` signal (hold, then payout window); identity → notify | commutative |
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
| `identity.AccountActivated` | 1 | `userId` | none yet | commutative |
| `identity.AccountSuspended` | 1 | `userId` | none yet (consumers are planned in ET-IDN-004; the broker filters are added with them) | commutative |
| `identity.AccountDeleted` | 1 | `userId` | none yet (consumers are planned in ET-IDN-004; the broker filters are added with them) | commutative |
| `identity.ContactAdded` | 1 | `userId`, `channel`, `changeId` | none yet (staged by `ContactChangeWorkflow`; notification may subscribe later). Carries no contact value, masked or raw | commutative |
| `identity.ContactChanged` | 1 | `userId`, `channel`, `changeId` | none yet (as above) | commutative |
| `identity.ContactRemoved` | 1 | `userId`, `channel`, `changeId` | none yet (as above) | commutative |

**25 wire names.** No other cross-service message exists.

Every payload component above is a `String` identifier except `capacity`,
`newCapacity`, `previousCapacity` and `quantity` (`int`); `price`, `amount`, `netAmount`
(`BigDecimal` serialised as a decimal string, never a float); `currency` (`String`);
`startsAt`, `salesStartAt`, `salesEndAt`, `newStartsAt`, `previousStartsAt`,
`completedAt`, `validatedAt` (`Instant`, ISO-8601); and `reason`, `failureCode`,
`eventRole`, `previousRole`, `newRole`, `slug` (`String` enum names or slugs).

### The publication shape

```java
// 1 · the document AND the outbox row, one session, one transaction, atomic —
//     in a service method, or in the body of a workflow activity
public Mono<Ticket> issue(IssueTicket cmd) {
    Ticket ticket = Ticket.from(cmd, clock.instant());
    return ticketRepository.save(ticket)
        .flatMap(t -> outbox.stage(EventEnvelopes.of(
            EventType.BOOKING_TICKET_PURCHASED, clock.instant(), cmd.correlationId(), null,
            Map.of("ticketId", t.id(), "eventId", t.eventId(), "tierId", t.tierId(),
                   "ownerId", t.ownerId(), "quantity", t.quantity())))
            .thenReturn(t))
        .as(transactionalOperator::transactional);
}

// 2 · the drain — separate, after commit, never inside a transaction, the only caller of the bus
@Scheduled(fixedDelayString = "${platform.outbox.drain-interval:PT2S}")
void drain() {
    outbox.reclaimStale()                                   // a claim abandoned by a dead process
        .thenMany(outbox.claimBatch(batchSize))             // PENDING → PUBLISHING by compare-and-set
        .concatMap(row -> eventBridge.publish(row.envelope())
            .then(outbox.markSent(row.id()))
            .onErrorResume(e -> outbox.release(row.id(), e)))   // never throws out
        .subscribe();
}
```

The drain never throws. A publish failure returns the row to `PENDING` with its attempt count
incremented, and the next drain retries it; after the attempt ceiling the row moves to `FAILED`
and is visible to [ET-ADM-003](../../admin/003-transaction-recovery/). Two instances draining
at once cannot take the same row, because the claim is a compare-and-set; no lock is needed.

### The consumption shape

```java
@Bean
Consumer<Message<EventEnvelope>> catalogEvents(ConsumerDispatch dispatch,
                                               TierInventoryService inventory,
                                               EventFinanceProcess eventFinance) {
    return msg -> dispatch.handle(msg.getPayload(), e -> switch (e.eventType()) {
        // one idempotent transaction
        case "catalog.TicketTierPublished"       -> inventory.create(e);
        case "catalog.TicketTierCapacityChanged" -> inventory.adjust(e);
        // one signal-with-start to the workflow that owns the consequence, carrying eventId
        case "catalog.EventPublished", "catalog.EventCompleted",
             "catalog.EventCancelled", "catalog.EventRescheduled" -> eventFinance.deliver(e);
        default -> Mono.empty();                 // filtered at the subscription
    });
}
```

`ConsumerDispatch` runs `ConsumerGuard.once` — the Redis `SET NX` fast path and the consumer's
durable check — then the body inside the retry budget, and dead-letters with a reason when the
budget is spent. It never throws on a duplicate — a duplicate is a no-op, not an error.

### Redis and configuration

| Key | TTL | Purpose |
|---|---|---|
| `evt:seen:{consumer}:{eventId}` | 7 d | consumer deduplication (ET-PLT-002 §4) |

| Property | Value | Why |
|---|---|---|
| `platform.outbox.drain-interval` | `PT2S` | how quickly a staged event reaches the bus |
| `platform.outbox.batch-size` | `100` | rows per drain pass |
| `platform.outbox.max-attempts` | `8` | then the row becomes `FAILED` rather than retrying forever |
| `platform.outbox.sent-retention` | `P7D` | TTL on `SENT` rows, so the collection stays bounded |
| `…servicebus.bindings.*.consumer.max-delivery-count` | `5` | then dead-letter |
| `…servicebus.bindings.*.consumer.max-concurrent-calls` | tuned per subscription | |
| `…servicebus.processor.retry.exponential.max-retries` | `4` | with a bounded maximum backoff |

## 5. Tasks

- [ ] **T1 · The `{service}_outbox` collection, staged in the business transaction, and its drain**
  - requirements: R1, R2
  - files: `backend/shared-library/src/main/java/com/pml/shared/event/`, each service's `application.yml`
  - verify: a rolled-back transaction leaves no document **and** no outbox row; the kill-between-commit-and-publish test yields exactly one message
  - parallel-safe: yes — one service per agent
  - depends: —

- [ ] **T2 · `EventEnvelope` and `EventEnvelopes` in `shared-library`**
  - requirements: R3
  - files: `backend/shared-library/src/main/java/com/pml/shared/event/`
  - verify: a serialisation round-trip test over every §4 payload shape
  - parallel-safe: no — every service imports it
  - depends: —

- [ ] **T3 · One `EventBridge` per service; the drain is its only caller**
  - requirements: R2, R4
  - files: `backend/shared-library/src/main/java/com/pml/shared/event/EventBridge.java`, `EventPublicationLintTest`
  - verify: no `StreamBridge.send` outside `EventBridge` in any service; no activity references `StreamBridge`; no in-memory event carries a step between components; a rollback test observes no message
  - parallel-safe: yes — one service per agent
  - depends: T1, T2

- [ ] **T4 · Topics, subscriptions and `eventType` SQL filters**
  - requirements: R4
  - files: `../docker-resources/servicebus/`, `backend/*/src/main/resources/application.yml`
  - verify: each subscription receives only the rows its §4 filter names
  - parallel-safe: no — shared infrastructure, coordinate
  - depends: T2

- [ ] **T5 · `ConsumerGuard`, `ConsumerDispatch` and the durable marker for non-idempotent consumers**
  - requirements: R5
  - files: `backend/shared-library/.../event/ConsumerGuard.java`, each consumer, each workflow a consumer signals
  - verify: double-delivery test per consumer; reverse-order test; a redelivered signal is dropped by its workflow
  - parallel-safe: yes — one consumer per agent
  - depends: T2

- [ ] **T6 · Retry, backoff, dead-letter configuration and the depth metric**
  - requirements: R6
  - files: `backend/*/src/main/resources/application.yml`
  - verify: a consumer failing past its budget dead-letters with a reason; depth is exported
  - parallel-safe: yes
  - depends: T4

- [ ] **T7 · Session keys on the nine ordered rows; the interleaving test**
  - requirements: R7
  - files: `EventBridge`, subscription configuration
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
| Collections, indexes, the outbox document shape, Redis registry | [ET-PLT-002](../002-persistence-baseline/) |
| Processes within a service: workflows, activities, timers, signals | [ET-PLT-015](../015-durable-execution/) |
| What each event's consumer actually does | the spec that introduces the consumer |
| Error codes and how a refusal reaches a client | [ET-PLT-005](../005-error-contract/) |
| Dead-letter inspection, replay and bulk retry as an admin capability | [ET-ADM-003](../../admin/003-transaction-recovery/) |
| Lag and depth alerting thresholds, dashboards | [ET-ADM-005](../../admin/005-observability-and-health/) |
| Payload evolution beyond an integer counter | [ET-PLT-010](../010-schema-evolution/) |
| PII classification and erasure | [ET-PLT-008](../008-data-protection/) |

Deliberately never in scope: **change-data-capture over a MongoDB outbox** (a second
delivery pipeline for a guarantee already held), **Kafka** (a better log the platform's
volume does not need), and **in-process events between a service's own steps** (the workflow's
next activity is that step).
