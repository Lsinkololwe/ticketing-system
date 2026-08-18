# ET-IDN-001 · Phone-OTP passwordless identity — tasks

> **Spec** [`specs/identity/001-phone-otp-identity/spec.md`](../identity/001-phone-otp-identity/spec.md) · **Wave 1** · `blocked_by:` ET-PLT-005, ET-PLT-007
> **Screen** `Login - Phone OTP & Admin MFA.dc.html` — **read it before writing UI**
> **Routes** `apps/ticketing/src/app/auth/page.tsx`, `auth/callback/page.tsx`; `apps/organization-admin/src/app/login/page.tsx`; admin login
> **Verify** `mvn -q -f backend/identity-service test -Dgroups=ET-IDN-001 -DfailIfNoTests=true` · `mvn -q -f backend/keycloak-extensions package`

WhatsApp is primary in-market and carries the OTP that **is** the login mechanism (**D-15**); SMS
is its fallback. The whole customer app depends on this working on a handset with a flaky
connection.

## R0 · Reconcile *(do this first)*

`backend/keycloak-extensions/` already contains `PhoneOtpAuthenticator`,
`PhoneOtpAuthenticatorFactory`, `OtpServiceClient`, FreeMarker templates and SPI registration;
identity-service has `InternalOtpController`. Much of this is `partially-satisfied`.

Check specifically, because these are the requirements existing code most often misses:
- Is the OTP stored **HMAC'd**, or in plaintext in Redis? Plaintext is `contradicted`.
- Is verification **constant-time**? A `String.equals` comparison is a timing oracle.
- Are cooldown (60 s), attempt limit and lockout (15 min) all present, or only cooldown?
- Does any log line, at any level, contain the code?

## A · Backend

### BE-1 · `PhoneNumbers` normalisation in `shared-library`, against `ZM`
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** no *(both the SPI and identity-service depend on it)*
- **Acceptance** the four representations of one number normalise identically
  (`0977…`, `260977…`, `+260977…`, `+260 977 …`); invalid numbers refuse with
  `PHONE_NUMBER_INVALID`.
- It lives in `shared-library` because the Keycloak SPI and identity-service must agree. Two
  normalisers means one number becomes two accounts.

### BE-2 · `OtpService` — HMAC storage, constant-time verify, cooldown, attempts, lockout
- **Spec** R2, R3, R4 · **§5** T2 · **depends** BE-1 · **parallel-safe** no
- **Acceptance** frozen-clock boundary tests at **4:59 / 5:01** (expiry), **59 s / 61 s**
  (cooldown), **14:59 / 15:01** (lockout) — using [`ET-PLT-006`](ET-PLT-006.md) `TestClock`.
- Both sides of each boundary. A test that only checks "expired after 6 minutes" passes on an
  implementation that expires after 30 seconds.

### BE-3 · Delivery — WhatsApp primary, SMS fallback, refuse when both fail
- **Spec** R2 · **§5** T3 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** a WireMock WhatsApp **503** falls back to SMS and reports `deliveredVia: SMS`;
  both failing **refuses** rather than silently succeeding.
- Reporting success when nothing was sent leaves the user staring at a code entry box forever.

### BE-4 · The four internal endpoints, scope-gated, with problem documents
- **Spec** R2, R3, R4 · **§5** T4 · **depends** BE-2 · **parallel-safe** yes
- `request`, `verify`, `status/{phone}`, `DELETE /{phone}` — scope-gated per
  [`ET-PLT-007`](ET-PLT-007.md) R5.
- **Acceptance** 401/403/200 per path; **an unregistered number is indistinguishable from a
  registered one**. Otherwise the endpoint is a free "is this person a user?" oracle.

### BE-5 · The no-logging assertion
- **Spec** R4 · **§5** T5 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** a full request-and-verify cycle captures logs containing **no code, at any
  level** — including DEBUG and TRACE, which is where it always ends up.

### BE-6 · `PhoneOtpAuthenticator`, factory, client, templates, SPI registration
- **Spec** R5 · **§5** T6 · **depends** BE-1, BE-4 · **parallel-safe** no
- Fat JAR shading Gson, Spring-free ([`ET-PLT-012`](ET-PLT-012.md) BE-4).
- **Acceptance** the JAR loads in Keycloak and the authenticator appears in the flow editor.

### BE-7 · Find-or-create with a deterministic username; the concurrent-login test
- **Spec** R6 · **§5** T7 · **depends** BE-6 · **parallel-safe** no
- **Acceptance** two concurrent verifications of one number produce **exactly one** user. Two
  users for one phone number is unrecoverable without a manual merge.

### BE-8 · Bind `phone-otp-browser`; exclude privileged roles; require MFA for them
- **Spec** R5, R7 · **§5** T8 · **depends** BE-6 · **parallel-safe** no *(shared realm export with [`ET-PLT-007`](ET-PLT-007.md) BE-1)*
- **Acceptance** an `ADMIN` **cannot** complete the phone-only flow.
- SIM-swap is a real attack in-market. Phone-only is right for a buyer and wrong for someone who
  can approve a payout.

## B · Contract

`subgraph: null` — this spec's surface is the Keycloak flow and the internal REST API, not
GraphQL. No `GQL-*` task.

## C · Frontend — `Login - Phone OTP & Admin MFA.dc.html`

**Read the screen first** (`DesignSync get_file`). It is the layout contract: field order,
resend affordance, timer placement, error copy position.

### FE-1 · Phone entry step
- **depends** BE-8, F0 · **parallel-safe** no
- E.164 input defaulting to `+260`; channel selection (WhatsApp default, SMS alternative).
- `Input` props are a closed set — `placeholder, value, onChange, type, size, variant, icon,
  error, style`, `variant` ∈ `outline|filled`. Anything else is a break.
- Touch targets ≥ 44px; `type="tel"` for the numeric keypad; label bound with `for`.
- **testids** `phone-input`, `channel-whatsapp`, `channel-sms`, `request-otp-submit`

### FE-2 · OTP verification step
- **depends** FE-1 · **parallel-safe** no
- Six-digit input, numeric keyboard, auto-submit on the sixth digit, visible countdown to expiry,
  resend enabled at 60 s.
- The countdown must derive from the server's expiry, not a client timer started on render — a
  backgrounded mobile browser throttles timers and the user is told they have time they do not.
- **testids** `otp-input`, `otp-countdown`, `otp-resend`, `otp-submit`

### FE-3 · Refusal states, wired to the registry
- **depends** FE-2, [`ET-PLT-005`](ET-PLT-005.md) FE-1 · **parallel-safe** yes
- `OTP_INVALID`, `OTP_EXPIRED`, `OTP_COOLDOWN_ACTIVE`, `OTP_ATTEMPTS_EXHAUSTED`,
  `PHONE_NUMBER_INVALID` — each a distinct, actionable message. Lockout says **when** it lifts.
- Errors render **beside the field**, not as a page banner.
- **testids** `otp-error-<code>`

### FE-4 · Admin MFA path
- **depends** BE-8 · **parallel-safe** yes
- Admin sign-in does **not** offer phone-only. The screen shows the MFA step instead.
- **Acceptance** an `ADMIN` cannot reach the phone-only flow from the UI **or** by URL.

### FE-5 · Three apps, three brands, one flow
- ticketing → iris, Space Grotesk headings; org-admin and admin → teal, Inter.
- Currency and status formatters from **F0-7**. No emoji. Sentence case.
- **Acceptance** compliance suite green on all three.

## D · Tests

### TS-1 · Normalisation *(L1)* — four representations converge; invalid refuses.

### TS-2 · OTP lifecycle *(L1 + L3)*
- Frozen-clock boundaries: 4:59/5:01, 59 s/61 s, 14:59/15:01 — **both sides of each**.
- HMAC at rest: the stored value is not the code. Assert by reading Redis, not by trusting the API.
- Constant-time verification.
- **L3** with a Redis container: attempts exhaust into lockout; lockout lifts on schedule.

### TS-3 · Delivery *(L3, WireMock)* — WhatsApp 503 → SMS, `deliveredVia: SMS`; both fail → refuse.

### TS-4 · Privacy *(L3)*
- No code in logs at any level.
- Registered and unregistered numbers are indistinguishable in body **and** status.

### TS-5 · SPI *(L3, Keycloak Testcontainer)*
- JAR loads; authenticator appears; two concurrent verifications → one user; `ADMIN` cannot
  complete phone-only.

### TS-6 · Frontend *(L5, Playwright)*
- Loading, empty, error, populated for both steps.
- Auto-submit on the sixth digit; resend disabled before 60 s and enabled after.
- Each refusal code renders its own message beside the field.
- Countdown survives a tab backgrounding — the case a client-only timer fails.
- **Use the F0-4 Testcontainers fixture** for Apollo-driven parts; Microcks 500s on fragments.

## E · Gate

- [ ] R0 recorded; HMAC-at-rest, constant-time compare and lockout each classified honestly
- [ ] Four phone representations normalise identically
- [ ] Both sides of all three frozen-clock boundaries asserted
- [ ] OTP HMAC'd at rest, verified in constant time, absent from logs at every level
- [ ] WhatsApp → SMS fallback proven; both-fail refuses
- [ ] Unregistered numbers indistinguishable
- [ ] Two concurrent verifications produce one user
- [ ] `ADMIN` cannot complete the phone-only flow, from UI or URL
- [ ] `Login - Phone OTP & Admin MFA.dc.html` read; layout matches
- [ ] Playwright covers loading/empty/error/populated by `data-testid`
- [ ] Compliance suite green on all three apps
- [ ] `mvn -q -f backend/identity-service test -Dgroups=ET-IDN-001 -DfailIfNoTests=true` green
- [ ] Spec `status:` → `implemented`
