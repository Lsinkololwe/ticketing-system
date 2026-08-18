# ET-TKT-001 · Reservation, the inventory hold, and the purchase saga

> **Conformance** · PDI Phase 2 transaction architecture · PDI Phase 7 saga state machine · V3 §4.1 purchase journey · US Part III §16 US-BUY-002

## 1. Capability

Between *I want this ticket* and *I have this ticket* sits a mobile-money payment that
takes somewhere between eight seconds and four minutes, depends on a subscriber answering a
prompt on their handset, and fails perhaps one time in six. The platform cannot hold the
buyer's place by optimism and it cannot take the money before knowing the seat exists. What
it can do is **reserve**: take the inventory out of circulation for ten minutes, attempt
the payment, and either convert the reservation into a ticket or give the inventory back.

That reservation is the pivot of the whole platform. It is the document the on-sale minute
contends over, the thing that makes overselling possible if it is wrong, and the head of
the saga that ends in a ticket, an escrow credit and a commission record. This spec builds
it: the five states a reservation may hold, the atomic hold that creates it, the ten-minute
expiry that releases it, and the orchestration that carries it to a ticket or back to
nothing.

The property everything else depends on is that inventory is conserved. A reservation that
fails halfway must give back exactly what it took, whether it failed because the payment
declined, the process died, the provider never answered, or the buyer closed the app and
went to bed. There are four ways a hold is released and all four must be idempotent,
because at least two of them will fire for the same reservation.

## 2. Design decisions

**Reserve first, pay second.** The alternative — charge and then find a seat — is how a
platform ends up owing refunds for tickets it never had. The hold costs ten minutes of
inventory and buys certainty about what is being sold.

**Ten minutes, and the number is a trade.** Mobile-money confirmation in-market has a long
tail: a subscriber whose handset is off, who is in a meeting, or who mistypes their PIN
twice. Two minutes releases inventory while people are still paying; thirty minutes lets a
hundred abandoned checkouts strangle a hot tier. Ten covers the confirmation distribution
with margin and is short enough that a sold-out tier recovers within one buyer's patience.

**Four release paths, all idempotent, and that is deliberate not accidental.** A hold ends
by confirmation, by explicit cancellation, by the TTL index, or by the sweep. The TTL index
and the sweep will both fire for the same expired reservation — MongoDB's TTL runs on its
own schedule and the sweep runs on the platform's — so *release* is written as an operation
that is safe to apply twice and asserts conservation afterwards.

**The TTL index deletes; the sweep is what actually releases.** MongoDB's TTL monitor
removes the document, which frees nothing — the counters live in
`booking_tier_inventory`. So the sweep is the mechanism, and the TTL index is set to a
**longer** window than the sweep's so it only ever cleans up rows the sweep has already
released. Getting that order wrong loses the inventory permanently, which is why it is
stated here rather than left to an implementer's judgement.

**The saga's state is the reservation's own status.** A separate saga collection would be a
second document to keep in step with the first. `ReservationStatus` plus the payment
intent's status is the whole state machine, and recovery reads them.

**The reservation quotes the price, and the price does not move under the buyer.** Unit
price, quantity, discount and total are computed at reservation time from the inventory
mirror and written onto the reservation. A tier price change during those ten minutes does
not change what this buyer pays. The quote is what the payment intent charges and what the
ticket records.

**The idempotency key belongs to the purchase, not to the reservation.** A buyer who taps
*pay* twice must get one reservation and one charge. The key is supplied by the client
([ET-PLT-007](../../_platform/007-security-and-authorization/) R6), carried on
`reserveTickets`, and the same key returns the same reservation rather than a second hold.

**Confirmation is a single transaction, and it is the moment the platform commits.**
Moving `reserved → sold`, writing the tickets, crediting escrow, recording commission and
marking the reservation `CONFIRMED` happen together. If any part fails, none of it happened
and the payment is refunded by the compensation path — the platform never issues a ticket
it did not account for.

**A buyer may hold one reservation per tier at a time.** A second `reserveTickets` for a
tier the buyer already holds returns the existing reservation rather than stacking holds.
Otherwise a buyer with a flaky connection accumulates four holds and locks out four other
buyers while paying for one.

**Rejected alternatives**

- *Charging before reserving.* Refunds for tickets that never existed.
- *Holding inventory optimistically with no document.* Nothing to expire, nothing to recover, nothing to audit.
- *A separate saga-state collection.* A second document to keep in step with the first, for state the first already holds.
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
- [ ] The reservation document is written in the **same transaction** as the counter movement
- [ ] A failure to take inventory refuses with `TIER_SOLD_OUT` or `TIER_NOT_ON_SALE` and persists no reservation
- [ ] `Persistence.assertNothingPersisted("booking_reservations")` holds on every refusal path
- [ ] `Inventory.assertConserved(tierId)` holds after every attempt, successful or not
- [ ] 200 parallel reservations against 50 available yield exactly 50 reservations and 150 refusals
- [ ] A multi-tier order takes every tier's inventory in one transaction, and a failure on the second tier returns the first

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

WHILE a reservation is `HELD` past its `expiresAt`, THE SYSTEM SHALL release its inventory,
and every release path SHALL be idempotent.

**Acceptance**
- [ ] `expiresAt` is `clock.instant() + booking.reservation.ttl` (PT10M)
- [ ] A sweep under `lock:sweep:reservation-expiry` runs every `booking.reservation.sweep-interval` (PT30S), claims rows `ORDER BY _id FOR UPDATE SKIP LOCKED LIMIT 500`, and releases each
- [ ] The TTL index on `expiresAt` is configured to fire **after** the sweep window — `expireAfterSeconds` corresponds to `ttl + booking.reservation.ttl-grace` (PT1H) — so it only removes rows the sweep has already released
- [ ] Releasing an already-released reservation is a no-op, not an error
- [ ] `Inventory.assertConserved(tierId)` holds after a sweep, after a TTL deletion, and after both
- [ ] A test expires 100 reservations, runs the sweep twice concurrently, and asserts each tier's counters are exactly correct
- [ ] A frozen-clock test asserts a reservation is live at 9:59 and expired at 10:01

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

### ET-TKT-001-R6 · The purchase is idempotent under retry

WHEN a purchase carries an idempotency key already seen, THE SYSTEM SHALL return the
original outcome rather than reserving again.

**Acceptance**
- [ ] `reserveTickets` takes a non-null `idempotencyKey: String!`
- [ ] A repeat with the same key and the same request fingerprint returns the original reservation
- [ ] A repeat with the same key and a different fingerprint is refused with `IDEMPOTENCY_KEY_REUSED`
- [ ] The guard is [ET-PLT-007](../../_platform/007-security-and-authorization/) R6's — Redis `SET NX` plus the unique index on the persisted intent
- [ ] Two parallel submissions of one key produce exactly one reservation and one hold
- [ ] The fingerprint covers `tierId`, `quantity`, `promoCode` and `userId`, and nothing else

### ET-TKT-001-R7 · Confirmation is one transaction, and failure compensates

WHEN a payment completes, THE SYSTEM SHALL convert the reservation into tickets, escrow and
commission in one transaction, and IF any part fails, THEN THE SYSTEM SHALL refund and
release.

**Acceptance**
- [ ] Confirmation moves `reserved → sold`, writes one `booking_tickets` row per seat, credits the event escrow, records the commission and sets `CONFIRMED` — all in one MongoDB transaction
- [ ] A forced failure at each of those five points leaves none of them applied, asserted by five tests
- [ ] A confirmation that cannot complete triggers a refund of the captured payment and releases the hold, and the reservation becomes `FAILED`
- [ ] `booking.TicketPurchased` and `booking.PaymentCompleted` are published from an `@TransactionalEventListener(AFTER_COMMIT)`, after commit ([ET-PLT-003](../../_platform/003-event-contract/) R2)
- [ ] Confirming an already-`CONFIRMED` reservation is a no-op returning the existing tickets
- [ ] `Ledger.assertBalanced()` and `Inventory.assertConserved()` both hold after every confirmation
- [ ] A confirmation arriving after expiry is refused with `RESERVATION_EXPIRED` and the payment is refunded

### ET-TKT-001-R8 · The saga is recoverable from its own state

IF the process terminates mid-purchase, THEN THE SYSTEM SHALL resolve every reservation to
a terminal state on restart or by sweep.

**Acceptance**
- [ ] The saga's state is `ReservationStatus` plus the payment intent's status — no separate saga collection exists
- [ ] A recovery sweep resolves reservations whose payment intent is terminal but whose own status is still `HELD`
- [ ] A `HELD` reservation whose intent is `SUCCEEDED` is confirmed; one whose intent is `FAILED` is released
- [ ] A `HELD` reservation with no intent past its expiry is released
- [ ] A reservation stuck with a `PENDING` intent past `booking.payment.max-pending` is escalated to [ET-ADM-003](../../admin/003-transaction-recovery/), not silently released — the money may still arrive
- [ ] Killing the process after each of the six saga steps in turn resolves correctly on restart, asserted by six tests
- [ ] No reservation remains `HELD` for longer than `ttl + sweep-interval + max-pending`

## 4. Model

### The document

`booking_reservations`

| Field | Type | Notes |
|---|---|---|
| `_id` | `String` | |
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
| 1 | — | `reserveTickets` | `HELD` | buyer |
| 2 | `HELD` | `confirmPurchase` | `CONFIRMED` | payment success |
| 3 | `HELD` | `cancelReservation` | `RELEASED` | buyer |
| 4 | `HELD` | `expireReservation` | `EXPIRED` | sweep |
| 5 | `HELD` | `releaseReservation` | `RELEASED` | payment failure |
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
| `booking_reservations` | `{ status: 1, expiresAt: 1 }` | compound | the expiry sweep's claim query |

### The purchase saga

```
1  reserveTickets      inventory hold + reservation      [TRANSACTION]     → HELD
2  createPaymentIntent intent + provider call            ET-PAY-001
3  provider callback   webhook or poll                   ET-PAY-002
4a SUCCEEDED  → confirmPurchase                          [TRANSACTION]     → CONFIRMED
                sold += qty · tickets written · escrow credited
                · commission recorded · reservation CONFIRMED
4b FAILED     → releaseReservation                       [TRANSACTION]     → RELEASED
                reserved -= qty · available += qty · promo released
4c TIMEOUT    → sweep → escalate to ET-ADM-003                             → HELD, flagged
5  after commit        publish TicketPurchased, PaymentCompleted           ET-PLT-003
```

Steps 1 and 4 are transactions. Step 5 is an `@TransactionalEventListener(AFTER_COMMIT)`, so the bus is
never reached inside one.

### Confirmation, in one transaction

```java
@Transactional
public Mono<List<Ticket>> confirm(String reservationId) {
    return reservations.findHeld(reservationId)
        .switchIfEmpty(Mono.error(new ReservationNotInExpectedState(reservationId)))
        .flatMap(r -> requireNotExpired(r)                       // RESERVATION_EXPIRED
            .then(inventory.confirmAll(r.items()))               // reserved → sold
            .then(tickets.issueAll(r))                           // ET-TKT-002
            .flatMap(issued -> escrow.credit(r.eventId(), r.netAmount())   // ET-FIN-001
                .then(commission.recordPending(r))                          // ET-FIN-002
                .then(reservations.markConfirmed(r, clock.instant()))
                .doOnSuccess(x -> publisher.publishEvent(new TicketPurchasedEvent(r, issued)))
                .thenReturn(issued)));
}
```

The event is staged into `booking_outbox` inside the transaction, through the same reactive session, so it commits with the
write; `StreamBridge` is reached only from the listener that consumes it
([ET-PLT-003](../../_platform/003-event-contract/) §2).

### Release, idempotent by construction

```java
// safe to apply twice — the TTL index and the sweep will both fire for the same row
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
    idempotencyKey: String!             # ET-PLT-007 R6
}
```

`confirmPurchase` is **not** a client mutation — confirmation is driven by the payment
outcome ([ET-PAY-002](../../payment/002-webhooks-and-settlement/)), never by a client
asserting that it paid.

### Events

| Tier | Name | When | Consumers |
|---|---|---|---|
| module | `TicketReservedEvent` | step 1 | metrics, abandoned-checkout analytics |
| module | `TicketPurchasedEvent` | step 4a | the bus bridge, escrow, commission |
| module | `ReservationReleasedEvent` | steps 4b, 4c | promo release, metrics |
| bus | `booking.TicketPurchased` v1 | after commit | catalog → sold counters; identity → notify |

### Redis keys

| Key | TTL | Purpose |
|---|---|---|
| `idem:{key}` | 24 h | [ET-PLT-007](../../_platform/007-security-and-authorization/) §4 |
| `lock:sweep:reservation-expiry` | 30 s | the R4 sweep mutex |
| `lock:sweep:reservation-recovery` | 30 s | the R8 recovery mutex |

### Configuration

| Property | Value |
|---|---|
| `booking.reservation.ttl` | `PT10M` |
| `booking.reservation.sweep-interval` | `PT30S` |
| `booking.reservation.ttl-grace` | `PT1H` — the TTL index fires this much later than the sweep |
| `booking.reservation.sweep-batch` | 500 |
| `booking.payment.max-pending` | `PT30M` — beyond this, escalate rather than release |

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

- [ ] **T2 · `reserveTickets`: the atomic hold and the reservation, in one transaction**
  - requirements: R1, R3
  - files: `backend/booking-service/.../service/impl/ReservationServiceImpl.java`
  - verify: 200-against-50; a multi-tier failure returns the first tier; nothing persists on refusal
  - parallel-safe: no — the platform's most contended write
  - depends: T1

- [ ] **T3 · The quote, its rounding, and the price-change immunity test**
  - requirements: R3
  - files: `backend/booking-service/.../domain/Quote.java`
  - verify: a tier price change mid-hold does not alter the reservation
  - parallel-safe: yes
  - depends: T2

- [ ] **T4 · The partial unique hold index and the idempotency guard**
  - requirements: R5, R6
  - files: `.../config/MongoIndexInitializer.java`, `.../service/impl/`
  - verify: two parallel reservations by one buyer produce one hold; a repeated key returns the original
  - parallel-safe: yes
  - depends: T2

- [ ] **T5 · The expiry sweep, the TTL grace, the double-release test**
  - requirements: R4
  - files: `backend/booking-service/.../scheduler/ReservationExpirySweeper.java`
  - verify: two concurrent sweeps over 100 expired reservations conserve exactly
  - parallel-safe: no
  - depends: T2

- [ ] **T6 · Confirmation in one transaction; the five forced-failure tests**
  - requirements: R7
  - files: `backend/booking-service/.../service/impl/PurchaseServiceImpl.java`
  - verify: a failure at each of the five points applies none of them
  - parallel-safe: no — spans tickets, escrow and commission
  - depends: T3

- [ ] **T7 · The recovery sweep and the six kill-point tests**
  - requirements: R8
  - files: `backend/booking-service/.../scheduler/ReservationRecoverySweeper.java`
  - verify: killing after each saga step resolves correctly on restart
  - parallel-safe: no
  - depends: T6

- [ ] **T8 · The subgraph half; `@auth` on every field**
  - requirements: R1–R8
  - files: `backend/booking-service/src/main/resources/graphql/schema.graphqls`
  - verify: `compose-supergraph.sh --static`; no `confirmPurchase` client mutation exists
  - parallel-safe: no — shared SDL across booking's specs
  - depends: T6

## 6. Out of scope

| Capability | Spec |
|---|---|
| Tier definition, counters and the conditional atomic update | [ET-CAT-002](../../catalog/002-ticket-tiers-and-inventory/) |
| Taking the money, the provider port, the intent | [ET-PAY-001](../../payment/001-payment-intents-and-providers/) |
| The provider callback that drives confirmation | [ET-PAY-002](../../payment/002-webhooks-and-settlement/) |
| Issuing the ticket and its QR identity | [ET-TKT-002](../002-ticket-issuance-and-qr/) |
| The escrow credit and the journal pair | [ET-FIN-001](../../finance/001-escrow-and-ledger/) |
| Recording and later recognising the commission | [ET-FIN-002](../../finance/002-commission/) |
| Refunding a confirmed purchase | [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) |
| Escalating a stuck reservation to an operator | [ET-ADM-003](../../admin/003-transaction-recovery/) |

Deliberately never in scope: **a client-callable `confirmPurchase`** (a client asserting it
paid), **cross-event baskets** (the escrow is per event, so it is two purchases wearing one
button), and **hold extension on request** (every buyer asks, and the tier never recovers).
