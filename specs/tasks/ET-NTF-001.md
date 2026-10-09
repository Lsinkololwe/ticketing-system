# ET-NTF-001 · Notification transport — channels, templates, devices, delivery — tasks

> **Spec** [`specs/notification/001-notification-transport/spec.md`](../notification/001-notification-transport/spec.md) · **Wave 5** · `blocked_by:` ET-PLT-002, 003, 005, ET-IDN-002
> **Screens** — **preferences only.** The Coverage map: *"notification transport and lifecycle triggers are delivery mechanics (WhatsApp/SMS/email); no dedicated admin screen was requested for template management."* Surfaces in `Ticketing - Profile & Registration` and `Org Admin - Settings`.
> **Verify** `mvn -q -f backend/identity-service test -Dgroups=ET-NTF-001 -DfailIfNoTests=false`

**D-15**: WhatsApp, SMS, push and email. WhatsApp is primary in-market and carries the OTP that
**is** the login mechanism; SMS is its fallback; email is for receipts and the invitation flow,
which needs a durable addressable identity.

The property that decides the architecture: **notification is never on a business transaction's
critical path.** A purchase must complete with every provider down.

## R0 · Reconcile

`MessagingService` exists for OTP ([`ET-IDN-001`](ET-IDN-001.md) BE-3). Classify:
- Is sending **synchronous** on any business path? That is `contradicted` — R4.
- Does any service **name a provider**? Same as [`ET-PAY-001`](ET-PAY-001.md): the port exists so
  the provider is an adapter.
- Can a **transactional** category be disabled by preference? It must not be.

## A · Backend

### BE-1 · `NotificationChannelPort` and the four adapters
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** yes *(one adapter per agent)*
- **Acceptance** **no service names a provider**; a test double needs **no service change**.

### BE-2 · The template registry, its seeding and the render-fail-fast rule
- **Spec** R2 · **§5** T2 · **depends** R0 · **parallel-safe** no
- **Acceptance** every §4 key exists **for every channel in its chain**; **a missing parameter
  fails**.
- Fail at render, loudly, rather than sending "Your ticket for {{eventName}} is confirmed" to a
  real person.

### BE-3 · Categories, chains and the transactional-versus-optional rule
- **Spec** R3, R5 · **§5** T3 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** **`OTP` never attempts email**; **a disabled transactional category still sends**.
- OTP by email would be a slow, interceptable path to an account whose whole identity is a phone
  number. Transactional messages — payment taken, payout sent, event cancelled — are not
  marketing, and a user cannot opt out of being told their money moved.

### BE-4 · The asynchronous send path and the three provider-outage tests
- **Spec** R4 · **§5** T4 · **depends** BE-1, BE-3 · **parallel-safe** no
- **Acceptance** **purchase, payout and cancellation all complete with every provider stopped.**
  Stop real containers; a mocked failure returns instantly and tests nothing about timeouts.

### BE-5 · Deduplication — the unique index and the Redis fast path
- **Spec** R7 · **§5** T5 · **depends** BE-4 · **parallel-safe** yes
- **Acceptance** three deliveries of one trigger produce **one** message. Redis is the fast path;
  the unique index is the authority ([`ET-PLT-002`](ET-PLT-002.md) R7) — confirm it live via MCP.

### BE-6 · Retry with backoff inside the workflow, and the terminal failure
- **Spec** R6 · **§5** T6 · **depends** BE-4 · **parallel-safe** yes
- **Acceptance** **nothing reaches the dead-letter queue**; a **failed transactional message
  surfaces**.
- A failed marketing message is noise. A failed "your event is cancelled" is a person who turns up
  at a locked venue, so it must become visible to an operator rather than dying in a DLQ.

### BE-7 · Device registration, invalidation and pruning
- **Spec** R8 · **§5** T7 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** register → invalidate → re-register yields **one active row**. Devices are
  deactivated at 90 days of inactivity.

### BE-8 · Preferences, and the refusal to disable a transactional category
- **Spec** R3 · **§5** T8 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** disabling `PAYMENT` is **refused, not ignored**.
- Silently ignoring the request is worse than refusing: the user believes they have opted out and
  the platform keeps sending. Refuse and explain.

### BE-9 · The subgraph half; `@auth` on every field
- **Spec** R1–R8 · **§5** T9 · **depends** BE-8 · **parallel-safe** **no — shared identity SDL**

## B · Contract

### GQL-1 · 5 queries, 7 mutations
- **depends** BE-9 · **parallel-safe** no

## C · Frontend — preferences only

### FE-1 · Notification preferences
- **depends** GQL-1 · **parallel-safe** no
- `Ticketing - Profile & Registration` and `apps/organization-admin/.../settings/notifications`
  (route exists).
- Optional categories toggle. **Transactional categories are shown as always-on with the reason**,
  not as a toggle that refuses — offering a control that cannot work is worse than not offering it.
- **testids** `notification-pref-<category>`, `notification-pref-transactional-locked`

### FE-2 · Channel preferences
- **depends** FE-1 · **parallel-safe** yes
- Per category, per channel, within the chain the backend allows. `OTP` shows no email option at
  all (BE-3).
- **testids** `notification-channel-<category>-<channel>`

### FE-3 · Device management
- **depends** BE-7 · **parallel-safe** yes
- Registered devices, last seen, revoke. Pairs with [`ET-IDN-003`](ET-IDN-003.md) FE-3's session
  list — same shape, different concern; do not build two different-looking tables.
- **testids** `device-row`, `device-revoke`, `device-last-seen`

### FE-4 · Delivery-failure visibility for transactional messages
- **depends** BE-6 · **parallel-safe** yes
- If a transactional message could not be delivered, the user sees the content **in the app**.
  Delivery is a convenience; the app is the record ([`ET-TKT-002`](ET-TKT-002.md) BE-7).
- **testids** `notification-delivery-failed`, `notification-inapp-fallback`

## D · Tests

### TS-1 · The port *(L1/L2)* — no service names a provider; a double needs no service change.

### TS-2 · Templates *(L1)*
Every §4 key for every channel in its chain; a missing parameter fails at render.

### TS-3 · Categories *(L1/L3)*
`OTP` never attempts email; a disabled transactional category still sends; disabling `PAYMENT` is
refused.

### TS-4 · Independence *(L3 — stop the real containers)*
Purchase, payout and cancellation each complete with **every** provider stopped.

### TS-5 · Deduplication *(L3)*
Three deliveries → one message; a Redis flush between deliveries still dedupes (the index is the
authority).

### TS-6 · Retry *(L3, WireMock)*
Backoff observed; **nothing reaches the DLQ**; a failed transactional message surfaces to an
operator.

### TS-7 · Devices *(L3)* — register/invalidate/re-register → one active row; 90-day pruning.

### TS-8 · e2e *(L5)*
Preferences toggle; transactional shown as locked with a reason; `OTP` offers no email; device
revoke; in-app fallback for a failed transactional message. Loading, empty, error, populated.

## E · Gate

- [ ] R0 recorded; any synchronous send on a business path classified `contradicted`
- [ ] No service names a provider
- [ ] Every template key exists for every channel in its chain; missing parameters fail at render
- [ ] `OTP` never attempts email
- [x] Transactional categories cannot be disabled; the attempt is **refused, not ignored** — each of the five essential switches set to false is refused with a field violation and nothing stored. Evidence: `NotificationPreferencesTest.Essential` (L2, real validators, 2026-09-19).
- [ ] Purchase, payout and cancellation complete with every provider stopped (real containers)
- [ ] Three deliveries of one trigger → one message, surviving a Redis flush
- [ ] Nothing reaches the DLQ; failed transactional messages surface
- [ ] Devices: one active row across register/invalidate/re-register
- [x] Transactional categories rendered as locked-with-reason, never as a failing toggle — organization-admin notification settings show the essential rows as disabled "Always on" switches excluded from saves; the page explains why (2026-09-19).
- [ ] **No template-management screen was built** — none is in scope
- [ ] `mvn -q -f backend/identity-service test -Dgroups=ET-NTF-001 -DfailIfNoTests=false` green
- [ ] Spec `status:` → `implemented`
