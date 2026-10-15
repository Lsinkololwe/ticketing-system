# ET-PLT-013 · The permission engine — tasks

> **Spec** [`specs/_platform/013-permission-engine/spec.md`](../_platform/013-permission-engine/spec.md) · **Wave 1** · `blocked_by:` ET-PLT-002, ET-PLT-005, ET-PLT-007
> **Status** `approved` — cleared to build, **as narrowed by ROADMAP D-37 and D-54…D-60** (below)
> **Screens** a **read-only** role matrix in `Admin - Platform Configuration.dc.html` / `Admin - Users & Organizations.dc.html`. **No screen for the resolver itself, and no editing surface.**
> **Verify** `mvn -q -f backend/identity-service test -Dgroups=ET-PLT-013 -DfailIfNoTests=false` · `mvn -q -f backend/shared-library test -Dgroups=ET-PLT-013 -DfailIfNoTests=false` · `compose-supergraph.sh --static`

**This spec amends [`ET-ORG-003`](ET-ORG-003.md) R6**, and was itself narrowed by
[ROADMAP D-37](../ROADMAP.md): the role → permission table lives **in the software** and changes
only by a release. No administrator edits it at runtime, so there is no stored mapping, no cache of
one, no change log and no write mutation. What this spec still owns is the *shape* of the catalogue,
that it is declared once in code and fully enforced, and how a caller's platform roles combine.

ET-ORG-003's six-step order, its audit of every step-1 allow and its single-implementation
constraint are unchanged. The two compose: ET-PLT-013 answers *what a code means and who holds it
by platform role*; ET-ORG-003 answers *which source decides*.

## Requirement disposition

| Requirement | Disposition | Reason / pointer |
|---|---|---|
| **R1** closed key grammar | **Stands** | `resource:action` as in use, per [D-59](../ROADMAP.md): one colon, lower-case snake-case parts, no dots, no `*`. The `read`/`update` vocabulary is not adopted and no code is renamed. |
| **R2** catalogue declared in code | **Stands** | `com.pml.shared.security.Permission` is the catalogue. Every code is enforced, or is listed in an explicit role-gated register (D-60). |
| **R3** one stored document per role | **Withdrawn** | D-37: no stored mapping exists; `identity_permissions` and `identity_role_permissions` were dropped. The mapping is `Permission.grantedByPlatformRoles` and identity's `OrganizationRole` / `EventRole`. |
| **R4** implication expander (`manage` ⇒ CRUD) | **Withdrawn** | D-37 and D-59: codes are not `module:verb` with an aggregate verb, so there is nothing to expand. Role sets are written out in code and reviewed in the diff. |
| **R5** union across roles, fail closed | **Stands** | Realised by the single platform-role model (P3) and the pure decider (P4). |
| **R6** inactive role / permission | **Withdrawn** | D-37: nothing is stored, so nothing has an `active` flag. A code is retired by a release. |
| **R7** per-role cache | **Withdrawn** | D-37: no stored mapping to cache. The decision cache of ET-ORG-003 R7 is separate and deferred until a measured p99 above 20 ms (P6). |
| **R8** `SUPER_ADMIN` edits with reason and change log | **Withdrawn** | D-37: the mapping is changed by a release, whose record is version control. |

Interpretation recorded in the original ("no view for role permissions" means no `$lookup` on the
authorization path) is now moot: there is no stored mapping to read.

## R0 · Reconcile

```bash
grep -rn 'Permission\b' backend/shared-library backend/identity-service --include='*.java' | grep -v /src/test/
grep -rn 'hasAuthority\|hasRole\|@PreAuthorize\|@RequiresPermission' backend --include='*.java' | grep -v /src/test/
grep -rn '"[a-z_]*:[a-z_]*"' backend --include='*.java' | grep -v /src/test/ | grep -v 'security/Permission.java'
```

Classify. The rows that decide the size of this slice:
- Which `Permission` codes are enforced, and which are declared and checked nowhere. Known at the
  time of D-54/D-55: `bank:manage`, `promotion:manage` and `organization:view` were declared and
  unchecked; `escrow:read` is checked in booking but is not a catalogue code (P1).
- How many places hold a platform-role hierarchy (expected three: `Permission.grantedByPlatformRoles`,
  `StaffRole.covers`, `AuthDirective.checkRoleHierarchy`). They are reduced to one in P3.
- Is any permission spelled as a string outside the enum? `PermissionNameLintTest` already fails the
  build on it; confirm it still passes before P1.

## A · Backend (build order P1 → P8)

### BE-1 · P1 — Characterization and defects
- **Spec** R2, R5 · **depends** R0 · **parallel-safe** no
- **Characterize first**: pin today's behaviour with tests before changing it, so a refactor in P2–P4
  cannot move a decision unnoticed.
- Remove the **dead `"escrow:read"` check** in booking `EscrowServiceImpl` — a string that is not a
  catalogue code can never be granted, so the check fails closed for everyone but a platform
  superuser and protects nothing.
- Guards take a `Permission`, **not a `String`**; a string overload is deleted, not deprecated.
- `Organization.canPerform` is made **exhaustive** over `Permission` (a `switch` with no default), so
  adding a code is a compile error until its organization behaviour is decided.
- **Pin the delegation rule (D-58)**: only a permission the actor holds may be granted *or denied*
  to a member. Both directions are asserted, including the denial case.
- **Acceptance** characterization suite green before and after; no string-typed permission guard
  remains; `canPerform` has no default branch; delegation tests cover grant and deny.

### BE-2 · P2 — Typed `Module` and `Action` on `Permission`
- **Spec** R1 · **depends** BE-1 · **parallel-safe** no *(every service imports the enum)*
- **Files** `backend/shared-library/src/main/java/com/pml/shared/security/`
- Each constant carries its resource (module) and action as typed values; the code string is
  derived, never typed twice.
- **Acceptance** a grammar test accepts every constant's code and asserts: exactly one colon, no
  `.`, no `*`, lower-case snake-case parts; parsing a code is total and round-trips; two constants
  never share a code; the existing 30 codes are unchanged.

### BE-3 · P3 — One platform-role model and `Authorities` constants
- **Spec** R5 · **depends** BE-2 · **parallel-safe** no
- Replace the **three hierarchies** — `Permission.grantedByPlatformRoles`, `StaffRole.covers`,
  `AuthDirective.checkRoleHierarchy` — with one `PlatformRole` model and an `Authorities` constants
  class. The other two become readers of it.
- Role names appear as constants, never as literals (`SUPER_ADMIN`, `ADMIN`, `FINANCE`,
  `FINANCE_LEAD`, `ORGANIZER`, `CUSTOMER`).
- **Acceptance** exactly one place decides "does platform role X cover Y"; a test asserts the three
  former call sites return the same answers as before for every (role, permission) pair; no role
  string literal remains in production source.

### BE-4 · P4 — `PermissionDecider`, `DecisionContextLoader`, `EffectivePermissions`, `DelegationPolicy`
- **Spec** R5 · **depends** BE-3 · **parallel-safe** no
- `PermissionDecider` is a **pure function** of an already-loaded context and a permission: no I/O,
  no clock read, never throws. It returns the outcome and the deciding step (ET-ORG-003 §4).
- `DecisionContextLoader` does the reads (membership, grant, organization settings and status) and
  hands the decider a value.
- `EffectivePermissions` (what `myEffectivePermissions` returns) is **derived by running the
  decider over every catalogue code**, never computed by a second path — a second path is a second
  resolver.
- `DelegationPolicy` holds the D-58 rule in one place.
- **Acceptance** every ET-ORG-003 decision-table row asserts outcome and step with no database;
  the effective set equals the decider's answer for every code; any failure while loading denies;
  union across platform roles, order-independent under permutation, and no roles denies.

### BE-5 · P5 — Enforcement closure
- **Spec** R2 · **depends** BE-4 · **parallel-safe** yes *(one service per agent)*
- **Enforce what was declared and unchecked**: `bank:manage` per **D-54** (Owner and Admin, whatever
  the payout switch says; `adminsCanRequestPayouts` governs only `payout:request`);
  `promotion:manage` and `organization:view` per **D-55**. A non-member or removed member sees
  *not found* on a profile read.
- **Platform-only codes** get a method-level `@RequiresPermission` on the specific operation, per
  **D-60**: `payout:approve`, `organization:suspend`, `organization:approve`, `platform:configure`,
  `transaction:recover`, `audit:view`. They resolve against the single platform-role model; the
  `@auth` directive keeps role gates only.
- **`PermissionEnforcementTest`, both directions**: every `Permission` constant is enforced at least
  once *or* appears in the explicit role-gated register; every enforced code is a constant. A code
  in neither set fails the build.
- **Acceptance** the test above; each newly enforced code has an allow and a deny integration test;
  `FINANCE` still holds no event-editing permission.

### BE-6 · P6 — Shared `IdentityAuthorizationClient`, check-many, latency first
- **Spec** R5 · **depends** BE-4 · **parallel-safe** yes
- One client in the shared library for catalog and booking, over `/api/internal/authorization/*`
  (`check`, `event-access`, `check-same-organization`, `user-organizations`, `organization-name`);
  a `check` that takes many (permission, resource) pairs so a list of 50 events is one call.
- An unreachable identity-service **denies**; the client never guesses.
- **Measure p99 resolution latency before any cache is considered.** A cache is added only if the
  measured p99 exceeds 20 ms warm (ET-ORG-003 R7); until then none exists.
- **Acceptance** both services use the one client; a source scan finds no second HTTP client for
  these paths; a stopped identity-service denies a mutation that would otherwise succeed; p99 is
  recorded in the task notes.

### BE-7 · P8 — Remove retired remnants *(only after the migration is confirmed)*
- **Spec** R2 (D-37) · **depends** BE-5, BE-6 · **parallel-safe** no
- **Gate on confirmation**: the migration that dropped the stored mapping has run in every
  environment, checked with the MongoDB MCP before any deletion.
- Remove `IdentityCollections.PERMISSIONS` and `ROLE_PERMISSIONS`; the two `_class` rewrites that
  mention them; the rename entries; the now-empty `MongoSchemaValidationConfig` section; and the
  `EventNameClosureTest` allowance for `identity.RolePermissionsChanged`.
- **Acceptance** no reference to the retired collections or event remains (`grep` clean); the
  closure test passes without its allowance; a fresh database and a migrated one end in the same
  state.

## B · Contract

### GQL-1 · Compose, generate, verify roles
- **depends** BE-4 · **parallel-safe** no
- **Surface as built**: `myPermissions` returns `{ permissions, roles }`;
  `myEffectivePermissions(organizationId, eventId)` returns the resolved set and the deciding step;
  `currentUserPermissions` returns platform codes alone; `permissions`, `permission(code)` and
  `rolePermissions(role)` read the in-code catalogue. **No mutation edits permissions.**
- `compose-supergraph.sh --static` → `npm run codegen` → commit; **restart the local router**.
- **Acceptance** the operations appear in `docs/FRONTEND_GRAPHQL_CONTRACT.md` with their roles.

## C · Frontend — reading, not resolving

**No screen for the evaluator.** Its effects appear everywhere as enabled and disabled affordances;
its algorithm appears nowhere. *(FE-2, FE-3, FE-4 are withdrawn with R3, R6, R8; there is nothing to
edit, no history to show and no unmapped or orphaned stored state.)*

### FE-1 · Role matrix — **read-only**
- **depends** GQL-1 · **parallel-safe** no
- Roles down, permission groups across, from `rolePermissions` / `permissions`. Platform roles and
  organization and event roles shown as the code defines them, including the two conditional cells
  (`financial:view` for `MANAGER`, `payout:request` for `ADMIN`) marked as setting-dependent.
- **No edit affordance of any kind** — not disabled checkboxes, none. The mapping changes by release.
- Wide matrix scrolls inside its own container; the page body never scrolls horizontally.
- **testids** `role-matrix`, `role-row`, `permission-cell`, `permission-cell-conditional`, `permission-group`

### FE-5 · Typed, server-driven gates; generated `PermissionCode`
- **depends** GQL-1, [`ET-ORG-003`](ET-ORG-003.md) FE-2 · **parallel-safe** no
- `PermissionCode` is a union type **generated from `Permission`**, not hand-written. `PermissionGate`
  accepts only a `PermissionCode`, so a typo is a build error rather than a silently-false check.
- One fetch per session of `myPermissions`; per-context checks use `myEffectivePermissions`. The
  server returns the resolved answer and the client reads it.
- **Do not implement precedence or role hierarchy in TypeScript.**
- **Acceptance** a gate on a non-existent code fails the build (asserted by a build test); the
  generated file matches the enum (checked in CI); no client precedence logic, by lint rule.

## D · Tests

### TS-1 · Grammar and catalogue *(L1)*
Every constant's code matches the grammar; one colon, no `.`, no `*`; typed module and action
reproduce the code; no duplicates; the code set equals the documented 30 plus any added by an
approved decision.

### TS-2 · Role model *(L1)*
One platform-role model; the three former hierarchies agree for every (role, permission) pair;
union across roles, order-independent; no roles denies; the decider never throws on any input.

### TS-3 · Enforcement closure *(L3 + L4)*
`PermissionEnforcementTest` both ways: enforced-but-undeclared and declared-but-unenforced-and-
unregistered each fail. Each of the D-54, D-55 and D-60 codes has allow and deny integration
tests on Testcontainers.

### TS-4 · ET-ORG-003 regression *(L1/L3)*
The decision table re-run with the D-56 rows removed and the D-57 platform-bypass row added;
every row asserts the same outcome and deciding step as the spec states.

### TS-5 · OWASP cases *(L3)*
- **A01 Broken access control** — member removed mid-session denied on next request; a non-member
  reads a profile and sees *not found*, not *forbidden*; a delegate cannot grant a permission they
  lack; a delegate cannot deny one they lack (D-58); cross-organization access refused.
- **A04 Insecure design** — an unreachable identity-service denies; an unknown code denies and is
  counted; adding a constant without deciding `canPerform` does not compile.
- **A05 Security misconfiguration** — every `/api/internal/**` path requires its scope; a method
  with `@RequiresPermission` and no authenticated caller is refused, not skipped; no `*` code
  accepted anywhere.
- **A08 Software and data integrity** — the generated `PermissionCode` equals the enum; the
  migration that dropped stored mappings leaves no document a code path still reads.
- **A09 Logging and monitoring** — every platform-role allow into an organization, including a
  suspended one (D-57), writes an audit row naming actor, permission, resource and organization; a
  denied delegation is logged.

### TS-6 · e2e *(L5, admin)*
- Matrix: loading, empty, error, populated; conditional cells marked; **no edit affordance** for
  any role including `SUPER_ADMIN`.
- A gate keyed on a non-existent code is a compile error.
- Compliance: teal, Inter, tokens, `data-brand="admin"`.

## E · Gate

- [x] **Spec `approved`** (2026-08-18; narrowed 2026-09-18 by D-37 and 2026-10-13 by D-54…D-60)
- [x] R0 recorded; every string-typed permission and every dead check classified (F-069)
- [x] P1 characterization suite in place (`PermissionDecisionTableTest`); `escrow:read` removed; guards take `Permission`; `canPerform` exhaustive
- [x] Delegation rule D-58 pinned (`delegationIsBoundedInBothDirections`)
- [x] `Permission` carries a typed action and a grammar check at class load; grammar test green (one colon, no dots, no `*`)
- [x] One platform-role model replaces the three hierarchies; no role literal in production source (`AuthorityLiteralLintTest`)
- [x] Pure `PermissionDecider`; `EffectivePermissions` derived from it; `DelegationPolicy` single-sourced
- [x] `PermissionEnforcementTest` green; one registered exception (`platform:configure`, open item 1 of F-069)
- [x] `bank:manage` (D-54), `promotion:manage` and `organization:view` (D-55) enforced with allow and deny tests
- [x] `@RequiresPermission` on payout approval, organization approve and suspend, transaction recovery, audit read **and platform configuration** (super administrator only, F-072); every service gate is a catalogue permission and the role-literal budgets are 0
- [x] Platform roles bypass the organization status gate (tested) **and every state-changing allow is audited**: `RequiresPermissionAspect` records `PLATFORM_PERMISSION_EXERCISED` with the permission, role, actor and service to the service's `PlatformWideAuditSink` (`RequiresPermissionAspectTest`, `PlatformWideAuditEndToEndTest`); views are not recorded, a failing sink does not block
- [x] Shared `IdentityAuthorizationClient` with `check-many` (`POST /api/internal/authorization/check-many`, 100 per call, answered in order); unreachable identity denies, per entry for a batch; catalog, booking and `RemoteTenantMemberships` use it and `AuthorizationClientSingleLintTest` keeps a second client out
- [x] p99 measured before any cache: 1 000 decisions against a real MongoDB p50 0.78 ms, p99 2.25 ms (`PermissionDecisionTableTest.decisionLatencyIsMeasured`); `identity.authorization.decision` timer with p50/p95/p99 in production; no cache built
- [x] Frontend `PermissionCode` generated and kept current by a test; a bad code is a compile error
- [x] Role matrix is read-only and covered by Playwright (`gating.spec.ts`: no edit controls for super admin or admin; finance has no access)
- [x] No client-side precedence or hierarchy logic
- [x] Retired remnants removed after the migration was confirmed on the dev database (no `*permissions` collection remained): the two collection constants, the `_class` rewrites, the rename entries and the `RolePermissionsChanged` allowance and spec row; the migration keeps dropping them by name in any environment that still has them
- [x] OWASP A01 / A04 / A05 / A08 / A09 cases pass (`PermissionGuardMisconfigurationTest` for A05 and A09; the aspect's audit tests for A09)
- [x] `mvn -q -f backend verify -Dgroups=ET-PLT-013 -DfailIfNoTests=false` green (2026-10-13)
- [x] Spec `status:` → `implemented` (2026-10-15)
