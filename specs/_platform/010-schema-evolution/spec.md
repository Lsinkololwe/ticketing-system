# ET-PLT-010 · Schema evolution — event versions, GraphQL deprecation, document migration

## 1. Capability

Three schemas in this platform change over time and each breaks a different consumer when it
does. A **cross-service event** changes and a consumer in another service deserialises a
shape it does not understand. A **GraphQL type** changes and a mobile app that will not be
updated for six months stops working. A **MongoDB document** changes and a query written
against the old shape silently returns nothing.

This spec declares how each evolves without breaking what depends on it. It declares the
compatibility rules — what may be added freely, what requires a version bump, and what may
never be done — the deprecation windows, and the migration mechanism for documents already
written. It declares that the platform supports **N and N−1** of every event schema, which
bounds how long a consumer may lag before it must be updated.

The rule underneath all of it is that **the platform never breaks a consumer it cannot see**.
A mobile app in somebody's pocket, a message already in a queue, a document written last
year — none of them can be asked to change first, so the platform changes in ways they
survive.

## 2. Design decisions

**Additive changes are free; everything else is versioned.** Adding an optional field to an
event payload, a nullable field to a GraphQL type, or a field with a default to a document
breaks nothing, because every consumer binds a subset. Removing, renaming or retyping breaks
somebody, and those are the changes that need a version and a window.

**Event schemas support N and N−1, and no more.** A consumer may lag by one version. Two
means the platform carries three shapes of every message indefinitely, and the transformation
code becomes the thing nobody dares touch. One version of lag is a deployment window, not a
permanent state.

**A breaking event change is a new version plus an upcaster, not a new event type.**
`booking.TicketPurchased` v2 with an upcaster from v1 keeps one wire name and one consumer
registration. Minting `booking.TicketPurchasedV2` doubles the registry and leaves both
forever.

**GraphQL deprecates before it removes, and the window is measured in app releases.**
`@deprecated` with a reason, then removal no sooner than `schema.deprecation.window`
(P180D) — six months, because a mobile app that will not be updated for six months is the
constraint, not a preference.

> **Exception, 2026-10-06 (product owner).** Everything then `@deprecated` was removed at once,
> ahead of the window, because the platform has no released client: nothing in production calls
> it, and the only consumers were the three web apps in this repository, migrated in the same
> change (F-045). The window applies from the first release of a client; from then on this
> section binds without exception.

**Field removal is gated by observed usage, not by elapsed time alone.** Apollo GraphOS
reports which operations use a field. A deprecated field still being called at the end of
its window stays, and the client is chased. Removing it because the calendar said so breaks
the app that was still using it.

**Document migration is lazy by default and batched where it must be eager.** A document is
migrated when it is read, guided by a `schemaVersion` field. Where a query must see the new
shape — a new index, a new filter — a batch migration runs, resumably, off-peak. Migrating
millions of documents eagerly for a field nobody queries yet is work with no deadline.

**Every document carries `schemaVersion`.** Without it, a lazy migration cannot tell an
already-migrated document from an untouched one, and the migration becomes an inspection of
every field.

**A change to a shared type in `shared-library` is a platform change.** `EventEnvelope`,
`Money`, the error codes, the `@auth` directive — each is used by three services that deploy
independently, so a change is additive or it is coordinated. There is no third option.

**Rejected alternatives**

- *Supporting every historical event version.* Transformation code nobody dares touch.
- *A new event type per breaking change.* Doubles the registry and leaves both forever.
- *Removing a GraphQL field on a fixed schedule regardless of usage.* Breaks the app that was still calling it.
- *Eager migration of every document on every change.* Work with no deadline, on the write path.
- *Lazy migration with no version field.* Every read inspects every field to guess.
- *Breaking a shared type and coordinating three deploys.* The coordination fails once and two services are down.
- *Versioning the GraphQL schema as a whole (`/graphql/v2`).* Two supergraphs, two contracts, and every client on the wrong one.

## 3. Requirements

### ET-PLT-010-R1 · Additive changes are free and are declared safe

THE SYSTEM SHALL permit additive changes without a version bump, and SHALL classify every
change by the §4 table.

**Acceptance**
- [ ] The §4 compatibility table classifies every change kind as `SAFE`, `VERSIONED` or `FORBIDDEN` for each of the three schemas
- [ ] Adding an optional field to an event payload requires no version bump
- [ ] Adding a nullable field or a new type to GraphQL requires no deprecation
- [ ] Adding a document field with a default requires no migration
- [ ] A `FORBIDDEN` change fails CI — retyping a field in place, reusing a removed name with a different meaning, changing an enum constant's meaning
- [ ] A test applies one change of each kind and asserts its classification

### ET-PLT-010-R2 · Event schemas support N and N−1, with an upcaster

WHEN a cross-service event's payload changes incompatibly, THE SYSTEM SHALL increment its
version and SHALL accept the previous one for the support window.

**Acceptance**
- [ ] The wire name is unchanged; only `schemaVersion` increments ([ET-PLT-003](../003-event-contract/) §4)
- [ ] An upcaster transforms v`N−1` to v`N` at the consumer, before the handler
- [ ] Every consumer accepts N and N−1 and dead-letters anything older with `UNSUPPORTED_SCHEMA_VERSION`
- [ ] An upcaster is a pure function over the payload map, testable at layer 1
- [ ] Each version step has its own upcaster and its own test — chains are composed, never hand-written across two steps
- [ ] The registry in §4 names every live version and its upcaster
- [ ] A test publishes v1 and v2 of a changed event and asserts both are handled identically

### ET-PLT-010-R3 · A version is retired only after every publisher has moved

THE SYSTEM SHALL retire an event version only when nothing publishes it.

**Acceptance**
- [ ] `event_published_total` is labelled by `eventType` and version, so publication of an old version is observable
- [ ] A version with no publications for `schema.event.retirement-window` (P30D) may be retired
- [ ] Retirement removes the upcaster and the acceptance, in that order, in separate releases
- [ ] A message of a retired version dead-letters rather than failing a consumer
- [ ] The retirement is recorded in §4's registry
- [ ] A test publishes a retired version and asserts a dead letter, not an exception

### ET-PLT-010-R4 · GraphQL deprecates, is measured, and only then removes

WHEN a GraphQL element is to be removed, THE SYSTEM SHALL deprecate it, measure its use, and
remove it only when unused.

**Acceptance**
- [ ] Removal is preceded by `@deprecated(reason:)` naming the replacement and the earliest removal date
- [ ] The window is at least `schema.deprecation.window` (P180D)
- [ ] GraphOS field-usage reporting is enabled and consulted before removal
- [ ] A field with any usage in the last 30 days is **not** removed regardless of elapsed time
- [ ] `rover subgraph check` fails a removal that breaks a published operation ([ET-PLT-004](../004-federation-contract/) R6)
- [ ] Deprecated elements are listed in a queryable inventory with their removal dates
- [ ] A test asserts a removal of a still-used field fails CI

### ET-PLT-010-R5 · Documents carry a version and migrate lazily

THE SYSTEM SHALL stamp every document with a schema version and SHALL migrate on read by
default.

**Acceptance**
- [ ] Every collection's document carries `schemaVersion`, defaulting to 1
- [ ] A read of a document below the current version applies the migration chain and writes it back
- [ ] Migrations are pure functions from version `n` to `n+1`, registered per collection
- [ ] A lazy migration never fails a read — a migration error logs, alerts and returns the document unmigrated
- [ ] Write-back is conditional on the version, so two concurrent readers migrate once
- [ ] A test seeds documents at three versions and asserts a read of each yields the current shape
- [ ] The count of unmigrated documents per collection is a metric

### ET-PLT-010-R6 · Eager migration is batched, resumable and off-peak

WHERE a change requires every document migrated, THE SYSTEM SHALL run a batched migration
that does not contend with the write path.

**Acceptance**
- [ ] An eager migration is required when a new index, filter or aggregation depends on the new shape — the §4 table states which past migrations were eager and why
- [ ] It runs as the service's migration workflow (`migration/{collection}/{version}`, `USE_EXISTING`) — one batch of `schema.migration.batch-size` (1,000) per activity, continuing as new from a cursor; no lock exists
- [ ] It reads from a secondary where one exists and is scheduled off-peak
- [ ] Progress, rate and remaining count are queryable and are metrics
- [ ] It is idempotent — a restart re-migrates nothing already at the target version
- [ ] A failure pauses rather than aborts, leaving the cursor
- [ ] A load test runs a full migration during a simulated on-sale and asserts reservation latency is unaffected beyond a stated bound

### ET-PLT-010-R7 · Shared types change additively or are coordinated explicitly

IF a type in `shared-library` changes incompatibly, THEN THE SYSTEM SHALL require an
explicit coordinated release.

**Acceptance**
- [ ] `EventEnvelope`, the error-code enum, the `@auth` directive and the shared scalars are marked as coordinated types
- [ ] An additive change to any of them deploys freely
- [ ] An incompatible change requires a documented release plan naming the order of deploys and the intermediate compatible state
- [ ] A CI check detects an incompatible change to a coordinated type and fails, requiring an explicit override with a plan reference
- [ ] Adding an error code is additive; removing or retyping one is coordinated ([ET-PLT-005](../005-error-contract/) §4)
- [ ] A test asserts the check fires on a removed enum constant

## 4. Model

### Compatibility table

| Change | Event payload | GraphQL | Document |
|---|---|---|---|
| add an optional field | **SAFE** | **SAFE** (nullable) | **SAFE** (with a default) |
| add a required field | VERSIONED | FORBIDDEN on input; SAFE on output | VERSIONED |
| remove a field | VERSIONED | deprecate then remove | VERSIONED |
| rename a field | VERSIONED | deprecate the old, add the new | VERSIONED |
| change a field's type | VERSIONED | **FORBIDDEN** | VERSIONED |
| add an enum constant | **SAFE** | **SAFE** on output; VERSIONED on input | **SAFE** |
| remove an enum constant | VERSIONED | deprecate then remove | VERSIONED |
| **change what a constant means** | **FORBIDDEN** | **FORBIDDEN** | **FORBIDDEN** |
| **reuse a removed name differently** | **FORBIDDEN** | **FORBIDDEN** | **FORBIDDEN** |
| add a type or operation | **SAFE** | **SAFE** | **SAFE** |
| remove a type or operation | VERSIONED | deprecate then remove | VERSIONED |

The two `FORBIDDEN` rows in bold are forbidden in every schema because no version, window or
migration protects against them — a consumer that reads the old meaning from the new value
has no way to know it is wrong.

### Event version registry

| Wire name | Live versions | Upcasters | Retired |
|---|---|---|---|
| all twenty-five of [ET-PLT-003](../003-event-contract/) §4 | v1 | — | — |

The registry starts empty of history and is the artefact that grows. Each future row records
the version, the change, the upcaster class and the retirement date.

```java
// an upcaster is a pure function over the payload map — layer-1 testable, no context
public final class TicketPurchasedV1ToV2 implements Upcaster {
    public String eventType()  { return "booking.TicketPurchased"; }
    public int    fromVersion() { return 1; }

    public Map<String,Object> upcast(Map<String,Object> v1) {
        var v2 = new HashMap<>(v1);
        v2.put("currency", v1.getOrDefault("currency", "ZMW"));   // added in v2
        return v2;
    }
}
```

Chains compose: v1 → v3 applies v1→v2 then v2→v3. No upcaster spans two steps.

### Consumer acceptance

| Received version | Behaviour |
|---|---|
| current | handled |
| current − 1 | upcast, then handled |
| older | **dead-lettered** with `UNSUPPORTED_SCHEMA_VERSION` |
| newer | **dead-lettered** — the consumer has not deployed yet |

Newer dead-letters rather than attempting a partial bind, which is
[ET-PLT-003](../003-event-contract/) R3's rule.

### Deprecation

```graphql
type Ticket {
    holderName: String @deprecated(reason: "Use `owner.displayName`. Removed after 2027-02-01.")
    owner: User!
}
```

| Step | Gate |
|---|---|
| deprecate | reason names the replacement and the earliest removal date |
| wait | ≥ `schema.deprecation.window` (P180D) |
| measure | GraphOS field usage over the last 30 days |
| remove | only when usage is zero **and** the window has passed |
| verify | `rover subgraph check` passes against published operations |

A field with usage at the end of its window stays and the client is chased. The window is a
minimum, not a schedule.

### Document migration

Every document carries `schemaVersion`, default 1.

```java
// registered per collection; pure, versioned, composable
@DocumentMigration(collection = "booking_tickets", from = 1, to = 2)
public Document migrate(Document d) {
    d.put("currency", d.getOrDefault("currency", "ZMW"));
    return d;
}
```

| Mode | When | Mechanism |
|---|---|---|
| **lazy** | default | migrate on read, conditional write-back on `schemaVersion` |
| **eager** | a new index, filter or aggregation needs the new shape | a migration workflow: batched activities, continued as new from a cursor, off-peak, secondary reads |

A lazy migration failure logs, alerts and returns the document unmigrated — a read must not
fail because a migration is wrong.

`catalog_migration_runs`, `booking_migration_runs`, `identity_migration_runs`
— identical shape, one per service. Each service backfills only the collections it owns.

| Field | Notes |
|---|---|
| `_id`, `collection`, `fromVersion`, `toVersion` | |
| `mode` | `LAZY_BACKFILL`, `EAGER` |
| `status` | `RUNNING`, `PAUSED`, `COMPLETED`, `FAILED` |
| `totalDocuments`, `migratedCount`, `failedCount` | |
| `lastProcessedId` | the resume cursor |
| `startedAt`, `completedAt` | |

### Coordinated shared types

| Type | Why coordinated |
|---|---|
| `EventEnvelope` | every publisher and consumer binds it |
| `ErrorCode` enum | every service raises it; every client branches on it |
| `@auth` directive and `Role` | composition fails if the three subgraphs disagree |
| shared scalars — `BigDecimal`, `DateTime`, `JSON`, `Long`, `PhoneNumber` | composition fails on a mismatch |
| `TenantScope`, `IdempotencyGuard` | every tenant-scoped and money-moving path |

An incompatible change to any of these requires a written release plan naming the deploy
order and the intermediate state in which both shapes work. CI fails the change without a
plan reference.

### Configuration

| Property | Value |
|---|---|
| `schema.deprecation.window` | `P180D` |
| `schema.event.support-versions` | 2 — N and N−1 |
| `schema.event.retirement-window` | `P30D` with no publications |
| `schema.migration.batch-size` | 1,000 |
| `schema.migration.usage-lookback` | `P30D` |

### Error codes

None introduced. `UNSUPPORTED_SCHEMA_VERSION` is
[ET-PLT-003](../003-event-contract/)'s dead-letter reason.

## 5. Tasks

- [ ] **T1 · The compatibility table as a CI check**
  - requirements: R1
  - files: `.github/workflows/` — the classification runs as a workflow step, not a repo script
  - verify: one change of each kind is classified correctly; a `FORBIDDEN` change fails CI
  - parallel-safe: no
  - depends: —

- [ ] **T2 · The upcaster interface, registry and dispatch**
  - requirements: R2
  - files: `backend/shared-library/.../event/upcast/`
  - verify: v1 and v2 of a changed event are handled identically; chains compose
  - parallel-safe: no
  - depends: —

- [ ] **T3 · Consumer acceptance of N and N−1, dead-lettering the rest**
  - requirements: R2, R3
  - files: every bus consumer
  - verify: an older and a newer version both dead-letter, neither throws
  - parallel-safe: yes — one service per agent
  - depends: T2

- [ ] **T4 · Version-labelled publication metrics and the retirement gate**
  - requirements: R3
  - files: `backend/*/.../event/bridge/`
  - verify: a version with no publications for 30 days is retirable; a retired one dead-letters
  - parallel-safe: yes
  - depends: T3

- [ ] **T5 · GraphOS usage reporting and the removal gate**
  - requirements: R4
  - files: `.github/workflows/`, GraphOS configuration
  - verify: removing a still-used field fails CI
  - parallel-safe: no
  - depends: —

- [ ] **T6 · `schemaVersion` on every document and lazy migration**
  - requirements: R5
  - files: `backend/*/.../persistence/migration/`
  - verify: documents at three versions all read as current; two concurrent readers migrate once
  - parallel-safe: yes — one service per agent
  - depends: —

- [ ] **T7 · The eager migration workflow: batched, resumable, off-peak**
  - requirements: R6
  - files: `backend/*/.../workflow/migration/`
  - verify: a restart re-migrates nothing; reservation latency is unaffected during a full run
  - parallel-safe: no
  - depends: T6

- [ ] **T8 · The coordinated-type CI check**
  - requirements: R7
  - files: `.github/workflows/`
  - verify: a removed error-code constant fails without a plan reference
  - parallel-safe: yes
  - depends: T1

## 6. Out of scope

| Capability | Spec |
|---|---|
| The event contract and its envelope | [ET-PLT-003](../003-event-contract/) |
| Composition, contracts and `rover subgraph check` | [ET-PLT-004](../004-federation-contract/) |
| The error-code registry whose evolution this governs | [ET-PLT-005](../005-error-contract/) |
| The collection registry and index declarations | [ET-PLT-002](../002-persistence-baseline/) |
| Deployment orchestration and release process | operations, not a spec in this corpus |
| Frontend codegen and client release cadence | [ET-PLT-004](../004-federation-contract/) R7 |

Deliberately never in scope: **supporting every historical event version** (transformation
code nobody dares touch), **a new event type per breaking change** (doubles the registry and
leaves both forever), and **versioning the GraphQL schema as a whole** (two supergraphs, and
every client on the wrong one).
