# ET-IDN-001 · Phone-OTP passwordless identity

> **Conformance** · US Part I §6 Keycloak integration

## 1. Capability

In this market a phone number is the identity people actually have. Email addresses are
secondary, often shared, and frequently invented at signup; a phone number is on the
handset the person is holding, it is how they receive money, and it is what they will type
without being asked twice. The platform therefore authenticates customers by phone number
and a one-time code, with no password anywhere in the flow — nothing to forget, nothing to
reuse from another breached site, and nothing to phish that is worth more than five
minutes.

This spec builds that login. It delivers a Keycloak authenticator — a server-side SPI, so
the credential never leaves the identity provider's own flow — that collects a phone
number, asks identity-service to generate and deliver a code, verifies what the user types,
and then finds or creates the Keycloak user that number belongs to. It declares the code's
lifetime, its delivery channels with WhatsApp first and SMS behind it, the cooldown that
stops the resend button becoming a free SMS gun, and the attempt limit that stops a
six-digit code being brute-forced in an afternoon.

It also declares the things an OTP system gets wrong in ways that are invisible until they
are exploited: that the code is stored hashed rather than in plaintext, that verification
is a constant-time comparison, that requesting a code for an unregistered number is
indistinguishable from requesting one for a registered number, and that the code appears in
no log at any level.

Administrators and finance staff do not use this flow. They authenticate with a password
and a second factor, because their accounts are the ones worth attacking and a
handset-based factor alone is not proportionate to what they can do.

## 2. Design decisions

**The authenticator is a Keycloak SPI, not an application endpoint that mints tokens.**
Login belongs inside the identity provider's own flow: Keycloak issues the tokens, applies
its brute-force detection, records its own login events, and remains the single issuer.
An application endpoint that verified an OTP and then asked Keycloak for a token on the
user's behalf would be a second, weaker authentication path around the first.

**Keycloak calls identity-service; identity-service owns the OTP.** The SPI holds no state
and touches no database. It calls four internal endpoints over `client_credentials`, and
the code's generation, storage, delivery and verification are identity-service's, where
Redis, the messaging providers and the platform's own rate limiting already live.

**The code is stored hashed, and compared in constant time.** A six-digit code is
low-entropy by construction, and the two things that make it safe are a short life and a
hard attempt cap — but neither helps if a Redis dump hands an attacker every live code in
the system. Redis holds `HMAC-SHA256(code, pepper)` with the pepper from the environment,
and verification compares digests with `MessageDigest.isEqual`. A `String.equals` on a
secret leaks its prefix through timing, and a six-digit secret does not have much prefix to
spare.

**A request for an unknown number looks exactly like a request for a known one.** Same
response, same latency band, same message. Otherwise the request endpoint is a free
membership oracle for anyone with a list of Zambian mobile numbers, and the platform's user
base becomes a marketing list.

**Five minutes, sixty seconds, five attempts, fifteen minutes.** The code lives 5 minutes;
a resend is refused for 60 seconds; five wrong entries exhaust the code and lock the number
for 15 minutes. Five attempts against a six-digit space is a one-in-two-hundred-thousand
chance per lockout window, which is the right side of the line between security and a user
who mistyped once being locked out of a concert.

**Requesting a new code invalidates the previous one.** Two live codes doubles the
guessable surface and produces the classic support case where the first SMS arrives second
and the user types a code that "should" work. One number, one live code.

**WhatsApp first, SMS behind it, and the caller is told which was used.** WhatsApp is
cheaper, richer and near-universal in-market; SMS reaches a handset with no data. The user
may choose, and if the chosen channel fails the platform falls back and says so. If every
channel fails the request is **refused** with a retryable code — telling a user a code is
on its way when it is not produces a support call and a lost sale.

**The code appears in no log, at no level, in no environment.** Not at `DEBUG`, not in a
provider request dump, not in an exception message. A development convenience that prints
it is a production leak one profile flag away; the development affordance is a fixed test
number with a fixed code, declared in configuration, never a log line.

**Phone numbers are normalised to E.164 once, at the edge, against `ZM`.** `0977123456`,
`+260977123456` and `260977123456` are one number and must not become three accounts. The
normalised form is what is stored, what is hashed into the Redis key, and what the Keycloak
attribute holds.

**Administrators and finance authenticate with a password and a second factor.** They are
not in this flow. A phone-only factor for an account that can approve payouts is a SIM swap
away from the platform's money, and SIM swap is a routine attack in-market.

**Rejected alternatives**

- *An application login endpoint that verifies the OTP and requests a token.* A second authentication path around Keycloak's, with none of its protections.
- *Storing the code in plaintext because it expires in five minutes.* A Redis dump then yields every live code in the platform; expiry does not help an attacker who is already inside.
- *Distinguishing "no account for this number" at request time.* A membership oracle.
- *A longer code instead of an attempt cap.* Eight digits a user must read off a screen and type on a handset, to avoid a counter.
- *Allowing multiple live codes so a slow SMS still works.* Doubles the guessable surface to solve a problem the 60-second cooldown already solves.
- *Email OTP as a third channel.* A third provider contract for a channel this market does not check.
- *SMS as primary.* Costs more per message, delivers less reliably in-market, and carries no delivery signal worth acting on.

## 3. Requirements

### ET-IDN-001-R1 · A phone number is one identity, normalised once

WHEN a phone number is submitted in any form, THE SYSTEM SHALL normalise it to E.164
against the `ZM` region and treat every representation of one number as one identity.

**Acceptance**
- [ ] `0977123456`, `260977123456`, `+260 977 123 456` and `+260977123456` all normalise to `+260977123456`
- [ ] A number that is not a valid mobile number for its region is refused with `PHONE_NUMBER_INVALID`, before any code is generated or any message sent
- [ ] The normalised form is what is stored in the Keycloak `phone_number` attribute, in `identity_users.phoneNumber`, and in every Redis key
- [ ] Normalisation exists in exactly one implementation, in `shared-library`, and both the SPI and identity-service reach it
- [ ] `identity_users.phoneNumber` carries a unique sparse index, so two normalisations of one number cannot become two accounts

### ET-IDN-001-R2 · A code is generated, delivered, and lives five minutes

WHEN a code is requested for a valid number, THE SYSTEM SHALL generate a six-digit code,
deliver it on the requested channel, and accept it for five minutes.

**Acceptance**
- [ ] The code is six digits from a `SecureRandom`, uniformly distributed over `000000`–`999999` with no rejection of leading zeros
- [ ] `otp:{phone}` holds `HMAC-SHA256(code, pepper)` with `EX 300`, never the code itself
- [ ] The pepper is environment-sourced and appears in no committed file
- [ ] Requesting a new code overwrites the previous digest, so exactly one code is live per number
- [ ] Delivery is attempted on the requested channel, falling back to the other, and the response names the channel actually used
- [ ] IF every channel fails, THEN the request is refused with `NOTIFICATION_CHANNEL_UNAVAILABLE`, marked retryable, and no digest is left behind for a code nobody received
- [ ] A frozen-clock test accepts the code at 4:59 and refuses it with `OTP_EXPIRED` at 5:01

### ET-IDN-001-R3 · Resends are throttled and attempts are capped

WHILE a code is live, THE SYSTEM SHALL refuse a resend within the cooldown, and IF the
verification attempt limit is reached, THEN THE SYSTEM SHALL invalidate the code and lock
the number.

**Acceptance**
- [ ] A second request within 60 seconds is refused with `OTP_COOLDOWN_ACTIVE` carrying `retryAfterSeconds`, and sends no message
- [ ] `otp:cooldown:{phone}` holds the throttle with `EX 60`
- [ ] A wrong code is refused with `OTP_INVALID` carrying `attemptsRemaining`, and decrements a counter held in `otp:attempts:{phone}` with the same expiry as the code
- [ ] The fifth wrong attempt deletes the digest and sets `otp:locked:{phone}` with `EX 900`; further requests and verifications are refused with `OTP_ATTEMPTS_EXHAUSTED` carrying `lockedUntil`
- [ ] A successful verification clears the digest, the attempt counter and the cooldown
- [ ] Frozen-clock tests assert the cooldown boundary at 59 s and 61 s, and the lock boundary at 14:59 and 15:01

### ET-IDN-001-R4 · Verification leaks nothing

WHEN a code is verified, THE SYSTEM SHALL compare in constant time and SHALL disclose
nothing about the number's registration state or the code's value.

**Acceptance**
- [ ] Comparison is `MessageDigest.isEqual` over the digests; no `String.equals` or `==` on a code or digest appears anywhere
- [ ] A request for an unregistered number returns the same response shape and status as one for a registered number
- [ ] No log statement at any level, in any profile, contains the code — asserted by a test that captures log output across a full request-and-verify cycle
- [ ] No provider request or response body containing the code is logged
- [ ] The code appears in no exception message and no GraphQL or REST error payload
- [ ] A configured list of test numbers may use fixed codes in non-production profiles; the list is empty in production and a startup check enforces that

### ET-IDN-001-R5 · Keycloak owns the login flow and issues the tokens

THE SYSTEM SHALL authenticate the user inside a Keycloak authentication flow, and no other
component SHALL issue or request tokens on a user's behalf.

**Acceptance**
- [ ] `PhoneOtpAuthenticator` and its factory are registered through `META-INF/services/org.keycloak.authentication.AuthenticatorFactory`
- [ ] The `phone-otp-browser` flow is bound as the realm's browser flow, with the username-password form and the phone-OTP form as alternatives
- [ ] The SPI holds no database connection and no MongoDB dependency; it reaches identity-service only over the four internal endpoints of §4
- [ ] The SPI authenticates to identity-service with `client_credentials` as `internal-service`; no static shared secret header is used
- [ ] Tokens are issued by Keycloak's own flow completion — no application code calls the token endpoint on a user's behalf
- [ ] The SPI JAR shades Gson and declares every Keycloak artifact `provided`

### ET-IDN-001-R6 · A verified number resolves to exactly one user

WHEN a code verifies, THE SYSTEM SHALL bind the session to the single Keycloak user holding
that number, creating one if none exists.

**Acceptance**
- [ ] A number with an existing Keycloak user resolves to that user; no second user is created
- [ ] A number with no user creates one with `phone_number` set to the normalised form, `phone_verified` true, `enabled` true, and the realm default `CUSTOMER` role
- [ ] The generated username is deterministic from the number, so a retry of a partially-failed creation converges rather than forking
- [ ] Two concurrent verifications of one number produce exactly one Keycloak user
- [ ] `phone_verified` is set to true only by this flow, never by a profile update
- [ ] Creation emits Keycloak's `REGISTER` event, which is what carries the user into MongoDB ([ET-IDN-002](../002-keycloak-user-sync/))

### ET-IDN-001-R7 · Privileged accounts are not phone-only

THE SYSTEM SHALL require a password and a second factor for accounts holding `ADMIN`,
`SUPER_ADMIN` or `FINANCE`.

**Acceptance**
- [ ] The phone-OTP execution is not reachable for a user holding any of those three realm roles — such a user completing it is denied and the denial is recorded
- [ ] Those roles require OTP-based MFA configured in Keycloak, in addition to a password
- [ ] Granting one of those roles to an account with no password configured requires the credential to be set before the role takes effect
- [ ] A test asserts an `ADMIN` cannot complete the phone-only flow

## 4. Model

### Redis keys

All four keyed on the **normalised** number. Every one is ephemeral by design; none is an
authority for anything.

| Key | Type | TTL | Holds |
|---|---|---|---|
| `otp:{phone}` | STRING | 300 s | `HMAC-SHA256(code, pepper)`, hex |
| `otp:cooldown:{phone}` | STRING | 60 s | resend throttle marker |
| `otp:attempts:{phone}` | STRING | 300 s | remaining verification attempts, initialised to 5 |
| `otp:locked:{phone}` | STRING | 900 s | lockout marker after exhaustion |

### Configuration

| Property | Value |
|---|---|
| `identity.otp.length` | 6 |
| `identity.otp.ttl` | `PT5M` |
| `identity.otp.cooldown` | `PT60S` |
| `identity.otp.max-attempts` | 5 |
| `identity.otp.lockout` | `PT15M` |
| `identity.otp.pepper` | `${OTP_PEPPER}` — environment only |
| `identity.otp.default-channel` | `WHATSAPP` |
| `identity.otp.test-numbers` | empty in production; a startup check enforces it |
| `identity.phone.default-region` | `ZM` |

### The internal REST contract

Every path requires an internal scope ([ET-PLT-007 §4](../../_platform/007-security-and-authorization/)).
Every response body is JSON; every refusal is an RFC 9457 problem document carrying the
registry `errorCode`.

| Method | Path | Request | Response | Refusals |
|---|---|---|---|---|
| `POST` | `/api/internal/otp/request` | `{ phoneNumber, channel }` | `{ deliveredVia, expiresAt }` | `PHONE_NUMBER_INVALID`, `OTP_COOLDOWN_ACTIVE`, `OTP_ATTEMPTS_EXHAUSTED`, `NOTIFICATION_CHANNEL_UNAVAILABLE` |
| `POST` | `/api/internal/otp/verify` | `{ phoneNumber, code }` | `{ valid: true }` | `OTP_INVALID`, `OTP_EXPIRED`, `OTP_ATTEMPTS_EXHAUSTED`, `PHONE_NUMBER_INVALID` |
| `GET` | `/api/internal/otp/status/{phone}` | — | `{ cooldownRemainingSeconds, lockedUntil, attemptsRemaining }` | `PHONE_NUMBER_INVALID` |
| `DELETE` | `/api/internal/otp/{phone}` | — | `204` | `PHONE_NUMBER_INVALID` |

`channel` is `WHATSAPP` or `SMS`. `deliveredVia` reports what actually carried it, which may
differ from what was asked. **The response never reveals whether the number has an
account** (R4).

### The flow

```
handset            Keycloak (phone-otp-browser)        identity-service        provider
   │  1 login           │                                     │                    │
   │───────────────────▶│                                     │                    │
   │  2 phone form      │                                     │                    │
   │◀───────────────────│                                     │                    │
   │  3 +260977123456   │                                     │                    │
   │───────────────────▶│  4 POST /otp/request                │                    │
   │                    │────────────────────────────────────▶│                    │
   │                    │                    5 normalise, cooldown+lock check       │
   │                    │                    6 SecureRandom → HMAC → SET otp: EX300 │
   │                    │                                     │  7 send            │
   │                    │                                     │───────────────────▶│
   │                    │  8 { deliveredVia, expiresAt }      │                    │
   │                    │◀────────────────────────────────────│                    │
   │  9 code form       │                                     │                    │
   │◀───────────────────│                                     │                    │
   │ 10 483920          │                                     │                    │
   │───────────────────▶│ 11 POST /otp/verify                 │                    │
   │                    │────────────────────────────────────▶│                    │
   │                    │                   12 HMAC, constant-time compare          │
   │                    │                   13 clear digest, attempts, cooldown     │
   │                    │ 14 { valid: true }                  │                    │
   │                    │◀────────────────────────────────────│                    │
   │                    │ 15 find-or-create user, grant CUSTOMER                    │
   │                    │ 16 context.success() → Keycloak issues tokens             │
   │ 17 tokens          │                                     │                    │
   │◀───────────────────│                                     │                    │
```

Step 15 emits Keycloak's `REGISTER` event on a first login, which is what carries the user
into MongoDB — [ET-IDN-002](../002-keycloak-user-sync/) owns that half.

### The SPI

`backend/keycloak-extensions/`, built as a shaded JAR into `/opt/keycloak/providers/`.

| Component | Role |
|---|---|
| `PhoneOtpAuthenticator` | the two-step challenge: phone form, then code form |
| `PhoneOtpAuthenticatorFactory` | SPI registration; declares configuration properties |
| `OtpServiceClient` | the four internal calls, with `client_credentials` and a bounded timeout |
| `PhoneNumbers` | normalisation, delegating to the `shared-library` rule |
| `phone-otp-input.ftl` | number entry, channel selection |
| `phone-otp-verify.ftl` | six-digit entry, countdown, resend enabled at 60 s |
| `messages_en.properties` | every user-visible string |

Keycloak artifacts are `provided`; Gson is shaded. The factory declares
`OTP_SERVICE_URL`, `OTP_CLIENT_ID`, `OTP_CLIENT_SECRET` and `KEYCLOAK_TOKEN_URL` as
configuration, all environment-sourced.

### Keycloak flow

```
phone-otp-browser                        [browser flow binding]
├── Cookie                               ALTERNATIVE
├── Identity Provider Redirector         ALTERNATIVE
└── phone-otp-forms                      ALTERNATIVE
    ├── Username Password Form           ALTERNATIVE   ← admin, finance (R7)
    └── Phone OTP Authentication         ALTERNATIVE   ← customers, organizers
```

Bound by the realm export of [ET-PLT-007](../../_platform/007-security-and-authorization/)
T1, not by a hand-run script.

### GraphQL

None. This capability has no graph surface: login happens in Keycloak's flow, and the only
programmatic surface is the internal REST contract above. `me` and profile reads belong to
[ET-IDN-002](../002-keycloak-user-sync/).

### Error codes

`PHONE_NUMBER_INVALID`, `OTP_INVALID`, `OTP_EXPIRED`, `OTP_COOLDOWN_ACTIVE`,
`OTP_ATTEMPTS_EXHAUSTED` — rows of [ET-PLT-005 §4](../../_platform/005-error-contract/)
introduced by this spec — plus `NOTIFICATION_CHANNEL_UNAVAILABLE`, introduced by
[ET-NTF-001](../../notification/001-notification-transport/).

## 5. Tasks

- [ ] **T1 · `PhoneNumbers` normalisation in `shared-library`, against `ZM`**
  - requirements: R1
  - files: `backend/shared-library/src/main/java/com/pml/shared/phone/PhoneNumbers.java`
  - verify: the four representations of one number normalise identically; invalid numbers refuse
  - parallel-safe: no — both the SPI and identity-service depend on it
  - depends: —

- [ ] **T2 · `OtpService`: HMAC storage, constant-time verify, cooldown, attempts, lockout**
  - requirements: R2, R3, R4
  - files: `backend/identity-service/src/main/java/com/pml/identity/service/impl/OtpServiceImpl.java`
  - verify: frozen-clock boundary tests at 4:59/5:01, 59 s/61 s, 14:59/15:01
  - parallel-safe: no
  - depends: T1

- [ ] **T3 · Delivery: WhatsApp primary, SMS fallback, refuse when both fail**
  - requirements: R2
  - files: `backend/identity-service/.../infrastructure/messaging/`
  - verify: a WireMock WhatsApp 503 falls back to SMS and reports `deliveredVia: SMS`; both failing refuses
  - parallel-safe: yes
  - depends: T2

- [ ] **T4 · The four internal endpoints, scope-gated, with problem documents**
  - requirements: R2, R3, R4
  - files: `backend/identity-service/.../web/rest/InternalOtpController.java`
  - verify: 401/403/200 per ET-PLT-007 R5; an unregistered number is indistinguishable
  - parallel-safe: yes
  - depends: T2

- [ ] **T5 · The no-logging assertion**
  - requirements: R4
  - files: `backend/identity-service/src/test/.../OtpLoggingTest.java`
  - verify: a full request-and-verify cycle captures logs containing no code, at any level
  - parallel-safe: yes
  - depends: T2

- [ ] **T6 · `PhoneOtpAuthenticator`, factory, `OtpServiceClient`, templates, SPI registration**
  - requirements: R5
  - files: `backend/keycloak-extensions/src/main/`
  - verify: the JAR loads in Keycloak and the authenticator appears in the flow editor
  - parallel-safe: no
  - depends: T1, T4

- [ ] **T7 · Find-or-create with a deterministic username; the concurrent-login test**
  - requirements: R6
  - files: `backend/keycloak-extensions/.../PhoneOtpAuthenticator.java`
  - verify: two concurrent verifications of one number produce exactly one user
  - parallel-safe: no
  - depends: T6

- [ ] **T8 · Bind `phone-otp-browser`; exclude privileged roles; require MFA for them**
  - requirements: R5, R7
  - files: `../docker-resources/keycloak/event-ticketing-realm.json`
  - verify: an `ADMIN` cannot complete the phone-only flow
  - parallel-safe: no — one realm export, shared with ET-PLT-007 T1
  - depends: T6

## 6. Out of scope

| Capability | Spec |
|---|---|
| The realm, roles, clients and scopes this flow runs inside | [ET-PLT-007](../../_platform/007-security-and-authorization/) |
| Carrying the Keycloak user into MongoDB, and the profile | [ET-IDN-002](../002-keycloak-user-sync/) |
| The WhatsApp and SMS transports themselves, templates, delivery outcomes | [ET-NTF-001](../../notification/001-notification-transport/) |
| Rate limiting the request endpoint by IP and by number, beyond the per-number cooldown | [ET-PLT-011](../../_platform/011-rate-limiting-and-abuse/) |
| Erasing a phone number on account deletion | [ET-PLT-008](../../_platform/008-data-protection/) |
| Recording login events for audit | [ET-PLT-009](../../_platform/009-audit-trail/) |
| Organization membership, which a login does not confer | [ET-ORG-002](../../organization/002-teams-and-invitations/) |

Deliberately never in scope: **an application endpoint that verifies an OTP and issues
tokens** (a second authentication path around Keycloak's), **plaintext OTP storage** (a
Redis dump then yields every live code), and **email as a third OTP channel** (a third
provider contract for a channel this market does not check).
