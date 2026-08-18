# ET-TKT-002 · Ticket issuance, the QR identity and delivery — tasks

> **Spec** [`specs/ticketing/002-ticket-issuance-and-qr/spec.md`](../ticketing/002-ticket-issuance-and-qr/spec.md) · **Wave 3** · `blocked_by:` ET-PLT-002, 005, 007, ET-TKT-001, ET-ORG-003
> **Screens** `Ticketing - Discover & Checkout.dc.html` *(confirmation)* + `Ticketing - My Tickets & Transfer.dc.html`
> **Routes** `apps/ticketing/src/app/my-tickets/page.tsx`
> **Verify** `mvn -q -f backend/booking-service test -Dgroups=ET-TKT-002 -DfailIfNoTests=true` · `compose-supergraph.sh --static`

The ticket is the thing the buyer actually bought, and its QR is a **bearer credential** —
whoever holds a valid payload gets through the gate. That single fact drives every design decision
here: offline-verifiable signatures, per-event key derivation, and owner-only payload access.

## R0 · Reconcile

`Ticket.java` and `TicketStateMachine` exist (the latter untracked), plus
`TicketStatusConformanceMigrationService`. 29 code references to `ET-TKT-002`.

Classify, with these as the decisive checks:
- Does `Ticket` carry a **quantity** field? R1 says one document per seat — a quantity field is
  `contradicted`, and it breaks transfer, validation and refund of a partial order.
- Is the QR payload **signed and offline-verifiable**, or is it a lookup id? A lookup id cannot be
  validated at a gate with no signal, which is the case [`ET-TKT-003`](ET-TKT-003.md) exists for.
- Is the signing key **per-event**, or one platform key?

## A · Backend

### BE-1 · The document, the seven states, the transition-ownership table
- **Spec** R1, R7 · **§5** T1 · **depends** R0 · **parallel-safe** no
- **Acceptance** all `(status, transition)` pairs asserted; **no quantity field**.
- One document per seat. Four tickets is four documents — anything else makes "transfer one of my
  four" unrepresentable.

### BE-2 · Issuance inside the confirmation transaction; the price snapshot
- **Spec** R1 · **§5** T2 · **depends** BE-1 · **parallel-safe** no
- Runs inside [`ET-TKT-001`](ET-TKT-001.md) BE-6's single transaction.
- **Acceptance** four seats yield four tickets; **a later tier price change does not alter a
  refund**. The ticket records what was paid, because the refund is computed from it.

### BE-3 · The reference generator and its alphabet
- **Spec** R4 · **§5** T3 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** **no ambiguous characters** (no `0`/`O`, `1`/`I`/`l`); uniqueness under 10⁶
  generations.
- The reference gets read aloud over a phone and typed by a steward at a gate. Ambiguous glyphs
  cost support calls at exactly the moment nobody has time.

### BE-4 · HKDF key derivation and the payload signer
- **Spec** R2, R3 · **§5** T4 · **depends** BE-1 · **parallel-safe** no
- **Acceptance** each altered component fails verification; **verification needs no database**;
  **one leaked key forges one event**.
- Per-event derivation is the containment property. Scanner devices hold keys and get lost; a
  platform-wide key on a lost device forges every ticket the platform will ever issue.

### BE-5 · Re-issue, rotation and the rate limit
- **Spec** R5 · **§5** T5 · **depends** BE-4 · **parallel-safe** yes
- **Acceptance** the **previous payload fails immediately**; a **validated ticket cannot be
  re-issued**.
- Re-issue exists for a lost phone. Without immediate rotation it is a duplication feature: screenshot
  the QR, re-issue, and two people walk in.

### BE-6 · The scanner key-provisioning query, scoped to the grant
- **Spec** R3 · **§5** T6 · **depends** BE-4 · **parallel-safe** yes
- **Acceptance** only an actor holding `ticket:scan` **on that event** receives a key; the field
  is `@tag(name: "internal")`.
- Scoped through [`ET-ORG-003`](ET-ORG-003.md)'s resolver — the grant is per event, so the key is
  per event.

### BE-7 · Delivery from the after-commit listener; the provider-outage test
- **Spec** R6 · **§5** T7 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** with **every messaging provider stopped**, the purchase **still completes** and
  the ticket is **visible in the app**.
- Delivery is a convenience; the app is the source of truth. A purchase that fails because
  WhatsApp is down has confused notification with issuance.

### BE-8 · The expiry sweep on event completion
- **Spec** R7 · **§5** T8 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** unscanned tickets expire 24 h after completion; **refund eligibility is
  unaffected**. Expiry is about gate access, not about money owed.

### BE-9 · The subgraph half; owner-only payload access
- **Spec** R2 · **§5** T9 · **depends** BE-4 · **parallel-safe** **no — shared booking SDL**
- **Acceptance** a non-owner requesting `ticketQrPayload` receives **`TICKET_UNKNOWN`** — not
  `ACTOR_NOT_PERMITTED`, which would confirm the ticket exists ([`ET-PLT-005`](ET-PLT-005.md) R6).

## B · Contract

### GQL-1 · 6 queries, 2 mutations
- **depends** BE-9 · **parallel-safe** no *(sequence against booking's other SDL tasks)*
- The QR payload field is owner-only and never appears in a list projection — one careless
  `tickets { qrPayload }` on an admin screen would dump every bearer credential for an event.

## C · Frontend — `Ticketing - My Tickets & Transfer.dc.html`

### FE-1 · Purchase confirmation
- **depends** GQL-1, F0-1 · **parallel-safe** no
- One card per seat (BE-1), each with its reference in **Fira Code**. Total in `K`.
- Says plainly that tickets are in the app **regardless** of whether WhatsApp or SMS arrives —
  that sentence prevents the most common support contact.
- **testids** `purchase-confirmation`, `ticket-card`, `ticket-reference`

### FE-2 · My tickets list
- **depends** GQL-1 · **parallel-safe** no
- Upcoming and past. Per-ticket status humanised (**F0-7**) — never `PURCHASED` on screen.
- **Empty is a designed screen** — a buyer with no tickets gets a route to discovery, not a blank
  panel.
- **testids** `my-tickets-list`, `my-tickets-empty`, `my-tickets-past`, `ticket-status`

### FE-3 · The QR display — treat it as a credential
- **depends** FE-2 · **parallel-safe** no
- Fetched **only** when the ticket is opened, never prefetched into a list. High contrast, large,
  screen brightness raised if the platform allows.
- Show the reference beneath it for the case where the scanner fails and a steward types it.
- **testids** `ticket-qr`, `ticket-qr-reference`

### FE-4 · Offline access — the requirement that decides the architecture
- **depends** FE-3 · **parallel-safe** no
- **Venues have poor connectivity. The QR must render for a ticket already opened, with no
  network.** Cache the payload locally after first fetch, scoped to the ticket, cleared on
  re-issue or transfer.
- **Acceptance** an e2e opens a ticket, goes offline, and the QR still renders. Without this the
  buyer stands at a gate with a spinner.
- **testids** `ticket-offline-badge`

### FE-5 · Re-issue
- **depends** BE-5, GQL-1 · **parallel-safe** yes
- Confirm first and **state the consequence**: the old QR stops working immediately, including any
  screenshot or forwarded copy. Rate limit surfaced as a wait, not a bare refusal.
- Absent entirely for a validated ticket.
- **testids** `ticket-reissue`, `ticket-reissue-confirm`, `ticket-reissue-ratelimited`

### FE-6 · Delivery status
- **depends** BE-7 · **parallel-safe** yes
- If WhatsApp and SMS both failed, say so **without alarming** — the ticket is valid and in the
  app. A failed notification is not a failed purchase, and the copy must not read as though it is.
- **testids** `ticket-delivery-status`

## D · Tests

### TS-1 · Document shape *(L1)* — all pairs; **no quantity field**; four seats → four documents.

### TS-2 · Signing *(L1 — the security core)*
- Each altered component (ticket id, event id, seat, expiry) fails verification.
- **Verification requires no database.**
- **One leaked key forges only that event's tickets** — derive two event keys and prove the first
  cannot sign the second.

### TS-3 · Reference *(L1)* — no ambiguous characters; 10⁶ generations unique.

### TS-4 · Re-issue *(L3)*
Previous payload fails **immediately**; validated tickets cannot be re-issued; rate limit holds.

### TS-5 · Key provisioning *(L3)*
Only `ticket:scan` on **that** event receives a key; the field is internal-tagged and absent from
the public contract.

### TS-6 · Delivery independence *(L3 — stop the real containers)*
Every messaging provider stopped: the purchase completes, the ticket is visible. Mocking the
failure tests the mock.

### TS-7 · Expiry *(L3, frozen clock)*
Unscanned expire at completion + 24 h; refund eligibility unchanged.

### TS-8 · Access control *(L3)*
Non-owner → **`TICKET_UNKNOWN`**; the payload never appears in any list projection — assert
against the composed schema.

### TS-9 · e2e *(L5, ticketing — needs F0-1, F0-4)*
- Confirmation → list → open → QR → re-issue.
- **Offline**: open a ticket, drop the network, QR still renders.
- Empty, loading, error, populated.
- Re-issue confirm states the consequence; validated tickets offer no re-issue.
- QR is not prefetched — assert on network calls from the list view.

## E · Gate

- [ ] R0 recorded; a quantity field or a lookup-id QR classified `contradicted`
- [ ] One document per seat; no quantity field
- [ ] Price snapshot survives later tier price changes
- [ ] Reference alphabet excludes ambiguous characters; unique at 10⁶
- [ ] Signature verifies **offline**, with no database
- [ ] One leaked key forges exactly one event, proven with two derived keys
- [ ] Re-issue invalidates the previous payload immediately; validated tickets cannot re-issue
- [ ] Scanner keys scoped to a `ticket:scan` grant on that event
- [ ] Purchase completes with **every** messaging provider stopped
- [ ] Non-owner payload access returns `TICKET_UNKNOWN`
- [ ] QR never appears in a list projection and is never prefetched
- [ ] **Offline QR render proven by e2e**
- [ ] `mvn -q -f backend/booking-service test -Dgroups=ET-TKT-002 -DfailIfNoTests=true` green
- [ ] Spec `status:` → `implemented`
