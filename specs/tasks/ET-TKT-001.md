# ET-TKT-001 · Reservation, the inventory hold, and the purchase saga — tasks

> **Spec** [`specs/ticketing/001-reservation-and-hold/spec.md`](../ticketing/001-reservation-and-hold/spec.md) · **Wave 3** · `blocked_by:` ET-PLT-002, 003, 005, 006, 007, ET-CAT-002
> **Screen** `Ticketing - Discover & Checkout.dc.html` — **read it first**
> **Routes** `apps/ticketing/src/app/events/[id]/book/page.tsx`
> **Verify** `mvn -q -f backend/booking-service test -Dgroups=ET-TKT-001 -DfailIfNoTests=true` · `compose-supergraph.sh --static`

The pivot of the whole platform. Between *I want this ticket* and *I have this ticket* sits a
mobile-money payment taking eight seconds to four minutes and failing about one time in six.
**D-09**: reserve first, pay second, ten-minute TTL.

The property everything else depends on: **inventory is conserved**. A reservation that fails
halfway gives back exactly what it took — whether the payment declined, the process died, the
provider never answered, or the buyer closed the app and went to bed. Four release paths, all
idempotent, and at least two of them will fire for the same reservation.

## R0 · Reconcile *(the largest reconciliation in the corpus)*

**55 code references to `ET-TKT-001` already exist**, plus untracked `PurchaseServiceImpl`,
`ReservationStateMachine`, `ReservationTransitions`, `ReservationExpirationScheduler`,
`PurchaseRecoveryScheduler`, `PurchaseEscalationService` and `ReservationConformanceMigrationService`.

```bash
grep -rn 'ET-TKT-001' backend/booking-service --include='*.java' | wc -l
grep -rn 'ReservationStatus\.' backend/booking-service --include='*.java' | grep -v /src/test/
```

Classify all 8 requirements carefully. Check in particular:
- Does `ReservationTransitions.LEGAL` hold **exactly 7** rows?
- Is the hold a single `findAndModify`, or read-then-write?
- Is the **TTL index longer than the sweep window**? If the TTL fires first it deletes the
  reservation and frees nothing — **inventory is lost permanently**. This is the highest-severity
  thing to check in the whole reconciliation pass.
- Is `ReservationExpirationScheduler` actually invoked at boot, or is it an orphaned bean?

## A · Backend

### BE-1 · Document, state machine, the 30-pair test
- **Spec** R2 · **§5** T1 · **depends** R0 · **parallel-safe** no
- Five states — `HELD`, `CONFIRMED`, `EXPIRED`, `RELEASED`, `FAILED`; six actions; **7 legal** of
  30 pairs. `CONFIRMED`, `EXPIRED`, `RELEASED`, `FAILED` all terminal.
- **`EXPIRED` and `RELEASED` are distinct on purpose**: *nobody paid in time* and *the payment
  failed* are different problems, and a platform that records both as released cannot tell a
  payments outage from a slow checkout.
- **Acceptance** 7 legal rows, 23 refusals, **no status literal**.

### BE-2 · `reserveTickets` — the atomic hold and the reservation, one transaction
- **Spec** R1, R3 · **§5** T2 · **depends** BE-1 · **parallel-safe** **no — the platform's most contended write**
- The hold is [`ET-CAT-002`](ET-CAT-002.md) §4's single `findAndModify`. The reservation document
  is written in the **same transaction** as the counter movement.
- **Acceptance** 200-against-50 → exactly 50 reservations and 150 refusals; a multi-tier order
  takes every tier in one transaction and a failure on the second **returns the first**;
  `Persistence.assertNothingPersisted("booking_reservations")` on every refusal;
  `Inventory.assertConserved(tierId)` after **every** attempt, successful or not.

### BE-3 · The quote, its rounding, and price-change immunity
- **Spec** R3 · **§5** T3 · **depends** BE-2 · **parallel-safe** yes
- Computed from `booking_tier_inventory`'s **mirrored** price, never a catalog call. `HALF_UP` at
  scale 2, applied **once**, to `totalAmount`.
- **Acceptance** a tier price change during the ten minutes does **not** alter the reservation.
  The buyer agreed to a number; honour it.

### BE-4 · The partial unique hold index and the idempotency guard
- **Spec** R5, R6 · **§5** T4 · **depends** BE-2 · **parallel-safe** yes
- Partial unique index on `{ userId, tierId }` where `status = HELD` — **at the database, not by a
  check**. Verify it exists with its filter via MongoDB MCP.
- Idempotency is [`ET-PLT-007`](ET-PLT-007.md) R6's guard. Fingerprint covers `tierId`,
  `quantity`, `promoCode`, `userId` — **and nothing else**.
- **Acceptance** two parallel reservations by one buyer for one tier → **one** hold; a repeated
  key returns the original; a changed fingerprint → `IDEMPOTENCY_KEY_REUSED`.

### BE-5 · The expiry sweep, the TTL grace, the double-release test
- **Spec** R4 · **§5** T5 · **depends** BE-2 · **parallel-safe** no
- `expiresAt = clock.instant() + PT10M`. Sweep every `PT30S` under
  `lock:sweep:reservation-expiry`, batch 500.
- **The TTL index deletes; the sweep releases.** Set `expireAfterSeconds` to `ttl + PT1H` so the
  TTL only ever removes rows the sweep has already released. **Getting this order wrong loses the
  inventory permanently.**
- **Acceptance** 100 expired reservations, sweep run **twice concurrently**, counters exactly
  correct; releasing an already-released reservation is a **no-op, not an error**; frozen-clock
  live at 9:59, expired at 10:01.

### BE-6 · Confirmation in one transaction; the five forced-failure tests
- **Spec** R7 · **§5** T6 · **depends** BE-3 · **parallel-safe** **no — spans tickets, escrow and commission**
- One transaction: `reserved → sold`, write tickets, credit escrow, record commission, mark
  `CONFIRMED`. The event is staged into `booking_outbox` **inside** the transaction;
  `StreamBridge` is reached only from the after-commit listener.
- **Acceptance** a forced failure at **each of the five points** leaves none applied — five tests;
  confirming an already-`CONFIRMED` reservation is a no-op returning the existing tickets; a
  confirmation arriving **after expiry** is refused with `RESERVATION_EXPIRED` and the payment
  refunded; `Ledger.assertBalanced()` and `Inventory.assertConserved()` both hold.

### BE-7 · The recovery sweep and the six kill-point tests
- **Spec** R8 · **§5** T7 · **depends** BE-6 · **parallel-safe** no
- The saga's state is `ReservationStatus` **plus the payment intent's status** — no separate saga
  collection.
- **A `HELD` reservation with a `PENDING` intent past `booking.payment.max-pending` (PT30M) is
  escalated to [`ET-ADM-003`](ET-ADM-003.md), not silently released — the money may still arrive.**
  Releasing it is how a buyer is charged for a seat somebody else now has.
- **Acceptance** killing the process after each of the six saga steps resolves correctly on
  restart — six tests; no reservation remains `HELD` longer than
  `ttl + sweep-interval + max-pending`.

### BE-8 · The subgraph half; `@auth` on every field
- **Spec** R1–R8 · **§5** T8 · **depends** BE-6 · **parallel-safe** **no — shared booking SDL**
- **Acceptance** static composition; **no client-callable `confirmPurchase` exists.** Confirmation
  is driven by the payment outcome ([`ET-PAY-002`](ET-PAY-002.md)), never by a client asserting
  that it paid.

## B · Contract

### GQL-1 · 2 queries, 2 mutations
- `reservation(id)`, `myActiveReservations` (bounded ≤ 20); `reserveTickets`, `cancelReservation`.
- `ReserveTicketsInput` carries a **non-null** `idempotencyKey: String!`.
- **depends** BE-8 · **parallel-safe** no *(booking's SDL is contended by TKT-001/002/003/004, PAY-001, FIN-001…005, ADM-003 — sequence these)*

## C · Frontend — `Ticketing - Discover & Checkout.dc.html`

### FE-1 · Tier selection and quantity
- **depends** GQL-1, F0-1 · **parallel-safe** no
- Quantity capped by [`ET-CAT-002`](ET-CAT-002.md) FE-6's limits. Live total in `K 12,500`,
  tabular Fira Code.
- **testids** `checkout-tier`, `checkout-quantity`, `checkout-total`

### FE-2 · Reserve — the idempotency key and the double-tap
- **depends** FE-1, [`ET-PLT-007`](ET-PLT-007.md) FE-2 · **parallel-safe** no
- Key generated client-side, **stable across retries of the same intent**, persisted so a reload
  reuses it. Regenerating it per attempt defeats the mechanism entirely.
- Button disables during the request (`loading-buttons`), but the **key** is what actually
  prevents the double hold — a disabled button does not survive a page refresh.
- **testids** `checkout-reserve`, `checkout-reserve-pending`

### FE-3 · The ten-minute countdown — **the defining element of this screen**
- **depends** FE-2 · **parallel-safe** no
- Derived from the server's `expiresAt`, **never** from a client timer started on render. A
  backgrounded mobile browser throttles timers, and the buyer is told they have four minutes left
  when the hold expired two minutes ago.
- Re-derive on tab focus and on every poll. Show remaining time plainly; escalate the visual
  weight under one minute, without a flashing effect that fails `prefers-reduced-motion`.
- **testids** `reservation-countdown`, `reservation-expiring-soon`

### FE-4 · Expiry and release states
- **depends** FE-3 · **parallel-safe** yes
- `RESERVATION_EXPIRED` → a designed screen offering to start again, with the current availability
  re-fetched. Not an error toast over a dead form.
- `cancelReservation` returns the buyer to the event with inventory visibly restored.
- **testids** `reservation-expired`, `reservation-restart`, `checkout-cancel`

### FE-5 · Refusals from the registry
- **depends** [`ET-PLT-005`](ET-PLT-005.md) FE-1 · **parallel-safe** yes
- `TIER_SOLD_OUT`, `TIER_NOT_ON_SALE`, `PURCHASE_LIMIT_EXCEEDED`, `RESERVATION_STATE_INVALID`,
  `IDEMPOTENCY_KEY_REUSED` — each distinct and actionable. Sold-out re-fetches availability rather
  than leaving a stale number on screen.
- **testids** `checkout-error-<code>`

### FE-6 · Active reservations
- **depends** GQL-1 · **parallel-safe** yes
- `myActiveReservations` in the header — a buyer who navigated away must be able to get back to a
  live hold before it expires. This is the difference between a recovered sale and an abandoned
  one.
- **testids** `active-reservations`, `active-reservation-resume`

## D · Tests

### TS-1 · Atomicity *(L3, replica set)*
200-against-50 → 50 and 150; conservation after every attempt; nothing persisted on refusal;
multi-tier failure returns the first tier.

### TS-2 · State machine *(L1)* — all 30 pairs: 7 allow, 23 refuse with `currentStatus`.

### TS-3 · Quote *(L1)*
Price change mid-hold does not alter it; `HALF_UP` scale 2 applied once; the payment intent
charges `totalAmount` exactly or `PAYMENT_AMOUNT_MISMATCH`.

### TS-4 · Expiry *(L3, frozen clock — the highest-risk test here)*
- Live at 9:59, expired at 10:01.
- 100 expired reservations, **two concurrent sweeps**, counters exactly correct.
- Double release is a no-op.
- **Explicitly assert the TTL fires after the sweep window** — construct the inverted
  configuration and prove it loses inventory, so the ordering is protected by a test rather than
  by a comment.

### TS-5 · One hold per buyer per tier *(L3)*
Partial unique index confirmed live via MCP; two parallel reservations → one hold.

### TS-6 · Idempotency *(L3)*
Replay → original; changed fingerprint → refuse; two parallel submissions of one key → one
reservation and one hold.

### TS-7 · Confirmation *(L3)*
Five forced failures, five tests; already-confirmed is a no-op; post-expiry confirmation refunds;
ledger balanced and inventory conserved after every confirmation.

### TS-8 · Recovery *(L3)*
Six kill points; `PENDING` past `max-pending` **escalates rather than releases**; no reservation
stays `HELD` beyond the bound.

### TS-9 · e2e *(L5, ticketing — needs F0-1 and F0-4)*
- Select → reserve → countdown → expire → restart.
- Double-tap reserve → **one** hold; reload mid-flow reuses the key.
- **Background the tab for two minutes and return**: the countdown is correct. This is the test a
  client-side timer fails, and the one most likely to be skipped.
- Sold-out, limit-exceeded, expired — each its own screen.
- Loading, empty, error, populated. Iris accent, Space Grotesk headings, `K` currency.

## E · Gate

- [ ] R0 recorded across all 55 existing references; TTL-vs-sweep ordering explicitly verified
- [ ] 7 legal transitions of 30 pairs; no status literal
- [ ] 200-against-50 → exactly 50; conservation after every attempt
- [ ] Nothing persisted on any refusal path
- [ ] Quote immune to tier price change; rounded once
- [ ] Partial unique hold index live, with its filter
- [ ] Idempotency: replay, fingerprint, parallel-once
- [ ] TTL index fires **after** the sweep window, proven by an inverted-config test
- [ ] Double release is a no-op; two concurrent sweeps conserve
- [ ] Five forced-failure tests; post-expiry confirmation refunds
- [ ] Six kill points recover; `PENDING` past budget **escalates**, never silently releases
- [ ] **No client-callable `confirmPurchase` in the composed schema**
- [ ] Countdown derived from server `expiresAt`, correct after tab backgrounding
- [ ] Double-tap and reload produce one hold
- [ ] `mvn -q -f backend/booking-service test -Dgroups=ET-TKT-001 -DfailIfNoTests=true` green
- [ ] Spec `status:` → `implemented`
