# ET-IDN-003 · Token revocation — cutting a live session before its token expires

> **Conformance** · OWASP ASVS 3.3 session termination · PDI Phase 5 idempotency and abuse
>
> **Amended 2026-10-04 (D-44, D-45, D-47; F-044).** The access token now lives **5 minutes** (was one hour in the
> examples below; `identity.revocation.access-token-lifespan` is `PT5M`), buyer tokens are held in the buyer app's
> **server-side session** and never in a browser, and three new triggers revoke automatically: **contact change**,
> **suspension** and **deletion**. Logout is end to end (R9 below).

> **Amended 2026-10-11.** Revocation propagates through the shared Redis keys and the MongoDB record,
> not through a bus event: there is no `identity.TokenRevoked`, no outbox row and no event-store document
> (R6). A single-token revocation is remembered only until that token's own `exp` plus skew; a session or user
> revocation for one lifespan plus skew (R8). An expired token is refused by the lifetime stage of the check
> pipeline before any store is asked (R2). The GraphQL surface keeps the existing self-service `mySessions` and
> `revokeSession(sessionId)` as the sessions list and `signOutSession`, and adds `signOutEverywhere` (the caller's
> *other* sessions), the administrator operations `revokeToken`, `revokeSessionAccess` and `revokeUserAccess`, and
> `activeRevocations`.

> **Status 2026-10-11 · `implemented`.** Every requirement below is built and tested against throwaway
> MongoDB, Redis and Keycloak containers, and the full backend verify is green. Two decisions closed it:
> accounts are not merged (ET-IDN-004 R6 is withdrawn, F-049), so there is no merge trigger and no
> `ACCOUNT_MERGED` value; and contact one-time-code sign-in (ET-IDN-001) is not a prerequisite, because
> revocation acts on the token's `sid`, `sub` and `jti` whatever way the person signed in. The buyer and
> organizer sessions screens are a front-end follow-up (ET-IDN-003 FE-3), not part of this spec's backend
> acceptance.

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

**Revocation propagates through the shared cache, and the cache also self-heals.** The gateway and
every service read the same Redis keys, so identity's write of a key is the propagation: the next request
anywhere sees it, with no message to deliver or lose. A revocation is deliberately **not** a domain event: it
is a security control, not business history, and a second channel would only be one more way for the record
and the cache to disagree. Because a key can still be lost (a flush, a failed write), identity re-warms the
shared keys from MongoDB at startup, before it reports ready, and on an interval; until the completeness
marker is back, every miss is answered from MongoDB.

**A key lives exactly as long as what it blocks can be presented.** A `TOKEN` revocation matches one token,
so it is kept until that token's own `exp` plus skew, never beyond a full lifespan; a token already expired
holds only the skew. A `SESSION` or `USER` revocation also matches tokens minted later, so it is kept for a
full lifespan plus skew from the moment of revocation. The Redis key's TTL is the remaining life of the
MongoDB record, so the cache can never outlive the system of record.

**The pipeline is signature, then lifetime, then revocation.** The decoder refuses a bad signature, a wrong
issuer and a token with no `exp`. The next stage refuses a token whose `exp` has passed beyond the decoder's
30-second tolerance as unauthenticated (`ACTOR_NOT_AUTHENTICATED`, not `TOKEN_REVOKED`), without touching
Redis or MongoDB. Only a token still inside its lifetime is looked up.

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
- [x] Revoking by `TOKEN` refuses exactly the token whose `jti` matches, and no other token held by the same user
  - *2026-10-09: `LogoutRevokesTokenEndToEndTest.tokenRevocationIsExact` — a real token is refused, the next token of the same session is not*
- [x] Revoking by `SESSION` refuses every token carrying that `sid`, including tokens minted after the revocation
  - *2026-10-09: `sessionRevocationCoversLaterTokens`*
- [x] Revoking by `USER` refuses every token carrying that `sub`, across every device and session
  - *2026-10-09: `userRevocationCutsEverySession`, including a token Keycloak issues after the revocation*
- [x] A token carrying none of the revoked identifiers is unaffected
  - *2026-10-09: `logoutCutsOnlyThatSession` — the user's other session is untouched*
- [x] A revocation takes effect on the next request; no test waits for a token to expire
- [x] Every revocation records `reason`, `revokedBy`, `trigger` and `revokedAt`, and `reason` is at least 20 characters
  - *2026-10-11: an administrator's reason is refused under 20 characters after trimming (`RevocationLifecycleTest.manualRevocationNeedsAWrittenReason`, `AccessRevocationEndToEndTest.administratorMustExplain`); a machine trigger is stored with its description so no stored reason is shorter than a sentence (`machineReasonIsExplained`); the text is cut at 500 characters (`reasonIsBounded`)*
- [x] Revoking an already-revoked identifier is idempotent and does not extend or duplicate the record
  - *2026-10-09: `MongoRevocationStore` inserts once and returns the active record unchanged; `repeatedEventIsIdempotent` asserts one row with the same `revokedAt`, `expiresAt` and reason*

### ET-IDN-003-R2 · Every service checks revocation on every authenticated request

THE SYSTEM SHALL evaluate the revocation list in each service independently, and no
service SHALL rely on another having checked.

**Acceptance**
- [x] All three subgraphs and the gateway perform the check; a request sent directly to a subgraph, bypassing the gateway, is still refused
  - *2026-09-19: catalog, booking and identity install `RevocationRequestGuard` after authentication.*
  - *2026-10-09: the gateway now runs the same `CachedRevocationCheck` (completeness marker, then identity's durable records) instead of a Redis-only lookup that failed open; a state-changing request is refused with 503 when no store answers (`GatewayRevocationEnforcementTest`, `SessionBlacklistFilterSidTest`).*
- [x] The check evaluates every revocation identifier present on the token in one lookup, not three round trips *(2026-09-19: `RemoteRevocationEnforcementTest`)*
- [x] A refused request returns `TOKEN_REVOKED` with `retryable: false`, and never the provider or store's raw error
  - *2026-10-11: `AccessRevocationEndToEndTest.refused` asserts the body on a real token; `RemoteRevocationEnforcementTest.refusalHidesTheStoreError`*
- [x] An expired token is refused as unauthenticated before any store is asked, and is not reported as revoked
  - *2026-10-11: `RemoteRevocationEnforcementTest.expiredTokenIsRefusedWithoutALookup`, `AccessRevocationEndToEndTest.expiredTokenIsNotARevocation`, `JwtClockSkewTest.tokenWithoutExpiryIsRefused`*
- [x] The check adds no more than 5 ms at p99 to an authenticated request when the cache is warm
  - *2026-10-11: `RevocationLifecycleTest.warmCheckIsFast`, 2 000 checks of three identifiers against a real Redis*
- [x] The check runs after signature validation, never before — an unsigned token is rejected without touching the store *(2026-09-19: `RemoteRevocationEnforcementTest`)*
- [x] A test asserts the refusal persisted nothing
  - *2026-10-11: `AccessRevocationEndToEndTest.administratorRevokesOneToken` counts rows across repeated refusals; `administratorMustExplain` and `nonAdministratorIsRefused` count rows across refused revocations*

### ET-IDN-003-R3 · MongoDB is authoritative; Redis is a cache that may be lost

THE SYSTEM SHALL hold the durable revocation record in MongoDB, and IF the cache is
emptied THEN THE SYSTEM SHALL continue to refuse every revoked token.

**Acceptance**
- [x] `identity_token_revocations` is the system of record; no revocation exists only in Redis
- [x] Flushing Redis mid-suite loses no revocation — a previously revoked token is still refused afterwards
  - *2026-10-09: `LogoutRevokesTokenEndToEndTest.flushedCacheStillRefuses`, `GatewayRevocationEnforcementTest.flushedCacheDoesNotAdmitARevokedToken`; the warmer then rebuilds the keys and the marker from MongoDB*
- [x] The cache is rebuilt from MongoDB on startup before the service reports ready
  - *2026-10-11: the warmer is an `ApplicationRunner`, which completes before readiness; `RevocationLifecycleTest.startupRebuildsTheCache`, `expiredRecordsAreNotReloaded`*
- [x] Identity re-warms the shared keys from the durable store on a bounded interval, so a lost key self-heals
  - *2026-10-11: `RevocationLifecycleTest.periodicReloadRepairsALostKey`; the interval must be at most half the completeness TTL or the platform refuses to start*
- [x] A cache entry is written with a TTL that never outlives the durable record
  - *2026-10-11: the key's TTL is the record's remaining life (`sessionAndUserKeepTheFullWindow`, `tokenTtlFollowsTheTokenExpiry`)*
- [x] The durable store is reachable from the other two services over the internal API only, never by reading identity's collection *(2026-09-19: `RemoteRevocationEnforcementTest`)*

### ET-IDN-003-R4 · The cache is eviction-safe, and the platform proves it at boot

THE SYSTEM SHALL verify that Redis cannot evict a revocation entry it did not expire, and
IF the policy permits it THEN THE SYSTEM SHALL treat the cache as untrusted.

**Acceptance**
- [x] The service probes `maxmemory-policy` at startup *(`RedisEvictionPolicyProbe`, on `ApplicationReadyEvent`)*
- [x] `noeviction`, and any policy while no `maxmemory` limit is set, are accepted *(amended 2026-10-09: the first draft also accepted `volatile-*`, but every revocation key carries a TTL, so a `volatile-*` policy evicts them exactly as `allkeys-*` does)*
- [x] Any evicting policy (`allkeys-*`, `volatile-*`) with `maxmemory` set is refused as unsafe, because it can evict a key the platform did not expire
- [x] Under an unsafe policy the service starts but marks the cache untrusted, and every check the cache cannot answer from a key goes to the durable store *(2026-10-09: `RevocationCacheTrust` consults the probe; before, the probe only changed the health status)*
- [x] The probe result is exposed on the health endpoint and is a named condition, not a boolean *(`cacheEvictionSafety`, `cacheMaxmemoryPolicy`, status `DEGRADED`)*
- [x] A test asserts an unsafe policy produces an untrusted cache rather than a silent downgrade *(`EvictionPolicyTrustTest`, against a Redis started with `allkeys-lru` and one with `noeviction`)*

### ET-IDN-003-R5 · The check fails closed for sensitive operations

THE SYSTEM SHALL refuse a sensitive operation WHEN revocation state cannot be
established, and SHALL degrade to the cached answer for ordinary reads.

**Acceptance**
- [x] Every money-moving mutation, role change, ticket issuance and ticket validation is marked sensitive
  - *2026-10-09: identity's member, role, grant, ownership, invitation, payout, document and credential mutations are marked; `SensitiveMutationsTest` (identity) lists every mutation not marked and fails when a new one is added without a decision*
  - *2026-09-19: booking's money-moving, check-in and recovery mutations and catalog's platform configuration are marked (`SensitiveMutationsTest` in each); identity's member, role and grant mutations are not yet.*
- [x] A sensitive operation refuses with `REVOCATION_UNAVAILABLE` and `retryable: true` when neither cache nor durable store answers within its timeout
  - *2026-10-11: `RemoteRevocationEnforcementTest.outageFailsOpenForReadsAndClosedForMoney`; the mapping to code and `retryable` is `RefusalTranslationTest`*
- [x] An ordinary read proceeds on the cached answer when the durable store is unreachable *(2026-09-19: `RemoteRevocationEnforcementTest`)*
- [x] The cache timeout and the durable timeout are separate, configured values, and the durable one is the longer
  - *2026-10-11: `RevocationTtlPolicyTest.durableTimeoutMustExceedCacheTimeout` — the platform refuses to start with the order reversed*
- [x] A money-moving mutation that is not marked sensitive fails review — the list of sensitive operations is enumerated in §4, not inferred
  - *2026-10-11: `SensitiveMutationsTest` in identity, catalog and booking lists every mutation not marked and fails when a new one is added without a decision; the new revocation operations are all marked*
- [x] A test asserts a sensitive operation refuses, and an ordinary read succeeds, under the same induced store outage *(2026-09-19: `RemoteRevocationEnforcementTest`)*

### ET-IDN-003-R6 · A revocation reaches every service within a bounded window, through the shared cache *(amended 2026-10-11)*

THE SYSTEM SHALL make a revocation effective in the gateway and every service by writing the MongoDB
record and the shared Redis key together, and the time from revocation to platform-wide effect SHALL be
bounded and asserted. THE SYSTEM SHALL NOT publish a domain event for a revocation.

**Acceptance**
- [x] Revoking writes the MongoDB record and the Redis key, and nothing else: no event, no outbox row, no event-store document
  - *2026-10-11: `RevocationLifecycleTest.nothingIsWrittenToAnEventStore` lists the database's collections*
- [x] Another service, with no route to MongoDB, refuses the token on its next request: effective within 5 seconds at p99, asserted over 100 revocations
  - *2026-10-11: `RevocationLifecycleTest.propagationIsBounded`*
- [x] A service whose cache lost the key still refuses the token, from MongoDB, and the next reload restores the key
  - *2026-10-11: `coldCacheIsAnsweredFromMongo`, `periodicReloadRepairsALostKey`, `LogoutRevokesTokenEndToEndTest.flushedCacheStillRefuses`*
- [x] The cached value is a constant marker: it never carries the token, the reason or who revoked
  - *2026-10-11: `RevocationLifecycleTest.cacheCarriesNoSecrets`*

### ET-IDN-003-R7 · Revocation is triggered by the events that require it

THE SYSTEM SHALL revoke automatically on the security events that invalidate existing
access, and SHALL NOT require an administrator to remember.

**Acceptance**
- [x] Keycloak logout revokes that `sid`: the `user-sync` listener forwards `LOGOUT`
  - *2026-10-09: `UserSyncEventListenerTest`, `KeycloakSyncControllerTest` (including the 503 retry) and `LogoutRevokesTokenEndToEndTest`; the listener plugin itself is not installed in the identity test container, so that test posts the event the listener would send* (and `REFRESH_TOKEN_ERROR`) with the session's `sid` to `POST /api/internal/keycloak/sync/event` ([ET-IDN-004](../004-accounts-and-contacts/) CONTRACT 4.6), and identity-service writes `SESSION:{sid}` (idempotent, durable first, no deletion, no personal data); an event with no `sid` revokes nothing, and a write failure answers `503` so the listener retries
- [x] The gateway refuses a token whose `sid` was revoked this way (401, `X-Token-Revoked`), still accepts the user's other sessions, and keys users on `sub`, never on the `accountId` claim
- [x] A successful **contact change** revokes the account's current sessions and ends them in Keycloak ([ET-IDN-004](../004-accounts-and-contacts/) R5)
  - *2026-10-11: the sessions are revoked, not the `sub`: the person signs in again with the new contact at once, and a subject revocation would refuse that sign-in for the whole window. `ContactChangeSteps.endSessions`; `ContactChangeWorkflowTest.changeEmail`, `AccessRevocationServiceTest.cutSessionsLeavesTheSubjectAlone`*
- [x] **Suspension** (`status` to `SUSPENDED`, including a console change adopted back, D-47) revokes that `sub` and disables the Keycloak user ([ET-IDN-004](../004-accounts-and-contacts/) R4)
  - *2026-10-11: `AccountService.suspend`; `AccountServiceTest`*
- [x] **Deletion** revokes the account's `sub` at the scheduled date ([ET-IDN-004](../004-accounts-and-contacts/) R7)
  - *2026-10-11: `AccountService.delete` revokes; `AccountServiceTest`. There is no merge trigger: accounts are not merged (ET-IDN-004 R6 withdrawn, F-049), so each way of signing in keeps its own account and its own revocations.*
- [x] A password or credential change revokes the person's other sessions and keeps the one that made the change
  - *2026-10-11: the listener forwards `UPDATE_PASSWORD`, `UPDATE_TOTP`, `REMOVE_TOTP` with the session id; `KeycloakSyncControllerTest`, `AccessRevocationServiceTest.credentialChangeKeepsTheChangingSession`. Revoking the whole `sub` was rejected here: it would also refuse the new sign-in the person makes straight after, for the whole window*
- [x] Removing an organization member revokes that member's `sub` ([ET-ORG-002](../../organization/002-teams-and-invitations/))
  - *2026-10-11: `OrganizationMemberServiceImpl.endMembership`; `AccessRevocationEndToEndTest.removingAMemberRefusesTheNextRequest` with a real Keycloak token*
- [x] Revoking an event access grant revokes the affected sessions, so a removed scanner cannot admit ([ET-ORG-003](../../organization/003-permission-resolution/))
  - *2026-10-11: `EventAccessMutationResolver.revokeEventAccess`; `RevokeEventAccessCutsTheGranteeTest`*
- [x] Suspending an organization revokes every member's `sub` ([ET-ORG-001](../../organization/001-organizer-onboarding/))
  - *2026-10-11: `OrganizationMutationResolver.suspendOrganization`; `AccessRevocationEndToEndTest.suspendingAnOrganizationRefusesEveryMember`, and `AccessRevocationServiceTest` for removed members and other organizations*
- [x] Account deletion revokes that `sub`; erasure by [ET-PLT-008](../../_platform/008-data-protection/) uses the same entry point when it is built
  - *2026-10-11: `AccountService.delete`; `AccountServiceTest`*
- [x] A revocation a platform administrator makes by hand writes an `ACCESS_REVOKED` audit row; automatic triggers do not ([ET-PLT-009](../../_platform/009-audit-trail/))
  - *2026-10-11: the row carries `trigger`, `type`, a masked identifier and the written reason's actor (`AccessRevocationServiceTest.administratorMustWriteAReason`, `administratorCutsATokenOrASession`); an automatic trigger writes none, because the event behind it (suspension, member removal, contact change) is already recorded, and suspension and deletion keep their own `USER_SUSPENDED` / `USER_DELETED` rows*

### ET-IDN-003-R9 · Logout ends the session end to end *(added 2026-10-04)*

WHEN a buyer or staff member signs out, THE SYSTEM SHALL end the Keycloak session, the buyer app's server-side session, and every token minted under that `sid`.

**Acceptance**
- [x] The buyer app's logout route deletes the server-side session record, clears the HttpOnly cookie, calls Keycloak's end-session endpoint with the stored ID token hint, and registers the `sid` revocation
- [x] Revocation is owned by identity-service's listener path above, not by the web app: the app's own call to `/api/internal/revocations/logout` is a redundant idempotent write, so a logout from a client with no web receiver (mobile, console, admin) is still revoked
- [x] Keycloak's **back-channel logout** (`backchannel.logout.url` = `${APP_URL}/api/auth/backchannel-logout` on each shared-BFF client: `myticketzm-web`, the organizer client and the admin client) reaches the buyer app, which deletes the matching session by `sid` and answers within the logout token's validity; a logout initiated anywhere (console, another device, an admin) therefore ends the buyer app session
- [x] The back-channel logout token is validated (signature, issuer, audience, `events` claim, no `nonce`, `jti` replay-checked) before any session is deleted
- [x] After logout the refresh token held server-side is unusable: refresh-token reuse detection ([ET-PLT-007](../../_platform/007-security-and-authorization/)) and the `sid` revocation both refuse it
  - *2026-10-11, R9 as a whole: `logoutHandler` and `backchannelLogoutHandler` in `frontend/web/libs/shared/src/auth/bff/handlers.ts`, `verifyLogoutToken` in `oidc.ts` (signature, issuer, audience, `events`, no `nonce`, `jti`, `iat`; replay marker in the store); `backchannel.logout.url` is set on `myticketzm-web`, the organizer client and `myticketzm-admin`. Verified: 227 shared auth/client tests, and `BFF_IT_KEYCLOAK=1` `keycloak.it.ts` + `revocation.it.ts` against a real Keycloak 26.5.2 (16 tests); `LogoutRevokesTokenEndToEndTest.refreshTokenIsDead`*
- [x] A test signs in, signs out from a second client, and asserts the first client's next request is refused within one access-token lifespan
  - *2026-10-09: `LogoutRevokesTokenEndToEndTest` with two real Keycloak sessions; the refusal is immediate, not within a lifespan*

### ET-IDN-003-R8 · A revocation row outlives its token, and no longer

THE SYSTEM SHALL retain a revocation record until the revoked token could no longer be
presented, and SHALL then remove it.

**Acceptance**
- [x] `expiresAt` is `revokedAt + accessTokenLifespan + clockSkew`
  - *2026-10-09: the lifespan defaults to PT5M in every service and in `RevocationProperties`, matching the realms; the E2E asserts the window is under 7 minutes*
- [x] A TTL index on `expiresAt` with `expireAfterSeconds: 0` removes the row
  - *2026-10-11: created by `IdentityIndexInitializer` and pinned by `IdentityIndexCensusTest`; `RevocationLifecycleTest.expiryIndexRemovesTheRow` against the real index*
- [x] A `USER` or `SESSION` revocation covers tokens minted up to its `expiresAt`, so re-issue after a ban is refused for the full window
  - *2026-10-11: `AccessRevocationEndToEndTest.administratorCutsThePerson` — a token Keycloak mints after the revocation is refused*
- [x] The collection does not grow without bound; a test asserts a row is gone after its window plus the TTL monitor's period
  - *2026-10-11: the same test, with the monitor's period set to one second on the throwaway server*
- [x] Clock skew is configured, not assumed zero — a token issued by a Keycloak whose clock is 30 s ahead is still covered
  - *2026-10-11: `RevocationLifecycleTest.skewIsCovered`; the platform refuses a negative skew*
- [x] Removal of the row is not itself a revocation event and publishes nothing
  - *2026-10-11: nothing is published for any revocation (R6)*

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
| `trigger` | `RevocationTrigger` | see below; absent on rows written before triggers were recorded |
| `revokedAt` | `Instant` | from the `Clock` bean |
| `expiresAt` | `Instant` | `revokedAt + accessTokenLifespan + clockSkew` |

### Indexes

| Index | Kind | Why |
|---|---|---|
| `{ _id: 1 }` | **unique** (implicit) | `_id` is derived from type and value, so one record per identifier and no check-then-insert race; a second unique index would add nothing |
| `{ expiresAt: 1 }` | **TTL, `expireAfterSeconds: 0`** | the row dies with the token it revokes |
| `{ revokedAt: -1 }` | single | the administrator's revocation log (`activeRevocations`) |

### Enums

| Type | Values |
|---|---|
| `RevocationType` | `TOKEN` (`jti`), `SESSION` (`sid`), `USER` (`sub`) |
| `RevocationTrigger` | `LOGOUT`, `CREDENTIAL_CHANGE`, `CONTACT_CHANGED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_ERASED`, `MEMBER_REMOVED`, `GRANT_REVOKED`, `ORGANIZATION_SUSPENDED`, `ADMIN_REVOKE`, `SUSPECTED_COMPROMISE` |

### Redis keys

| Key | TTL | Authority |
|---|---|---|
| `pml:blacklist:{jti}` | that token's own `exp` + skew, at most lifespan + skew | `identity_token_revocations` |
| `pml:session:{sid}` | lifespan + skew from revocation | `identity_token_revocations` |
| `pml:revoked:{sub}` | lifespan + skew from revocation | `identity_token_revocations` |
| `pml:revocation:cache-complete` | 10 min, renewed every 2 min | the cache-completeness marker; absent means untrusted |

**Redis holds no revocation that MongoDB does not.** A flush costs latency, never
correctness.

### Events

None. A revocation is the MongoDB record and the shared Redis key (R6); no domain event is published and
nothing is staged in the outbox.

### GraphQL

`backend/identity-service/src/main/resources/graphql/schema.graphqls`

| Operation | Role | Returns |
|---|---|---|
| `revokeToken(jti, reason)` | `ADMIN` `@tag(admin)` | `RevocationRecord!` |
| `revokeSessionAccess(sid, reason)` | `ADMIN` `@tag(admin)` | `RevocationRecord!` |
| `revokeUserAccess(userId, reason)` | `ADMIN` `@tag(admin)` | `RevocationRecord!` — `userId` is the account id |
| `signOutEverywhere` | authenticated | `Int!` — the caller's *other* sessions ended; the session making the request stays, so the response can be delivered |
| `revokeSession(sessionId)` | authenticated | `Boolean!` — one of the caller's own sessions (the sessions list's sign-out); an id that is not the caller's is refused as unknown |
| `mySessions` | authenticated | `[AccountSession!]!` — the caller's live sessions, the current one marked |
| `activeRevocations(type, pagination)` | `ADMIN` `@tag(admin)` | `RevocationRecordOffsetPage!` — what is still in force, newest first |

The administrator's `revokeSession` of the first draft is `revokeSessionAccess`: the self-service
`revokeSession` already held that name.

### Internal API

| Endpoint | Method | Purpose |
|---|---|---|
| `/api/internal/revocations/check` | POST | the durable check, for a service whose cache is untrusted |
| `/api/internal/revocations` | POST | one revocation by type and value (service to service) |
| `/api/internal/revocations/logout` | POST | a sign-out's token, session and user in one call |
| `/api/internal/revocations/{type}/{value}` | DELETE | lifts one revocation (an unsuspended account) |
| `/api/internal/keycloak/sync/event` | POST | the Keycloak listener's slim event: a session end revokes that `sid`; a password or second-factor change cuts the person's other sessions |

There is no re-warm feed: identity is the one writer and the one warmer of the shared keys.

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
[ET-ADM-003](../../admin/003-transaction-recovery/); and the revocation operations themselves
(`revokeToken`, `revokeSessionAccess`, `revokeUserAccess`, `signOutEverywhere`).

### Accepted Redis eviction policies

| Policy | Verdict |
|---|---|
| `noeviction`, or any policy while `maxmemory` is unset | trusted |
| any evicting policy (`allkeys-*`, `volatile-*`) with `maxmemory` set | **untrusted** — can evict a revocation key, which always carries a TTL |

### Error codes

| Code | `ErrorType` | Retryable |
|---|---|---|
| `TOKEN_REVOKED` | `UNAUTHENTICATED` | no |
| `REVOCATION_UNAVAILABLE` | `UNAVAILABLE` | yes |

### Configuration

Keys are under `pml.security.revocation.`; the platform refuses to start when the durable timeout is not
longer than the cache timeout, when the skew is negative, or when the completeness TTL is less than twice the
warm interval.

| Key | Default |
|---|---|
| `enabled` | `true` |
| `access-token-lifespan` | `PT5M` |
| `clock-skew` | `PT60S` |
| `cache-timeout` | `PT0.25S` |
| `durable-timeout` | `PT2S` |
| `require-eviction-safe-cache` | `true` |
| `cache-completeness-ttl` | `PT10M` |
| `cache-warm-interval` | `PT2M` |

## 5. Tasks

- [x] **T1 · The document, its three indexes and the durable store**
  - requirements: R1, R8
  - files: `backend/identity-service/.../revocation/`
  - verify: the unique index refuses a duplicate identifier; the TTL index removes an expired row
  - parallel-safe: no — everything else reads this
  - depends: —

- [x] **T2 · The revocation check: one lookup over all three identifiers**
  - requirements: R1, R2
  - files: `backend/shared-library/.../security/revocation/`
  - verify: revoking by each type refuses exactly the intended tokens and no others
  - parallel-safe: no — shared by all three services
  - depends: T1

- [x] **T3 · The Redis cache, its completeness marker, and the boot warm**
  - requirements: R3
  - files: `backend/shared-library/.../security/revocation/`, `backend/identity-service/.../revocation/`
  - verify: a mid-suite `FLUSHALL` still refuses a previously revoked token
  - parallel-safe: no — cache and warm together
  - depends: T2

- [x] **T4 · The eviction-policy probe and the untrusted-cache path**
  - requirements: R4
  - files: `backend/shared-library/.../security/revocation/`
  - verify: `allkeys-lru` yields an untrusted cache and a health condition naming it
  - parallel-safe: yes
  - depends: T3

- [x] **T5 · The sensitive-operation guard; fail closed, degrade for reads**
  - requirements: R5
  - files: `backend/shared-library/.../security/revocation/`, every sensitive resolver
  - verify: under an induced store outage a payout mutation refuses and an event query succeeds
  - parallel-safe: no — touches every sensitive operation
  - depends: T4

- [x] **T6 · Propagation through the shared cache: the write, the reload, the bound** *(amended: no event)*
  - requirements: R6
  - files: `backend/identity-service/.../revocation/`
  - verify: a revocation is refused by another service within 5 s at p99, with nothing written to an event store
  - parallel-safe: yes
  - depends: T3

- [x] **T7 · The automatic triggers, each with its audit row**
  - requirements: R7
  - files: `backend/identity-service/.../{organization,team,permission}/`, `backend/keycloak-extensions/`
  - verify: removing a member refuses that member's next request; suspending an organization refuses every member
  - parallel-safe: yes — one trigger per agent
  - depends: T6

- [x] **T8 · The GraphQL surface, the internal API and the two error codes**
  - requirements: R1, R2, R5
  - files: `backend/identity-service/src/main/resources/graphql/schema.graphqls`, `.../revocation/`
  - verify: `signOutEverywhere` refuses the caller's other tokens; every admin field carries `@tag(admin)`
  - parallel-safe: yes
  - depends: T5

## 6. Out of scope

| Capability | Spec |
|---|---|
| Realm configuration, roles, `@auth`, JWT validation itself | [ET-PLT-007](../../_platform/007-security-and-authorization/) |
| The contact-OTP lifecycle and passwordless login | [ET-IDN-001](../001-phone-otp-identity/) |
| Accounts, suspension, contact change and deletion that trigger revocations | [ET-IDN-004](../004-accounts-and-contacts/) |
| Keycloak ↔ MongoDB user synchronisation | [ET-IDN-002](../002-keycloak-user-sync/) |
| Permission resolution and event access grants | [ET-ORG-003](../../organization/003-permission-resolution/) |
| Rate limiting, temporary blocks and abuse control | [ET-PLT-011](../../_platform/011-rate-limiting-and-abuse/) |
| The audit trail this writes into | [ET-PLT-009](../../_platform/009-audit-trail/) |
| Refresh-token rotation and reuse detection | Keycloak, configured by the realm export ([ET-PLT-007](../../_platform/007-security-and-authorization/)); the buyer app holds the refresh token server-side and never the browser |

Deliberately never in scope: **token introspection against Keycloak on the request path**
(makes Keycloak a synchronous dependency of every authenticated request), and **shortening
token lifetimes as a substitute** (narrows the window, never closes it, and multiplies
token-endpoint traffic by the same factor).
