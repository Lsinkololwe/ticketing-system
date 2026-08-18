# Implementation plan · 41 specs, backend + frontend + tests

The execution layer over [`specs/`](README.md). Every task here is a **vertical slice of one
spec**: the backend that satisfies its EARS requirements, the subgraph half that exposes them,
the screen that consumes them, and the tests that prove all three.

**The spec is the authority, not this plan.** A task tells you *what to build and in what
order*; `spec.md` §3 tells you *what correct means*. Where they disagree, the spec wins and
this plan is the defect. No task may introduce a collection, index, event name, GraphQL
operation, Redis key or error code that is not already named in that spec's §4 — if you find
yourself inventing a name, stop: either you are reading the wrong spec, or §4 has a hole that
must be filled in the spec first.

---

## 1 · How a task is addressed

```
ET-TKT-001 / BE-4          backend task 4 of the reservation spec
ET-TKT-001 / GQL-1         its subgraph + supergraph + codegen step
ET-TKT-001 / FE-2          frontend task 2
ET-TKT-001 / TS-3          test task 3
```

Every task file lives at `specs/tasks/<ET-ID>.md` and carries, per task: the spec requirements
it satisfies, the files it touches, its acceptance condition, `depends`, and `parallel-safe`.
`BE-*` tasks correspond to the spec's own §5 list and never contradict it.

## 2 · The slice, and why the order inside it is fixed

```
BE ──▶ GQL ──▶ FE ──▶ TS
```

- **BE** — domain, persistence, service, events. Nothing in the slice can be proved before the
  behaviour exists.
- **GQL** — the subgraph SDL, then `compose-supergraph.sh --static`, then
  `cd frontend/web && npm run codegen`. **The frontend may not begin until codegen is clean.**
  A hand-written TypeScript type for a GraphQL shape is a defect
  ([frontend-quality §3](../.claude/skills/frontend-quality/SKILL.md)); the only legitimate way
  to obtain a type is to generate it, and the only way to generate it is for the schema to
  exist first.
- **FE** — the screen, bound to generated types, built from the `.dc.html` layout contract.
- **TS** — the five backend layers plus Playwright. Tests are the last task in the slice but
  the **first** thing written inside each task: no `BE-*` task is complete without the test
  that its acceptance box names.

Reversing BE and GQL produces a schema nothing implements. Reversing GQL and FE produces
hand-written types. Both are the failure modes this order exists to prevent.

## 3 · Wave −1 · What must be true before task one

All 41 specs are `approved`. Of the preconditions below, **P1, P4 and P7 are cleared**; **P2 and P3
remain open and still block every slice.**

| # | Precondition | Why it blocks everything |
|---|---|---|
| ~~**P1**~~ ✅ | ~~Fix the 47 `verify:` predicates that omit `-DfailIfNoTests=true`~~ — **done**: 47 commands across 37 files; proven by a bogus tag exiting 0 without the flag and 1 with it | 13 specs, including most of Wave 0, run `mvn -Dgroups=<ID>` with no such guard. A tag matching nothing exits 0, so the spec verifies green having executed nothing — the exact failure [README §Traceability](README.md#traceability) names as *"worse than not running them, because it looks like proof."* Combined with P2 this reports the whole corpus green today. |
| **P2** ⛔ | Build the backend test harness — **`ET-PLT-006`** | `find backend -path '*/src/test/*' -name '*.java'` returns **zero**. There is no `Persistence.assertNothingPersisted`, no `Inventory.assertConserved`, no `Ledger.assertBalanced`, no frozen `Clock` — yet acceptance boxes across the corpus cite them by name. Until this lands, *no spec can reach `verified`.* |
| **P3** ⛔ | Build the frontend test foundation — **Track F0** below | `apps/*/e2e` holds `auth.setup.ts` and a Microcks harness and **no `.spec.ts` at all**; `apps/ticketing` has no Playwright config. Zero frontend tests exist. |

The smaller repairs, now closed:

- ~~**P4**~~ ✅ — the 10 broken cross-area links are repaired; 0 broken relative links remain across 780.
- **P5** — `ROADMAP.md`'s dependency-edge count was stale (167 against an actual 180, now corrected).
  The graph is a clean DAG with no cycles and no backward-wave edges, so this was a stale count,
  not a structural fault.
- ~~**P7**~~ ✅ — [`ET-PLT-013`](tasks/ET-PLT-013.md) and [`ET-PLT-014`](tasks/ET-PLT-014.md) are
  `approved`. ET-PLT-013's approval carries the **amendment to ET-ORG-003 R6** with it: its
  30-constant enum is superseded by the flat `module:action` catalogue, and ET-ORG-003's decision
  table must be re-run unchanged as the regression guard.
- **P6** — `ROADMAP.md` line 84 describes `ET-ORG-001` as *"six states"*. The spec's §4 defines
  **nine** states, ten actions, 100 pairs, 15 legal. The spec wins ([README §Precedence](README.md#precedence));
  correct the roadmap row. Left alone, an implementer who reads the index and not the spec builds
  the wrong state machine — and it is the state machine three other specs gate on.

### Reconciliation is a real gate, and it is currently being skipped

[README §Reconciliation](README.md#reconciliation) makes reconciliation the pass that runs
*before* implementation planning: classify every requirement as `already-satisfied`,
`partially-satisfied`, `contradicted` or `absent`, then plan against real deltas.

The working tree already contains ~30 untracked `booking-service` classes and **86 files
carrying `ET-*` spec references** — 55 for `ET-TKT-001` alone. Code is being written against
specs whose requirements were never classified. **Each task file therefore opens with an
`R0 · Reconcile` step**: before writing anything, audit what already exists against that
spec's §3 and record the four-way classification. `R0` is not optional and not a formality —
it is what stops a slice from rebuilding something that works, or from leaving in place
something the spec contradicts.

## 4 · Track F0 · Frontend foundation (blocks every `FE-*` and Playwright `TS-*`)

Independent of the backend waves — start it in parallel with Wave 0.

| Task | What | Acceptance |
|---|---|---|
| **F0-1** | Playwright project for `apps/ticketing` — config, `data-brand="ticketing"` fixture, iris accent | `nx e2e ticketing` runs and reports 0 tests, not a config error |
| **F0-2** | **org-admin auth harness.** Per [frontend-quality §4](../.claude/skills/frontend-quality/SKILL.md), org-admin has none, and `page.route` cannot intercept a Server Component's fetch — mocking there silently does nothing | An org-admin spec authenticates as an `ORGANIZER` against the real realm and reaches `(dashboard)/dashboard` |
| **F0-3** | Extend the Microcks container setup (`e2e/global-setup.ts`, fixed host port, `MICROCKS_PORT`) to admin and ticketing | Three apps share one container-backed mock path |
| **F0-4** | **Apollo-driven surfaces need a real schema.** Microcks returns 500 on any GraphQL query containing fragments, so Apollo Client traffic cannot be mocked through it. Stand up a Testcontainers-backed subgraph fixture for those surfaces | An Apollo-driven screen's e2e passes against a container speaking the real schema, not a stub |
| **F0-5** | Design-system compliance suite — token-only assertions (no hex, no `px`, three fonts, closed prop sets, correct `data-brand`) extended to all three apps | `npm run e2e:compliance` green across admin, org-admin, ticketing |
| **F0-6** | Shared error-UI primitives: `extensions.errorCode` drives the message, `retryable` drives whether a retry is offered, `TOKEN_REVOKED` signs the user out rather than retrying | Every code in [`ET-PLT-005`](_platform/005-error-contract/) §4 maps to a rendered state |
| **F0-7** | Currency + status formatters — `K 125,430` (Kwacha symbol, space, tabular Fira Code), humanised statuses (`PENDING_REVIEW` → "Pending Review"). Never `ZMW`, never `$`, never a raw enum | Unit-tested; compliance suite asserts no raw enum reaches the DOM |

## 5 · Test layers, and what each is allowed to prove

| Layer | Scope | Technology | Rule |
|---|---|---|---|
| **L1** | Domain unit — state machines, quotes, rounding, permission order | JUnit 5, frozen `Clock` | No Spring context. Pure functions only |
| **L2** | Slice — repository, resolver, listener | `@DataMongoTest` / DGS slice | — |
| **L3** | **Integration — the load-bearing layer** | **Testcontainers**: MongoDB *replica set*, Redis, Keycloak, WireMock for PawaPay | A standalone `mongod` makes `@Transactional` **silently inert** (D-01), so every money or inventory test runs against a replica set or it proves nothing |
| **L4** | Contract | `compose-supergraph.sh --static`, event-envelope round-trip | Composition must fail the build, not warn |
| **L5** | End-to-end | Playwright by `data-testid` | Loading, empty, error, populated — an empty state is a designed screen, not a fallback |

Every test class carries `@Tag("<ET-ID>")`; every method's `@DisplayName` names the requirement
it proves (`ET-FIN-002-R3`). That is what makes coverage a `grep` rather than a judgement call.

**MongoDB MCP is the audit instrument, not a test.** Use it to check a running database against
the [`ET-PLT-002`](_platform/002-persistence-baseline/) §4 registry — `list-collections`,
`collection-indexes`, `collection-schema` — and to confirm an index the spec names actually
exists with the filter and uniqueness it specifies. A partial unique index that a test never
contends against will pass every test and oversell in production. Verifying it through MCP is
cheap and catches exactly that. It does **not** substitute for an L3 container test.

## 6 · Definition of done — every slice

- [ ] `R0` recorded: every §3 requirement classified against existing code
- [ ] Every §3 acceptance box ticked, each backed by a test tagged with the spec ID
- [ ] No name outside §4 introduced — no invented collection, index, event, operation or code
- [ ] `mvn -q -f backend verify -Dgroups=<ET-ID> -DfailIfNoTests=true` green
- [ ] `compose-supergraph.sh --static` green; `npm run codegen` clean and committed
- [ ] Every operation the UI calls appears in `docs/FRONTEND_GRAPHQL_CONTRACT.md` with a role the caller holds
- [ ] The `.dc.html` screen was read and the implementation matches its structure
- [ ] Tokens only — no hex, no `px`, no font outside Inter / Space Grotesk / Fira Code; closed prop sets; barrel imports; correct `data-brand`
- [ ] Playwright covers loading, empty, error, populated by `data-testid`
- [ ] L3 runs against containers, never a hand-rolled `fetch` stub
- [ ] `npm run lint:all` and `npm run e2e` pass
- [ ] Spec `status:` advanced to `implemented`, then `verified` only when both halves are true

---

## 7 · The index — 39 specs, in dependency order

`FE` names the app(s) that gain a surface. **"—" means the design authority's own
[Coverage map](https://claude.ai/design/p/03cea541-469f-44d2-aa91-a5c6f5456295) says this
capability has no screen** — those slices are backend + contract + tests only, and inventing a
screen for them is a defect, not diligence.

### Wave 0 · Platform foundation — no UI, and nothing after it is safe to start

| Spec | Capability | FE | Blocked by |
|---|---|---|---|
| [ET-PLT-012](tasks/ET-PLT-012.md) | Build topology — parent POM, BOM set, reactor, enforcer | — | *(none — first task in the corpus)* |
| [ET-PLT-001](tasks/ET-PLT-001.md) | Runtime baseline — reactive contract, `Clock`, module boundaries | — | PLT-012 |
| [ET-PLT-002](tasks/ET-PLT-002.md) | Persistence — replica set, collection + index registry, money/time types | — | PLT-001 |
| [ET-PLT-003](tasks/ET-PLT-003.md) | Event contract — two tiers, envelope, outbox, idempotent consumers, DLQ | — | PLT-001, 002 |
| [ET-PLT-004](tasks/ET-PLT-004.md) | Federation — ownership, keys, `@tag` contracts, composition gate | *codegen* | PLT-001 |
| [ET-PLT-005](tasks/ET-PLT-005.md) | Error contract — closed code registry, typed handlers, retryability | **F0-6** | PLT-001, 004 |
| [ET-PLT-006](tasks/ET-PLT-006.md) | **Five-layer test harness** — Testcontainers, WireMock, frozen `Clock` | — | PLT-001…005 |

### Wave 1 · Identity and access

| Spec | Capability | FE | Blocked by |
|---|---|---|---|
| [ET-PLT-007](tasks/ET-PLT-007.md) | Realm, roles, `@auth`, internal scopes, idempotency, tenant scoping | all three | PLT-001, 004, 005 |
| [ET-PLT-013](tasks/ET-PLT-013.md) | **Permission engine** — flat `module:action` catalogue, role→permission mapping, evaluation | `Admin - Platform Configuration` | PLT-002, 005, 007 |
| [ET-IDN-001](tasks/ET-IDN-001.md) | Phone-OTP passwordless identity — Keycloak SPI, OTP lifecycle | `Login - Phone OTP & Admin MFA` | PLT-005, 007 |
| [ET-IDN-002](tasks/ET-IDN-002.md) | Keycloak ↔ MongoDB sync, drift detection, recovery | — | PLT-002, 005, 007, IDN-001 |
| [ET-IDN-003](tasks/ET-IDN-003.md) | Token revocation — `jti`/`sid`/`sub`, fail-closed, propagation | all three *(sign-out path)* | PLT-002, 003, 005, 007, IDN-001 |
| [ET-ORG-001](tasks/ET-ORG-001.md) | Organizer onboarding — nine states, staged access, KYB docs | `Org Admin - Onboarding Wizard` + `Admin - Approvals Workbench` | PLT-002, 003, 005, 007, IDN-002 |
| [ET-ORG-002](tasks/ET-ORG-002.md) | Members, invitations, ownership transfer | `Org Admin - Team & Permissions` | PLT-002, 003, 005, 007, ORG-001 |
| [ET-ORG-003](tasks/ET-ORG-003.md) | Permission **resolution** — precedence, event access grants *(the catalogue is PLT-013's)* | `Org Admin - Team & Permissions` *(grants only — the resolver has no graph surface)* | PLT-003, 005, 007, **013**, ORG-002 |

### Wave 2 · The catalogue

| Spec | Capability | FE | Blocked by |
|---|---|---|---|
| [ET-CAT-001](tasks/ET-CAT-001.md) | Event lifecycle — eight states, approval, publish, reschedule, cancel | `Org Admin - Event Editor` + `Create Event Wizard`, `Admin - Approvals Workbench`, `Ticketing - Discover` | PLT-002, 003, 005, 007, ORG-001, 003 |
| [ET-CAT-002](tasks/ET-CAT-002.md) | Tiers, capacity, sales windows, purchase limits, promo codes | `Org Admin - Event Editor` | PLT-002, 003, 005, CAT-001 |
| [ET-PLT-014](tasks/ET-PLT-014.md) | **Reference data engine** — enum-derived rows, workflow semantics, admin-owned lookups | `Admin - Transactions & System` | PLT-002, 004, 005 |
| [ET-CAT-003](tasks/ET-CAT-003.md) | Provinces, cities, venues, categories, discovery and search | `Ticketing - Discover`, `Admin - Transactions & System` (reference data), `Org Admin - Event Editor` (venue) | PLT-002, 004, 005, CAT-001 |

### Wave 3 · The purchase loop

| Spec | Capability | FE | Blocked by |
|---|---|---|---|
| [ET-TKT-001](tasks/ET-TKT-001.md) | Reservation, atomic hold, TTL expiry, purchase saga | `Ticketing - Discover & Checkout` | PLT-002, 003, 005, 006, 007, CAT-002 |
| [ET-PAY-001](tasks/ET-PAY-001.md) | Payment intents, provider port, PawaPay adapter, idempotency | *checkout payment step only* | PLT-002, 003, 005, 007, TKT-001 |
| [ET-PAY-002](tasks/ET-PAY-002.md) | Webhook signature, replay defence, confirmation path | — *(operator surface is ADM-003's drawer)* | PLT-002, 005, 007, TKT-001, PAY-001 |
| [ET-TKT-002](tasks/ET-TKT-002.md) | Ticket issuance, QR signing, delivery, re-issue | `Ticketing - My Tickets` | PLT-002, 005, 007, TKT-001, ORG-003 |
| [ET-FIN-001](tasks/ET-FIN-001.md) | Per-event escrow, chart of accounts, double-entry journal | `Admin - Ledger, Commission & Reconciliation` | PLT-002, 003, 005, 006, TKT-001, CAT-001 |

### Wave 4 · Money out

| Spec | Capability | FE | Blocked by |
|---|---|---|---|
| [ET-FIN-002](tasks/ET-FIN-002.md) | Two-stage commission, rate resolution, recognition | `Admin - Ledger…` + `Org Admin - Event Editor` (preview) | PLT-005, 006, FIN-001, CAT-001, 002 |
| [ET-FIN-003](tasks/ET-FIN-003.md) | Payout eligibility, request lifecycle, bank accounts, settlement saga | `Admin - Finance` + `Org Admin - Events & Finance` | PLT-005, 007, FIN-001, 002, PAY-001, ORG-003 |
| [ET-FIN-004](tasks/ET-FIN-004.md) | Refund policy and fees, cancellation refunds, chargebacks | `Admin - Ledger…` + `Ticketing - My Tickets` (quote) | PLT-005, 007, FIN-001, 002, 003, CAT-001, TKT-002 |
| [ET-FIN-005](tasks/ET-FIN-005.md) | Provider reconciliation, ledger-to-balance proof, financial close | `Admin - Ledger, Commission & Reconciliation` | PLT-005, FIN-001…004, PAY-002 |

### Wave 5 · At the venue, and after

| Spec | Capability | FE | Blocked by |
|---|---|---|---|
| [ET-TKT-003](tasks/ET-TKT-003.md) | QR validation, offline scanning, duplicate-scan defence, reporting | **Decision required** — see task file. Scanner is a distinct offline-first surface, *not* one of the three web apps; only check-in **reporting** belongs in `Org Admin` | PLT-005, TKT-002, ORG-003, CAT-001 |
| [ET-TKT-004](tasks/ET-TKT-004.md) | Transfer between users, controlled resale | `Ticketing - My Tickets & Transfer` | PLT-005, 007, TKT-002, 003, PAY-001, FIN-001, 002 |
| [ET-NTF-001](tasks/ET-NTF-001.md) | Channels, templates, devices, preferences, delivery outcomes | *preferences only* — `Ticketing - Profile`, `Org Admin - Settings` | PLT-002, 003, 005, IDN-002 |
| [ET-NTF-002](tasks/ET-NTF-002.md) | Which fact produces which message, to whom, on which channel | — | PLT-003, NTF-001, CAT-001, TKT-002, FIN-003, 004, ORG-002 |

### Wave 6 · Operations — the admin app becomes real here

| Spec | Capability | FE | Blocked by |
|---|---|---|---|
| [ET-ADM-001](tasks/ET-ADM-001.md) | Approval queues, claim, SLA, escalation, bulk approve | `Admin - Approvals Workbench` | PLT-004, 005, ORG-001, 003, CAT-001, NTF-002 |
| [ET-ADM-002](tasks/ET-ADM-002.md) | Market config, commission defaults, feature flags, versioned config | `Admin - Platform Configuration` | PLT-005, ORG-003, FIN-002 |
| [ET-ADM-003](tasks/ET-ADM-003.md) | Stuck transactions, dual approval, dead letters, bulk retry | `Admin - Transaction Recovery` | PLT-003, 005, PAY-001, 002, FIN-003, 004, 005 |
| [ET-ADM-004](tasks/ET-ADM-004.md) | Dashboard aggregations across all three services, polling contract | `Admin - Analytics`, `Admin - Dashboard`, `Org Admin - Dashboard` | PLT-004, 007, FIN-001, 005, TKT-003 |
| [ET-ADM-005](tasks/ET-ADM-005.md) | Metrics, tracing, correlation IDs, SLOs, health, alerting | `Admin - Observability & Health` | PLT-001, 003, 005, FIN-005, ADM-003 |

### Wave 7 · Scale and compliance

| Spec | Capability | FE | Blocked by |
|---|---|---|---|
| [ET-PLT-008](tasks/ET-PLT-008.md) | PII inventory, GDPR erasure, 30-day grace, anonymised retention | `Ticketing - Profile` (erasure request), `Admin - Users` | PLT-002, 007, IDN-002, FIN-001, NTF-001 |
| [ET-PLT-009](tasks/ET-PLT-009.md) | Immutable audit log — what is recorded, who may read it | `Admin - Transactions & System` (Audit logs) | PLT-001, 007, 008, ADM-005 |
| [ET-PLT-010](tasks/ET-PLT-010.md) | Event + GraphQL versioning, upcasting, deprecation windows | — | PLT-002, 003, 004, 005 |
| [ET-PLT-011](tasks/ET-PLT-011.md) | Rate limits, on-sale queueing, bot defence, OTP abuse control | `Ticketing - Checkout` (queue state) | PLT-001, 005, 007, IDN-001, CAT-002, 009 |

---

## 8 · What can run in parallel

Within a wave, slices whose `blocked_by` sets are already `implemented` fan out freely. The
genuine serialisation points are few and worth naming, because everything else is parallel:

| Contention | Rule |
|---|---|
| **One SDL per subgraph** | `booking`'s `schema.graphqls` is touched by TKT-001/002/003/004, PAY-001, FIN-001…005 and ADM-003. Every `GQL-*` task on one subgraph is `parallel-safe: no` against its siblings. Sequence them or serialise the merge |
| **`ET-PLT-002` §4 registry** | Any slice adding a collection or index edits one registry table. Same rule |
| **`ET-PLT-005` §4 code registry** | Same — the registry is closed, and two slices appending at once will conflict |
| **Inventory writes** | `ET-CAT-002`'s `findAndModify` and `ET-TKT-001`'s hold are the platform's most contended write. One author, one slice at a time |
| **Everything else** | Parallel. Use `isolation: 'worktree'` when two agents would otherwise edit the same module concurrently |

**Wave gate.** A wave opens only when every spec in the previous wave is `implemented` — not
`in-progress`. `blocked_by` is a hard edge; a slice started against an unimplemented blocker
builds against an interface that does not exist yet, which is how a corpus this carefully
sequenced still ends up with 30 untracked classes and no tests.
