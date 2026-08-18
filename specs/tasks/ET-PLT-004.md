# ET-PLT-004 · Federation contract — tasks

> **Spec** [`specs/_platform/004-federation-contract/spec.md`](../_platform/004-federation-contract/spec.md) · **Wave 0** · `blocked_by:` ET-PLT-001
> **Screens** — none, but this is where **every** frontend type comes from. `FE-*` tasks in every later slice are blocked on BE-8 here.
> **Verify** `compose-supergraph.sh --static` · `mvn -q -f backend test -Dgroups=ET-PLT-004` · `cd frontend/web && npm run codegen`

Three subgraphs, one supergraph, ~8,000 lines of existing SDL and **589 operations** already
catalogued in `docs/FRONTEND_GRAPHQL_CONTRACT.md`. This slice makes the contract composable,
tagged, and the sole source of frontend types.

## R0 · Reconcile *(do this first)*

```bash
cd ../docker-resources/apollo-router/ticketing && ./compose-supergraph.sh --static   # does it compose today?
grep -n 'extend type' backend/*/src/main/resources/graphql/schema.graphqls
grep -rn 'success\|message' backend/*/src/main/resources/graphql/schema.graphqls | grep -i 'type.*Payload\|type.*Result'
```

Inputs to this pass — but **not** to the spec: `docs/STUB_TYPES_ANALYSIS.md` and
`docs/BACKEND_GAP_ANALYSIS_REPORT.md`. Read them here, in R0, where gap documents belong.

Classify per §3 requirement. Flag every `id: ID! @external` inside an `extend type` block: that
is the "tried to redefine field 'id'" error, and it is `contradicted`, not `partial`.

## A · Backend

### BE-1 · Reconcile ownership — one `@key` per type, stubs elsewhere, no `id` in extends
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** **no — the three SDL files must agree**
- **Acceptance** no `id` redeclared inside an `extend type`; all subgraphs link **one** federation
  version (v2.9); the shared scalars agree; the supergraph composes.
- Ownership per [README §IDs](../README.md): `catalog` owns Event/Location/Category/Tier,
  `booking` owns Ticket/Payment/Escrow/Commission, `identity` owns User/Organization/Role.

### BE-2 · One `@DgsEntityFetcher` per owned type; strip authorization from all of them
- **Spec** R1 · **§5** T2 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** a cross-subgraph query resolves each entity by key.
- Entity fetchers are called by the **router**, not by a user — an authorization check inside one
  fails the resolution of a legitimately federated field. Authorization belongs on the field
  ([`ET-PLT-007`](ET-PLT-007.md) `@auth`), never in the fetcher.

### BE-3 · Align federation version, scalars and shared SDL
- **Spec** R2 · **§5** T3 · **depends** BE-1 · **parallel-safe** no *(shared definitions)*
- **Files** the three `schema.graphqls`, `shared-library/.../auth.graphqls`
- **Acceptance** `compose-supergraph.sh --static`; a `BigDecimal` round-trip test.
- `BigDecimal` must serialise identically from all three subgraphs. A money scalar that means one
  thing in `booking` and another in `catalog` is a rounding bug with extra steps.

### BE-4 · Tag every admin and internal element; configure the `public` contract
- **Spec** R3 · **§5** T4 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** the public contract composes and contains **no admin vocabulary**.
- [ROADMAP §Cross-cutting](../ROADMAP.md): *every admin-only schema field is `@tag`ged* — asserted
  here and by composition. An untagged admin field is not merely a leak of data; the field name
  itself leaks the platform's internal model to every public client.

### BE-5 · Remove every `success`/`message` mutation wrapper
- **Spec** R4 · **§5** T5 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** no mutation return type declares `success`.
- Failure is an error with a registry code ([`ET-PLT-005`](ET-PLT-005.md)), not a boolean the
  client must remember to check. `{ success: false }` returns HTTP 200 and a client that forgot
  the check proceeds as though it worked.

### BE-6 · Declare `PageInfo`/`Connection`/`Edge`/`Page`; apply the §4 registry
- **Spec** R5 · **§5** T6 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** every list field matches its registry row; an over-limit page size is refused
  with `PAGE_SIZE_EXCEEDED`.
- Unbounded list fields are how one client takes the platform down at on-sale.

### BE-7 · CI — static composition, `subgraph check`, `subgraph publish`, codegen freshness
- **Spec** R6, R7 · **§5** T7 · **depends** BE-3 · **parallel-safe** no *(one workflow)*
- **Files** `.github/workflows/`
- **Acceptance** a deliberately broken subgraph **fails CI**, asserted by a test. Composition must
  fail the build, not warn.
- Note the router does **not** hot-reload reliably here — after any local recompose, restart the
  local router rather than trusting the file watcher.

### BE-8 · Point codegen at the contracts; delete every hand-written GraphQL type
- **Spec** R7 · **§5** T8 · **depends** BE-4 · **parallel-safe** no *(one codegen configuration)*
- **Files** `frontend/web/codegen.ts`, `frontend/web/libs/shared/src/types/`
- **Acceptance** `npm run codegen && git diff --exit-code`.
- **This task unblocks the entire frontend half of the corpus.** Until it lands, every `FE-*`
  task in every wave is blocked, because the only legitimate source of a TypeScript type for a
  GraphQL shape is codegen.
- **Codegen has two caches that both report SUCCESS while emitting stale types.** If the output
  does not match a schema you just changed, clear them before believing it. A green codegen run
  is not evidence the types are current.

## B · Contract

BE-1 through BE-8 *are* the contract slice for this spec.

## C · Frontend

### FE-1 · Delete hand-written GraphQL types; switch every import to generated
- **Spec** R7 · **depends** BE-8 · **parallel-safe** no
- **Files** `frontend/web/libs/shared/src/types/`, every consumer
- Import from the generated schema types. A `type MyCustomPagination = { page, size }` is a
  defect — use the generated `PageableInput`, with `Partial<T>` where fields are optional.
- **Acceptance** no hand-written type describes a GraphQL shape anywhere under `frontend/web`.

### FE-2 · Regenerate `docs/FRONTEND_GRAPHQL_CONTRACT.md` from the composed contracts
- **Spec** R3, R7 · **depends** BE-4 · **parallel-safe** yes
- It lists every operation with the role it needs — 589 today. It must be generated from the live
  subgraphs, never hand-maintained, or it becomes a document that describes a schema that no
  longer exists.
- **Acceptance** every operation any app calls appears in it with a role the caller holds.

## D · Tests

### TS-1 · Composition *(L4 — the gate)*
- `compose-supergraph.sh --static` in CI; a deliberately broken subgraph fails it.
- The **public contract** composes and contains no `@tag`ged admin vocabulary.

### TS-2 · Entity resolution *(L3)*
- Testcontainers with all three services + router: resolve each owned type by key across
  subgraphs; assert no entity fetcher performs authorization.

### TS-3 · Scalars and pagination *(L1/L2)*
- `BigDecimal` round-trips identically from all three subgraphs.
- Every list field matches its §4 registry row; over-limit page size → `PAGE_SIZE_EXCEEDED`.
- No mutation return type declares `success`.

### TS-4 · Codegen freshness *(L4)*
- `npm run codegen && git diff --exit-code` in CI. Clear both caches first, or this test passes
  on stale output — which is worse than not running it.

Tag `@Tag("ET-PLT-004")`.

## E · Gate

- [ ] R0 recorded; gap documents read **here** and nowhere else
- [ ] Supergraph composes statically; broken subgraph fails CI, proven
- [ ] No `id` redeclared in any `extend type`; one federation version across three subgraphs
- [ ] Every admin/internal element `@tag`ged; public contract free of admin vocabulary
- [ ] No `success`/`message` mutation wrappers remain
- [ ] Every list field bounded; over-limit refused with `PAGE_SIZE_EXCEEDED`
- [ ] `npm run codegen && git diff --exit-code` clean, **after clearing both caches**
- [ ] Zero hand-written GraphQL types under `frontend/web`
- [ ] `docs/FRONTEND_GRAPHQL_CONTRACT.md` regenerated
- [ ] Spec `status:` → `implemented`
