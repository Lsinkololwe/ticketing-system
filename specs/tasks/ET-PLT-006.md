# ET-PLT-006 · Five-layer test harness — tasks

> **Spec** [`specs/_platform/006-test-harness/spec.md`](../_platform/006-test-harness/spec.md) · **Wave 0** · `blocked_by:` ET-PLT-001, 002, 003, 004, 005
> **Screens** — none, but **Track F0** (frontend test foundation) is specified here and runs in parallel.
> **Verify** `mvn -q -f backend test` · `mvn -q -f backend verify -Dgroups=ET-PLT-006`

**This is the most important slice in Wave 0 and the corpus's largest single gap.**

```
find backend -path '*/src/test/*' -name '*.java'   →  0 files
find frontend/web -name '*.spec.ts*' -o -name '*.test.ts*'   →  0 files
```

Acceptance boxes across all 39 specs cite `Persistence.assertNothingPersisted`,
`Inventory.assertConserved`, `Ledger.assertBalanced`, `Concurrency.inParallel`, `TestClock` and
`Providers` **by name**. None of them exist. Until they do, no spec can reach `verified` and the
corpus's own safeguard is inert.

## R0 · Reconcile *(short, for once)*

There is nothing to reconcile in `src/test` — it is empty. What exists and must be classified:

- `backend/pom.xml` manages `testcontainers.version` 1.21.4 and `testcontainers-keycloak` 3.7.0 —
  **already-satisfied**, and do not bump (see [`ET-PLT-012`](ET-PLT-012.md) BE-1).
- No Surefire/Failsafe group configuration exists — `absent`.
- One `@Tag(...)` exists in the tree and it is a **Swagger** annotation, not JUnit. Do not count
  it as coverage.
- Frontend: `apps/{admin,organization-admin}/e2e/auth.setup.ts`, a Microcks harness
  (`global-setup.ts`, `client.ts`, `collection.ts`) and **zero** specs — scaffolding, classified
  `partially-satisfied`; `apps/ticketing` has no Playwright config at all — `absent`.

## A · Backend

### BE-1 · `shared-library` test-jar; Testcontainers singleton with a **real replica set**
- **Spec** R2 · **§5** T1 · **depends** R0 · **parallel-safe** no *(every service depends on it)*
- **Files** `backend/shared-library/src/test/java/com/pml/shared/testing/`, `pom.xml`
- **Acceptance** a transaction test **passes on the container and fails against a standalone
  `mongod`.** Both halves. Against a standalone, `@Transactional` is silently inert (D-01) — a
  harness that cannot demonstrate the difference cannot prove the transaction exists.
- Singleton container, reused across the suite. A container per class turns a 10-minute suite
  into an hour and gets switched off.

### BE-2 · `TestClock`, the test-context override, the auditing provider
- **Spec** R3 · **§5** T2 · **depends** BE-1 · **parallel-safe** no
- **Acceptance** a test advances across a sales-window close and observes `TIER_NOT_ON_SALE`.
- This is what [`ET-PLT-001`](ET-PLT-001.md) BE-3's injected `Clock` was for. A frozen clock is
  the only way to test "live at 9:59, expired at 10:01" without sleeping for ten minutes.

### BE-3 · `Refusals`, `Persistence`, `Ledger`, `Inventory` assertions
- **Spec** R4 · **§5** T3 · **depends** BE-1 · **parallel-safe** yes
- The four named across the whole corpus:
  - `Persistence.assertNothingPersisted(collection)` — *a refused operation persists nothing*
  - `Inventory.assertConserved(tierId)` — *an event can never be sold beyond its capacity*
  - `Ledger.assertBalanced()` — *no balance is written except as a double-entry pair*
  - `Refusals` — the code + `retryable` shape of [`ET-PLT-005`](ET-PLT-005.md)
- **Acceptance** a deliberately write-then-throw service **fails** the assertion. Write that
  service, watch it fail, delete it. An assertion nobody has seen fail is an assumption.

### BE-4 · `Concurrency.inParallel` and the four contention tests
- **Spec** R5 · **§5** T4 · **depends** BE-1, BE-3 · **parallel-safe** no
- **Acceptance** 200-against-50 yields exactly 50, and **repeated runs are stable**.
- A flaky contention test is worse than none: it trains everyone to re-run until green, which is
  precisely how a real oversell reaches production.

### BE-5 · `Providers` WireMock stubs, **failure-first**
- **Spec** R6 · **§5** T5 · **depends** BE-1 · **parallel-safe** yes *(one provider per agent)*
- **Acceptance** each of R6's six failure modes has a named stub and a test.
- Failure-first is deliberate. PawaPay's happy path is the case that already works; timeouts,
  duplicate callbacks, late callbacks and signature mismatches are what break the money.

### BE-6 · Crash-recovery, double-delivery and reverse-order event tests
- **Spec** R6 · **§5** T6 · **depends** BE-1, BE-5 · **parallel-safe** yes
- **Acceptance** `mvn -q -f backend verify -Dgroups=ET-PLT-003 -DfailIfNoTests=true`.

### BE-7 · `SchemaContractTest` per subgraph, over static composition
- **Spec** R7 · **§5** T7 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** a deliberately broken subgraph **fails** the test.

### BE-8 · Tag every test; wire the three-stage CI split
- **Spec** R1, R7 · **§5** T8 · **depends** BE-2…BE-7 · **parallel-safe** no *(one workflow)*
- Every class `@Tag("<ET-ID>")`; every `@DisplayName` names its requirement (`ET-FIN-002-R3`).
  That is what makes coverage a `grep`.
- **Acceptance** fast stage under 60 s; full suite under 10 min. A suite slower than that is a
  suite people skip locally, and it stops being a gate.

### BE-9 · `-DfailIfNoTests=true` on every module-scoped verify command · **precondition P1**
- **Spec** R7 · **§5** T9 · **depends** R0 · **parallel-safe** yes *(one spec per agent)*
- **Files** the `verify:` block of every `spec.yaml`
- **47 of 71 `-Dgroups` commands currently omit it**, across 13 specs — ET-PLT-001, 002, 003,
  004, 005, 006, 007, 008, 010, 011 and ET-ADM-001, 004, 005. Most of Wave 0 is in that list.
- Without the flag a tag matching nothing exits 0, so a spec verifies green having executed
  nothing — [README §Traceability](../README.md#traceability) calls this *"worse than not running
  them, because it looks like proof."* Combined with an empty `src/test`, the corpus reports
  green across the board **today**.
- **Acceptance** `mvn -f backend/<module> test -Dgroups=ET-XXX-999 -DfailIfNoTests=true` exits
  **non-zero** on an empty selection. Prove it with a deliberately bogus tag.
- **Do this first.** It is a one-line edit per spec and it is the difference between a
  verification stage and a green light that means nothing.

## B · Contract

BE-7 — composition is a test layer, not a build step that warns.

## C · Frontend — **Track F0**, the frontend test foundation

Blocks every `FE-*` and Playwright `TS-*` task in the corpus. Independent of the backend waves —
start it alongside [`ET-PLT-012`](ET-PLT-012.md).

### FE-1 · Playwright project for `apps/ticketing` *(F0-1)*
- **Acceptance** `nx e2e ticketing` runs and reports **0 tests**, not a configuration error.
  `data-brand="ticketing"`, iris accent, Space Grotesk headings in the fixture.

### FE-2 · org-admin auth harness *(F0-2)* — **the one that bites**
- org-admin has **no auth harness**, and its specs have never run green.
- `page.route` **cannot** intercept a Next.js Server Component's fetch. The usual mocking
  approach silently does nothing there — the suite looks present and proves nothing.
- Build the real auth path (as `apps/admin` does against the `myticketzm-admin` realm), or do not
  write org-admin specs.
- **Acceptance** a spec authenticates as `ORGANIZER` and reaches `(dashboard)/dashboard`.

### FE-3 · Microcks container setup extended to admin and ticketing *(F0-3)*
- Playwright starts `webServer` **before** `globalSetup`, so the app's `GRAPHQL_ENDPOINT` must be
  known when the config is evaluated — hence the **fixed host port**, overridable via
  `MICROCKS_PORT`. Do not "improve" this to a random port.
- **Acceptance** three apps share one container-backed mock path.

### FE-4 · Testcontainers subgraph fixture for Apollo-driven surfaces *(F0-4)*
- **Microcks returns 500 on any GraphQL query containing fragments.** Fine for Server Components,
  which issue plain queries; **Apollo Client traffic cannot be mocked this way.**
- **Acceptance** an Apollo-driven screen's e2e passes against a container speaking the real
  schema. Never a hand-rolled `fetch` stub — a stub proves the stub matches the test's belief
  about the response, which is exactly where beliefs are wrong.

### FE-5 · Design-system compliance suite across all three apps *(F0-5)*
- Assert the **token contract**, not pixel values: no raw hex, no raw `px`, only Inter /
  Space Grotesk / Fira Code, closed prop sets, barrel imports, correct `data-brand` per app
  (`admin`/teal, `org-admin`/teal, `ticketing`/iris).
- Model to copy: `apps/*/e2e/compliance/`.
- **Acceptance** `npm run e2e:compliance` green on admin, org-admin and ticketing.

### FE-6 · Error-UI primitives *(F0-6)* → specified in [`ET-PLT-005`](ET-PLT-005.md) FE-1…FE-5

### FE-7 · Currency and status formatters *(F0-7)*
- `K 125,430` — Kwacha symbol, space, tabular Fira Code numerals. **Never `ZMW`, never `$`.**
- Statuses humanised: `PENDING_REVIEW` → "Pending Review", `PUBLISHED` → "Live". No raw enum ever
  reaches the DOM.
- **Acceptance** unit-tested, and the compliance suite asserts no raw enum renders.

### FE-8 · Selector discipline
- Selectors are `data-testid`, never text or CSS class. A `PreToolUse` hook already enforces test
  ids on `.tsx` — treat it as the contract it is.
- Every screen covers **loading, empty, error, populated**. An empty state is a designed screen,
  not a fallback.

## D · Tests — the harness tests itself

### TS-1 · Negative proofs *(the whole point)*
Each assertion gets a deliberately broken subject that must fail it:
- write-then-throw service → `Persistence.assertNothingPersisted` fails
- unbalanced journal write → `Ledger.assertBalanced` fails
- leaked hold → `Inventory.assertConserved` fails
- broken subgraph → `SchemaContractTest` fails
- standalone `mongod` → the transaction test fails
- bogus tag + `-DfailIfNoTests=true` → non-zero exit

### TS-2 · Stability
Run BE-4's contention tests **20 times** in CI before declaring them stable.

### TS-3 · Budget
Fast stage < 60 s, full suite < 10 min, asserted in CI rather than hoped for.

## E · Gate

- [ ] R0 recorded; the single existing `@Tag` correctly identified as Swagger, not coverage
- [ ] **BE-9 done first** — all 47 verify commands carry `-DfailIfNoTests=true`, proven with a bogus tag
- [ ] Transaction test passes on a replica-set container and **fails** on standalone
- [ ] All four assertions exist and each has been **seen to fail** against a broken subject
- [ ] `TestClock` drives a sales-window close to `TIER_NOT_ON_SALE`
- [ ] 200-against-50 stable over 20 runs
- [ ] Six provider failure modes stubbed and tested
- [ ] Broken subgraph fails `SchemaContractTest`
- [ ] Every test tagged with its spec ID; `@DisplayName` names the requirement
- [ ] Fast stage < 60 s; full suite < 10 min
- [ ] **F0 complete**: ticketing Playwright project, org-admin auth harness, Microcks on three apps, Testcontainers subgraph fixture, compliance suite, error primitives, formatters
- [ ] Spec `status:` → `implemented` — **and Wave 1 does not open until it is**
