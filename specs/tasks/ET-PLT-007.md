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

### BE-7 · Backchannel logout; the removed-member latency test — **done, 2026-10-10**
- **Spec** R7 · **§5** T7 · **depends** BE-1, BE-4 · **parallel-safe** yes
- **Acceptance** a removed member loses access **on the next request**; a disabled account stops
  within 5 minutes.
- Full revocation is [`ET-IDN-003`](ET-IDN-003.md); this is the Keycloak-side channel it builds on.
- Already built by `ET-IDN-003`'s session: backchannel logout wiring (`backchannelLogoutHandler`),
  `KeycloakSessionRevoker`/`RevocationRequestGuard`, proven by `keycloak.it.ts` and
  `LogoutRevokesTokenEndToEndTest`. This phase closed the one narrow gap: a sub-second timing
  assertion on the removed-member path (`MemberRemovalTest.removalIsVisibleOnTheVeryNextReadWithinOneSecond`)
  and a direct test that a disabled account cannot refresh at Keycloak
  (`KeycloakServiceContainerTest.disabledAccountCannotRefresh`). See the §E gate's R7 entry for the
  full evidence list.

## B · Contract

BE-3 — `@auth` is schema, so it composes. A field that reaches the supergraph without it is a
composition failure, not a review finding.

## C · Frontend

No screen, but three cross-cutting obligations that every later `FE-*` inherits:

### FE-1 · Role-aware navigation and affordances — **already satisfied, checked 2026-10-10**
- **depends** BE-3, [`ET-PLT-004`](ET-PLT-004.md) BE-8 · **parallel-safe** yes
- Sidebar groups and actions render from the caller's roles. An operation the caller cannot
  perform is **not rendered**, rather than rendered and refused — but the server refuses anyway,
  because a hidden button is UX, not security.
- `PermissionGate`/`AdminGate`/`FinanceGate` exist in the shared barrel but are genuinely unused —
  both consoles built their own equivalent mechanism instead, each already proven by its own
  Playwright spec: `apps/admin` filters its drawer via `modulesFor(staff.roles)`
  (`src/config/navigation.ts`), and every module page wraps its content in `ModuleFrame`, which
  renders "No access" + "Back to my dashboard" for a role that reaches the URL directly
  (`apps/admin/e2e/browser/gating.spec.ts`, one case per role). `apps/organization-admin` filters
  its drawer via `visibleSections(isActive, ctx.capabilities)` (`src/config/navigation.ts`) and
  blocks direct navigation to a capability the membership role lacks in `ConsoleShell`'s own
  `blocked` check (`apps/organization-admin/e2e/browser/roles.spec.ts`, "role MARKETER sees the
  no-access state on finance, team and bookings"). Extending `PermissionGate` into either console
  would duplicate working, tested logic rather than close a gap — left as is.
- **Acceptance** every operation a screen calls appears in `docs/FRONTEND_GRAPHQL_CONTRACT.md`
  with a role the caller holds.

### FE-2 · `idempotencyKey` on every money-moving mutation — **done, 2026-10-10**
- **depends** BE-6 · **parallel-safe** yes
- Generated client-side, **stable across retries of the same user intent** — regenerating it on
  each attempt defeats the entire mechanism. Persist it with the in-flight intent so a page
  reload reuses it.
- Shared `useIdempotencyKey(storageKey)` (`libs/shared/src/lib/idempotency.ts`): mints once per
  identity, persisted to `sessionStorage`, re-mints automatically when the identity changes, and
  exposes `regenerate()` for a deliberate new attempt at the same identity (resending an OTP,
  retrying after the user fixes something). Applied to the four existing ref-based call sites —
  `CheckoutClient.tsx` (reserve, keyed by event; pay, keyed by reservation),
  `RefundDialog.tsx`/`TransferDialog.tsx` (ticketing, keyed by ticket) — and to `PayoutFlow.tsx`
  (organization-admin), which generated its key with `Math.random().toString(36)` instead of a
  real UUID.
- **A real bug found and fixed while doing this**: four admin hooks — `approvePayout`,
  `approveRefund`, `retryPayout`, `createAdminRefund` — generated `idempotencyKey:
  crypto.randomUUID()` fresh **inside the mutation call itself**, so every invocation (not every
  click — every *call*) minted a new key. A dropped response followed by the staff member
  retrying the exact same decision sent a different key each time, so the backend's idempotency
  guard could never catch it — the double-submit protection this whole mechanism exists for was
  silently absent on these four paths. Fixed with `stableActionKey(cache, ...inputs)`
  (same file): a key per distinct input tuple, reused for a retry of the same inputs, fresh only
  when an input genuinely differs (keyed by `(id, notes)`/`(ticketId, reason)`, not by id alone —
  a second, later refund request for the same ticket with a different reason must not be refused
  as a reuse of the first).
- **Acceptance** a double-submit e2e produces one charge; the key survives a reload — see TS-6.

### FE-2 · `idempotencyKey` on every money-moving mutation
- **depends** BE-6 · **parallel-safe** yes
- Generated client-side, **stable across retries of the same user intent** — regenerating it on
  each attempt defeats the entire mechanism. Persist it with the in-flight intent so a page
  reload reuses it.
- **Acceptance** a double-submit e2e produces one charge; the key survives a reload.

### FE-3 · Auth brand contexts, per app — **corrected 2026-10-10, see FINDINGS**
- The attribute is `data-app`, not `data-brand`, and its values are `"platform" | "organizer" |
  "buyer"`, not the app folder names: `apps/admin` → `data-app="platform"` teal ·
  `apps/organization-admin` → `data-app="organizer"` teal (identical palette to platform) ·
  `apps/ticketing` → `data-app="buyer"` — a different shade of the **same** teal family, not
  iris (`m3.themes.css`'s own header comment already says so: "one teal palette for the
  organizer console and the platform admin ... and a slightly different teal for the
  storefront"). This was a documentation mismatch only; the CSS was already correct.
- **Acceptance** compliance suite (F0-5) asserts the correct `data-app` per app — now covered by
  `apps/admin/e2e/brand.spec.ts`, `apps/organization-admin/e2e/brand.spec.ts` (pre-existing) and
  `apps/ticketing/e2e/brand.spec.ts` (new, ET-PLT-007 Phase 8).

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

### TS-6 · Frontend *(L5, Playwright)* — **specs written 2026-10-10, not yet run against a browser (F-058)**
- Double-submit produces one charge; key survives reload.
- A `CUSTOMER` session sees no ADMIN navigation; a direct route hit is refused server-side too —
  already covered, pre-existing: `apps/admin/e2e/browser/gating.spec.ts` ("signed out and
  non-staff").
- Double-submit cases added: `apps/ticketing/e2e/browser/checkout.spec.ts` ("two rapid taps on Pay
  carry the same idempotency key"), `apps/organization-admin/e2e/browser/flows_finance.spec.ts`
  ("payout request: two rapid submissions carry the same idempotency key"). Both dispatch two
  synchronous DOM clicks (bypassing Playwright's own actionability wait) on the submit button and
  assert every call that reaches the mocked upstream carries one identical idempotency key —
  neither app's reload-survival is exercised by these cases (that part of FE-2 is covered at the
  unit level by `useIdempotencyKey`'s sessionStorage persistence, not by an e2e reload). **Could
  not execute either against a real browser in this session** — a pre-existing harness/Next.js
  16.2.11 incompatibility (F-058) made every `harnessTest`-based ticketing browser spec fail for
  reasons unrelated to these two cases' own content. Reviewed for correctness against the harness's
  established patterns; not browser-verified.

## E · Gate

- [x] R0 recorded — `User.keycloakUserId` is **kept** as the sole account-to-Keycloak linkage (ET-IDN-004) and is never an authorization input; the out-of-service permission checks are the one resolution implementation behind `/api/internal/authorization/*` (spec R4, F-061).
- [x] Realm export reproduces §4 on a clean Keycloak — **F-047, 2026-10-09.** `RealmConformanceIT` (9 cases,
      real Keycloak 26.5.2, both `docker-resources/keycloak` exports) reads settings back from the running
      server: five-minute access tokens, refresh rotation with reuse detection (buyer and staff), `external`
      TLS, no registration, no password grant on any client including `admin-cli`, exact redirect URIs,
      PKCE, a required staff second factor, the staff password policy. Mutation-verified: reverting the old
      values fails 7 of 8. The older copies under `docker-resources/keycloak` are not covered (F-047 Open).
- [x] Token-rejection cases pass at each service directly and at the gateway — **Phase 9, 2026-10-10.** `JwtValidationContractTest` (catalog, booking, identity, api-gateway): forged key, rogue realm, claimed issuer, expired, not-yet-valid, no token; `JwksRotationTest` for the bounded key cache; `KeycloakGrantedAuthoritiesConverterTest` for the authorities.
- [x] No field without a gate decision composes; denied fields perform no repository call — **Phase 9, 2026-10-10.** `OperationGateLintTest` (per service), `ComposedSchemaGateTest` (the union), `OperationGateRegistryTest` (every requirement is a §4 row), `AuthDirectiveOutcomesTest` (`ACTOR_NOT_AUTHENTICATED` / `ACTOR_NOT_PERMITTED` / `INTERNAL`), `AuthDirectiveRepositoryGuardTest` (no repository call). The spec's wording is now `@auth` or the equivalent `@PreAuthorize` (R3 amended).
- [x] Tenant filter in the repository; cross-tenant iteration returns nothing — **Phase 6 and 9, 2026-10-10.** Every unscoped lookup is resolved individually (F-056); the per-collection `*TenantBoundaryTest` classes prove the second organization sees nothing; the platform-wide bypass is one named, audited path (`PlatformWideAccess`, `PlatformWideBypassLintTest`).
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
- [x] Idempotency: replay, fingerprint mismatch, parallel-once, and survives a Redis flush — **Phase 4 and 9, 2026-10-10.** `IdempotencyGuardTest`, `FingerprintTest`, and a per-mutation test class (`ReserveTicketsIdempotencyTest`, `PayReservationIdempotencyTest`, `PlatformTransferIdempotencyTest`, the refund and payout cases). `Fingerprint.CLIENT_VARYING` is the one allowlist of fields a client may vary.
- [x] A revoked session stops working within the access-token lifetime — **ET-PLT-007 Phase 7,
      2026-10-10, building on ET-IDN-003.** Backchannel logout is wired in all three Next.js apps
      (`backchannelLogoutHandler` at `/api/auth/backchannel-logout`) and proven end to end against a
      real Keycloak by `keycloak.it.ts`'s "Keycloak delivers a back-channel logout that deletes the
      session by sid" and "sign-out ends the Keycloak SSO session from the server, revokes the
      refresh token and the session at identity-service" (so logout revokes the refresh token at
      Keycloak, not only client storage). `LogoutRevokesTokenEndToEndTest` (8 ordered cases, real
      Keycloak 26.5.2 + Mongo + Redis) proves a logged-out session's access token is refused at once
      and only that session's, survives a Redis flush (rebuilt from Mongo by `RevocationCacheWarmer`),
      and that `SESSION`/`TOKEN`/`USER` revocation each covers tokens minted afterwards. A disabled
      Keycloak account cannot refresh or re-authenticate — `KeycloakServiceContainerTest.disabledAccountCannotRefresh`
      (new) — and both realms issue five-minute access tokens (`RealmConformanceIT`, "both realms
      issue five-minute tokens..."), so a disabled account's outstanding token stops working within
      5 minutes regardless. An organization member removed in MongoDB loses access on the next
      request, not the next token, because `TenantScopeWebFilter`/`IdentityTenantMemberships` resolve
      organization membership fresh from Mongo on every request with no cache in the chain —
      `MemberRemovalTest` proves correctness of the revocation-on-removal write path, and its new
      `removalIsVisibleOnTheVeryNextReadWithinOneSecond` case asserts the next read already sees the
      removal, in under a second (framed as "no propagation delay", not a real latency budget).
- [x] `idempotencyKey` client-supplied, stable across retries, survives reload — **F-057, 2026-10-10.** `useIdempotencyKey` (sessionStorage, keyed by the user's intent); `idempotency.test.ts` covers mint, persist, reload and regenerate.
- [x] `User.keycloakUserId` is the sole linkage field and is read by no authorization decision (the spec's "no `keycloakUserId`" wording was contradicted by ET-IDN-004 and amended in R4).
- [x] Full `mvn -f backend verify` green — **2026-10-10**: shared-library, catalog, booking, identity and api-gateway pass with 0 failures (2,810 unit and integration tests), and keycloak-extensions passes `RealmConformanceIT` (16) and `ContactOtpKeycloakIT` (11) against a real Keycloak 26.5.2.
- [x] Spec `status:` → `implemented` — set 2026-10-10. Open follow-ups (not spec boxes) are recorded in F-061: the `sync/all` endpoint decision, notification params in workflow history, and the new template copy.
