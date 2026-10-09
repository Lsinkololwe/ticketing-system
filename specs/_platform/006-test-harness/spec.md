# ET-PLT-006 · Five-layer test harness — fixtures, Testcontainers, the frozen clock

> **Conformance** · PDI Phase 9 monitoring and verification

## 1. Capability

Every guarantee this corpus makes is worth exactly as much as the test that proves it.
Some of those guarantees cannot be proven any other way: that two hundred concurrent
buyers against fifty tickets yields fifty tickets, that a service killed between its
database commit and its bus publish republishes on restart, that a reservation expires at
exactly ten minutes, that debits equal credits after a refund of a promotional
multi-tier order. None of those is observable by reading the code, and all of them are
observable by a test that is cheap to write once the harness exists.

This spec builds that harness. It declares five layers, what each catches, what each is
allowed to depend on, and roughly how many of each the platform should have — because a
suite that is eighty percent integration tests is a suite nobody runs, and a suite with no
integration tests proves the business logic while the wiring is broken. It fixes the two
capabilities the whole corpus leans on: a **frozen clock**, so a seven-day invitation
expiry is a millisecond assertion rather than a week, and **real infrastructure in a
container**, so a MongoDB transaction test actually exercises a replica set.

And it fixes one rule that matters more than any other in a platform that moves money:
**a refused operation must persist nothing.** A test asserting only that an exception was
thrown still passes when the service wrote the reservation, decremented the inventory, and
*then* threw — and an oversold event is not recoverable by deleting a row.

It delivers no domain behaviour. Its success criterion is that every spec in this corpus
can express its acceptance criteria as tests without inventing infrastructure, and that
the whole suite runs in CI in under ten minutes.

## 2. Design decisions

**Five layers, with a deliberate shape.**

| Layer | Catches | May use | Speed | Target count |
|---|---|---|---|---|
| 1 · Decision | wrong rules, bad state transitions, arithmetic | nothing — plain JUnit | ~1 ms | hundreds |
| 2 · Persistence | queries and aggregation pipelines drifting from documents | Testcontainers MongoDB | ~50 ms | dozens |
| 3 · Saga | broken long-running processes, bad compensation, timers | `TestWorkflowEnvironment`, time skipping, stubbed activities | ~50 ms | one per workflow |
| 4 · Contract | composition failures, breaking schema changes | rover | ~1 s | one per subgraph |
| 5 · Integration | wiring, real concurrency, real money paths | Testcontainers + WireMock | seconds | a handful |

Nearly all tests are layer 1, because nearly all the risk is. **If you are asserting on
commission arithmetic inside a Testcontainers test, move it down** — the arithmetic does
not need a database, and putting it there makes it slow, flaky and rarely run.

**Layer 1 is possible only if decisions are extractable.** A commission calculation that
can only be exercised through a repository is a design problem, not a testing problem. The
platform's rules — fare of a multi-tier order, refund fee tiers, payout eligibility,
permission resolution, state-machine transitions — are pure functions over values, and
they are tested as such.

**Testcontainers, not embedded, not shared.** An embedded MongoDB is not a replica set, so
every transaction test against it passes vacuously — which is the single worst outcome
available, because it converts the platform's most important guarantee into a green tick.
A shared developer database makes tests order-dependent. Containers are started once per
suite via a singleton, and each test class gets a clean database rather than a clean
container.

**The clock is frozen by default in tests.** The test context replaces the `Clock` bean of
[ET-PLT-001](../001-runtime-baseline/) with a mutable fixed clock. Every deadline in the
platform then has a boundary test that runs in a millisecond: a reservation at 9:59 and at
10:01, an invitation at day 6 and day 8, a sales window closing at exactly midnight, an
OTP at 299 and 301 seconds. **Those boundaries are where the bugs are**, and a suite that
cannot reach them tests only the middle of every range.

**Every refusal test asserts that nothing was persisted.** This is a required box on every
refusal, not a nice-to-have: the assertion is that the collections the operation would have
touched are unchanged, and for inventory specifically that `available + reserved + sold`
still equals capacity. A `assertThrows` with no persistence assertion is an incomplete
test and fails review.

**Concurrency is tested with real concurrency.** The inventory guarantee
([ET-PLT-002](../002-persistence-baseline/) R6) is proven by two hundred parallel callers
against a real replica set, asserting exactly the available count succeeds. A test that
issues those calls sequentially, or against a single-connection harness, passes without
exercising the property at all.

**External providers are WireMock, and their failure modes are the point.** PawaPay,
WhatsApp, SMS and S3 are stubbed. The valuable stubs are not the happy paths — they are
the 503, the timeout, the duplicate webhook, the webhook that arrives before the API
response, and the response that says *pending* forever. Those are the shapes that produce
stuck money.

**Contract tests run without the services.** `compose-supergraph.sh --static` composes
from on-disk SDL, so composition is provable in CI without a database, a broker and three
JVMs. Live introspection composition is a developer convenience, not the gate.

**Every test carries its spec ID.** `@Tag("ET-FIN-002")` on the class, and each method's
display name naming the requirement it proves — `ET-FIN-002-R3`. That makes
`mvn test -Dgroups=ET-FIN-002` the answer to *what verifies this*, using nothing but
JUnit's own tagging.

**The suite is fast enough to run.** Layers 1–3 complete in under a minute and run on
every save; layers 4–5 run in CI and complete the whole suite in under ten minutes. A
suite slower than that is a suite that gets skipped under deadline, which is precisely
when it was most needed.

**Rejected alternatives**

- *Embedded MongoDB (`flapdoodle`) for speed.* No replica set, therefore no transactions, therefore every transaction test is a false positive. Fast and wrong.
- *A shared developer MongoDB.* Order-dependent tests and a suite that fails differently for each person.
- *Mocking repositories at layer 2.* Proves the mock matches the test's belief about the query. Aggregation pipelines are exactly where beliefs are wrong.
- *Injecting a `Clock` per test rather than replacing the bean.* Every class that forgot to accept one silently uses real time, and the test passes for the wrong reason.
- *Testing concurrency with `CompletableFuture.allOf` over a mocked repository.* Concurrency against a mock proves the mock is thread-safe.
- *Contract testing by standing up all three services in CI.* Slow, flaky, and the first thing disabled when the pipeline gets long.
- *A coverage percentage as the quality gate.* Rewards testing getters. The gate here is that every acceptance box has a test carrying its requirement id.

## 3. Requirements

### ET-PLT-006-R1 · Business decisions are testable without infrastructure

THE SYSTEM SHALL express every business rule as a function over values that a layer-1 test
can exercise with no database, broker or network.

**Acceptance**
- [ ] State-machine transitions, fee and commission arithmetic, payout eligibility, refund-window rules and permission resolution are each callable from a plain JUnit test with no Spring context
- [ ] No layer-1 test declares `@SpringBootTest`, `@Testcontainers` or a mock of a repository
- [ ] Layer-1 tests are the majority of the suite by count
- [ ] Every state machine in the platform has a test driving **every** `(state, transition)` pair, legal and illegal, not only the legal ones
- [ ] The whole of layers 1–3 completes in under 60 seconds

### ET-PLT-006-R2 · Infrastructure under test is the infrastructure that ships

THE SYSTEM SHALL run persistence and integration tests against containerised MongoDB,
PostgreSQL and Redis matching the deployed versions.

**Acceptance**
- [ ] A singleton Testcontainers configuration starts MongoDB (**as a replica set**), PostgreSQL and Redis once per suite
- [ ] Container image versions match the deployed versions and are declared in one place
- [ ] Each test class gets a clean database, not a fresh container
- [ ] A transaction test writes two documents, fails after the first, and observes neither — and this test **fails** if the container is not a replica set
- [ ] No test uses an embedded or in-memory MongoDB
- [ ] Azure Service Bus is faked by a local test binder; no test requires an Azure subscription

### ET-PLT-006-R3 · Time is frozen, and every deadline has boundary tests

THE SYSTEM SHALL replace the application `Clock` with a controllable fixed clock in every
test context.

**Acceptance**
- [ ] A `TestClock` bean replaces `platformClock` in the test context and can be set and advanced
- [ ] No test sleeps to wait for a timeout
- [ ] Every deadline named in this corpus has tests either side of its boundary: reservation TTL, invitation expiry, OTP validity and cooldown, sales window open and close, payout window, refund window, settlement window
- [ ] A test advancing the clock across a sales-window close observes the tier refuse with `TIER_NOT_ON_SALE`
- [ ] Auditing timestamps (`@CreatedDate`, `@LastModifiedDate`) freeze with the same clock

### ET-PLT-006-R4 · A refused operation persists nothing

WHEN an operation is refused, THE SYSTEM SHALL leave every document it would have written
unchanged, and every refusal test SHALL assert this.

**Acceptance**
- [ ] Every refusal test asserts both the registry `errorCode` and that the operation persisted nothing
- [ ] `assertNothingPersisted(collections…)` exists in the shared test support and is what those tests call
- [ ] For inventory refusals the assertion additionally proves `available + reserved + sold == capacity`
- [ ] For money refusals the assertion additionally proves the ledger's debit and credit totals are unchanged
- [ ] A deliberately broken service that writes and then throws fails these tests — asserted by a mutation test that introduces exactly that defect

### ET-PLT-006-R5 · Concurrency is proven under real contention

THE SYSTEM SHALL prove the platform's concurrency guarantees with parallel callers against
real infrastructure.

**Acceptance**
- [ ] 200 parallel reservations against an inventory of 50 yield exactly 50 successes and 150 `TIER_SOLD_OUT`, and the counters sum to capacity
- [ ] Two parallel payment attempts carrying the same idempotency key produce exactly one payment intent
- [ ] Two parallel payout approvals for one request produce exactly one settlement
- [ ] Two parallel escrow debits exceeding the balance produce one success and one `ESCROW_INSUFFICIENT_BALANCE`
- [ ] Each of these runs against the containerised replica set, with real threads, and is repeated enough times to be meaningful rather than lucky

### ET-PLT-006-R6 · External providers are stubbed, failure-first

THE SYSTEM SHALL stub every external provider and SHALL test its failure modes explicitly.

**Acceptance**
- [ ] PawaPay, the WhatsApp and SMS senders, S3 and the Keycloak Admin API are all WireMock stubs; no test reaches a real provider
- [ ] Stubbed failure modes include: 503, connection timeout, read timeout, a duplicate webhook, a webhook arriving **before** the initiating call returns, and a status that stays `PENDING` past the platform's own timeout
- [ ] A crash-recovery test kills the service between its database commit and its bus publish and asserts exactly one message after restart (ET-PLT-003 R1)
- [ ] Every consumer has a double-delivery test and a reverse-order test (ET-PLT-003 R5)
- [ ] Provider contract fixtures are recorded from real sandbox responses, not hand-written from documentation

### ET-PLT-006-R7 · Every acceptance box is traceable to a test

THE SYSTEM SHALL tag every test with the spec it verifies, and the roll-up SHALL report
untested and drifting specs.

**Acceptance**
- [ ] Every test class carries `@Tag("ET-<AREA>-<NNN>")`, and every test method's display name names the requirement it verifies — `ET-<AREA>-<NNN>-R<n>`
- [ ] `mvn -f backend test -Dgroups=ET-FIN-002` runs exactly that spec's tests
- [ ] A `-Dgroups` selection that matches **no test anywhere in the reactor fails**. This is the single most important box in this spec: without it every spec's `verify:` block passes green having executed zero tests, and `verified` becomes a status the corpus can award itself by accident
- [ ] The guard is `-DfailIfNoTests=false` on a **module-scoped** run. A spec's tests live in exactly one service, so `mvn -f backend/<module> test -Dgroups=<ID> -DfailIfNoTests=false` fails when the tag matches nothing — whereas the same flag across the whole reactor would fail the five modules that correctly have nothing to run
- [ ] Every spec's `verify:` block uses the module-scoped form, so no spec can verify green having executed zero tests
- [ ] A cross-cutting spec whose tests span services runs the reactor form and names, in its own `verify:` block, the modules that must contribute tests
- [ ] Running a tag no test carries fails, and the failure names the tag
- [ ] Coverage is not the gate; the gate is that every acceptance box names a test that asserts it

## 4. Model

### Layer map

| Layer | Base class / annotation | Infrastructure | Where tests live |
|---|---|---|---|
| 1 · Decision | plain JUnit 5 | none | beside the type under test |
| 2 · Persistence | `@DataMongoTest` + `PersistenceTest` | Mongo container | `…/repository/` |
| 3 · Saga | `TestWorkflowExtension` / `TestWorkflowEnvironment` | time-skipping test server + stubbed activities ([ET-PLT-015](../015-durable-execution/) R8) | `…/workflow/` |
| 4 · Contract | `SchemaContractTest` | rover, on-disk SDL | `backend/*/src/test/.../graphql/` |
| 5 · Integration | `@SpringBootTest` + `IntegrationTest` | all containers + WireMock | `…/it/` |

### Shared test support

`backend/shared-library/src/test/java/com/pml/shared/testing/` — published as a
`test-jar` and depended on by all three services.

| Component | Purpose |
|---|---|
| `TestContainers` | the singleton Mongo replica set, PostgreSQL and Redis |
| `TestClock` | mutable fixed clock; `set(Instant)`, `advance(Duration)` |
| `CleanDatabase` | per-class database reset |
| `Refusals.assertRefused(code, thrown)` | asserts the registry code and retryability |
| `Persistence.assertNothingPersisted(…)` | the R4 assertion |
| `Ledger.assertBalanced()` | debits equal credits across the journal |
| `Inventory.assertConserved(tierId)` | `available + reserved + sold == capacity` |
| `Concurrency.inParallel(n, task)` | real threads, collected outcomes |
| `Providers` | the WireMock stubs and their failure scenarios |
| `Spec` | the `SOURCE`-retention traceability annotation (ET-PLT-001 R5) |

### Container versions

Declared once, in `TestContainers`, matching what is deployed.

| Image | Version | Notes |
|---|---|---|
| `mongo` | 8.x | **`--replSet rs0`**, initiated in a `@BeforeAll`; a standalone container must fail R2 |
| `redis` | 7.x | |
| Service Bus | — | Spring Cloud Stream **test binder**; no Azure dependency |

### The refusal-test shape

```java
@Tag("ET-TKT-001")
class ReserveTicketsRefusalTest extends IntegrationTest {

    @Test @DisplayName("ET-TKT-001-R2")
    void refusesWhenSoldOut() {
        clock.set(SALE_OPEN);
        var inventory = given.inventory(tierId).capacity(50).available(0);

        var thrown = catchThrowable(() -> reserve(tierId, 1, buyer));

        Refusals.assertRefused(TIER_SOLD_OUT, thrown);      // the code AND retryable:false
        Persistence.assertNothingPersisted("booking_reservations",
                                           "booking_payment_intents");
        Inventory.assertConserved(tierId);                  // capacity still adds up
    }
}
```

Both assertions are required. The first proves the client is told the right thing; the
second proves the platform did not quietly take the ticket anyway.

### The concurrency-test shape

```java
@Test @DisplayName("ET-PLT-002-R6")
void sellsExactlyTheAvailableQuantity() {
    given.inventory(tierId).capacity(50).available(50);

    var outcomes = Concurrency.inParallel(200, () -> reserve(tierId, 1, someBuyer()));

    assertThat(outcomes.successes()).hasSize(50);
    assertThat(outcomes.refusalsOf(TIER_SOLD_OUT)).hasSize(150);
    Inventory.assertConserved(tierId);
}
```

### Suite budget

| Stage | Layers | Budget | Runs |
|---|---|---|---|
| fast | 1–3 | < 60 s | every save, every push |
| full | 1–5 | < 10 min | every CI run |
| concurrency | 5, repeated | < 3 min | every CI run, not skippable |

The concurrency stage is separated so it cannot be quietly dropped from CI when the
pipeline gets long — it is the stage that proves the platform does not oversell.

## 5. Tasks

- [x] **T1 · `shared-library` test-jar; `TestContainers` singleton with a real replica set** — *done 2026-08-18. `MongoReplicaSet` (mongo:8.0, `--replSet`, reused) + `MongoStandalone` (plain `mongod`). `TransactionRealityTest` proves both halves: the two-document rollback leaves nothing on `REPLICA_SET_PRIMARY`, and on `STANDALONE` the transaction is refused outright while the un-transacted write survives its failure. Test-jar verified to ship the harness. `-Dapi.version=${docker.api.version}` pinned in surefire — without it Testcontainers dies on Docker Engine 29 with a misleading "no valid Docker environment".*
  - requirements: R2
  - files: `backend/shared-library/src/test/java/com/pml/shared/testing/`, `pom.xml`
  - verify: a transaction test passes on the container and fails against a standalone `mongod`
  - parallel-safe: no — every service depends on it
  - depends: —

- [~] **T2 · `TestClock`, the test context override, and the auditing provider** — *partial, 2026-08-18. `TestClock` built and proven (frozen, `advance`, `justBefore`/`justAfter`, refuses to rewind). The stated acceptance — advancing across a sales-window close to observe `TIER_NOT_ON_SALE` — **cannot be proven until [ET-CAT-002](../../catalog/002-ticket-tiers-and-inventory/) exists**, and is deferred to that slice. The test-context override and auditing provider await ET-PLT-001's `Clock` bean.*
  - requirements: R3
  - files: `.../testing/TestClock.java`, per-service test configuration
  - verify: a test advances across a sales-window close and observes `TIER_NOT_ON_SALE`
  - parallel-safe: no
  - depends: T1

- [~] **T3 · `Refusals`, `Persistence`, `Ledger`, `Inventory` assertions** — *3 of 4 done, 2026-08-18. `Persistence`, `Inventory` and `Ledger` are built and **each has been watched failing** against a seeded defect: write-then-throw, a leaked hold (drift −1), a negative counter, an unbalanced entry, a single-line entry, a negative amount, and money stored as a `double`. `Refusals` waits on [ET-PLT-005](../005-error-contract/)'s `ErrorCode`/`DomainRefusal`, which do not exist yet.*
  - requirements: R4
  - files: `.../testing/`
  - verify: a deliberately write-then-throw service fails the assertion
  - parallel-safe: yes
  - depends: T1

- [~] **T4 · `Concurrency.inParallel` and the four contention tests** — *primitive done, 2026-08-18. Virtual threads released together on one start gate (submitting to a pool does not contend — the first finishes before the last begins). `repeat()` for R5's "meaningful rather than lucky". Proven both ways on a real replica set: the conditional `findAndModify` yields **exactly 50 of 200** over 5 runs with conservation intact, and the forbidden read-modify-write **oversells** — same workload, opposite outcomes, which is what proves the contention is real. The other three scenarios (idempotency key, payout approval, escrow debit) wait on ET-PAY-001, ET-FIN-003 and ET-FIN-001.*
  - requirements: R5
  - files: `.../testing/Concurrency.java`, `backend/booking-service/src/test/.../it/`
  - verify: 200-against-50 yields exactly 50; repeated runs are stable
  - parallel-safe: no — depends on T1's replica set
  - depends: T1, T3

- [~] **T5 · `Providers` WireMock stubs, failure-first** — *stubs done, fixtures not. 2026-08-18. All six R6 failure modes have a named stub and a test: 503, connection reset, read timeout, duplicate callback, callback-overtakes-response, and stuck `PENDING`. WireMock 3.13.1 `standalone` (shaded, so it cannot collide with Spring's Jetty/Jackson), managed in the parent. **The remaining acceptance box — fixtures recorded from real sandbox responses — is NOT met**: the bodies are structurally-shaped placeholders, and that box must not be ticked until someone records against the PawaPay sandbox.*
  - requirements: R6
  - files: `.../testing/Providers.java`, recorded sandbox fixtures
  - verify: the six failure modes of R6 each have a named stub and a test
  - parallel-safe: yes — one provider per agent
  - depends: T1

- [ ] **T6 · The crash-recovery, double-delivery and reverse-order event tests**
  - requirements: R6
  - files: `backend/*/src/test/.../it/`
  - verify: `mvn -q -f backend verify -Dgroups=ET-PLT-003`
  - parallel-safe: yes — one service per agent
  - depends: T1, T5

- [ ] **T7 · `SchemaContractTest` per subgraph, over static composition**
  - requirements: R7
  - files: `backend/*/src/test/.../graphql/SchemaContractTest.java`
  - verify: a deliberately broken subgraph fails the test
  - parallel-safe: yes — one service per agent
  - depends: T1

- [ ] **T8 · Tag every test; wire the three-stage CI split**
  - requirements: R1, R7
  - files: every test class, `.github/workflows/`
  - verify: fast stage under 60 s; full suite under 10 min
  - parallel-safe: no — one workflow
  - depends: T2, T3, T4, T5, T6, T7

- [x] **T9 · `-DfailIfNoTests=false` on every module-scoped verify command** — *done 2026-08-18; 47 commands across 37 spec.yaml files. Proven: a bogus tag exits 0 without the flag and 1 with it.*
  - requirements: R7
  - files: every `spec.yaml` `verify:` block
  - verify: `mvn -f backend/<module> test -Dgroups=ET-XXX-999 -DfailIfNoTests=false` exits non-zero rather than reporting success on an empty selection
  - parallel-safe: yes — one spec per agent
  - depends: —

## 6. Out of scope

| Capability | Spec |
|---|---|
| The production `Clock` bean and module boundaries | [ET-PLT-001](../001-runtime-baseline/) |
| The replica set in deployed environments | [ET-PLT-002](../002-persistence-baseline/) |
| Composition as a CI gate and codegen freshness | [ET-PLT-004](../004-federation-contract/) |
| The error registry the refusal assertions check against | [ET-PLT-005](../005-error-contract/) |
| Production metrics, tracing and alerting | [ET-ADM-005](../../admin/005-observability-and-health/) |
| Load and soak testing against a deployed environment | operations, not a spec in this corpus |
| Which specific rules each capability tests | the spec that introduces the rule |

Deliberately never in scope: **embedded MongoDB** (no replica set, therefore every
transaction test is a false positive), and **a coverage percentage as the quality gate**
(the gate is that every acceptance box names a test).
