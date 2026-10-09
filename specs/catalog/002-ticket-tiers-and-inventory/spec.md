# ET-CAT-002 · Ticket tiers, capacity and the authoritative inventory

> **Conformance** · V3 §7.1 catalog collections · PDI Phase 4 optimistic locking · US Part III §16 buyer stories

## 1. Capability

A tier is a price and a promise: *this many seats, at this price, on sale between these two
moments*. It is the smallest unit the platform actually sells, and it is the object that
five thousand people hit simultaneously in the on-sale minute. Everything about how it is
modelled is downstream of that one fact.

This spec declares the tier and, separately, its inventory — because the two have opposite
characteristics. A tier's **definition** is authored by an organizer, changes a handful of
times in its life, and is owned by catalog. A tier's **counters** move thousands of times a
minute and are owned by booking, which is the service that reserves, sells and releases
(D-08, [ET-PLT-002](../../_platform/002-persistence-baseline/) §2). The two are joined by
`catalog.TicketTierPublished`, and the counters are the only thing a purchase decision ever
reads.

It declares the sales window, which is what makes an on-sale a moment rather than a state
somebody has to remember to toggle; the per-buyer purchase limit, which is the difference
between a fan sale and a scalper's warehouse; capacity changes, including the one that must
be refused — reducing capacity below what has already been committed; and promotion codes,
which are the only thing that changes the price a buyer pays and therefore the only thing
that must not be able to make it wrong.

The conservation property this spec exists to protect is one line:
`available + reserved + sold == capacity`, always, under any concurrency, at every instant.

## 2. Design decisions

**Definition in catalog, counters in booking, joined by an event.** `catalog_ticket_tiers`
holds name, price, capacity, sales window and status. `booking_tier_inventory` holds
`availableQuantity`, `reservedQuantity` and `soldQuantity` with a `@Version`. Booking
creates the inventory document on `catalog.TicketTierPublished` and adjusts it on
`catalog.TicketTierCapacityChanged`. The alternative — booking calling catalog to hold
inventory — puts an HTTP round trip inside the reservation transaction, which is the one
transaction in the platform that must be short.

**The sales window is data, not a state somebody toggles.** `salesStartAt` and `salesEndAt`
are timestamps, and *on sale* is a predicate over them and the `Clock`. An organizer
setting an on-sale for Friday at noon does not have to be awake at noon, and the platform
does not need a sweep to open sales — the predicate is simply true from that instant. A
tier that must be closed early is closed by moving `salesEndAt`, which leaves an audit
trail that a status flag does not.

**Capacity may rise freely and may fall only to what is committed.** Adding seats is always
safe. Reducing capacity below `reserved + sold` would mean the platform has sold something
that does not exist, so it is refused with `CAPACITY_BELOW_COMMITTED` carrying the
committed figure. This is the answer to the open question
[ET-PLT-002](../../_platform/002-persistence-baseline/) raised: a reduction is refused at
source, never absorbed as oversold.

**A capacity change is applied to the counters as a delta, never as an assignment.**
`$inc: { availableQuantity: delta }` where `delta = newCapacity − previousCapacity`. An
assignment would overwrite concurrent reservations happening in the same instant; a delta
composes with them. The event carries both the new and the previous capacity so the
consumer can compute the delta without reading catalog.

**Purchase limits are per buyer per tier, and they are enforced where the count lives.**
`maxPerOrder` bounds one transaction; `maxPerBuyer` bounds a person across all their orders
for that tier. The first is a validation; the second requires counting what that buyer
already holds, which is booking's data — so the limit is declared here and enforced in
[ET-TKT-001](../../ticketing/001-reservation-and-hold/).

**Tier pricing is a single amount, and every adjustment is explicit.** `price` plus a
`currency`. There is no implicit fee, no inclusive-or-exclusive ambiguity, and no
service-charge field that different code paths interpret differently. What a buyer pays is
`price × quantity − discount`, and the platform's commission comes out of the organizer's
side (D-04), never added to the buyer's.

**Promotion codes discount, are budgeted, and are consumed atomically.** A code carries a
type (percentage or fixed), a value, a maximum redemption count and a validity window. The
redemption counter is decremented by the same conditional-atomic pattern as inventory,
because a code with a hundred redemptions available and five hundred simultaneous attempts
is the same race as a tier with a hundred seats.

**A tier is never deleted once it has sold anything.** It is closed by moving `salesEndAt`
into the past and marked `CLOSED`. Deleting it would orphan every ticket that references
it, and a ticket that cannot name its tier cannot be priced, refunded or validated.

**Rejected alternatives**

- *Counters in catalog, alongside the definition.* Puts an HTTP hop inside the reservation transaction.
- *An `ON_SALE` status toggled by a sweep.* Adds a scheduled job and a window in which the data says one thing and the status says another.
- *Absorbing a capacity reduction as oversold.* The platform would owe seats it does not have, and the discovery of that is at the gate.
- *Assigning the new available count on a capacity change.* Overwrites concurrent reservations.
- *A service-fee field on the tier.* Two numbers describing one price, and a buyer who is charged the one the developer happened to read.
- *Per-order limits only.* A scalper places twenty orders.
- *Deleting a tier.* Orphans every ticket that names it.
- *Optimistic locking alone on the inventory document.* Correct, and it turns the on-sale minute into a retry storm; the conditional atomic update succeeds or refuses in one round trip.

## 3. Requirements

### ET-CAT-002-R1 · A tier is defined in catalog and counted in booking

THE SYSTEM SHALL hold each tier's definition in `catalog_ticket_tiers` and its counters in
`booking_tier_inventory`, and neither service SHALL write the other's document.

**Acceptance**
- [ ] `catalog_ticket_tiers` carries name, description, `price`, `currency`, `capacity`, `salesStartAt`, `salesEndAt`, `maxPerOrder`, `maxPerBuyer`, `status`, `displayOrder`
- [ ] `booking_tier_inventory` carries `tierId`, `eventId`, `capacity`, `availableQuantity`, `reservedQuantity`, `soldQuantity`, `status`, `@Version`
- [ ] Booking creates exactly one inventory document per tier on `catalog.TicketTierPublished`, idempotent on `tierId`
- [ ] Catalog-service never writes `booking_tier_inventory`; booking-service never writes `catalog_ticket_tiers`
- [ ] `TicketTier.availableQuantity` and `soldQuantity` are contributed to the graph by **booking**, as an `extend type` ([ET-PLT-004 §4](../../_platform/004-federation-contract/))
- [ ] Every `@Document` names a row of the ET-PLT-002 §4 registry and carries `@TypeAlias`; no document field is `LocalDateTime` or `java.util.Date`; every balance-bearing document declares `@Version`

### ET-CAT-002-R2 · Conservation holds under any concurrency

THE SYSTEM SHALL maintain `availableQuantity + reservedQuantity + soldQuantity == capacity`
for every tier at every instant.

**Acceptance**
- [ ] Every counter movement is a single `findAndModify` with the guard in its filter — no read-modify-write anywhere
- [ ] A reservation decrements `available` and increments `reserved` in one update; a purchase moves `reserved` to `sold`; a release moves `reserved` back to `available`
- [ ] `Inventory.assertConserved(tierId)` ([ET-PLT-006](../../_platform/006-test-harness/) §4) holds after every operation in every test
- [ ] 200 parallel reservations against 50 available yield exactly 50 successes and 150 `TIER_SOLD_OUT`, run against a real replica set under contention
- [ ] A test interleaving reservations, expiries, purchases and releases asserts conservation throughout, not only at the end
- [ ] No code path sets a counter by assignment

### ET-CAT-002-R3 · The sales window is a predicate over time

WHILE the current instant is outside a tier's sales window, THE SYSTEM SHALL refuse a
reservation for it.

**Acceptance**
- [ ] *On sale* is `salesStartAt <= clock.instant() < salesEndAt AND status = ON_SALE`, computed in one place
- [ ] A reservation outside the window is refused with `TIER_NOT_ON_SALE` carrying `salesStartAt` and `salesEndAt`
- [ ] No scheduled job opens or closes a sales window
- [ ] `salesEndAt` defaults to the event's `startsAt` when the organizer does not set one
- [ ] Frozen-clock tests assert a refusal one second before `salesStartAt`, a success at `salesStartAt`, a success one second before `salesEndAt`, and a refusal at `salesEndAt`
- [ ] The window predicate is evaluated in booking, from the inventory document's mirrored window — a reservation never calls catalog

### ET-CAT-002-R4 · Capacity rises freely and falls only to what is committed

WHEN an organizer changes a tier's capacity, THE SYSTEM SHALL apply the change as a delta,
and IF the new capacity is below `reserved + sold`, THEN THE SYSTEM SHALL refuse it.

**Acceptance**
- [ ] An increase is always accepted and raises `availableQuantity` by the delta
- [ ] A decrease below `reservedQuantity + soldQuantity` is refused with `CAPACITY_BELOW_COMMITTED` carrying `committedQuantity`
- [ ] The refusal is raised by **booking**, which holds the committed figure, and surfaced to the organizer through catalog's mutation
- [ ] `catalog.TicketTierCapacityChanged` carries `newCapacity` and `previousCapacity`; the consumer computes and applies the delta with `$inc`
- [ ] No consumer assigns `availableQuantity`
- [ ] A capacity change concurrent with reservations conserves — asserted by a test that changes capacity while 100 reservations are in flight
- [ ] The event's `totalCapacity` is recomputed as the sum of its tiers, and doing so does not itself trigger [ET-CAT-001](../001-event-lifecycle/) R3's material-change rule

### ET-CAT-002-R5 · Purchase limits bound an order and a buyer

THE SYSTEM SHALL enforce `maxPerOrder` on each reservation and `maxPerBuyer` across a
buyer's holdings for that tier.

**Acceptance**
- [ ] A reservation for more than `maxPerOrder` is refused with `PURCHASE_LIMIT_EXCEEDED` carrying `limit`
- [ ] A reservation that would take a buyer's total for that tier above `maxPerBuyer` is refused with `PURCHASE_LIMIT_EXCEEDED` carrying `limit` and `alreadyHeld`
- [ ] `alreadyHeld` counts tickets in `PURCHASED` and `VALIDATED` plus reservations in `HELD` — a buyer cannot exceed the limit by holding rather than buying
- [ ] Refunded and expired holdings do not count
- [ ] Both limits default to `catalog.tier.default-max-per-order` (10) and `null` respectively; `null` means unlimited
- [ ] Two parallel reservations that would jointly exceed `maxPerBuyer` produce one success and one refusal

### ET-CAT-002-R6 · A promotion code discounts, is budgeted, and cannot be over-redeemed

WHEN a valid promotion code is applied, THE SYSTEM SHALL reduce the order total and consume
one redemption atomically, and IF its budget is exhausted, THEN THE SYSTEM SHALL refuse it.

**Acceptance**
- [ ] `booking_promo_codes` carries `code`, `discountType`, `discountValue`, `maxRedemptions`, `redemptionCount`, `validFrom`, `validUntil`, `eventId`, `tierIds`, `minOrderAmount`, `status`, `@Version`
- [ ] `code` is unique and matched case-insensitively
- [ ] A redemption is a conditional atomic `$inc` filtering on `redemptionCount < maxRedemptions` — the same pattern as inventory
- [ ] An exhausted code is refused with `PROMO_CODE_EXHAUSTED`; an unknown one with `PROMO_CODE_UNKNOWN`; one outside its window, below `minOrderAmount`, or not applicable to the tier with `PROMO_CODE_NOT_APPLICABLE` carrying `reason`
- [ ] The discount never takes the order total below zero; a percentage code is applied to the tier subtotal before any rounding
- [ ] A failed purchase releases the redemption, so an abandoned checkout does not consume budget
- [ ] 200 parallel redemptions of a code with 50 remaining yield exactly 50 successes
- [ ] The discount is borne by the **organizer** — the platform's commission is computed on the discounted amount ([ET-FIN-002](../../finance/002-commission/))

### ET-CAT-002-R7 · A tier that has sold is closed, never deleted

WHEN an organizer removes a tier, THE SYSTEM SHALL delete it only when nothing has been
sold or reserved, and otherwise SHALL close it.

**Acceptance**
- [ ] `deleteTier` succeeds only when `soldQuantity` and `reservedQuantity` are both zero
- [ ] Otherwise it is refused, and `closeTier` sets `status = CLOSED` and `salesEndAt` to now
- [ ] A closed tier remains resolvable so that existing tickets can name it
- [ ] A closed tier is excluded from the event's on-sale tier list but included in its full tier list
- [ ] Deleting a tier with zero sales also deletes its inventory document, driven by an event and idempotent
- [ ] `TierStatus` is `SCHEDULED`, `ON_SALE`, `PAUSED`, `SOLD_OUT`, `CLOSED`

## 4. Model

> **Amended 2026-09-01 under [D-19](../../ROADMAP.md).** 1 operation name below adopts the
> shipped name: `deleteTier` → `deleteTicketTier`. D-19 rules that where the schema and §4 disagree on an operation's
> *name*, the schema stands and §4 adopts it.
>
> **Only the names were adopted.** Argument lists and return types were not re-verified against
> the schema, so a row here can now name a real operation and still describe it wrongly. That
> gap is unmeasured, and calling it verified would be the same mistake as counting a file's
> existence as proof it runs.

### Documents

`catalog_ticket_tiers` — the definition, owned by catalog.

| Field | Type | Notes |
|---|---|---|
| `_id`, `eventId` | `String` | |
| `name`, `description` | `String` | |
| `price` | `BigDecimal` | `Decimal128`; `currency` alongside, default `ZMW` |
| `capacity` | `int` | |
| `salesStartAt`, `salesEndAt` | `Instant` | `salesEndAt` defaults to the event's `startsAt` |
| `maxPerOrder` | `int` | default 10 |
| `maxPerBuyer` | `Integer` | nullable — `null` is unlimited |
| `status` | `TierStatus` | `SCHEDULED`, `ON_SALE`, `PAUSED`, `SOLD_OUT`, `CLOSED` |
| `displayOrder` | `int` | |
| `createdAt`, `updatedAt`, `version` | | |

`booking_tier_inventory` — the counters, owned by booking.

| Field | Type | Notes |
|---|---|---|
| `_id` | `String` | |
| `tierId` | `String` | **unique** |
| `eventId` | `String` | |
| `capacity` | `int` | mirrored |
| `availableQuantity`, `reservedQuantity`, `soldQuantity` | `int` | the invariant |
| `salesStartAt`, `salesEndAt` | `Instant` | mirrored, so R3's predicate needs no catalog call |
| `maxPerOrder`, `maxPerBuyer` | `int`, `Integer` | mirrored |
| `price`, `currency` | `BigDecimal`, `String` | mirrored — the price a reservation quotes |
| `status` | `InventoryStatus` | `ON_SALE`, `PAUSED`, `SOLD_OUT`, `CLOSED` |
| `version` | `Long` | `@Version` |

Everything after `capacity` is a **mirror**. Catalog is the authority for all of it; the
mirror exists so the reservation path reads one document.

### The three counter movements

Each is one `findAndModify`. None is a read-modify-write.

| Movement | Filter adds | Update |
|---|---|---|
| reserve | `status = ON_SALE`, `salesStartAt <= now < salesEndAt`, `availableQuantity >= qty` | `available −qty`, `reserved +qty` |
| confirm | `reservedQuantity >= qty` | `reserved −qty`, `sold +qty` |
| release | `reservedQuantity >= qty` | `reserved −qty`, `available +qty` |

An empty result is a refusal, not an error. Reserve → `TIER_SOLD_OUT` or `TIER_NOT_ON_SALE`
depending on which clause failed, distinguished by a follow-up read that runs **only on the
refusal path**.

### Capacity change

```java
// the delta composes with concurrent reservations; an assignment would overwrite them
int delta = event.newCapacity() - event.previousCapacity();

Query q = Query.query(Criteria.where("tierId").is(event.tierId())
    .and("$expr").is(gte(add("$reservedQuantity", "$soldQuantity"), event.newCapacity())
                     .not()));       // refuse when committed > newCapacity

return mongo.findAndModify(q, new Update().inc("availableQuantity", delta)
                                          .set("capacity", event.newCapacity()),
                           TierInventory.class)
            .switchIfEmpty(Mono.error(new CapacityBelowCommitted(event.tierId())));
```

### Promotion codes

`booking_promo_codes`, owned by booking because redemption is a purchase-path operation.

| Field | Type | Notes |
|---|---|---|
| `code` | `String` | **unique**, matched case-insensitively |
| `discountType` | `DiscountType` | `PERCENTAGE`, `FIXED_AMOUNT` |
| `discountValue` | `BigDecimal` | percent, or an amount with a currency |
| `maxRedemptions`, `redemptionCount` | `int` | the atomic pair |
| `validFrom`, `validUntil` | `Instant` | |
| `eventId` | `String` | nullable — platform-wide when null |
| `tierIds` | `List<String>` | empty means every tier of the event |
| `minOrderAmount` | `BigDecimal` | nullable |
| `status` | `PromoStatus` | `ACTIVE`, `PAUSED`, `EXHAUSTED`, `EXPIRED` |
| `version` | `Long` | |

Order arithmetic, in this order, with one rounding at the end:

```
subtotal   = Σ (tier.price × quantity)
discount   = PERCENTAGE ? subtotal × value / 100 : min(value, subtotal)
total      = round(subtotal − discount, 2, HALF_UP)
commission = computed on `total`, by ET-FIN-002 — never on `subtotal`
```

The discount reduces the organizer's take. The platform does not subsidise a promotion it
did not authorise.

### GraphQL

Subgraph `catalog`, except the contributed fields, which are booking's.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `ticketTiers(eventId)` | query | `PUBLIC` | `[TicketTier!]!` — bounded, ≤ 50 |
| `ticketTier(id)` | query | `PUBLIC` | `TicketTier` |
| `createTicketTier(input)` | mutation | `ORGANIZER` | `TicketTier!` |
| `updateTicketTier(id, input)` | mutation | `ORGANIZER` | `TicketTier!` |
| `changeTierCapacity(id, capacity)` | mutation | `ORGANIZER` | `TicketTier!` |
| `pauseTierSales(id)` / `resumeTierSales(id)` | mutation | `ORGANIZER` | `TicketTier!` |
| `closeTier(id)` | mutation | `ORGANIZER` | `TicketTier!` |
| `deleteTicketTier(id)` | mutation | `ORGANIZER` | `Boolean!` |
| `promoCode(code, eventId)` | query | `AUTHENTICATED` | `PromoCodeValidation!` |
| `createPromoCode(input)` | mutation | `ORGANIZER` | `PromoCode!` |
| `updatePromoCode(id, input)` | mutation | `ORGANIZER` | `PromoCode!` |
| `deactivatePromoCode(id)` | mutation | `ORGANIZER` | `PromoCode!` |

```graphql
# catalog owns the definition
type TicketTier @key(fields: "id") {
    id: ID!   name: String!   price: BigDecimal!   currency: String!
    capacity: Int!   salesStartAt: DateTime!   salesEndAt: DateTime!
    maxPerOrder: Int!   maxPerBuyer: Int   status: TierStatus!
}

# booking contributes the state. NOTE: no `id` here.
extend type TicketTier @key(fields: "id") {
    availableQuantity: Int!
    soldQuantity: Int!
    onSale: Boolean!
}
```

`PromoCodeValidation` returns `valid`, `discountType`, `discountValue` and, when invalid, a
`reason` — it never reveals `maxRedemptions` or `redemptionCount`, which would let a buyer
see how much budget is left.

### Events

| Tier | Name | When | Consumers |
|---|---|---|---|
| bus | `catalog.TicketTierPublished` v1 | tier created on a published event, or event published | booking → create inventory |
| bus | `catalog.TicketTierCapacityChanged` v1 | capacity change | booking → `$inc` the delta |

Closing a tier updates the event's on-sale list in the same transaction; there is no in-memory event.

Both bus rows are session-keyed on `tierId`
([ET-PLT-003 §4](../../_platform/003-event-contract/)) — ordering matters, because a
capacity change arriving before the creation would apply a delta to nothing.

### Redis keys

| Key | TTL | Purpose | Authority |
|---|---|---|---|
| `cache:tier:{tierId}` | 30 s | **display only** — remaining count on a listing | `booking_tier_inventory` |

No reservation decision reads this key. The decision is the conditional update
([ET-PLT-002](../../_platform/002-persistence-baseline/) §4).

### Configuration

| Property | Value |
|---|---|
| `catalog.tier.default-max-per-order` | 10 |
| `catalog.tier.max-tiers-per-event` | 20 |
| `booking.promo.release-on-failure` | `true` |

### Error codes

`TIER_UNKNOWN`, `TIER_NOT_ON_SALE`, `CAPACITY_BELOW_COMMITTED`, `PROMO_CODE_UNKNOWN`,
`PROMO_CODE_EXHAUSTED`, `PROMO_CODE_NOT_APPLICABLE` — rows of
[ET-PLT-005 §4](../../_platform/005-error-contract/) introduced by this spec.
`TIER_SOLD_OUT` and `PURCHASE_LIMIT_EXCEEDED` are rows this spec's rules raise, introduced
by [ET-PLT-002](../../_platform/002-persistence-baseline/) and
[ET-TKT-001](../../ticketing/001-reservation-and-hold/).

## 5. Tasks

- [ ] **T1 · `catalog_ticket_tiers`, its mutations and the window defaults**
  - requirements: R1, R3
  - files: `backend/catalog-service/.../domain/model/TicketTier.java`, `.../service/impl/`
  - verify: `salesEndAt` defaults to the event's `startsAt`; no sweep opens a window
  - parallel-safe: no
  - depends: —

- [ ] **T2 · `booking_tier_inventory`, its creation consumer and the mirror**
  - requirements: R1
  - files: `backend/booking-service/.../domain/model/TierInventory.java`, `.../event/listener/`
  - verify: one inventory document per tier, idempotent on `tierId`
  - parallel-safe: no
  - depends: T1

- [ ] **T3 · The three conditional atomic movements; the conservation test**
  - requirements: R2
  - files: `backend/booking-service/.../repository/impl/TierInventoryRepositoryImpl.java`
  - verify: 200-against-50 under real contention; conservation asserted throughout an interleaved run
  - parallel-safe: no — the most important write in the platform
  - depends: T2

- [ ] **T4 · The on-sale predicate and its four boundary tests**
  - requirements: R3
  - files: `backend/booking-service/.../domain/SalesWindow.java`
  - verify: refuse at `salesStartAt − 1s`, succeed at `salesStartAt`, succeed at `salesEndAt − 1s`, refuse at `salesEndAt`
  - parallel-safe: yes
  - depends: T3

- [ ] **T5 · Capacity change: the delta consumer and the committed-floor refusal**
  - requirements: R4
  - files: `backend/catalog-service/.../service/impl/`, `backend/booking-service/.../event/listener/`
  - verify: a change concurrent with 100 in-flight reservations conserves; a reduction below committed refuses
  - parallel-safe: no — spans two services
  - depends: T3

- [ ] **T6 · Purchase limits, including the `alreadyHeld` count**
  - requirements: R5
  - files: `backend/booking-service/.../service/impl/ReservationServiceImpl.java`
  - verify: two parallel reservations jointly exceeding `maxPerBuyer` yield one success
  - parallel-safe: yes
  - depends: T3

- [ ] **T7 · Promotion codes: atomic redemption, release on failure, the validation type**
  - requirements: R6
  - files: `backend/booking-service/.../service/impl/PromoCodeServiceImpl.java`
  - verify: 200 parallel redemptions of 50 remaining yield 50; an abandoned checkout releases
  - parallel-safe: yes
  - depends: T3

- [ ] **T8 · Close versus delete; the tier status machine**
  - requirements: R7
  - files: `backend/catalog-service/.../service/impl/TicketTierServiceImpl.java`
  - verify: a tier with sales cannot be deleted; a closed tier still resolves for existing tickets
  - parallel-safe: yes
  - depends: T1

- [ ] **T9 · The subgraph halves: catalog's type, booking's `extend`**
  - requirements: R1
  - files: both `schema.graphqls`
  - verify: `compose-supergraph.sh --static`; no `id` inside the extend block
  - parallel-safe: no — two SDL files must agree
  - depends: T2

## 6. Out of scope

| Capability | Spec |
|---|---|
| The event's own lifecycle and its material-change rule | [ET-CAT-001](../001-event-lifecycle/) |
| Venues, categories, cities | [ET-CAT-003](../003-locations-and-reference-data/) |
| Creating and expiring a reservation | [ET-TKT-001](../../ticketing/001-reservation-and-hold/) |
| Taking the money for a reservation | [ET-PAY-001](../../payment/001-payment-intents-and-providers/) |
| Issuing the ticket a confirmed reservation becomes | [ET-TKT-002](../../ticketing/002-ticket-issuance-and-qr/) |
| Computing commission on the discounted total | [ET-FIN-002](../../finance/002-commission/) |
| Refunding a ticket and restoring the counter | [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) |

Deliberately never in scope: **counters in catalog** (an HTTP hop inside the reservation
transaction), **a scheduled job to open sales** (the window is a predicate), and **absorbing
a capacity reduction as oversold** (the discovery of that is at the gate).
