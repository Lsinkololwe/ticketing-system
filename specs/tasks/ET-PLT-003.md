# ET-PLT-003 · Event contract — tasks

> **Spec** [`specs/_platform/003-event-contract/spec.md`](../_platform/003-event-contract/spec.md) · **Wave 0** · `blocked_by:` ET-PLT-001, ET-PLT-002
> **Screens** — none. Dead-letter depth surfaces later in [`ET-ADM-005`](ET-ADM-005.md) and the recovery queue in [`ET-ADM-003`](ET-ADM-003.md).
> **Verify** `mvn -q -f backend test -Dgroups=ET-PLT-003` · `mvn -q -f backend verify -Dgroups=ET-PLT-003`

**D-03** and **D-21**: a service's own next step is a Temporal workflow activity or the rest of one
transaction; a **fact** another service needs is staged in the **MongoDB outbox** in the same
reactive transaction as the document, so the write and the intent to publish cannot disagree, and
only the drain reaches Azure Service Bus. `StreamBridge` is **never** called from a transaction, an
activity or a request path, and no in-memory event carries a step between components.

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

## R0 findings *(2026-08-18)*

| Item | Expected | Measured | Class |
|---|---|---|---|
| `StreamBridge.send` inside a `@Transactional` method | 0 | **0** | `already-satisfied` |
| `StreamBridge.send` sites | — | **14** (identity 11, booking 3, catalog **0**) | `partially-satisfied` |
| Distinct binding names | 3 | **7**, none matching §4 | `contradicted` |
| `{service}_outbox` | 3 collections | **no outbox package anywhere** | `absent` |
| `@ApplicationModuleListener` | 0 | **0** | `already-satisfied` — D-03's Modulith removal is real |
| Consumer beans | 14 consumed names | **2** | `absent` |

**The worst case does not exist.** R0 says any `StreamBridge.send` inside a `@Transactional`
method is a `contradicted` row. There are none. My first pass flagged
`ChargebackEventListener:145` by proximity; reading it shows the enclosing method is
`@TransactionalEventListener(AFTER_COMMIT)`, not `@Transactional` — a false positive from a
coarse heuristic, corrected rather than carried.

**What is wrong is different, and quieter.** All three after-commit sends do
`boolean sent = streamBridge.send(...); if (sent) log.debug else log.warn`. A warning is not a
retry. The write is committed, the message is lost, and the only trace is a WARN line — which is
precisely the gap the outbox closes.

**Catalog publishes nothing at all**, while declaring an `eventOutput-out-0` binding in its
config. Six of §4's nineteen wire names are catalog's.

**Correction to this file's own header.** It claimed *"20 bus event names are published and 14
consumed"*. §4's table holds **19**, and §4 states 19. Same class of drift as ET-PLT-002's 66
vs 67 collections — a count asserted in prose and never re-derived. `EventEnvelopeTest` now
parses the table and fails if the two disagree.

## A · Backend

- [x] **BE-2 · `EventEnvelope`, `EventEnvelopes` and `EventType`** — *26 tests.*
  - **`EventType` is an enum, not a string.** §4 calls the registry closed, and a `String` cannot
    say that. A typo in a published name fails nowhere: the message reaches the topic, matches no
    subscription filter, and is discarded — a consumer simply never hears about a payment.
  - **The registry is parsed from §4, not copied.** Adding a row to the spec and forgetting the
    enum fails the build; so does the reverse. Proven by adding a name the spec does not have.
  - **Payloads are validated at the publisher.** Missing identifier, unexpected key, blank value.
    Rejected here it is one stack trace pointing at the cause; allowed onto the topic it becomes
    a different failure in each of three subscribers at three different times. The closed-key
    rule is also what keeps personal data out of every subscriber's dead-letter queue, which
    ET-PLT-008's erasure obligation would otherwise reach.
  - **Money was being serialised as a JSON float.** §4 requires a decimal string; Jackson writes
    `BigDecimal` as a number by default, so a consumer reading into `Map<String,Object>` gets a
    `Double` — and 150.10 as a double is 150.09999999999999. Fixed **in the value**, not by
    configuring the serialiser: the envelope crosses a service boundary and the consumer's
    Jackson is not ours to configure.
  - All 19 wire names round-trip with their §4 payload intact, `Instant` precision included.

- [x] **BE-1 · the outbox** — *6 tests, on a replica set.*
  - `Outbox` stages an envelope inside the business transaction and drains it afterwards. Both
    acceptances hold: a rolled-back transaction leaves **no document and no outbox row**, and a
    kill between commit and publish yields **exactly one** message on restart.
  - **Rows are claimed, not just read.** `findAndModify` moves `PENDING → PUBLISHING` so two
    instances draining concurrently cannot both take the same row — a find-then-update would let
    both read it before either wrote.
  - **A failed publish returns the row to `PENDING`.** Left in `PUBLISHING` it is never retried,
    because the claim filter only matches `PENDING`: the message is not lost but is never sent
    either, and its status says somebody is dealing with it. `reclaimStale` covers the process
    that dies mid-publish.
  - **No `@Document` in shared-library** (ET-PLT-001 R5) — the engine works in raw `Document`s
    against a collection name each service supplies.
  - Mutation: running the same scenario without a transaction leaves both the document and the
    row, so the rollback assertion measures the transaction rather than an empty database.

- [x] **BE-3 · one publishing path, and the lint that keeps it** — *4 assertions.*
  - `EventBridge` is the only place `StreamBridge.send` belongs. It turns a `false` return into
    an **error**, so the outbox row stays `PENDING`; as a boolean it is the `log.warn` that loses
    the message. The call is wrapped on `boundedElastic` — the one blocking-SDK adapter
    ET-PLT-001 R1 allows, contained in one file.
  - Sends are ratcheted per module (identity 11, booking 3) and burn down as each slice moves.
  - **Correction (F-051, 2026-10-09):** that guarantee held only if `send` reports the bus's answer, and the
    Azure binder's producers are asynchronous by default, so `send` returned `true` before the bus had
    accepted anything and a failed delivery was dropped after the row was marked `SENT`. All three producer
    bindings now declare `sync: true`, and `ProducerBindingsAreSynchronousLintTest` fails for one that does
    not. Still open: the sender does not recover after a broker outage without a restart of the service.
  - **`nothingPublishesInsideATransaction` has no budget** — it is at zero today, which is the
    cheapest moment to fix it there.
  - **Found: two after-commit listeners rethrow, for a retry that no longer exists.**
    `PaymentEventListener`'s comment reads *"Rethrow so Modulith leaves the publication
    incomplete and retries it"*. **Modulith is gone** — D-03 replaced it, there is no
    `spring-modulith` dependency and no `@ApplicationModuleListener` anywhere. Spring logs the
    exception from an after-commit listener and discards it; the transaction is already durable;
    the paid-for reservation is stranded in `HELD` with nothing scheduled to look at it again —
    the exact outcome the comment says it prevents. Frozen at 2 rather than "fixed" by deleting
    the throw, which would make the failure silent instead of logged. The real fix is the outbox
    drain, and it belongs with the purchase saga.

- [x] **BE-5 · `ConsumerGuard`** — *5 tests.* **Reworked after re-reading §4.**
  - **A collection I invented is gone.** The first version wrote markers to
    `booking_consumed_events`, which is in no registry — and ET-PLT-002 §4 is closed. §4 of this
    spec says the durable marker is *"a durable marker in its own write"*: the authority is
    whatever the consumer already persists, checked through a `DurablyHandled` the consumer
    supplies. That marker is **atomic with the effect**, which a separate bookkeeping row is
    not — a marker written beside the work can commit while the work rolls back, and then the
    message is never retried.
  - **⚠️ This still deviates from §4 on one point, and it needs a decision.** §4 specifies
    `SET evt:seen NX EX 604800` **before** the work; this marks **after**.
    | | Stops | Costs |
    |---|---|---|
    | Before (§4) | two concurrent deliveries doing the work twice | a consumer that crashes mid-work leaves the key set for **seven days**, so every redelivery in that window is skipped and the work never happens |
    | After (built) | nothing extra | two concurrent deliveries may both run — safe only because the durable check is the consumer's own idempotent write |
    I chose *after* because the failure it accepts is one the durable write absorbs and the
    failure it avoids is silent. That is an argument, not a ruling.
  - Redis (`evt:seen:{consumer}:{eventId}`, 7-day TTL) is the fast path; **MongoDB is the
    authority**. A cache miss falls through rather than being read as "not seen".
  - **The flush case is the one that matters.** A guard trusting only Redis passes a plain
    double-delivery test and still loses every marker on a `FLUSHALL`. Proven by mutation:
    treating a cache miss as an answer fails `survivesACacheFlush`.
  - Markers are keyed **per consumer**, so two consumers each handle the same event once —
    keyed on `eventId` alone, whichever ran first would silence the rest.
  - Marked **after** the work: marking first records as handled something that never happened,
    and skips the redelivery that would have recovered it.

- [x] **BE-8 · no personal data in payloads** — *21 assertions, one per wire name.*
  - Checked against the registry rather than a sample, so a new row carrying `ownerEmail` fails
    here rather than in review. Proven by adding exactly that.
  - The allowlist is **empty**, and asserted empty: every §4 payload key passes on its own
    merits, and an entry added later has to be visible in a diff.

- [x] **BE-4 · bindings, topics, subscriptions and filters** — *3 tests.*
  - §4's three bindings (`catalogEvents-out-0`, `bookingEvents-out-0`, `identityEvents-out-0`)
    exist on the right destinations. Booking had **no output binding at all** while sending to
    two undeclared names.
  - **Added alongside the existing bindings, not replacing them.** Renaming while 14 sends still
    name the old bindings would point them at names nothing is bound to — and that fails
    silently: `send` returns `false` and the message evaporates. The old names retire with their
    call sites under BE-3's ratchet.
  - `docker-resources/servicebus/config.json` **regenerated from §4**: 3 topics, 6 subscriptions,
    6 SQL `eventType` filters, 4 session-enabled. What was there before had one subscription
    named `booking-service`, two named `default`, and `"Rules": []` throughout — **no filtering
    at all**, so every service would have been woken by every message its neighbours published.
  - Verified against the running emulator: `Entity Sync complete; Operation Result:True`, all 6
    subscriptions and filters processed, no error-level lines. `ServiceBusTopologyTest` compares
    §4's Consumers column to the broker config and is proven to fail on a removed subscription
    and on an un-sessioned ordered row.
  - **The emulator would not start**: its SQL Server backing store collides on port 1433 with
    `zrs-local-db`, another project. Our host port moved to `${SERVICEBUS_MSSQL_PORT:-1434}`
    rather than touching theirs; the container still listens on 1433 internally.

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

### BE-3 · One `EventBridge` per service; the drain is its only caller
- **Spec** R2, R4 · **§5** T3 · **depends** BE-1, BE-2 · **parallel-safe** yes
- **Acceptance** `StreamBridge.send` only inside `EventBridge` — every service's budget in
  `EventPublicationLintTest` is **zero**; no `@ActivityImpl` references `StreamBridge`; no
  `ApplicationEventPublisher` or event listener carries a step between components; a rollback test
  observes no message.
- An activity that publishes is retried whenever the activity is, and loses the message whenever
  the worker dies between its write and its send — the outbox staged in the activity's own
  transaction is the only safe shape.

- [x] **BE-3 · every direct send gone, 2026-09-13** — booking at 0 since phase 4; identity's last
  four (`UserRegisteredEvent` twice, `UserRoleChangedEvent`, an uncalled `OrganizationCreatedEvent`)
  named no registry row, carried email and phone numbers, and had no consumer, so they were removed
  rather than moved; the undeclared `userOutput-out-0` binding went with them. Booking's four
  in-memory events (`EscrowCreditedEvent`, `RefundCompletedEvent`, `CommissionEarnedEvent`,
  `JournalEntryPostedEvent`) had no listener anywhere and were deleted. Budget 0 in every service
  ([F-032](../FINDINGS.md)).

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

### BE-7 · Session keys on the nine ordered rows; the interleaving test
- **Spec** R7 · **§5** T7 · **depends** BE-4 · **parallel-safe** no *(shared subscription config)*
- **Acceptance** two entities' sequences interleave **without blocking each other**. Ordering per
  entity, not global — a global order turns the bus into a queue of one.

### BE-8 · Assert no envelope payload carries personal data
- **Spec** R3 · **§5** T8 · **depends** BE-2 · **parallel-safe** yes
- **Files** `backend/shared-library/src/test/.../EnvelopePayloadTest.java`
- [ROADMAP §Cross-cutting](../ROADMAP.md): *no personal data leaves the platform in an event
  payload* — this is the test that holds the line, and [`ET-PLT-008`](ET-PLT-008.md) depends on
  it staying true.

- [x] **BE-6 · retry, backoff, dead-letter and the depth metric** — *11 tests.*
  - `RetryBudget` holds all four numbers together — attempts, initial backoff, ceiling,
    multiplier — and refuses a combination that cannot behave as configured: a ceiling below the
    floor silently shortens the first wait, a multiplier below 1 makes each retry sooner than the
    last. All four are declared in every service's **base** `application.yml`, not just `local`,
    and `ConsumerRetryLintTest` fails the build if one goes missing.
  - `ConsumerDispatch` is the single path a consumed message takes: guard → retry → give up. It
    **never rethrows**, which R6 requires: letting the exception reach the binder does eventually
    dead-letter the message, but with the broker's own reason, `MaxDeliveryCountExceeded`, which
    names no consumer and describes no failure.
  - **The reason nearly wasn't the reason.** Reactor's `Retry.backoff` wraps the final failure in
    `RetryExhaustedException` unless `onRetryExhaustedThrow` overrides it. A dispatch written
    without it retries correctly, dead-letters correctly, exports the metric correctly — and
    records *"Retries exhausted"* as the description of every failure the platform ever has. The
    override lives in `RetryBudget` rather than at the call site, because a caller who forgets it
    gets a plausible dead letter that is useless and nothing fails. Mutation-verified: removing
    it fails exactly the assertion written for it.
  - `PermanentFailure` skips the budget. A malformed envelope retried three times is three
    identical failures and a slower path to the dead letter that was always coming.
  - Two meters, because they answer different questions: a **counter** of what this process gave
    up on, tagged by subscription, consumer and reason; and a **gauge** of live queue depth fed
    from the broker. A gauge counting only this process reads zero after a restart while the
    queue is full — quietest exactly when the platform is worst — so an unpolled subscription has
    no gauge at all rather than a confident zero.
  - Mutations verified: dropping `onRetryExhaustedThrow`, retrying permanent failures, and
    removing the no-rethrow guard each failed the assertion written for it (the last across five
    tests).

- [x] **BE-7 · session keys and the ordering tests** — *6 tests, plus 2 that skip here.*
  - `SessionKeyTest` parses §4's *Ordering* column and compares it to `EventType`, so the two
    cannot drift. Mutation-verified: repointing one row's session key from `tierId` to `eventId`
    fails three tests with the drift named exactly.
  - **A real defect found and fixed.** `EventBridge` derived the session id with
    `String.valueOf(payload.get(key))`. For an ordered row whose payload lacks its key that is
    the string `"null"` — a perfectly valid session id. Every such message would land in one
    session called `"null"`: serialised against each other and against nothing they relate to.
    The send succeeds, the broker is happy, and the guarantee R7 buys is silently inverted. Now
    refused with `MissingSessionKey`, which is a `PermanentFailure` so BE-6 dead-letters it
    rather than retrying.
  - **Count drift, third of its kind.** This file, §5 T7 and a javadoc all said *"the eight
    ordered rows"*. §4's table has **nine**. After 19-vs-20 wire names and 66-vs-67 collections,
    the count is now derived from the table on both sides and compared; the only fixed assertion
    is the property R7 actually states, that ordering is the exception. All three prose sites
    corrected.
  - `SessionOrderingTest` is the real broker test — interleaved sequences for two `tierId`s
    through the live `catalog-events` / `booking-sub` pair, holding one session unsettled while
    requiring the other to arrive. **It skips on this machine and proves nothing here**, see the
    finding below.

## Finding · the emulator is unreachable from the Java SDK on this machine *(2026-08-19)*

`SessionOrderingTest` cannot run locally, and the reason is worth writing down because every
layer of it looks healthy:

| Check | Result |
|---|---|
| Emulator container | running, `Emulator Service is Successfully Up!` |
| Topology loaded | all 6 subscriptions and filters, from the current `config.json` |
| `localhost:5672` accepts TCP | yes |
| Raw AMQP 1.0 protocol header | emulator replies correctly |
| Azure SDK `sendMessage` | **times out**, on every endpoint spelling and both transports |

Port 5672 is held by an **`ssh` process** — Testcontainers Desktop's tunnel to the Docker VM. It
accepts the connection and answers the protocol header, and the SDK's handshake behind it never
completes. Nothing in the platform's code is implicated.

**Two things this cost, both worth keeping.** First, a socket-connect precondition *passes* here,
so the test hung for its full 240-second retry budget twice — eight minutes of red for an
environment problem. The precondition is now a real send with a 6-second budget, which
distinguishes the two honestly. Second, the first failure was misread as a filter problem; the
emulator had in fact been started **two hours before** the config was written, so it was running
an older topology. That is the same shape as the router hot-reload trap: **a config file change
needs the container restarted**, and the log line saying it loaded is cumulative, so grepping the
tail for it matches the *previous* boot.

**R7's platform-side half is fully covered without a broker** — which key serialises which row,
and that two entities never share one. The broker-side half stays skipped until the emulator is
reachable, and the box below stays unticked to say so.

## Finding · the mechanism is built, tested and unwired *(2026-08-19)*

`grep` for `EventBridge|Outbox|ConsumerGuard|ConsumerDispatch|EventEnvelopes` across all three
services' production source returns **nothing**. Every part of this spec's machinery exists, is
covered, and is reachable from no running service. Meanwhile the **14 `streamBridge.send` sites
R0 catalogued are still there**, still doing `if (sent) log.debug else log.warn` — the pattern R0
identified as losing the message.

**This is why several gate rows above are now `[~]` rather than `[x]`.** "A rollback leaves no
document and no outbox row" is a true statement about `Outbox` and an empty one about the
platform: nothing stages a row, so there is no row to leave. The tests are not wrong — they
construct the class directly and prove exactly what they claim — but a reader scanning ticked
boxes would conclude the platform has an outbox, and it does not.

This is the same shape as the orphaned-`@Service` problem this codebase has produced before, and
it is now guarded: `EventPublicationLintTest.theEventMechanismIsReachableFromAService` freezes the
five unwired names and fails if a **sixth** appears, or if one of the five becomes wired and the
list is not tightened. Proven by leaving `EventEnvelopes` out of the frozen set — it was caught by
name.

**What remains is migration, not construction.** BE-1, BE-2, BE-3, BE-5 and BE-6 built the parts;
moving the 14 call sites behind them is the work that makes any of it true, and it belongs with
the services that own those sites.

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

- [x] R0 recorded; every in-transaction `StreamBridge.send` classified as `contradicted` — **zero
      found**; the one flagged by proximity was an after-commit listener, corrected in R0
- [x] Rollback leaves no document and no outbox row — **now true of the platform, not just the
      mechanism.**
  - The caveat this row carried was the honest one, and it was the largest thing wrong with this
    spec: `Outbox` could stage, claim and mark; `EventBridge` could publish one envelope and said in
    its own javadoc that *"the drain calls this"* — **and there was no drain.** The mechanism was
    complete, proven on a replica set, and unreachable. Every publisher still sent directly.
  - `OutboxDrain` written: reclaims abandoned claims, then publishes a bounded batch oldest-first,
    releasing a row back to `PENDING` when the bus refuses. Seven cases on a replica set, including
    a failing bus, an abandoned claim, a *fresh* claim that must not be stolen, and a row that must
    not go twice. `OutboxAutoConfiguration` wires it per service from two properties with no
    defaults — a guessed collection gives a misconfigured service a working outbox pointed where no
    drain looks.
  - `OrganizationServiceImpl.suspend` is the first real publisher on it. It read
    `save(org).doOnSuccess(suspended -> streamBridge.send(...))` — the shape CLAUDE.md's DON'T list
    names — and now stages `IDENTITY_ORGANIZATION_SUSPENDED` inside its own transaction.
    `SuspensionOutboxTest` asserts **both or neither** against a real replica set; mutation-verified
    by moving the stage outside the transaction, which produces an envelope announcing a suspension
    that was rolled back.
- [x] Kill between commit and publish yields exactly one message — the caveat is gone
  - `OutboxDrainTest.anAbandonedClaimIsReclaimed` is the crash: a row left in `PUBLISHING` by a dead
    process, which the claim filter cannot see because it matches only `PENDING`. Without the
    reclaim that message is not lost and never sent either — the harder failure to notice, because
    the row's status says somebody is handling it.
  - `aSentRowIsNotResent` is the other half: three passes over one staged row deliver once. The bus
    is at-least-once regardless, and the drain must not add duplicates of its own on top of it.
- [x] No `StreamBridge.send` inside any `@Transactional` method — `EventPublicationLintTest`
- [x] Every consumer idempotent on `eventId`, proven by double-delivery **and** reverse-order tests
  - **Every consumer that exists.** The platform has exactly one consumer bean — booking's catalog
    subscription — and it now routes every delivery through `ConsumerDispatch`. §4 names 14 consumed
    rows; the other 13 are not unguarded, they are unbuilt, and each arrives with its own spec.
  - What this fixed is not hypothetical. The consumer caught exceptions and declined to checkpoint,
    so a redelivered `EventPublished` re-ran `createEscrowAccount`, the unique index on
    `escrow_accounts.eventId` refused the write, Service Bus redelivered, it failed identically, and
    after `maxDeliveryCount` the message **dead-lettered**. The bus behaving exactly as documented
    produced a dead letter and an operator investigating a non-problem.
  - `CatalogConsumerIdempotencyTest` — 5 cases against a real Redis container: double delivery runs
    the work once; two *different* events are both handled, so it is not deduplicating everything;
    a poisonous handler dead-letters with a reason; reverse order handles both.
- [x] Idempotency survives a Redis flush — and now on a wired consumer, not only the guard
  - Redis is the fast path and is allowed to be empty: after a flush, a restart, an eviction. The
    authority is `DurablyHandled`, which booking answers with "does an escrow exist for this event"
    — atomic with the effect it describes, because it *is* the effect. A separate "seen ids" table
    would be a second record that can disagree with the state it claims to describe.
  - Mutation-verified: making the durable check always answer "not handled" fails exactly the
    flush case and nothing else.
- [x] Dead letters carry a reason; depth is exported — on the wired consumer
  - `ConsumerDispatch` retries on a bounded budget and then dead-letters with a reason and a
    description, counting it through `DeadLetterDepth`. The `catch (Exception e) { log }` it
    replaced could do neither: no bounded retry, and a give-up nobody counted.
- [x] No envelope payload carries personal data — `EnvelopePayloadTest`, 21 assertions
- [x] Event-name closure test green in CI — `EventNameClosureTest`, corpus-wide over `spec.yaml`
- [x] `mvn -q -f backend verify -Dgroups=ET-PLT-003 -DfailIfNoTests=false` green — **194 tagged
      tests**, exit 0 across the reactor
  - The flag reads `false` rather than `true` because `true` **cannot pass for any spec** — it is
    evaluated per module, so it fails on the first module with no test for the tag. See
    [F-022](../FINDINGS.md); the guarantee it was meant to give now lives in `SpecTagCoverageTest`,
    which fails the build if a spec at `implemented` has no test carrying its tag.
- [x] Spec `status:` → `implemented` — **2026-09-19, once ET-PLT-002 closed.** The note below is the
      record of why it waited; the chain it describes was cut when the product owner placed the
      platform settings in one catalog-owned table instead of waiting for ET-ADM-002.
  - `blocked_by: [ET-PLT-001, ET-PLT-002]`. ET-PLT-001 went `implemented` 2026-09-02. ET-PLT-002 is
    held by its single remaining `[~]`: the unprefixed `platform_configuration` collection, whose
    move belongs to **ET-ADM-002** — a Wave 6 spec that is still `approved` and unbuilt.
  - So the chain is **ET-ADM-002 → ET-PLT-002 → ET-PLT-003**, and this spec has no work of its own
    left. Worth stating plainly rather than leaving as a status that looks like unfinished
    engineering.
