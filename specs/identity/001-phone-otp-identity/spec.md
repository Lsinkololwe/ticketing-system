# ET-IDN-001 · Contact-OTP passwordless identity

> **Conformance** · US Part I §6 Keycloak integration
>
> **Redesigned 2026-10-04** (decisions D-38..D-50 in [ROADMAP](../../ROADMAP.md); binding wire contract in
> [`../004-accounts-and-contacts/CONTRACT.md`](../004-accounts-and-contacts/CONTRACT.md); finding
> [F-044](../../FINDINGS.md)). The folder keeps its old name `001-phone-otp-identity` so references stay
> stable; the capability is no longer phone-only.

## 1. Capability

A buyer proves they own a **contact** — a WhatsApp number or an email address — by typing a
one-time code, and nothing else. There is no password anywhere in the buyer flow: nothing to
forget, nothing to reuse from a breached site, nothing to phish that is worth more than five
minutes. In this market the handset is the identity people actually have, and email is the
fallback for the diaspora and for anyone without WhatsApp; both are accepted from the first day
(**D-39**).

This spec builds the proof, not the account. identity-service owns the **challenge** (generate,
deliver, throttle, verify), and a correct code yields a single-use **proof**. The proof is
handed to the account workflow of [ET-IDN-004](../004-accounts-and-contacts/), which resolves the
contact to exactly one account, creating it identity-first when needed. A buyer completes the
code step inside the checkout page (**D-44**); identity-service then issues a one-time **login
handle** that a Keycloak authenticator redeems to finish an authorization-code flow with no
further screen. **Keycloak remains the only issuer of tokens.**

It also declares the properties an OTP system gets wrong invisibly until they are exploited:
the code is stored as a keyed hash, verification is one atomic constant-time script, asking for a
code for an unknown contact looks exactly like asking for a known one, the code appears in no log,
and every request is limited per contact, per IP, per device, per country and per day. Platform
staff do not use this flow at all; they sign in with a password and a second factor.

## 2. Design decisions

**Keycloak issues every token; identity-service verifies the code.** For the buyer flow the code is
entered in the checkout page, so the verification cannot be a Keycloak screen. identity-service
therefore verifies the code, mints a proof, and (through the account workflow) a login handle: a
60-second, single-use secret bound to an ACTIVE account and a client. A Keycloak authenticator
(the *contact authenticator*) redeems the handle and completes the flow. This amends the earlier
rule that the code is typed into a Keycloak form (**D-44**) but keeps the property that matters:
no application component ever mints or requests a token on a user's behalf. The same authenticator
also has a SCREEN mode that shows the two pages itself, for clients that cannot host the checkout
step.

**Two channels at launch: WhatsApp and email (D-39).** International WhatsApp numbers and email
addresses are accepted; the country must be on an allowlist. Delivery is attempted on the buyer's
preferred channel for the contact type and the response names the channel used. A WhatsApp number
whose message cannot be delivered is refused with a retryable code; the platform does not silently
switch a code to email, because that would send it to an address the buyer never gave.

**The code is stored as a keyed hash and compared in constant time, atomically.**
`HMAC-SHA256(code, pepper)` lives in Redis; verification is one Lua script that checks the lock,
decrements the attempts, compares in constant time, deletes the code on success and sets the lock
on exhaustion. Doing these as separate Redis calls is a race that gives an attacker more than five
guesses.

**Keys are derived from the contact, never contain it.** Redis keys, the uniqueness index and
workflow ids use `contactKey = HMAC-SHA256(identity.contact.hash-key, TYPE + ":" + normalized)`. A
Redis dump or a Temporal history reveals no phone number or address (**D-46**).

**A request for an unknown contact looks exactly like a request for a known one.** Same status,
same body shape, same latency band, extended to email. Registration state is learned only after
the contact is proved, and only by the proof holder.

**Five minutes, sixty seconds, five tries, fifteen minutes (D-50).** The code lives 5 minutes; a
resend is refused for 60 seconds; five wrong entries delete the code and lock the contact for 15
minutes. On top of that, volume limits apply per contact per day, per IP per hour, per device
(distinct contacts per day) and per country per day, so a code cannot be used as a free
messaging gun against strangers.

**The proof and the handle are separate, short and single-use.** The proof (2 minutes while new)
authorises exactly one `ensure` call; the handle (60 seconds) authorises exactly one sign-in. Each
is read-and-delete or compare-and-set, so a replay finds nothing.

**One contact, one account, created by the workflow, never by the plugin (D-43).** The Keycloak
authenticator loads the user whose username is the account id and fails with a generic error when
none exists. It never calls `addUser`, never grants roles, and never talks to Temporal or MongoDB.

**Staff keep password plus a second factor.** Platform staff live in a separate realm
(**D-48**) and never reach the contact flow. A handset-only factor for an account that can approve a
payout is a SIM swap away from the platform's money.

**Rejected alternatives**

- *An application login endpoint that verifies the OTP and requests a token on the user's behalf.* A second authentication path around Keycloak's. The login handle is not this: it is redeemed *by Keycloak*, which issues the token.
- *Storing the code in plaintext because it expires in five minutes.* A Redis dump yields every live code.
- *Distinguishing "no account for this contact" at request time.* A membership oracle, now for emails too.
- *Allowing multiple live codes so a slow message still works.* Doubles the guessable surface to solve a problem the 60-second cooldown solves.
- *SMS as a channel or as a fallback.* Removed (**D-39**): the cost and the SIM-swap and interception exposure outweigh the reach it adds for buyers who already have WhatsApp or email, and sending sign-in links by SMS trains buyers to tap links in texts.
- *Email OTP as "a third provider contract for a channel this market does not check".* Removed from the rejected list (**D-39**): the diaspora and buyers without WhatsApp check email, a mail provider is a commodity, and email is already the receipt channel.
- *Silently re-sending a failed WhatsApp code by email.* Sends a secret to an address the buyer did not give for this purpose; the buyer is told and chooses.
- *The authenticator creating the Keycloak user on first login.* Races produce duplicate users, and the plugin would hold realm-admin rights; creation is the workflow's.
- *Letting the registrant choose their own account type.* Roles come from the server, never from a form field.

## 3. Requirements

### ET-IDN-001-R1 · A contact is one identity, normalised once

WHEN a contact is submitted in any form, THE SYSTEM SHALL normalise it and treat every
representation of one contact as one identity.

**Acceptance**
- [ ] A phone number is normalised to strict E.164: it carries a country code (`+` or `00` prefix) or the caller supplies `regionHint`; `ZM` is assumed only for a 10-digit number starting with `0`
- [ ] `0977123456`, `260977123456`, `+260 977 123 456`, `00260977123456` and `+260977123456` all normalise to `+260977123456`
- [ ] A phone number must be a MOBILE or FIXED_LINE_OR_MOBILE number for its region; anything else is refused with `CONTACT_INVALID` before any code is generated or message sent
- [ ] The phone's country must be in `identity.limits.allowed-countries`; otherwise `CONTACT_INVALID`
- [ ] An email is trimmed, lower-cased, NFC-normalised, matches the simple RFC 5322 form and is at most 254 characters
- [ ] Both forms hash to a `contactKey`; no raw contact appears in a Redis key, a log line, a workflow id or a URL
- [ ] Normalisation exists in exactly one implementation, in `shared-library`, used by identity-service and by the contact authenticator

### ET-IDN-001-R2 · A code is generated, delivered on WhatsApp or email, and lives five minutes

WHEN a code is requested for a valid contact, THE SYSTEM SHALL generate a six-digit code, deliver it by the contact's channel, and accept it for five minutes.

**Acceptance**
- [ ] The code is six digits from a `SecureRandom`, uniform over `000000`–`999999`
- [ ] `ch:{contactKey}` holds `HMAC-SHA256(code, pepper)`, the challenge id and the expiry, with a 300 s TTL, never the code
- [ ] A new request replaces the previous code: exactly one code is live per contact
- [ ] A phone contact is delivered on WhatsApp and an email contact by email; `preferredChannel` may choose between channels only where the contact type supports both
- [ ] The 202 response names the `channel` actually used, the `maskedContact`, `expiresInSeconds` and `resendAfterSeconds`
- [ ] WHEN the contact's channel is not enabled, THEN the request is refused with `NOTIFICATION_CHANNEL_UNAVAILABLE`
- [ ] IF the provider call fails or times out (`timeout` 5 s), THEN the challenge is deleted, no code is left live, and the request is refused with `OTP_DELIVERY_FAILED`, retryable
- [ ] The WhatsApp message uses an approved template and the email a fixed template; the code is URL-encoded or escaped wherever it is placed in a provider request
- [ ] A frozen-clock test accepts the code at 4:59 and refuses it with `OTP_EXPIRED` at 5:01

### ET-IDN-001-R3 · Resends are throttled, attempts are capped, volume is limited

WHILE a code is live, THE SYSTEM SHALL refuse a resend within the cooldown; IF the attempt limit is reached, THEN THE SYSTEM SHALL invalidate the code and lock the contact; AND volume limits SHALL apply per contact, IP, device and country.

**Acceptance**
- [ ] A second request within 60 s is refused with `OTP_COOLDOWN_ACTIVE` carrying `retryAfterSeconds`, and sends no message (`ch:cool:{contactKey}`, 60 s)
- [ ] A wrong code is refused with `OTP_INVALID` carrying `attemptsRemaining`, decrementing `ch:att:{contactKey}` from 5
- [ ] The fifth wrong attempt deletes the code and sets `ch:lock:{contactKey}` for 15 minutes; further requests and verifications are refused with `OTP_LOCKED` carrying `lockedUntil`
- [ ] The lock is also written to MongoDB `identity_account_events` (kind `OTP_LOCK`) so a Redis loss does not release it
- [ ] Limits apply as `lim:{scope}:{id}:{window}` counters: contact codes per day (10), IP codes per hour (10), distinct contacts per device per day (5), codes per country per day (configurable); exceeding one is `OTP_RATE_LIMITED` with `retryAfterSeconds`
- [ ] The client IP is taken only from the trusted-proxy chain defined by [ET-PLT-011](../../_platform/011-rate-limiting-and-abuse/), never from a caller-supplied header alone
- [ ] A successful verification deletes the code, the attempts and the cooldown
- [ ] Verification is ONE Lua script; a concurrency test of 50 parallel wrong guesses consumes exactly 5 attempts
- [ ] Frozen-clock tests assert the cooldown at 59 s / 61 s and the lock at 14:59 / 15:01

### ET-IDN-001-R4 · Verification leaks nothing

WHEN a code is requested or verified, THE SYSTEM SHALL compare in constant time and SHALL disclose nothing about the contact's registration state or the code's value, for phone and for email alike.

**Acceptance**
- [ ] Comparison is a constant-time digest comparison inside the verify script; no `String.equals` or `==` on a code or digest appears
- [ ] A challenge for an unregistered contact and one for a registered contact (phone or email) return the same status, the same body shape and a latency within the same band, asserted by a test
- [ ] `verify` does not distinguish "no such challenge" from "wrong contact" beyond `OTP_EXPIRED` / `OTP_INVALID`
- [ ] No log statement at any level, in any profile, contains the code; asserted by capturing log output across a full request-and-verify cycle
- [ ] No provider request or response body containing the code is logged; the in-memory capture used by tests never writes the code to a log
- [ ] The code appears in no exception message and no REST error body; contact values appear in no URL (contacts travel in request bodies)
- [ ] Fixed test codes exist only under profiles `local` and `test`; a startup check refuses them elsewhere

### ET-IDN-001-R5 · Keycloak is the only token issuer

THE SYSTEM SHALL authenticate the user through a Keycloak authentication flow, and no other component SHALL issue or request tokens on a user's behalf.

**Acceptance**
- [ ] The contact authenticator and its factory are registered through `META-INF/services/org.keycloak.authentication.AuthenticatorFactory`
- [ ] In HANDOFF mode the authenticator reads `login_hint`, calls `POST /api/internal/auth/handles/redeem`, and loads the user whose username equals the returned `accountId`; the handle is consumed by the first redeem
- [ ] In SCREEN mode it renders the contact page and code page, calls `challenges`, `challenges/verify` and `accounts/ensure` with `issueHandle=false`, then loads the user by username
- [ ] The code is verified by identity-service in both modes; the authenticator holds no MongoDB, Redis or Temporal dependency
- [ ] The authenticator authenticates to identity-service with `client_credentials` using `IDENTITY_CLIENT_ID` / `IDENTITY_CLIENT_SECRET`; it refuses to start when they are missing, and there is no unauthenticated mode
- [ ] The buyer client is confidential with PKCE S256 and no direct-access grant; no application code calls the token endpoint with a password or a service token on a buyer's behalf
- [ ] Platform staff authenticate in realm `myticketzm-admin` with password and a second factor; the contact flow is not bound there

### ET-IDN-001-R6 · A verified contact resolves to exactly one account

WHEN a proof is presented, THE SYSTEM SHALL resolve it to the single account holding that contact, creating the account identity-first through the account workflow when none exists.

**Acceptance**
- [ ] `POST /api/internal/auth/accounts/ensure` consumes a proof once; a second call with the same proof returns the same answer and never starts a second workflow
- [ ] A known contact with an ACTIVE account is answered from the database with no workflow
- [ ] An unknown contact starts `AccountEnsureWorkflow` ([ET-IDN-004](../004-accounts-and-contacts/)); the response is 202 `PROVISIONING` until the account is ACTIVE
- [ ] The Keycloak username of a new account equals the account id; the account id is never derived from the contact
- [ ] The contact authenticator never creates a user, never grants a role, and fails with a generic error when the user is missing or disabled
- [ ] Two concurrent proofs for one contact produce exactly one account; the loser receives the winner's account (`CONTACT_ALREADY_CLAIMED` is retried, not surfaced as a second account)
- [ ] A SUSPENDED account is refused with `ACCOUNT_SUSPENDED`; an account being merged with `ACCOUNT_MERGING`
- [ ] The role granted to a new account is `CUSTOMER`, set by the workflow; a registrant cannot choose a type

### ET-IDN-001-R7 · Privileged accounts are not contact-only

THE SYSTEM SHALL require a password and a second factor for platform staff and keep them out of the contact flow.

**Acceptance**
- [ ] Platform staff exist only in realm `myticketzm-admin`, whose browser flow is username-password plus a required second factor
- [ ] The contact authenticator refuses an account holding `ADMIN`, `SUPER_ADMIN` or `FINANCE` and records the denial, without revealing why to the caller
- [ ] A staff account without a configured credential cannot be activated
- [ ] A test asserts an `ADMIN` cannot complete the contact flow

### ET-IDN-001-R8 · A signed-in buyer manages sign-in contacts in the profile

WHEN a signed-in buyer opens `/profile`, THE SYSTEM SHALL show their sign-in contacts and let them add the other kind of contact, change a contact, remove a contact and choose the primary one, each proved by code, and SHALL show a clear state for every refusal and for a server that is still working.

Binding behaviour lives in [ET-IDN-004](../004-accounts-and-contacts/) R3 (one account per contact, last verified contact is never removed) and R5 (change by re-proof, without a gap); this requirement is the buyer-app surface of them.

**Acceptance**
- [ ] `/profile` is reachable only with a valid server-side session (cookie guard in the proxy plus `requireSession`); a gone session during any call sends the buyer to `/auth?next=/profile`
- [ ] The list shows, per contact, the masked value, a WhatsApp or Email badge, Verified or Not verified, Primary, and any pending change (masked new value and expiry time); no raw value is ever rendered
- [ ] Add offers only the kind of contact the account does not hold yet; the code is sent to the new contact and the contact appears only after the correct code
- [ ] Change asks for a code sent to the new contact and, when the server requires it, a second code sent to the current primary contact; both are confirmed in one step
- [ ] Remove sends a code to the current primary contact; a contact that is the last verified one is blocked with an explanation before any request, and a server refusal `LAST_VERIFIED_CONTACT` shows the same explanation
- [ ] Set primary is offered only for a verified contact that is not primary, and is authorised by a code sent to the current primary contact (`requestPrimaryContact`, `setPrimaryContact`)
- [ ] A wrong code shows the attempts left; `OTP_LOCKED` and `OTP_RATE_LIMITED` disable input and show when to try again; `OTP_EXPIRED` and a lost claim return to the entry step; `OTP_DELIVERY_FAILED` and a network failure keep the step and say nothing changed
- [ ] `CONTACT_ALREADY_CLAIMED` (another account holds the contact, a lost race, or a quarantine) shows one neutral message that never says another account holds it
- [ ] Every code field has its own "Send code again" button (add, remove, primary: one; change: one per code, new and current contact), disabled with a visible countdown and an accessible name carrying the time left until `resendAfterSeconds` has passed, announced once when it becomes available; a click calls `resendContactCode` through the BFF action `resend`, never twice at once, shows "Code sent again to <masked>", clears the field and restarts the countdown from the response
- [ ] A too-early resend (`OTP_RATE_LIMITED` + `retryAfterSeconds`) re-arms the countdown without locking the step; `OTP_LOCKED` locks it; `OTP_EXPIRED` returns to entry; `CONTACT_UNKNOWN` (change gone) is explained; a network error keeps the step; an expired code offers both send again and start over
- [ ] `status: APPLYING` (HTTP 202 from the BFF) shows a calm "finishing" state, polls `myContacts` until no change is pending, never re-sends the codes, then offers "Check again"
- [ ] A pending change from `myContacts` is shown (kind, masked new contact, expiry, attempts left) with a "Cancel change" action (`cancelContactChange`)
- [ ] Every mutation is a same-origin POST to `/api/profile/contacts/{action}` carrying the CSRF guard (Origin equals `APP_URL`, `Sec-Fetch-Site` same-origin, `X-Requested-With`), rate limited per IP; the route attaches the buyer's token server-side; responses are whitelisted to documented fields
- [ ] Dialogs trap and restore focus, move focus to the code field when a code is requested, label every input, announce code errors in an `aria-live`/`role="alert"` region and "code sent" in a polite status region; buttons carry the contact in their accessible name; the layout works at 320px width and touch targets are at least 44px
- [ ] A raw contact value, code, challenge or proof is never written to `localStorage`, `sessionStorage`, a cookie, a URL or a log line; the value lives in component state for the life of the dialog only

### ET-IDN-001-R9 · Terms and Privacy pages exist and are linked

THE SYSTEM SHALL serve `/terms` and `/privacy` as public pages, clearly marked as drafts awaiting legal review, and link them from sign-in and from the consent line.

**Acceptance**
- [ ] `GET /terms` and `GET /privacy` return 200 for an anonymous visitor and are not behind the session guard
- [ ] Each page shows a visible "Draft for legal review" notice and a last-updated date
- [ ] The identify step's consent line links to both pages
- [ ] Replacing the draft text with the reviewed text needs no code change outside the page content (tracked as an open item for legal review)

## 4. Model

### Redis keys

None contains a raw contact. Every key is ephemeral; none is an authority for anything except the
challenge itself.

| Key | Type | TTL | Holds |
|---|---|---|---|
| `ch:{contactKey}` | STRING/HASH | 5 min | `HMAC(code, pepper)`, challengeId, expiry (one live code per contact) |
| `chid:{challengeId}` | STRING | 5 min | contactKey |
| `ch:att:{contactKey}` | STRING | 5 min | tries left, from 5 |
| `ch:lock:{contactKey}` | STRING | 15 min | lock marker (mirrored to MongoDB `identity_account_events` kind `OTP_LOCK`) |
| `ch:cool:{contactKey}` | STRING | 60 s | resend throttle |
| `lim:{scope}:{id}:{window}` | counter | hour/day | scope `contact`\|`ip`\|`device`\|`country` |
| `proof:{id}` | HASH | 2 min while NEW; `ensure-hold` (PT30M) once CONSUMED | contactKey, type, valueEncrypted, valueMasked, state NEW\|CONSUMED, accountId? |
| `handle:{id}` | HASH | 60 s | accountId, clientId; read-and-delete (GETDEL) |

### Configuration

Secrets have no defaults; startup fails when blank outside profiles `local` and `test`.

| Property | Value |
|---|---|
| `identity.challenge.code-length` | 6 |
| `identity.challenge.ttl` | `PT5M` |
| `identity.challenge.max-attempts` | 5 |
| `identity.challenge.lock` | `PT15M` |
| `identity.challenge.cooldown` | `PT60S` |
| `identity.challenge.hmac-pepper` | environment only |
| `identity.proof.ttl` / `identity.proof.ensure-hold` | `PT2M` / `PT30M` |
| `identity.login-handle.ttl` | `PT60S` |
| `identity.contact.hash-key`, `identity.contact.encryption-key-id` | environment only; encryption reuses `app.security.encryption.key` |
| `identity.id-hash.key` | environment only (workflow ids) |
| `identity.limits.contact-codes-per-day` / `ip-codes-per-hour` / `device-distinct-contacts-per-day` | 10 / 10 / 5 |
| `identity.limits.country-codes-per-day` | `{default: 2000}` |
| `identity.limits.allowed-countries` | `ZM, GB, US, ZA, ZW, MW, TZ, KE, BW, NA, AE, CA, AU, IE, DE, FR, NL` |
| `identity.delivery.whatsapp.*` | `enabled, api-url, phone-number-id, access-token, template-name, template-language, timeout: PT5S` |
| `identity.delivery.email.*` | `enabled, from, timeout: PT5S` (+ `spring.mail.*`) |
| `identity.delivery.capture.enabled` | profiles `local`/`test` only: in-memory `CapturedMessages`; never logs the code |

### The internal REST contract

Scope `internal-write` (GET: `internal-read`). Every refusal is an RFC 9457 problem document with
the registry `errorCode`, plus `retryable`, `retryAfterSeconds`, `attemptsRemaining`, `lockedUntil`
where relevant. Request and response shapes are fixed by
[CONTRACT.md §4](../004-accounts-and-contacts/CONTRACT.md) and are not repeated here.

| Method | Path | Refusals |
|---|---|---|
| `POST` | `/api/internal/auth/challenges` | `CONTACT_INVALID` 400, `OTP_RATE_LIMITED` 429, `OTP_COOLDOWN_ACTIVE` 429, `OTP_LOCKED` 423, `OTP_DELIVERY_FAILED` 503, `NOTIFICATION_CHANNEL_UNAVAILABLE` |
| `POST` | `/api/internal/auth/challenges/verify` | `OTP_INVALID` 400, `OTP_EXPIRED` 410, `OTP_LOCKED` 423 |
| `POST` | `/api/internal/auth/accounts/ensure` | `PROOF_INVALID`, `ACCOUNT_SUSPENDED` 403, `ACCOUNT_MERGING` 409, `CONTACT_ALREADY_CLAIMED` 409 |
| `POST` | `/api/internal/auth/handles/redeem` | `LOGIN_HANDLE_INVALID`, `ACCOUNT_NOT_ACTIVE` 409 |
| `GET` | `/api/internal/auth/accounts/{accountId}/status` | `USER_UNKNOWN` |

The old `/api/internal/otp/*` endpoints, the GraphQL authentication operations and the password and
service-token login paths are deleted with no compatibility shim.

### The flow

```
checkout page        buyer app (server)        identity-service              Keycloak
     │ contact            │                         │                           │
     │───────────────────▶│ POST /challenges        │                           │
     │                    │────────────────────────▶│ normalise, limits, code,  │
     │                    │ 202 channel, masked     │ deliver (WhatsApp|email)  │
     │ code               │◀────────────────────────│                           │
     │───────────────────▶│ POST /challenges/verify │                           │
     │                    │────────────────────────▶│ one Lua script            │
     │                    │ proof (2 min)           │                           │
     │                    │ POST /accounts/ensure   │ known+ACTIVE: DB answer   │
     │                    │────────────────────────▶│ unknown: AccountEnsureWorkflow
     │                    │ ACTIVE + loginHandle    │ (idempotent, 202 until ACTIVE)
     │                    │◀────────────────────────│                           │
     │ redirect           │ /auth/start (PKCE, login_hint=handle)               │
     │◀───────────────────│─────────────────────────────────────────────────────▶│
     │                    │                         │◀ handles/redeem (GETDEL) ──│
     │                    │                         │ accountId ───────────────▶│ load user by username
     │                    │ /auth/callback code ◀──────────── tokens to server ──│
     │                    │ status == ACTIVE ⇒ set HttpOnly session cookie       │
```

### Keycloak flow

```
contact-browser                           [buyer realm browser flow]
├── Cookie                                ALTERNATIVE
└── Contact OTP Authenticator             ALTERNATIVE   (modes HANDOFF, SCREEN)

staff-browser                             [realm myticketzm-admin]
├── Username Password Form                REQUIRED
└── OTP Form (second factor)              REQUIRED
```

Realms, clients, flows and the user profile are code (realm export), reviewed in
[ET-PLT-007](../../_platform/007-security-and-authorization/). The `user-sync` listener is
enabled in both realms.

### The SPI

`backend/keycloak-extensions/`, a shaded JAR in `/opt/keycloak/providers/`.

| Component | Role |
|---|---|
| `ContactOtpAuthenticator` | HANDOFF and SCREEN modes; loads the user by username = account id; never creates |
| `ContactOtpAuthenticatorFactory` | SPI registration; env `IDENTITY_BASE_URL`, `IDENTITY_CLIENT_ID`, `IDENTITY_CLIENT_SECRET`, `KEYCLOAK_TOKEN_URL`; refuses to start if any is blank |
| `IdentityClient` | the calls above, bounded timeouts, `client_credentials`, no unauthenticated mode |
| `contact-input.ftl`, `contact-code.ftl` | SCREEN mode pages; channel is never read from the form |
| `messages_en.properties` | every user-visible string |

### The buyer app server

How `apps/ticketing` holds identity, recorded as built (D-45):

- **Session.** A custom opaque session, not Better Auth: Better Auth's generic OAuth callback cannot gate session creation on the identity-service account status or carry `login_hint`, and it persists a legacy `users` row that new accounts (optional email and username) do not have. The browser holds only an opaque HttpOnly cookie (`__Host-pml_buyer` in production); the server record in Redis (`REDIS_URL`; in-process map for local and tests) holds the tokens, with a one-hour TTL and refresh when the access token is within 30 s of expiry. A pre-session `flow` cookie and record hold the `proof`, the login handle (60 s) and the PKCE state; none of them is returned to the browser.
- **Account id.** The callback takes the account id from the `accountId` claim, then `preferred_username`, never `sub`, and calls `GET /api/internal/auth/accounts/{accountId}/status`; it sets the cookie only for `ACTIVE`.
- **CSRF.** Every browser mutation route (`/api/identity/*`, `/api/checkout/intent`, `/api/profile/contacts/*`, `/api/graphql`) requires `Origin == APP_URL`, `Sec-Fetch-Site: same-origin` and `X-Requested-With: pml-web`, and is rate limited per client IP (trusted-proxy chain only).
- **GraphQL.** `/api/graphql` and the profile routes attach the session's bearer token on the server; anonymous discovery calls go without one.
- **CSP.** `src/proxy.ts` sets a per-request nonce Content-Security-Policy and redirects guarded paths (`/my-tickets`, `/profile`) that have no session cookie to `/auth?next=`; real authorisation is `requireSession()` against the server store.
- **Contact operations.** `/api/profile/contacts` (GET) and `/api/profile/contacts/{action}` (POST: `add-request`, `add-confirm`, `change-request`, `change-confirm`, `remove-request`, `remove-confirm`, `primary`) call the identity GraphQL operations of CONTRACT section 14 (`myContacts, requestContactAdd, confirmContactAdd, requestContactChange, confirmContactChange, cancelContactChange, requestContactRemoval, confirmContactRemoval, requestPrimaryContact, setPrimaryContact`); actions are `add-*`, `change-request|confirm|cancel`, `remove-*`, `primary-request|confirm` through the gateway.

### GraphQL

None for sign-in. The buyer app reaches identity-service by server-side REST. `me` and the profile belong to
[ET-IDN-002](../002-keycloak-user-sync/) and [ET-IDN-004](../004-accounts-and-contacts/).

### Error codes

New here: `CONTACT_INVALID, OTP_LOCKED, OTP_RATE_LIMITED, OTP_DELIVERY_FAILED, LOGIN_HANDLE_INVALID, PROOF_INVALID` (rows of [ET-PLT-005 §4](../../_platform/005-error-contract/)); used here and introduced by [ET-IDN-004](../004-accounts-and-contacts/): `CONTACT_ALREADY_CLAIMED, ACCOUNT_SUSPENDED, ACCOUNT_MERGING, ACCOUNT_NOT_ACTIVE`. Kept: `PHONE_NUMBER_INVALID, OTP_INVALID, OTP_EXPIRED, OTP_COOLDOWN_ACTIVE, OTP_ATTEMPTS_EXHAUSTED, USER_UNKNOWN, USER_SYNC_CONFLICT`. `NOTIFICATION_CHANNEL_UNAVAILABLE` is introduced by [ET-NTF-001](../../notification/001-notification-transport/) and used here.

### Workflows

`AccountEnsureWorkflow` (`account-ensure/{contactKey}`, queue `identity-account`) is owned by
[ET-IDN-004](../004-accounts-and-contacts/); this spec only calls it through the `AccountEnsurer` port.

## 5. Tasks

- [ ] **T1 · `ContactNormalizer` and hashing in `shared-library`**
  - requirements: R1
  - files: `backend/shared-library/src/main/java/com/pml/shared/contact/`
  - verify: the five phone representations converge; email normalisation; allowlist; `contactKey` is stable
  - parallel-safe: no — the plugin and identity-service depend on it
  - depends: —

- [ ] **T2 · `ChallengeService`: HMAC storage, one-script verify, cooldown, attempts, lock mirrored to MongoDB**
  - requirements: R2, R3, R4
  - files: `backend/identity-service/src/main/java/com/pml/identity/auth/`
  - verify: frozen-clock boundaries; 50 parallel guesses consume exactly 5 attempts; lock survives a Redis flush
  - parallel-safe: no
  - depends: T1

- [ ] **T3 · Limits: contact, IP, device, country, day**
  - requirements: R3
  - files: `backend/identity-service/.../auth/limits/`
  - verify: each scope refuses at its threshold with `OTP_RATE_LIMITED`; IP comes from the trusted chain
  - parallel-safe: yes
  - depends: T2

- [ ] **T4 · Delivery: WhatsApp template and email, timeouts, encoding, capture bean, refusal codes**
  - requirements: R2
  - files: `backend/identity-service/.../infrastructure/messaging/`
  - verify: WireMock 503 and a timeout each yield `OTP_DELIVERY_FAILED` and leave no live code; disabled channel yields `NOTIFICATION_CHANNEL_UNAVAILABLE`
  - parallel-safe: yes
  - depends: T2

- [ ] **T5 · The five internal endpoints, scope-gated, problem documents, no contact in any URL**
  - requirements: R2, R3, R4, R6
  - files: `backend/identity-service/.../web/rest/AuthInternalController.java`
  - verify: 401/403/200 per ET-PLT-007 R5; unregistered and registered contacts are indistinguishable
  - parallel-safe: yes
  - depends: T2

- [ ] **T6 · The no-logging assertion**
  - requirements: R4
  - files: `backend/identity-service/src/test/.../ChallengeLoggingTest.java`
  - verify: a full request-and-verify cycle captures no code at any level
  - parallel-safe: yes
  - depends: T2

- [ ] **T7 · `proof`, `handle`, and `ensure` (calls `AccountEnsurer`)**
  - requirements: R5, R6
  - files: `backend/identity-service/.../auth/`
  - verify: replayed proof is idempotent; handle redeems once; suspended and merging refused
  - parallel-safe: no
  - depends: T2, ET-IDN-004 T3

- [ ] **T8 · `ContactOtpAuthenticator`, factory, `IdentityClient`, templates; never creates users**
  - requirements: R5, R6
  - files: `backend/keycloak-extensions/src/main/`
  - verify: the JAR loads in Keycloak; a missing user fails generically; start fails without credentials
  - parallel-safe: no
  - depends: T1, T5

- [ ] **T9 · Realm as code: `myticketzm` buyer flow, `myticketzm-admin` staff flow, listener in both, staff excluded from the contact flow**
  - requirements: R5, R7
  - files: `../docker-resources/keycloak/` (owned by the coordinator; not edited by this spec's authors)
  - verify: an `ADMIN` cannot complete the contact flow
  - parallel-safe: no — shared realm export with ET-PLT-007
  - depends: T8

- [ ] **T10 · Buyer profile "Sign-in contacts" (list, add, change, remove, set primary, all states)**
  - requirements: R8
  - status: schema-aligned with CONTRACT section 14 and Vitest green; not yet exercised against the running backend ([ET-IDN-004](../004-accounts-and-contacts/) R3, R5)
  - files: `frontend/web/apps/ticketing/src/{components/contacts,lib/contacts,lib/server/contacts.ts,app/profile,app/api/profile/contacts}/`
  - verify: `npx vitest run --config apps/ticketing/vitest.config.ts` (tests below)
  - parallel-safe: yes
  - depends: ET-IDN-004 BE-1

- [ ] **T11 · `/terms` and `/privacy` draft pages**
  - requirements: R9
  - files: `frontend/web/apps/ticketing/src/{app/terms,app/privacy,components/LegalPage.tsx}`
  - verify: `nx build ticketing` lists both routes; legal review replaces the text
  - parallel-safe: yes
  - depends: —

## 6. Out of scope

| Capability | Spec |
|---|---|
| The account, its states, contacts, merge, change, deletion hook and repair | [ET-IDN-004](../004-accounts-and-contacts/) |
| The realm, roles, clients, token lifetimes and the buyer session | [ET-PLT-007](../../_platform/007-security-and-authorization/) |
| Carrying Keycloak changes into MongoDB (adoption and repair) | [ET-IDN-002](../002-keycloak-user-sync/) |
| Logout and revocation | [ET-IDN-003](../003-token-revocation/) |
| WhatsApp and email transports, templates, opt-in, delivery outcomes | [ET-NTF-001](../../notification/001-notification-transport/) |
| Concrete rate-limit numbers and the trusted proxy | [ET-PLT-011](../../_platform/011-rate-limiting-and-abuse/) |
| Erasing a contact on account deletion | [ET-PLT-008](../../_platform/008-data-protection/) |
| Recording sign-in events | [ET-PLT-009](../../_platform/009-audit-trail/) |

Deliberately never in scope: **an application endpoint that issues tokens**, **plaintext code
storage**, **SMS**, and **the authenticator creating users**.
