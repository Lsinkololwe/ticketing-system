# ET-TKT-002 · Ticket issuance, the QR identity and delivery

> **Amended 2026-10-05 (bookings, resend, holder messaging).**
> - **Bookings.** Every purchase has a durable `Booking` with a human number `BK-<year>-<8 digits>` drawn from a per-year Mongo counter
>   (unique index), staged at checkout with the order contact (name, email, phone: optional, typed once and never overwritten) and enriched when the
>   hold exists. Tickets carry `bookingId` and `bookingNumber`. Queries: `booking(id)` (buyer owner, organizer of the event's organization, or admin),
>   `bookingsByBuyer(buyerId?)` (a buyer sees only their own; admin may pass any), `bookingsByOrganizer(filter, pagination)` (own organization only).
>   The status is derived from stored facts (`BookingRules`), including `PARTIALLY_REFUNDED`, `REFUNDED` and `PAID_AFTER_EXPIRY_AUTO_REFUNDED`.
> - **resendTicket.** The holder only; re-delivers the same QR to the holder's verified contact through the notification service; at most
>   `booking.ticket.resend-limit` (5) per ticket per day (Redis), refusal `RATE_LIMITED`.
> - **messageTicketHolders(eventId, subject, message).** Organizer of the event's organization only; one message to every current holder of a valid ticket,
>   subject 3-80 and message 10-500 characters, control characters stripped, at most 5000 recipients, rate-limited per event; sent through the
>   notification service in batches of 200 and audited. Returns the number of recipients; holder contact details are never returned.
> - **Sales analytics.** `salesOverTime(eventId, from, to, bucket)` (HOUR/DAY/WEEK, Lusaka-time buckets, empty buckets present as zero, bounded range) and
>   `purchasesByDayAndHour` for the organizer's own events.
> Tests: `BookingOperationsRulesTest` (L1).
>
> **Verified 2026-10-05 (integration tests `TicketResendTest`, `HolderMessagingTest`, `ReadsScopingTest`).** As built, `resendTicket` is allowed to the ticket's
> holder, to an attendee-list reader of the event (an organizer's team, the prototype's Bookings tab needs it) and to platform staff; anybody else is told
> `TICKET_UNKNOWN`, exactly as for an invented id. It is capped at 3 per ticket and 30 per caller per hour (Redis; a down limiter refuses). It sends no QR
> and no raw contact: the notification names the ticket and event and goes to the *current holder's* verified contact, resolved by identity. `messageTicketHolders`
> reaches the holders of ISSUED/VALIDATED tickets of the event (a ticket in an open transfer is skipped), at most 5000, 3 per event, 10 per sender and 30 per organization
> per day, the organizer is told only a count, and `deliveredCount` is what identity says it reached (`PARTIAL`/`FAILED` otherwise).

> **Conformance** · V3 §4.1 purchase journey · US Part III §16 US-BUY-002 · US Part III §18 scanner stories

> **Amended 2026-10-04 (D-39, D-40, D-41; F-044).** Each ticket has **one fixed QR**, issued with the ticket and never rotated (D-40).
> Delivery is **WhatsApp and email** (no SMS). **R5 below is superseded**: there is no signature-rotating re-issue; a lost or shared
> QR is handled by re-sending the same QR, by the gate fallback (ticket code plus holder ID, [ET-TKT-003](../003-validation-and-checkin/)),
> and by **first scan wins**: a second presentation of the same ticket is refused as already used. The human-readable ticket code is
> still not a credential on its own.

## 1. Capability

A ticket is a promise the platform makes to a person and then has to honour at a gate,
months later, on a phone with no signal, in front of a queue. Everything about how it is
represented is downstream of that moment: it must be verifiable offline, impossible to
forge, cheap to scan, and recoverable when somebody loses their phone the morning of the
event.

This spec issues that ticket. It declares the document, the human-readable reference a
person reads out at a will-call desk, and — the part that matters — the **QR payload**,
which is not a database key but a signed assertion. A QR code containing a ticket id is a
QR code that anybody can generate by counting; a QR code carrying an HMAC over the ticket's
identity, event and issue time is one that can be checked at the gate against a key the
scanner holds, without a network call and without a database.

It declares delivery across the channels a buyer actually has — in-app, WhatsApp, SMS and
email — and re-issue, because phones are lost and a ticket that cannot be recovered is a
refund request. And it declares the one rule that keeps re-issue from becoming a
duplication attack: re-issuing rotates the signature, and the previous QR stops working the
instant the new one is generated.

Validation itself — scanning, the duplicate defence, offline operation at the gate — is
[ET-TKT-003](../003-validation-and-checkin/). This spec's job is to produce a ticket that
validation can trust.

## 2. Design decisions

**The QR carries a signed payload, not an identifier.** `{ticketId}.{eventId}.{issuedAt}.{sig}`
where `sig` is an HMAC-SHA256 over the first three fields with a per-event signing key. A
scanner holding the event's key can verify a ticket with no network and no database, which
is what makes the gate work when the venue's connectivity does not. An identifier alone
requires a lookup, and a lookup requires a network.

**One signing key per event, derived from a platform master key.** `HKDF(masterKey, eventId)`.
Per-event derivation means a scanner app can be issued exactly the key for the event it is
working, and a leaked key compromises one event rather than the platform. The master key
never leaves the server.

**Superseded 2026-10-04 (D-40): the QR is fixed and never rotated; there is no re-issue that kills an old QR.** *Original text:* **Re-issue rotates the signature and invalidates the old QR immediately.** The ticket keeps
its id, its reference and its history; `issuedAt` moves and the signature changes. A buyer
who lost their phone gets a working ticket, and whoever has the old phone does not. This is
the property that makes re-issue safe enough to offer self-service.

**The human-readable reference is not the security boundary.** `ET-7K3M-9QX2` is short,
unambiguous when read aloud (no `0`/`O`, no `1`/`I`), and unique — but it is guessable
enough that it must never be sufficient to admit somebody. It exists for the will-call desk
and for support conversations, and admitting on it requires an operator with `ticket:scan`
making a deliberate override that is recorded.

**Tickets are issued inside the confirmation transaction, one per seat.** A four-seat order
produces four ticket documents, not one with a quantity. Every seat can be transferred,
refunded and scanned independently, and a quantity field would have to be decomposed the
first time somebody transfers one of four.

**The buyer is the first owner, and ownership is a field, not the buyer's identity.**
`ownerId` starts as the purchaser and moves on transfer
([ET-TKT-004](../004-transfer-and-resale/)). `purchasedById` never moves, because the refund
goes back to whoever paid.

**Delivery is best-effort across channels and never gates issuance.** The ticket exists the
moment the transaction commits. WhatsApp, SMS and email are attempts recorded against it; a
failed delivery is a retry and a notification, never a reason the ticket does not exist. A
buyer can always see it in the app.

**A ticket names its tier and event by id and carries a snapshot of what it cost.** Prices
change, tiers are renamed, events are rescheduled. A refund six weeks later needs to know
what was actually paid, so `unitPrice`, `commissionAmount` and `netAmount` are written onto
the ticket at issue and never recomputed.

**Rejected alternatives**

- *A QR containing the ticket id.* Anyone can generate one by counting, and verifying requires a network call the venue may not have.
- *A per-ticket signing key.* The scanner would need every key for the event, which is the same as the event key with extra steps.
- *A globally rotating key.* Rotation invalidates every ticket in the platform, including for events already under way.
- *Re-issue that leaves the old QR working.* Turns a lost phone into two admissions.
- *One ticket document with a quantity.* Decomposes the first time somebody transfers one of four.
- *Admitting on the human-readable reference alone.* It is short enough to guess and is printed on things people photograph.
- *Blocking the purchase on delivery.* A WhatsApp outage would stop ticket sales.
- *Recomputing price at refund time.* Refunds the current price for a ticket bought at the early-bird rate.

## 3. Requirements

### ET-TKT-002-R1 · One ticket per seat, issued inside the confirmation transaction

WHEN a reservation is confirmed, THE SYSTEM SHALL issue exactly one ticket per reserved
seat, in the same transaction.

**Acceptance**
- [ ] A reservation for four seats produces four `booking_tickets` documents
- [ ] No ticket document carries a quantity field
- [ ] Issuance happens inside [ET-TKT-001](../001-reservation-and-hold/) R7's single transaction; a failure issues none
- [ ] Each ticket records `eventId`, `tierId`, `reservationId`, `ownerId`, `purchasedById`, `unitPrice`, `commissionAmount`, `netAmount`, `currency`
- [ ] The price snapshot is written at issue and is never recomputed, asserted by a test that changes the tier price and then refunds
- [ ] Re-confirming an already-confirmed reservation returns the existing tickets and issues none
- [ ] `ticketReference` is unique across the platform

### ET-TKT-002-R2 · The QR is a signed assertion, verifiable offline

THE SYSTEM SHALL encode each ticket as a signed payload that a scanner can verify without a
network call.

**Acceptance**
- [ ] The payload is `{ticketId}.{eventId}.{issuedAt}.{signature}`, base64url, and fits comfortably in a QR at the error-correction level the scanner uses
- [ ] `signature` is `HMAC-SHA256` over the first three components with the event's signing key, truncated to 128 bits
- [ ] Verification is a constant-time comparison
- [ ] A payload with any component altered fails verification, asserted per component
- [ ] Verification requires only the payload and the event key — no database, no network
- [ ] The QR image is generated client-side from the payload; the server returns the payload, not an image
- [ ] `TICKET_SIGNATURE_INVALID` is the refusal, and it discloses nothing about which component failed

### ET-TKT-002-R3 · Keys are per event, derived, and never shipped whole

THE SYSTEM SHALL derive each event's signing key from a platform master key and SHALL issue
only per-event keys to scanners.

**Acceptance**
- [ ] The event key is `HKDF-SHA256(masterKey, eventId)`, computed on demand and never persisted
- [ ] `TICKET_SIGNING_MASTER_KEY` is environment-sourced and appears in no committed file, no log and no response
- [ ] A scanner is issued the key for exactly the events it holds `ticket:scan` on ([ET-ORG-003](../../organization/003-permission-resolution/))
- [ ] An event key is delivered over an authenticated channel and is scoped to the grant's expiry
- [ ] Compromise of one event key permits forging tickets for that event only, asserted by a test
- [ ] Master-key rotation is possible with a declared dual-key verification window, so rotation does not invalidate live tickets

### ET-TKT-002-R4 · The human-readable reference is legible and is not a credential

THE SYSTEM SHALL give each ticket a short reference that is unambiguous when read aloud,
and admission SHALL NOT be possible on it alone.

**Acceptance**
- [ ] The reference is `ET-XXXX-XXXX` over an alphabet excluding `0`, `O`, `1`, `I` and `L`
- [ ] It is unique, generated from a CSPRNG, and unique-indexed
- [ ] No validation path admits a ticket presented only by reference
- [ ] An operator holding `ticket:scan` may admit by reference as an explicit override, which records the operator, the reason and a `MANUAL` validation method ([ET-TKT-003](../003-validation-and-checkin/))
- [ ] The reference appears on the ticket, in delivery messages and in support tooling
- [ ] A test asserts that presenting a valid reference with no signature is refused

### ET-TKT-002-R5 · ~~Re-issue rotates the signature and kills the old QR~~ — superseded: the QR is fixed; re-send replaces re-issue

**Replacement acceptance (2026-10-04)**
- [ ] The payload of a ticket is identical for the ticket's whole life; `issuedAt` and the signature never change after issuance
- [ ] `resendTicket` (owner only) re-delivers the **same** QR on WhatsApp or email, rate-limited per ticket to `booking.ticket.resend-limit` (5 per day), and writes a delivery attempt row
- [ ] A second presentation of a ticket already `VALIDATED` is refused with `TICKET_ALREADY_VALIDATED`: **first scan wins**
- [ ] The `reissueTicket` operation and `issueCount` rotation are removed; an operator who must stop a lost ticket cancels it, and the buyer is issued a new ticket id with a new fixed QR

**Original acceptance, retained for history only**

WHEN a ticket is re-issued, THE SYSTEM SHALL generate a new payload and SHALL cause the
previous one to fail verification.

**Acceptance**
- [ ] `reissueTicket` may be called by the ticket's owner and by an operator holding `ticket:scan`
- [ ] Re-issue updates `issuedAt`, which changes the signature; the ticket id and reference are unchanged
- [ ] The previous payload fails verification immediately, asserted by a test that verifies before and after
- [ ] Verification checks `issuedAt` against the ticket's current `issuedAt`, which is the only lookup a **networked** scanner performs; an offline scanner accepts any correctly signed `issuedAt` and reconciles on reconnect ([ET-TKT-003](../003-validation-and-checkin/))
- [ ] Re-issue is rate-limited per ticket to `booking.ticket.reissue-limit` (5 per day)
- [ ] Each re-issue increments `issueCount` and writes an audit row
- [ ] A validated ticket cannot be re-issued — it has already been used

### ET-TKT-002-R6 · Delivery is attempted on every channel and never gates issuance

THE SYSTEM SHALL deliver the ticket over the buyer's available channels, and IF delivery
fails, THEN THE SYSTEM SHALL retain the ticket and retry.

**Acceptance**
- [ ] Delivery is triggered by `booking.TicketPurchased`, after commit — never inside the confirmation transaction
- [ ] Channels attempted are in-app (always), then WhatsApp and email per the buyer's contact and preferences; **SMS is not a channel (D-39)**; no message contains a sign-in link ([ET-NTF-001](../../notification/001-notification-transport/))
- [ ] Delivery runs as `TicketDeliveryWorkflow` (`ticket-delivery/{ticketId}`, [ET-PLT-015](../../_platform/015-durable-execution/)); when no channel confirms, the ticket remains available in-app and at the gate by ticket code plus ID
- [ ] A failure on every channel leaves the ticket valid and visible in the app, and raises a notification failure, not a purchase failure
- [ ] Delivery attempts are recorded against the ticket with channel, outcome and timestamp
- [ ] A buyer can re-send delivery themselves, rate-limited
- [ ] A test stops every messaging provider and asserts the purchase still completes and the ticket is visible

### ET-TKT-002-R7 · A ticket's state machine is small and every transition is owned

THE SYSTEM SHALL admit exactly the seven ticket states of §4, and each transition SHALL be
owned by exactly one spec.

**Acceptance**
- [ ] `TicketStatus` declares exactly `ISSUED`, `VALIDATED`, `TRANSFERRED`, `REFUND_PENDING`, `REFUNDED`, `CANCELLED`, `EXPIRED`
- [ ] The §4 table names the owning spec for every transition, and no spec introduces one that is not there
- [ ] `REFUNDED`, `CANCELLED` and `EXPIRED` are terminal
- [ ] A transition from a terminal state is refused with `TICKET_STATE_INVALID` carrying `currentStatus`
- [ ] A test drives all `(status, transition)` pairs
- [ ] `EXPIRED` is reached through the event's `TicketExpiryWorkflow`, 24 h after `catalog.EventCompleted`, for tickets never validated — a reporting outcome, not a punishment, and it does not affect refund eligibility

## 4. Model

> **Amended 2026-09-01 under [D-19](../../ROADMAP.md).** 1 operation name below adopts the
> shipped name: `eventTickets` → `ticketsByEvent`. D-19 rules that where the schema and §4 disagree on an operation's
> *name*, the schema stands and §4 adopts it.
>
> **Only the names were adopted.** Argument lists and return types were not re-verified against
> the schema, so a row here can now name a real operation and still describe it wrongly. That
> gap is unmeasured, and calling it verified would be the same mistake as counting a file's
> existence as proof it runs.

### The document

`booking_tickets`

| Field | Type | Notes |
|---|---|---|
| `_id` | `String` | the `ticketId` in the QR payload |
| `ticketReference` | `String` | `ET-XXXX-XXXX`, **unique** |
| `eventId`, `tierId`, `reservationId` | `String` | |
| `ownerId` | `String` | moves on transfer |
| `purchasedById` | `String` | **never moves** — the refund goes here |
| `organizationId` | `String` | denormalised for tenant scoping |
| `unitPrice`, `commissionAmount`, `netAmount` | `BigDecimal` | the snapshot, never recomputed |
| `currency` | `String` | |
| `status` | `TicketStatus` | the seven |
| `issuedAt` | `Instant` | **part of the signature** — rotates on re-issue |
| `issueCount` | `int` | |
| `validatedAt`, `validatedById`, `validationMethod` | | [ET-TKT-003](../003-validation-and-checkin/) |
| `transferredAt`, `transferredFromId` | | [ET-TKT-004](../004-transfer-and-resale/) |
| `refundRequestId` | `String` | [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) |
| `deliveryAttempts` | `List<DeliveryAttempt>` | channel, outcome, timestamp |
| `createdAt`, `updatedAt` | `Instant` | |

### The QR payload

```
payload   = base64url(ticketId) "." base64url(eventId) "." issuedAtEpochSeconds "." base64url(sig)
eventKey  = HKDF-SHA256(masterKey, salt = "et-ticket-v1", info = eventId, length = 32)
sig       = HMAC-SHA256(eventKey, ticketId "." eventId "." issuedAtEpochSeconds)[0..15]
```

Truncated to 128 bits, which is ample against forgery and keeps the QR at a density a
cheap scanner reads in one pass. Verification is constant-time and needs only the payload
and `eventKey`.

**The server returns the payload string.** The QR image is rendered client-side, so a
ticket can be displayed offline from local storage.

### Ticket state machine

| From | Transition | To | Owned by |
|---|---|---|---|
| — | issue | `ISSUED` | this spec |
| `ISSUED` | validate | `VALIDATED` | [ET-TKT-003](../003-validation-and-checkin/) |
| `ISSUED` | transfer | `TRANSFERRED` → `ISSUED` for the new owner | [ET-TKT-004](../004-transfer-and-resale/) |
| `ISSUED`, `VALIDATED` | request refund | `REFUND_PENDING` | [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) |
| `REFUND_PENDING` | refund settles | `REFUNDED` | [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) |
| `REFUND_PENDING` | refund refused | back to previous | [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) |
| `ISSUED` | event cancelled | `CANCELLED` | [ET-CAT-001](../../catalog/001-event-lifecycle/) |
| `ISSUED` | event completed, never scanned | `EXPIRED` | this spec's `TicketExpiryWorkflow` |

`REFUNDED`, `CANCELLED` and `EXPIRED` are terminal. `TRANSFERRED` is a marker on the
**source** ticket's history rather than a resting state — the ticket itself returns to
`ISSUED` under a new `ownerId`, which is what keeps it scannable.

### The reference alphabet

`23456789ABCDEFGHJKMNPQRSTUVWXYZ` — 31 characters, excluding `0`, `O`, `1`, `I`, `L`.
`ET-` prefix, two groups of four: 31⁸ ≈ 8.5 × 10¹¹ combinations, generated from a CSPRNG
and unique-indexed. Guessable enough that R4 forbids admitting on it; unambiguous enough to
read down a phone line.

### GraphQL

Subgraph `booking`. Every field carries `@auth` explicitly.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `ticket(id)` | query | `AUTHENTICATED` | `Ticket` |
| `ticketByReference(reference)` | query | `ORGANIZER` | `Ticket` |
| `myTickets(status, first, after)` | query | `AUTHENTICATED` | `TicketConnection!` |
| `ticketsByEvent(eventId, status, page)` | query | `ORGANIZER` | `TicketPage!` `@tag(name: "admin")` |
| `ticketQrPayload(id)` | query | `AUTHENTICATED` | `String!` |
| `reissueTicket(id)` | mutation | `AUTHENTICATED` | `Ticket!` |
| `resendTicketDelivery(id, channel)` | mutation | `AUTHENTICATED` | `Boolean!` |
| `eventSigningKey(eventId)` | query | `ORGANIZER` | `String!` `@tag(name: "internal")` |

`ticketQrPayload` returns the payload only to the ticket's **current owner**, checked at the
repository. `eventSigningKey` is the scanner-provisioning call of R3 and is available only
to an actor holding `ticket:scan` on that event.

`Ticket` is booking's `@key(fields: "id")` type. It is extended by no other subgraph.

### Events

| Tier | Name | When | Consumers |
|---|---|---|---|
| bus | `booking.TicketPurchased` v1 | staged in the confirmation transaction | catalog → counters; identity → deliver |

Delivery and the re-issue audit row are steps of the purchase workflow and of `reissueTicket`,
not in-memory events.

### Workflows

| Workflow | Id | Queue | Started by | Timer and step |
|---|---|---|---|---|
| `TicketExpiryWorkflow` | `ticket-expiry/{eventId}` | `booking-finance` | booking's catalog-events consumer on `catalog.EventCompleted`, `USE_EXISTING` | sleeps 24 h past `completedAt`, then moves unscanned `ISSUED` tickets to `EXPIRED` in batches by compare-and-set, continuing as new per batch |

### Configuration

| Property | Value |
|---|---|
| `booking.ticket.reissue-limit` | 5 per day per ticket |
| `booking.ticket.signature-bits` | 128 |
| `booking.ticket.key-rotation-window` | `P30D` — the dual-key verification window |
| `TICKET_SIGNING_MASTER_KEY` | environment only |

### Error codes

`TICKET_UNKNOWN`, `TICKET_STATE_INVALID`, `TICKET_SIGNATURE_INVALID` — rows of
[ET-PLT-005 §4](../../_platform/005-error-contract/) introduced by this spec.

## 5. Tasks

- [ ] **T1 · The document, the seven states, the transition-ownership table**
  - requirements: R1, R7
  - files: `backend/booking-service/.../domain/model/Ticket.java`
  - verify: all `(status, transition)` pairs; no quantity field
  - parallel-safe: no
  - depends: —

- [ ] **T2 · Issuance inside the confirmation transaction; the price snapshot**
  - requirements: R1
  - files: `backend/booking-service/.../service/impl/TicketIssuanceService.java`
  - verify: four seats yield four tickets; a later tier price change does not alter a refund
  - parallel-safe: no
  - depends: T1

- [ ] **T3 · The reference generator and its alphabet**
  - requirements: R4
  - files: `backend/booking-service/.../domain/TicketReference.java`
  - verify: no ambiguous characters; uniqueness under 10⁶ generations
  - parallel-safe: yes
  - depends: T1

- [ ] **T4 · HKDF key derivation and the payload signer**
  - requirements: R2, R3
  - files: `backend/booking-service/.../domain/TicketSigner.java`
  - verify: each altered component fails; verification needs no database; one leaked key forges one event
  - parallel-safe: no
  - depends: T1

- [ ] **T5 · Re-issue, rotation and the rate limit**
  - requirements: R5
  - files: `backend/booking-service/.../service/impl/TicketServiceImpl.java`
  - verify: the previous payload fails immediately; a validated ticket cannot be re-issued
  - parallel-safe: yes
  - depends: T4

- [ ] **T6 · The scanner key-provisioning query, scoped to the grant**
  - requirements: R3
  - files: `backend/booking-service/.../web/graphql/query/`
  - verify: only an actor with `ticket:scan` on that event receives a key; it is `@tag(name: "internal")`
  - parallel-safe: yes
  - depends: T4

- [ ] **T7 · Delivery from the after-commit listener; the provider-outage test**
  - requirements: R6
  - files: `backend/booking-service/.../event/listener/TicketDeliveryListener.java`
  - verify: every messaging provider stopped, the purchase still completes and the ticket is visible
  - parallel-safe: yes
  - depends: T2

- [ ] **T8 · The expiry workflow on event completion**
  - requirements: R7
  - files: `backend/booking-service/.../workflow/ticket/TicketExpiryWorkflowImpl.java`
  - verify: a time-skipping test expires unscanned tickets 24 h after completion; refund eligibility is unaffected; the history replays
  - parallel-safe: yes
  - depends: T1

- [ ] **T9 · The subgraph half; owner-only payload access**
  - requirements: R2
  - files: `backend/booking-service/src/main/resources/graphql/schema.graphqls`
  - verify: a non-owner requesting `ticketQrPayload` receives `TICKET_UNKNOWN`
  - parallel-safe: no — shared SDL across booking's specs
  - depends: T4

## 6. Out of scope

| Capability | Spec |
|---|---|
| The reservation and confirmation that trigger issuance | [ET-TKT-001](../001-reservation-and-hold/) |
| Scanning, the duplicate defence, offline operation | [ET-TKT-003](../003-validation-and-checkin/) |
| Transferring a ticket to another person | [ET-TKT-004](../004-transfer-and-resale/) |
| Refunding a ticket | [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) |
| The channels, templates and delivery outcomes | [ET-NTF-001](../../notification/001-notification-transport/) |
| Who may scan which event | [ET-ORG-003](../../organization/003-permission-resolution/) |
| Erasing an attendee's identity from a ticket | [ET-PLT-008](../../_platform/008-data-protection/) |

Deliberately never in scope: **a QR containing only an identifier** (guessable, and
unverifiable without a network at a venue that may not have one), **admission on the
human-readable reference alone** (it is printed on things people photograph), and
**blocking a purchase on delivery** (a WhatsApp outage would stop ticket sales).
