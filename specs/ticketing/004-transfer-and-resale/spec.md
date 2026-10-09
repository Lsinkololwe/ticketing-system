# ET-TKT-004 · Ticket transfer and controlled resale

> **Amended 2026-10-05 (D-40, F-044; prototype requirements).** Transfers are **direct offers between two verified accounts**, not claim links.
> The QR is fixed and is **not** rotated on transfer (D-40, see ET-TKT-002 R5): only the holder (`buyerId`) changes. Operations are
> `transferRecipient(channel, value)` (looks up an ACTIVE account by WhatsApp number or email and answers only a masked display name, `First L.`,
> and an opaque id; an unknown or ambiguous contact answers null so existence is not disclosed), `initiateTicketTransfer`, `cancelTicketTransfer`
> (sender, while pending), `acceptTicketTransfer` / `declineTicketTransfer` (recipient only), `myTicketTransfers` and `ticketTransferChain`.
> Rules (pure, `TransferRules`): holder only (otherwise `TICKET_UNKNOWN`), status ISSUED, no transfer already pending, not to self (`TRANSFER_TO_SELF`),
> event not cancelled or ended, chain below `booking.transfer.max-chain` (5), and closed at `booking.transfer.cutoff` (2 h) before the start
> (`TICKET_NOT_TRANSFERABLE` with reason). An offer lapses after `booking.transfer.ttl` (48 h) or at the event start, whichever is first, by the
> `TicketTransferWorkflow` timer. Initiation is rate-limited per user in Redis. Resale (R6-R7) remains out of scope. Tests: `BookingOperationsRulesTest` (L1).
>
> **Verified 2026-10-05 (integration test `TicketTransferTest`: real Temporal server, activities, MongoDB replica set and Redis).** Only the holder can offer; only the recipient
> can accept or decline; only the sender can cancel; every other caller is told `TICKET_TRANSFER_UNKNOWN` and nothing changes. Accept changes the holder exactly once
> (twelve simultaneous accepts: one hand-over, one `booking.TicketTransferred`), keeps the QR, records the payer once (`originalBuyerId`) and drops the previous holder's cached
> contact. Asking again for what was already done (accept, decline, cancel) is answered with the result, not a refusal. An offer past `expiresAt` cannot be accepted even
> before its timer fires; the timer returns the ticket. Offers are capped at 10 per hour per user and 3 per ticket per hour, contact lookups at 20 per hour. A worker restart
> mid-offer loses nothing and a settled history replays against the current workflow code.

> **Conformance** · US-BUY-003 ticket transfer

## 1. Capability

Somebody buys four tickets for their friends, and then has to get three of them to three
people. Somebody else falls ill the week before and wants to pass their ticket to a
colleague. Both are ordinary, both happen constantly, and a platform without an answer for
them gets the answer anyway — screenshots forwarded on WhatsApp, two people at a gate with
the same QR, and a steward making a judgement call.

This spec gives the platform an answer. **Transfer** moves a ticket's ownership to another
person: the sender loses it, the recipient gains it, the QR rotates so the sender's copy
stops working, and both are notified. It is free, it is the common case, and it is designed
to be easier than forwarding a screenshot — because that is the only way it displaces
forwarding a screenshot.

**Resale** is transfer with money attached, and it is a different animal. It invites
scalping, it makes the platform a marketplace with obligations it has not signed up for,
and it needs price controls, a settlement path and a fraud story. This spec therefore
builds transfer fully and declares resale as a **capability behind a per-event flag, capped
at face value**, off by default — so an organizer who wants a controlled resale channel has
one, and the platform never becomes a secondary market by accident.

The property both share is that a ticket has exactly one owner at every instant, and the
transition between owners is atomic.

## 2. Design decisions

**A transfer rotates the QR, and that is what makes it real.** Ownership moving in a
database while the sender's phone still shows a working code is not a transfer. Transfer
calls [ET-TKT-002](../002-ticket-issuance-and-qr/) R5's re-issue, `issuedAt` moves, the
signature changes, and the sender's copy stops verifying. This is the single most important
mechanical detail in the spec.

**Transfer is by claim link, not by requiring the recipient to already exist.** The sender
generates a transfer with a token; the recipient opens the link, authenticates or registers,
and claims it. Requiring the recipient's account first means the sender has to ask *are you
on the platform* before they can do anything, which is the friction that sends them back to
WhatsApp.

**A pending transfer holds the ticket, and the ticket is not scannable while it is held.**
Otherwise the sender could transfer and then walk in. The ticket moves to `TRANSFER_PENDING`,
validation refuses it, and either the claim completes or the sender cancels and gets it back.

**Transfers expire in 48 hours.** A pending transfer is a ticket nobody can use. Long enough
for the recipient to see the message and act, short enough that a forgotten transfer does
not orphan a seat through the event.

**Transfer closes before the event starts.** `catalog.transfer.cutoff` before `startsAt` —
because a transfer completing while a queue is moving is a steward's problem, and because
the attendee list an organizer prints has to stop changing at some point.

**The refund follows the money, not the ticket.** `purchasedById` never moves
([ET-TKT-002](../002-ticket-issuance-and-qr/) §4), so a refund returns to whoever paid.
A recipient who did not pay cannot refund a ticket they were given — they can transfer it
onward or let it lapse. This is stated plainly because it is the rule people are most
surprised by.

**Resale is off unless the organizer turns it on, and it is capped at face value.**
`allowResale` per event, `maxResalePrice` defaulting to the original price. A cap at face
value removes the scalping incentive entirely; an organizer who wants a genuine transfer
market gets one, and nobody builds a secondary market on this platform by default.

**A resale settles through the same escrow as the original sale.** The buyer pays through
[ET-PAY-001](../../payment/001-payment-intents-and-providers/), the seller's proceeds
credit their own payout balance, and the platform takes its commission on the resale as on
any sale. No side channel, no cash between strangers.

**A ticket may be transferred more than once, and the chain is recorded.** People pass
tickets along. The chain is the audit trail an organizer needs when something goes wrong at
a gate, and a per-ticket transfer limit exists to stop a ticket circulating as currency.

**Rejected alternatives**

- *Transferring ownership without rotating the QR.* The sender's screenshot still admits.
- *Requiring the recipient to have an account first.* The friction that keeps people on WhatsApp.
- *Leaving the ticket scannable during a pending transfer.* The sender transfers and then walks in.
- *Transfers that never expire.* A forgotten transfer orphans a seat through the event.
- *Resale on by default.* Makes the platform a secondary market it has not designed for.
- *Resale above face value.* Every argument for it is an argument for scalping.
- *Cash or off-platform resale settlement.* Two strangers, no recourse, and the platform blamed anyway.
- *Letting the recipient refund a gifted ticket.* Pays out to somebody who never paid in.

## 3. Requirements

### ET-TKT-004-R1 · A transfer moves ownership and rotates the QR atomically

WHEN a transfer is claimed, THE SYSTEM SHALL move ownership and re-issue the ticket in one
transaction.

**Acceptance**
- [ ] Claiming sets `ownerId` to the recipient and calls re-issue ([ET-TKT-002](../002-ticket-issuance-and-qr/) R5) in the same transaction
- [ ] The sender's previous QR payload fails verification immediately afterwards, asserted directly
- [ ] `purchasedById` is unchanged
- [ ] `transferredFromId` and `transferredAt` are recorded, and the transfer is appended to the ticket's chain
- [ ] A failure at any point leaves the ticket with its original owner and its original payload
- [ ] Two parallel claims of one transfer produce exactly one ownership change
- [ ] `booking.TicketTransferred` is published after commit

### ET-TKT-004-R2 · A transfer is a claim link with a token and an expiry

WHEN a transfer is initiated, THE SYSTEM SHALL create a claimable transfer valid for a
bounded period.

**Acceptance**
- [ ] `initiateTransfer` requires the caller to be the ticket's current `ownerId`
- [ ] The transfer carries a 256-bit token, unique-indexed, and `expiresAt` at `booking.transfer.ttl` (PT48H)
- [ ] The recipient may be identified by phone, email, or neither — an open link is permitted and is stated as such to the sender
- [ ] `transferByToken` is `PUBLIC` and returns only the event title, the tier name, the sender's display name and `expiresAt`
- [ ] Claiming after expiry refuses; the ticket returns to the sender automatically
- [ ] The sender may cancel while pending, returning the ticket immediately
- [ ] A test asserts the preview exposes nothing else

### ET-TKT-004-R3 · A ticket in transfer is not usable by anybody

WHILE a transfer is pending, THE SYSTEM SHALL refuse validation, refund and a second
transfer of that ticket.

**Acceptance**
- [ ] The ticket moves to `TRANSFER_PENDING` on initiation
- [ ] Validation of a `TRANSFER_PENDING` ticket refuses with `TICKET_STATE_INVALID` carrying `currentStatus`
- [ ] A second `initiateTransfer` refuses with `TICKET_STATE_INVALID`
- [ ] A refund request on a pending transfer refuses until it resolves
- [ ] Expiry or cancellation returns the ticket to `ISSUED` with the sender as owner and **no** re-issue — the sender's QR keeps working
- [ ] The transfer's `TicketTransferWorkflow` (`ticket-transfer/{transferId}`) returns the ticket at `expiresAt` by timer; no sweep or lock exists
- [ ] Both expiry and cancellation are idempotent

### ET-TKT-004-R4 · Transfer closes before the event

IF the event starts within the cutoff, THEN THE SYSTEM SHALL refuse a new transfer.

**Acceptance**
- [ ] `initiateTransfer` refuses within `catalog.transfer.cutoff` (PT2H) of `startsAt` with `TICKET_NOT_TRANSFERABLE` carrying `reason`
- [ ] A transfer pending at the cutoff may still be claimed until it expires or the event starts, whichever is first
- [ ] A transfer unclaimed when the event starts returns to the sender
- [ ] A cancelled event refuses every new transfer and returns every pending one
- [ ] Frozen-clock tests assert refusal at the cutoff boundary either side
- [ ] The cutoff is per event and configurable by the organizer within platform bounds

### ET-TKT-004-R5 · Transfer is refused where it must be

THE SYSTEM SHALL refuse a transfer of a ticket that is not the sender's, is not `ISSUED`,
or would exceed the platform's transfer limit.

**Acceptance**
- [ ] Transferring a ticket the caller does not own refuses with `TICKET_UNKNOWN` — not a permission error, which would confirm the ticket exists
- [ ] A `VALIDATED`, `REFUNDED`, `CANCELLED` or `EXPIRED` ticket refuses with `TICKET_STATE_INVALID`
- [ ] Transferring to oneself refuses with `TRANSFER_TO_SELF`
- [ ] A ticket already transferred `booking.transfer.max-chain` (5) times refuses with `TICKET_NOT_TRANSFERABLE` carrying `reason`
- [ ] An event with transfers disabled by the organizer refuses with `TICKET_NOT_TRANSFERABLE`
- [ ] Every refusal persists nothing

### ET-TKT-004-R6 · Resale is off by default and capped at face value

WHERE an organizer has enabled resale, THE SYSTEM SHALL permit a priced transfer at or
below the original price, and otherwise SHALL refuse.

**Acceptance**
- [ ] `allowResale` defaults to `false` on every event
- [ ] With it disabled, `listForResale` refuses with `TICKET_NOT_TRANSFERABLE` carrying `reason`
- [ ] With it enabled, a listing above `min(originalPrice, event.maxResalePrice)` is refused
- [ ] The listing shows the buyer the price and the platform's commission on it before purchase
- [ ] A resale listing may be withdrawn by the seller while unsold
- [ ] A resale listing expires with the transfer cutoff of R4
- [ ] A test asserts no path permits a price above face value

### ET-TKT-004-R7 · A resale settles through escrow, and both sides are accounted

WHEN a resale completes, THE SYSTEM SHALL collect from the buyer, credit the seller and
take commission, all through the ledger.

**Acceptance**
- [ ] The buyer pays through [ET-PAY-001](../../payment/001-payment-intents-and-providers/) with an idempotency key
- [ ] Commission is computed on the resale price by [ET-FIN-002](../../finance/002-commission/) and is `PENDING` like any sale
- [ ] The seller's net proceeds credit a seller balance, payable through [ET-FIN-003](../../finance/003-payouts-and-settlement/)
- [ ] The entries balance: `debit 1010 resalePrice` / `credit 2040 Seller Proceeds net` / `credit 2020 Pending Commission`
- [ ] `2040 Seller Proceeds` is added to the chart of accounts by this spec
- [ ] Ownership moves only when the payment succeeds; a failed payment leaves the listing live
- [ ] `Ledger.assertBalanced()` holds after every resale
- [ ] The original purchase's commission is untouched — a resale is a new sale, not a re-pricing of the old one

### ET-TKT-004-R8 · The chain is recorded and visible to the organizer

THE SYSTEM SHALL record every transfer of a ticket and make the chain available to the
event's organizer.

**Acceptance**
- [ ] `booking_ticket_transfers` records every transfer with sender, recipient, type, price and timestamps
- [ ] The ticket carries `transferCount` and the ordered chain is queryable
- [ ] An organizer holding `attendee:view` sees the current owner and the chain
- [ ] A buyer sees only their own transfers, in or out
- [ ] The chain survives a refund and an event cancellation
- [ ] A test transfers a ticket three times and asserts the chain reads correctly from either end

## 4. Model

### Documents

`booking_ticket_transfers`

| Field | Type | Notes |
|---|---|---|
| `_id`, `ticketId`, `eventId` | `String` | |
| `fromUserId` | `String` | the sender |
| `toUserId` | `String` | null until claimed |
| `recipientPhone`, `recipientEmail` | `String` | optional — an open link is permitted |
| `transferToken` | `String` | 256-bit, **unique** |
| `transferType` | `TransferType` | `GIFT`, `RESALE` |
| `price`, `commissionAmount`, `netProceeds`, `currency` | `BigDecimal` | null for `GIFT` |
| `paymentIntentId` | `String` | `RESALE` only |
| `status` | `TransferStatus` | `PENDING`, `CLAIMED`, `EXPIRED`, `CANCELLED`, `FAILED` |
| `expiresAt` | `Instant` | **TTL index at `ttl` + grace** |
| `claimedAt`, `cancelledAt` | `Instant` | |
| `createdAt` | `Instant` | |

### Ticket status during transfer

| Ticket status | Meaning |
|---|---|
| `ISSUED` | normal, scannable, transferable |
| `TRANSFER_PENDING` | a transfer is outstanding — **not scannable, not refundable** |
| `ISSUED` (again) | claimed by the recipient, or returned to the sender |

`TRANSFER_PENDING` is added to
[ET-TKT-002](../002-ticket-issuance-and-qr/) §4's ticket state machine by this spec, and
the transition table there gains two rows:
`ISSUED → TRANSFER_PENDING` and `TRANSFER_PENDING → ISSUED`, both owned here.

### The claim, in one transaction

```java
@Transactional
public Mono<Ticket> claim(String token, String recipientId) {
    return transfers.findClaimable(token, clock.instant())
        .switchIfEmpty(Mono.error(new TicketNotTransferable(token, "EXPIRED_OR_CLAIMED")))
        .flatMap(t -> requireNotSelf(t, recipientId)
            .then(tickets.changeOwner(t.ticketId(), t.fromUserId(), recipientId))
            .then(tickets.reissue(t.ticketId()))          // ET-TKT-002 R5 — kills the old QR
            .then(transfers.markClaimed(t.id(), recipientId, clock.instant()))
            .then(tickets.find(t.ticketId()))
            .doOnSuccess(tk -> publisher.publishEvent(new TicketTransferredEvent(t, tk))));
}
```

`changeOwner` is a conditional update filtering on the **current** owner, so a concurrent
claim or a concurrent cancellation loses cleanly.

### Return on expiry or cancellation — no re-issue

```
expiry / cancellation:
  transfer  → EXPIRED | CANCELLED
  ticket    → ISSUED, ownerId unchanged (still the sender)
  QR        → UNCHANGED — the sender's code never stopped working
```

Re-issuing on return would break the sender's own ticket for no reason. Only a **successful
claim** rotates the signature.

### Resale accounting

`2040 Seller Proceeds` — a liability, credit-normal — is added to
[ET-FIN-001](../../finance/001-escrow-and-ledger/)'s chart by this spec.

| Account | Direction | Amount |
|---|---|---|
| `1010` Provider Settlement Receivable | debit | resalePrice |
| `2040` Seller Proceeds | credit | netProceeds |
| `2020` Pending Commission | credit | commissionAmount |

The seller's proceeds are payable through
[ET-FIN-003](../../finance/003-payouts-and-settlement/) against `2040` rather than an event escrow —
the seller is not the organizer and their money is not the event's.

### Refusals

| Condition | Code | Detail |
|---|---|---|
| not the caller's ticket | `TICKET_UNKNOWN` | — (deliberately not a permission error) |
| ticket not `ISSUED` | `TICKET_STATE_INVALID` | `currentStatus` |
| within the cutoff | `TICKET_NOT_TRANSFERABLE` | `reason: CUTOFF_PASSED` |
| chain limit reached | `TICKET_NOT_TRANSFERABLE` | `reason: CHAIN_LIMIT` |
| transfers disabled | `TICKET_NOT_TRANSFERABLE` | `reason: DISABLED_BY_ORGANIZER` |
| resale disabled | `TICKET_NOT_TRANSFERABLE` | `reason: RESALE_DISABLED` |
| price above face | `TICKET_NOT_TRANSFERABLE` | `reason: ABOVE_FACE_VALUE` |
| to oneself | `TRANSFER_TO_SELF` | — |

### GraphQL

Subgraph `booking`.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `transferByToken(token)` | query | `PUBLIC` | `TransferPreview` |
| `myTransfers(direction, page)` | query | `AUTHENTICATED` | `TicketTransferPage!` |
| `ticketTransferChain(ticketId)` | query | `AUTHENTICATED` | `[TicketTransfer!]!` |
| `resaleListings(eventId, first, after)` | query | `PUBLIC` | `ResaleListingConnection!` |
| `initiateTransfer(input)` | mutation | `CUSTOMER` | `TicketTransfer!` |
| `cancelTransfer(id)` | mutation | `CUSTOMER` | `TicketTransfer!` |
| `claimTransfer(token)` | mutation | `AUTHENTICATED` | `Ticket!` |
| `listForResale(input)` | mutation | `CUSTOMER` | `TicketTransfer!` |
| `withdrawResaleListing(id)` | mutation | `CUSTOMER` | `TicketTransfer!` |
| `buyResaleTicket(input)` | mutation | `CUSTOMER` | `PaymentIntent!` |

`buyResaleTicket` and `claimTransfer` each carry an `idempotencyKey`; `buyResaleTicket` is
added to [ET-PLT-007](../../_platform/007-security-and-authorization/) §4's idempotent-operation
registry by this spec, making it ten.

`TransferPreview` carries `eventTitle`, `tierName`, `senderDisplayName`, `transferType`,
`price` and `expiresAt` — and nothing else, because the token is a bearer credential.

### Events

| Tier | Name | When | Consumers |
|---|---|---|---|
| bus | `booking.TicketTransferred` v1 | after a claim commits | identity → notify both parties |

Sending the claim link and telling the sender of an expiry are activities of the transfer's
workflow, not in-memory events.

### Workflows

| Workflow | Id | Queue | Start | Updates | Timer |
|---|---|---|---|---|---|
| `TicketTransferWorkflow` | `ticket-transfer/{transferId}` | `booking-checkout` | `initiateTransfer`, Update-with-Start, `USE_EXISTING` | `claim`, `cancel` | `expiresAt` → return the ticket to the sender |

### Configuration

| Property | Value |
|---|---|
| `booking.transfer.ttl` | `PT48H` |
| `booking.transfer.max-chain` | 5 |
| `catalog.transfer.cutoff` | `PT2H` before `startsAt` |
| `catalog.event.allow-transfer` | `true` by default, per event |
| `catalog.event.allow-resale` | **`false`** by default, per event |
| `catalog.event.max-resale-price` | the original price |

### Error codes

`TICKET_NOT_TRANSFERABLE`, `TRANSFER_TO_SELF` — rows of
[ET-PLT-005 §4](../../_platform/005-error-contract/) introduced by this spec.
`TICKET_UNKNOWN` and `TICKET_STATE_INVALID` are raised here and owned by
[ET-TKT-002](../002-ticket-issuance-and-qr/).

## 5. Tasks

- [ ] **T1 · The transfer document, its token, its TTL and `TRANSFER_PENDING`**
  - requirements: R2, R3
  - files: `backend/booking-service/.../domain/model/TicketTransfer.java`
  - verify: a pending ticket is not scannable, not refundable, not re-transferable
  - parallel-safe: no
  - depends: —

- [ ] **T2 · `initiateTransfer` and its five refusals**
  - requirements: R5
  - files: `backend/booking-service/.../service/impl/TransferServiceImpl.java`
  - verify: a non-owner receives `TICKET_UNKNOWN`, not a permission error
  - parallel-safe: yes
  - depends: T1

- [ ] **T3 · `claimTransfer`: ownership plus re-issue, in one transaction**
  - requirements: R1
  - files: `backend/booking-service/.../service/impl/TransferServiceImpl.java`
  - verify: the sender's QR fails immediately after; two parallel claims yield one change
  - parallel-safe: no
  - depends: T2

- [ ] **T4 · Expiry and cancellation return the ticket without re-issuing**
  - requirements: R3
  - files: `backend/booking-service/.../workflow/transfer/TicketTransferWorkflowImpl.java`
  - verify: the sender's QR still works after a return; both paths are idempotent; a time-skipping test expires at `expiresAt`; the history replays
  - parallel-safe: yes
  - depends: T3

- [ ] **T5 · The cutoff, its boundary tests and the cancelled-event rule**
  - requirements: R4
  - files: `backend/booking-service/.../domain/TransferWindow.java`
  - verify: refusal either side of the cutoff; a cancelled event returns every pending transfer
  - parallel-safe: yes
  - depends: T2

- [ ] **T6 · The resale flag, the face-value cap and the listing lifecycle**
  - requirements: R6
  - files: `backend/catalog-service/.../domain/model/Event.java`, `backend/booking-service/.../service/impl/`
  - verify: resale is off by default; no path permits a price above face value
  - parallel-safe: yes
  - depends: T3

- [ ] **T7 · Resale settlement: `2040`, the entries and the payment path**
  - requirements: R7
  - files: `backend/booking-service/.../service/impl/ResaleServiceImpl.java`
  - verify: the entries balance; a failed payment leaves the listing live; the original commission is untouched
  - parallel-safe: no
  - depends: T6

- [ ] **T8 · The chain, its query and the organizer's view**
  - requirements: R8
  - files: `backend/booking-service/.../web/graphql/query/TransferQueryResolver.java`
  - verify: three transfers read correctly from either end; a buyer sees only their own
  - parallel-safe: yes
  - depends: T3

- [ ] **T9 · The subgraph half; the narrow preview type**
  - requirements: R2
  - files: `backend/booking-service/src/main/resources/graphql/schema.graphqls`
  - verify: `TransferPreview` exposes exactly six fields; `compose-supergraph.sh --static`
  - parallel-safe: no — shared SDL across booking's specs
  - depends: T7

## 6. Out of scope

| Capability | Spec |
|---|---|
| The QR, its signing and the re-issue this spec calls | [ET-TKT-002](../002-ticket-issuance-and-qr/) |
| Validation, which refuses a pending transfer | [ET-TKT-003](../003-validation-and-checkin/) |
| Collecting the resale payment | [ET-PAY-001](../../payment/001-payment-intents-and-providers/) |
| The chart of accounts this spec adds `2040` to | [ET-FIN-001](../../finance/001-escrow-and-ledger/) |
| Commission on the resale | [ET-FIN-002](../../finance/002-commission/) |
| Paying out the seller's proceeds | [ET-FIN-003](../../finance/003-payouts-and-settlement/) |
| Refunds, which follow `purchasedById` and not the current owner | [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) |
| Notifying sender and recipient | [ET-NTF-002](../../notification/002-lifecycle-triggers/) |

Deliberately never in scope: **transfer without QR rotation** (the sender's screenshot
still admits), **resale above face value** (every argument for it is an argument for
scalping), and **off-platform resale settlement** (two strangers, no recourse, and the
platform blamed anyway).
