# ET-TKT-001 · Reservation, the inventory hold, and the purchase workflow — tasks

> **Spec** [`specs/ticketing/001-reservation-and-hold/spec.md`](../ticketing/001-reservation-and-hold/spec.md) · **Wave 3** · `blocked_by:` ET-PLT-002, 003, 005, 006, 007, 015, ET-CAT-002
> **Screen** `Ticketing - Discover & Checkout.dc.html` — **read it first**
> **Routes** `apps/ticketing/src/app/events/[id]/book/page.tsx`
> **Verify** `mvn -q -f backend/booking-service test -Dgroups=ET-TKT-001 -DfailIfNoTests=false` · `compose-supergraph.sh --static`

The pivot of the whole platform. Between *I want this ticket* and *I have this ticket* sits a
mobile-money payment taking eight seconds to four minutes and failing about one time in six.
**D-09**: reserve first, pay second, ten-minute TTL. **D-21**: the purchase is one Temporal
workflow, `purchase/{reservationId}`, and its timers are workflow timers.

The property everything else depends on: **inventory is conserved**. A reservation that fails
halfway gives back exactly what it took — whether the payment declined, the process died, the
provider never answered, or the buyer closed the app and went to bed. Every release is a
compare-and-set, because confirmation, cancellation and the expiry timer can race.

## R0 · Reconcile *(the largest reconciliation in the corpus)*

The purchase path is `PurchaseProcess` → `PurchaseWorkflowImpl` → `CheckoutActivitiesImpl` →
`ReservationService` / `PaymentOutcomeService`, with `PurchaseAdoptionRunner` at boot.

```bash
grep -rn 'ET-TKT-001' backend/booking-service --include='*.java' | wc -l
grep -rn 'ReservationStatus\.' backend/booking-service --include='*.java' | grep -v /src/test/
```

Classify all 8 requirements carefully. Check in particular:
- Does `ReservationTransitions.LEGAL` hold **exactly 7** rows?
- Is the hold a single `findAndModify`, or read-then-write?
- Does the **TTL index fire after the workflow's last possible release** (`expiresAt` + seat
  grace)? If the TTL fires first it deletes the reservation and frees nothing — **inventory is
  lost permanently**. This is the highest-severity thing to check in the whole reconciliation pass.
- Does anything other than the purchase workflow move a payment's status? It must not.

## A · Backend

### BE-1 · Document, state machine, the 30-pair test
- **Spec** R2 · **§5** T1 · **depends** R0 · **parallel-safe** no
- Five states — `HELD`, `CONFIRMED`, `EXPIRED`, `RELEASED`, `FAILED`; six actions; **7 legal** of
  30 pairs. `CONFIRMED`, `EXPIRED`, `RELEASED`, `FAILED` all terminal.
- **`EXPIRED` and `RELEASED` are distinct on purpose**: *nobody paid in time* and *the payment
  failed* are different problems, and a platform that records both as released cannot tell a
  payments outage from a slow checkout.
- **Acceptance** 7 legal rows, 23 refusals, **no status literal**.

### BE-2 · The `hold` activity — the atomic hold and the reservation, one transaction
- **Spec** R1, R3 · **§5** T2 · **depends** BE-1 · **parallel-safe** **no — the platform's most contended write**
- The hold is [`ET-CAT-002`](ET-CAT-002.md) §4's single `findAndModify`. The reservation document
  is written in the **same transaction** as the counter movement. A hold that fails part-way runs
  `abandonHold`.
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

### BE-4 · The workflow id, Update-with-Start, and the partial unique hold index
- **Spec** R5, R6 · **§5** T4 · **depends** BE-2 · **parallel-safe** yes
- The reservation id is `nameUUID("purchase:" + userId + ":" + idempotencyKey)`; the workflow id
  is `purchase/{reservationId}`; `reserveTickets` is Update-with-Start under `USE_EXISTING`.
- Partial unique index on `{ userId, tierId }` where `status = HELD` — **at the database, not by a
  check**. Verify it exists with its filter via MongoDB MCP.
- Fingerprint covers `tierId`, `quantity`, `promoCode`, `userId` — **and nothing else**.
- **Acceptance** two parallel reservations by one buyer for one tier → **one** hold; a repeated
  key reaches the original execution; a changed fingerprint → `IDEMPOTENCY_KEY_REUSED`.

### BE-5 · The expiry timer, the TTL backstop, the double-release test
- **Spec** R4 · **§5** T5 · **depends** BE-2 · **parallel-safe** no
- `expiresAt = clock.instant() + PT10M`. The workflow sleeps to `expiresAt` and, with no payment
  started, runs `release(EXPIRE)`. No `@Scheduled` method, sweep or Redis lock.
- **The TTL index deletes; only a release returns seats.** `expireAfterSeconds` is `ttl + PT1H`,
  past the seat grace and the TTL monitor's period, so the TTL only ever removes reservations
  already terminal.
- **Acceptance** time-skipping test live at 9:59, expired at 10:01; 100 reservations released
  **twice concurrently**, counters exactly correct; releasing an already-released reservation is
  a **no-op, not an error**.

### BE-6 · Confirmation in one transaction; the five forced-failure tests
- **Spec** R7 · **§5** T6 · **depends** BE-3 · **parallel-safe** **no — spans tickets, escrow and commission**
- One transaction: `reserved → sold`, write tickets, credit escrow, record commission, mark
  `CONFIRMED`, and stage `booking.TicketPurchased` into `booking_outbox`. Only the drain
  publishes, after commit.
- **Acceptance** a forced failure at **each of the five points** leaves none applied — five tests;
  confirming an already-`CONFIRMED` reservation is a no-op returning the existing tickets; money
  arriving **after the seats were released** confirms nothing and escalates `PAID_AFTER_EXPIRY`;
  `Ledger.assertBalanced()` and `Inventory.assertConserved()` both hold.

### BE-7 · The payment polls, the seat grace, escalation, adoption and replay
- **Spec** R8 · **§5** T7 · **depends** BE-6 · **parallel-safe** no
- The purchase's process state is its workflow execution; the reservation and intent documents
  are its projection — no saga collection.
- Polls 10 s doubling to 5 min, sooner on a verified callback; seats released at expiry + 5 min
  with the money still watched; **a payment `PENDING` past `booking.payment.max-pending` (PT30M)
  is escalated to [`ET-ADM-003`](ET-ADM-003.md) and polled hourly, never silently called paid or
  unpaid**; polling stops after 7 days.
- `PurchaseAdoptionRunner` starts an execution for every `HELD` reservation that has none.
- **Acceptance** a layer-3 test for every branch of the spec's §4 diagram; a recorded purchase
  history replays with `WorkflowReplayer`; no reservation stays `HELD` beyond `ttl + seat-grace`.

### BE-8 · The subgraph half; `@auth` on every field
- **Spec** R1–R8 · **§5** T8 · **depends** BE-6 · **parallel-safe** **no — shared booking SDL**
- **Acceptance** static composition; **no client-callable `confirmPurchase` and no
  payment-lifecycle mutation exists.** Confirmation is the verified payment outcome inside the
  workflow ([`ET-PAY-002`](ET-PAY-002.md)), never a client asserting that it paid.

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
  reuses it. Regenerating it per attempt defeats the mechanism entirely — the key names the
  purchase's workflow.
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
- `cancelReservation` returns the buyer to the event with inventory visibly restored. Once the
  payment prompt is sent, cancel is refused (`RESERVATION_STATE_INVALID`, `PAYMENT_IN_FLIGHT`) —
  show *waiting for your payment*, not a cancel button.
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

### TS-1 · Atomicity *(L2, replica set)*
200-against-50 → 50 and 150; conservation after every attempt; nothing persisted on refusal;
multi-tier failure returns the first tier.

### TS-2 · State machine *(L1)* — all 30 pairs: 7 allow, 23 refuse with `currentStatus`.

### TS-3 · Quote *(L1)*
Price change mid-hold does not alter it; `HALF_UP` scale 2 applied once; the payment intent
charges `totalAmount` exactly or `PAYMENT_AMOUNT_MISMATCH`.

### TS-4 · Expiry *(L3 time-skipping, and L2 — the highest-risk test here)*
- Live at 9:59, expired at 10:01, under `TestWorkflowEnvironment`.
- 100 reservations, **two concurrent releases**, counters exactly correct.
- Double release is a no-op.
- **Explicitly assert the TTL fires after the workflow's last release** — construct the inverted
  configuration and prove it is rejected, so the ordering is protected by a test rather than by a
  comment.

### TS-5 · One hold per buyer per tier *(L2)*
Partial unique index confirmed live via MCP; two parallel reservations → one hold.

### TS-6 · Idempotency *(L2 against the Temporal dev server)*
Replay → original execution; changed fingerprint → refuse; two parallel submissions of one key →
one execution, one reservation and one hold.

### TS-7 · Confirmation *(L2)*
Five forced failures, five tests; already-confirmed is a no-op; paid-after-release escalates;
ledger balanced and inventory conserved after every confirmation.

### TS-8 · Recovery *(L3)*
Every branch of the workflow diagram under time skipping; `PENDING` past `max-pending`
**escalates rather than releases**; adoption of a `HELD` reservation with no execution; the
recorded history replays.

### TS-9 · e2e *(L5, ticketing — needs F0-1 and F0-4)*
- Select → reserve → countdown → expire → restart.
- Double-tap reserve → **one** hold; reload mid-flow reuses the key.
- **Background the tab for two minutes and return**: the countdown is correct. This is the test a
  client-side timer fails, and the one most likely to be skipped.
- Sold-out, limit-exceeded, expired — each its own screen.
- Loading, empty, error, populated. Iris accent, Space Grotesk headings, `K` currency.

## E · Gate

- [ ] R0 recorded across every existing reference; TTL-vs-release ordering explicitly verified
- [x] 7 legal transitions of 30 pairs — `ReservationStateMachineTest`, 10 cases, layer 1 (no
      Spring context, no database). All thirty `(status, action)` pairs are driven, legal and
      illegal: the twenty-three refusals are where the money is, since `CONFIRM` on an already-
      `CONFIRMED` hold writes tickets twice and credits escrow twice, and `RELEASE` on an
      `EXPIRED` one returns the same seats twice. `ReservationTransitions` is the only writer and
      it compare-and-sets, so no status literal reaches a document
- [ ] 200-against-50 → exactly 50; conservation after every attempt
- [ ] Nothing persisted on any refusal path
- [ ] Quote immune to tier price change; rounded once
- [x] Partial unique hold index live, with its filter — `uniq_reservation_user_tier_held` on
      `{userId, items.ticketTierId}`, unique, partial on `status = HELD`, asserted against a live
      database by `BookingIndexRegistryTest`. The filter is what makes it a constraint rather than
      a permanent ban: without it last month's released hold would occupy the key forever
- [ ] Idempotency: replay, fingerprint, parallel-once
- [x] TTL index fires **after** the workflow's last release — `ttl + ttl-grace` = PT1H10M, past
      the seat grace and MongoDB's TTL-monitor period. `ReservationTtlOrderingTest` is the
      inverted-config test R4 asks for: a zero expiry must be rejected **and** the registry's own
      spec accepted, so the rule cannot pass while comparing nothing. See
      [F-009](../FINDINGS.md#f-009--the-reservation-ttl-raced-the-sweep-that-returns-the-seats)
- [ ] Double release is a no-op; two concurrent releases conserve
- [ ] Five forced-failure tests; paid-after-release escalates
- [x] The purchase's timers, polls, escalation and seat grace run in `PurchaseWorkflow` — no
      `@Scheduled` method, sweep or Redis lock remains in booking; `PurchaseWorkflowTest` (13 cases,
      time skipping) covers every branch and replays its recorded history
      ([F-031](../FINDINGS.md))
- [x] **No client-callable `confirmPurchase`** — absent from all three subgraph SDLs and from
      the composed supergraph, asserted by `ReservationScopeLintTest`; no mutation starts,
      verifies, expires or fulfils a payment outside the workflow ([F-032](../FINDINGS.md))
- [ ] Countdown derived from server `expiresAt`, correct after tab backgrounding
- [x] A reservation is readable only by its buyer, or by support — **F-010, fixed 2026-09-01.**
      OWASP A01 / CWE-639. `reservation(id)` carried `isAuthenticated()` and answered on the id
      alone, publishing the buyer's identity, tier choices, promo code and exact money to any
      signed-in caller. Both sibling operations already scoped; this was the third.
      `ReservationVisibilityTest` (5 cases, replica set) and `ReservationScopeLintTest`
      (mutation-verified). See
      [F-010](../FINDINGS.md#f-010--the-one-reservation-read-that-did-not-check-who-was-asking)
- [ ] Double-tap and reload produce one hold
- [ ] `mvn -q -f backend/booking-service test -Dgroups=ET-TKT-001 -DfailIfNoTests=false` green
- [ ] Spec `status:` → `implemented`
