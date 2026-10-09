# ET-PLT-013 · The permission engine — catalogue, role mapping and evaluation

> **Conformance** · US Part IV §22 permission resolution algorithm · V3 §3 account access

> **Superseded in part, 2026-09-18 — ROADMAP D-37.** The role → permission mapping is fixed in
> software and changes only with a release. What this spec calls an administrator-edited mapping —
> R3's stored role documents, R7's per-role cache, R8's change log, and the `setRolePermissions`,
> `setRolePermissionActive` and `setPermissionActive` mutations — will not be built, and the
> `identity_permissions` / `identity_role_permissions` collections were dropped. What stands:
> R1 and R2, realised as `com.pml.shared.security.Permission` (30 codes, one colon each, no runtime
> extension), with the platform-role sets on the same enum and the organization and event sets on
> identity's `OrganizationRole` and `EventRole` ([ET-ORG-003 §4](../../organization/003-permission-resolution/)).

## 1. Capability

A role is a name. It carries no meaning until something says what a holder of that name may do,
and the platform has three services that must agree on that answer for every request they serve.
This spec builds the thing that answers it: a **closed catalogue of permissions**, a **mapping from
role to permission** that a platform administrator owns and edits without a deployment, and an
**evaluator** that turns a token into a decision in constant time.

The division of authority is the whole design. **Keycloak owns roles** — it mints them, it puts
them in the token, and it is the only place a user is granted or denied one. **MongoDB owns the
mapping** — which permissions a role carries, edited by a platform administrator, versioned and
audited. Neither store duplicates the other's job. A role that exists in Keycloak with no mapping
carries no permissions; a mapping for a role Keycloak has never heard of grants nothing to anyone.

Permission keys are written `module:action` — `events:create`, `payouts:approve`,
`ticket_qr:read`. **Exactly one colon, and no dots.** A key has two parts and only ever two, so
there is nothing to parse and nothing to get wrong. Where a finer subject is needed it becomes its
own flat module — `ticket_tiers`, `bank_accounts`, `user_phone` — rather than a dotted path,
because a nested grammar invites arguments about where the boundary sits and produces keys nobody
can scan at a glance. A closed verb vocabulary keeps the catalogue from sprawling into a thousand
one-off strings that nobody can audit.

An aggregate verb, `manage`, implies the four CRUD verbs, and any write verb implies `read`. This
lets a role be **stored compactly** — one grant of `events:manage` rather than four rows — while a
fine-grained check for `events:update` still resolves exactly. Expansion happens once, per role,
on load; evaluation is then a set membership test.

## 2. Design decisions

**`module:action`, two parts, one colon, no dots.** A grammar with sub-paths separates subject and
verb by the *last* occurrence of the character it also uses inside the subject, so every reader and
every parser has to know the last one is special. Flat modules remove the question entirely:
`split(':')` yields two parts, always. Where a finer subject is genuinely needed it becomes its own
module — `ticket_tiers`, not `events.tiers` — which also makes the catalogue a flat list an
administrator can read.

**Keycloak is the sole source of truth for roles; MongoDB never stores a role list.** Two stores
that both believe they define roles will disagree, and the disagreement surfaces as a user who has
a role in the token and no permissions, or permissions for a role that was deleted last month. The
mapping document is keyed by the Keycloak role name and is the *only* thing MongoDB asserts.

**One document per role, holding its grants as an array — no junction collection and no view.** The
relational shape is three tables and a join, and in MongoDB that join is a `$lookup` on the hottest
authorization path in the platform. A role's grants change together, are read together, and number
in the dozens; they are one document. Resolution becomes a single `findById`, and a grant change is
a single-document atomic update needing no transaction.

**The catalogue is a separate collection and is never read on the hot path.** It exists to validate
what may be granted and to render the administrator's picker. Resolution reads the role document
and nothing else.

**A code-declared registry validates the catalogue, and the catalogue validates the grants.** The
set of legal `module:action` keys is declared in code, because a permission is enforced by an
annotation somewhere and a key nobody enforces is a key that lies. An administrator may not invent
a permission; they may only decide which roles hold the ones that exist.

**`manage` implies CRUD; any write implies read. Two rules, and no third.** Stored compactly,
evaluated precisely. Without implication, every role document lists four keys per module and the
administrator maintains a matrix by hand. A sensitive subject that needs its own read gate becomes
its own module — `ticket_qr`, `user_phone` — so it obeys the same two rules as everything else and
there is no special case to remember.

**Caching is keyed by role, never by principal.** Memory is then bounded by the number of roles —
dozens — rather than by the number of users, and one role's expansion is shared by every holder.

**A failed load is never cached.** A transient store error denies the check in flight and is
retried on the next one. Caching the failure would strip a role of its permissions for the whole
TTL because the database blinked.

**Fail closed, everywhere.** No mapping, no roles in the token, an unreachable store, an inactive
role, an inactive permission — every one of them yields the empty set, and the empty set denies.

**Only a platform administrator writes the mapping, and every write is versioned and audited with a
reason.** Granting `payouts:approve` to a role is the most consequential edit available in the
product; it must be attributable months later.

**Rejected alternatives**

- *Roles defined in MongoDB.* A second source of truth for the thing the token already asserts.
- *A `role_permissions` junction collection.* Relational modelling in a document store; it buys a join and nothing else.
- *A materialised view of role × permission.* MongoDB's equivalent is a `$lookup` or a maintained duplicate; the embedded array is both, for free, and cannot drift from itself.
- *Permissions embedded in the Keycloak role.* Keycloak role attributes are awkward to edit in bulk, invisible to the platform's own audit trail, and would put a product decision inside the IdP.
- *Wildcards such as `events:*`.* Unauditable — nobody can answer "who can cancel an event?" by reading grants.
- *An open permission vocabulary.* A key nobody enforces looks like protection and is not.
- *Caching by principal.* Unbounded memory, and one role change evicts nothing usefully.
- *Per-user permission overrides.* Two mechanisms for one question; the override becomes the real system and the roles become decoration.

## 3. Requirements

### ET-PLT-013-R1 · The key grammar is closed and parses one way

THE SYSTEM SHALL admit only permission keys matching the §4 grammar, built from the §4 module and
action vocabularies.

**Acceptance**
- [ ] A key matches `^[a-z][a-z0-9_]+:[a-z][a-z0-9_]+$` or is refused with `PERMISSION_KEY_INVALID`
- [ ] **A key containing a `.` is refused**; the grammar has no sub-path form
- [ ] Splitting on `:` yields exactly two parts for every key in the catalogue, with no further splitting needed
- [ ] The action part is a member of the §4 closed `Action` vocabulary; anything else is refused
- [ ] No key contains `*`, and a key containing one is refused rather than interpreted
- [ ] The module part of every key names a row of the §4 module registry
- [ ] `PermissionKey.parse` is total: every accepted key round-trips to the same string

### ET-PLT-013-R2 · The catalogue is declared in code and cannot be extended at runtime

THE SYSTEM SHALL derive the set of grantable permissions from the code-declared registry, and
SHALL NOT permit an administrator to create one.

**Acceptance**
- [ ] `identity_permissions` is populated from the §4 registry at boot, inserting rows that are absent and leaving existing rows untouched
- [ ] Running the bootstrap twice produces identical rows
- [ ] No mutation creates a permission; the GraphQL surface exposes no `createPermission`
- [ ] A permission present in the collection but absent from the registry is reported as orphaned and is not grantable
- [ ] Every key in the registry is enforced by at least one `@auth` field or method annotation, asserted by a test that scans both sets
- [ ] An administrator may edit a permission's description and `active` flag, and nothing else

### ET-PLT-013-R3 · One document per role holds its grants, and it is the only mapping

WHEN a role's permissions are resolved, THE SYSTEM SHALL read exactly one `identity_role_permissions`
document and no other collection.

**Acceptance**
- [ ] Resolution issues exactly one query per uncached role, asserted by query count
- [ ] No `$lookup`, aggregation or view participates in resolution
- [ ] No `identity_roles` collection exists
- [ ] `_id` is the lower-cased Keycloak role name; two documents for one role are impossible by construction
- [ ] A grant change is a single-document update and requires no transaction
- [ ] A role with no document resolves to the empty set, not an error
- [ ] Every key in a document's `grants` exists in the catalogue and is `active`; a grant of an unknown key is refused at write time with `PERMISSION_UNKNOWN`

### ET-PLT-013-R4 · Implication expands compact grants into the effective set

WHEN a role's grants are loaded, THE SYSTEM SHALL expand them by the §4 implication rules, and the
expansion SHALL be idempotent.

**Acceptance**
- [ ] `module:manage` yields `module:create`, `module:read`, `module:update`, `module:delete`
- [ ] `module:create`, `module:update` and `module:delete` each yield `module:read`
- [ ] Cross-cutting verbs — `approve`, `export`, `assign`, `scan`, `refund`, `settle`, `configure` — pass through unchanged and imply nothing
- [ ] `manage` does **not** imply any cross-cutting verb, asserted per verb
- [ ] `expand(expand(x))` equals `expand(x)` for every catalogue subset in a randomised test of 1,000 cases
- [ ] Expansion introduces no key absent from the catalogue
- [ ] Expansion never produces a key containing a `.`

### ET-PLT-013-R5 · Evaluation is a set membership test over the union of the caller's roles

WHEN an operation requires a permission, THE SYSTEM SHALL grant it only if the key is present in
the union of the expanded sets of the caller's roles.

**Acceptance**
- [ ] Roles are read from the token's `realm_access.roles`; no other claim contributes
- [ ] A caller with no roles resolves to the empty set and is denied
- [ ] The union is order-independent, asserted by permuting the role list
- [ ] A denied check performs no repository call beyond the cached resolution
- [ ] The evaluator returns a boolean and never throws; an internal failure denies
- [ ] A caller holding two roles receives the union, not the intersection, asserted by a test where each role alone is insufficient

### ET-PLT-013-R6 · Inactive roles and inactive permissions grant nothing

IF a role mapping or a catalogue row is inactive, THEN THE SYSTEM SHALL exclude it from every
resolution.

**Acceptance**
- [ ] A mapping with `active: false` resolves to the empty set even when its `grants` are non-empty
- [ ] A grant naming a permission whose catalogue row is `active: false` is excluded from the expanded set
- [ ] Deactivating a permission takes effect within the cache TTL without a deployment
- [ ] Deactivation never deletes a row; no mutation deletes a catalogue row or a mapping
- [ ] An inactive permission remains visible to the administrator, marked inactive

### ET-PLT-013-R7 · The cache is per role, self-healing, and evicted across instances

THE SYSTEM SHALL cache the expanded set per role, and SHALL propagate an eviction to every
instance when a mapping changes.

**Acceptance**
- [ ] The cache key is the lower-cased role name; casing differences between token and store collapse to one entry
- [ ] Entries expire after `platform.permissions.cache-ttl` (PT5M)
- [ ] A failed load is not cached; the next call retries and succeeds
- [ ] A mapping change publishes `identity.RolePermissionsChanged` and every instance evicts that role within `platform.permissions.eviction-budget` (PT5S)
- [ ] A Redis flush does not grant a permission that is not in the store, asserted by flushing mid-suite
- [ ] Cache memory is bounded by role count; a test with 10,000 distinct principals over 5 roles holds 5 entries

### ET-PLT-013-R8 · Only a platform administrator changes the mapping, and every change is recorded

WHEN a role's grants change, THE SYSTEM SHALL require `SUPER_ADMIN`, a reason, and SHALL append an
immutable record of the change.

**Acceptance**
- [ ] `setRolePermissions` and `setRolePermissionActive` require `SUPER_ADMIN`; `ADMIN` may read and not write
- [ ] A change with no reason is refused with `COMMAND_NOT_WELL_FORMED`
- [ ] Every change appends a row to `identity_role_permission_changes` naming actor, role, added keys, removed keys, reason and instant
- [ ] No mutation updates or deletes a change row
- [ ] The grants as at a past instant are recoverable from the change log
- [ ] Granting a key the actor does not themselves hold is permitted and audited — a platform administrator configures roles they do not occupy
- [ ] A change to a role held by live sessions takes effect on the next request, within the eviction budget

## 4. Model

> **Reconciliation note, 2026-09-01 — `myPermissions` · **absent**, not renamed.**
> Same finding as [ET-ORG-003](../../organization/003-permission-resolution/spec.md): of the four
> candidates, `allPermissions` and `myEffectivePermissions` have no resolver at all. The catalogue
> this spec defines has no working query surface, so the name is `absent` rather than misnamed.

### The grammar

```
<module>:<action>        events:create · ticket_tiers:update · payouts:approve · ticket_qr:read
```

`^[a-z][a-z0-9_]+:[a-z][a-z0-9_]+$` — modules snake_case, plural where they name a collection of
things. **Exactly one colon and no dots.** A key is two parts, always; `split(':')` is the whole
parser. A finer subject is a module of its own, never a dotted path.

### Actions — the closed vocabulary

| Action | Meaning | Implies |
|---|---|---|
| `create` | bring into existence | `read` |
| `read` | see it | — |
| `update` | change it | `read` |
| `delete` | remove or deactivate it | `read` |
| `manage` | aggregate | `create`, `read`, `update`, `delete` |
| `approve` | decide on someone else's submission | — |
| `export` | remove data from the platform | — |
| `assign` | give it to another actor | — |
| `scan` | validate a ticket at a gate | — |
| `refund` | return money to a buyer | — |
| `settle` | move money out to an organizer | — |
| `configure` | change platform behaviour | — |

The cross-cutting verbs imply nothing and are implied by nothing. `manage` on payouts does not
confer `approve` — reviewing a payout request is not the same authority as editing one, and
collapsing them is how dual control disappears.

### Modules

| Group | Modules |
|---|---|
| Identity | `users`, `user_phone`, `sessions`, `roles`, `permissions` |
| Organization | `organizations`, `organization_kyb`, `members`, `invitations`, `event_grants` |
| Catalog | `events`, `ticket_tiers`, `promo_codes`, `venues`, `reference_data` |
| Ticketing | `tickets`, `ticket_qr`, `reservations`, `transfers`, `checkins` |
| Payment | `payments`, `payment_providers`, `webhooks` |
| Finance | `escrow`, `ledger`, `commission`, `payouts`, `bank_accounts`, `refunds`, `chargebacks`, `reconciliation` |
| Operations | `approvals`, `recovery`, `configuration`, `flags`, `analytics`, `audit`, `observability` |
| Notification | `notifications`, `notification_templates`, `devices` |

### Documents

| Collection | Owning service | Key fields | Notes |
|---|---|---|---|
| `identity_permissions` | identity-service | `_id` (the key), `module`, `action`, `group`, `description`, `active` | the catalogue; bootstrapped from the code registry, never created by an administrator |
| `identity_role_permissions` | identity-service | `_id` (lower-cased Keycloak role name), `roleName`, `grants[]`, `description`, `active`, `updatedBy`, `updatedAt`, `version` | **the mapping — one document per role, and the only join-free read on the authorization path** |
| `identity_role_permission_changes` | identity-service | `_id`, `roleName`, `added[]`, `removed[]`, `reason`, `actorId`, `occurredAt` | append-only |

There is **no** `identity_roles` collection. Keycloak owns roles.

### Indexes

| Collection | Index | Kind | Why |
|---|---|---|---|
| `identity_permissions` | `{ module: 1, action: 1 }` | unique | one row per key, independent of `_id` |
| `identity_permissions` | `{ group: 1, active: 1 }` | compound | the administrator's grouped picker |
| `identity_role_permissions` | `{ active: 1 }` | single | the mapping list |
| `identity_role_permission_changes` | `{ roleName: 1, occurredAt: -1 }` | compound | the history query |

`identity_role_permissions` needs no index for resolution: it is read by `_id`.

### Implication

```
module:manage                      ⇒ module:create, module:read, module:update, module:delete
module:create|update|delete        ⇒ module:read
approve|export|assign|scan|refund|settle|configure   ⇒ nothing
```

Two rules, and no third. A sensitive subject that needs its own read gate is its own module —
`ticket_qr:read`, `user_phone:read` — so it obeys the same two rules as everything else.

Applied once, on load, per role. Idempotent and order-preserving.

### Resolution

```
token realm_access.roles
   │
   ├─ per role → cache hit? ────────────────► expanded set
   │                 │ miss
   │                 ▼
   │      findById(identity_role_permissions, role)   ← ONE query, no join
   │                 │
   │                 ├─ absent or inactive → empty set
   │                 ▼
   │      filter grants by catalogue active
   │                 ▼
   │      PermissionExpander.expand(grants)
   │                 ▼
   │      cache (5 min, failures never cached)
   │
   └─ union across roles ──────────────────► has(key)?
```

### Relationship to [ET-ORG-003](../../organization/003-permission-resolution/)

This spec answers **what a role may do**. ET-ORG-003 answers **which source decides**, in its six
ordered steps. They compose: ET-ORG-003 step 1 (platform role) and step 3 (organization role)
consult this engine for the role's expanded set; steps 2, 4 and 5 — event grant, custom, explicit
deny — remain ET-ORG-003's. Explicit deny still beats inherited allow.

**This spec amends ET-ORG-003 R6.** The `Permission` enum of fixed constants is replaced by the
flat `module:action` catalogue defined here. ET-ORG-003's resolver, its precedence order, its
audit of every step-1 allow and its single-implementation rule are unchanged and remain
authoritative.

### GraphQL

Subgraph `identity`. Every field `@tag(name: "admin")`.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `permissionCatalogue` | query | `ADMIN` | `[Permission!]!` — grouped |
| `rolePermissions(roleName)` | query | `ADMIN` | `RolePermissionMapping` |
| `rolePermissionMappings` | query | `ADMIN` | `[RolePermissionMapping!]!` |
| `keycloakRoles` | query | `ADMIN` | `[String!]!` — read live from Keycloak, never stored |
| `rolePermissionHistory(roleName)` | query | `ADMIN` | `RolePermissionChangePage!` |
| `myPermissions` | query | `AUTHENTICATED` | `[String!]!` — the caller's expanded union |
| `setRolePermissions(input)` | mutation | `SUPER_ADMIN` | `RolePermissionMapping!` |
| `setRolePermissionActive(input)` | mutation | `SUPER_ADMIN` | `RolePermissionMapping!` |
| `setPermissionActive(input)` | mutation | `SUPER_ADMIN` | `Permission!` |

`myPermissions` is the only non-admin field, and it returns the caller's own set so a client can
render affordances from one fetched answer rather than reimplementing the algorithm.

### Events

| Tier | Java type | Wire name | Topic | Consumers |
|---|---|---|---|---|
| bus | `RolePermissionsChangedEvent` | `identity.RolePermissionsChanged` v`1` | `identity-events` | catalog, booking — cache eviction |

Identity evicts its own cache in the method that changes the role; there is no in-memory event.

### Redis keys

| Key | Type | TTL | Purpose |
|---|---|---|---|
| `perm:role:{roleName}` | STRING | 5 min | the expanded set for one role; authority is `identity_role_permissions` |

### Configuration

| Property | Value |
|---|---|
| `platform.permissions.cache-ttl` | `PT5M` |
| `platform.permissions.eviction-budget` | `PT5S` |

### Error registry rows

| Code | Refusal type | GraphQL `ErrorType` | Retryable |
|---|---|---|---|
| `PERMISSION_KEY_INVALID` | `PermissionKeyInvalid` | `BAD_REQUEST` | no |
| `PERMISSION_UNKNOWN` | `PermissionUnknown` | `NOT_FOUND` | no |
| `ROLE_MAPPING_UNKNOWN` | `RoleMappingUnknown` | `NOT_FOUND` | no |

## 5. Tasks

- [ ] **T1 · `PermissionKey`, the `Action` vocabulary and the module registry**
  - requirements: R1
  - files: `backend/shared-library/src/main/java/com/pml/shared/auth/`
  - verify: the grammar accepts every catalogue key and rejects every wildcard and every dotted key; parse is total
  - parallel-safe: no — every service imports it
  - depends: —

- [ ] **T2 · The code-declared registry and the catalogue bootstrapper**
  - requirements: R2
  - files: `backend/identity-service/.../permission/PermissionRegistry.java`, `.../PermissionCatalogueBootstrapper.java`
  - verify: bootstrapping twice produces identical rows; every registry key is enforced by an annotation; no create mutation exists
  - parallel-safe: no
  - depends: T1

- [ ] **T3 · `identity_role_permissions`, one document per role, and its write path**
  - requirements: R3
  - files: `backend/identity-service/.../domain/model/RolePermissionMapping.java`, `.../service/impl/`
  - verify: resolution issues one query and no `$lookup`; no `identity_roles` collection exists; an unknown key refuses at write time
  - parallel-safe: no
  - depends: T2

- [ ] **T4 · `PermissionExpander` and its idempotence test**
  - requirements: R4
  - files: `backend/shared-library/src/main/java/com/pml/shared/auth/PermissionExpander.java`
  - verify: both implication rules; `manage` implies no cross-cutting verb; no expanded key contains a `.`; `expand(expand(x)) == expand(x)` over 1,000 randomised cases
  - parallel-safe: no — every service imports it
  - depends: T1

- [ ] **T5 · The evaluator, the role union and the fail-closed paths**
  - requirements: R5, R6
  - files: `backend/shared-library/.../auth/PermissionEvaluator.java`
  - verify: union not intersection; no roles denies; inactive role and inactive permission each grant nothing; the evaluator never throws
  - parallel-safe: no
  - depends: T4

- [ ] **T6 · The per-role cache, its non-caching of failures, and cross-instance eviction**
  - requirements: R7
  - files: `backend/shared-library/.../auth/RolePermissionCache.java`, `backend/identity-service/.../event/`
  - verify: 10,000 principals over 5 roles hold 5 entries; a failed load is retried; a mapping change evicts everywhere within the budget; a Redis flush grants nothing
  - parallel-safe: no — cache and eviction together
  - depends: T5

- [ ] **T7 · Administration: `SUPER_ADMIN` writes, mandatory reason, append-only change log**
  - requirements: R8
  - files: `backend/identity-service/.../web/graphql/mutation/RolePermissionMutationResolver.java`
  - verify: `ADMIN` reads and cannot write; a reasonless change refuses; no change row is ever updated; grants as at a past instant are recoverable
  - parallel-safe: yes
  - depends: T3

- [ ] **T8 · Amend [ET-ORG-003](../../organization/003-permission-resolution/): the resolver consumes this catalogue**
  - requirements: R5
  - files: `backend/identity-service/.../service/impl/PermissionResolverImpl.java`
  - verify: precedence and explicit-deny behaviour unchanged; steps 1 and 3 read the expanded set; permission resolution still exists in exactly one implementation
  - parallel-safe: no — it amends another spec's core
  - depends: T5

- [ ] **T9 · The subgraph half, `myPermissions`, and the generated frontend catalogue**
  - requirements: R2, R8
  - files: `backend/identity-service/src/main/resources/graphql/schema.graphqls`, `frontend/web/codegen.ts`
  - verify: `compose-supergraph.sh --static`; the frontend catalogue is generated from the registry, not hand-written; no client implements precedence
  - parallel-safe: no — shared SDL across identity's specs
  - depends: T7

## 6. Out of scope

| Capability | Spec |
|---|---|
| Precedence between permission sources, event grants, explicit deny | [ET-ORG-003](../../organization/003-permission-resolution/) |
| Roles, realm, token issuance and `@auth` on schema fields | [ET-PLT-007](../007-security-and-authorization/) |
| Cutting a live session when a role changes | [ET-IDN-003](../../identity/003-token-revocation/) |
| Organization membership and the roles a member holds | [ET-ORG-002](../../organization/002-teams-and-invitations/) |
| Recording that a privileged action happened | [ET-PLT-009](../009-audit-trail/) |
| Platform settings and feature flags | [ET-ADM-002](../../admin/002-platform-configuration/) |

Deliberately never in scope: **wildcard permissions**, **per-user overrides** (two mechanisms for
one question), **roles stored in MongoDB** (Keycloak owns them), and **a runtime-extensible
permission vocabulary** (a key nobody enforces is protection that does not exist).
