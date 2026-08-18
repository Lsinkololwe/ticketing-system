# ET-PLT-007 · Keycloak realm, roles, `@auth`, tenant scoping, idempotency

> **Conformance** · PDI Phase 5 payment idempotency · US Part I §1 platform role hierarchy

## 1. Capability

Four kinds of actor reach this platform: a customer buying a ticket, an organizer running
events, a platform administrator, and another service. Telling them apart is Keycloak's
job. Deciding what each may do is the platform's, and the two questions that decision
splits into are answered in different places — *is this kind of actor allowed to call this
operation at all* is a property of the operation, and *may this particular actor touch
this particular resource* is a property of the data. Conflating them produces either a
graph where every field is `AUTHENTICATED` and the real check is buried in a service, or a
directive vocabulary that grows a term per resource.

This spec fixes the security substrate. It declares the realm, its roles and its clients;
the two authorization surfaces and which question each answers; how a JWT is validated by
every service independently; and the rule that keeps a multi-tenant platform from leaking
across tenants — that the organization filter is a repository predicate, not a resolver
`if`, because a missing `if` returns another organization's data with a `200`.

It also fixes idempotency, which in a mobile-money market is a correctness requirement
rather than a nicety. Networks in-market drop responses routinely; a user who does not see
a confirmation taps again; and the platform must be able to tell *this is the same
purchase* from *this is a second purchase* without guessing. Every money-moving mutation
therefore carries a client-supplied key, and this spec says exactly what happens on each
of the three ways that key can arrive.

It delivers no domain behaviour. Its success criterion is that a forged or expired token
is rejected by every service independently, that a caller cannot read across a tenant
boundary through any operation, and that the same purchase submitted twice produces one
ticket and one charge.

## 2. Design decisions

**Keycloak answers *who*; MongoDB answers *in which organization, as what*.** Neither is
authoritative for the other's question, and no code asks the wrong one.

| Question | Answered by | Changes |
|---|---|---|
| Is this a valid identity? | Keycloak, via JWT | on login |
| What platform role does this actor hold? | the JWT's `realm_access.roles` | rarely |
| Which organizations is this actor a member of, and as what? | `identity_organization_members` | often |
| What may this actor do to *this* event? | `identity_event_access_grants` | often |

**The JWT is not the authority on organization role, deliberately.** A token minted before
a demotion carries the old role until it expires. For platform roles that is acceptable —
they change on the order of months and an administrator being demoted is not an
adversarial race. For organization roles it is not: a member removed at 14:00 must not
retain `ADMIN` on a payout screen until 14:05. Organization role is therefore resolved
per request from MongoDB ([ET-ORG-003](../../organization/003-permission-resolution/)),
and Keycloak's groups are a **mirror**, written from MongoDB, used for coarse partitioning
and never read as truth by an authorization decision.

**Two authorization surfaces, two questions.**

`@auth(requires: Role)` on a schema field is the **coarse gate**: may an actor of this
kind call this operation at all. It is declarative, visible in the schema, and composes
into the contract. `requestPayout` requires `ORGANIZER`; `suspendOrganization` requires
`ADMIN`; `events` is `PUBLIC`.

The **fine gate** is the resource check, and it lives at the repository: is this payout
request one of the organizations this actor belongs to, and does their role there permit
it. The two are not alternatives — passing the coarse gate is necessary and never
sufficient, and any organizer with the `ORGANIZER` role passes `@auth(requires: ORGANIZER)`
on every organizer's payout request.

**Operations are deny-by-default and the schema says so explicitly.** An untagged,
un-`@auth`ed field is `PUBLIC` in the directive's own default, which is the wrong default
for a platform where most fields are not. Every field therefore carries `@auth`
explicitly, including the public ones, which carry `@auth(requires: PUBLIC)` — so a field
with no directive is a review failure that a lint catches, rather than a field silently
open.

**Tenant scoping is a repository predicate.** Every query for a tenant-scoped collection
takes the caller's permitted organization ids and includes them in the Mongo filter. Not a
post-fetch check, not a resolver `if`, and never an application-level comparison after the
document is already in memory — a document loaded before the check is a document that has
already reached the log line that dumps it. A cross-tenant id then returns nothing, which
[ET-PLT-005](../005-error-contract/) R6 renders as `*_UNKNOWN`.

**Every service validates every JWT.** Signature against Keycloak's JWKS, issuer, audience
and expiry, with a bounded clock skew. The gateway is a router, not a security boundary: a
subgraph reachable directly on `:8082` inside a Docker network must reject a forged token
by itself, because it will be reached that way.

**Service-to-service is `client_credentials` with scopes, not a shared secret header.**
`/api/internal/**` demands `SCOPE_internal-read` or `SCOPE_internal-write`. These endpoints
bypass user authorization entirely — they exist so Keycloak's authenticator can verify an
OTP and so identity-service can answer a permission question for booking — so they are the
one place where a mistake is total.

**Idempotency is a client-supplied key with three defined outcomes.** Every money-moving
mutation takes `idempotencyKey`. The key is guarded in Redis for the in-flight window and
enforced by a unique index on the persisted intent, because Redis alone is a fast path and
not a guarantee.

| Arrival | Outcome |
|---|---|
| key unseen | proceed; record the key with the request fingerprint and the response |
| key seen, **same** request fingerprint | return the original response — a replay, not a second purchase |
| key seen, **different** request fingerprint | refuse with `IDEMPOTENCY_KEY_REUSED` |

The third case matters: a client reusing a key for a different purchase is a client bug,
and silently returning the first response would show the user a ticket they did not buy.

**Token lifetimes are short, and refresh is where the session lives.** Five-minute access
tokens bound the window in which a revoked platform role still works. Refresh lifetimes
differ by client because the risk does: a mobile app on a personal handset holds a long
refresh token, an admin console in a browser holds a short one.

**Rejected alternatives**

- *Reading organization role from the JWT's groups claim.* Fast, and wrong for up to a token lifetime after every role change — on precisely the screens where it matters.
- *A single `@auth(requires: AUTHENTICATED)` everywhere with all checks in services.* Makes the schema useless as a description of who may call what, and moves every check somewhere a reviewer has to go find.
- *Resource authorization in the GraphQL directive.* Needs the resource, which the directive has not fetched; a directive that fetches is a directive that N+1s.
- *Post-fetch tenant checks.* Correct until someone logs the object, and then it is a breach that passed its tests.
- *The gateway as the trust boundary, subgraphs open inside the network.* Assumes the network is a perimeter. It is a Docker bridge.
- *A shared static secret header for service-to-service calls.* Cannot be rotated without simultaneous deploys, cannot be scoped, and appears in every log that dumps headers.
- *Server-generated idempotency keys.* The retry that needs the key is the one where the client never saw the response, so the client must be the one that minted it.
- *Treating a repeated key with a different body as a replay.* Shows a user someone else's purchase.

## 3. Requirements

### ET-PLT-007-R1 · The realm defines the platform's actors

THE SYSTEM SHALL define one Keycloak realm carrying the five platform roles and the client
set of §4, with composite inheritance as declared.

**Acceptance**
- [ ] Realm `event-ticketing` exists and is exported to `../docker-resources/keycloak/` as versioned configuration, not configured by hand
- [ ] The realm declares exactly `SUPER_ADMIN`, `ADMIN`, `FINANCE`, `ORGANIZER`, `CUSTOMER`
- [ ] Composites are as §4 declares — `SUPER_ADMIN` includes `ADMIN`; `ADMIN` includes `FINANCE`; `ORGANIZER` includes `CUSTOMER`
- [ ] `CUSTOMER` is the realm's default role, granted on registration with no administrative step
- [ ] Each of the three services has its own confidential client with its own secret
- [ ] Public clients declare PKCE and an exact redirect-URI allowlist — no wildcard host

### ET-PLT-007-R2 · Every service validates every token, independently

THE SYSTEM SHALL validate each JWT at the service that receives it, against Keycloak's
JWKS.

**Acceptance**
- [ ] Each service builds a `ReactiveJwtDecoder` from the issuer URI and validates signature, `iss`, `aud`, `exp` and `nbf`
- [ ] Clock skew tolerance is explicit and no greater than 30 seconds
- [ ] A token forged with a different key, one with a wrong issuer, one with a wrong audience and an expired one are each rejected when sent **directly** to a service, bypassing the gateway
- [ ] JWKS is cached with a bounded TTL and a key rotation is picked up without a restart
- [ ] Authorities are derived from `realm_access.roles`, `resource_access.{client}.roles` and `scope`, by one converter in `shared-library`
- [ ] Access tokens live 5 minutes; refresh lifetimes are per §4 and differ by client

### ET-PLT-007-R3 · Every operation declares its coarse gate

THE SYSTEM SHALL carry an explicit `@auth` on every query, mutation and protected field,
and a field without one SHALL fail the build.

**Acceptance**
- [ ] Every field in `Query` and `Mutation` in all three subgraphs carries `@auth`, including public ones, which carry `@auth(requires: PUBLIC)`
- [ ] A test enumerates the composed schema and fails on any `Query`/`Mutation` field lacking `@auth`
- [ ] The `@auth` runtime denies with `ACTOR_NOT_AUTHENTICATED` when no identity is present and `ACTOR_NOT_PERMITTED` when the role is insufficient
- [ ] `@auth(requires: INTERNAL)` is denied for any token not carrying an internal scope
- [ ] The directive is evaluated **before** the data fetcher runs — a denied field performs no repository call
- [ ] Every `@auth` requirement is a row of the §4 operation-gate registry

### ET-PLT-007-R4 · Tenant scoping is enforced in the query, not after it

WHILE a caller reads or writes tenant-scoped data, THE SYSTEM SHALL restrict the query to
the organizations that caller belongs to.

**Acceptance**
- [ ] Every repository method over a tenant-scoped collection takes the permitted organization ids and includes them in the Mongo filter
- [ ] No resolver or service compares an organization id after loading the document
- [ ] A caller requesting another organization's resource by a known-good id receives the `*_UNKNOWN` code for that type, indistinguishable from a non-existent id (ET-PLT-005 R6)
- [ ] A `SUPER_ADMIN` or `ADMIN` bypasses the filter through one explicitly named code path, and that path is logged to the audit trail
- [ ] A test iterates every tenant-scoped query with a second organization's ids and asserts no data is returned
- [ ] Permission resolution exists in exactly one implementation, `/api/internal/**` is scope-gated, and no `User.keycloakUserId` exists

### ET-PLT-007-R5 · Internal endpoints are scope-gated and user-agnostic

THE SYSTEM SHALL restrict `/api/internal/**` to callers holding an internal scope, and
these endpoints SHALL perform no user authorization.

**Acceptance**
- [ ] Every `/api/internal/**` path requires `SCOPE_internal-read`, `SCOPE_internal-write` or `ROLE_INTERNAL_SERVICE`; none is `permitAll`
- [ ] The `internal-service` client uses `client_credentials` and its secret is environment-sourced in every consumer
- [ ] No internal endpoint reads a user identity from the request or makes a decision on one
- [ ] An unauthenticated call to each internal endpoint returns 401; one with a user token but no internal scope returns 403
- [ ] The internal surface is enumerated in §4 and adding a path changes this spec

### ET-PLT-007-R6 · Money-moving mutations are idempotent under retry

WHEN a mutation that moves money is submitted, THE SYSTEM SHALL require an idempotency key
and apply the operation at most once per key.

**Acceptance**
- [ ] Every mutation in the §4 idempotent-operation registry takes a non-null `idempotencyKey: String!`
- [ ] A first submission records the key, a fingerprint of the canonical request, and the response
- [ ] A repeat with the same key and the same fingerprint returns the original response without re-applying anything
- [ ] A repeat with the same key and a different fingerprint is refused with `IDEMPOTENCY_KEY_REUSED` and applies nothing
- [ ] The guard is `SET idem:{key} NX EX 86400` in Redis **and** a unique index on the persisted intent — a Redis flush cannot cause a second application
- [ ] Two parallel submissions of one key produce exactly one application (ET-PLT-006 R5)
- [ ] The fingerprint excludes the key itself and any field the client may legitimately vary on retry

### ET-PLT-007-R7 · A revoked session stops working

WHEN a session is terminated or an account disabled, THE SYSTEM SHALL stop accepting that
actor's credentials within the access-token lifetime.

**Acceptance**
- [ ] Backchannel logout is configured for every confidential client and each service invalidates its cached session state on receipt
- [ ] A disabled Keycloak account cannot refresh, and its access token stops working within 5 minutes
- [ ] An organization member removed in MongoDB loses organization-scoped access on the **next request**, not on the next token — because organization role is resolved per request (R4, ET-ORG-003)
- [ ] Logout revokes the refresh token at Keycloak, not only in client storage
- [ ] A test asserts the removed-member case at sub-second latency

## 4. Model

### Realm

| Setting | Value |
|---|---|
| Realm | `event-ticketing` |
| Issuer | `${KEYCLOAK_URL}/realms/event-ticketing` |
| JWKS | `${KEYCLOAK_URL}/realms/event-ticketing/protocol/openid-connect/certs` |
| Access-token lifetime | 5 min |
| Default role | `CUSTOMER` |

### Realm roles and composites

| Role | Includes | Holds |
|---|---|---|
| `SUPER_ADMIN` | `ADMIN` | everything, including platform configuration |
| `ADMIN` | `FINANCE` | approvals, suspensions, all-tenant reads |
| `FINANCE` | — | payouts, refunds, reconciliation, ledger reads |
| `ORGANIZER` | `CUSTOMER` | create events, request payouts — **within their own organizations only**, which R4 enforces |
| `CUSTOMER` | — | browse, buy, transfer, validate-if-granted |

`ORGANIZER` is a *kind of actor*, not a permission over a particular organization.
Everything organization-scoped is decided by
[ET-ORG-003](../../organization/003-permission-resolution/).

### Clients

| Client | Type | Flow | Refresh lifetime | Used by |
|---|---|---|---|---|
| `event-ticketing-web` | public | authorization code + PKCE | 30 min | customer web |
| `event-ticketing-admin` | public | authorization code + PKCE | 30 min | admin, organizer console |
| `event-ticketing-mobile` | public | authorization code + PKCE | 30 days | Expo app |
| `internal-service` | confidential | client credentials | — | keycloak-extensions → identity |
| `catalog-service` | confidential | client credentials | — | service-to-service |
| `booking-service` | confidential | client credentials | — | service-to-service |
| `identity-service` | confidential | client credentials | — | service-to-service |

Scopes: `internal-read`, `internal-write`, plus `openid profile phone`.

### Keycloak groups — a mirror, never an authority

```
/organizations/{org-slug}/{owners|admins|managers|marketers|contributors}
```

Written from MongoDB by [ET-ORG-002](../../organization/002-teams-and-invitations/) when
membership changes. Used for coarse partitioning and administrative visibility. **No
authorization decision reads them**, because a token minted before a change carries the
old set.

### User attributes

| Attribute | Meaning |
|---|---|
| `phone_number` | E.164; the login identity |
| `phone_verified` | set by the OTP authenticator (ET-IDN-001) |
| `primary_org_id` | convenience for client routing; never an authorization input |

`User.id` in MongoDB **is** the Keycloak user ID. There is no `keycloakUserId` field.

### Operation-gate registry

Every `Query` and `Mutation` field carries one of these. Fields not listed inherit the row
for their capability's spec and are enumerated there.

| Requirement | Applies to |
|---|---|
| `PUBLIC` | event discovery, category and location reads, invitation-by-token lookup |
| `AUTHENTICATED` | `me`, my tickets, my notifications, my organizations, ticket purchase |
| `CUSTOMER` | reserve, purchase, transfer, request refund |
| `ORGANIZER` | create/publish/cancel an event, manage tiers, invite members, request payout, manage bank accounts |
| `FINANCE` | approve payouts, issue refunds, reconciliation, ledger and journal reads |
| `ADMIN` | approve/reject organizers and events, suspend organizations, read across tenants, transaction recovery |
| `SUPER_ADMIN` | platform configuration, feature flags, commission defaults, platform accounts |
| `INTERNAL` | nothing on the graph — the internal surface is REST |

### The internal REST surface

| Path | Scope | Purpose | Spec |
|---|---|---|---|
| `POST /api/internal/otp/request` | `internal-write` | generate and send an OTP | ET-IDN-001 |
| `POST /api/internal/otp/verify` | `internal-write` | verify an OTP | ET-IDN-001 |
| `GET /api/internal/otp/status/{phone}` | `internal-read` | cooldown state | ET-IDN-001 |
| `DELETE /api/internal/otp/{phone}` | `internal-write` | invalidate | ET-IDN-001 |
| `POST /api/internal/keycloak/sync/user` | `internal-write` | sync one user | ET-IDN-002 |
| `POST /api/internal/keycloak/sync/event` | `internal-write` | process a Keycloak event | ET-IDN-002 |
| `POST /api/internal/permissions/resolve` | `internal-read` | resolve a permission for booking/catalog | ET-ORG-003 |

No other internal path exists.

### Tenant scoping

```java
// the permitted set, resolved once per request and carried on the ProcessingContext
public interface TenantScope {
    Set<String> permittedOrganizationIds();   // empty for a customer
    boolean isPlatformWide();                 // ADMIN and above only
}

// the only shape a tenant-scoped repository method may take
Flux<PayoutRequest> findForScope(TenantScope scope, PayoutStatus status) {
    Criteria c = Criteria.where("status").is(status);
    if (!scope.isPlatformWide()) {
        c = c.and("organizationId").in(scope.permittedOrganizationIds());
    }
    return template.find(Query.query(c), PayoutRequest.class);
}
```

The `isPlatformWide` branch is the **one** bypass, and taking it writes an audit row
([ET-PLT-009](../009-audit-trail/)).

### Idempotent-operation registry

| Mutation | Subgraph | Spec |
|---|---|---|
| `reserveTickets` | booking | ET-TKT-001 |
| `confirmPurchase` | booking | ET-PAY-001 |
| `initiatePayment` | booking | ET-PAY-001 |
| `requestRefund` | booking | ET-FIN-004 |
| `approveRefund` | booking | ET-FIN-004 |
| `requestPayout` | booking | ET-FIN-003 |
| `approvePayout` | booking | ET-FIN-003 |
| `retryPayout` | booking | ET-FIN-003 |
| `transferTicket` | booking | ET-TKT-004 |

Nine operations. A mutation that moves money and is not listed here is a defect.

The fingerprint is a SHA-256 over the canonicalised input with `idempotencyKey` removed
and with fields the client may legitimately vary — `clientTimestamp`, `deviceId` — excluded
by an explicit allowlist rather than by omission.

### Redis keys

| Key | TTL | Purpose | Authority |
|---|---|---|---|
| `idem:{key}` | 24 h | in-flight guard + cached response | the persisted intent's unique index |

### Error codes used

`ACTOR_NOT_AUTHENTICATED`, `ACTOR_NOT_PERMITTED`, `IDEMPOTENCY_KEY_REUSED` — all rows of
[ET-PLT-005 §4](../005-error-contract/), introduced by this spec.

## 5. Tasks

- [ ] **T1 · Realm export: roles, composites, clients, scopes, PKCE, redirect allowlists**
  - requirements: R1
  - files: `../docker-resources/keycloak/event-ticketing-realm.json`
  - verify: importing the export into a clean Keycloak reproduces §4 exactly
  - parallel-safe: no — one realm
  - depends: —

- [ ] **T2 · One JWT converter and one decoder configuration in `shared-library`**
  - requirements: R2
  - files: `backend/shared-library/src/main/java/com/pml/shared/security/`
  - verify: forged, wrong-issuer, wrong-audience and expired tokens are each rejected directly at a service
  - parallel-safe: no — every service imports it
  - depends: T1

- [ ] **T3 · `@auth` on every `Query`/`Mutation` field; the enumeration test**
  - requirements: R3
  - files: the three `schema.graphqls`, `shared-library/.../graphql/auth/`
  - verify: a field without `@auth` fails the build; a denied field performs no repository call
  - parallel-safe: yes — one service per agent
  - depends: T2

- [ ] **T4 · `TenantScope`; move every tenant check into the repository filter**
  - requirements: R4
  - files: every tenant-scoped repository in booking and identity
  - verify: permission resolution exists in exactly one implementation, `/api/internal/**` is scope-gated, and no `User.keycloakUserId` exists; the cross-tenant iteration test returns nothing
  - parallel-safe: no — the resolution is shared
  - depends: T2

- [ ] **T5 · Lock down `/api/internal/**`; `client_credentials` for every caller**
  - requirements: R5
  - files: `backend/*/src/main/java/com/pml/*/config/security/SecurityConfig.java`
  - verify: 401 unauthenticated, 403 with a user token, 200 with an internal scope, per path
  - parallel-safe: yes — one service per agent
  - depends: T1

- [ ] **T6 · `IdempotencyGuard`: Redis + unique index, fingerprint, three outcomes**
  - requirements: R6
  - files: `backend/shared-library/.../security/IdempotencyGuard.java`, booking's mutations
  - verify: replay returns the original; a changed body refuses; two parallel submissions apply once
  - parallel-safe: no — one guard, nine call sites
  - depends: T2

- [ ] **T7 · Backchannel logout; the removed-member latency test**
  - requirements: R7
  - files: `backend/*/src/main/java/com/pml/*/config/security/`, realm client config
  - verify: a removed member loses access on the next request; a disabled account stops within 5 minutes
  - parallel-safe: yes
  - depends: T1, T4

## 6. Out of scope

| Capability | Spec |
|---|---|
| The `ReactiveJwtDecoder` bean's declaration and the gateway's role | [ET-PLT-001](../001-runtime-baseline/) |
| The error codes this spec raises | [ET-PLT-005](../005-error-contract/) |
| How an OTP is generated, delivered and verified | [ET-IDN-001](../../identity/001-phone-otp-identity/) |
| How Keycloak users reach MongoDB | [ET-IDN-002](../../identity/002-keycloak-user-sync/) |
| Organization membership, groups and the mirror that writes them | [ET-ORG-002](../../organization/002-teams-and-invitations/) |
| The permission resolution algorithm and event access grants | [ET-ORG-003](../../organization/003-permission-resolution/) |
| What the platform-wide bypass writes to the audit trail | [ET-PLT-009](../009-audit-trail/) |
| Rate limits, quotas, bot defence, on-sale queueing | [ET-PLT-011](../011-rate-limiting-and-abuse/) |
| PII handling and erasure | [ET-PLT-008](../008-data-protection/) |

Deliberately never in scope: **reading organization role from the JWT** (stale for a token
lifetime after every change), and **a shared static secret for service-to-service calls**
(unrotatable, unscoped, and in every header dump).
