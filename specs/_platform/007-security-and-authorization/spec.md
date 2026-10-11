# ET-PLT-007 · Keycloak realm, roles, `@auth`, tenant scoping, idempotency

> **Conformance** · PDI Phase 5 payment idempotency · US Part I §1 platform role hierarchy
>
> **Amended 2026-10-04 (D-44, D-45, D-48, D-49; F-044).** Where the earlier text says "one realm `event-ticketing`", the
> platform has **two realms**, `myticketzm` (buyers and organizers) and `myticketzm-admin` (platform staff), as code
> (R8). Buyer tokens live only in a server-side session of the buyer app. The `SCANNER` role does not exist (event
> staff are event-scoped grants, [ET-ORG-003](../../organization/003-permission-resolution/)). Binding detail:
> [CONTRACT §9](../../identity/004-accounts-and-contacts/CONTRACT.md).

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

THE SYSTEM SHALL define the Keycloak realms of R8 carrying the platform roles and the client
set of §4, with composite inheritance as declared. *(Amended: two realms, see R8; the buyers realm
declares `CUSTOMER` and `ORGANIZER`, the staff realm `SUPER_ADMIN`, `ADMIN`, `FINANCE` and
`FINANCE_LEAD`. Event staff are event-scoped grants in identity, never a realm role.)*

**Acceptance**
- [x] Realms `myticketzm` and `myticketzm-admin` exist and are exported to `../docker-resources/keycloak/` as versioned configuration, not configured by hand (previously one realm `event-ticketing`) — `RealmConformanceIT` imports both exports into a clean Keycloak and asserts every row below against the running server
- [x] Each realm declares exactly its platform roles and nothing but Keycloak's built-ins — `RealmConformanceIT.realmRoleSetIsExact` (the staff export's retired `SCANNER` role was removed 2026-10-10)
- [x] Composites are as §4 declares — `SUPER_ADMIN` includes `ADMIN`; `ADMIN` includes `FINANCE`; `ORGANIZER` includes `CUSTOMER` — `RealmConformanceIT.roleHierarchyIsComposite`
- [x] `CUSTOMER` is the realm's default role, granted on registration with no administrative step — `RealmConformanceIT.customerIsTheDefaultRole` (the default-role composite carries it)
- [x] Each service has its own confidential service-account client with its own secret — `RealmConformanceIT.serviceClientsAreConfidentialWithOwnSecrets` (secrets non-blank and pairwise distinct; each consumer reads its own from the environment)
- [x] Public clients declare PKCE and an exact redirect-URI allowlist — no wildcard host — `RealmConformanceIT.redirectsAreExact`

### ET-PLT-007-R2 · Every service validates every token, independently

THE SYSTEM SHALL validate each JWT at the service that receives it, against Keycloak's
JWKS.

**Acceptance**
- [x] Each service builds a `ReactiveJwtDecoder` from the issuer URI and validates signature, `iss`, `aud`, `exp` and `nbf` — `MultiIssuerJwtResolver`, proven per service by `JwtValidationContractTest` (forged key, rogue realm, claimed issuer, expired, not-yet-valid, no token)
- [x] Clock skew tolerance is explicit and no greater than 30 seconds — `JwtClockSkewTest`; a token whose `nbf` is past the skew is refused by every service (`JwtRejectionContract.assertRejectsNotYetValid`)
- [x] A token forged with a different key, one with a wrong issuer, one with a wrong audience and an expired one are each rejected when sent **directly** to a service, bypassing the gateway — the three services' `JwtValidationContractTest` and the gateway's own, which also proves a bad bearer token is refused on the anonymous GraphQL path
- [x] JWKS is cached with a bounded TTL and a key rotation is picked up without a restart — `keycloak.jwks-cache-ttl` (default 5 min, at most 15, validated at startup); `JwksRotationTest` covers a rotated-in key accepted at once by an unknown `kid` refetch and a withdrawn key refused once the TTL has aged out
- [x] Authorities are derived from `realm_access.roles`, `resource_access.{client}.roles` and `scope`, by one converter in `shared-library` — `KeycloakGrantedAuthoritiesConverterTest` (a claim of the wrong shape contributes nothing and never fails the request)
- [x] Access tokens live 5 minutes; session lifetimes are as §4 states — `RealmConformanceIT.tokenAndTransportSettings` and `refreshLifetimesFollowTheTable` (the mobile client sets no per-client override, so it follows the realm's)

### ET-PLT-007-R3 · Every operation declares its coarse gate

THE SYSTEM SHALL carry an explicit gate decision on every query, mutation and protected field,
and a field without one SHALL fail the build. *(Amended: the decision is `@auth` or, in booking and
identity, the equivalent method-level `@PreAuthorize`; the two are interchangeable and the build
treats either as the decision. See §4.)*

**Acceptance**
- [x] Every field in `Query` and `Mutation` in all three subgraphs carries a gate decision — `@auth` (catalog, and identity's public fields, which carry `@auth(requires: PUBLIC)`) or `@PreAuthorize` — `OperationGateLintTest` in each service
- [x] A test enumerates all three schemas and fails on any `Query`/`Mutation` field lacking a decision — `ComposedSchemaGateTest` (one rule over the union, with per-service counts) beside each service's `OperationGateLintTest`
- [x] The `@auth` runtime denies with `ACTOR_NOT_AUTHENTICATED` when no identity is present and `ACTOR_NOT_PERMITTED` when the role is insufficient — `AuthDirectiveOutcomesTest`
- [x] `@auth(requires: INTERNAL)` is denied for any token not carrying an internal scope — `AuthDirectiveOutcomesTest` (an ADMIN without one is refused; `SCOPE_internal-read` and `ROLE_INTERNAL_SERVICE` are admitted)
- [x] The directive is evaluated **before** the data fetcher runs — a denied field performs no repository call — `AuthDirectiveRepositoryGuardTest` (both `@auth` and `@PreAuthorize`)
- [x] Every `@auth` requirement is a row of the §4 operation-gate registry — `OperationGateRegistryTest` (also asserts every `Role` value is a row)

### ET-PLT-007-R4 · Tenant scoping is enforced in the query, not after it

WHILE a caller reads or writes tenant-scoped data, THE SYSTEM SHALL restrict the query to
the organizations that caller belongs to.

**Acceptance**
- [x] Every repository method over a tenant-scoped collection takes the permitted organization ids and includes them in the Mongo filter, or is a recorded, individually justified exception — `TenantBoundaryLintTest` (every remaining unscoped lookup is named with its reason)
- [x] No resolver or service compares an organization id after loading the document — `TenantBoundaryLintTest`; the converted paths go through `TenantGuard.locate`
- [x] A caller requesting another organization's resource by a known-good id receives the `*_UNKNOWN` code for that type, indistinguishable from a non-existent id (ET-PLT-005 R6) — the per-collection `*TenantBoundaryTest` classes
- [x] A `SUPER_ADMIN` or `ADMIN` bypasses the filter through one explicitly named code path (`PlatformWideAccess`), and every use is recorded — `PlatformWideAccessTest`; `PlatformWideBypassLintTest` fails the build on a new bypass outside it. The record is a structured `audit.platform-wide` log line today; the durable audit-trail row arrives with ET-PLT-009 (the sink is an interface)
- [x] A test iterates every tenant-scoped query with a second organization's ids and asserts no data is returned — the per-collection `*TenantBoundaryTest` classes (one per converted collection shape)
- [x] Permission resolution exists in exactly one implementation (`PermissionResolutionServiceImpl`; `AuthorizationServiceImpl` only maps its DTOs onto it) — `PermissionResolutionSingleImplementationTest`; `User.keycloakUserId` is the sole account-to-Keycloak linkage and is never an authorization input

### ET-PLT-007-R5 · Internal endpoints are scope-gated and user-agnostic

THE SYSTEM SHALL restrict `/api/internal/**` to callers holding an internal scope, and
these endpoints SHALL perform no user authorization.

**Acceptance**
- [x] Every `/api/internal/**` path requires `SCOPE_internal-read`, `SCOPE_internal-write` or `ROLE_INTERNAL_SERVICE`; none is `permitAll` — `InternalSurfaceTest` in each service, reflective over every internal controller
- [x] Every service-to-service client uses `client_credentials` and its secret is environment-sourced in every consumer — `RealmConformanceIT.serviceClientsAreConfidentialWithOwnSecrets` and `passwordGrantIsOffEverywhere`
- [x] No internal endpoint reads the *caller's* identity from the request: the caller is the service, and a `userId` in a body names the subject being asked about, never who is asking — `InternalAuthScopeRulesTest`
- [x] An unauthenticated call to each internal endpoint returns 401; one with a user token but no internal scope returns 403; a read-only scope on a write path returns 403 — `InternalSurfaceTest`
- [x] The internal surface is enumerated in §4 and adding a path changes this spec — `InternalSurfaceTableTest` in each service fails on any path missing from, or extra to, the table

### ET-PLT-007-R6 · Money-moving mutations are idempotent under retry

WHEN a mutation that moves money is submitted, THE SYSTEM SHALL require an idempotency key
and apply the operation at most once per key.

**Acceptance**
- [x] Every mutation in the §4 idempotent-operation registry takes a non-null `idempotencyKey: String!` — the per-mutation idempotency tests and the registry in §4
- [x] A first submission records the key, a fingerprint of the canonical request, and the response — `IdempotencyGuardTest`
- [x] A repeat with the same key and the same fingerprint returns the original response without re-applying anything — `ReserveTicketsIdempotencyTest`, `PayReservationIdempotencyTest`, `PlatformTransferIdempotencyTest`
- [x] A repeat with the same key and a different fingerprint is refused with `IDEMPOTENCY_KEY_REUSED` and applies nothing — the same classes
- [x] The guard is `SET idem:{scope}:{key} NX EX 86400` in Redis **and** a durable ledger row whose `_id` is `scope|key` — a Redis flush cannot cause a second application — `IdempotencyGuardTest` (flush mid-flight; the ledger's `expiresAt` TTL index is asserted by `ledgerRowsExpireOnTheirOwnExpiresAt`)
- [x] Two parallel submissions of one key produce exactly one application (ET-PLT-006 R5) — `parallelSubmissionsTakeInventoryOnce`, `parallelSubmissionsPromptOnce`, and the transfer test's parallel case
- [x] The fingerprint excludes the key itself and any field the client may legitimately vary on retry — `Fingerprint.CLIENT_VARYING` (`clientTimestamp`, `deviceId`), used at every call site; `FingerprintTest`

### ET-PLT-007-R8 · Two realms as code, short tokens, a server-side buyer session *(added 2026-10-04)*

THE SYSTEM SHALL run buyers and staff in separate realms defined as code, issue short-lived audience-bound tokens, rotate refresh tokens with reuse detection, and keep buyer tokens out of the browser.

**Acceptance**
- [x] `myticketzm` holds buyers, organizers, team members and event staff and binds the contact flow ([ET-IDN-001](../../identity/001-phone-otp-identity/)); `myticketzm-admin` holds platform staff and binds password plus a required second factor; `user-sync` is enabled in both
- [x] Access tokens live 5 minutes; every service validates `iss` and an `aud` — the platform's single gateway audience, which every client's mapper stamps (F-053); a token minted for another audience is refused — `tokenAndTransportSettings`, `JwtValidationContractTest`
- [x] Refresh tokens rotate on every use with **reuse detection**: presenting a used refresh token revokes the whole session family — `RealmConformanceIT.buyerTokensRotate`, `staffTokensRotate`
- [x] The buyer client `myticketzm-web` is confidential with PKCE S256, exact redirect URIs, **no direct-access (password) grant**; the password grant is disabled on every client in both realms — `RealmConformanceIT.passwordGrantIsOffEverywhere`, `redirectsAreExact`
- [x] The buyer app (Next.js server side) is the OAuth client: it holds access and refresh tokens in a server-side session keyed by an opaque HttpOnly, Secure, SameSite=Lax cookie; no token reaches browser JavaScript, `localStorage` or a URL
- [x] The realm user profile makes `email`, `firstName`, `lastName` optional; users are edited only with a full representation — `ContactOtpKeycloakIT.adminApiCreatesUserWithoutProfile`
- [x] The contact authenticator ([ET-IDN-001](../../identity/001-phone-otp-identity/)) is the only custom authenticator in the buyer browser flow — `RealmConformanceIT.buyerBrowserFlowHasExactlyTheContactAuthenticator`; it authenticates to identity-service with `client_credentials` and has no unauthenticated mode
- [x] No registration page or mapper lets a user choose a role or an account type; roles come only from identity-service — `RealmConformanceIT.registrationCannotGrantRoles`
- [x] Importing the exports into clean Keycloak instances reproduces this table; no step is a console click — `RealmConformanceIT` (the whole class runs against a freshly imported server)

### ET-PLT-007-R9 · Anonymous access is an allowlist per service, enforced in one shared component *(added 2026-10-05)*

WHEN a caller without an `Authorization` header posts to a service's `/graphql`, THE SYSTEM SHALL admit it only if
the service's declared allowlist names every root field it selects, and SHALL refuse everything else with 401.

A service's `/graphql` stays authenticated. The shared `PublicGraphQlFilter` (`com.pml.shared.security.publicop`)
marks an exchange public only when the request is a POST with no token carrying one parsed query, so opening the
path to anonymous callers can never expose federation's `_entities`/`_service`, introspection or a resolver that
assumes a token. Each service declares its own `PublicOperationPolicy`; identity's is `publicPlatformRules`
([ET-ADM-002](../../admin/002-platform-configuration/) R10), catalog's is [ET-CAT-004](../../catalog/004-event-content-media-and-ranking/) R13.

**Acceptance**
- [x] A single, unbatched, non-persisted query is admitted only when every root field is allowlisted; a mutation, a subscription, a batch, a second operation, a root fragment, a `__schema`/`__type`/`__typename` root, an unallowlisted field or a mixed selection is refused with 401
- [x] `_entities` is admitted only for an entity type and leaf fields the policy names, over representations carrying nothing but `__typename` and `id`; `_service` never
- [x] Fragments below a root field are followed: depth, field count and fragment count are limited by the policy (defaults 10, 300, 10), a fragment cycle or an unknown fragment is refused, and a body over 16 KiB is never parsed
- [x] An admitted caller is limited per client address (the last `X-Forwarded-For` entry) in Redis, default 120 per minute; the next request is refused with 429 and `Retry-After`; if Redis is unreachable the request is served
- [x] Metrics `platform.public_graphql.requests{service,outcome,reason}` carry no address, query text or variable — `PublicGraphQlFilterTest.meterTagsAreAFixedLowCardinalitySet`
- [x] A request that carries a token is never touched by the filter
- [x] A field that is not part of a service's public surface carries `@auth` even where it is reachable from an allowlisted root — `CatalogPublicSurfaceTest`, `IdentityPublicSurfaceTest` (booking has no public surface)

**Tests** `PublicOperationRulesTest` (L1), `PublicGraphQlFilterTest` (L1), `PublicGraphQlSecurityTest` (L2)

### ET-PLT-007-R7 · A revoked session stops working

WHEN a session is terminated or an account disabled, THE SYSTEM SHALL stop accepting that
actor's credentials within the access-token lifetime.

**Acceptance**
- [x] Backchannel logout is configured for every confidential client and each service invalidates its cached session state on receipt — **ET-PLT-007 Phase 7, 2026-10-10, built by ET-IDN-003.**
      Every confidential client with a browser session (`myticketzm-web`, the organizer web client, the admin client — the only ones with a session to notify; the service clients use client-credential grants) carries `backchannel.logout.url` pointing at that app's `/api/auth/backchannel-logout` in both realm exports. Receipt is proven end to end by `keycloak.it.ts`'s "Keycloak delivers a back-channel logout that deletes the session by sid", and the resulting revocation record / cache invalidation by `LogoutRevokesTokenEndToEndTest.logoutCutsOnlyThatSession` (the logged-out session's token is refused at once; the other session's is not).
- [x] A disabled Keycloak account cannot refresh, and its access token stops working within 5 minutes — **ET-PLT-007 Phase 7, 2026-10-10.**
      `KeycloakServiceContainerTest.disabledAccountCannotRefresh` (new): a real refresh token and a fresh password grant are both refused once the account is disabled, directly by Keycloak. `RealmConformanceIT`'s "both realms issue five-minute tokens..." case fixes `accessTokenLifespan` at 300s in both realm exports, so any already-issued token stops working within 5 minutes regardless of the account's state.
- [x] An organization member removed in MongoDB loses organization-scoped access on the **next request**, not on the next token — because organization role is resolved per request (R4, ET-ORG-003) — **proven by `MemberRemovalTest`** (the write path) together with `TenantScopeWebFilter`/`IdentityTenantMemberships`, which resolve tenancy fresh from Mongo on every request (lazily, at most once per request, never cached across requests — see their javadoc).
- [x] Logout revokes the refresh token at Keycloak, not only in client storage — **`keycloak.it.ts`**'s "sign-out ends the Keycloak SSO session from the server, revokes the refresh token and the session at identity-service", and `LogoutRevokesTokenEndToEndTest.refreshTokenIsDead` (Keycloak itself refuses `grant_type=refresh_token` for the logged-out session with `invalid_grant`).
- [x] A test asserts the removed-member case at sub-second latency — **ET-PLT-007 Phase 7, 2026-10-10.**
      `MemberRemovalTest.removalIsVisibleOnTheVeryNextReadWithinOneSecond` (new): removes a member, immediately re-reads their membership, asserts it already shows `REMOVED`, and asserts the elapsed time is under 1 second. Framed as proof of no cache/async propagation delay in the removal path, not a real latency budget.

## 4. Model

### Realm

| Setting | Value |
|---|---|
| Realms | `myticketzm` (buyers, organizers, team members, event staff) and `myticketzm-admin` (platform staff) — formerly one realm `event-ticketing` |
| Issuer | `${KEYCLOAK_URL}/realms/{realm}` |
| JWKS | `${KEYCLOAK_URL}/realms/{realm}/protocol/openid-connect/certs` |
| Refresh tokens | rotated on use, reuse detection on |
| Audience | each service's `aud` is validated |
| Access-token lifetime | 5 min |
| Default role | `CUSTOMER` |

### Realm roles and composites

| Role | Includes | Holds |
|---|---|---|
| `SUPER_ADMIN` | `ADMIN` | everything, including platform configuration |
| `ADMIN` | `FINANCE` | approvals, suspensions, all-tenant reads |
| `FINANCE` | — | payouts, refunds, reconciliation, ledger reads |
| `FINANCE_LEAD` | — | receives chargeback and refund escalations (ROADMAP D-32); grants nothing, and is held together with `FINANCE` |
| `ORGANIZER` | `CUSTOMER` | create events, request payouts — **within their own organizations only**, which R4 enforces |
| `CUSTOMER` | — | browse, buy, transfer, validate-if-granted |

`ORGANIZER` is a *kind of actor*, not a permission over a particular organization.
Everything organization-scoped is decided by
[ET-ORG-003](../../organization/003-permission-resolution/).

### Clients

| Client | Type | Flow | Refresh lifetime | Used by |
|---|---|---|---|---|
| `myticketzm-web` | **confidential**, PKCE S256, no direct grant | authorization code (buyer app server side) | 30 min | customer web — tokens stay in the server-side session |
| `event-ticketing-admin` (realm `myticketzm-admin` for platform staff; the organizer console is a client of `myticketzm`) | public | authorization code + PKCE | 30 min | admin, organizer console |
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
| `accountId` | the account id; equals the Keycloak username for new accounts (written by identity-service only) |
| `primary_org_id` | convenience for client routing; never an authorization input |

Legacy `User.id` in MongoDB is the Keycloak user ID; new accounts use an account UUID that equals the Keycloak username, linked by `keycloakUserId` ([ET-IDN-004](../../identity/004-accounts-and-contacts/)). No phone number is stored in Keycloak.

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

The table is pinned by `InternalSurfaceTableTest` in each service, which fails when it and the controllers disagree in either direction.

| Method and path | Service | Scope | Purpose |
|---|---|---|---|
| `GET /api/internal/payouts/by-event/{eventId}/open` | booking | `internal-read` | whether the event has a payout request not yet in a terminal state |
| `GET /api/internal/tickets/sold-count/by-event/{eventId}` | booking | `internal-read` | the sold-ticket count from booking's own inventory, for catalog's unpublish check |
| `GET /api/internal/events/{id}` | catalog | `internal-read` | an event summary, for booking's purchases |
| `POST /api/internal/inventory/tiers/{tierId}/commit` | catalog | `internal-write` | move reserved inventory to sold |
| `POST /api/internal/inventory/tiers/{tierId}/release` | catalog | `internal-write` | return reserved inventory to the available pool |
| `POST /api/internal/inventory/tiers/{tierId}/reserve` | catalog | `internal-write` | reserve inventory for a pending purchase |
| `POST /api/internal/inventory/tiers/{tierId}/restore` | catalog | `internal-write` | return sold inventory to the available pool on a refund or chargeback |
| `POST /api/internal/tiers/{tierId}/access-code/verify` | catalog | `internal-write` | whether the code a buyer holds opens a hidden tier |
| `POST /api/internal/alerts` | identity | `internal-write` | raise an operator alert for a condition |
| `POST /api/internal/alerts/resolve` | identity | `internal-write` | close an alert when its condition clears |
| `POST /api/internal/auth/accounts/ensure` | identity | `internal-write` | consume a proof, find or create the account, and issue a login handle for an active one |
| `GET /api/internal/auth/accounts/{accountId}/status` | identity | `internal-read` | the status of a buyer account |
| `POST /api/internal/auth/challenges` | identity | `internal-write` | start a contact-verification challenge |
| `POST /api/internal/auth/challenges/verify` | identity | `internal-write` | verify a challenge and obtain its proof |
| `GET /api/internal/auth/dev/captured` | identity | `internal-read` | the codes the in-memory capture kept; exists only where `identity.delivery.capture.enabled=true` (local and test), loopback callers only |
| `POST /api/internal/auth/handles/redeem` | identity | `internal-write` | redeem a login handle |
| `POST /api/internal/authorization/check` | identity | `internal-write` | decide whether a user may perform an action on a resource |
| `GET /api/internal/authorization/check-same-organization` | identity | `internal-read` | whether two users share an organization |
| `GET /api/internal/authorization/event-access` | identity | `internal-read` | whether a user holds a permission on an event |
| `GET /api/internal/authorization/organization-name` | identity | `internal-read` | an organization's display name, for denormalizing; 404 when none is on file |
| `GET /api/internal/authorization/user-organizations` | identity | `internal-read` | the organizations a user is a member of |
| `GET /api/internal/finance-leads/contacts` | identity | `internal-read` | the active finance leads' email addresses, for booking's escalation emails |
| `POST /api/internal/finance-leads/notifications` | identity | `internal-write` | a WhatsApp escalation to every finance lead, deduplicated per lead and escalation |
| `POST /api/internal/keycloak/sync/event` | identity | `internal-write` | process a Keycloak event notification |
| `POST /api/internal/notifications/approvals` | identity | `internal-write` | an event review's messages: every active `ADMIN` when an event joins the queue, the organizer when it is decided; deduplicated per recipient, event, submission and outcome |
| `POST /api/internal/notifications/users` | identity | `internal-write` | message one account known only by id |
| `POST /api/internal/notifications/users/batch` | identity | `internal-write` | message up to 100 accounts; outcome counts only |
| `POST /api/internal/revocations` | identity | `internal-write` | create one revocation |
| `POST /api/internal/revocations/check` | identity | `internal-write` | the durable revocation read other services fall back to; a POST, so the chain requires `internal-write` although the method also admits `internal-read` |
| `GET /api/internal/revocations/health` | identity | `internal-read` | liveness of the durable revocation store |
| `POST /api/internal/revocations/logout` | identity | `internal-write` | revoke a sign-out's token, session and optionally user in one call |
| `DELETE /api/internal/revocations/{type}/{value}` | identity | `internal-write` | lift one revocation |
| `POST /api/internal/users/lookup` | identity | `internal-write` | resolve a verified contact to the account that owns it, for a transfer |

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
| `rl:{service}:public-graphql:{clientAddress}` | 1 min | signed-out GraphQL request counter (R9) | none; fails open |

### Error codes used

`ACTOR_NOT_AUTHENTICATED`, `ACTOR_NOT_PERMITTED`, `IDEMPOTENCY_KEY_REUSED` — all rows of
[ET-PLT-005 §4](../005-error-contract/), introduced by this spec.

## 5. Tasks

- [x] **T1 · Realm export: roles, composites, clients, scopes, PKCE, redirect allowlists**
  - requirements: R1
  - files: `../docker-resources/keycloak/event-ticketing-realm.json`
  - verify: importing the export into a clean Keycloak reproduces §4 exactly
  - parallel-safe: no — one realm
  - depends: —

- [x] **T2 · One JWT converter and one decoder configuration in `shared-library`**
  - requirements: R2
  - files: `backend/shared-library/src/main/java/com/pml/shared/security/`
  - verify: forged, wrong-issuer, wrong-audience and expired tokens are each rejected directly at a service
  - parallel-safe: no — every service imports it
  - depends: T1

- [x] **T3 · `@auth` on every `Query`/`Mutation` field; the enumeration test**
  - requirements: R3
  - files: the three `schema.graphqls`, `shared-library/.../graphql/auth/`
  - verify: a field without `@auth` fails the build; a denied field performs no repository call
  - parallel-safe: yes — one service per agent
  - depends: T2

- [x] **T4 · `TenantScope`; move every tenant check into the repository filter**
  - requirements: R4
  - files: every tenant-scoped repository in booking and identity
  - verify: permission resolution exists in exactly one implementation, `/api/internal/**` is scope-gated, and no `User.keycloakUserId` exists; the cross-tenant iteration test returns nothing
  - parallel-safe: no — the resolution is shared
  - depends: T2

- [x] **T5 · Lock down `/api/internal/**`; `client_credentials` for every caller**
  - requirements: R5
  - files: `backend/*/src/main/java/com/pml/*/config/security/SecurityConfig.java`
  - verify: 401 unauthenticated, 403 with a user token, 200 with an internal scope, per path
  - parallel-safe: yes — one service per agent
  - depends: T1

- [x] **T6 · `IdempotencyGuard`: Redis + unique index, fingerprint, three outcomes**
  - requirements: R6
  - files: `backend/shared-library/.../security/IdempotencyGuard.java`, booking's mutations
  - verify: replay returns the original; a changed body refuses; two parallel submissions apply once
  - parallel-safe: no — one guard, nine call sites
  - depends: T2

- [x] **T8 · Two realms as code, audience, refresh rotation, password grant banned, server-side buyer session**
  - requirements: R8
  - files: `../docker-resources/keycloak/` (realm exports; coordinator-owned), `frontend/web/apps/ticketing/src/` server routes
  - verify: a realm-export test finds no client with direct grant; reuse of a refresh token kills the family; a wrong-audience token is refused; no token appears in browser storage
  - parallel-safe: no — one realm export
  - depends: T1

- [x] **T9 · Public operations as a per-service allowlist**
  - requirements: R9
  - files: `backend/shared-library/src/main/java/com/pml/shared/security/publicop/*`, `ServiceSecurity`
  - verify: the L1 rules and filter tests, and the L2 test through the real security chain with Redis
  - parallel-safe: yes
  - depends: T3

- [x] **T7 · Backchannel logout; the removed-member latency test**
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
