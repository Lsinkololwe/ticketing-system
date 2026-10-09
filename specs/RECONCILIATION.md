# Reconciliation · the corpus against the working tree

> **SNAPSHOT, measured 2026-08-31. Not the current state.** This document records presence of §4 names
> on that date. Its "zero implemented, zero verified" and "all 41 specs" statements are stale. As of
> 2026-10-03 the corpus is **42 specs: 8 `implemented`** (ET-PLT-001, 002, 003, 004, 005, 006, 012, 015),
> **34 `approved`, 0 `verified`**. No fresh conformance figures have been measured; the numbers below are
> not to be quoted as current.
>
> Live sources: the [open items index in FINDINGS.md](FINDINGS.md#open-items-index) for known defects, and
> each spec's `spec.yaml` status plus the gates in its `specs/tasks/ET-*.md` file for per-spec progress.

**Measured 2026-08-31.** The pass [`README.md` §Reconciliation](README.md#reconciliation) requires
and [`IMPLEMENTATION_PLAN.md` §3](IMPLEMENTATION_PLAN.md) records as *"currently being skipped."*

Per-spec evidence: [`specs/reconciliation/ET-*.md`](reconciliation/) — one file per spec, every
§4 name classified individually.

---

## What this pass does and does not establish

It measures **presence**: for every name a spec's §4 declares — collection, GraphQL operation,
error code, event wire name — does the tree contain it, contain it under a different name, or
not contain it. That is mechanical, complete across all 41 specs, and reproducible.

It does **not** establish conformance. Presence answers *"is there a `requestPayout`"*, not
*"does it refuse below the minimum"*. The §3 requirement read is the second half of R0 and
remains open on all 41 specs. Every per-spec file says so in its own words.

The distinction is not pedantry. This repository has already produced three artifacts that were
present, read correctly, were cited as coverage, and did nothing:
`MongoValidationExceptionHandler` (unreachable behind a catch-all), `TenantAccessGuard` (called
by nothing), and `libs/shared/src/__tests__/compliance/index.ts` (re-exported three files that
did not exist). A presence census that called any of those "done" would have been wrong in the
most expensive direction.

---

## The headline

**The data model was built to the specs. The graph surface was not.**

| §4 artifact | Conforms | Contradicted | Absent | Base |
|---|---|---|---|---|
| Collections (unique) | **42** `@Document`-bound | 13 named but unbound · 12 **reserved, used nowhere** | **0** | 67 |
| GraphQL operations | **87** (30%) | **61** (21%) | **143** (49%) | 291 |
| Error codes | **93** (100%) | 0 | **1** | 94 |
| Event wire names | **75** (76%) | — | 24 | 99 |

Every collection the corpus names already exists in the tree in some form — **zero absent**.
Half of every operation it names does not exist at all, and a fifth exists under a name §4 does
not sanction.

That asymmetry has a cause. `ET-PLT-002` (persistence baseline, 7/10 gate) ran a registry pass
across all three services and aligned collection naming *including for capabilities nobody has
built* — which is why twelve collections are declared as constants and referenced by nothing.
No equivalent pass has run over the schema.

### Confidence is the weaker axis

| | Specs |
|---|---|
| Has ≥1 test tagged with its own ID | **6** — PLT-001, 002, 003, 004, 005, 006 |
| Has none | **35** |
| `implemented` or `verified` | **0** |

`mvn -f backend test` is **green**: 325 tests, 0 failures, across 4 modules. The L3 layer is real
— `ContentionTest` runs 39s against a MongoDB replica set, `TransactionRealityTest` 9.6s. But
every one of those tests belongs to Wave 0. **No test in the tree asserts anything about
identity, organizations, catalogue, ticketing, payment, finance, notification or admin.**

---

## Corrections to the corpus's own record

### 1 · ET-PLT-006 is built and its task file says it is not

The file opens with `find backend -path '*/src/test/*' -name '*.java' → 0 files` and carries
0/12 gate boxes. The tree contains, in `shared-library/src/test/java/com/pml/shared/testing/`:

`Harness`, `TestClock`, `Concurrency`, `Persistence`, `Inventory`, `Ledger`, `Providers`,
`MongoReplicaSet`, `MongoStandalone`, `RedisNode`, `IndexRegistryAssertions`,
`blockhound/PlatformBlockHoundIntegration`, `jwt/StubIssuer`, and six `it/` suites including
`TransactionRealityTest` and `AssertionsProveThemselvesTest` — the "watch the assertion fail"
suite BE-3 asks for. Five classes carry `@Tag("ET-PLT-006")` and all pass.

Only `Refusals` is missing under that exact name; `error/RefusalCoverage.java` and the three
`*RefusalCoverageTest` classes appear to be it.

This is the *"finishing and not saying so"* failure [`README` §Status lifecycle](README.md#status-lifecycle)
names, and it is load-bearing: **`IMPLEMENTATION_PLAN.md` P2 cites ET-PLT-006 as the open
blocker on every slice in the corpus.** P2 should be closed or re-scoped to what actually
remains.

### 2 · ET-PLT-005 is complete on its central deliverable and shows 0/11

`ErrorCode.java` holds **93 rows**. Set-compared against every code declared across all 41
`spec.yaml` files: **zero extra, one missing** — `UNSUPPORTED_SCHEMA_VERSION`, declared by
ET-PLT-010. The registry is otherwise exactly closed, in both directions, which is what its §4
asks for. Twelve test classes carry the tag.

### 3 · ET-PLT-012's work appears done and shows 0/6

`backend/pom.xml` declares all six modules and the managed properties BE-1 names. It has no
tagged tests and no R0 — classify it rather than assume either way.

### 4 · The pre-corpus gap documents must not be carried forward

`docs/BACKEND_GAP_ANALYSIS_REPORT.md` (2026-03-26) and `docs/STUB_TYPES_ANALYSIS.md` predate the
corpus (2026-07-30). Per README they are *inputs* to this pass. Their headline "33% complete"
measures a different thing against a different target and should not be quoted.

---

## The systemic contradiction: paginated twins

61 operations are `contradicted`, and they are not 61 independent decisions. **137 of the 542
root fields across the three subgraphs (25%) are `*OffsetPagination` / `*CursorPagination`
twins** of each other:

| Subgraph | Query | Mutation | Paginated twins |
|---|---|---|---|
| catalog | 110 | 45 | **76** (49% of all fields) |
| booking | 143 | 80 | 47 |
| identity | 69 | 95 | 14 |

Where ET-CAT-003 §4 names `provinces`, the schema carries `provincesOffsetPagination`,
`provincesCursorPagination`, `provincesByCountryOffsetPagination` and
`provincesByCountryCursorPagination`. Where ET-FIN-003 names `requestPayout` / `approvePayout`,
the schema carries `createPayoutRequest` / `approvePayoutRequest`.

This is one decision — pagination strategy in the field name, and a `<verb><Noun>Request` naming
convention — taken before the corpus and repeated everywhere. It is worth one deliberate ruling
rather than 61 per-spec ones:

- **The specs win** ([README §Precedence](README.md#precedence)) and the schema is renamed. Large,
  breaks every client document, and `ET-PLT-010` (schema deprecation windows) exists for it.
- **Or §4 is amended** to the shipped convention, which means editing 41 specs and accepting that
  a query's pagination strategy is part of its name.

Either is defensible. Leaving it undecided is not: today every one of those 61 is a slice that
will either rebuild something that works or leave in place something its spec contradicts —
exactly what [`IMPLEMENTATION_PLAN.md` §3](IMPLEMENTATION_PLAN.md) says R0 exists to prevent.

---

## Per-spec classification

`colls` = `@Document`-bound / named-but-unbound / **reserved (used nowhere)**.
`ops` = conform · contradicted · absent.
Confidence is `presence only` unless tests tagged with the spec's own ID exist.

| Spec | W | Status | colls | ops (✓·✗·—) | errors | events | gate | tests |
|---|---|---|---|---|---|---|---|---|
| ET-PLT-012 | 0 | in-progress | — | — | — | — | 0/6 | 0 |
| ET-PLT-001 | 0 | in-progress | — | — | — | — | 5/8 | 15 |
| ET-PLT-002 | 0 | in-progress | 42/13/11 | — | 1/1 | — | 7/10 | 16 |
| ET-PLT-003 | 0 | in-progress | 0/3/0 | — | 0/1 | 19/19 | 4/7 | 11 |
| ET-PLT-004 | 0 | in-progress | — | — | 1/1 | — | 2/8 | 4 |
| ET-PLT-005 | 0 | in-progress | — | — | 84/84 | — | 0/11 | 12 |
| ET-PLT-006 | 0 | in-progress | — | — | — | — | 0/12 | 5 |
| ET-PLT-007 | 1 | approved | — | — | 3/3 | — | 0/11 | 0 |
| ET-PLT-013 | 1 | approved | 2/0/**1** | 1·1·7 | 3/3 | 0/2 | 1/19 | 0 |
| ET-IDN-001 | 1 | approved | — | — | 5/5 | — | 0/13 | 0 |
| ET-IDN-002 | 1 | approved | 1/0/0 | 3·3·2 | 2/2 | — | 0/13 | 0 |
| ET-IDN-003 | 1 | approved | 1/0/0 | 0·0·6 | 2/2 | 1/2 | 0/13 | 0 |
| ET-ORG-001 | 1 | approved | 2/0/0 | 9·3·6 | 7/7 | 2/4 | 0/15 | 0 |
| ET-ORG-002 | 1 | approved | 3/0/0 | 17·2·1 | 8/8 | 2/5 | 0/15 | 0 |
| ET-ORG-003 | 1 | approved | 3/0/0 | 6·2·0 | 2/2 | 2/2 | 0/13 | 0 |
| ET-CAT-001 | 2 | approved | 1/0/0 | 10·5·2 | 3/3 | 9/10 | 0/13 | 0 |
| ET-CAT-002 | 2 | approved | 2/1/0 | 3·2·8 | 6/6 | 2/3 | 0/14 | 0 |
| ET-CAT-003 | 2 | approved | 5/0/0 | 3·7·4 | 1/1 | — | 0/13 | 0 |
| ET-PLT-014 | 2 | approved | 1/0/0 | 2·4·1 | 4/4 | 0/2 | 1/17 | 0 |
| ET-TKT-001 | 3 | approved | 1/0/0 | 4·0·0 | 4/4 | 3/4 | 0/16 | 0 |
| ET-PAY-001 | 3 | approved | 2/0/0 | 0·3·2 | 6/6 | 4/5 | 0/14 | 0 |
| ET-PAY-002 | 3 | approved | 0/1/0 | — | 2/2 | 0/2 | 0/15 | 0 |
| ET-TKT-002 | 3 | approved | 1/0/0 | 1·2·5 | 3/3 | 1/3 | 0/14 | 0 |
| ET-FIN-001 | 3 | approved | 5/1/0 | 4·4·4 | 5/5 | 4/4 | 0/15 | 0 |
| ET-FIN-002 | 4 | approved | 1/0/0 | 0·0·5 | 2/2 | 3/3 | 0/13 | 0 |
| ET-FIN-003 | 4 | approved | 2/0/0 | 6·9·2 | 5/5 | 1/3 | 0/15 | 0 |
| ET-FIN-004 | 4 | approved | 2/0/0 | 2·6·3 | 4/4 | 5/5 | 0/14 | 0 |
| ET-FIN-005 | 4 | approved | 1/0/**1** | 2·3·6 | 1/1 | 1/1 | 0/14 | 0 |
| ET-TKT-003 | 5 | approved | 2/0/0 | 6·0·1 | 2/2 | 1/2 | 0/14 | 0 |
| ET-TKT-004 | 5 | approved | 0/0/**1** | 0·0·10 | 2/2 | 2/3 | 0/14 | 0 |
| ET-NTF-001 | 5 | approved | 3/0/**1** | 7·1·4 | 2/2 | 0/1 | 0/13 | 0 |
| ET-NTF-002 | 5 | approved | 1/0/**1** | 0·0·6 | — | 12/12 | 0/13 | 0 |
| ET-ADM-001 | 6 | approved | 2/0/**1** | 0·0·9 | — | 1/2 | 0/14 | 0 |
| ET-ADM-002 | 6 | approved | 0/1/**1** | 0·0·9 | 2/2 | — | 0/13 | 0 |
| ET-ADM-003 | 6 | approved | 0/0/**1** | 1·2·12 | 1/1 | — | 0/13 | 0 |
| ET-ADM-004 | 6 | approved | 0/3/0 | — | — | — | 0/15 | 0 |
| ET-ADM-005 | 6 | approved | — | 0·0·5 | — | — | 0/15 | 0 |
| ET-PLT-008 | 7 | approved | 0/0/**3** | 0·2·9 | — | — | 0/14 | 0 |
| ET-PLT-009 | 7 | approved | 1/0/0 | 0·0·5 | — | — | 0/12 | 0 |
| ET-PLT-010 | 7 | approved | 0/3/0 | — | — | — | 0/13 | 0 |
| ET-PLT-011 | 7 | approved | 0/0/**1** | 0·0·9 | 1/1 | — | 0/14 | 0 |

Three specs — **ET-PLT-007, ET-IDN-001, ET-ADM-004** — declare almost nothing mechanically
checkable (a handful of error codes, or three rollup collections referenced only by an index
initialiser). Their rows are *not* evidence of completeness; they must be judged by reading §3.

### The twelve reserved collections — the sharpest "remaining" signal

Declared as a constant, referenced by nothing. Each names a capability nobody has started:

| Collection | Spec |
|---|---|
| `booking_ticket_transfers` | ET-TKT-004 transfer and resale |
| `booking_recovery_proposals` | ET-ADM-003 transaction recovery |
| `booking_reconciliation_items` | ET-FIN-005 reconciliation |
| `identity_erasure_requests`, `identity_data_exports`, `identity_consent_records` | ET-PLT-008 data protection |
| `identity_feature_flags` | ET-ADM-002 platform configuration |
| `identity_notification_templates`, `identity_mass_sends` | ET-NTF-001 notification transport |
| `identity_review_claims` | ET-ADM-001 approvals workbench |
| `identity_role_permission_changes` | ET-PLT-013 permission engine |
| `identity_temporary_blocks` | ET-PLT-011 rate limiting |

---

## Frontend

| App | Routes | Playwright specs |
|---|---|---|
| `apps/admin` | **34** | **0** |
| `apps/organization-admin` | 26 | 2 |
| `apps/ticketing` | 6 | 2 |

`apps/admin` is the largest surface in the platform and has no end-to-end test of any kind.

**Unit tests are real and green**: `npm run test:shared` → 7 files, 80 tests, 0 failures,
including the design-system compliance assertions and `documentValidity.test.ts`.

### Three Definition-of-Done gates do not execute

[`IMPLEMENTATION_PLAN.md` §6](IMPLEMENTATION_PLAN.md) requires `npm run lint:all` and the
compliance suite on every slice. Measured today:

| Command | Exit | Failure |
|---|---|---|
| `npm run test:shared` | **0** | — 80 tests pass |
| `npm run test:compliance` | **1** | `NX Cannot find configuration for task @pml.tickets/shared:test` |
| `npm run compliance:all` | **1** | `externalDependency 'eslint' for '@pml.tickets/organization-admin:lint' could not be found` |
| `npm run lint:all` | **1** | same — though `eslint@^9.8.0` **is** installed in `node_modules` |

The compliance *assertions* are fine — they run under `test:shared`, which is where
`libs/shared/src/__tests__/compliance/design-system.test.ts` lives. What is broken is the two
scripts the DoD names, and an nx project-graph fault that makes lint unrunnable workspace-wide.
A gate that exits 1 for a configuration reason is a gate everyone learns to pass with `--force`.

### Carried forward

- **`KNOWN_BROKEN`: 7 frozen documents** that select fields the supergraph lacks —
  `verifyOrganizationDocuments`, `bulkApproveDocuments`, `bulkRejectDocuments`,
  `organizationStatistics`, `myPermissions`, `BusinessAddress.street`, and the org-admin
  check-in `TicketOffsetPage` shape. (F-003 recorded eleven; the list now holds seven.)
- **Raw `px`: 684**, ratcheted per app — admin 119, org-admin 520, ticketing 45. Raw hex is **0**
  everywhere and frozen, so that one is a guarantee rather than a debt.
- 26 files carry `gql` documents against the three subgraphs.

---

## Findings

### F-003 is measurably smaller than recorded, and now exactly bounded

Re-measured by binding every `@DgsQuery`/`@DgsMutation` to its schema field. **Zero orphan
resolvers** in all three subgraphs, which is what makes the reverse count trustworthy:

| Subgraph | Query fields | Mutation fields | Declared with **no resolver** |
|---|---|---|---|
| catalog | 110 | 45 | **0** |
| booking | 143 | 80 | **5** |
| identity | 69 | 95 | **21** |

- **booking (5)** — `accountBalance`, `trialBalance`, `createPlatformAccount`,
  `creditPlatformAccount`, `debitPlatformAccount`. `uploadScans`, named in F-003, now resolves.
- **identity (21)** — `allPermissions`, `hasEventPermission`, `hasOrganizationPermission`,
  `myEffectivePermissions`, `myEventRole`, `myOrganizationRole`, `organizerStatistics`,
  `platformStatistics`, `teamStatistics`, `usersCountByRole`, `cancelAccountDeletion`,
  `disableTwoFactor`, `linkSocialAccount`, `requestAccountDeletion`, `setupTwoFactor`,
  `socialAuth`, `suspendUser`, `unlinkSocialAccount`, `unsuspendUser`, `updateMyProfile`,
  `verifyTwoFactor`.

Ten of identity's are the permission-resolution surface ET-ORG-003 and ET-PLT-013 own — which
means the platform advertises a permission API that returns nothing, while [`FINDINGS.md`
F-001](FINDINGS.md) reports there is no tenant boundary behind it either.

### F-001 remains open and is still the most urgent item in the repository

Not re-measured by this pass; nothing in the tree suggests it changed. Any account holding the
`ORGANIZER` realm role can reprice or delete another organization's ticket tiers by id. It is
ordered by blast radius, not by wave, and it precedes every capability slice below.

### New · ET-PLT-003's only ordering proof is environment-gated and did not run

`catalog.event.SessionOrderingTest` reports **0 tests in 6.083 s**. It is not a facade — it
skips deliberately, via `Assumptions`, when the Azure Service Bus emulator in the sibling
`docker-resources` repository is unreachable, and its javadoc explains why a socket probe would
be worse. But the effect is that ET-PLT-003-R7 — *ordered per entity, and not beyond it* — is
asserted by nothing in a normal run, while the suite reports green. ET-PLT-003's gate row for
consumer idempotency is already held open; this belongs beside it.

### New · one error code missing from an otherwise exactly-closed registry

`UNSUPPORTED_SCHEMA_VERSION` (ET-PLT-010) is the single corpus-declared code absent from
`ErrorCode`. Either ET-PLT-005 §4 gains the row or ET-PLT-010 stops declaring it — the registry
is closed, so it cannot stay as it is.

### New · one collection is declared by a spec and absent from the registry that owns collections

ET-PLT-002 §4 is meant to be the single authority on collections. It carries **66** rows; the
corpus declares **67** distinct collections. The extra one is **`identity_role_permission_changes`**,
declared by ET-PLT-013 §4 alone. Nothing else diverges — every registry row is claimed by some
capability spec, and no other spec invents a collection.

This is [`FINDINGS.md` F-002](FINDINGS.md) one level up. F-002 records that annotations create
indexes outside the registry; this records that a *spec* can name a collection outside it. One
row is trivial to fix; the absence of any check that would have caught it is not. `ET-PLT-002`'s
own registry test compares the registry against the code, never against the rest of the corpus.

---

## What to do next, in order

1. **Rule on the paginated-twin naming contradiction.** 61 operations and 137 schema fields turn
   on it, and no slice in Waves 2–6 can start honestly until it is settled.
2. **Close F-001.** Decide where tenant identity comes from, add `findByIdAndOrganizationId`
   finders, convert the tier and accessibility write paths first, then lint the count downward.
3. **Correct the corpus's own record** — ET-PLT-006's gate and P2, ET-PLT-005's gate, and
   ET-PLT-012's classification. (`IMPLEMENTATION_PLAN.md` P6 is already closed: `ROADMAP.md:87`
   reads *"nine states"*, matching ET-ORG-001 §4. P6 still lists itself as open.)
4. **Repair the three frontend gates** — `test:compliance`, `compliance:all`, `lint:all`.
   They are named in the Definition of Done and all three exit 1.
5. **Finish the §3 requirement read**, spec by spec, into `specs/reconciliation/ET-*.md`. Start
   with the six specs whose operations are ≥60% conformant — ET-ORG-002, ET-ORG-003, ET-TKT-001,
   ET-TKT-003, ET-CAT-001, ET-NTF-001 — because those are verification-and-test work rather than
   build work, and they are far cheaper than their wave position implies.
6. **Then** re-sequence the build order against real deltas, and only then open Wave 1.

---

## How to reproduce this

Every number above comes from scripts, not from reading. They are not committed; re-derive with:

```bash
mvn -f backend test                                    # 325 tests, 4 modules, green
grep -c '^    [A-Z][A-Z0-9_]*(' backend/shared-library/src/main/java/com/pml/shared/error/ErrorCode.java
grep -rho '@Tag("ET-[A-Z]*-[0-9]*")' backend --include='*.java' | sort | uniq -c
cd frontend/web && npm run test:shared && npm run lint:all    # the second exits 1
```

The `spec.yaml` header is the machine-readable contract this census reads: `persistence.collections`,
`graphql.queries`, `graphql.mutations`, `errors` and `events` are present on all 41 specs, which is
what makes a mechanical §4 census possible at all. Any re-run should start there.

**Not yet verified, and needed before any `already-satisfied` is trusted:** §4 index definitions
against a running database via the MongoDB MCP — [`FINDINGS.md` F-002](FINDINGS.md) shows a
`unique + sparse` index passes every test and oversells in production.
