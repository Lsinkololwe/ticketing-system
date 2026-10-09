# ET-PLT-002 · Persistence baseline — tasks

> **Spec** [`specs/_platform/002-persistence-baseline/spec.md`](../_platform/002-persistence-baseline/spec.md) · **Wave 0** · `blocked_by:` ET-PLT-001
> **Screens** — none.
> **Verify** `mvn -q -f backend test -Dgroups=ET-PLT-002` · `mvn -q -f backend verify -Dgroups=ET-PLT-002`

The longest spec in the corpus (5,843 words, 8 requirements, 58 acceptance boxes) and the one
that decides whether money is correct. Its §4 is the **67-row collection registry** every other
spec's `persistence.collections` is checked against.

> **Corrected at R0 (2026-08-18).** The table holds **67** rows — catalog 12, booking 27,
> identity 28 — while §4's own closing sentence said 66 and this file claimed "all 66 currently
> reconcile". The count was not re-derived when ET-PLT-013's three permission collections and
> ET-PLT-014's `catalog_reference_data` were added. A closed registry whose stated total does not
> match its own table cannot be the thing every other spec is checked against, so
> `CollectionRegistryLintTest` now asserts the two agree.

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

## R0 findings *(2026-08-18)*

**Static half — done.** 48 `@Document` classes against the 67-row registry.

| Finding | Count | Class |
|---|---|---|
| Collections declared at a **non-registry name** | 43 of 48 | `contradicted` |
| …of those, a plain missing service prefix (`users` → `identity_users`) | 38 | mechanical |
| …`event_categories` → `catalog_categories` (a rename prefixing alone never finds) | 1 | mechanical |
| …no registry row under **any** name | 4 | needs a §4 decision |
| Registry rows not yet declared | 62 → **23** after the renames | later specs own these |
| Missing `@TypeAlias` | 48 | `absent` |
| Document fields on a zoneless time type | **124** (booking 59, catalog 51, identity 14) | `contradicted` |
| Money on a floating-point type | **0** | `already-satisfied` |
| `@Version` disagreeing with §4 | 11 | `contradicted`, both directions |

**Live half — blocked, and not worked around.** R0 calls for MongoDB MCP against the running
`ticketing` database to check that declared indexes exist with the filter and uniqueness §4
specifies. Every route to it is refused by the environment's database-access policy, which
cannot confirm the preconfigured connection is loopback. **So nothing here has been checked
against real data**: a partial unique index that no test contends against still passes
everything, which is the exact case R0 wanted the live check for. BE-4's index-set equality
test is the substitute and it is not yet written.

### The registry contradicted itself

§4's table holds **67** rows; §4's own closing sentence said **66**, and this file claimed "all
66 currently reconcile". Corrected, and `CollectionRegistryLintTest` now asserts the table and
the sentence agree — a closed registry that miscounts itself cannot be the thing every other
spec is checked against.

### Four collections have no registry row

Left at their current names on purpose. Renaming them would invent a row in a registry §4 calls
closed, and each is a different question:

| Collection | Class | The question |
|---|---|---|
| `escrow_accounts` | `EscrowAccount` | An **orphan** — referenced only by its own repository, while `EventEscrowAccount` is used across 20+ files. Recommend deleting both. |
| `booking_purchase_escalations` | `PurchaseEscalation` | Is this ET-ADM-003's `booking_recovery_proposals` under another name? |
| `approval_notifications` | `ApprovalNotification` | Catalog owning a notification collection, when identity owns `identity_notifications`. |
| `platform_configuration` | `PlatformConfiguration` (catalog) | Catalog's copy of a row §4 gives to identity. |
| `payout_config_audit_logs` | `PayoutConfigAuditLog` | Neither `identity_audit_logs` nor a row of its own. |

## The five unregistered collections, analysed *(2026-08-19)*

Checked against the corpus and against their callers, because "no §4 row" and "not needed" are
different claims. **Three of the five are live features the specification does not describe; two
are genuinely dead.** None of them is redundant in the sense of duplicating a registry row.

| Collection | Callers | Spec position | Verdict |
|---|---|---|---|
| `escrow_accounts` | repository, schema config, migrations — **no business code** | §4 has `booking_escrow_accounts`; `EventEscrowAccount` is used in **31** files against `EscrowAccount`'s 5 | **dead — safe to drop** |
| `approval_notifications` | repository and a migration only — **no business code** | §4 gives notifications to identity (`identity_notifications`, ET-NTF-001); catalog owning one contradicts that | **dead — safe to drop** |
| `platform_configuration` | 10 files, **two services** | §4 says `identity_platform_configuration`, owned by ET-ADM-002 | **live — moved 2026-09-19 to `catalog_platform_configuration`** |
| `booking_purchase_escalations` | 6 files | nothing in the corpus | **live — needs a §4 row** |
| `payout_config_audit_logs` | resolver, service, impl, repository | nothing; `identity_audit_logs` is the general trail (ET-PLT-009) | **live — needs a §4 row** |

### `platform_configuration` is written by two services

`catalog.PlatformConfiguration` and `identity.PlatformConfigurationView` both carry
`@Document(collection = "platform_configuration")`. Two services, one unprefixed collection —
precisely the arrangement the prefix scheme exists to prevent, and §4 assigns the row to identity.

`CollectionRegistryLintTest.oneClassPerCollection` runs a budget of 1 and its comment claimed the
budget was *unused*. It is not: this pair occupies it. The guard therefore has **no headroom** —
the next occurrence fails the build, and this one passes only by being tolerated. Comment
corrected; moving the row to identity is what lowers the budget to zero.

### `booking_purchase_escalations` is not ET-ADM-003's recovery queue

R0 guessed it might be `booking_recovery_proposals` under another name. Comparing the shapes says
otherwise:

| `PurchaseEscalation` (live) | `booking_recovery_proposals` (ET-ADM-003 §4, **unimplemented**) |
|---|---|
| `reason`, `detail`, `amount`, `resolved`, `resolvedBy`, `resolution` | `action`, `proposedById`, `confirmedById`, `status`, `expiresAt`, `idempotencyKey`, `outcome` |
| one person records a resolution | **dual control** — `confirmedById` ≠ `proposedById`, `PT2H` expiry |

Different guarantees. One is a record that somebody dealt with a failed purchase; the other is a
two-person authorisation for a money-moving fix. Merging them would quietly drop the four-eyes
requirement ET-ADM-003 exists to impose.

Note the direction of the gap as well: `BookingCollections.RECOVERY_PROPOSALS` exists as a
constant generated from §4, and **no document class implements it**. So the corpus has a
specified-but-absent collection and an implemented-but-unspecified one, on the same subject,
and they are not the same thing.

### Applied *(2026-08-19)*

**Dropped, both empty.** `escrow_accounts` and `approval_notifications` each held **0 documents**
when the drop ran, which is the confirmation the callers analysis predicted — nothing had written
either of them. Four classes deleted (the two documents and their repositories), their references
removed from the schema validator and the conformance migrations, and `RetiredCollectionDrop`
logs the count at WARN before removing the collection so an unexpected population would surface
while the backup is still current.

**Opened as §4 rows.** `booking_purchase_escalations` (already prefixed, so no rename) and
`identity_payout_config_audit_logs` (renamed from the unprefixed `payout_config_audit_logs`,
`1 renamed, 0 already absent, 0 refused`). The registry's closing count moves 67 → 69.

The rename needed **its own migration step**. `collection-registry-rename` is already recorded
SUCCEEDED, so extending the table it reads would have changed what an applied row means without
ever re-running it — the same immutability the index repair ran into three times.

**Unregistered ratchet 5 → 1.** What remains is `platform_configuration`, and it is not a missing
row: §4 already carries `identity_platform_configuration` (ET-ADM-002) and catalog holds the
collection anyway, with identity's `PlatformConfigurationView` mapping the same unprefixed name.
Closing it means catalog giving the collection up and reading it over the graph — a cross-service
move that belongs to ET-ADM-002, not to this slice.

Deleting the two classes also removed inline `now()` sites, which the clock ratchet caught
immediately: catalog 38 → 32, booking 149 → 147, both budgets lowered to lock the gain in.

## A · Backend

- [x] **BE-3 · rename to registry names** — *39 collections, 66 files.* Done by introducing
  `CatalogCollections` / `BookingCollections` / `IdentityCollections`, generated from §4, so a
  collection name is a constant rather than a literal.
  - **The annotation was the smallest part.** 81 further occurrences named collections as bare
    strings — `$lookup` sources, `mongoTemplate.aggregate` targets, index initialisers, schema
    validators and migrations. Renaming only `@Document` would have left every one of them
    pointing at a collection that no longer exists, and **that fails silently**: an aggregation
    over a missing collection returns zero rows, so the organizer dashboard reports K0.00 and
    the category breakdown reports zero for every category, both indistinguishable from a quiet
    week.
  - **A collision the rename would have created.** `EscrowAccount` and `EventEscrowAccount` are
    different shapes; prefixing the first would have aimed both at `booking_escrow_accounts` and
    interleaved two schemas in the collection that holds the money. Caught by the
    "bare literals remaining" report, not by the compiler — both versions compile.
  - **`MoneyFieldMigrationService` named 11 booking collections at their old names.** It lives in
    shared-library, which may not import a service's constants, so those stay literals — which is
    the argument for moving it into booking (T5), where drift would be a compile error.
- [x] **BE-10 · `@TypeAlias`** — added to 48 documents, aliased to the collection's own short
  name rather than the class name, so moving or renaming a class cannot orphan its documents.
- [x] **`CollectionRegistryLintTest`** — 7 assertions, parsing §4 directly rather than copying it.
  Budgets: unregistered 5, extra `@Version` 10, zoneless timestamps booking 59 / catalog 51 /
  identity 14. Each may only fall.
  - **The lint caught its own blind spot.** After the rename it matched only 5 documents,
    because it looked for string literals; its "an empty sweep is not a passing lint" guard
    failed the build rather than reporting green. It now resolves the constants.
- [x] **`PromoCode` gained `@Version`** — §4 marks `booking_promo_codes` versioned and
  `currentUses` is why: redemption is read-check-increment against `maxUses`, so two concurrent
  checkouts both see room and one redemption is lost. A code capped at 100 settles more than 100
  discounts, and it surfaces in reconciliation rather than at the point of sale.
- [x] **10 documents carry `@Version` where §4 says unversioned** — *settled 2026-09-19: §4 now
  marks the ten rows versioned; the code was right. `versionedRowsCarryVersion` budget 10 → 0.*
  Original note: — Ticket, JournalEntry,
  PaymentAttempt, ChargebackRecord, CommissionRecord, ChartOfAccountsEntry, BankAccount,
  RefundRequest, StandaloneEscrowTransaction, ReconciliationRun. **Deliberately not "fixed" by
  deleting the annotation**: removing optimistic locking can only make concurrency worse, so the
  safe direction is to leave it and get the decision. Several are append-only, where `@Version`
  costs nothing and protects nothing; `Ticket` is not, and is the one worth deciding first.

- [x] **The rename migration** — *done, 38 collections across the three services.*
  - **One engine, three tables.** `CollectionRenameMigration` in shared-library; each service
    holds its own source→target table. Thirty-eight near-identical migration classes would have
    been thirty-eight chances for one to differ, and the difference would not announce itself.
  - **`renameCollection`, not `$out` + drop.** Atomic, carries the collection's indexes across,
    and needs no second copy on disk. `$out` writes documents and nothing else, so every index
    would have to be rebuilt — and a missing index is not an error, it is a `COLLSCAN` nobody
    notices until load. Asserted: `name_1` survives the move.
  - **An empty target is the normal case, not a conflict.** A service that booted against the
    renamed code has already created the new collection via index or schema-validator setup.
    Refusing there would mean never migrating any environment where the application started
    first — which is all of them. Empty target is dropped; **populated** target is refused,
    because two populated collections mean someone has written to the new name and no automatic
    resolution is safe.
  - **Runs first** in each service's runner. Every conformance migration rewrites fields inside
    collections this one moves, and a field rewrite against an empty destination reports a clean
    zero — the same output as success.
  - **Catalog had no `MigrationRunner`.** Adding the migration as a bean with nothing to invoke
    it would have repeated the exact failure `MigrationRunner` exists to prevent, so
    `CatalogMigrationRunner` was written.
  - **Seven container-backed tests**, against a replica set. Every assertion watched failing:
    dropping the populated-target refusal fails `populatedTargetIsRefused`; refusing empty
    targets fails `emptyTargetIsNotAConflict`.
  - **One assertion was vacuous and was rewritten.** Removing the `from.equals(to)` guard did
    *not* fail `selfRenameIsRefused`, because a self-rename on a *populated* collection is
    already stopped by the populated-target check — the test proved that check, not this one.
    On an **empty** collection the populated-target branch waves it through to
    `dropCollection(to)`, which drops the source. Rewritten to use an empty collection; it now
    fails under the mutation.

> ### ⚠️ Two collections are deliberately not migrated
>
> **`escrow_accounts`** stays put. It belongs to the orphaned `EscrowAccount`, whose shape
> differs from the per-event `EventEscrowAccount` that already owns `booking_escrow_accounts`.
> Moving it would merge two schemas into the collection that holds the money.
>
> **`platform_configuration`** stays put, and identity's `PlatformConfigurationView` was moved
> *back* onto the unprefixed name to match. Two classes declare this collection — identity's
> view and catalog's `PlatformConfiguration`, which has no §4 row and has not moved. Renaming
> the data would take it out from under catalog, which would then report that the platform has
> no configuration; pointing the view at the new name without moving the data would do the same
> to identity. Both are silent. Staying put is the only option that changes nothing while the
> ownership question is open.

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

> **Three index rows added to §4 when ET-PLT-003's outbox landed** — `{service}_outbox
> { status: 1, stagedAt: 1 }`. The drain claims with `status = PENDING` sorted by `stagedAt`;
> without the index that is a collection scan plus an in-memory sort, and MongoDB aborts an
> in-memory sort above 32MB. An outbox is largest exactly when it has fallen behind, so the
> failure arrives at the moment the drain is most needed. Registry now 49 rows.

- [x] **BE-4 · declare every index; assert it on a live server** — *46 declarations, 25 tests.*
  - **34 of §4's 46 rows did not exist.** All three services run with
    `auto-index-creation: true`, so the live index set came from 198 `@Indexed` annotations.
    An annotation produces one single-field index and nothing else, so **every compound index,
    every TTL, and every sparse-unique pair in §4 was absent**. Among them:
    | Missing | Consequence |
    |---|---|
    | `booking_tier_inventory { tierId }` **unique** | two inventory rows for one tier; the conditional decrement guards *one of them* and the tier oversells while every counter reconciles |
    | `booking_webhook_receipts { providerEventId }` **unique** | a redelivered provider callback is not stopped at the database, and the application's own check is a read-then-write two deliveries both pass |
    | `identity_organization_members { userId, organizationId }` **unique** | accepting an invitation twice creates two memberships; ET-ORG-002's owner-count invariant counts one person twice |
    | `identity_users { phoneNumber }` unique+sparse | phone is the login identity, so a duplicate is two accounts one OTP opens |
    | `booking_tickets { ticketReference }` unique | the scanned identity is not unique |
  - §4 opens by saying uniqueness rows are *constraints, not optimisations — each one is a race
    the application cannot win by checking first*. None of these makes anything slower when
    absent. They permit the duplicate, under exactly the concurrency that makes it matter.
  - **`IndexSpec` + `IndexEnsurer` in shared-library**, per-service `*IndexInitializer` holding
    that service's rows. Each index is attempted independently: `Mono.when` over a batch fails
    the batch, and booking's existing config carries a comment saying error 85 aborted the
    migration runner on startup once already.
  - **Tests assert the property, not the metadata.** A unique index is proven by inserting the
    duplicate and requiring the server to refuse it — a unique index built over the wrong field
    reports identical metadata and refuses nothing. `explain()` covers §4's five hot rows.
  - **Two bugs of mine, both caught by the tests.**
    - `Map.copyOf` is hash-ordered, so compound key order was randomised per JVM run.
      `{userId:1, status:1}` and `{status:1, userId:1}` are *different* indexes — only the first
      serves a query on `userId` alone — and the server rejected the second run with
      `IndexKeySpecsConflict` having built the wrong index on the first. Now `LinkedHashMap`.
    - `.thenReturn(null)` throws while the chain is assembled, before `onErrorResume` exists to
      catch anything, so the duplicate-key error under test never surfaced.
  - **A mutation passed, and the assertion was rewritten.** Reversing a compound index's key
    order in the declaration did not fail anything: `assertAllPresent` compares the server to
    the code, and changing the code moves the server with it. `assertMatchesRegistry` now parses
    §4 and compares the declarations to the specification, closing §4 → code → server.

> ### ⚠️ `auto-index-creation` stays on, deliberately
>
> §4's acceptance is index-set **equality**, and it is not met on a real environment. The
> annotations also create **28 unique constraints §4 never recorded** — `chargebackId`,
> `entryNumber`, `ticketNumber`, `accountCode` and others that look entirely legitimate.
>
> Turning auto-creation off now would leave every *new* environment without them. Deleting them
> to reach equality would drop live uniqueness constraints in order to satisfy a document, which
> is the wrong way round. They belong in §4 first; the switch flips after that.
>
> Also outstanding: `MongoIndexConfig` creates indexes on `financial_transactions`, a collection
> with no `@Document` and no registry row — and creating an index creates the collection.

### BE-4 · Declare every index; add the live-index assertion test
- **Spec** R3 · **§5** T4 · **depends** BE-3 · **parallel-safe** yes
- **Files** `backend/*/src/main/java/com/pml/*/config/MongoIndexInitializer.java`
- **Acceptance** index-set **equality** (not containment — an extra undeclared index is drift);
  `explain()` reports `IXSCAN` on the five hot queries named in §4.
- A `COLLSCAN` on a hot query at on-sale peak is an outage, and it is invisible until the peak.

- [x] **BE-5 · money** — *R4's three rules, each now asserted.*
  - **Rounding was already consistent** — 26 sites, every one `HALF_UP` at scale 2. R4's real
    exposure was elsewhere.
  - **`Money` in shared-library** decides scale, mode and default currency once. R4 requires
    rounding *applied once*, and per-site decisions lose that quietly: rounding an intermediate
    and then computing from it is not a smaller version of rounding the result. `MoneyTest`
    pins the difference — 2.5% of K100.00 is **K2.50** in one pass and **K3.00** if the rate is
    rounded first.
  - **`PromoCode.calculateDiscount` rounded nothing.** A percentage discount ran
    `total.multiply(value).divide(100)` with no rounding mode: 15% of K33.33 is K4.9995, and an
    unrounded figure is stored as `Decimal128` at whatever scale it happens to have, then summed
    into a commission and a ledger line that no longer agree to the cent.
  - **`minimumPayoutAmount` was `Double` in three places and `BigDecimal` in a fourth.**
    `PayoutConfig` (identity), `PlatformPaymentDefaults` (shared-library) and catalog's
    `PlatformConfiguration` held a double; booking's `PaymentProperties` held a BigDecimal — and
    booking is where the value is compared against an escrow balance. K0.10 has no exact binary
    form, so a payout of precisely the minimum can be refused with nothing in the numbers to
    explain it. All four are now `BigDecimal`.
  - **Two more traps in the same code path.** The GraphQL resolver built the value with
    `((Number) input).doubleValue()` — now via `toString()`, so the BigDecimal holds what the
    client sent rather than the nearest binary approximation — and compared it with
    `.equals()`, which is false for `100.0` against `100.00` and reported a change on every
    save.
  - **`TicketTier` used the deprecated `BigDecimal.ROUND_HALF_UP` int constant.** The lint does
    not accept it: an int in that position is indistinguishable from a scale at a glance.
  - **`currency` added to the three money-bearing documents that lacked it** — `TicketTier`,
    `PromoCode`, `Event`.
  - **The lint keys on field *names*, not types**, and excludes rates. `commissionRate` and
    `refundPercentage` are ratios, where a `double` is honest; flagging all seven hits when only
    two were real would have earned a blanket suppression rather than a fix.
  - Mutations watched failing: `HALF_UP → HALF_EVEN` fails two `MoneyTest` cases; removing a
    `currency` field fails the lint.

> **Backfill still owed.** `@Builder.Default` sets `currency` on newly-built objects only —
> documents already in the database deserialise with `currency` null. The three collections need
> a backfill step alongside the collection renames, in the shape
> `MoneyFieldMigrationService` already uses.

### BE-5 · Money — purge floating point, add `currency`, pin `HALF_UP` at scale 2
- **Spec** R4 · **§5** T5 · **depends** BE-2 · **parallel-safe** no *(booking only, but it is most of booking)*
- **Acceptance** no production field typed `double`/`float` names an amount, balance, price, fee,
  total or commission; every rounding is `HALF_UP` at scale 2, applied **once**; no balance is
  assigned outside the ledger.
- `currency` is stored on every monetary field even though launch is ZMW-only (D-14) —
  single-currency assumptions are cheap to make and expensive to remove.

- [x] **BE-6 · time** — *all 124 document fields migrated to `Instant`; inline `now()` down from
  467 to 236.*
  - **The auditing provider was correct and never used.** ET-PLT-001 A3 created
    `auditingDateTimeProvider` bound to the platform `Clock`, and **not one**
    `@EnableReactiveMongoAuditing` specified `dateTimeProviderRef`. Spring Data therefore fell
    back to `CurrentDateTimeProvider` and stamped every `@CreatedDate` from the wall clock, with
    a perfectly good provider sitting beside it. Nothing fails; timestamps look plausible; a
    frozen-clock test writes a document carrying real time. This is the exact failure the
    provider's own javadoc predicted. Now wired in all three services, and asserted — the test
    was watched failing when the reference was removed.
  - **`@EnableReactiveMongoAuditing` appeared three times in identity and twice each elsewhere.**
    Reduced to one canonical declaration per service, so there is no question which is in force.
  - **Documents: 124 → 0.** identity 14, catalog 51, booking 59. Per-module ratchets tightened
    to zero at each step.
  - **Inline `now()`: catalog 108 → 38, booking 229 → 149, identity 111 → 97.**

  ### What the migration actually surfaced

  `Instant` has no calendar — no `toLocalDate`, no `format`, no `plusHours` — so every place
  that needed one had to name a zone. `LocalDateTime` had been answering that question with the
  JVM default, which is right on a laptop in Lusaka and two hours out in CI or on a UTC host:

  | Site | What it silently decided |
  |---|---|
  | `Event.isHappeningToday` | an event at 00:30 Lusaka fell on the previous day |
  | `FinancialReportServiceImpl.formatPeriod` | which ISO week and month a sale was reported in |
  | `ReconciliationMutationResolver` | which calendar day a reconciliation run covers |
  | `JournalServiceImpl.generateEntryNumber` | which month a journal entry number belongs to |
  | `OrganizerDashboardServiceImpl` | where "start of this month" falls |
  | `EventQueryResolver`, `RefundServiceImpl` | that a client's wall-clock string meant UTC |

  `PlatformTime` (`Africa/Lusaka`) is now the single answer. Named zone rather than
  `ZoneOffset.ofHours(2)`: identical today, but an offset is a fact about now and a zone is a
  rule the tz database maintains.

  - **`Duration.ofMonths` does not exist, and should not.** A month is not a fixed number of
    seconds, so stepping a reporting period had to go through the calendar. Weeks became
    `Duration.ofDays(7)`, which is exact; months became `ZonedDateTime.plusMonths`.
  - **Cross-service DTOs were the quiet one.** `EventSummaryDto` and `TicketSummaryDto` live in
    shared-library and carried `LocalDateTime` between catalog and booking — two services that
    need not run in the same zone. Both are now `Instant`.
  - **The blanket rename damaged code that was already right.** `PayoutEligibility` and
    `OrganizerPayoutWindow` held `Instant` and converted explicitly at the boundary;
    `LocalDateTime.ofInstant(...)` became `Instant.ofInstant(...)`, which does not exist. Caught
    by the compiler and restored — and their conversion now goes through the platform zone
    rather than UTC, so a payout window opens on the date it opens in Zambia.

> **Remaining, and bounded.** 236 inline `now()` sites: 149 booking, 38 catalog, 97 identity,
> 4 gateway, 8 keycloak-extensions, 7 shared-library. Most are in records, event envelopes and
> DTOs whose static factories have no constructor to inject a clock into — moving those means
> changing every construction site, which is ET-PLT-003's outbox work. The ratchets hold the
> line meanwhile.
>
> The end-to-end frozen-clock audit test — write a document through a service context, assert
> `createdAt` equals the frozen instant — is **not** written. The provider is unit-tested and the
> wiring is lint-asserted, which together cover the defect found here, but they are not the same
> thing as observing a stamped document.

### BE-6 · Time — `Instant` everywhere; `DateTimeProvider` on the platform `Clock`
- **Spec** R5 · **§5** T6 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** no inline `now()` in production source; a frozen-clock audit-field test.
- This is the ~150-file change [`ET-PLT-001`](ET-PLT-001.md) R0 counted. Do it per service.

- [x] **BE-7 · optimistic locking and bounded retry** — *4 tests.*
  - `@Version` coverage was already asserted by `CollectionRegistryLintTest`: **zero** versioned
    rows without it. What was missing was a test that watches one refuse a write.
  - **`OptimisticLockingTest.staleWriteIsRefused`** does that against a replica set: two callers
    hold the same document, the second `save` raises `OptimisticLockingFailureException`, and the
    first caller's change is still there afterwards. Without it the second save wins silently and
    nothing records that the first happened.
  - **`BoundedRetry`** in shared-library, with jitter. Two callers that collide and both wait
    exactly 25ms collide again at 25ms; the jitter is what separates them.
  - **The unbounded mutation did not fail the test — it hung.** Replacing `backoff(attempts, …)`
    with `indefinitely()` produced no result at all until a 120s timeout killed the run. That is
    R6's sentence made literal: *an unbounded retry under contention is a livelock that reports
    as latency*. It never errors and never completes.
  - `BoundedRetry` deliberately does **not** wrap the inventory decrement: a conditional
    `findAndModify` that matches nothing is a refusal, not a conflict, and retrying it would be
    retrying "sold out".

- [x] **BE-8 · the atomic inventory decrement, under 200-caller contention** — *5 tests,
  `@RepeatedTest`, against a real replica set.*
  - The decrement is already the correct shape: one `findAndModify` whose filter uses `$expr` to
    compare `availableQuantity - reservedQuantity >= quantity`, evaluated with the update by the
    server in one round trip.
  - **Exactly 50, not at most 50.** More is an oversell; fewer means the filter refuses inventory
    that exists — which nobody reports as a bug, because the only symptom is a smaller number.
  - **Two mutations, and the pair is the finding:**
    | Mutation | Result |
    |---|---|
    | `findAndModify` → read-modify-write | **no oversell** — `OptimisticLockingFailureException` ×150 |
    | read-modify-write **and** `@Version` removed | **200 successes against 50 seats** |
  - So the two guards do different jobs, and the first mutation would have been easy to misread
    as "the atomic write is unnecessary". `@Version` converts a lost update into an **error** —
    at checkout, a 500 the customer sees. The conditional `findAndModify` converts it into a
    **refusal**, which is an answer. D-08 requires the second for this write precisely because a
    sold-out tier is a normal outcome, not a fault.

> ### ⚠️ §4 places these counters in the wrong service today
>
> `booking_tier_inventory` has **no `@Document` anywhere** — the collection §4 calls "the
> authoritative counters" does not exist. Reservations decrement `catalog_ticket_tiers` instead,
> which is exactly what §4's own code sample forbids: *"booking_tier_inventory, never
> catalog_ticket_tiers: the definition is catalog's, the counters are booking's."*
>
> The write is correct; it is in the wrong place. Moving it is **ET-TKT-001**'s slice — that spec
> introduces the collection — so the contention test lives in catalog for now and moves with the
> counters. It is written against the service interface rather than the query, so the move does
> not invalidate it.
>
> Two smaller notes on the same method: it stamps `updatedAt` with `$currentDate`, which is the
> **server's** wall clock and bypasses both the platform `Clock` and `@LastModifiedDate`; and
> `findAndModify` does not increment `@Version`, so a concurrent `save()` elsewhere can still
> overwrite the result.

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

## Migration readiness — what a live database needs *(2026-08-18)*

Every change made in this spec that a running database cannot pick up on its own is now a
ledger-recorded migration step. Each runs once, in order, and is idempotent.

| Step | Applies | Why the code change alone is not enough |
|---|---|---|
| `collection-registry-rename` | 38 collections | The `@Document` names moved; the data did not. Services would read empty collections and report no data rather than fail. |
| `registry-conformance-backfill` | 48 `_class` values, 18 `version` fields, 20 `currency` fields | `@TypeAlias` changes what new writes store; `@Builder.Default` runs on build, not on read; `@Version` on a document with no `version` makes Spring Data treat it as **new**, so optimistic locking guards nothing until something writes it once. |
| `money-field-decimal128` | `payoutConfig.minimumPayoutAmount`, `payment.minimumPayoutAmount` | Fields changed from `Double` to `BigDecimal` are still BSON doubles until converted. |

**The money migration would have skipped both new fields.** Its filter matched only
`$type: "string"`, and a Java `Double` lands in BSON as a `double` — so it would have reported
zero conversions, which is the same output as "nothing to do". Widened to
`string | double | int | long`, and deliberately **not** `decimal`: rewriting a field that is
already correct makes every run report work it did not do.

**Ordering is load-bearing.** The backfills filter on registry collection names, so they run
after the rename — before it, the documents are still under their old names and nothing matches.
`MigrationRunner` runs steps with `concatMap` for exactly this reason.

**Idempotence is asserted, not assumed.** Every operation filters on the absence of what it
sets, so a second run reports zero. `DocumentBackfillTest` proves it, and proves the reverse
matters: with the absence filter removed, the backfill reset a live `version: 7` to `0` — which
would hand every in-flight writer a stale document the server then accepts.

Not yet covered by a migration, and stated rather than implied:

- **Three collections keep pre-registry names by decision** — `escrow_accounts`,
  `platform_configuration`, and the two others awaiting a §4 answer. Their documents are not
  moved and not backfilled.
- **`LocalDateTime` → `Instant` needs no data migration.** Both map to BSON `Date`, so stored
  values are already instants. What changes is interpretation: a value written by a JVM in a
  non-Zambian zone was stored shifted by that offset, and now reads as the instant it literally
  is. On a host in `Africa/Lusaka` nothing moves; elsewhere, historical timestamps shift by the
  difference. No migration can distinguish the two cases from the data alone.

- [x] **BE-9 · relational removal and Redis confinement** — *3 container-backed tests.*
  - **The relational half was already satisfied**, and is guarded rather than merely true: no
    module declares a relational dependency or a datasource, and ET-PLT-012's enforcer bans
    `postgresql`, `data-jpa` and `jdbc` at the reactor root, so a transitive one fails the build.
  - **All nine Redis writes carry a TTL** — OTP and its cooldown, the idempotency guard, consumer
    deduplication, the sweep lock, rate-limit counters, the two caches and the revocation marker.
  - **`RedisNode`** joins the harness: `redis:7-alpine` with `--appendonly yes`, matching the
    compose file. **Its own container, never `dev_redis`** — the development instance is shared
    with Twende-Ride and Delight Store, and a `FLUSHALL` against it would take two other
    projects' data with it.
  - **The flush test asserts the decision, not the key.** `identity_token_revocations` is the
    system of record and Redis is a read-through cache over it. A flush that left a revoked token
    reading `ACTIVE` would un-revoke every session on the platform at once — no error, no alert,
    and no symptom until somebody who was signed out is let back in. The test warms the cache,
    confirms `REVOKED` through it, flushes, and requires `REVOKED` again from the durable store.
  - **The opposite failure is asserted too.** A fail-closed cache that treated "I don't know" as
    `REVOKED` after a flush would sign out every user until the warmer ran. A token that was
    never revoked must not come back `REVOKED`.
  - Mutation: making the durable store forget its revocations fails the test — so it is the
    fall-through being measured, not a cache that happened to survive.

> **The `--appendonly yes` in the compose file is worth knowing about.** Redis persists to disk
> and survives a restart, so a service that wrongly kept business state there would look correct
> indefinitely. Only removing the data distinguishes a cache from a database, which is why R7
> asks for a flush rather than an inspection of TTLs.

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

## Migrations run against the live dev database *(2026-08-19)*

All three services run, in order, against `dev_ticketing` on the local stack. **35 collections
renamed, 2 renames refused, 13 index declarations refused.** The renames and backfills are done;
the three findings below are not, and none of them was visible from the code.

| Service | Renamed | Refused | Backfilled | Index conflicts |
|---|---|---|---|---|
| catalog | 8 | 1 | `catalog_reference_data._class` × 130 | 1 |
| booking | 12 | 1 | nothing | 5 |
| identity | 15 | 0 | `_class` × 2 | 7 |

### 1 · Index conformance — resolved, and it took three passes

**End state: catalog 7, booking 26, identity 18 indexes, `0 conflicting` in all three**, each
verified by a restart rather than by the run that made the change.

The three defects behind the 13 conflicts, in the order they surfaced:

**a. Two TTL indexes that expired nothing.** `booking_reservations` and
`identity_team_invitations` each carried a *plain* index on `expiresAt` where §4 declares a TTL
index. Unpaid holds and expired invitations sat there indefinitely, behind an index that reads as
correct in `getIndexes()` unless its options are checked. Both replaced.

**b. `sparse` is the wrong tool for an optional field, and §4 said `sparse`.** Sparse excludes a
document where the field is *absent*; it still indexes one storing an explicit `null`, which is
what mapping an object with a null field produces. Under `unique` the second such document
collides on the key `null`. Proven, not theorised — building `identity_users.idx_phoneNumber`
failed with `E11000 dup key: { phoneNumber: null }`.

`IndexSpec` could not express the alternative at all, so `partial()` / `partialWhereTypeIs()`
were added and **§4's three sparse rows corrected to partial**. Four declarations changed; only
one had fired, the other three were waiting for their first null:

| Index | Consequence had it stood |
|---|---|
| `identity_users.idx_phoneNumber` | **already failing** |
| `identity_users.idx_email` | one user without an email, then no others |
| `booking_payment_attempts.idx_providerReference` | second unacknowledged attempt rejected, under retry load |
| `booking_checkins.scanId` | a gate recording one offline scan could record no others |

**c. A second index authority.** `ReservationIndexInitializer` created indexes on
`booking_reservations` from its own `@EventListener`, outside the registry, outside the index
tests and invisible to the repair. It existed because its unique index needs a partial filter the
registry could not express — and **nothing called** its `ensureAll`, so that half was dead code.
Both its indexes are now §4 rows and the class is deleted.

Its partial filter is worth keeping in view: without `status = HELD`, a buyer could never reserve
the same tier **again, ever** — a released reservation from last month would still hold the key.

### 2 · What the repair itself got wrong, twice

`ConflictingIndexRepair` was written for this and was wrong in two ways that the live database
found and no test had:

- **It dropped, failed to create, and left nothing behind.** `identity_users` was briefly without
  a `phoneNumber` index at all. It now captures each index's definition before dropping and
  restores it if the create fails, rebuilding from the server's own `listIndexes` output so
  options it does not model survive.
- **It equated "an index with the declared name exists" with "the declared index exists".** An
  index can hold the right name and the wrong options — the server refuses that with error **86**
  rather than 85 — so the repair reported *already declared* while the constraint on disk was a
  different one. Options are now compared too. That is how `idx_email` was found: it had the
  registry's name and sparse's behaviour, plus a second index `idx_users_email` on the same field.

A third, smaller one: text indexes are stored as `{_fts, _ftsx}` with the field names moved to
`weights`, so key-equality never matched and catalog's text index was skipped while
`IndexEnsurer` went on reporting it — the two components disagreeing in the same log with nothing
to say which was right.

**Each correction needed a new step name.** A recorded ledger row is immutable, so
`index-registry-conformance` → `-2` → `-3`; the earlier rows stay as the record of what the
earlier passes did.

### 3 · Two renames refused, both correctly

- `reference_data → catalog_reference_data` — target already held **204** documents
- `chart_of_accounts → booking_chart_of_accounts` — target already held **26** documents

Both sets were left untouched, which is the designed behaviour: this needs a decision about which
population is authoritative, not a retry. Note catalog's backfill then stamped `_class` on 130
documents in `catalog_reference_data`, so that collection now mixes two provenances. Reconcile it
against the pre-rename `reference_data` before anything reads it.

### What this changes about R0

R0's live half was recorded as *"blocked, and not worked around … nothing here has been checked
against real data"*. It has now been checked. Everything the static half claimed held up; all
three findings above are things only a database with history could show, and two of them
(the dead TTLs, the possibly-non-unique idempotency index) touch guarantees the platform sells.

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

- [x] Inventory moves by one conditional atomic update; no read-compare-write remains — R6
  - The live purchase path was already correct: `InventoryServiceImpl` uses `findAndModify` with an
    `$expr` filter asserting `availableQuantity - reservedQuantity >= n` and `$inc` for the move.
  - Two paths were not. `decrementAvailability`/`incrementAvailability` were the forbidden shape and
    **dead** — zero callers, by name or method reference — a second implementation of inventory
    sitting beside a correct one and differing on whether a venue oversells. Deleted.
  - The live one was `updateTier`, and it is [F-026](../FINDINGS.md): an organiser adjusting
    capacity read `availableQuantity`, and a sale committing in between was silently undone —
    returning a sold seat to the pool to be sold again. Now a single atomic `$inc`.
  - `InventoryWriteShapeLintTest` bans the shape across all three services, self-checked against
    the exact line removed and the atomic form that replaced it.

- [x] R0 recorded, including the **live** audit of collections and indexes — done 2026-08-19 by
      running all three migration runners against `dev_ticketing`. Not via MCP, which stays
      refused: the services' own `IndexEnsurer` reports every declaration the server rejected,
      which is the same audit from the other end. It found 13 conflicting indexes and 2 refused
      renames — see the section above
- [x] Replica set in dev; rollback test passes on it and fails on standalone —
      `TransactionRealityTest` proves both halves; it carries `@Tag("ET-PLT-006")` because that
      spec owns the harness, but this is the evidence for this row
- [x] All registry rows reconcile; no unregistered collection exists — **done 2026-09-19.** The
      platform settings are one table like the reference data (product owner): catalog writes
      `catalog_platform_configuration`, identity reads it through shared-library's
      `PlatformConfigurationReader`, and identity's `PlatformConfigurationView` is deleted.
      Catalog step `platform-settings-rename` moves the data. `everyDocumentNamesARegistryRow`
      and `oneClassPerCollection` both drop 1 → 0. `PlatformSettingsTableTest` (L2, replica set,
      real validators) proves the move keeps an administrator's values, seeds once, and that
      identity reads money as an exact decimal — and found the validator refusing that decimal,
      which would have stopped catalog starting on a new database. Fixed.
  - Superseded record:
    All 66 registry rows reconcile; no unregistered collection exists — **one remains, not five.
      The figure this row carried was stale.**
  - `CollectionRegistryLintTest` is the authority and has been frozen at **1** since 2026-08-19.
    Re-measured 2026-09-02 and it holds. The row's list of five did not survive contact:
    `escrow_accounts` and `identity_payout_config_audit_logs` are `@TypeAlias` values — R8's
    package-independent *document type*, not collection names, and both classes carry a properly
    prefixed `@Document(collection = …)`. `approval_notifications` is a **retired** collection that
    `CatalogMigrationRunner` drops. `booking_purchase_escalations` became a §4 row.
  - The one that is real is `platform_configuration`, and it is not a missing row: §4 already
    carries it as `identity_platform_configuration`, and **ET-ADM-002 declares
    `service: identity-service`**. So the ownership question this row was said to be "awaiting a
    decision" on is already decided — what is missing is the *implementation* of ET-ADM-002, which
    owns the move: identity takes the row, catalog reads it over the graph.
  - Verified rather than assumed while re-measuring: the shared document is safe today. Catalog
    **owns and writes** it; identity's `PlatformConfigurationView` is read-only and declares only
    the payment defaults it needs, ignoring catalog's approval fields. Two services on one
    unprefixed collection is what the prefix scheme exists to prevent, and it is currently a
    boundary violation rather than a data-loss one.

  **The `@Version` half of this row was misframed, and re-measured 2026-09-02.** Ten documents do
  carry `@Version` where §4's Versioned column says no — all in booking. That is **not an R6
  violation**: R6's acceptance is positive only, *"`@Version` is present on every document in §4
  marked versioned"*, and all eleven of those do have it. Nothing forbids the extra ten.

  It is still worth resolving, and [F-026](../FINDINGS.md) is why: `@Version` is not free, because
  it *looks* like protection. `TicketTier` carried it and was not protected — the inventory path
  writes through `findAndModify`, which does not bump the version field, so the lock covered every
  field except the two writers actually contend for. Each of the ten is therefore either genuinely
  protective, and §4 should say so, or noise on an append-only ledger where it advertises a
  guarantee nothing enforces. That is a per-document judgement, not a sweep.
- [x] Index sets equal §4 exactly; five hot queries `IXSCAN` — **`0 conflicting` on the live
      database in all three services** (catalog 7, booking 26, identity 18), each confirmed by a
      restart rather than by the run that changed it. Getting there corrected §4's three
      `sparse` rows to `partial`, added the two reservation indexes a second initializer had
      been creating outside the registry, and fixed three defects in the repair itself — see the
      section above. 48 declarations, 26 tests;
      `assertMatchesRegistry` compares the server to §4 rather than to the code, after the
      code-to-server comparison passed a mutation that reversed a compound key order
- [x] No `double`/`float` money anywhere; `HALF_UP` scale 2 once; `currency` on every amount —
      `MoneyDisciplineLintTest` and `MoneyTest`
- [x] No JDBC driver, no datasource, in any service — enforced at dependency resolution by the
      parent POM's `bannedDependencies` rule (postgresql, data-jpa, jdbc), not by a test that
      could be skipped; no `spring.datasource` in any service's configuration
- [x] `FLUSHALL` mid-suite loses no business data — `RedisFlushTest`, against a real container
- [x] 200-against-50 yields exactly 50, repeatedly, on a replica set — `InventoryContentionTest`,
      `@RepeatedTest(3)`. The mutation that mattered: reverting to read-modify-write did **not**
      oversell, because `@Version` caught it; removing `@Version` as well produced **200
      successes against 50 seats**. The pair is the finding — `@Version` turns a lost update into
      an error, `findAndModify` turns it into a refusal
- [x] `mvn -q -f backend verify -Dgroups=ET-PLT-002 -DfailIfNoTests=false` green — exit 0 across
      the reactor, 2026-09-02
- [x] Spec `status:` → `implemented` — **2026-09-19.** The last two rows closed: the platform settings
      moved to one catalog-owned table, and §4 marks the ten `@Version` rows versioned. Formerly
      held back by the unregistered collections above
