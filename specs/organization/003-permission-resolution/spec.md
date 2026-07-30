# ET-ORG-003 · Permission resolution and event access grants

> **Conformance** · US Part I §1–§3 role hierarchies · US Part I §3 event-level roles · US Part II §11 event access CRUD · US Part IV §22 permission resolution algorithm

## 1. Capability

Three services need to answer one question: *may this actor do this thing to this
resource?* Catalog asks it before publishing an event, booking asks it before approving a
payout, identity asks it before changing a team member's role, and a scanner app asks it
several hundred times an hour at a gate. If each of them answers it independently, they
will answer it differently, and the difference will be discovered by somebody seeing data
they should not.

This spec is the one answer. It declares the closed catalogue of permission names, the
resolution algorithm and its exact order, and the internal API through which the other two
services ask rather than decide. It declares event access grants — the mechanism by which
a venue steward gets scanner access to one concert without joining the organization, and by
which an external editor gets to edit one event and nothing else — and it settles how those
interact with organization roles when they disagree.

The order matters more than any individual rule. A platform administrator's access is
checked first, because support cannot work otherwise. An event-level grant is checked
before the organization role, because that is what makes a grant an *override* rather than
an addition — it is how a `MANAGER` is restricted to `VIEWER` on one sensitive event. A
custom permission adds, a denied permission removes, and the denial wins over any allow it
meets. And the default, when nothing matches, is deny — because a permission system whose
default is allow is a permission system that is wrong every time somebody forgets to add a
rule.

## 2. Design decisions

**One implementation, in identity-service, consulted over the internal API.** Booking and
catalog do not resolve permissions; they ask. The alternative — a shared library each
service embeds — looks cheaper and produces three versions in production the day one
service deploys and the others do not. The call is `POST /api/internal/permissions/resolve`,
scope-gated, and it returns a decision, not the inputs to one.

**Six steps, in this order, and the order is the specification.**

```
1  platform role      SUPER_ADMIN / ADMIN / FINANCE grants → ALLOW
2  event access grant if one exists and is ACTIVE:
                        2a  in deniedPermissions          → DENY
                        2b  in customPermissions          → ALLOW
                        2c  in the event role's set       → ALLOW
                        2d  otherwise                     → DENY   (an override is exhaustive)
3  organization membership — absent or not ACTIVE         → DENY
4     in deniedPermissions                                → DENY
5     in customPermissions or the org role's set          → ALLOW
6  otherwise                                              → DENY
```

**Step 2d is the decision that makes a grant an override.** When an event access grant
exists, resolution ends there — it does not fall through to the organization role. That is
what allows a grant to *reduce* access: giving an organization `ADMIN` a `VIEWER` grant on
one event restricts them on that event, which is impossible if a grant can only add. The
cost is that a grant must be complete for that event, and the acceptance criteria say so
explicitly, because the natural mistake is to grant `ticket:scan` to a manager and
accidentally remove their ability to edit.

**A platform role short-circuits, and taking that path is audited.** An `ADMIN` reading
across tenants is a legitimate and necessary capability, and it is also the capability most
worth logging. Step 1 writes an audit row naming the actor, the permission and the
resource ([ET-PLT-009](../../_platform/009-audit-trail/)).

**`FINANCE` is not a lesser `ADMIN`.** It grants the financial permission set across every
organization and nothing else. A finance operator can approve a payout for any organization
and cannot suspend one, edit an event or read an attendee list. Modelling it as a rank
below `ADMIN` would give it everything `ADMIN` has minus a subtraction list, and
subtraction lists are where privilege leaks.

**Deny always beats allow, at every level.** Step 2a beats 2b and 2c; step 4 beats step 5.
An explicit denial is a statement somebody made deliberately, and the only safe reading of
it is that they meant it.

**The permission vocabulary is closed and is a flat string catalogue.** `event:publish`,
`payout:request`, `ticket:scan` — `{resource}:{action}`, declared in §4, enumerated in a
Java enum. Not a hierarchy, not wildcards. `event:*` is convenient right up to the point
somebody adds `event:delete` and discovers who already had it.

**Resolution results are cached for seconds, not minutes, and invalidated by event.** The
scanner at a gate asks hundreds of times an hour; the answer changes rarely. A 30-second
Redis cache keyed on `(actor, permission, resource)` makes that affordable, and
`identity.MemberRoleChanged`, `identity.MemberRemoved`, `identity.EventAccessGranted` and
`identity.EventAccessRevoked` each evict the affected keys. Thirty seconds is short enough
that a missed eviction self-heals faster than anyone raises a ticket — which is the point,
because eviction across three services will be missed.

**Batch resolution exists, because the alternative is an N+1 over HTTP.** Listing twenty
events with a per-event *may I edit this* is twenty calls. `resolveMany` takes a list and
returns a map, and the graph's field-level checks use it.

**Grants expire, and expiry is a real state.** A venue steward gets scanner access for the
night, not forever. `expiresAt` is optional; where set, an expired grant is inactive and
resolution treats it as absent — falling through to the organization role, which for an
external steward with no membership means deny.

**Rejected alternatives**

- *A shared permission library embedded in all three services.* Three versions in production the first time deploys are not simultaneous.
- *Grants that can only add permissions.* Then a grant cannot restrict, and the "give this contractor access to exactly one event" case requires creating a whole role.
- *Falling through from a grant to the organization role.* Makes every grant additive, which is the previous bullet.
- *Wildcard permissions (`event:*`).* Silently grants every action added later, to everybody who already had the wildcard.
- *A permission hierarchy with inheritance between permission names.* Two inheritance systems — one over roles, one over permissions — and every question needs both.
- *`FINANCE` as a rank between `ORGANIZER` and `ADMIN`.* Makes it a subtraction from `ADMIN`, and subtraction lists leak.
- *Caching resolutions for minutes.* Turns a missed eviction into a support incident instead of a self-healing blip.
- *Resolving permissions in the GraphQL directive.* The directive has not fetched the resource, and one that fetches is one that N+1s.

## 3. Requirements

### ET-ORG-003-R1 · One resolver, and the other services ask it

THE SYSTEM SHALL resolve every permission decision in one implementation, and catalog and
booking SHALL obtain decisions from it rather than computing them.

**Acceptance**
- [ ] `PermissionResolver` in identity-service is the only implementation of the §4 algorithm in the platform
- [ ] Neither catalog-service nor booking-service contains a role comparison, a membership lookup or a permission set
- [ ] `POST /api/internal/permissions/resolve` and `/resolve-many` are scope-gated per [ET-PLT-007](../../_platform/007-security-and-authorization/) R5
- [ ] The endpoint returns a decision — `allowed`, plus the deciding step for diagnostics — and never the membership or grant documents
- [ ] `./scripts/spec-lint.sh --security` reports permission resolution in exactly one implementation
- [ ] A calling service that cannot reach the resolver **denies**; it does not fall back to a local guess

### ET-ORG-003-R2 · The six steps run in order, and the order is testable

THE SYSTEM SHALL evaluate the six steps of §4 in the declared order, and the outcome SHALL
depend only on that order.

**Acceptance**
- [ ] `PermissionResolver.resolve` implements exactly the §4 steps, in order, with no additional branch
- [ ] The resolution returns which step decided, and a test asserts the deciding step for every one of the §4 decision-table rows
- [ ] A platform role short-circuits before any database read for membership or grants
- [ ] The default, when no step allows, is deny
- [ ] Every step is a layer-1 test over values, with no database ([ET-PLT-006](../../_platform/006-test-harness/) R1)
- [ ] Adding a step requires editing this spec

### ET-ORG-003-R3 · An event access grant is an exhaustive override

WHILE an active event access grant exists for an actor and an event, THE SYSTEM SHALL
decide from that grant alone and SHALL NOT consult the organization role.

**Acceptance**
- [ ] A permission in the grant's `deniedPermissions` is denied, even when the event role and the organization role both allow it
- [ ] A permission in the grant's `customPermissions` is allowed, even when the event role does not include it
- [ ] A permission in neither, and not in the event role's set, is **denied** — resolution does not fall through
- [ ] An organization `ADMIN` holding a `VIEWER` grant on an event cannot edit that event, and can still edit every other event of that organization
- [ ] An expired or non-`ACTIVE` grant is treated as absent, and resolution falls through to the organization role
- [ ] A test drives every (grant present/absent × event role × organization role) combination in the §4 decision table

### ET-ORG-003-R4 · Platform roles grant across tenants, and the crossing is recorded

IF an actor holds `SUPER_ADMIN`, `ADMIN` or `FINANCE`, THEN THE SYSTEM SHALL allow the
permissions §4 grants that role and SHALL record the decision.

**Acceptance**
- [ ] `SUPER_ADMIN` is allowed every catalogue permission; `ADMIN` and `FINANCE` are allowed exactly the sets §4 declares
- [ ] `FINANCE` is allowed no event-editing, organization-suspension or attendee-list permission
- [ ] `ORGANIZER` and `CUSTOMER` grant **nothing** at step 1 — an organizer's access comes entirely from membership
- [ ] Every step-1 allow writes an audit row naming the actor, the permission, the resource and the organization it crossed into
- [ ] A test asserts an `ORGANIZER` cannot reach another organization's resources through any permission

### ET-ORG-003-R5 · Grants are managed, scoped and expiring

THE SYSTEM SHALL allow an authorised actor to grant, update and revoke event access, and
IF a grant carries an expiry, THEN THE SYSTEM SHALL treat it as inactive afterwards.

**Acceptance**
- [ ] `grantEventAccess` requires organization `OWNER`/`ADMIN`, or `EVENT_OWNER`/`EVENT_ADMIN` on that event
- [ ] An `EVENT_ADMIN` cannot grant `EVENT_OWNER` — refused with `EVENT_ROLE_NOT_GRANTABLE` carrying `eventRole`
- [ ] A grant to an actor who already has one for that event updates it rather than creating a second — `{ userId, eventId }` is unique
- [ ] `revokeEventAccess` sets `status = REVOKED` with `revokedById`, `revokedAt` and a reason; it deletes nothing
- [ ] `EVENT_OWNER` cannot be revoked — the event's creator holds it until the event is deleted
- [ ] A grant past `expiresAt` resolves as absent, asserted at the boundary with a frozen clock
- [ ] Removing a member revokes every grant they hold for that organization's events, in the same transaction ([ET-ORG-002](../002-teams-and-invitations/) R6)

### ET-ORG-003-R6 · The permission catalogue is closed

THE SYSTEM SHALL recognise only the permission names of §4, and IF an unknown name is
resolved, THEN THE SYSTEM SHALL deny and record it.

**Acceptance**
- [ ] `Permission` is a Java enum with one constant per §4 row and no wildcard
- [ ] Every name is `{resource}:{action}`, lowercase, with no `*`
- [ ] `customPermissions` and `deniedPermissions` accept only catalogue names; an unknown one is refused at write time
- [ ] Resolving an unknown name denies and increments a metric — a silent deny on a typo'd permission is how a capability disappears without a bug report
- [ ] Adding a permission changes this spec's §4 in the same commit as its first use

### ET-ORG-003-R7 · Resolution is fast enough for a gate, and stale for seconds at most

THE SYSTEM SHALL cache resolutions briefly and SHALL invalidate them when the inputs change.

**Acceptance**
- [ ] A resolution is cached at `perm:{userId}:{permission}:{resourceType}:{resourceId}` for 30 seconds
- [ ] `identity.MemberRoleChanged`, `identity.MemberRemoved`, `identity.EventAccessGranted` and `identity.EventAccessRevoked` each evict the affected keys
- [ ] A missed eviction self-heals within 30 seconds, asserted by a test that suppresses eviction and observes the correct answer at 31 seconds
- [ ] `resolveMany` answers a list of (permission, resource) pairs in one call and one cache round trip
- [ ] The graph's field-level checks use `resolveMany`; no resolver issues one call per list item
- [ ] Resolution latency at the 99th percentile is under 20 ms warm, measured by a metric
- [ ] A denial is cached on the same terms as an allow — caching only allows makes a denial a database read every time, which is the request pattern an attacker generates

## 4. Model

### The permission catalogue — closed

| Permission | Meaning |
|---|---|
| `event:view` | see an event's details and sales data |
| `event:create` | create a draft event |
| `event:edit` | edit an event's details and tiers |
| `event:publish` | publish, unpublish, reschedule |
| `event:delete` | delete a draft event |
| `event:cancel` | cancel a published event |
| `attendee:view` | see the attendee list |
| `ticket:scan` | validate tickets at the gate |
| `ticket:refund` | issue a refund for a ticket |
| `analytics:view` | see event and sales analytics |
| `promotion:manage` | create and manage promo codes |
| `financial:view` | see revenue, escrow and commission figures |
| `payout:request` | request a payout |
| `payout:approve` | approve a payout — platform side |
| `bank:manage` | add and verify bank accounts |
| `team:invite` | invite a team member |
| `team:remove` | remove a team member |
| `team:role` | change a team member's role |
| `team:view` | see the team list |
| `event:access:grant` | grant event-level access |
| `organization:view` | see the organization's profile |
| `organization:edit` | edit the organization's profile |
| `organization:billing` | manage billing settings |
| `organization:transfer` | transfer ownership |
| `organization:delete` | request deletion |
| `organization:suspend` | suspend an organization — platform side |
| `organization:approve` | approve or reject an application — platform side |
| `platform:configure` | platform configuration and feature flags |
| `transaction:recover` | resume, retry and resolve stuck transactions |
| `audit:view` | read the audit trail |

**30 permissions.** No other name exists, and there is no wildcard.

### Platform role sets — step 1

| Role | Grants |
|---|---|
| `SUPER_ADMIN` | every catalogue permission |
| `ADMIN` | everything except `platform:configure` |
| `FINANCE` | `financial:view`, `payout:approve`, `ticket:refund`, `transaction:recover`, `audit:view`, `organization:view`, `event:view` |
| `ORGANIZER` | **nothing** — access comes from membership |
| `CUSTOMER` | **nothing** |

`FINANCE` is a distinct set, not a subtraction from `ADMIN` — it cannot suspend an
organization, edit an event or read an attendee list.

### Organization role sets — step 5

The transitive closure declared in [ET-ORG-002 §4](../002-teams-and-invitations/).

| Role | Effective set |
|---|---|
| `CONTRIBUTOR` | `event:view`, `attendee:view`, `ticket:scan`, `organization:view`, `team:view` |
| `MARKETER` | `CONTRIBUTOR` + `analytics:view`, `promotion:manage` |
| `MANAGER` | `CONTRIBUTOR` + `event:create`, `event:edit`, `event:publish`, `analytics:view`, `promotion:manage`, `financial:view`\* |
| `ADMIN` | `MANAGER` ∪ `MARKETER` + `event:delete`, `event:cancel`, `ticket:refund`, `team:invite`, `team:remove`, `team:role`, `event:access:grant`, `organization:edit`, `bank:manage`, `payout:request`\* |
| `OWNER` | `ADMIN` + `organization:billing`, `organization:transfer`, `organization:delete`, `payout:request` |

\* `financial:view` for `MANAGER` and `payout:request` for `ADMIN` are gated by
`settings.managersCanViewFinancials` and `settings.adminsCanRequestPayouts`, both default
`false`. **These two are the only permissions that are not a pure function of the role**,
and any cache or test keyed on role alone is wrong for them.

### Event role sets — step 2c

| Role | Grants |
|---|---|
| `EVENT_OWNER` | `event:view`, `event:edit`, `event:publish`, `event:cancel`, `event:delete`, `attendee:view`, `ticket:scan`, `ticket:refund`, `analytics:view`, `event:access:grant` |
| `EVENT_ADMIN` | everything `EVENT_OWNER` has except `event:cancel`, `event:delete` |
| `EDITOR` | `event:view`, `event:edit`, `attendee:view`, `ticket:scan`, `analytics:view` |
| `CHECK_IN` | `event:view`, `attendee:view`, `ticket:scan` |
| `VIEWER` | `event:view`, `analytics:view` |

`EVENT_OWNER` is created when the event is created and cannot be revoked (R5).

### The algorithm

```java
public Decision resolve(Actor actor, Permission permission, Resource resource) {

    // 1 — platform role. Short-circuits before any membership or grant read.
    if (platformGrants(actor.platformRoles(), permission)) {
        audit.crossedTenant(actor, permission, resource);       // R4
        return Decision.allow(Step.PLATFORM_ROLE);
    }

    // 2 — event access grant. EXHAUSTIVE: if one exists, resolution ends here.
    if (resource.isEventScoped()) {
        var grant = grants.activeFor(actor.userId(), resource.eventId(), clock.instant());
        if (grant != null) {
            if (grant.deniedPermissions().contains(permission)) return Decision.deny(Step.GRANT_DENIED);
            if (grant.customPermissions().contains(permission)) return Decision.allow(Step.GRANT_CUSTOM);
            if (grant.eventRole().grants(permission))           return Decision.allow(Step.GRANT_ROLE);
            return Decision.deny(Step.GRANT_EXHAUSTIVE);        // no fall-through — R3
        }
    }

    // 3 — organization membership
    var member = members.active(actor.userId(), resource.organizationId());
    if (member == null) return Decision.deny(Step.NOT_A_MEMBER);

    // 4 — explicit denial beats any allow below it
    if (member.deniedPermissions().contains(permission)) return Decision.deny(Step.MEMBER_DENIED);

    // 5 — custom grant, or the role's effective set
    if (member.customPermissions().contains(permission)) return Decision.allow(Step.MEMBER_CUSTOM);
    if (member.role().effectiveSet(organizationSettings).contains(permission))
        return Decision.allow(Step.MEMBER_ROLE);

    // 6 — default deny
    return Decision.deny(Step.DEFAULT);
}
```

### Decision table

The rows R2 and R3 require a test for, each asserting both the outcome and the deciding
step.

| Platform | Grant | Grant says | Org membership | Org role says | Outcome | Step |
|---|---|---|---|---|---|---|
| `ADMIN` | — | — | none | — | ALLOW | `PLATFORM_ROLE` |
| `FINANCE` | — | — | none | — | ALLOW iff in the `FINANCE` set | `PLATFORM_ROLE` / `DEFAULT` |
| `ORGANIZER` | absent | — | `OWNER` | allows | ALLOW | `MEMBER_ROLE` |
| `ORGANIZER` | absent | — | none | — | DENY | `NOT_A_MEMBER` |
| `ORGANIZER` | absent | — | `MANAGER`, `REMOVED` | allows | DENY | `NOT_A_MEMBER` |
| `ORGANIZER` | `VIEWER` | not in set | `ADMIN` | allows | **DENY** | `GRANT_EXHAUSTIVE` |
| `ORGANIZER` | `CHECK_IN` | in set | none | — | ALLOW | `GRANT_ROLE` |
| `ORGANIZER` | `EDITOR` + denied | denied | `OWNER` | allows | **DENY** | `GRANT_DENIED` |
| `ORGANIZER` | `CHECK_IN` + custom | custom | none | — | ALLOW | `GRANT_CUSTOM` |
| `ORGANIZER` | expired | — | `MANAGER` | allows | ALLOW | `MEMBER_ROLE` |
| `ORGANIZER` | absent | — | `MANAGER` + denied | allows | DENY | `MEMBER_DENIED` |
| `ORGANIZER` | absent | — | `CONTRIBUTOR` + custom | does not allow | ALLOW | `MEMBER_CUSTOM` |
| `CUSTOMER` | absent | — | none | — | DENY | `NOT_A_MEMBER` |

Rows 6 and 8 are the ones that make a grant an override rather than an addition.

### Documents

`identity_event_access_grants`

| Field | Type | Notes |
|---|---|---|
| `_id` | `String` | |
| `userId`, `eventId`, `organizationId` | `String` | `{userId, eventId}` unique |
| `eventRole` | `EventRole` | the five of §4 |
| `customPermissions`, `deniedPermissions` | `List<String>` | catalogue names only |
| `grantedById`, `reason` | `String` | |
| `status` | `AccessGrantStatus` | `ACTIVE`, `SUSPENDED`, `REVOKED`, `EXPIRED` |
| `expiresAt` | `Instant` | nullable |
| `revokedById`, `revokedAt`, `revocationReason` | | |
| `grantedAt`, `createdAt`, `updatedAt` | `Instant` | |

`identity_permissions` and `identity_role_permissions` hold the catalogue and the role sets
as reference data, seeded from the §4 tables and read-only at runtime — they exist so an
administrator can *see* the model, not so it can be edited without a deploy.

### Internal API

| Method | Path | Scope | Request | Response |
|---|---|---|---|---|
| `POST` | `/api/internal/permissions/resolve` | `internal-read` | `{ userId, permission, resourceType, resourceId, organizationId }` | `{ allowed, step }` |
| `POST` | `/api/internal/permissions/resolve-many` | `internal-read` | `{ userId, checks: [...] }` | `{ results: [{ key, allowed, step }] }` |

A caller that cannot reach this endpoint **denies** (R1).

### GraphQL

Subgraph `identity`. Every field carries `@auth` explicitly.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `myEventAccess(eventId)` | query | `AUTHENTICATED` | `EventAccessGrant` |
| `myEventAccessGrants` | query | `AUTHENTICATED` | `[EventAccessGrant!]!` |
| `eventAccessGrants(eventId, status, page)` | query | `AUTHENTICATED` | `EventAccessGrantPage!` |
| `userEventAccess(userId, eventId)` | query | `AUTHENTICATED` | `EventAccessGrant` |
| `myPermissions(organizationId)` | query | `AUTHENTICATED` | `[String!]!` |
| `grantEventAccess(input)` | mutation | `ORGANIZER` | `EventAccessGrant!` |
| `updateEventAccess(input)` | mutation | `ORGANIZER` | `EventAccessGrant!` |
| `revokeEventAccess(input)` | mutation | `ORGANIZER` | `EventAccessGrant!` |

`Event` is stubbed here and extended with `accessGrants`
([ET-PLT-004 §4](../../_platform/004-federation-contract/)).

### Events

| Tier | Name | When | Consumers |
|---|---|---|---|
| bus | `identity.EventAccessGranted` v1 | grant, update | booking → validation authorisation |
| bus | `identity.EventAccessRevoked` v1 | revoke, expiry, member removal | booking → validation authorisation |

Both session-keyed on `eventId` ([ET-PLT-003 §4](../../_platform/003-event-contract/)).
All four cache-invalidating events of R7 are these two plus
[ET-ORG-002](../002-teams-and-invitations/)'s `MemberRoleChanged` and `MemberRemoved`.

### Redis keys

| Key | TTL | Purpose | Authority |
|---|---|---|---|
| `perm:{userId}:{permission}:{resourceType}:{resourceId}` | 30 s | resolution cache, allows **and** denies | the resolver |

### Error codes

`ACCESS_GRANT_UNKNOWN`, `EVENT_ROLE_NOT_GRANTABLE` — rows of
[ET-PLT-005 §4](../../_platform/005-error-contract/) introduced by this spec.
`ACTOR_NOT_PERMITTED` is what a denial becomes at the boundary.

## 5. Tasks

- [ ] **T1 · The `Permission` enum, the platform sets, the event-role sets**
  - requirements: R6, R4
  - files: `backend/shared-library/.../auth/Permission.java`, identity's role tables
  - verify: 30 constants, no wildcard; `FINANCE` holds no event-editing permission
  - parallel-safe: no — everything depends on it
  - depends: —

- [ ] **T2 · `PermissionResolver`: the six steps, the deciding step, layer-1 tests**
  - requirements: R1, R2, R3
  - files: `backend/identity-service/.../service/impl/PermissionResolverImpl.java`
  - verify: every decision-table row asserts outcome **and** step, with no database
  - parallel-safe: no — this is the spec
  - depends: T1

- [ ] **T3 · The grant document, its unique index, grant/update/revoke, expiry**
  - requirements: R5
  - files: `backend/identity-service/.../domain/model/EventAccessGrant.java`, `.../service/impl/`
  - verify: `EVENT_ADMIN` cannot grant `EVENT_OWNER`; an expired grant resolves as absent at the boundary
  - parallel-safe: yes
  - depends: T1

- [ ] **T4 · The internal resolve and resolve-many endpoints**
  - requirements: R1, R7
  - files: `backend/identity-service/.../web/rest/InternalPermissionController.java`
  - verify: scope-gated per ET-PLT-007 R5; the response carries no membership or grant document
  - parallel-safe: yes
  - depends: T2

- [ ] **T5 · Remove every permission decision from catalog and booking**
  - requirements: R1
  - files: `backend/catalog-service/`, `backend/booking-service/`
  - verify: `./scripts/spec-lint.sh --security`; an unreachable resolver denies rather than guesses
  - parallel-safe: yes — one service per agent
  - depends: T4

- [ ] **T6 · The 30-second cache, its four evictions, and the self-heal test**
  - requirements: R7
  - files: `backend/identity-service/.../infrastructure/cache/`
  - verify: eviction suppressed, the correct answer returns at 31 s; denials are cached too
  - parallel-safe: yes
  - depends: T2

- [ ] **T7 · Audit every step-1 allow**
  - requirements: R4
  - files: `backend/identity-service/.../service/impl/PermissionResolverImpl.java`
  - verify: an `ADMIN` cross-tenant read writes a row naming actor, permission and resource
  - parallel-safe: yes
  - depends: T2

- [ ] **T8 · The subgraph half; `myPermissions`; `@auth` on every field**
  - requirements: R5
  - files: `backend/identity-service/src/main/resources/graphql/schema.graphqls`
  - verify: `compose-supergraph.sh --static`
  - parallel-safe: no — shared SDL with ET-ORG-001 and ET-ORG-002
  - depends: T3

## 6. Out of scope

| Capability | Spec |
|---|---|
| Realm roles, `@auth`'s coarse gate, tenant scoping, token validation | [ET-PLT-007](../../_platform/007-security-and-authorization/) |
| The organization lifecycle and the stage access matrix | [ET-ORG-001](../001-organizer-onboarding/) |
| Roles, memberships, invitations and ownership transfer | [ET-ORG-002](../002-teams-and-invitations/) |
| Who creates the `EVENT_OWNER` grant, and when | [ET-CAT-001](../../catalog/001-event-lifecycle/) |
| What the audit rows look like and how long they are kept | [ET-PLT-009](../../_platform/009-audit-trail/) |
| Scanner authorisation at the gate, and offline scanning | [ET-TKT-003](../../ticketing/003-validation-and-checkin/) |
| Rate limiting the resolve endpoint | [ET-PLT-011](../../_platform/011-rate-limiting-and-abuse/) |

Deliberately never in scope: **wildcard permissions** (they silently grant every action
added later), **a shared permission library embedded in each service** (three versions in
production the first time deploys are not simultaneous), and **grants that can only add**
(then a grant cannot restrict, which is half of what grants are for).
