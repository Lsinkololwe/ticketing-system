# ET-TKT-001 · Reservation, the inventory hold, and the purchase workflow

> **Deferred 2026-10-05: virtual queue for hot events (`events/[id]/queue`).** Not built. A fair queue needs an admission token service in front
> of `reserveTickets`, a Redis sorted-set of waiters with positions pushed over a subscription, an admission rate tied to remaining inventory, and an
> anti-bot policy; none of those is specified, and a queue without them is a lottery for bots. Until the product owner decides the admission policy,
> the platform relies on the inventory compare-and-set and the hold ttl (R1-R5) and the per-user reservation rate limit. No schema or data is added.

> **Conformance** · PDI Phase 2 transaction architecture · PDI Phase 7 saga state machine, run as a Temporal workflow ([ET-PLT-015](../../_platform/015-durable-execution/), D-21) · V3 §4.1 purchase journey · US Part III §16 US-BUY-002

> **Amended 2026-10-04 (D-38, D-39, D-42, D-43; F-044).** Buy first, sign in second: the buyer browses and chooses tickets signed out,
> proves a contact (WhatsApp number or email, [ET-IDN-001](../../identity/001-phone-otp-identity/)) at checkout, and **the hold is created
> only after that verification**, for an ACTIVE account ([ET-IDN-004](../../identity/004-accounts-and-contacts/)). Payment is mobile money
> only (D-42). R9 below states the new precondition; where this spec's earlier text says the buyer is "authenticated" when reserving, it
> means *verified and ACTIVE*.

## 1. Capability

Between *I want this ticket* and *I have this ticket* sits a mobile-money payment that
takes somewhere between eight seconds and four minutes, depends on a subscriber answering a
prompt on their handset, and fails perhaps one time in six. The platform cannot hold the
buyer's place by optimism and it cannot take the money before knowing the seat exists. What
it can do is **reserve**: take the inventory out of circulation for ten minutes, attempt
the payment, and either convert the reservation into a ticket or give the inventory back.

That reservation is the pivot of the whole platform. It is the document the on-sale minute
contends over, the thing that makes overselling possible if it is wrong, and the head of
the process that ends in a ticket, an escrow credit and a commission record. This spec builds
it: the five states a reservation may hold, the atomic hold that creates it, the ten-minute
expiry that releases it, and the **purchase workflow** — one durable Temporal execution per
purchase — that carries it to a ticket or back to nothing.

The property everything else depends on is that inventory is conserved. A reservation that
fails halfway must give back exactly what it took, whether it failed because the payment
declined, the process died, the provider never answered, or the buyer closed the app and
went to bed. A hold ends by confirmation, by cancellation, or by its workflow's expiry timer,
and every one of those is a compare-and-set, because two of them can race for the same
reservation.

## 2. Design decisions

**Reserve first, pay second.** The alternative — charge and then find a seat — is how a
platform ends up owing refunds for tickets it never had. The hold costs ten minutes of
inventory and buys certainty about what is being sold.

**Ten minutes, and the number is a trade.** Mobile-money confirmation in-market has a long
tail: a subscriber whose handset is off, who is in a meeting, or who mistypes their PIN
twice. Two minutes releases inventory while people are still paying; thirty minutes lets a
hundred abandoned checkouts strangle a hot tier. Ten covers the confirmation distribution
with margin and is short enough that a sold-out tier recovers within one buyer's patience.

**The purchase is one workflow, named by the purchase.** Each purchase is a
`PurchaseWorkflow` execution with id `purchase/{reservationId}`, where the reservation id is a
name-based UUID of the buyer and their idempotency key. `reserveTickets` reaches it by
Update-with-Start under conflict policy `USE_EXISTING`, so a double tap, a retried request and a
reload all arrive at the same execution. The workflow holds the sequence, the timers and the
recovery; activities do every write.

**The workflow owns the clock; MongoDB owns the truth.** The ten-minute expiry, the payment
polls and the escalation are workflow timers. They fire whether or not the pod that set them is
alive, with no sweep and no lock. What a buyer or an operator reads is the reservation and
payment-intent documents, which activities advance by compare-and-set — the workflow's own state
is never served to a screen.

**Release is a compare-and-set, so every path may repeat.** Confirmation, cancellation and the
expiry timer can race; an activity can run twice after a worker dies. Only the writer that moves
the status out of `HELD` touches the counters, and a repeated release changes nothing. The TTL
index on `expiresAt` is a backstop that deletes documents long after any release could still be
in flight — it frees no inventory and is never the release mechanism.

**The workflow never infers a payment's outcome.** The provider's callback, the workflow's own
status polls and operator recovery all go through one verification against the provider, so they
converge on one transition. A callback only wakes the workflow sooner.

**Once the prompt is sent, the buyer cannot cancel.** A payment in flight may still succeed; a
buyer cancelling at that moment is how someone is charged for seats that were released. Only the
provider's answer — or an operator — ends it.

**Seats come back at expiry plus five minutes; the money is watched for a week.** A buyer approving
the prompt in the last seconds is not refused, but a payment the provider has not answered cannot
hold a tier hostage: the seats return, the workflow keeps polling, and money that arrives after the
release is escalated for refund rather than taking back seats already resold.

**The reservation quotes the price, and the price does not move under the buyer.** Unit
price, quantity, discount and total are computed at reservation time from the inventory
mirror and written onto the reservation. A tier price change during those ten minutes does
not change what this buyer pays. The quote is what the payment intent charges and what the
ticket records.

**The idempotency key belongs to the purchase, not to the reservation.** A buyer who taps
*pay* twice must get one reservation and one charge. The key is supplied by the client
([ET-PLT-007](../../_platform/007-security-and-authorization/) R6), carried on
`reserveTickets`, and names the workflow execution — the same key reaches the same purchase.

**Confirmation is a single transaction, and it is the moment the platform commits.**
Moving `reserved → sold`, writing the tickets, crediting escrow, recording commission, marking
the reservation `CONFIRMED` and staging `booking.TicketPurchased` in `booking_outbox` happen in
one activity's transaction. If any part fails, none of it happened.

**A buyer may hold one reservation per tier at a time.** A second `reserveTickets` for a
tier the buyer already holds returns the existing reservation rather than stacking holds.
Otherwise a buyer with a flaky connection accumulates four holds and locks out four other
buyers while paying for one.

**Rejected alternatives**

- *Charging before reserving.* Refunds for tickets that never existed.
- *Holding inventory optimistically with no document.* Nothing to expire, nothing to recover, nothing to audit.
- *An expiry sweep on a schedule, under a Redis lock.* It fires only while some pod runs it, needs a lock so two pods do not, and a missed run holds seats with nobody accountable.
- *Serving workflow state to screens.* History expires after the namespace's retention and is not the record; the documents are.
- *Relying on the TTL index to release inventory.* It deletes the reservation and frees nothing.
- *Re-reading the tier price at confirmation.* The buyer agreed to a number; honour it.
- *A server-generated idempotency key.* The retry that needs the key is the one where the client never saw a response.
- *Allowing multiple concurrent holds per buyer per tier.* One flaky connection locks out four buyers.
- *Extending a hold when the buyer asks.* Every buyer asks, and the tier never recovers.

## 3. Requirements

### ET-TKT-001-R1 · A reservation takes inventory atomically or not at all

WHEN a buyer reserves, THE SYSTEM SHALL take the inventory in one conditional atomic
update, and IF it cannot, THEN THE SYSTEM SHALL refuse and write nothing.

**Acceptance**
- [ ] The hold is the single `findAndModify` of [ET-CAT-002](../../catalog/002-ticket-tiers-and-inventory/) §4 — no read-modify-write
- [ ] The reservation document is written in the **same transaction** as the counter movement, inside the `hold` activity
- [ ] A failure to take inventory refuses with `TIER_SOLD_OUT` or `TIER_NOT_ON_SALE` and persists no reservation
- [ ] `Persistence.assertNothingPersisted("booking_reservations")` holds on every refusal path
- [ ] `Inventory.assertConserved(tierId)` holds after every attempt, successful or not
- [ ] 200 parallel reservations against 50 available yield exactly 50 reservations and 150 refusals
- [ ] A multi-tier order takes every tier's inventory in one transaction, and a failure on the second tier returns the first
- [ ] A hold that fails after taking some inventory runs the `abandonHold` compensation, which returns what was taken

### ET-TKT-001-R2 · Five states, and the transitions are enumerated

THE SYSTEM SHALL admit exactly the five reservation states and seven transitions of §4.

**Acceptance**
- [ ] `ReservationStatus` declares exactly `HELD`, `CONFIRMED`, `EXPIRED`, `RELEASED`, `FAILED`
- [ ] `ReservationTransitions.LEGAL` holds exactly 7 rows and equals §4 row for row
- [ ] `CONFIRMED`, `EXPIRED`, `RELEASED` and `FAILED` are terminal
- [ ] An operation against a terminal reservation is refused with `RESERVATION_STATE_INVALID` carrying `currentStatus`
- [ ] A test drives all 30 `(status, action)` pairs
- [ ] No service sets a reservation status by literal assignment

### ET-TKT-001-R3 · The quote is fixed at reservation and honoured at confirmation

WHEN a reservation is created, THE SYSTEM SHALL compute and record the price, and the
confirmation SHALL charge exactly that.

**Acceptance**
- [ ] The reservation records `unitPrice`, `quantity`, `subtotal`, `discountAmount`, `totalAmount` and `currency`, all `Decimal128`
- [ ] The quote is computed from `booking_tier_inventory`'s mirrored price, never from a catalog call
- [ ] Rounding is `HALF_UP` at scale 2, applied once, to `totalAmount`
- [ ] A tier price change after the reservation does not alter it, asserted by a test
- [ ] The payment intent charges `totalAmount` exactly; a mismatch is `PAYMENT_AMOUNT_MISMATCH`
- [ ] A promo code applied at reservation consumes its redemption then, and releases it if the reservation does not confirm ([ET-CAT-002](../../catalog/002-ticket-tiers-and-inventory/) R6)

### ET-TKT-001-R4 · A hold expires in ten minutes and the inventory comes back

WHILE a reservation is `HELD` past its `expiresAt` with no payment started, THE SYSTEM SHALL
release its inventory through the purchase workflow's timer, and every release path SHALL be
idempotent.

**Acceptance**
- [ ] `expiresAt` is `clock.instant() + booking.reservation.ttl` (PT10M)
- [ ] The reservation's `PurchaseWorkflow` sleeps until `expiresAt` and, with no payment started and no cancellation, runs `release(EXPIRE)`
- [ ] No `@Scheduled` method, Redis lock or sweep releases reservations
- [ ] The TTL index on `expiresAt` fires at `ttl + booking.reservation.ttl-grace` (PT1H10M) — after the seat grace and the TTL monitor's own period — so it only deletes reservations already terminal
- [ ] Releasing an already-released reservation is a no-op, not an error
- [ ] `Inventory.assertConserved(tierId)` holds after a release, after a TTL deletion, and after both
- [ ] A test releases the same 100 reservations twice concurrently and asserts each tier's counters are exactly correct
- [ ] A time-skipping workflow test asserts a reservation is live at 9:59 and `EXPIRED` at 10:01

### ET-TKT-001-R5 · One buyer, one live hold per tier, and the limits are enforced

WHEN a buyer reserves, THE SYSTEM SHALL enforce the tier's purchase limits and SHALL NOT
create a second concurrent hold for that buyer and tier.

**Acceptance**
- [ ] `maxPerOrder` and `maxPerBuyer` are enforced per [ET-CAT-002](../../catalog/002-ticket-tiers-and-inventory/) R5, refusing with `PURCHASE_LIMIT_EXCEEDED`
- [ ] `alreadyHeld` counts `HELD` reservations plus `PURCHASED` and `VALIDATED` tickets
- [ ] A second `reserveTickets` for a tier the buyer already holds returns the existing reservation, unchanged, rather than creating a second
- [ ] A partial unique index on `{ userId, tierId }` where `status = HELD` enforces this at the database, not by a check
- [ ] Two parallel reservations by one buyer for one tier produce exactly one hold
- [ ] The limit counts across the buyer's identity, not their device or session

### ET-TKT-001-R9 · A hold exists only for a verified, ACTIVE buyer *(added 2026-10-04)*

WHEN a buyer reserves, THE SYSTEM SHALL require a signed-in buyer whose account is ACTIVE, and SHALL NOT hold inventory for an anonymous or unverified session.

**Acceptance**
- [ ] `reserveTickets` refuses with `ACTOR_NOT_AUTHENTICATED` without a valid buyer token, and with `ACCOUNT_NOT_ACTIVE`, `ACCOUNT_SUSPENDED` or `ACCOUNT_MERGING` for an account in those states (the booking service confirms ACTIVE through `GET /api/internal/auth/accounts/{id}/status`, or the token's account claim plus the revocation check)
- [ ] The flow order is: choose tickets (no inventory touched) -> prove contact -> account ensured -> `reserveTickets` -> pay; a test shows no hold exists before the proof
- [ ] The 10-minute hold clock starts at reservation, not at choosing tickets, so a slow code does not burn the hold
- [ ] Browsing tiers and availability is public and creates no reservation
- [ ] The limit counts across the buyer's account (R5), which a contact change or a merge preserves

### ET-TKT-001-R6 · The purchase is idempotent under retry

WHEN a purchase carries an idempotency key already seen, THE SYSTEM SHALL return the
original outcome rather than reserving again.

**Acceptance**
- [ ] `reserveTickets` takes a non-null `idempotencyKey: String!`
- [ ] The workflow id is `purchase/{reservationId}`, the reservation id `UUID.nameUUIDFromBytes("purchase:" + userId + ":" + idempotencyKey)` — two buyers reusing one key never collide
- [ ] `reserveTickets` is Update-with-Start under `WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING`; a repeat with the same key reaches the same execution and returns the original reservation
- [ ] A repeat with the same key and a different fingerprint is refused with `IDEMPOTENCY_KEY_REUSED`
- [ ] Two parallel submissions of one key produce exactly one execution, one reservation and one hold
- [ ] The fingerprint covers `tierId`, `quantity`, `promoCode` and `userId`, and nothing else

### ET-TKT-001-R7 · Confirmation is one transaction, and failure compensates

WHEN a verified payment succeeds, THE SYSTEM SHALL convert the reservation into tickets, escrow
and commission in one transaction, and IF any part fails, THEN THE SYSTEM SHALL refund and
release.

**Acceptance**
- [ ] Confirmation moves `reserved → sold`, writes one `booking_tickets` row per seat, credits the event escrow, records the commission and sets `CONFIRMED` — all in one MongoDB transaction, in one activity
- [ ] `booking.TicketPurchased` is staged into `booking_outbox` inside that transaction ([ET-PLT-003](../../_platform/003-event-contract/) R1); no step calls `StreamBridge`
- [ ] A forced failure at each of those five points leaves none of them applied, asserted by five tests
- [ ] A confirmation that cannot complete triggers a refund of the captured payment and releases the hold, and the reservation becomes `FAILED`
- [ ] Confirming an already-`CONFIRMED` reservation is a no-op returning the existing tickets
- [ ] `Ledger.assertBalanced()` and `Inventory.assertConserved()` both hold after every confirmation
- [ ] Money that arrives after the seats were released confirms nothing and is escalated as `PAID_AFTER_EXPIRY` for refund

### ET-TKT-001-R8 · The purchase survives a crash because its workflow does

IF a worker, a pod or the whole service terminates mid-purchase, THEN THE SYSTEM SHALL carry
every purchase on from its last recorded step, and SHALL resolve every reservation to a terminal
state or an escalation.

**Acceptance**
- [ ] A purchase's process state is its `PurchaseWorkflow` execution; `booking_reservations` and `booking_payment_intents` are its projection, and no saga collection exists
- [ ] Every activity is idempotent by compare-and-set: a retried `hold`, `startPayment`, `verify`, `release` or `escalatePending` finds its work done and returns what is stored
- [ ] A recorded purchase history replays against the current implementation with `WorkflowReplayer`
- [ ] While a payment is in flight the workflow polls the provider at 10 s doubling to 5 min; a callback wakes it sooner; the outcome comes only from `PaymentOutcomeService.verifyAndApply`
- [ ] A `HELD` reservation whose verified intent is `SUCCEEDED` is confirmed; one whose intent is `FAILED` is released
- [ ] At `expiresAt + seat-grace` (PT5M) with the payment still pending, the seats are released and the workflow keeps polling
- [ ] A payment pending past `booking.payment.max-pending` (PT30M) is escalated to [ET-ADM-003](../../admin/003-transaction-recovery/) and polled hourly — never silently released as paid or unpaid; polling stops after 7 days with the escalation standing
- [ ] A `HELD` reservation with no running execution is adopted by `PurchaseAdoptionRunner` at boot and carried on like any other
- [ ] No reservation remains `HELD` for longer than `ttl + seat-grace`

## 4. Model

### The document

`booking_reservations`

| Field | Type | Notes |
|---|---|---|
| `_id` | `String` | the name-based UUID of buyer and idempotency key |
| `userId`, `eventId` | `String` | |
| `items` | `List<ReservationItem>` | `{ tierId, quantity, unitPrice, lineTotal }` |
| `subtotal`, `discountAmount`, `totalAmount` | `BigDecimal` | `Decimal128` |
| `currency` | `String` | `ZMW` |
| `promoCodeId` | `String` | nullable |
| `status` | `ReservationStatus` | the five |
| `paymentIntentId` | `String` | nullable until an intent exists |
| `idempotencyKey` | `String` | |
| `expiresAt` | `Instant` | **TTL index at `ttl + grace`** |
| `confirmedAt`, `releasedAt`, `failedAt` | `Instant` | |
| `failureReason` | `String` | |
| `createdAt`, `updatedAt`, `version` | | `@Version` |

A reservation may span tiers of **one** event. A basket across events is not supported: the
escrow is per event (D-05), so a cross-event basket is two independent purchases wearing
one button.

### The state machine

| # | From | Action | To | Actor |
|---|---|---|---|---|
| 1 | — | `reserveTickets` | `HELD` | buyer, through the workflow's `reserve` update |
| 2 | `HELD` | `confirmPurchase` | `CONFIRMED` | verified payment success |
| 3 | `HELD` | `cancelReservation` | `RELEASED` | buyer before payment, or an operator |
| 4 | `HELD` | `expireReservation` | `EXPIRED` | the workflow's expiry timer |
| 5 | `HELD` | `releaseReservation` | `RELEASED` | verified payment failure, or the seat grace |
| 6 | `HELD` | `failReservation` | `FAILED` | confirmation failure |
| 7 | `CONFIRMED` | *(none)* | — | terminal |

Five states, six actions: 30 pairs, of which 7 are legal (counting the null origin).
Every one of `CONFIRMED`, `EXPIRED`, `RELEASED`, `FAILED` is terminal.

`EXPIRED` and `RELEASED` are distinct because the reports differ: *nobody paid in time* and
*the payment failed* are different problems, and a platform that records both as released
cannot tell a payments outage from a slow checkout.

### Indexes beyond [ET-PLT-002](../../_platform/002-persistence-baseline/) §4

| Collection | Index | Kind | Why |
|---|---|---|---|
| `booking_reservations` | `{ userId: 1, tierId: 1 }` where `status = HELD` | **partial unique** | R5 — one live hold per buyer per tier |
| `booking_reservations` | `{ status: 1, expiresAt: 1 }` | compound | R8 — boot adoption and the recovery queue read `HELD` reservations by expiry |

### The purchase workflow

| | |
|---|---|
| Type | `PurchaseWorkflow` |
| Id | `purchase/{reservationId}` |
| Task queue | `booking-checkout`; provider calls on `booking-provider` ([ET-PLT-015](../../_platform/015-durable-execution/) §4) |
| Start | Update-with-Start `reserve`, conflict policy `USE_EXISTING` |
| Updates | `reserve(ReserveCommand)`, `pay(PayCommand)` with validator, `cancel(CancelCommand)` with validator |
| Signal | `paymentCallback(Evidence)` — a verified deposit webhook wakes the poll |
| Query | `stage()` |
| Reached through | `PurchaseProcess`; `PurchaseAdoptionRunner` at boot |

```
reserve ──▶ hold (activity, 10 s, 3 attempts) ──▶ HELD            refused ─▶ abandonHold ─▶ closed
  │
  ├── timer to expiresAt, no payment ─────────────────────────────▶ release(EXPIRE) ─▶ EXPIRED
  ├── cancel before payment ──────────────────────────────────────▶ release(CANCEL) ─▶ RELEASED
  └── pay ─▶ startPayment ─▶ verify: 10 s, 20 s, 40 s … 5 min, or sooner on callback
                │  SUCCEEDED ─▶ confirm, one transaction, TicketPurchased staged ─▶ CONFIRMED
                │  FAILED    ─▶ release ─────────────────────────────────────────▶ RELEASED
                ├─ expiresAt + 5 min, still pending ─▶ seats released; polling continues
                ├─ pending 30 min ─▶ escalatePending (ET-ADM-003); polls go hourly
                ├─ success after release ─▶ PAID_AFTER_EXPIRY escalation
                └─ 7 days unanswered ─▶ polling stops; the escalation stands
```

A started execution that receives no reservation within 5 minutes closes. `cancel` is refused
with `RESERVATION_STATE_INVALID` (reason `PAYMENT_IN_FLIGHT`) once a payment has started, unless
the command is administrative.

### Confirmation, in one transaction

```java
// the confirm step of the verify activity: one transaction, the outbox row inside it
public Mono<List<Ticket>> confirm(String reservationId) {
    return reservations.findHeld(reservationId)
        .switchIfEmpty(Mono.error(new ReservationNotInExpectedState(reservationId)))
        .flatMap(r -> inventory.confirmAll(r.items())                // reserved → sold
            .then(tickets.issueAll(r))                               // ET-TKT-002
            .flatMap(issued -> escrow.credit(r.eventId(), r.netAmount())   // ET-FIN-001
                .then(commission.recordPending(r))                          // ET-FIN-002
                .then(reservations.markConfirmed(r, clock.instant()))
                .then(outbox.stage(TicketPurchased.of(r, issued)))          // ET-PLT-003 R1
                .thenReturn(issued)))
        .as(transactionalOperator::transactional);
}
```

The drain publishes the staged row afterwards, outside any transaction
([ET-PLT-003](../../_platform/003-event-contract/) §2).

### Release, idempotent by construction

```java
// confirmation, cancellation and the expiry timer may race; a retried activity may repeat
public Mono<Void> release(Reservation r, ReservationStatus terminal) {
    return reservations.compareAndSetStatus(r.id(), HELD, terminal, clock.instant())
        .flatMap(moved -> moved                       // false: somebody else released it
            ? inventory.releaseAll(r.items()).then(promo.release(r.promoCodeId()))
            : Mono.empty());
}
```

Only the writer that wins the compare-and-set touches the counters. A second release does
nothing and reports success.

### GraphQL

Subgraph `booking`. Every field carries `@auth` explicitly.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `reservation(id)` | query | `AUTHENTICATED` | `Reservation` |
| `myActiveReservations` | query | `AUTHENTICATED` | `[Reservation!]!` — bounded, ≤ 20 |
| `reserveTickets(input)` | mutation | `CUSTOMER` | `Reservation!` |
| `cancelReservation(id)` | mutation | `CUSTOMER` | `Reservation!` |

```graphql
input ReserveTicketsInput {
    eventId: ID!
    items: [ReservationItemInput!]!     # tierId + quantity, one event only
    promoCode: String
    idempotencyKey: String!             # ET-PLT-007 R6; names the workflow execution
}
```

`confirmPurchase` is **not** a client mutation — confirmation is driven by the verified payment
outcome ([ET-PAY-002](../../payment/002-webhooks-and-settlement/)), never by a client
asserting that it paid. No mutation starts, verifies, expires or fulfils a payment outside the
purchase workflow.

### Events

| Tier | Name | When | Consumers |
|---|---|---|---|
| bus | `booking.TicketPurchased` v1 | staged in the confirmation transaction | catalog → sold counters; identity → notify |

In-service steps are workflow activities, not in-memory events.

### Redis keys

None. Idempotency is the workflow id and the unique index; expiry is a workflow timer.

### Configuration

| Property | Value |
|---|---|
| `booking.reservation.ttl` | `PT10M` |
| `booking.reservation.ttl-grace` | `PT1H` — the TTL index fires this much after `expiresAt` |
| `booking.payment.max-pending` | `PT30M` — beyond this, escalate rather than release |

Workflow constants, in `PurchaseRules`: seat grace `PT5M`, reserve window `PT5M`, polls 10 s
doubling to `PT5M` and `PT1H` once escalated, give-up `P7D`.

### Error codes

`RESERVATION_UNKNOWN`, `RESERVATION_EXPIRED`, `RESERVATION_STATE_INVALID`,
`PURCHASE_LIMIT_EXCEEDED` — rows of
[ET-PLT-005 §4](../../_platform/005-error-contract/) introduced by this spec.
`TIER_SOLD_OUT`, `TIER_NOT_ON_SALE`, `IDEMPOTENCY_KEY_REUSED` are raised here and owned
elsewhere.

## 5. Tasks

- [ ] **T1 · The document, the state machine, the 30-pair test**
  - requirements: R2
  - files: `backend/booking-service/.../domain/`
  - verify: 7 legal rows, 23 refusals, no status literal
  - parallel-safe: no
  - depends: —

- [ ] **T2 · The `hold` activity: the atomic hold and the reservation, in one transaction**
  - requirements: R1, R3
  - files: `backend/booking-service/.../service/impl/ReservationServiceImpl.java`, `.../workflow/purchase/CheckoutActivitiesImpl.java`
  - verify: 200-against-50; a multi-tier failure returns the first tier; nothing persists on refusal
  - parallel-safe: no — the platform's most contended write
  - depends: T1

- [ ] **T3 · The quote, its rounding, and the price-change immunity test**
  - requirements: R3
  - files: `backend/booking-service/.../domain/Quote.java`
  - verify: a tier price change mid-hold does not alter the reservation
  - parallel-safe: yes
  - depends: T2

- [ ] **T4 · The workflow id, Update-with-Start, and the partial unique hold index**
  - requirements: R5, R6
  - files: `.../workflow/purchase/PurchaseProcess.java`, `.../workflow/purchase/PurchaseRules.java`, `.../config/BookingIndexInitializer.java`
  - verify: two parallel reservations by one buyer produce one hold; a repeated key reaches the original execution
  - parallel-safe: yes
  - depends: T2

- [ ] **T5 · The expiry timer, the TTL backstop, the double-release test**
  - requirements: R4
  - files: `backend/booking-service/.../workflow/purchase/PurchaseWorkflowImpl.java`
  - verify: time-skipping test live at 9:59, expired at 10:01; two concurrent releases over 100 reservations conserve exactly
  - parallel-safe: no
  - depends: T2

- [ ] **T6 · Confirmation in one transaction; the five forced-failure tests**
  - requirements: R7
  - files: `backend/booking-service/.../service/PaymentOutcomeService.java`
  - verify: a failure at each of the five points applies none of them; the outbox row commits with the tickets
  - parallel-safe: no — spans tickets, escrow and commission
  - depends: T3

- [ ] **T7 · The payment polls, the seat grace, escalation, adoption and replay**
  - requirements: R8
  - files: `.../workflow/purchase/PurchaseWorkflowImpl.java`, `.../workflow/purchase/PurchaseAdoptionRunner.java`
  - verify: layer-3 tests for each branch of §4's diagram; the recorded history replays
  - parallel-safe: no
  - depends: T6

- [ ] **T8 · The subgraph half; `@auth` on every field**
  - requirements: R1–R8
  - files: `backend/booking-service/src/main/resources/graphql/schema.graphqls`
  - verify: `compose-supergraph.sh --static`; no `confirmPurchase` client mutation and no payment-lifecycle mutation exist
  - parallel-safe: no — shared SDL across booking's specs
  - depends: T6

## 6. Out of scope

| Capability | Spec |
|---|---|
| Tier definition, counters and the conditional atomic update | [ET-CAT-002](../../catalog/002-ticket-tiers-and-inventory/) |
| Taking the money, the provider port, the intent | [ET-PAY-001](../../payment/001-payment-intents-and-providers/) |
| The provider callback that wakes the workflow | [ET-PAY-002](../../payment/002-webhooks-and-settlement/) |
| Issuing the ticket and its QR identity | [ET-TKT-002](../002-ticket-issuance-and-qr/) |
| The escrow credit and the journal pair | [ET-FIN-001](../../finance/001-escrow-and-ledger/) |
| Recording and later recognising the commission | [ET-FIN-002](../../finance/002-commission/) |
| Refunding a confirmed purchase | [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) |
| Escalating a stuck reservation to an operator | [ET-ADM-003](../../admin/003-transaction-recovery/) |
| Temporal itself: namespace, queues, determinism, replay | [ET-PLT-015](../../_platform/015-durable-execution/) |

Deliberately never in scope: **a client-callable `confirmPurchase`** (a client asserting it
paid), **cross-event baskets** (the escrow is per event, so it is two purchases wearing one
button), and **hold extension on request** (every buyer asks, and the tier never recovers).
