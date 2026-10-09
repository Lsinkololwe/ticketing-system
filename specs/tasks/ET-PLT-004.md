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

## R0 findings *(2026-08-19)*

| Item | Expected | Measured | Class |
|---|---|---|---|
| Static composition | composes | **composes** | `already-satisfied` |
| `id: ID! @external` inside an `extend type` | 0 | **0** | `already-satisfied` |
| Federation `@link` version per subgraph | one, v2.9 | **v2.9 in all three** | `already-satisfied` |
| Authorization inside an entity fetcher | 0 | **0** | `already-satisfied` |
| `@DgsEntityFetcher` per resolvable `@key` type | 5 | **3** | `absent` |
| `success:` mutation wrappers | 0 | **66** (booking 32, identity 19, catalog 15) | `contradicted` |
| Committed `supergraph.graphql` matches the subgraphs | yes | **no — 195 fields behind** | `contradicted` |
| `federation_version` in `supergraph-static.yaml` | pinned | **`2` — floating** | `partially-satisfied` |
| `@tag` usage | every admin/internal element | **766 across three subgraphs** | `partially-satisfied` |
| Pagination types (`PageInfo`/`Connection`/`Edge`/`Page`) | declared | **present in all three** | `partially-satisfied` |

**The good news first: it composes.** `./compose-supergraph.sh --static` exits 0 with six hints and
no errors. The spec's central worry — that the three subgraphs no longer compose — does not hold,
and BE-1's hardest sub-case, `id` redefined inside an `extend type`, does not occur anywhere.

**The committed supergraph was 195 fields behind the subgraphs.** Recomposing changed 9,393 lines.
Most of that is the composition plugin reordering directive declarations, so line count proves
nothing — but a semantic comparison does:

- **28 types and 195 fields present in the subgraph SDL and missing from the committed
  supergraph**, including the entire `CheckIn` / `CheckInConflict` / `CheckInSummary` group,
  `ReferenceData`, `PayReservationInput` and `ValidateTicketInput`.
- **5 types and 49 fields in the committed supergraph that the subgraphs no longer define** —
  `Mutation.purchaseTicket`, `Mutation.useTicket`, `Mutation.completeReservation`,
  `Mutation.extendReservation`, and the `PurchaseTicketMutationResponse` /
  `UseTicketMutationResponse` / `ValidateTicketMutationResponse` wrappers.

Those four mutations are the sharp end: the local router advertised them, and any client calling
one would have had its query planned against a subgraph field that no longer exists. The router
does not hot-reload reliably here ([`ET-PLT-003`](ET-PLT-003.md) recorded the same for the Service
Bus emulator), so a stale artefact stays stale until someone recomposes **and** restarts.

**`federation_version: 2` is floating**, not pinned. Rover resolves it to the newest 2.x — this run
fetched `supergraph-v2.15.2`. The composed output can therefore change with no schema change at
all, which is part of what made the diff above unreadable, and would make BE-7's CI check
non-deterministic. R2 asks for one federation version; the composition config is where that has
to be said.

**`Organization` and `EventAccessGrant` carry a resolvable `@key` and have no entity fetcher.**
Five types are declared federatable; three (`Event`, `Ticket`, `User`) have a
`@DgsEntityFetcher`. When the router resolves an `Organization` reference from another subgraph it
calls `_entities`, finds no fetcher, and the field comes back null — a federated query that
silently returns nothing rather than failing. This is BE-2's acceptance and it is not met.

**66 `success:` wrappers remain**, so BE-5 is the largest single piece of work in this slice.

### What was changed during R0

`docker-resources/apollo-router/ticketing/supergraph.graphql` was **recomposed and is now
uncommitted** in that sibling repository, along with an untracked `.static-build/` directory the
script generates. Recomposing is the only way to answer "does it compose today", and the result is
strictly more correct than what was there. It is left uncommitted for review rather than committed.

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

## A · Backend progress *(2026-08-19)*

- [x] **BE-4 · Tag every admin and internal element; configure the `public` contract** — *5 tests,
  1 mutation verified.*
  - Tagging was already in good shape: **529 `admin`, 246 `organizer`, 59 `mobile`, 27 `internal`**,
    and every `@auth(requires: ADMIN)` element already carried a matching tag. What was missing was
    anything that would notice when that stopped being true.
  - `PublicContractTest` **derives** the public contract rather than trusting it: it strips every
    element tagged `admin` or `internal` from the on-disk SDL and looks for admin vocabulary in
    what is left. GraphOS applies the real exclusion in the cloud, so a test that asserted the
    published result would be checking a system this build cannot see.
  - Four separate rules, because they fail differently. Admin vocabulary reaching the public
    contract; `@auth(requires: ADMIN)` without a tag; the exclusion matching **nothing** (a
    formatting change to the tag would make the vocabulary check pass by finding no admin fields
    at all); and tag names outside the closed set — `@tag(name: "Admin")` reads correct in a diff
    and excludes nothing, which is the worst case because the author believes it is protected.
  - One reviewed exception: `suspendMember` matches `^suspend[A-Z]` and is an organizer acting
    inside their own organization. It is listed by name rather than by loosening the pattern —
    widening it would also stop checking `suspendUser` and `suspendOrganization`, which are
    platform-staff actions.

- [x] **BE-5 · Remove every `success`/`message` mutation wrapper** — *66 → 0, across schema, Java
  and the frontend.*
  - **`{ success: false }` is HTTP 200 with a well-formed body.** Every transport check passes, the
    Apollo error link sees nothing, and a client that forgot the flag shows a confirmation for a
    payment that never happened. A refusal in the `errors` array cannot be missed the same way.
  - 66 wrapper types removed or reduced: **45 pure wrappers** where the mutation now returns the
    entity, **6 delete mutations** that now return the deleted `ID!` (which is what a cache
    eviction needs), and **15 real payload types** — bulk counts, auth tokens, export descriptors —
    that kept their fields and lost only the boolean-error machinery.
  - **`DeleteMutationResponse` and `ReportExport` are `@shareable` across booking and catalog**, so
    they had to change in both at once or composition fails. Checking that before starting is the
    only reason this was one change rather than three broken ones.
  - Every `.onErrorResume(e -> …success(false, e.getMessage()…))` is gone. Those blocks were
    simultaneously the CWE-209 leak and the reason failures were invisible: they caught
    *everything*, echoed the exception message to the caller, and returned HTTP 200.

  **What the transformation nearly cost, and how it was caught.** An automated pass removed a
  `.onErrorResume(DuplicateKeyException…)` in `createPayoutRequest` that was not error-swallowing
  at all — it resolved an idempotency race by returning the winning request. A diff of every
  removed handler against the originals found it, and found that the only other typed handlers lost
  were `SecurityException` ones. That exposed a real gap: `SecurityContextUtils` raises
  `SecurityException` for "not authenticated", and it had no registry mapping, so those would have
  become `INTERNAL_ERROR`. It is now `ACTOR_NOT_AUTHENTICATED`, listed explicitly rather than by a
  broad rule, because `SecurityException` is a JDK type.

- [x] **BE-8 · Point codegen at the contracts; delete hand-written GraphQL types** — *4 tests,
  1 mutation verified.*
  - Codegen read from **a running router**. That makes the committed types a function of whatever
    happened to be up: three services on slightly different branches compose a supergraph that
    exists on no branch, and the types can change with no schema change or fail to change with one.
    It also put codegen out of reach of CI entirely. It now reads
    `docker-resources/apollo-router/ticketing/supergraph.graphql`, composed with a pinned
    federation version. Two consecutive runs are byte-identical.
  - **The one cost is stated in the config**: a supergraph carries federation's own machinery, so
    the output gains four inert scalar aliases an introspecting router would have hidden.
  - Three hand-written `PageInfo` shapes now derive their field set from the generated type via
    `Pick<>`. Verified by deleting `totalPages` from the generated schema: the build fails in three
    places. Before, it would have compiled and rendered a blank cell.
  - `codegenAuthority.test.ts` forbids re-declaring a GraphQL shape by hand while allowing an alias
    to a derived one — the brace is what matters, not the name.

  **`graphql-codegen` exits 0 on a document that selects a field the schema does not have.** It
  generates types for what it can resolve and moves on, so the generated types agree with the
  document and both disagree with the server. TypeScript cannot see inside a template literal, so
  neither check a developer would expect to catch this can. `documentValidity.test.ts` parses every
  `gql` document and validates it against the composed schema — the only thing that does. It found
  six documents broken by BE-5 (now fixed) and **eleven that were already broken**, held in a frozen
  `KNOWN_BROKEN` list that fails the build if it grows *or* if an entry is fixed without being
  removed. See [`specs/FINDINGS.md`](../FINDINGS.md) F-003.

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

- [x] R0 recorded; gap documents read **here** and nowhere else — findings table above
- [x] §4's ownership registry and the SDL describe the same graph — `FederationRegistryTest`
  - The direction nothing checked. `FederationContractLintTest` reads the SDL and proves it
    *internally* consistent, which stays true even if §4 describes a different graph — and it
    does, in three places, each allowlisted with why it is a design decision rather than a defect:
    `TicketTier/booking` (§4 is the stale half — catalog owns inventory and booking reserves over
    REST), `Organization/booking` (root queries instead of an extension — a product decision),
    `Event/identity` (recorded, never built).
  - This is how [F-024](../FINDINGS.md) was found: §4 said booking extends `TicketTier` with
    `availableQuantity`; it does not; asking how booking gets that number instead led into a REST
    client where an ordinary sold-out threw a `NullPointerException` the circuit breaker counted
    as an outage.
- [x] `BigDecimal` round-trips as a decimal, never a float — `MoneyScalarRoundTripTest`
  - R2 names this scalar specifically and no round-trip test existed for any scalar. Every price,
    fee, commission, escrow balance and payout in the platform crosses the graph through it; a
    `Double` anywhere in that path is not a rounding inconvenience but a wrong number. Five cases,
    including scale preservation and the `0.10 + 0.20` case that breaks binary floating point.
  - One gap pinned rather than closed: the coercion accepts a GraphQL **float literal**, so a
    client can send `amount: 150.10` unquoted and lose precision before the server sees it. That is
    a frontend obligation (R7 — generated input types are strings) and the backend cannot enforce
    it. Recorded in the test so it is visible rather than discovered from a mismatched payout.
- [x] Supergraph composes statically; broken subgraph fails CI, proven — **2026-09-19.**
      `backend/tools/federation/composition-gate.sh` composes the on-disk SDL (with the shared
      `@auth` SDL, as the services serve it) at the pinned `=2.15.2`, then composes three broken
      variants and requires each to fail **with its own error** — a redeclared `id` in an
      `extend type` (*duplicate definitions*), a shared field with a mismatched scalar
      (`FIELD_TYPE_MISMATCH`), a field of a type another subgraph owns (`INVALID_FIELD_SHARING`).
      A fixture whose anchor disappears fails the gate rather than skipping. CI's
      `composition-check` runs it on every change; `FederationContractLintTest.compositionGate`
      runs it in the local build. The old CI job composed the bare `schema.graphqls` without
      `@auth`, at federation `=2.9.0`, and could not have reproduced the local supergraph; the
      GraphOS check and publish steps sent the same bare files — they now send the assembled SDL
      (`subgraph-sdl.sh`)
- [x] No `id` redeclared in any `extend type`; one federation version across three subgraphs —
      `FederationContractLintTest`, which also holds the two entity-fetcher rules
- [x] Every admin/internal element `@tag`ged; public contract free of admin vocabulary —
      **2026-09-19.** `PublicContract` derives the public variant from the parsed SDL the way a
      contract does (a type tag excludes the type in every subgraph) and `PublicContractTest`
      asserts: no admin vocabulary survives; every operation only the console may call — resolved
      from `@auth` or `@PreAuthorize` via the generated contract document — is excluded; and the
      variant still composes (nothing public points at an excluded type, no type is emptied).
      `PublicContractDerivationTest` (L1) shows each check red on a small schema. What it found:
  - **98 elements tagged both `organizer` and `admin`** — `Organization`, `OrganizationMember`,
    `BankAccount`, `PayoutRequest`, `RefundRequest`, `PromoCode` among them. A contract excludes an
    element carrying *any* excluded tag, so these were hidden from organization-admin, which §4
    generates from the public variant — and 54 public operations returned them, so the public
    variant would not have composed. The `admin` tag is removed wherever `organizer` is present.
  - `EventEscrowAccount`, `StandaloneEscrowTransaction`, `EscrowTransactionOffsetPage` and
    `ReportExport` were admin-only types returned by organizer operations (`myEscrowAccounts`,
    `escrowTransactions`, `exportSalesReport`); untagged. Access is still decided per call by the
    ownership checks on those resolvers.
  - The contract document's role column **dropped ownership grants**: `hasAnyRole('ADMIN',
    'FINANCE') or @eventSecurityService.isEventOrganizer(…)` read as `ADMIN or FINANCE`, so an
    organizer's own queries looked console-only. The generator now keeps them
    (`@isEventOrganizer or ADMIN or FINANCE`, `self`).
  - CI now publishes the `public` contract (`rover contract publish … --exclude-tag admin
    --exclude-tag internal`) after the subgraphs.
- [x] No `success`/`message` mutation wrappers remain — **the "66" was a miscount, and the
      contract was already clean.**
  - Re-measured against what R4 actually governs, which is the **GraphQL contract**: of **215
    mutations** across the three subgraphs, **zero** return a type carrying `success: Boolean`.
  - The 66 were Java counts — `boolean success` fields on internal DTOs, service results and REST
    payloads. R4 says nothing about those, and it should not: a `success` flag on an internal
    method's return value is ordinary Java. What R4 forbids is putting the outcome of an operation
    in the *data payload a client receives*, where an empty `errors` array and a 200 look like the
    operation worked.
  - Six SDL types do carry `message: String`, and each is **content, not a failure channel** —
    `TeamInvitation` (the inviter's note), `ValidationResult` (a sentence a steward reads at a
    gate, beside an exhaustive `CheckInOutcome` enum the client actually branches on),
    `ApprovalNotification`, `OrganizerActivityItem`, and two error-detail types.
    `MutationShapeLintTest` allowlists them **by name with the machine-readable field each pairs
    with**, and refuses any new one.
- [x] Every list field bounded; over-limit refused with `PAGE_SIZE_EXCEEDED`
  - **The previous note was true and incomplete, and the gap it left was the large one.** Six sites
    that were *clamping* had been fixed. Four pagination input types were not clamping either —
    they were not checking at all, and **81 of the 137 paged fields take one of them**. See
    [F-025](../FINDINGS.md): `size: 1000000` produced `limit(1000000)`, on `payoutRequests`,
    `auditLogs` and `stuckTransactions` among others.
  - All five files now route through `PageSize.require`, which refuses rather than reduces.
    `PagingBoundLintTest` scans every pagination input for the call and is mutation-verified.
  - §4's paging registry gained four rows — `provinces`, `categories`, `citiesWithEvents`,
    `currentUserPermissions` — which were correctly implemented as bounded bare lists and simply
    absent from the registry, making R5's "every list field is a row of this registry" false about
    the spec rather than the code.
- [x] `npm run codegen && git diff --exit-code` clean, **after clearing both caches** —
      **2026-09-19.** With `.nx/cache` and `node_modules/.cache` cleared, codegen regenerates
      byte-identical output, and twice in a row is byte-identical. CI job `codegen-current` now
      composes the supergraph, regenerates and fails on any diff. The ordering trap stands:
      recompose before codegen, or the old schema is read.
- [x] Zero hand-written GraphQL types under `frontend/web` — **2026-09-19.** Codegen generated **no
      operation types at all**: its documents glob matched `*Definitions.ts` and no such file
      existed, so all 95 hooks typed their results by hand. The glob now covers every document;
      88 operation and 86 variables types are generated and every hook uses them, view models
      derived with `Pick<>`/indexed access. `codegenAuthority.test.ts` now fails on an inline
      object-literal generic on a query/mutation hook, and on any `interface`/type literal named
      after one of the 963 supergraph types, across `libs/shared` and all three apps — each rule
      shown red on a fixture. `documentValidity.test.ts` `KNOWN_BROKEN` is empty: every document
      validates. 56 hooks no app reached were deleted with their documents. Typing the rest
      against the real schema found live defects, fixed:
  - **Mutations that always reported failure.** Admin event approve/reject/request-changes, and in
    organization-admin publish, unpublish, create event, create payout request and every bank-account
    action, read a `success` field the mutations never return — so every success was shown as a
    refusal. Success is now the mutation resolving, failure a thrown error.
  - **Team role changes never worked**: `updateMemberRole` sent `{role}`; the input is
    `{organizationId, memberId, newRole}`.
  - Seven documents selected fields the schema lacks (check-in roster, `updateProfile`,
    `BusinessAddress.street`, `reactivateOrganization`, `approveOrganization($comments)`, and the
    three document mutations no spec declares).
  - The shared barrel re-exported whole entity types (`Event`, `Ticket`, …) that the customer app
    used to type partial selections; replaced with derived row types, the barrel now exports enums only.
- [x] `docs/FRONTEND_GRAPHQL_CONTRACT.md` regenerated — **the generator exists**, 2026-09-01.
      `FrontendContractLintTest` derives the document from the three SDLs plus each resolver's
      `@PreAuthorize`, and fails the build when the committed file disagrees;
      `-Dcontract.write=true` rewrites it. Identity's 19 doc-only and 15 missing operations are
      gone by construction rather than by hand. It also corrected the document's own arithmetic:
      **51 of its 557 "operations" were object fields**, not root fields — the real figure is 506.
      Three guards ride along: a >300 floor so a broken parser cannot regenerate a blank file and
      then agree with it, a check that `status`/`type`/`verified` never appear as operations, and
      `graphQlEndpointRequiresAToken`, which pins the `.authenticated()` rule the 76 field-level-
      unguarded operations stand on. See [F-006](../FINDINGS.md#f-006--the-frontend-contract-claims-to-be-generated-and-nothing-generates-it)
      and [F-008](../FINDINGS.md#f-008--the-sign-in-surface-sits-behind-the-sign-in-requirement)

> **D-19 progress, 2026-09-01.** The pagination half of [D-19](../ROADMAP.md) is complete: 39 twin
> pairs collapsed (identity 7, booking 18, catalog 14) and 15 of 17 lone variants de-suffixed. The
> naming half advanced again on 2026-09-01 under the O-2 … O-6 rulings: `myPermissions` resolved
> in both specs that declared it, `ticketTiers` split into the two operations it always was,
> `eventApprovalQueue` merged into `pendingApprovalEvents`, and six one-candidate renames applied.
> §4 resolution stands at **161/303**; of the 127 unresolved, **107 have no shipped candidate at
> all** and are build work rather than naming. Three `PaginationCollapseTest` classes hold the result, including the assertion
> that catalog's 20 **audience-split** pairs keep both halves, since collapsing those deletes a
> public capability rather than duplication.

- [x] Spec `status:` → `implemented` — 2026-09-19
