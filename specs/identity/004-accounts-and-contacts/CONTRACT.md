# Buyer identity contract v1 (single source for specs, plugin, identity-service, app)

Status: working contract for the 2026-10-04 redesign. If an implementation needs to deviate, change THIS file first.

## 1. Vocabulary
- **Account**: the application record of a person (collection `identity_users`). One per person.
- **Contact**: a verified WhatsApp number or email owned by exactly one account (`identity_contacts`).
- **Challenge**: a one-time code request held in Redis. **Proof**: single-use token returned after a correct code. **Login handle**: single-use token that lets the Keycloak plugin sign an ACTIVE account in without a screen.
- Keycloak is the only token issuer. identity-service is the only writer of Keycloak users/roles. The plugin never creates users, never grants roles, never talks to Temporal or MongoDB.

## 2. Account states (`status`)
`PROVISIONING` (contact claimed, Keycloak user not finished) -> `ACTIVE` (Keycloak user exists with role+attributes, linked) -> `SUSPENDED` | `MERGED` | `DELETED`. Transient markers: `MERGING`, `CHANGING`, `DELETION_REQUESTED` (field `pendingKind` + `pendingSince`). Legacy `AccountStatus` values map: ACTIVE->ACTIVE, INACTIVE/LOCKED/SUSPENDED->SUSPENDED, PENDING_VERIFICATION->PROVISIONING, PENDING_DELETION->DELETION_REQUESTED (status stays ACTIVE).
Only ACTIVE accounts get a login handle, may sign in, may hold a reservation.

## 3. Normalisation and hashing
- Phone: strict E.164; input must carry a country code (`+` or `00` prefix) OR the caller supplies `regionHint` (default `ZM` only for numbers starting with `0` and 10 digits). Must be a valid MOBILE (libphonenumber `getNumberType` MOBILE or FIXED_LINE_OR_MOBILE). Country must be in `identity.limits.allowed-countries`.
- Email: trim, lower-case the whole address, NFC normalise; must match RFC 5322 simple form; max 254.
- `contactKey = HMAC-SHA256(identity.contact.hash-key, TYPE + ":" + normalized)` as lower-case hex. Used for uniqueness, Redis keys, workflow ids. Never log or store the normalised raw value outside `valueEncrypted`.
- Display: `maskedContact` e.g. `+260 97* ***123`, `j***@gmail.com`.

## 4. Internal REST (service-to-service, scope `internal-write`; GET uses `internal-read`)
Errors are RFC 9457 `application/problem+json` with extension `errorCode` (and `retryable`, `retryAfterSeconds`, `attemptsRemaining`, `lockedUntil` where relevant).

1. `POST /api/internal/auth/challenges`
   Request: `{ "contact": {"value": "...", "type": "WHATSAPP|EMAIL (optional; EMAIL if value contains @)"}, "regionHint": "ZM (optional)", "clientIp": "...", "deviceId": "... (optional)", "preferredChannel": "WHATSAPP|EMAIL (optional)" }`
   202: `{ "challengeId": "uuid", "contactType": "...", "maskedContact": "...", "channel": "WHATSAPP|EMAIL", "expiresInSeconds": 300, "resendAfterSeconds": 60 }`
   The response is identical (shape and timing band) whether or not an account exists for the contact.
   Errors: `CONTACT_INVALID` 400, `OTP_RATE_LIMITED` 429, `OTP_COOLDOWN_ACTIVE` 429, `OTP_LOCKED` 423, `OTP_DELIVERY_FAILED` 503 (retryable; the challenge is deleted).
2. `POST /api/internal/auth/challenges/verify`
   Request: `{ "challengeId": "uuid", "code": "123456" }`
   200: `{ "proof": "opaque", "contactType": "...", "maskedContact": "...", "expiresInSeconds": 120 }`
   Errors: `OTP_INVALID` 400 (+`attemptsRemaining`), `OTP_EXPIRED` 410, `OTP_LOCKED` 423 (+`lockedUntil`).
3. `POST /api/internal/auth/accounts/ensure`
   Request: `{ "proof": "...", "clientId": "myticketzm-web", "issueHandle": true|false, "displayName": "(optional)", "consents": [{"purpose":"TERMS","version":"2026-10"}] }`
   200: `{ "accountId": "uuid", "status": "ACTIVE", "isNew": true|false, "loginHandle": "(only if issueHandle)" }`
   202: `{ "accountId": "uuid|null", "status": "PROVISIONING", "retryAfterSeconds": 2 }` (workflow still running; calling again with the same proof is idempotent and never starts a second workflow)
   Errors: `PROOF_INVALID` 400/410, `ACCOUNT_SUSPENDED` 403, `ACCOUNT_MERGING` 409, `CONTACT_ALREADY_CLAIMED` 409 (race lost, retry).
   Known contact with an ACTIVE account: no workflow, answered from the database.
4. `POST /api/internal/auth/handles/redeem`
   Request: `{ "handle": "...", "clientId": "myticketzm-web" }` -> 200 `{ "accountId": "uuid" }`. Handle is deleted on first redeem (atomic GETDEL). Errors: `LOGIN_HANDLE_INVALID` 400/410, `ACCOUNT_NOT_ACTIVE` 409.
5. `GET /api/internal/auth/accounts/{accountId}/status` -> `{ "accountId": "...", "status": "...", "keycloakUserId": "...|null" }` (used by the app callback and by booking to confirm ACTIVE).
6. Keycloak -> identity events (existing path, slimmed): `POST /api/internal/keycloak/sync/event` body `{ "eventId": "...", "eventType": "LOGIN|DELETE|UPDATE_PROFILE|UPDATE_EMAIL|VERIFY_EMAIL|ADMIN_*", "userId": "<keycloak user id>", "username": "<account id or staff username>", "realm": "myticketzm|myticketzm-admin", "enabled": true|false|null, "emailVerified": true|false|null, "timestamp": 1730000000000 }`. No attribute map, no names, no roles, no phone.
   **Session-ending events (D4, ET-IDN-003 R7).** `eventType` also takes `LOGOUT` and `REFRESH_TOKEN_ERROR`, and only these two carry one extra field, `"sid": "<Keycloak SSO session id, the access token's sid claim>"` (an opaque id, not personal data; every other event omits the field). The listener drops either event when it has no `sid` or no `userId`. identity-service answers `202 {"action":"REVOKED"}` after writing the idempotent revocation `SESSION:{sid}` (Mongo first, then Redis `pml:session:{sid}`; reason `keycloak-logout` / `keycloak-refresh-token-error`, `revokedBy` `keycloak:<realm>`), `200 SKIPPED` for an event with no `sid` or a realm it does not serve (never widened to the whole user), and `503` when the revocation could not be written, so the listener retries (5 attempts, backoff 0.5 s doubling). Nothing is deleted; other sessions of the same user are untouched. This is the primary owner of "Keycloak logout revokes that session": it covers a logout from the app, the console, an admin, another client or the mobile app whether or not a web app receives a back-channel logout. The web apps' own `POST /api/internal/revocations/logout` (same `SESSION:{sid}` upsert) is a redundant, harmless second writer.
The old `/api/internal/otp/*` endpoints, GraphQL `requestPhoneOtp/verifyPhoneOtp/login/register/refreshToken/validateToken`, `KeycloakAuthService` password/service-token paths are DELETED (no shim).

## 5. Error codes to add to shared `ErrorCode` (each needs its documentation row)
`CONTACT_INVALID, OTP_LOCKED, OTP_RATE_LIMITED, OTP_DELIVERY_FAILED, CONTACT_ALREADY_CLAIMED, ACCOUNT_SUSPENDED, ACCOUNT_MERGING, ACCOUNT_NOT_ACTIVE, LOGIN_HANDLE_INVALID, PROOF_INVALID`. Existing and kept: `PHONE_NUMBER_INVALID, OTP_INVALID, OTP_EXPIRED, OTP_COOLDOWN_ACTIVE, OTP_ATTEMPTS_EXHAUSTED, USER_UNKNOWN, USER_SYNC_CONFLICT`.

## 6. Redis keys (never contain raw contact values)
`ch:{contactKey}` HMAC(code,pepper)+challengeId+expiry, TTL 5 min (one live code per contact) · `chid:{challengeId}` -> contactKey, TTL 5 min · `ch:att:{contactKey}` tries left from 5, TTL 5 min · `ch:lock:{contactKey}` TTL 15 min (also mirrored to MongoDB `identity_account_events` kind OTP_LOCK) · `ch:cool:{contactKey}` TTL 60 s · `lim:{scope}:{id}:{window}` counters (scope contact|ip|device|country, window hour|day) · `proof:{id}` -> {contactKey, type, valueEncrypted, valueMasked, state NEW|CONSUMED, accountId?}, TTL 2 min while NEW, extended to `identity.proof.ensure-hold` (PT30M) when consumed by `ensure` so the account workflow can read the encrypted contact from the proof (workflow input carries only proofId and contactKey, never the raw contact) · `handle:{id}` -> {accountId, clientId}, TTL 60 s, read-and-delete. Verify is ONE Lua script: checks lock, decrements attempts, constant-time compares, deletes on success, sets lock on exhaustion.

## 7. Config keys (identity-service `application.yml`; no defaults for secrets, startup fails if blank outside profiles `local`,`test`)
`identity.challenge.{code-length:6, ttl:PT5M, max-attempts:5, lock:PT15M, cooldown:PT60S, hmac-pepper}` · `identity.proof.ttl:PT2M` · `identity.proof.ensure-hold:PT30M` · `identity.login-handle.ttl:PT60S` · `identity.contact.{hash-key, encryption-key-id}` (encryption key reuses `app.security.encryption.key`) · `identity.id-hash.key` (workflow ids) · `identity.limits.{contact-codes-per-day:10, ip-codes-per-hour:10, device-distinct-contacts-per-day:5, country-codes-per-day:{default:2000}, allowed-countries:[ZM,GB,US,ZA,ZW,MW,TZ,KE,BW,NA,AE,CA,AU,IE,DE,FR,NL]}` · `identity.delivery.whatsapp.{enabled,api-url,phone-number-id,access-token,template-name,template-language,timeout:PT5S}` · `identity.delivery.email.{enabled,from,timeout:PT5S}` (+ `spring.mail.*`) · `identity.delivery.capture.enabled` (profiles local/test only: in-memory `CapturedMessages` bean for tests; NEVER logs the code).

## 8. Data model (MongoDB)
- `identity_users` (account) keeps `_id`; legacy `_id` = Keycloak id; NEW accounts: `_id` = account UUID. Added optional: `status`, `keycloakUserId`, `primaryContactId`, `preferredChannel` (WHATSAPP|EMAIL), `displayName`, `locale`, `mergedInto`, `provisionedAt`, `pendingKind`, `pendingSince`, `createdVia`. `email`, `username`, `firstName`, `lastName` become optional. Keycloak username for new accounts = `_id`.
- `identity_contacts`: `_id`, `accountId`, `type` (WHATSAPP|EMAIL), `valueHash` (= contactKey), `valueEncrypted`, `valueMasked`, `verifiedAt`, `primary`, `source`, `createdAt`, `releasedAt?`. Unique partial index `uniq_verified_contact` on (`type`,`valueHash`) where `verifiedAt` exists and `releasedAt` absent; `idx_accountId`.
- `identity_consents`: `accountId`, `purpose`, `version`, `grantedAt`, `source`, `withdrawnAt?`.
- `identity_account_events`: `_id`/`eventId` unique, `accountId`, `kind`, `at`, `data` (no personal data).
- Add validators for `identity_audit_logs` and `identity_token_revocations`.

## 9. Keycloak
- Realm `myticketzm` (buyers), client `myticketzm-web` confidential + PKCE S256, no direct grant, exact redirect URIs. Realm `myticketzm-admin` for staff (password + second factor), `user-sync` listener enabled in BOTH realms.
- **Token identity.** Keycloak 26.5.2 ignores an `id` supplied on user create, so for a buyer the token `sub` is the Keycloak user id and is NOT the account id. Every buyer token therefore carries an `accountId` claim (`oidc-usermodel-attribute-mapper` on the user attribute `accountId`, access + id + userinfo, on `myticketzm-web`, the mobile and organizer clients; the staff realm client carries the same mapper and emits nothing for staff). The application user id (`User._id`: org membership, tickets, bookings, catalog ownership, auditing, `Authentication.getName()`) is `AccountIdentity.userIdOf(jwt)` in shared-library: the `accountId` claim when present and non-blank, else `sub` (staff and legacy users, whose `_id` is the Keycloak id). `preferred_username` equals the account id too but is never the source of truth. Revocation is unchanged: `jti`, `sid` and the per-user kill switch keep keying on `sub`/`sid` because they identify Keycloak sessions, and identity revokes by `keycloakUserId`. The gateway `SessionBlacklistFilter` therefore reads `jti`, `sid` and `sub` and never the `accountId` claim.
- **Web clients (one shared BFF for the buyer, organizer and admin Next.js apps).** Confidential, standard flow only, PKCE S256, no direct grant, front-channel logout off. `myticketzm-web` and the organizer client (`TICKETING_ORGANIZER_CLIENT_ID`) live in realm `myticketzm`; the admin client `myticketzm-admin` lives in `myticketzm-admin`. Per client, with `APP_URL` the app's public origin: redirect URI exactly `${APP_URL}/api/auth/callback` (the old `/api/auth/callback/keycloak` Better Auth path is gone); `post.logout.redirect.uris` = `${APP_URL}/`, `${APP_URL}/logged-out` and, buyer only, `${APP_URL}/api/auth/start?resume=1`; back-channel logout URL `${APP_URL}/api/auth/backchannel-logout` with `backchannel.logout.session.required=true` and `backchannel.logout.revoke.offline.tokens=true`. RP-initiated logout with `id_token_hint` and a registered `post_logout_redirect_uri` redirects with no confirmation page.
- **Token claims.** Access tokens carry the gateway audience (`oidc-audience-mapper` on every web and mobile client, value `TICKETING_API_GATEWAY_CLIENT_ID`, default `myticketzm-api-gateway`; set the gateway's `KEYCLOAK_EXPECTED_AUDIENCES` to the same value), `sid`, `sub`, `accountId` (buyer realm), and `auth_time` (id and access token). The staff client takes the built-in `basic` scope, without which a staff token has no `sub` or `auth_time`. Lifetimes: access token 300 s; SSO idle 1800 s; SSO max 36000 s (buyer realm) and 28800 s (staff realm); `revokeRefreshToken=true`, `refreshTokenMaxReuse=0` in both realms.
- New buyer user: `username=<accountId>`, `enabled=true`, `emailVerified` per contact, realm role `CUSTOMER`, attribute `accountId`. User profile: `email`, `firstName`, `lastName` OPTIONAL. Users are edited only with a FULL representation.
- Plugin `contact-otp-authenticator` modes: SCREEN (contact page, code page; calls challenges, verify, ensure(issueHandle=false); then loads user by username=accountId) and HANDOFF (reads `login_hint`, calls handles/redeem, loads user by username=accountId). On missing/disabled user: fail with generic error, never create. Env: `IDENTITY_BASE_URL`, `IDENTITY_CLIENT_ID`, `IDENTITY_CLIENT_SECRET`, `KEYCLOAK_TOKEN_URL`. No unauthenticated mode.

## 10. Temporal
- `AccountEnsureWorkflow` id `account-ensure/{contactKey}`, task queue `identity-account`, update `ensure`, started by Update-with-Start, conflict policy USE_EXISTING. Workflow input: `EnsureCommand(proofId, contactKey, contactType, clientId, issueHandle, displayName, consents)` — no raw contact. Steps (activities, all repeatable): `claimContact` (reads the encrypted contact from `proof:{proofId}`, mints accountId, upsert on unique index, returns existing account if taken), `createKeycloakUser` (409 => read back by username), `applyAttributesAndRoles` (full representation), `activateAccount` (compare-and-set PROVISIONING->ACTIVE, consents + outbox in ONE transaction), `stageOutbox`. Accounts are completed forward, never deleted on failure.
- Search attributes: `BusinessId`=accountId, `ProcessKind`; never contact values. A lint fails if an id/search attribute contains `@` or `+<digits>`.
- Other workflows (later waves): `ContactChange`, `AccountMerge`, `Erasure`, `TicketDelivery`.

## 11. Buyer app (Next.js server side)
Server routes: `POST /api/identity/challenge`, `/api/identity/verify`, `/api/identity/ensure` call identity-service with a service token; session via server-side Better Auth (confidential client `myticketzm-web`); `GET /api/auth/start` redirects to Keycloak with PKCE, `state`, and `login_hint=<handle>`; `GET /api/auth/callback` exchanges the code, then calls `GET .../accounts/{accountId}/status` (the `accountId` claim, else `preferred_username`; never `sub`) and requires ACTIVE before setting the cookie. GraphQL goes through a server route `/api/graphql` that attaches the token; the browser never holds a token.

## 12. Ports shared between agents (identity-service, package `com.pml.identity.account`)
- `interface AccountEnsurer { Mono<EnsureResult> ensure(EnsureCommand cmd); }` implemented by the Temporal facade (`AccountEnsureProcess`); used by the `/accounts/ensure` endpoint (tests use a fake).
- `record EnsureCommand(String proofId, String contactKey, ContactType contactType, String clientId, boolean issueHandle, String displayName, List<ConsentGrant> consents)`; `record ConsentGrant(String purpose, String version)`; `record EnsureResult(String accountId, AccountState status, boolean isNew, String loginHandle, Integer retryAfterSeconds)`.
- `enum ContactType { WHATSAPP, EMAIL }`; `enum AccountState { PROVISIONING, ACTIVE, SUSPENDED, MERGED, DELETED }` (new field `status`; legacy `accountStatus` stays until the cleanup migration).
- `interface KeycloakAccountPort` (implemented by the id-keyed Keycloak admin client): `createUser(accountId, enabled, role)`, `applyAttributesAndRoles(...)`, `findByUsername`, `setEnabled`, used only by workflow activities.

## 13. Division of work inside identity-service (agreed between the engine agent and the account agent)
- **Engine agent** owns packages `com.pml.identity.auth.**` (challenge, limits, delivery, proof, handle, internal controllers incl. `/accounts/ensure`, `/handles/redeem`, `/accounts/{id}/status`, the RFC 9457 advice) and implements `ProofLookup`.
- **Account agent** owns `com.pml.identity.account.**`, `workflow.ensure.**`, `infrastructure.keycloak.*`, user/sync services, GraphQL resolvers and schema, notification destination choice, `TaskQueues`/`WorkflowIds`, and implements `AccountEnsurer` (Temporal) and `AccountStatusLookup`.
- Ports (package `com.pml.identity.account`; the account agent creates them first, the engine agent uses them with fakes in tests): 
  - `interface ProofLookup { Mono<ProofRecord> find(String proofId); }` and `record ProofRecord(String proofId, String contactKey, ContactType type, String valueEncrypted, String valueMasked, String state, String accountId)` (implemented by the engine agent over Redis).
  - `interface AccountStatusLookup { Mono<AccountStatusView> byAccountId(String accountId); }` and `record AccountStatusView(String accountId, AccountState status, String keycloakUserId)`.
- The `/accounts/ensure` endpoint (engine agent) marks the proof CONSUMED, extends its TTL, builds `EnsureCommand`, calls `AccountEnsurer.ensure`, and — only when the result is ACTIVE and `issueHandle` — issues the login handle itself (`LoginHandleService`), so `EnsureResult.loginHandle` from the Temporal side is always null. Repeated calls with a CONSUMED proof re-call `ensure` (idempotent through the workflow id) and may issue a fresh handle.
- Tests run with Testcontainers: MongoDB replica set (mongo:8), Redis (redis:7-alpine), Temporal (TemporalDevServer fixture in shared-library or `TestWorkflowEnvironment`), Keycloak (quay.io/keycloak/keycloak:26.5.2 with the realm from `docker-resources/keycloak`).

## 14. Contact management (GraphQL, signed-in buyer; ET-IDN-004 R3, R5)

Added 2026-10-04. Decisions (F-044): authorisation of a change, removal or primary switch = a fresh code to the CURRENT primary verified contact; no support-recovery path (an account with no verified contact able to receive a code is refused `NO_VERIFIED_CONTACT`); a released contact is quarantined for `identity.contact.quarantine` (default `P30D`); the last verified contact cannot be removed; one primary, switchable among verified contacts after step-up.

All operations are authenticated, act on the token's own account (`AccountIdentity.userIdOf`), take no account id, and return masked values only. Staff tokens get `ACTOR_NOT_PERMITTED`; a non-ACTIVE account gets `ACCOUNT_NOT_ACTIVE` / `ACCOUNT_MERGING`. Errors are the platform's GraphQL errors with `extensions.errorCode`.

```graphql
type Query    { myContacts: MyContacts! }
type Mutation {
  requestContactAdd(input: {type: ContactType!, value: String!, regionHint: String}): ContactCodeSent!
  confirmContactAdd(input: {challengeId: ID!, code: String!}): ContactChangeResult!
  requestContactChange(input: {contactId: ID!, type: ContactType!, value: String!, regionHint: String}): ContactChangeRequested!
  confirmContactChange(input: {changeId: ID!, newContactCode: String!, currentContactCode: String}): ContactChangeResult!
  cancelContactChange(changeId: ID!): Boolean!
  requestContactRemoval(contactId: ID!): ContactCodeSent!
  confirmContactRemoval(input: {challengeId: ID!, code: String!}): ContactChangeResult!
  requestPrimaryContact(contactId: ID!): ContactCodeSent!
  setPrimaryContact(input: {contactId: ID!, challengeId: ID!, code: String!}): ContactChangeResult!
}
ContactCodeSent        { challengeId, contactType, maskedContact, expiresInSeconds, resendAfterSeconds }
ContactChangeRequested { changeId, newContact: ContactCodeSent!, currentContact: ContactCodeSent!, expiresAt }
ContactChangeResult    { changeId, kind: ADD|CHANGE|REMOVE|PRIMARY, status: COMPLETED|APPLYING, contacts: [Contact!]! }
MyContacts             { contacts: [Contact!]!, pendingChange: PendingContactChange }
PendingContactChange   { changeId, kind, newContactMasked, expiresAt, currentContactVerified, attemptsRemaining }
Contact                { id, type, valueMasked, verifiedAt, primary, createdAt }   # unchanged
```

Flows. **Add:** `requestContactAdd` -> code to the new contact -> `confirmContactAdd`. **Change:** `requestContactChange` -> two codes (new contact, current primary) and an open change (`pendingKind=CHANGING`, workflow `contact-change/{accountId}`, 48 h) -> `confirmContactChange` with both (the current-contact code may be left out once accepted; wrong codes count, five abort) -> the new contact is claimed, Keycloak updated, the old contact released, sessions ended. **Remove / primary:** `requestContactRemoval` / `requestPrimaryContact` -> code to the current primary -> `confirmContactRemoval` / `setPrimaryContact`.

`status: APPLYING` means the codes were right and the change is finishing in the background (Keycloak slow or down; the old contact still works); ask `myContacts` again. It never means failure. Errors by operation: `CONTACT_INVALID`, `OTP_INVALID` (+`attemptsRemaining`), `OTP_EXPIRED`, `OTP_LOCKED`, `OTP_RATE_LIMITED`, `OTP_COOLDOWN_ACTIVE`, `OTP_DELIVERY_FAILED`, `OTP_ATTEMPTS_EXHAUSTED` (change aborted), `CONTACT_ALREADY_CLAIMED` (another account holds it or released it within the quarantine), `CONTACT_UNKNOWN` (not your contact, or no such change), `CONTACT_CHANGE_IN_PROGRESS`, `LAST_VERIFIED_CONTACT`, `NO_VERIFIED_CONTACT`, `PROOF_INVALID` (the code was for another contact), `COMMAND_NOT_WELL_FORMED` (same type required for a change; current-contact code missing).

**Resend.** `resendContactCode(input: {challengeId: ID, changeId: ID, target: NEW|CURRENT}): ContactCodeSent!` - name the `challengeId` of an add, removal or primary switch, or the open `changeId` with a `target` (the code to the new contact, or to the current primary). Allowed once `identity.contact.resend-after` (default `PT5M`; "after five" read as five minutes) has passed since the code was last sent; earlier: `OTP_RATE_LIMITED` with `retryAfterSeconds`. The new code REPLACES the old one and the result carries a NEW `challengeId` (and `resendAfterSeconds`): use it from then on; the old id is `OTP_EXPIRED`. An open change keeps its 48-hour expiry and its attempt budget. Other errors: `CONTACT_UNKNOWN` (not your challenge or change), `COMMAND_NOT_WELL_FORMED` (target missing, or that code was already accepted), the usual `OTP_*` from the code request. `confirmContactAdd` needs only the new contact's code (no step-up), by decision.

**Repair.** There is none: the scheduled drift repair was removed on 2026-10-10 (spec R4).

Config: `identity.contact.quarantine` (`P30D`), `identity.contact.resend-after` (`PT5M`), `identity.contact.change-wait` (`PT20S`, how long a request waits before answering `APPLYING`), `identity.delivery.whatsapp.notice-template-name` (optional prefix of parameterless notice templates).

