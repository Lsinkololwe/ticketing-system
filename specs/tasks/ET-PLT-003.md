# ET-PLT-003 · Event contract — tasks

> **Spec** [`specs/_platform/003-event-contract/spec.md`](../_platform/003-event-contract/spec.md) · **Wave 0** · `blocked_by:` ET-PLT-001, ET-PLT-002
> **Screens** — none. Dead-letter depth surfaces later in [`ET-ADM-005`](ET-ADM-005.md) and the recovery queue in [`ET-ADM-003`](ET-ADM-003.md).
> **Verify** `mvn -q -f backend test -Dgroups=ET-PLT-003` · `mvn -q -f backend verify -Dgroups=ET-PLT-003`

**D-03** replaces the Modulith/PostgreSQL arrangement described in `CLAUDE.md`: in-process events
inside a service, Azure Service Bus between services, and a **MongoDB outbox** bridging the two.
The outbox row is staged in the same reactive transaction as the document, so the write and the
intent to publish cannot disagree. `StreamBridge` is **never** called inside a transaction.

The corpus depends on this being right: 20 bus event names are published and 14 consumed, and the
closure currently holds — every consumed name has a publisher.

## R0 · Reconcile *(do this first)*

```bash
grep -rn 'StreamBridge' backend --include='*.java' | grep -v /src/test/
grep -rn '@ApplicationModuleListener\|@TransactionalEventListener\|@EventListener' backend --include='*.java'
grep -rn 'modulith' backend/*/pom.xml
```

`booking-service` already has `event/listener/*` classes and a deleted `ModulithEventConfig`.
Classify each: does it become a `@TransactionalEventListener(AFTER_COMMIT)` module listener, an
outbox drain, or is it contradicted outright? **Any `StreamBridge.send` found inside a
`@Transactional` method is a `contradicted` row** — record it, do not quietly fix it in R0.

## A · Backend

### BE-1 · `{service}_outbox`, staged in the business transaction, and its drain
- **Spec** R1, R2 · **§5** T1 · **depends** R0 · **parallel-safe** yes *(one service per agent)*
- **Files** `backend/*/src/main/java/com/pml/*/outbox/`, `application.yml`
- **Acceptance** a rolled-back transaction leaves **no document and no outbox row**; the
  kill-between-commit-and-publish test yields **exactly one** message.
- Exactly one, not at-least-one. At-least-once delivery is the bus's job; the outbox's job is
  that the row and the intent commit together.

### BE-2 · `EventEnvelope` and `EventEnvelopes` in `shared-library`
- **Spec** R3 · **§5** T2 · **depends** R0 · **parallel-safe** no *(every service imports it)*
- **Acceptance** a serialisation round-trip over **every** §4 payload shape.

### BE-3 · One `*EventBridge` per service; move every `StreamBridge` call into it
- **Spec** R2, R4 · **§5** T3 · **depends** BE-1, BE-2 · **parallel-safe** yes
- **Acceptance** no `StreamBridge.send` inside a `@Transactional` method; no module boundary uses
  a bare `@EventListener`; no `@TransactionalEventListener(AFTER_COMMIT)` **rethrows** a delivery
  failure; a rollback test observes no message.
- The rethrow rule matters: an after-commit listener that throws cannot un-commit the write, so
  rethrowing converts a delivery problem into a lie about the transaction.

### BE-4 · Topics, subscriptions and `eventType` SQL filters
- **Spec** R4 · **§5** T4 · **depends** BE-2 · **parallel-safe** no *(shared infrastructure — coordinate)*
- **Files** `../docker-resources/servicebus/`, `backend/*/src/main/resources/application.yml`
- Three topics: `catalog-events`, `booking-events`, `identity-events`.
- **Acceptance** each subscription receives **only** the rows its §4 filter names.

### BE-5 · `ConsumerGuard` and the durable marker for non-idempotent consumers
- **Spec** R5 · **§5** T5 · **depends** BE-2 · **parallel-safe** yes *(one consumer per agent)*
- **Acceptance** a double-delivery test **per consumer**, and a reverse-order test.
- `evt:seen:{consumer}:{eventId}` in Redis with a 7-day TTL is the fast path, but Redis is not
  the authority — the consumer's own write is ([`ET-PLT-002`](ET-PLT-002.md) R7: Redis never
  holds business state). A guard that trusts only Redis loses idempotency on a flush.
- [ROADMAP §Cross-cutting](../ROADMAP.md): *every cross-service consumer is idempotent on
  `eventId`* — asserted here, for all 14 consumed names.

### BE-6 · Retry, backoff, dead-letter configuration and the depth metric
- **Spec** R6 · **§5** T6 · **depends** BE-4 · **parallel-safe** yes
- **Acceptance** a consumer failing past its budget dead-letters **with a reason**; depth is
  exported as a metric.
- The reason is what makes [`ET-ADM-003`](ET-ADM-003.md)'s recovery queue usable; a dead letter
  with no reason is a message an operator cannot triage.

### BE-7 · Session keys on the eight ordered rows; the interleaving test
- **Spec** R7 · **§5** T7 · **depends** BE-4 · **parallel-safe** no *(shared subscription config)*
- **Acceptance** two entities' sequences interleave **without blocking each other**. Ordering per
  entity, not global — a global order turns the bus into a queue of one.

### BE-8 · Assert no envelope payload carries personal data
- **Spec** R3 · **§5** T8 · **depends** BE-2 · **parallel-safe** yes
- **Files** `backend/shared-library/src/test/.../EnvelopePayloadTest.java`
- [ROADMAP §Cross-cutting](../ROADMAP.md): *no personal data leaves the platform in an event
  payload* — this is the test that holds the line, and [`ET-PLT-008`](ET-PLT-008.md) depends on
  it staying true.

## B · Contract

None directly. The **event** contract is this spec; the **GraphQL** contract is
[`ET-PLT-004`](ET-PLT-004.md).

## C · Frontend

None.

## D · Tests

### TS-1 · Outbox atomicity
- **L3 (Testcontainers: Mongo replica set)** — rollback leaves neither document nor row;
  kill-between-commit-and-publish yields exactly one message; the drain is restartable.

### TS-2 · Envelope
- **L1** — round-trip over every §4 payload shape.
- **L1** — reflective assertion that no payload type carries a PII-bearing field.

### TS-3 · Consumers
- **L3** — per consumer: double delivery is a no-op; reverse order converges to the same state.
- **L3** — a Redis `FLUSHALL` between the two deliveries **must not** break idempotency.

### TS-4 · Bus behaviour
- **L3 (Testcontainers / Service Bus emulator)** — filters admit only the named rows; a consumer
  failing past budget dead-letters with a reason; depth metric is exported; two entities' ordered
  sequences interleave.

### TS-5 · Closure
- **L4 (contract)** — every name under any spec's `events.consumes` appears under some spec's
  `events.bus`. Currently 20 published / 14 consumed / **0 orphans** — this test is what keeps it
  at zero, and it belongs in CI because it is a corpus-wide property no single service can check.

Tag `@Tag("ET-PLT-003")`.

## E · Gate

- [ ] R0 recorded; every in-transaction `StreamBridge.send` classified as `contradicted`
- [ ] Rollback leaves no document and no outbox row
- [ ] Kill between commit and publish yields exactly one message
- [ ] No `StreamBridge.send` inside any `@Transactional` method
- [ ] Every consumer idempotent on `eventId`, proven by double-delivery **and** reverse-order tests
- [ ] Idempotency survives a Redis flush
- [ ] Dead letters carry a reason; depth is exported
- [ ] No envelope payload carries personal data
- [ ] Event-name closure test green in CI
- [ ] `mvn -q -f backend verify -Dgroups=ET-PLT-003 -DfailIfNoTests=true` green
- [ ] Spec `status:` → `implemented`
