# ET-PLT-002 · Persistence baseline — tasks

> **Spec** [`specs/_platform/002-persistence-baseline/spec.md`](../_platform/002-persistence-baseline/spec.md) · **Wave 0** · `blocked_by:` ET-PLT-001
> **Screens** — none.
> **Verify** `mvn -q -f backend test -Dgroups=ET-PLT-002` · `mvn -q -f backend verify -Dgroups=ET-PLT-002`

The longest spec in the corpus (5,843 words, 8 requirements, 58 acceptance boxes) and the one
that decides whether money is correct. Its §4 is the **66-row collection registry** every other
spec's `persistence.collections` is checked against — all 66 currently reconcile.

**D-01 is the load-bearing decision:** a replica set in every environment. Against a standalone
`mongod`, `@Transactional` on a reactive method is **silently inert** — it does not error, it
does nothing. Reservations, escrow movements and journal pairs are all multi-document writes, so
a standalone dev database gives you a platform that oversells under load and passes its tests.

## R0 · Reconcile *(do this first)*

Two halves, and the second is where MongoDB MCP earns its place:

**Static** — every `@Document` class against the §4 registry:
```bash
grep -rn '@Document' backend --include='*.java' | grep -v /src/test/
grep -rln 'double \|float \|Double \|Float ' backend/booking-service --include='*.java'
```

**Live** — point **MongoDB MCP** at the running `ticketing` database and compare reality to §4:
- `list-collections` → which of the 66 registry rows exist, and which collections exist that the
  registry does not name.
- `collection-indexes` on each → does the declared index actually exist, with the **filter
  expression and uniqueness** §4 specifies?
- `collection-schema` → are money fields `Decimal128` and timestamps `Date`, or has something
  been written as a double?

A partial unique index that no test contends against passes every test and oversells in
production. Checking it live is cheap and catches exactly that class of defect. Record the
four-way classification per requirement before editing.

## A · Backend

### BE-1 · Replica set in every environment; keyfile and `rs.initiate()` in dev
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** no *(shared compose file — coordinate)*
- **Files** `../docker-resources/mongodb/`, `../docker-resources/docker-compose.yml`
- Infrastructure lives in the **sibling repo**. A task that creates compose or Mongo init inside
  `ticketing-system/` is wrong ([README §Infrastructure](../README.md)).
- **Acceptance** `rs.status()` reports a PRIMARY; a two-document rollback test passes.
- Verify the rollback test **fails** against a standalone `mongod` before you trust it passing
  against the replica set. That contrast is the only proof the transaction is real.

### BE-2 · `ReactiveMongoTransactionManager`; `Decimal128` and `Instant` converters
- **Spec** R1, R4, R5 · **§5** T2 · **depends** BE-1 · **parallel-safe** yes *(one service per agent)*
- **Files** `backend/*/src/main/java/com/pml/*/config/MongoConfig.java`
- **Acceptance** round-trip tests for `K1234.56` and a frozen `Instant`.

### BE-3 · Rename every collection to its registry name; add the lint check
- **Spec** R2 · **§5** T3 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** every `@Document` names a row of §4 **and** carries `@TypeAlias`; no document
  field is `LocalDateTime` or `java.util.Date`; every balance-bearing document declares `@Version`.
- Confirm the rename landed with MCP `list-collections`, not only by reading the annotation.

### BE-4 · Declare every index; add the live-index assertion test
- **Spec** R3 · **§5** T4 · **depends** BE-3 · **parallel-safe** yes
- **Files** `backend/*/src/main/java/com/pml/*/config/MongoIndexInitializer.java`
- **Acceptance** index-set **equality** (not containment — an extra undeclared index is drift);
  `explain()` reports `IXSCAN` on the five hot queries named in §4.
- A `COLLSCAN` on a hot query at on-sale peak is an outage, and it is invisible until the peak.

### BE-5 · Money — purge floating point, add `currency`, pin `HALF_UP` at scale 2
- **Spec** R4 · **§5** T5 · **depends** BE-2 · **parallel-safe** no *(booking only, but it is most of booking)*
- **Acceptance** no production field typed `double`/`float` names an amount, balance, price, fee,
  total or commission; every rounding is `HALF_UP` at scale 2, applied **once**; no balance is
  assigned outside the ledger.
- `currency` is stored on every monetary field even though launch is ZMW-only (D-14) —
  single-currency assumptions are cheap to make and expensive to remove.

### BE-6 · Time — `Instant` everywhere; `DateTimeProvider` on the platform `Clock`
- **Spec** R5 · **§5** T6 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** no inline `now()` in production source; a frozen-clock audit-field test.
- This is the ~150-file change [`ET-PLT-001`](ET-PLT-001.md) R0 counted. Do it per service.

### BE-7 · `@Version` on every versioned row; bounded retry
- **Spec** R6 · **§5** T7 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** a concurrent-modification test observes `OptimisticLockingFailureException`.
- Retry is **bounded**. An unbounded retry under contention is a livelock that reports as latency.

### BE-8 · The conditional atomic inventory decrement, and the 200-caller load test
- **Spec** R6 · **§5** T8 · **depends** BE-7 · **parallel-safe** **no — the single most important write in the platform**
- **Files** `backend/booking-service/.../TierInventoryRepositoryImpl.java`
- One `findAndModify` filtering on `available >= quantity` (D-08). Read-modify-write oversells at
  on-sale and only at on-sale, which is exactly when it matters.
- **Acceptance** 200 concurrent reservations against 50 available yield **exactly** 50 successes,
  **against a real replica set under contention**. Run it repeatedly — a concurrency test that
  passes once has told you nothing.

### BE-9 · Remove every relational dependency; confine Redis; the flush test
- **Spec** R7 · **§5** T9 · **depends** BE-2 · **parallel-safe** yes
- Per **D-02**, no service connects to PostgreSQL. Keycloak keeps its own schema; nothing else
  relational exists. The old Modulith-on-JDBC arrangement in `CLAUDE.md` is superseded by
  [`ET-PLT-003`](ET-PLT-003.md)'s MongoDB outbox.
- **Acceptance** no service resolves a JDBC driver or declares a datasource; a mid-suite
  `FLUSHALL` loses **no business data**. Redis holds OTPs, idempotency guards, locks, rate-limit
  counters and caches — every key with a TTL, never business state.

### BE-10 · `@TypeAlias` on every document; assert no `com.pml.*` reaches storage
- **Spec** R8 · **§5** T10 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** a document written before its class moves package still deserialises after the
  move; **no stored `_class` matches `com.pml.*`** — check this with MCP `find`, on real data.

## B · Contract

None. This spec exposes no GraphQL.

## C · Frontend

None. **Track F0** runs in parallel.

## D · Tests

### TS-1 · Transaction reality
- **L3 (Testcontainers, replica set)** — the two-document rollback; the same test against a
  standalone container asserted to **fail**. Both directions, or the test proves nothing.

### TS-2 · Registry and index conformance
- **L3** — index-set equality per collection against §4; `explain()` asserts `IXSCAN` on the five
  hot queries; `@TypeAlias` present on every `@Document`; no `_class` matching `com.pml.*`.
- **Live audit (MCP, not a substitute for the above)** — after the suite, confirm the running
  database matches the registry.

### TS-3 · Money
- **L1** — `HALF_UP` at scale 2, applied once; rounding a rounded value is a defect.
- **L1** — no `double`/`float` in monetary positions, asserted by reflection over the document
  classes rather than by grep, so it survives a rename.

### TS-4 · Contention *(the one that matters)*
- **L3** — 200-against-50, repeated; `OptimisticLockingFailureException` observed under
  concurrent modification; the bounded retry terminates.

Tag `@Tag("ET-PLT-002")`.

## E · Gate

- [ ] R0 recorded, including the **live** MCP audit of collections and indexes
- [ ] Replica set in dev; rollback test passes on it and fails on standalone
- [ ] All 66 registry rows reconcile; no unregistered collection exists
- [ ] Index sets equal §4 exactly; five hot queries `IXSCAN`
- [ ] No `double`/`float` money anywhere; `HALF_UP` scale 2 once; `currency` on every amount
- [ ] No JDBC driver, no datasource, in any service
- [ ] `FLUSHALL` mid-suite loses no business data
- [ ] 200-against-50 yields exactly 50, repeatedly, on a replica set
- [ ] `mvn -q -f backend verify -Dgroups=ET-PLT-002 -DfailIfNoTests=true` green
- [ ] Spec `status:` → `implemented`
