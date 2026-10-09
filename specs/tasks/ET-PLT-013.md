# ET-PLT-013 · The permission engine — tasks

> **Spec** [`specs/_platform/013-permission-engine/spec.md`](../_platform/013-permission-engine/spec.md) · **Wave 1** · `blocked_by:` ET-PLT-002, ET-PLT-005, ET-PLT-007
> **Status** `approved` — cleared to build
> **Screens** the role-permission configuration surface in `Admin - Platform Configuration.dc.html` / `Admin - Users & Organizations.dc.html`. **No screen for the resolver itself.**
> **Verify** `mvn -q -f backend/identity-service test -Dgroups=ET-PLT-013 -DfailIfNoTests=false` · `mvn -q -f backend/shared-library test -Dgroups=ET-PLT-013 -DfailIfNoTests=false` · `compose-supergraph.sh --static`

**This spec amends [`ET-ORG-003`](ET-ORG-003.md) R6.** Its fixed `Permission` enum of 30 constants
is replaced by the flat `module:action` catalogue defined here. ET-ORG-003's six-step precedence,
its explicit-deny-beats-inherited-allow rule, its audit of every step-1 allow, and its
single-implementation constraint are **unchanged and remain authoritative**. The two compose:
ET-PLT-013 answers *what a role may do*; ET-ORG-003 answers *which source decides*.

> **Interpretation recorded** — "no view for role permissions" is read as *no MongoDB view and no
> `$lookup` on the authorization path* (the shape a relational design would need). An **admin
> configuration UI is in scope**, because the platform administrator must configure the mapping in
> full. If that reading is wrong, FE-1…FE-4 are what change.

## R0 · Reconcile

The persistence baseline already registers `identity_permissions` and `identity_role_permissions`
(previously attributed to ET-ORG-003; **now attributed to this spec**, and
`identity_role_permission_changes` added alongside them).

```bash
grep -rn 'Permission\b' backend/shared-library backend/identity-service --include='*.java' | grep -v /src/test/
grep -rn 'hasAuthority\|hasRole\|@PreAuthorize' backend --include='*.java' | grep -v /src/test/
```

Classify. The rows that will decide the size of this slice:
- Does a fixed `Permission` enum exist? It is **superseded**, not deleted — record every constant
  and map it onto a flat `module:action` key so no authority is silently dropped in translation.
- Is any role list stored in MongoDB? **Keycloak is the sole source of truth for roles** — a
  MongoDB role collection is `contradicted`.
- Is any permission string a wildcard? Wildcards are refused, not interpreted.

## A · Backend

### BE-1 · `PermissionKey`, the `Action` vocabulary and the module registry
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** no *(every service imports it)*
- **Files** `backend/shared-library/src/main/java/com/pml/shared/auth/`
- Grammar `^[a-z][a-z0-9_]+:[a-z][a-z0-9_]+$` — **exactly one colon, and no dots.** A key is two
  parts, always, so `split(':')` is the entire parser.
- A finer subject is its **own flat module** — `ticket_tiers`, not `events.tiers`; `ticket_qr`, not
  `tickets.qr`; `bank_accounts`, not `payouts.accounts`. A dotted grammar forces every reader to
  know that the *last* dot is the special one, and produces keys nobody can scan at a glance.
- **Acceptance** the grammar accepts every catalogue key and rejects every wildcard **and every
  dotted key**; `parse` is **total** — every accepted key round-trips to the same string.

### BE-2 · The code-declared registry and the catalogue bootstrapper
- **Spec** R2 · **§5** T2 · **depends** BE-1 · **parallel-safe** no
- **Acceptance** bootstrapping twice produces identical rows; **no `createPermission` mutation
  exists**; an administrator may edit only `description` and `active`.
- **The load-bearing test: every registry key is enforced by at least one `@auth` field or method
  annotation**, asserted by scanning both sets. A permission nobody enforces looks like protection
  and is not — it appears in the admin picker, gets granted, and protects nothing.
- Same insert-never-update discipline as [`ET-PLT-014`](ET-PLT-014.md) BE-3: a description an
  administrator rewrote must survive a deployment.

### BE-3 · `identity_role_permissions` — one document per role
- **Spec** R3 · **§5** T3 · **depends** BE-2 · **parallel-safe** no
- `_id` is the **lower-cased Keycloak role name**. `grants` is an array of catalogue keys.
- **Acceptance** resolution issues **exactly one query** per uncached role, asserted by query
  count; **no `$lookup`, aggregation or view participates**; **no `identity_roles` collection
  exists**; a grant change is a single-document update needing no transaction; a role with no
  document resolves to the empty set rather than erroring; an unknown key refuses at write time
  with `PERMISSION_UNKNOWN`.
- Why this shape and not three collections: a role's grants are read together, change together and
  number in the dozens. Embedding them removes the join a relational design needs a view for, and
  makes the edit atomic without a transaction.

### BE-4 · `PermissionExpander` and its idempotence test
- **Spec** R4 · **§5** T4 · **depends** BE-1 · **parallel-safe** no *(every service imports it)*
- **Files** `backend/shared-library/.../auth/PermissionExpander.java` — stateless, no dependencies,
  no I/O. Not a Spring bean.
- **Two rules and no third**: `manage` ⇒ CRUD, and `create|update|delete` ⇒ `read`.
- **Acceptance** both rules; **`manage` implies no cross-cutting verb, asserted per verb**;
  `expand(expand(x)) == expand(x)` over 1,000 randomised catalogue subsets; expansion introduces
  no key absent from the catalogue **and no key containing a `.`**.
- The per-verb assertion matters: `payouts:manage` must **not** confer `payouts:approve`. Reviewing
  someone else's payout request is a different authority from editing one, and collapsing them
  destroys the dual control [`ET-FIN-003`](ET-FIN-003.md) BE-5 depends on.

### BE-5 · The evaluator, the role union and the fail-closed paths
- **Spec** R5, R6 · **§5** T5 · **depends** BE-4 · **parallel-safe** no
- **Acceptance** roles read **only** from `realm_access.roles`; no roles → deny; the result is the
  **union** across roles, not the intersection (test it where each role alone is insufficient);
  order-independent under permutation; a denied check performs no extra repository call; **the
  evaluator never throws — an internal failure denies**.
- Inactive role or inactive permission grants nothing, and deactivation takes effect within the
  cache TTL with no deployment.

### BE-6 · The per-role cache, non-cached failures, cross-instance eviction
- **Spec** R7 · **§5** T6 · **depends** BE-5 · **parallel-safe** no *(cache and eviction together)*
- **Acceptance** the key is the lower-cased role name, so token/store casing collapses to one
  entry; TTL `PT5M`; **a failed load is not cached** and the next call succeeds; a mapping change
  publishes `identity.RolePermissionsChanged` and every instance evicts within `PT5S`; a Redis
  flush grants nothing that is not in the store; **10,000 principals over 5 roles hold 5 entries**.
- Two traps worth naming because both are easy to reintroduce:
  - **Caching by principal** — unbounded memory, and a role change evicts nothing useful.
  - **Caching a failure** — a transient store blip would strip a role of every permission for the
    whole TTL.
- If the cache is a Spring `@Cacheable` proxy, **the call must cross a bean boundary.** A sibling
  method calling `this.getPermissionsForRole(...)` bypasses the proxy and silently disables caching
  entirely — the failure looks like latency, not like a bug.

### BE-7 · Administration — `SUPER_ADMIN` writes, mandatory reason, append-only change log
- **Spec** R8 · **§5** T7 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** `ADMIN` reads and cannot write; a reasonless change refuses; every change appends
  actor, role, added, removed, reason, instant; **no change row is ever updated or deleted**;
  grants as at a past instant are recoverable; **granting a key the actor does not hold is
  permitted and audited** — a platform administrator configures roles they do not occupy; a change
  takes effect on live sessions within the eviction budget.

### BE-8 · Amend [`ET-ORG-003`](ET-ORG-003.md) — the resolver consumes this catalogue
- **Spec** R5 · **§5** T8 · **depends** BE-5 · **parallel-safe** **no — it amends another spec's core**
- Steps 1 (platform role) and 3 (organization role) read the expanded set from this engine. Steps
  2 (event grant), 4 (custom) and 5 (explicit deny) are unchanged.
- **Acceptance** precedence and explicit-deny behaviour **unchanged**, proven by re-running
  ET-ORG-003's decision table; permission resolution still exists in **exactly one**
  implementation; ET-ORG-003's audit of every step-1 allow still fires.
- Update [`ET-ORG-003`](../organization/003-permission-resolution/spec.md) §3 R6 and its task file
  to reference this catalogue rather than the 30-constant enum, so the two specs cannot disagree.

### BE-9 · The subgraph half, `myPermissions`, and the generated frontend catalogue
- **Spec** R2, R8 · **§5** T9 · **depends** BE-7 · **parallel-safe** **no — shared identity SDL**
- 6 queries, 3 mutations; every admin field `@tag(name: "admin")`.
- **Acceptance** static composition; **the frontend catalogue is generated from the registry, not
  hand-written**; no client implements precedence.
- Generating it is the improvement over a hand-mirrored catalogue: two files that mirror each other
  by discipline drift the first time someone adds a key to one.

## B · Contract

### GQL-1 · Compose, generate, verify roles
- **depends** BE-9 · **parallel-safe** no
- `compose-supergraph.sh --static` → `npm run codegen` → commit; **restart the local router**.
- **Acceptance** all nine operations appear in `docs/FRONTEND_GRAPHQL_CONTRACT.md` with their roles.

## C · Frontend — configuration, not resolution

**No screen for the evaluator.** Its effects appear everywhere as enabled and disabled affordances;
its algorithm appears nowhere.

### FE-1 · Role → permission matrix
- **depends** GQL-1 · **parallel-safe** no
- Roles down (from `keycloakRoles`, read **live from Keycloak** — never a stored list), permission
  groups across. Grouped by the §4 module groups, from `permissionCatalogue`.
- **Show granted keys and implied keys differently.** An administrator granting `events:manage`
  must see that `events:read` is now held without having been ticked — otherwise they tick all
  five and the compact grant the engine was designed around never gets used.
- Wide matrix scrolls inside its own container; the page body never scrolls horizontally.
- **testids** `role-matrix`, `role-row`, `permission-cell`, `permission-cell-implied`, `permission-group`

### FE-2 · Editing a role's grants
- **depends** FE-1 · **parallel-safe** no
- `SUPER_ADMIN` only — an `ADMIN` sees the matrix with **no edit affordance**, not disabled
  checkboxes.
- Reason mandatory. The confirmation names **what is being added and removed**, and states that
  holders are affected on their next request.
- Granting `payouts:approve`, `refunds:manage`, `configuration:configure` or `permissions:manage`
  is called out as consequential before it is applied.
- **testids** `role-grants-edit`, `role-grants-reason`, `role-grants-diff-added`, `role-grants-diff-removed`, `role-grants-confirm`, `role-matrix-readonly`

### FE-3 · Change history
- **depends** BE-7 · **parallel-safe** yes
- Per role: added, removed, actor, reason, time. Append-only, **no edit affordance** — the same
  discipline as the ledger ([`ET-FIN-001`](ET-FIN-001.md) FE-3) and the config log
  ([`ET-ADM-002`](ET-ADM-002.md) FE-3).
- **testids** `role-history-row`, `role-history-added`, `role-history-removed`, `role-history-reason`

### FE-4 · Unmapped and orphaned states
- **depends** FE-1 · **parallel-safe** yes
- A Keycloak role with **no mapping** is shown as holding nothing, plainly — it is the fail-closed
  default and an administrator must be able to see it rather than infer it from an empty row.
- A catalogue row present in the store but absent from the code registry is marked **orphaned** and
  is **not grantable** (BE-2).
- **testids** `role-unmapped`, `permission-orphaned`

### FE-5 · `myPermissions` drives every affordance, in all three apps
- **depends** GQL-1, [`ET-ORG-003`](ET-ORG-003.md) FE-2 · **parallel-safe** no
- One fetch per session, consumed by the existing `PermissionGate` in the shared barrel.
- **The generated catalogue gives compile-time key checking** — a typo in a gate is a build error,
  not a silently-always-false check. That is the single biggest practical win of a generated
  catalogue over string literals.
- **Do not implement expansion or precedence in TypeScript.** The server returns the expanded
  union; the client reads it.
- **Acceptance** no client-side precedence or expansion logic exists, asserted by a lint rule in
  the compliance suite.

## D · Tests

### TS-1 · Grammar *(L1)*
Every catalogue key parses; **wildcards and dotted keys both refused**; `parse` total; the action is
always a member of the closed vocabulary; `split(':')` yields two parts for every key in the
catalogue.

### TS-2 · Catalogue *(L3 + L4)*
Bootstrap idempotent. **Registry ↔ annotation equality**: every declared key is enforced somewhere,
and every enforced key is declared. Both directions — an enforced-but-undeclared key cannot be
granted, and a declared-but-unenforced key is a lie.

### TS-3 · Mapping shape *(L3)*
One query per role, no `$lookup`, no `identity_roles` collection. Unknown key refused at write.
Missing document → empty set. Confirm the indexes live via **MongoDB MCP** `collection-indexes`.

### TS-4 · Expansion *(L1)*
Both rules; `manage` implies no cross-cutting verb, **per verb**; idempotent over 1,000 randomised
subsets; introduces no uncatalogued key and no dotted key.

### TS-5 · Evaluation *(L1/L3)*
Union not intersection; order-independent; no roles → deny; inactive role and inactive permission
each grant nothing; the evaluator never throws.

### TS-6 · Cache *(L3)*
10,000 principals / 5 roles → 5 entries. Failed load retried, not cached. Mapping change evicts
across instances within `PT5S` (run two instances). Redis flush grants nothing.
**Assert the proxy actually fires** — a self-invocation would silently disable caching, so assert
on the query count, not on latency.

### TS-7 · Administration *(L3)*
`ADMIN` cannot write; reasonless change refuses; change rows immutable; past grants recoverable;
a live session is affected within the eviction budget.

### TS-8 · ET-ORG-003 regression *(L1/L3 — the amendment's safety net)*
**Re-run ET-ORG-003's full decision table unchanged.** Every row must assert the same outcome
**and the same deciding step** as before this amendment. If any row changes, the amendment has
altered precedence, which it must not.

### TS-9 · e2e *(L5, admin)*
- Matrix: granted vs implied visually distinct; loading, empty, error, populated.
- `ADMIN` sees no edit affordance; `SUPER_ADMIN` does.
- Edit shows an added/removed diff and demands a reason.
- Unmapped role and orphaned permission states render.
- A gate keyed on a non-existent permission is a **compile error**, asserted by a build test.
- Compliance: teal, Inter, tokens, `data-brand="admin"`.

## E · Gate

- [x] **Spec `approved`** (2026-08-18)
- [ ] R0 recorded; every old `Permission` constant mapped to a flat `module:action` key, none dropped
- [ ] No MongoDB role collection; Keycloak is the sole source of truth for roles
- [ ] Grammar closed and **flat**: exactly one colon, no dots; wildcards and dotted keys both refused, not interpreted
- [ ] Every finer subject is its own module (`ticket_tiers`, `ticket_qr`, `bank_accounts`, `user_phone`), never a dotted path
- [ ] Registry ↔ annotation equality asserted in both directions
- [ ] One document per role; **one query, no `$lookup`, no view** on the authorization path
- [ ] `manage` implies CRUD and **no** cross-cutting verb, asserted per verb
- [ ] Expansion idempotent over 1,000 randomised cases
- [ ] Union across roles; evaluator never throws; every failure path denies
- [ ] Cache keyed by role; failures never cached; 5 entries for 10,000 principals
- [ ] Cross-instance eviction within `PT5S`; Redis flush grants nothing
- [ ] `SUPER_ADMIN` writes with a mandatory reason; change log append-only and time-queryable
- [ ] **ET-ORG-003's decision table re-run unchanged — same outcome and same deciding step on every row**
- [ ] ET-ORG-003 §3 R6 and its task file updated to reference this catalogue
- [ ] Frontend catalogue **generated**, not hand-written; a bad key is a compile error
- [ ] No client-side expansion or precedence logic
- [ ] `mvn -q -f backend verify -Dgroups=ET-PLT-013 -DfailIfNoTests=false` green
- [ ] Spec `status:` → `implemented`
