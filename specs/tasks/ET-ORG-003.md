# ET-ORG-003 · Permission resolution and event access grants — tasks

> **Spec** [`specs/organization/003-permission-resolution/spec.md`](../organization/003-permission-resolution/spec.md) · **Wave 1** · `blocked_by:` ET-PLT-003, ET-PLT-005, ET-PLT-007, ET-ORG-002
> **Screen** `Org Admin - Team & Permissions.dc.html` — **grants only.** The Coverage map is explicit: *"the permission-resolution algorithm itself is an internal service-to-service API with no graph surface — its effects show up as disabled buttons and role badges throughout, not as its own screen."*
> **Verify** `mvn -q -f backend/identity-service test -Dgroups=ET-ORG-003 -DfailIfNoTests=false` · `compose-supergraph.sh --static`

**D-10** in one line: one resolver, one order — platform role → event grant → organization
membership → member denial → member custom or role → default deny. **A member's explicit denial
beats that member's allow; an event grant is exhaustive and carries no deny list** (D-56). Written
once, in identity-service, consumed by the other two over `/api/internal/authorization/*`.

**As built and decided, 2026-10-13.** Step names are `PLATFORM_ROLE`, `GRANT_CUSTOM`, `GRANT_ROLE`,
`GRANT_EXHAUSTIVE`, `NOT_A_MEMBER`, `MEMBER_DENIED`, `MEMBER_CUSTOM`, `MEMBER_ROLE`, `DEFAULT`;
refusal outcomes are `ORGANIZATION_STATUS`, `ORGANIZATION_UNKNOWN`, `NO_ORGANIZATION`,
`PERMISSION_UNKNOWN`. Platform roles bypass the organization status gate and are audited (D-57);
only a held permission may be granted or denied (D-58); `bank:manage` follows Owner and Admin, not
the payout switch (D-54); the permission catalogue is `resource:action` and is owned by
[ET-PLT-013](ET-PLT-013.md) (D-37, D-59).

This slice is as much **deletion** as construction: BE-5 removes every permission decision from
catalog and booking.

## R0 · Reconcile *(the deletion inventory)*

```bash
grep -rn '@PreAuthorize\|hasRole\|hasAuthority\|Permission\.' backend/catalog-service backend/booking-service --include='*.java' | grep -v /src/test/
```

Every hit is a `contradicted` row and a deletion in BE-5. Count them in R0 so the size of the
change is known before it starts — this is the task most likely to be quietly left half-done,
and a second resolver surviving in booking is invisible until it disagrees with the first.

[ROADMAP §Cross-cutting](../ROADMAP.md): *permission is resolved in exactly one implementation* —
asserted by this spec **and by lint**.

## A · Backend

### BE-1 · The `Permission` enum, the platform sets, the event-role sets
- **Spec** R6, R4 · **§5** T1 · **depends** R0 · **parallel-safe** no *(everything depends on it)*
- **Acceptance** **30 constants, no wildcard**; `FINANCE` holds **no** event-editing permission.
- The role sets are code, changed by a release (D-37); there is no stored mapping.
- A wildcard permission is a permission nobody can reason about, and it is always the one that
  turns out to include the thing you did not mean.

### BE-2 · `PermissionResolver` — the six steps, the deciding step, layer-1 tests
- **Spec** R1, R2, R3 · **§5** T2 · **depends** BE-1 · **parallel-safe** **no — this is the spec**
- **Acceptance** every decision-table row asserts the **outcome and the deciding step**, with
  **no database**.
- Asserting the step, not just the allow/deny, is what makes the order testable. Two
  implementations can agree on every outcome in the table and still differ on precedence — until
  a row is added that separates them.
- A member's explicit denial beats that member's allow, and that ordering is the whole reason the
  resolver exists rather than a set union. A grant has no deny list: `GRANT_DENIED` and its two
  decision-table rows are removed (D-56).

### BE-3 · The grant document, its unique index, grant/update/revoke, expiry
- **Spec** R5 · **§5** T3 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** **`EVENT_ADMIN` cannot grant `EVENT_OWNER`** (no privilege escalation by
  delegation); an expired grant resolves as **absent at the boundary** — frozen-clock, both sides;
  **only a permission the granter holds may be granted or denied** (D-58); the grant document has
  no `deniedPermissions` field.

### BE-4 · The internal authorization endpoints
- **Spec** R1, R7 · **§5** T4 · **depends** BE-2 · **parallel-safe** yes
- **As built** `/api/internal/authorization/` — `check`, `event-access`, `check-same-organization`,
  `user-organizations`, `organization-name`. There are no `/permissions/resolve` endpoints.
- **Acceptance** scope-gated per [`ET-PLT-007`](ET-PLT-007.md) R5; **the response carries no
  membership or grant document** — only the decision.
- Returning the underlying documents would let catalog and booking re-derive the answer, which is
  how a second resolver grows back.
- A check-many form so a list of 50 events costs one call, not 50, is planned under
  [ET-PLT-013](ET-PLT-013.md) P6; it is not built.

### BE-5 · Remove every permission decision from catalog and booking
- **Spec** R1 · **§5** T5 · **depends** BE-4 · **parallel-safe** yes *(one service per agent)*
- **Acceptance** permission resolution exists in **exactly one** implementation;
  `/api/internal/**` scope-gated; no `User.keycloakUserId`; **an unreachable resolver denies
  rather than guesses**.
- Fail closed. A cached "probably allowed" during an identity-service outage is how a suspended
  organizer publishes an event.

### BE-6 · The 30-second cache, its four evictions, the self-heal test — **DEFERRED, not built**
- **Spec** R7 · **§5** T6 · **depends** BE-2 · **parallel-safe** yes
- **Built only if** the measured p99 resolution latency exceeds 20 ms warm. Until then none exists
  and the boxes below stay unticked by design.
- **Acceptance** with eviction **suppressed**, the correct answer returns at **31 s**; **denials
  are cached too**.
- Caching only allows means every denial pays full price — which under a permission-heavy list
  query is most of them. And a 30-second self-heal bounds the damage of a missed eviction: the
  cache is wrong for at most half a minute, by construction rather than by hope.

### BE-7 · Audit every step-1 allow
- **Spec** R4 · **§5** T7 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** an `ADMIN` cross-tenant read writes a row naming **actor, permission and
  resource**; a platform-role allow on a **suspended** organization is allowed and audited through
  `PlatformWideAccess`, while a member of that organization is refused with `ORGANIZATION_STATUS`
  (D-57).
- Step 1 is the platform-role override — the step that lets an admin see another tenant's data.
  It is legitimate and it must never be silent. Feeds [`ET-PLT-009`](ET-PLT-009.md).

### BE-8 · Subgraph half; `myPermissions`; `@auth` on every field
- **Spec** R5 · **§5** T8 · **depends** BE-3 · **parallel-safe** **no — shared identity SDL with [`ET-ORG-001`](ET-ORG-001.md) and [`ET-ORG-002`](ET-ORG-002.md)**
- 5 queries, 3 mutations — grants and the caller's permissions, **not** the resolver.
- **As built** `myPermissions` returns `{ permissions, roles }`;
  `myEffectivePermissions(organizationId, eventId)` returns the resolved set and the deciding
  step.

## B · Contract

### GQL-1 · Compose and generate
- **depends** BE-8 · **parallel-safe** no
- **Acceptance** `myPermissions` returns the caller's effective set so the UI can gate affordances
  from **one** fetched answer rather than reimplementing the algorithm in TypeScript. A
  client-side re-implementation is a second resolver wearing a different language.

## C · Frontend

**No screen for the resolver.** Its effects surface everywhere; its algorithm surfaces nowhere.

### FE-1 · Event access grants panel *(within Team & Permissions)*
- **depends** GQL-1, F0-2 · **parallel-safe** yes
- Grant an event role to a member, scoped to one event; list, edit, revoke; show expiry.
- `EVENT_ADMIN` cannot offer `EVENT_OWNER` in the picker (BE-3) — and the server refuses anyway.
- **testids** `grant-row`, `grant-add`, `grant-role-select`, `grant-revoke`, `grant-expiry`

### FE-2 · `myPermissions` drives every affordance, in all three apps
- **depends** GQL-1 · **parallel-safe** no
- One fetch per session, cached; `PermissionGate` (already in the shared barrel) consumes it.
- **Do not compute permissions client-side.** Render from the resolved set.
- Disabled affordances explain themselves; a greyed control with no reason reads as a broken app.
- **Acceptance** no TypeScript implements precedence logic — assert by review and by a lint rule
  in the compliance suite.

### FE-3 · Role badges are consistent platform-wide
- **depends** FE-2 · **parallel-safe** yes
- `Badge` closed colour set. Roles humanised (**F0-7**). Incomparable roles are not rendered as a
  ranked list ([`ET-ORG-002`](ET-ORG-002.md) FE-1).

## D · Tests

### TS-1 · The decision table *(L1 — the heart of this spec)*
Every row asserts **outcome and deciding step**, with **no database**. Include the rows that
separate precedence orders — the ones where a set-union implementation would agree on the outcome
but disagree on the step.

### TS-2 · Explicit deny *(L1)*
A member's denial beats that member's allow, and does not apply on an event with an active grant
(D-56). This is the property most likely to regress under a "simplification".

### TS-3 · Grants *(L3)*
`EVENT_ADMIN` cannot grant `EVENT_OWNER`; expiry resolves absent at the boundary, both sides,
frozen clock; the unique index holds — confirmed live via MCP.

### TS-4 · Internal API *(L3)*
Scope-gated 401/403/200; the response carries **no** membership or grant document; `resolveMany`
over 50 events is one call.

### TS-5 · Single implementation *(L4 — lint-as-test)*
Source scan: **zero** permission comparisons in catalog and booking; exactly one `PermissionResolver`.
This is the assertion that stops BE-5 from being quietly undone.

### TS-6 · Failure mode *(L3)*
Stop identity-service: the resolver is unreachable and every consumer **denies**. Assert on a
mutation that would otherwise succeed.

### TS-7 · Cache *(L3 — deferred with BE-6)*
Runs only if the cache is built. Suppress eviction; correct answer at 31 s. Denials cached. Each of
the four eviction triggers observed. Until then, assert only that resolution latency is measured.

### TS-8 · Audit *(L3)*
Every step-1 allow writes a row naming actor, permission, resource, including an allow on a
suspended organization (D-57).

### TS-9 · Frontend *(L5)*
- Grants panel: loading, empty, error, populated.
- A member with a revoked grant loses the affordance on the next resolve.
- A `MARKETER` sees no finance navigation; direct URL is refused server-side too.

## E · Gate

- [ ] R0 deletion inventory recorded, with counts per service
- [ ] 30 permission constants, no wildcard; `FINANCE` holds no event-editing permission
- [ ] Decision table asserts outcome **and** deciding step, with no database
- [ ] A member's denial beats that member's allow; a grant is exhaustive and has no deny list (D-56)
- [ ] `EVENT_ADMIN` cannot grant `EVENT_OWNER`; expiry absent at the boundary; only held permissions granted or denied (D-58)
- [ ] Internal API scope-gated and leaks no underlying document
- [ ] **Zero** permission decisions remain in catalog and booking, asserted by lint
- [ ] Unreachable resolver **denies**
- [ ] Cache **deferred** until measured p99 exceeds 20 ms; if built, self-heals at 31 s, denials cached, four evictions observed
- [ ] Every step-1 allow audited with actor, permission, resource; platform roles bypass the organization status gate (D-57)
- [ ] No client-side precedence logic anywhere in `frontend/web`
- [ ] `mvn -q -f backend/identity-service test -Dgroups=ET-ORG-003 -DfailIfNoTests=false` green
- [ ] Spec `status:` → `implemented` — **Wave 2 does not open until all of Wave 1 is**
