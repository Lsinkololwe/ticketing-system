# ET-PLT-007 · Security and authorization — tasks

> **Spec** [`specs/_platform/007-security-and-authorization/spec.md`](../_platform/007-security-and-authorization/spec.md) · **Wave 1** · `blocked_by:` ET-PLT-001, ET-PLT-004, ET-PLT-005
> **Screens** — no screen of its own; it decides what every screen may call. `docs/FRONTEND_GRAPHQL_CONTRACT.md` lists 589 operations by required role: **ADMIN 96, ORGANIZER 41, CUSTOMER 4, INTERNAL 7, public 441**.
> **Verify** `mvn -q -f backend test -Dgroups=ET-PLT-007` · `mvn -q -f backend verify -Dgroups=ET-PLT-007`

The realm, the roles, `@auth` on every field, tenant scoping in the repository, and the
idempotency guard that stands between a retried tap and a double charge (**D-07**).

## R0 · Reconcile *(do this first)*

```bash
grep -rn '@PreAuthorize\|hasRole\|hasAuthority' backend --include='*.java' | grep -v /src/test/
grep -rn 'keycloakUserId' backend --include='*.java'
grep -c '@auth' backend/*/src/main/resources/graphql/schema.graphqls
```

Classify. Two rows will almost certainly be `contradicted`:
- **`User.keycloakUserId`** — R4 requires it not to exist. The Keycloak `sub` **is** the user id;
  a second identifier is a second source of truth that will drift.
- **Any permission comparison in `catalog-service` or `booking-service`** — D-10 puts resolution
  in exactly one implementation, in identity-service. Those get deleted in
  [`ET-ORG-003`](ET-ORG-003.md) BE-5, and R0 is where they get counted.

`recent commits` include "redesigned security" and "configured production better auth" — read
what those changed before assuming anything is absent.

## A · Backend

### BE-1 · Realm export — roles, composites, clients, scopes, PKCE, redirect allowlists
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** no *(one realm)*
- **Files** `../docker-resources/keycloak/event-ticketing-realm.json` *(sibling repo)*
- Roles per [README](../README.md): `CUSTOMER`, `ORGANIZER`, `ADMIN`, `INTERNAL_SERVICE`.
- **Acceptance** importing the export into a **clean** Keycloak reproduces §4 exactly. A realm
  configured by hand and never exported is a realm that exists on one laptop.
- Shared file with [`ET-IDN-001`](ET-IDN-001.md) BE-8 — coordinate, do not edit concurrently.

### BE-2 · One JWT converter and one decoder configuration in `shared-library`
- **Spec** R2 · **§5** T2 · **depends** BE-1 · **parallel-safe** no *(every service imports it)*
- Extracts `realm_access.roles` → `ROLE_*`, `resource_access.{client}.roles`, `scope` → `SCOPE_*`.
- **Acceptance** forged, wrong-issuer, wrong-audience and expired tokens are each rejected
  **directly at a service**, not only at the gateway.

### BE-3 · `@auth` on every `Query`/`Mutation` field; the enumeration test
- **Spec** R3 · **§5** T3 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** a field **without** `@auth` fails the build; a denied field performs **no
  repository call**.
- The second half is the security property. Denying after the query has run still lets timing and
  error shape confirm the row exists — an enumeration oracle, the same failure
  [`ET-PLT-005`](ET-PLT-005.md) BE-5 closes from the error side.

### BE-4 · `TenantScope`; move every tenant check into the repository filter
- **Spec** R4 · **§5** T4 · **depends** BE-2 · **parallel-safe** no *(the resolution is shared)*
- **Acceptance** permission resolution exists in exactly **one** implementation;
  `/api/internal/**` is scope-gated; **no `User.keycloakUserId` exists**; the cross-tenant
  iteration test returns nothing.
- The filter belongs in the repository, not the service. A service-layer check is one forgotten
  call site away from a leak; a repository filter cannot be forgotten because there is no
  unfiltered query to call.
- [ROADMAP §Cross-cutting](../ROADMAP.md): *no tenant's data is reachable through another
  tenant's query.*

### BE-5 · Lock down `/api/internal/**`; `client_credentials` for every caller
- **Spec** R5 · **§5** T5 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** **401** unauthenticated, **403** with a user token, **200** with an internal
  scope — asserted **per path**, not once. A user token reaching an internal endpoint is a
  privilege escalation, and it is the failure mode a single smoke test misses.

### BE-6 · `IdempotencyGuard` — Redis + unique index, fingerprint, three outcomes
- **Spec** R6 · **§5** T6 · **depends** BE-2 · **parallel-safe** no *(one guard, nine call sites)*
- Per **D-07**: every money-moving mutation takes a client-supplied `idempotencyKey`, guarded by
  Redis `SET NX` with a **24-hour TTL** (`idem:{key}`) **and** a unique index on the persisted
  attempt. Redis alone is not enough — it is a cache, and [`ET-PLT-002`](ET-PLT-002.md) R7 says
  it never holds business state.
- Three outcomes: same key + same fingerprint → the original result; same key + different
  fingerprint → `IDEMPOTENCY_KEY_REUSED`; new key → proceed.
- **The key is client-supplied, not server-generated.** The retry that needs it is the one where
  the client never saw a response.
- **Acceptance** replay returns the original; a changed body refuses; two parallel submissions
  apply exactly once.

### BE-7 · Backchannel logout; the removed-member latency test
- **Spec** R7 · **§5** T7 · **depends** BE-1, BE-4 · **parallel-safe** yes
- **Acceptance** a removed member loses access **on the next request**; a disabled account stops
  within 5 minutes.
- Full revocation is [`ET-IDN-003`](ET-IDN-003.md); this is the Keycloak-side channel it builds on.

## B · Contract

BE-3 — `@auth` is schema, so it composes. A field that reaches the supergraph without it is a
composition failure, not a review finding.

## C · Frontend

No screen, but three cross-cutting obligations that every later `FE-*` inherits:

### FE-1 · Role-aware navigation and affordances
- **depends** BE-3, [`ET-PLT-004`](ET-PLT-004.md) BE-8 · **parallel-safe** yes
- Sidebar groups and actions render from the caller's roles. An operation the caller cannot
  perform is **not rendered**, rather than rendered and refused — but the server refuses anyway,
  because a hidden button is UX, not security.
- `PermissionGate` already exists in the shared barrel; extend it rather than adding a second.
- **Acceptance** every operation a screen calls appears in `docs/FRONTEND_GRAPHQL_CONTRACT.md`
  with a role the caller holds.

### FE-2 · `idempotencyKey` on every money-moving mutation
- **depends** BE-6 · **parallel-safe** yes
- Generated client-side, **stable across retries of the same user intent** — regenerating it on
  each attempt defeats the entire mechanism. Persist it with the in-flight intent so a page
  reload reuses it.
- **Acceptance** a double-submit e2e produces one charge; the key survives a reload.

### FE-3 · Auth brand contexts, per app
- `apps/admin` `data-brand="admin"` teal · `apps/organization-admin` `data-brand="org-admin"`
  teal · `apps/ticketing` `data-brand="ticketing"` iris.
- **Acceptance** compliance suite (F0-5) asserts the correct `data-brand` per app.

## D · Tests

### TS-1 · Token validation *(L3, Keycloak Testcontainer)*
Forged / wrong-issuer / wrong-audience / expired — each rejected at **each** service directly.

### TS-2 · Field authorization *(L2 + L4)*
- A field without `@auth` fails the build.
- A denied field performs no repository call — assert on the repository, with a spy, not on the
  response.

### TS-3 · Tenant isolation *(L3)*
- Cross-tenant iteration returns nothing.
- Cross-tenant id and unknown id are **indistinguishable** — same code, same shape
  ([`ET-PLT-005`](ET-PLT-005.md) BE-5).

### TS-4 · Internal endpoints *(L3)*
401 / 403 / 200 **per path**.

### TS-5 · Idempotency *(L3, the load-bearing one)*
- Replay → original result. Changed fingerprint → `IDEMPOTENCY_KEY_REUSED`, **not retryable**.
- `Concurrency.inParallel`: two parallel submissions of one key → exactly one effect.
- Redis `FLUSHALL` between attempts → the unique index still refuses the duplicate. This is the
  test that proves the guard is not really Redis-only.

### TS-6 · Frontend *(L5, Playwright)*
- Double-submit produces one charge; key survives reload.
- A `CUSTOMER` session sees no ADMIN navigation; a direct route hit is refused server-side too.

## E · Gate

- [ ] R0 recorded; `keycloakUserId` and every out-of-service permission check classified `contradicted`
- [x] Realm export reproduces §4 on a clean Keycloak — **F-047, 2026-10-09.** `RealmConformanceIT` (9 cases,
      real Keycloak 26.5.2, both `docker-resources/keycloak` exports) reads settings back from the running
      server: five-minute access tokens, refresh rotation with reuse detection (buyer and staff), `external`
      TLS, no registration, no password grant on any client including `admin-cli`, exact redirect URIs,
      PKCE, a required staff second factor, the staff password policy. Mutation-verified: reverting the old
      values fails 7 of 8. The older copies under `docker-resources/keycloak` are not covered (F-047 Open).
- [ ] Four token-rejection cases pass at each service directly
- [~] No `@auth`-less field composes; denied fields perform no repository call — **F-046, 2026-10-09.**
      `OperationGateLintTest` (catalog, booking, identity) fails on any query or mutation with neither
      `@auth` in the schema nor `@PreAuthorize` on its resolver, and the deliberately open reads now carry
      `@auth(requires: PUBLIC)`. Mutation-verified (removing the gate on `validatePromoCode` fails it).
      Not done: the spec's wording is `@auth` on *every* field (identity has two, booking about a third,
      the rest rely on `@PreAuthorize`), and no spy test asserts a denied field makes no repository call.
- [~] Tenant filter in the repository; cross-tenant iteration returns nothing — **the mechanism
      exists; write paths shut in catalog, caller-scoped reads built in booking.** Three of the four §4 caller-scoped
      operations now exist — `myPayoutRequests`, `myEscrowAccounts`, `myRefundRequests`. `TenantScope`
      (a set, resolved once per request), `TenantGuard.locate` (which takes the scoped lookup as
      an argument, so it cannot be called without writing the filter), `CallerScope` (which makes
      an `organizationId` argument a selector over the caller's memberships rather than a grant),
      and `findByIdAndOrganizationIdIn` / `findByOrganizationIdIn` finders. `TenantScopeWebFilter`
      is installed in **all three services**. Proven by
      `TicketTierTenantBoundaryTest` and `CallerScopedReadTest` against a Testcontainers replica
      set, both mutation-verified. **The pre-existing read paths are not converted**:
      `TenantBoundaryLintTest` freezes them at catalog 25 · identity 16 · booking 64, a budget
      that may only fall. Catalog rose 24 → 25 for the guard built to close F-007, which is the
      only movement upward this budget admits: one new `TenantGuard.locate` on the same commit.
      See [F-001](../FINDINGS.md#f-001--organization-scoped-data-has-no-tenant-boundary)
- [x] Event writes apply the tenant filter **and** the permission check — **D-20, 2026-09-01.**
      All seven catalog event mutations now go through `EventWriteGuard.forWrite(id, permission)`:
      `findByIdAndOrganizationIdIn` so another organization's event never comes back from the
      query, then `checkEventAccess` for the D-10 permission this mutation needs. They answer
      different questions and the platform needs both — a MARKETER cannot edit events and a
      CONTRIBUTOR is view-only, so membership alone would give every team member owner-level
      power. `TenantGuard.locateAndPermit` takes both as required arguments; `EventWriteGuardLintTest`
      (comments stripped, so a javadoc cannot satisfy it) asserts the code is present and that no
      mutation reaches `eventService.findById`; `EventWriteBothLocksTest` proves each lock refuses
      with the other wide open, against a Testcontainers replica set. Mutation-verified both ways:
      dropping the filter fails exactly the filter cases, stubbing the check fails exactly the
      check cases. `duplicateEvent` moved to `findVisibleById` in the same pass — it copies the
      source event's line-up, capacity and pricing, so reading it *is* the operation.
      **The availability cost is accepted** (D-20): an identity-service outage stops event edits.
- [x] The public single-event query applies a visibility filter — **F-007, closed 2026-09-01.**
      `event(id: ID!)` sits in the schema's PUBLIC block beside a dozen list queries that all end
      `PublishedTrueAndIsActiveTrue`, carried no `@auth` and no `@PreAuthorize`, and resolved to a
      bare `findById`. Drafts, rejected events with their `rejectionReason`, and soft-deleted
      events with `deletedBy` / `deletionReason` were readable by **any authenticated caller**
      holding an id — a self-service `CUSTOMER` token is enough. (First recorded as
      "unauthenticated"; corrected the same day. All three subgraphs require a token on
      `/graphql/**`, whatever the schema's PUBLIC annotations say.) `EventService.findVisibleById` now filters — public first, tenancy only on a miss —
      and is wired into both `event(id)` and the `Event` entity fetcher, since `_entities`
      resolves a caller-supplied key the same way. `EventVisibilityTest`, 11 cases against a
      Testcontainers replica set, mutation-verified twice. See
      [F-007](../FINDINGS.md#f-007--the-one-public-query-that-took-an-id-did-not-filter-on-visibility)
- [x] `/api/internal/**` 401/403/200 per path — **F-046, 2026-10-09.** `InternalSurfaceTest` in catalog,
      booking and identity discovers every endpoint from the controllers and runs it through the service's
      real chain with signed tokens (none 401, administrator user token 403, profile-scope token 403, the
      intended scope 200). Mutation-verified with a `permitAll` placed ahead of the rule. A read-scoped token
      can still call a write endpoint outside `/api/internal/auth/**`; see F-046 Open.
- [~] Idempotency: replay, fingerprint mismatch, parallel-once, and survives a Redis flush — **F-046,
      2026-10-09.** `IdempotencyGuard` and `MongoIdempotencyLedger` are built and proven on real Mongo and
      Redis (`IdempotencyGuardTest`, 11 cases, mutation-verified; `FingerprintTest`). No mutation in the §4
      registry calls it yet, so the nine call sites remain.
- [ ] `idempotencyKey` client-supplied, stable across retries, survives reload
- [ ] No `User.keycloakUserId` anywhere
- [ ] `mvn -q -f backend verify -Dgroups=ET-PLT-007 -DfailIfNoTests=false` green
- [ ] Spec `status:` → `implemented`
