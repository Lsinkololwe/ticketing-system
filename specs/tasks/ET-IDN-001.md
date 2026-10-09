# ET-IDN-001 · Contact-OTP passwordless identity — tasks

> **Spec** [`specs/identity/001-phone-otp-identity/spec.md`](../identity/001-phone-otp-identity/spec.md) · **Wave 1** · `blocked_by:` ET-PLT-005, ET-PLT-007
> **Contract** [`CONTRACT.md`](../identity/004-accounts-and-contacts/CONTRACT.md) — binding wire contract; change it first if an implementation must deviate
> **Screen** `Login - Phone OTP & Admin MFA.dc.html` — **read it before writing UI**; the buyer flow is now a step inside checkout (D-38, D-44)
> **Routes** buyer app `apps/ticketing` server routes `/api/identity/*`, `/api/auth/start`, `/api/auth/callback`; `apps/organization-admin/src/app/login/page.tsx`; admin login
> **Verify** `mvn -q -f backend/identity-service test -Dgroups=ET-IDN-001 -DfailIfNoTests=false` · `mvn -q -f backend/keycloak-extensions package`

Redesigned 2026-10-04 (D-38..D-50, F-044). WhatsApp and email carry the code (**D-39**; SMS is dropped);
the code is typed in checkout and verified by identity-service; Keycloak remains the only token issuer
(**D-44**); the plugin never creates users (**D-43**).

## R0 · Reconcile *(do this first)*

Measured 2026-10-04 by code review of `keycloak-extensions` and `identity-service`. Evidence is the
review, not an executing test: **no test exists** for `OtpService`, `InternalOtpController` or
`MessagingService`, so no row below can be `already-satisfied`.

| Req | Class | Evidence (code review facts) | Action |
|---|---|---|---|
| R1 contact normalisation | `partially-satisfied` | phone only, `+260` default applied to anything; no email; no country allowlist; no mobile-type check | rewrite as `ContactNormalizer` (BE-1) |
| R2 generate and deliver | `contradicted` | code stored in plain text at `otp:phone:<e164>`; Twilio body unencoded; no provider timeouts; the WhatsApp-to-SMS fallback never fires; no email provider; the channel is trusted from the form | delete and rebuild (BE-2, BE-4) |
| R3 throttle, attempts, lock | `contradicted` | 3 tries instead of 5; no lock; check-then-act races; resend link in the page is dead; no per-IP/device/country limits | rebuild as one Lua script (BE-2, BE-3) |
| R4 leaks nothing | `contradicted` | `String.equals` comparison; phone number in the URL; controller returns bare enums and 500s instead of problem documents; no logging test | rebuild; add no-oracle and no-log tests (BE-2, BE-5, BE-6, TS-4) |
| R5 Keycloak only issuer | `contradicted` | `OtpServiceClient` sends unauthenticated when credentials are missing; `PhoneOtpMutationResolver` returns a service-account token as the buyer token (a second token path); `KeycloakAuthService` password paths | delete both paths; authenticator fails closed (BE-7, BE-8) |
| R6 one contact, one account | `contradicted` | `PhoneOtpAuthenticator` creates users (`addUser`, username `user_<last8>`) and grants `CUSTOMER`; `KeycloakService` finds users by email and adopts on 409; `users-schema` requires email and names; duplicate email indexes (partial plus a plain unique at `IdentityIndexInitializer` L283) | creation moves to ET-IDN-004; username = account id (BE-8) |
| R7 privileged not contact-only | `absent` | `AccountTypeRoleMapper` lets registrants choose `ORGANIZER`; the admin realm has no `user-sync` listener; realm user profile requires email and names; docs still name a `SCANNER` role | realm as code, two realms (BE-9) |

Also re-check, because they are the usual misses: any log line (any level) containing a code or an
Authorization value; every place that still reads `otp:`-prefixed keys; every caller of the deleted
`/api/internal/otp/*` and of the GraphQL operations `requestPhoneOtp, verifyPhoneOtp, login,
register, refreshToken, validateToken` (deleted, no shim).

## A · Backend

### BE-1 · `ContactNormalizer` and `contactKey` in `shared-library`
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** no *(the plugin and identity-service both depend on it)*
- **Acceptance** five phone representations converge; `regionHint`; the `ZM` assumption only for 10 digits
  starting `0`; non-mobile and non-allowlisted countries refuse with `CONTACT_INVALID`; emails
  trimmed, lower-cased, NFC, max 254; `contactKey` is `HMAC-SHA256(hash-key, TYPE:normalized)` hex.
- Two normalisers means one contact becomes two accounts.

### BE-2 · `ChallengeService` — HMAC storage, one Lua verify, cooldown, attempts, lock
- **Spec** R2, R3, R4 · **§5** T2 · **depends** BE-1 · **parallel-safe** no
- **Acceptance** frozen-clock boundary tests at **4:59 / 5:01**, **59 s / 61 s**, **14:59 / 15:01** using
  [`ET-PLT-006`](ET-PLT-006.md) `TestClock`; 50 parallel wrong guesses consume exactly 5 attempts; the
  lock is mirrored to `identity_account_events` and survives a Redis flush; the stored value is not the code (read Redis).

### BE-3 · Limits per contact, IP, device, country, day
- **Spec** R3 · **§5** T3 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** each scope refuses at its configured threshold with `OTP_RATE_LIMITED` and `retryAfterSeconds`;
  the client IP comes from the trusted-proxy chain ([`ET-PLT-011`](ET-PLT-011.md)) and a spoofed header does not move the counter.

### BE-4 · Delivery — WhatsApp template and email; no SMS
- **Spec** R2 · **§5** T4 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** WireMock 503 and a timeout each yield `OTP_DELIVERY_FAILED`, delete the challenge and leave no live code;
  a disabled channel yields `NOTIFICATION_CHANNEL_UNAVAILABLE`; the response names the channel used;
  the code is encoded in the provider body; the capture bean exists only under `local`/`test`.

### BE-5 · The five internal endpoints, scope-gated, with problem documents
- **Spec** R2, R3, R4, R6 · **§5** T5 · **depends** BE-2 · **parallel-safe** yes
- `challenges`, `challenges/verify`, `accounts/ensure`, `handles/redeem`, `accounts/{id}/status` ([`CONTRACT.md`](../identity/004-accounts-and-contacts/CONTRACT.md) §4);
  scope-gated per [`ET-PLT-007`](ET-PLT-007.md) R5; no contact value in any URL.
- **Acceptance** 401/403/200 per path; a registered and an unregistered contact (phone and email) are indistinguishable in body, status and latency band; the old `/api/internal/otp/*` routes return 404.

### BE-6 · The no-logging assertion
- **Spec** R4 · **§5** T6 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** a full challenge-and-verify cycle captures logs containing **no code, at any level**, including provider request dumps.

### BE-7 · `proof`, `handle` and `ensure`
- **Spec** R5, R6 · **§5** T7 · **depends** BE-2, [`ET-IDN-004`](ET-IDN-004.md) BE-3 · **parallel-safe** no
- **Acceptance** a replayed proof returns the same answer and starts no second workflow; a handle redeems once (GETDEL); `ACCOUNT_SUSPENDED` and `ACCOUNT_MERGING` refuse; the service-account-token-as-buyer-token path and `KeycloakAuthService` password paths are deleted.

### BE-8 · `ContactOtpAuthenticator`, factory, `IdentityClient`, templates
- **Spec** R5, R6 · **§5** T8 · **depends** BE-1, BE-5 · **parallel-safe** no
- Fat JAR shading Gson, Spring-free ([`ET-PLT-012`](ET-PLT-012.md) BE-4). Modes HANDOFF and SCREEN.
- **Acceptance** the JAR loads and the authenticator appears in the flow editor; startup fails when `IDENTITY_*` or `KEYCLOAK_TOKEN_URL` is blank; a missing or disabled user fails with a generic error and **no user is created**; the channel is never read from the form.

### BE-9 · Realm as code — two realms, flows, listener in both, staff excluded
- **Spec** R5, R7 · **§5** T9 · **depends** BE-8 · **parallel-safe** no *(shared realm export with [`ET-PLT-007`](ET-PLT-007.md) BE-1; the file lives in the sibling `docker-resources` repository)*
- **Acceptance** `myticketzm` binds `contact-browser`, `myticketzm-admin` binds password plus second factor; `user-sync` is enabled in both; the user profile makes `email`, `firstName`, `lastName` optional; no registration page lets a user pick an account type (`AccountTypeRoleMapper` removed); an `ADMIN` cannot complete the contact flow.

## B · Contract

`subgraph: null` — the surface is the Keycloak flow and the internal REST API. No `GQL-*` task. The
wire contract is [`CONTRACT.md`](../identity/004-accounts-and-contacts/CONTRACT.md); change it first
if an implementation must deviate.

## C · Frontend — buyer app, checkout step

**Read the screen first** (`DesignSync get_file`) for layout; the step now lives inside checkout.

### FE-1 · Contact entry
- **depends** BE-5, F0 · **parallel-safe** no
- One field accepting a phone number or an email; the channel is shown, never chosen by a hidden field; country picker or `+` prefix; `type` follows the value.
- Touch targets >= 44px; label bound with `for`; `Input` props are a closed set.
- **testids** `contact-input`, `request-code-submit`

### FE-2 · Code entry inside checkout
- **depends** FE-1 · **parallel-safe** no
- Six digits, numeric keyboard, auto-submit on the sixth, countdown derived from the server's `expiresInSeconds`, resend enabled at `resendAfterSeconds`, a visible "use email instead" when WhatsApp delivery fails.
- Server routes only (`/api/identity/challenge|verify|ensure`); the browser never holds a token.
- **testids** `otp-input`, `otp-countdown`, `otp-resend`, `otp-submit`

### FE-3 · Refusal states
- **depends** FE-2, [`ET-PLT-005`](ET-PLT-005.md) FE-1 · **parallel-safe** yes
- `OTP_INVALID` (with attempts left), `OTP_EXPIRED`, `OTP_COOLDOWN_ACTIVE`, `OTP_LOCKED` (when it lifts), `OTP_RATE_LIMITED`, `OTP_DELIVERY_FAILED`, `CONTACT_INVALID`, `ACCOUNT_SUSPENDED` — each its own message beside the field.
- **testids** `otp-error-<code>`

### FE-4 · Session handover
- **depends** BE-7, BE-8 · **parallel-safe** no
- `ensure` -> `GET /api/auth/start` (PKCE, `state`, `login_hint=<handle>`) -> `GET /api/auth/callback` requires `status == ACTIVE` before setting the HttpOnly cookie; a 202 PROVISIONING answer polls with `retryAfterSeconds`.
- **Acceptance** no token, handle or code reaches `localStorage`, the page's JavaScript, analytics or logs.

### FE-5 · Staff sign-in
- **depends** BE-9 · **parallel-safe** yes
- Admin and organizer-admin sign-in show password plus second factor; the contact flow is not offered. An `ADMIN` cannot reach it from the UI or by URL.

### FE-6 · Three apps, three brands
- ticketing -> iris, Space Grotesk; org-admin and admin -> teal, Inter. No emoji. Sentence case. Compliance suite green on all three.

### FE-7 · Profile "Sign-in contacts" — **status: built and schema-aligned with CONTRACT section 14, Vitest green; not yet run against the live backend**
- **Spec** R8 · cross-link [`ET-IDN-004`](ET-IDN-004.md) R3, R5 · **depends** ET-IDN-004 BE-1 · **parallel-safe** yes
- `/profile`, `SignInContacts`, `ContactFlowDialog`, reducer `flow.ts`, BFF `/api/profile/contacts[/{action}]`, typed server client `lib/server/contacts.ts`.
- **Tests (Vitest, `apps/ticketing/src/__tests__/`)** `contacts-routes.test.ts` (resend passes challengeId or changeId+target, masked-only, OTP_RATE_LIMITED, list masked/whitelisted, 401, CSRF origin and header, add-request leaks no raw value, input validation, change-request two challenges, `OTP_INVALID`/`OTP_LOCKED`/`OTP_EXPIRED`/`CONTACT_ALREADY_CLAIMED`/`LAST_VERIFIED_CONTACT` mapping, claimed-by-other indistinguishable from a lost race, last-verified removal refused, 202 pending with retry-after, 503 and 401) · `contact-flow.test.ts` (phases, wrong code with attempts, lock seconds, expiry, lost claim, blocked removal, pending, network) · `SignInContacts.test.tsx` (list rendering, other-kind add, session redirect, load error, set primary, add success, wrong code alert, locked countdown, expired, neutral claimed wording, 202 poll, network failure, local validation, two-code change, last-verified block, server refusal, removal, no raw value in storage/URL; describe `send code again` with fake timers: countdown then enabled and announced, resend resets countdown and clears the field, no double submit, OTP_RATE_LIMITED re-arm, OTP_LOCKED, OTP_EXPIRED, CONTACT_UNKNOWN, network error, expired code offers resend and start over, separate resend per code in a change). `resendContactCode` matches the landed schema (`challengeId` or `changeId` + `target` NEW|CURRENT).
- **testids** `sign-in-contacts`, `contacts-list`, `contact-<id>`, `contact-add-email`, `contact-add-whatsapp`, `contact-flow-code`, `contact-flow-primary-code`, `contact-flow-error`, `contact-flow-blocked`, `contact-flow-pending`

### FE-8 · `/terms` and `/privacy` — **status: built (draft text); legal review open**
- **Spec** R9 · **parallel-safe** yes · the pages carry a visible draft banner; `identify-terms` links to both.

### FE-9 · Buyer-app server design recorded — **status: done** (spec §4 "The buyer app server"; custom opaque session, CSRF guard, CSP nonce, `accountId` claim handling).

## D · Tests

### TS-1 · Normalisation *(L1)* — representations converge; email; allowlist; refusals.

### TS-2 · Challenge lifecycle *(L1 + L3, Redis container)*
- Frozen-clock boundaries, both sides of each. HMAC at rest read from Redis. Constant-time verify.
- 50 concurrent guesses consume exactly 5 attempts; attempts exhaust into a lock that lifts on schedule and survives a Redis flush.

### TS-3 · Delivery *(L3, WireMock + capture bean)* — 503, timeout, disabled channel; encoding of the code in the provider body.

### TS-4 · Privacy *(L3)* — no code in logs at any level; registered vs unregistered indistinguishable for phone and email.

### TS-5 · SPI *(L3, Keycloak Testcontainer)* — JAR loads; HANDOFF with a valid, a replayed and an expired handle; missing user does not create one; `ADMIN` refused; startup refuses without credentials.

### TS-6 · Frontend *(L5, Playwright)* — loading, empty, error, populated; auto-submit; resend gated; each refusal code beside the field; countdown survives tab backgrounding. Use the F0-4 Testcontainers fixture.

## E · Gate

- [ ] R0 recorded; every requirement classified against the code review facts, none claimed `already-satisfied` without an executing test
- [ ] Phone (five forms) and email normalise to one `contactKey`; allowlist and mobile-type enforced
- [ ] Both sides of all three frozen-clock boundaries asserted
- [ ] Code HMAC'd at rest, verified by one Lua script in constant time, absent from logs at every level
- [ ] 50 concurrent guesses consume exactly 5 attempts; the lock survives a Redis flush
- [ ] Limits per contact, IP, device and country refuse with `OTP_RATE_LIMITED`; IP from the trusted chain only
- [ ] WhatsApp and email delivery proven; failure and timeout refuse with `OTP_DELIVERY_FAILED` and leave no live code; no SMS code path remains
- [ ] Registered and unregistered contacts indistinguishable
- [ ] The authenticator never creates a user; a replayed handle fails; the plugin refuses to start without credentials
- [ ] No service-account token is returned as a buyer token; the old `/api/internal/otp/*` and GraphQL authentication operations are gone
- [ ] `ADMIN` cannot complete the contact flow, from UI or URL; `user-sync` listener enabled in both realms
- [ ] Profile "Sign-in contacts" (R8) and the draft legal pages (R9) pass their Vitest suites and work against the live GraphQL operations of ET-IDN-004 (currently schema-aligned only, not run against the backend)
- [ ] Playwright covers loading/empty/error/populated by `data-testid`; compliance suite green on all three apps
- [ ] `mvn -q -f backend/identity-service test -Dgroups=ET-IDN-001 -DfailIfNoTests=false` green
- [ ] Spec `status:` -> `implemented`
