# ET-IDN-003 · Token revocation — cutting a live session before its token expires

> **Conformance** · OWASP ASVS 3.3 session termination · PDI Phase 5 idempotency and abuse

## 1. Capability

A JWT is a bearer credential that is valid because of its signature, not because anybody
still consents to it. Between the moment a token is issued and the moment it expires, the
platform has no opinion about it at all — which means a stolen phone, a fired staff
member, a compromised organizer account or a revoked event-scanner grant all remain fully
authorised for the remainder of the token lifetime. At a one-hour lifespan that is a
one-hour window in which "remove this person's access" is a statement the platform cannot
honour.

This capability closes that window. Identity-service holds a durable revocation list;
every service checks it on every authenticated request; and a revocation takes effect
across the whole platform within seconds rather than at the next token expiry. Revocation
works at three granularities — one token, one SSO session, or every token a user holds —
because "log out this device", "end this session" and "this account is compromised" are
three different operations with three different blast radii.

The check is on the hot path of every request in the platform, so it is a Redis lookup
with a MongoDB system of record behind it, and it **fails closed**: if the platform cannot
prove a token is still good, it refuses rather than assuming.

## 2. Design decisions

**Three revocation granularities, keyed on three JWT claims.** `TOKEN` revokes one access
token by its `jti`. `SESSION` revokes every token minted for one Keycloak SSO session by
its `sid`. `USER` revokes every token a subject holds by its `sub`. A single check
evaluates all three identifiers present on the presented token, and any match refuses.
Without `SESSION`, "sign out this device" cannot be expressed; without `USER`, a
compromised account cannot be cut off without enumerating its live tokens.

**MongoDB is the system of record and Redis is a cache.** This is the one place the
platform's own rule — *Redis never holds business state* — could plausibly be argued away,
because a revocation is arguably ephemeral. It is not: losing a revocation silently
restores access that somebody deliberately removed, which is the worst failure this
capability has. The durable record lives in `identity_token_revocations`; Redis holds a
copy for the hot path, and a flushed Redis costs latency, never correctness.

**The cache must be eviction-safe, and the platform verifies that at boot.** A revocation
cache under `allkeys-lru` is a cache that silently drops entries under memory pressure —
and the entry it drops is a token that becomes valid again. Redis must run `noeviction` or
a `volatile-*` policy that can only evict keys carrying a TTL. The platform probes
`maxmemory-policy` on startup and refuses to trust the cache when the policy permits
evicting a key it did not expire itself.

**The check fails closed, and only for operations that warrant it.** Failing closed on
every request means a Redis blip logs every user out of browsing the event catalogue. So
the guard is explicit: operations annotated as sensitive — anything that moves money,
changes a role, issues or validates a ticket, or alters an organization — refuse when the
revocation state cannot be established. Ordinary reads degrade to the cached answer. The
default for an unannotated money-moving mutation is a defect, not a lenient default.

**A revocation row lives exactly as long as the token it revokes, plus clock skew.** Once
the revoked token's own `exp` has passed, the row proves nothing that the signature check
does not already prove, and keeping it grows an unbounded collection on the hot path. A
TTL index removes it. The retention is `accessTokenLifespan + clockSkew`, not a guess.

**Revocation propagates over the bus, and the cache also self-heals.** Identity publishes
`identity.TokenRevoked`; each service applies it to its local cache immediately. But bus
delivery is at-least-once and unordered, so each service additionally re-warms from the
durable store on an interval — the event is the fast path and the warm is the correct one.
A design that trusts only the event has a revocation that silently fails to arrive.

**Keycloak logout is an input, not the mechanism.** Keycloak can end an SSO session, but
it cannot invalidate an access token already in a client's hands, and the subgraphs
validate signatures offline against JWKS by design ([ET-PLT-001](../../_platform/001-runtime-baseline/) R7).
Revocation therefore has to be the platform's own list. Keycloak's logout event feeds it.

**Rejected alternatives**

- *Short token lifetimes instead of a revocation list.* Pushes the window down but never to zero, and multiplies token endpoint traffic by the same factor. A five-minute token still leaves five minutes of a compromised session, and mobile clients on intermittent connections suffer most.
- *Introspection against Keycloak on every request.* Makes Keycloak a synchronous dependency of every authenticated request in the platform, converting an outage there into a total outage here.
- *Redis alone, no durable store.* A `FLUSHALL`, an eviction or a restart without persistence silently restores every revoked session.
- *Failing closed on every request.* One Redis blip becomes a platform-wide logout; the cure is worse than the window.
- *Revoking by `sub` only.* Cannot express "sign out this one device", so every device logout becomes a full logout everywhere.

## 3. Requirements

### ET-IDN-003-R1 · A token can be revoked before it expires, at three granularities

THE SYSTEM SHALL revoke access by `jti`, by `sid` or by `sub`, and WHEN a revoked token is
presented THEN THE SYSTEM SHALL refuse the request.

**Acceptance**
- [ ] Revoking by `TOKEN` refuses exactly the token whose `jti` matches, and no other token held by the same user
- [ ] Revoking by `SESSION` refuses every token carrying that `sid`, including tokens minted after the revocation
- [ ] Revoking by `USER` refuses every token carrying that `sub`, across every device and session
- [ ] A token carrying none of the revoked identifiers is unaffected
- [ ] A revocation takes effect on the next request; no test waits for a token to expire
- [ ] Every revocation records `reason`, `revokedBy` and `revokedAt`, and `reason` is at least 20 characters
- [ ] Revoking an already-revoked identifier is idempotent and does not extend or duplicate the record

### ET-IDN-003-R2 · Every service checks revocation on every authenticated request

THE SYSTEM SHALL evaluate the revocation list in each service independently, and no
service SHALL rely on another having checked.

**Acceptance**
- [ ] All three subgraphs and the gateway perform the check; a request sent directly to a subgraph, bypassing the gateway, is still refused
- [ ] The check evaluates every revocation identifier present on the token in one lookup, not three round trips
- [ ] A refused request returns `TOKEN_REVOKED` with `retryable: false`, and never the provider or store's raw error
- [ ] The check adds no more than 5 ms at p99 to an authenticated request when the cache is warm
- [ ] The check runs after signature validation, never before — an unsigned token is rejected without touching the store
- [ ] A test asserts the refusal persisted nothing

### ET-IDN-003-R3 · MongoDB is authoritative; Redis is a cache that may be lost

THE SYSTEM SHALL hold the durable revocation record in MongoDB, and IF the cache is
emptied THEN THE SYSTEM SHALL continue to refuse every revoked token.

**Acceptance**
- [ ] `identity_token_revocations` is the system of record; no revocation exists only in Redis
- [ ] Flushing Redis mid-suite loses no revocation — a previously revoked token is still refused afterwards
- [ ] The cache is rebuilt from MongoDB on startup before the service reports ready
- [ ] Each service re-warms from the durable store on a bounded interval, so a missed event self-heals
- [ ] A cache entry is written with a TTL that never outlives the durable record
- [ ] The durable store is reachable from the other two services over the internal API only, never by reading identity's collection

### ET-IDN-003-R4 · The cache is eviction-safe, and the platform proves it at boot

THE SYSTEM SHALL verify that Redis cannot evict a revocation entry it did not expire, and
IF the policy permits it THEN THE SYSTEM SHALL treat the cache as untrusted.

**Acceptance**
- [ ] The service probes `maxmemory-policy` at startup
- [ ] `noeviction`, `volatile-lru`, `volatile-lfu`, `volatile-ttl` and `volatile-random` are accepted
- [ ] `allkeys-lru`, `allkeys-lfu` and `allkeys-random` are refused as unsafe, because they can evict a key the platform did not expire
- [ ] Under an unsafe policy the service starts but marks the cache untrusted, and every sensitive check goes to the durable store
- [ ] The probe result is exposed on the health endpoint and is a named condition, not a boolean
- [ ] A test asserts an unsafe policy produces an untrusted cache rather than a silent downgrade

### ET-IDN-003-R5 · The check fails closed for sensitive operations

THE SYSTEM SHALL refuse a sensitive operation WHEN revocation state cannot be
established, and SHALL degrade to the cached answer for ordinary reads.

**Acceptance**
- [ ] Every money-moving mutation, role change, ticket issuance and ticket validation is marked sensitive
- [ ] A sensitive operation refuses with `REVOCATION_UNAVAILABLE` and `retryable: true` when neither cache nor durable store answers within its timeout
- [ ] An ordinary read proceeds on the cached answer when the durable store is unreachable
- [ ] The cache timeout and the durable timeout are separate, configured values, and the durable one is the longer
- [ ] A money-moving mutation that is not marked sensitive fails review — the list of sensitive operations is enumerated in §4, not inferred
- [ ] A test asserts a sensitive operation refuses, and an ordinary read succeeds, under the same induced store outage

### ET-IDN-003-R6 · A revocation propagates platform-wide within a bounded window

THE SYSTEM SHALL publish every revocation to the other services, and the time from
revocation to platform-wide effect SHALL be bounded and asserted.

**Acceptance**
- [ ] Revoking publishes `identity.TokenRevoked` to `identity-events` with the full envelope of [ET-PLT-003](../../_platform/003-event-contract/)
- [ ] Each consuming service applies it to its local cache and is idempotent on `eventId`
- [ ] A revocation is effective in every service within 5 seconds at p99, asserted end to end
- [ ] A service that missed the event still refuses the token after its next re-warm
- [ ] The event carries the identifier and its type, never the token itself and never the reason text
- [ ] Publication happens from an `@TransactionalEventListener(AFTER_COMMIT)` after commit, never inside the write transaction

### ET-IDN-003-R7 · Revocation is triggered by the events that require it

THE SYSTEM SHALL revoke automatically on the security events that invalidate existing
access, and SHALL NOT require an administrator to remember.

**Acceptance**
- [ ] Keycloak logout revokes that `sid`
- [ ] A password or credential change revokes that `sub`
- [ ] Removing an organization member revokes that member's `sub` ([ET-ORG-002](../../organization/002-teams-and-invitations/))
- [ ] Revoking an event access grant revokes the affected sessions, so a removed scanner cannot admit ([ET-ORG-003](../../organization/003-permission-resolution/))
- [ ] Suspending an organization revokes every member's `sub` ([ET-ORG-001](../../organization/001-organizer-onboarding/))
- [ ] Account deletion or erasure revokes that `sub` ([ET-PLT-008](../../_platform/008-data-protection/))
- [ ] Each automatic revocation writes an audit row naming the trigger ([ET-PLT-009](../../_platform/009-audit-trail/))

### ET-IDN-003-R8 · A revocation row outlives its token, and no longer

THE SYSTEM SHALL retain a revocation record until the revoked token could no longer be
presented, and SHALL then remove it.

**Acceptance**
- [ ] `expiresAt` is `revokedAt + accessTokenLifespan + clockSkew`
- [ ] A TTL index on `expiresAt` with `expireAfterSeconds: 0` removes the row
- [ ] A `USER` or `SESSION` revocation covers tokens minted up to its `expiresAt`, so re-issue after a ban is refused for the full window
- [ ] The collection does not grow without bound; a test asserts a row is gone after its window plus the sweep interval
- [ ] Clock skew is configured, not assumed zero — a token issued by a Keycloak whose clock is 30 s ahead is still covered
- [ ] Removal of the row is not itself a revocation event and publishes nothing

## 4. Model

### Collection

`identity_token_revocations` — the system of record.

| Field | Type | Notes |
|---|---|---|
| `_id` | `String` | |
| `type` | `RevocationType` | `TOKEN`, `SESSION`, `USER` |
| `value` | `String` | the `jti`, `sid` or `sub` |
| `reason` | `String` | required, ≥ 20 characters |
| `revokedBy` | `String` | actor id, or `system` for an automatic trigger |
| `trigger` | `RevocationTrigger` | see below |
| `revokedAt` | `Instant` | from the `Clock` bean |
| `expiresAt` | `Instant` | `revokedAt + accessTokenLifespan + clockSkew` |

### Indexes

| Index | Kind | Why |
|---|---|---|
| `{ type: 1, value: 1 }` | **unique** | one live record per identifier; the race a check-then-insert cannot win |
| `{ expiresAt: 1 }` | **TTL, `expireAfterSeconds: 0`** | the row dies with the token it revokes |
| `{ revokedAt: -1 }` | single | the admin revocation log |

### Enums

| Type | Values |
|---|---|
| `RevocationType` | `TOKEN` (`jti`), `SESSION` (`sid`), `USER` (`sub`) |
| `RevocationTrigger` | `LOGOUT`, `CREDENTIAL_CHANGE`, `MEMBER_REMOVED`, `GRANT_REVOKED`, `ORGANIZATION_SUSPENDED`, `ACCOUNT_ERASED`, `ADMIN_REVOKE`, `SUSPECTED_COMPROMISE` |

### Redis keys

| Key | TTL | Authority |
|---|---|---|
| `revoked:token:{jti}` | token lifespan + skew | `identity_token_revocations` |
| `revoked:session:{sid}` | token lifespan + skew | `identity_token_revocations` |
| `revoked:user:{sub}` | token lifespan + skew | `identity_token_revocations` |
| `revoked:complete` | 10 min | the cache-completeness marker; absent means untrusted |

**Redis holds no revocation that MongoDB does not.** A flush costs latency, never
correctness.

### Events

| Wire name | Topic | Payload |
|---|---|---|
| `identity.TokenRevoked` | `identity-events` | `type`, `value`, `expiresAt` — never the token, never the reason text |

### GraphQL

`backend/identity-service/src/main/resources/graphql/schema.graphqls`

| Operation | Role | Returns |
|---|---|---|
| `revokeToken(jti, reason)` | `ADMIN` `@tag(admin)` | `RevocationRecord!` |
| `revokeSession(sid, reason)` | `ADMIN` `@tag(admin)` | `RevocationRecord!` |
| `revokeUserAccess(userId, reason)` | `ADMIN` `@tag(admin)` | `RevocationRecord!` |
| `signOutEverywhere` | authenticated | `Boolean!` — revokes the caller's own `sub` |
| `signOutSession(sid)` | authenticated | `Boolean!` — the caller's own sessions only |
| `activeRevocations(type, page)` | `ADMIN` `@tag(admin)` | `RevocationPage!` |

### Internal API

| Endpoint | Method | Purpose |
|---|---|---|
| `/api/internal/revocations/check` | POST | the durable check, for a service whose cache is untrusted |
| `/api/internal/revocations/since/{instant}` | GET | the re-warm feed |

Scope-gated to `SCOPE_internal-read` / `ROLE_INTERNAL_SERVICE` per
[ET-PLT-007](../../_platform/007-security-and-authorization/).

### Sensitive operations — the fail-closed list

Every money-moving mutation of [ET-PAY-001](../../payment/001-payment-intents-and-providers/),
[ET-FIN-003](../../finance/003-payouts-and-settlement/) and
[ET-FIN-004](../../finance/004-refunds-and-chargebacks/); ticket issuance
([ET-TKT-002](../../ticketing/002-ticket-issuance-and-qr/)); ticket validation
([ET-TKT-003](../../ticketing/003-validation-and-checkin/)); every role, member and grant
mutation ([ET-ORG-002](../../organization/002-teams-and-invitations/),
[ET-ORG-003](../../organization/003-permission-resolution/)); and every mutation in
[ET-ADM-002](../../admin/002-platform-configuration/) and
[ET-ADM-003](../../admin/003-transaction-recovery/).

### Accepted Redis eviction policies

| Policy | Verdict |
|---|---|
| `noeviction`, `volatile-lru`, `volatile-lfu`, `volatile-ttl`, `volatile-random` | trusted |
| `allkeys-lru`, `allkeys-lfu`, `allkeys-random` | **untrusted** — can evict a key the platform did not expire |

### Error codes

| Code | `ErrorType` | Retryable |
|---|---|---|
| `TOKEN_REVOKED` | `UNAUTHENTICATED` | no |
| `REVOCATION_UNAVAILABLE` | `UNAVAILABLE` | yes |

### Configuration

| Key | Default |
|---|---|
| `identity.revocation.enabled` | `true` |
| `identity.revocation.access-token-lifespan` | `PT1H` |
| `identity.revocation.clock-skew` | `PT60S` |
| `identity.revocation.cache-timeout` | `PT0.25S` |
| `identity.revocation.durable-timeout` | `PT2S` |
| `identity.revocation.require-eviction-safe-cache` | `true` |
| `identity.revocation.cache-completeness-ttl` | `PT10M` |
| `identity.revocation.cache-warm-interval` | `PT2M` |

## 5. Tasks

- [ ] **T1 · The document, its three indexes and the durable store**
  - requirements: R1, R8
  - files: `backend/identity-service/.../revocation/`
  - verify: the unique index refuses a duplicate identifier; the TTL index removes an expired row
  - parallel-safe: no — everything else reads this
  - depends: —

- [ ] **T2 · The revocation check: one lookup over all three identifiers**
  - requirements: R1, R2
  - files: `backend/shared-library/.../security/revocation/`
  - verify: revoking by each type refuses exactly the intended tokens and no others
  - parallel-safe: no — shared by all three services
  - depends: T1

- [ ] **T3 · The Redis cache, its completeness marker, and the boot warm**
  - requirements: R3
  - files: `backend/shared-library/.../security/revocation/`, `backend/identity-service/.../revocation/`
  - verify: a mid-suite `FLUSHALL` still refuses a previously revoked token
  - parallel-safe: no — cache and warm together
  - depends: T2

- [ ] **T4 · The eviction-policy probe and the untrusted-cache path**
  - requirements: R4
  - files: `backend/shared-library/.../security/revocation/`
  - verify: `allkeys-lru` yields an untrusted cache and a health condition naming it
  - parallel-safe: yes
  - depends: T3

- [ ] **T5 · The sensitive-operation guard; fail closed, degrade for reads**
  - requirements: R5
  - files: `backend/shared-library/.../security/revocation/`, every sensitive resolver
  - verify: under an induced store outage a payout mutation refuses and an event query succeeds
  - parallel-safe: no — touches every sensitive operation
  - depends: T4

- [ ] **T6 · Publish `identity.TokenRevoked`; idempotent consumers in the other two services**
  - requirements: R6
  - files: `backend/identity-service/.../revocation/`, `backend/{catalog,booking}-service/.../event/listener/`
  - verify: a revocation is effective in all three services within 5 s at p99
  - parallel-safe: yes — one consumer per agent
  - depends: T3

- [ ] **T7 · The automatic triggers, each with its audit row**
  - requirements: R7
  - files: `backend/identity-service/.../{organization,team,permission}/`, `backend/keycloak-extensions/`
  - verify: removing a member refuses that member's next request; suspending an organization refuses every member
  - parallel-safe: yes — one trigger per agent
  - depends: T6

- [ ] **T8 · The GraphQL surface, the internal API and the two error codes**
  - requirements: R1, R2, R5
  - files: `backend/identity-service/src/main/resources/graphql/schema.graphqls`, `.../revocation/`
  - verify: `signOutEverywhere` refuses the caller's other tokens; every admin field carries `@tag(admin)`
  - parallel-safe: yes
  - depends: T5

## 6. Out of scope

| Capability | Spec |
|---|---|
| Realm configuration, roles, `@auth`, JWT validation itself | [ET-PLT-007](../../_platform/007-security-and-authorization/) |
| The OTP lifecycle and passwordless login | [ET-IDN-001](../001-phone-otp-identity/) |
| Keycloak ↔ MongoDB user synchronisation | [ET-IDN-002](../002-keycloak-user-sync/) |
| Permission resolution and event access grants | [ET-ORG-003](../../organization/003-permission-resolution/) |
| Rate limiting, temporary blocks and abuse control | [ET-PLT-011](../../_platform/011-rate-limiting-and-abuse/) |
| The audit trail this writes into | [ET-PLT-009](../../_platform/009-audit-trail/) |
| Refresh-token rotation | Keycloak's own concern; the platform never sees a refresh token |

Deliberately never in scope: **token introspection against Keycloak on the request path**
(makes Keycloak a synchronous dependency of every authenticated request), and **shortening
token lifetimes as a substitute** (narrows the window, never closes it, and multiplies
token-endpoint traffic by the same factor).
