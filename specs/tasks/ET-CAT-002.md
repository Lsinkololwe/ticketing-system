# ET-CAT-002 · Ticket tiers, capacity and the authoritative inventory — tasks

> **Spec** [`specs/catalog/002-ticket-tiers-and-inventory/spec.md`](../catalog/002-ticket-tiers-and-inventory/spec.md) · **Wave 2** · `blocked_by:` ET-PLT-002, 003, 005, ET-CAT-001
> **Screen** `Org Admin - Event Editor.dc.html` *(tier panel)* · tier display in `Ticketing - Event Detail (Full).dc.html`
> **Verify** `mvn -q -f backend/catalog-service test -Dgroups=ET-CAT-002 -DfailIfNoTests=false` · `compose-supergraph.sh --static`

**This spec owns the number that must never be wrong.** `booking_tier_inventory` is the
authoritative count; catalog's tier document is the definition. Target scale (**D-16**) is
**5,000 reservations/minute against a single event** — every design choice here is sized against
that, because 200,000 tickets/month never breaks anything.

## R0 · Reconcile

```bash
grep -rn 'TierInventory\|available\|reserved\|sold' backend/booking-service --include='*.java' | grep -v /src/test/
```

The question that decides everything: is the decrement a **single conditional `findAndModify`**,
or a read-then-write? Read-modify-write is `contradicted` — it oversells at on-sale and only at
on-sale, so it will have passed every test ever run against it.

## A · Backend

### BE-1 · `catalog_ticket_tiers`, its mutations and the window defaults
- **Spec** R1, R3 · **§5** T1 · **depends** R0 · **parallel-safe** no
- **Acceptance** `salesEndAt` defaults to the event's `startsAt`; **no sweep opens a window**.
- The window is a **predicate over the clock**, not a state a job flips. A sweep that opens sales
  makes on-sale time depend on scheduler health — and on-sale is the one minute that must not
  depend on a cron.

### BE-2 · `booking_tier_inventory`, its creation consumer and the mirror
- **Spec** R1 · **§5** T2 · **depends** BE-1 · **parallel-safe** no
- **Acceptance** exactly one inventory document per tier, **idempotent on `tierId`** (the consumer
  will receive the creation event twice).
- The mirror carries the tier's **price** as well as its counts, because
  [`ET-TKT-001`](ET-TKT-001.md) R3 computes the quote from the mirror, never from a catalog call.

### BE-3 · The three conditional atomic movements; the conservation test
- **Spec** R2 · **§5** T3 · **depends** BE-2 · **parallel-safe** **no — the most important write in the platform**
- `available → reserved` (hold), `reserved → sold` (confirm), `reserved → available` (release).
  Each **one** `findAndModify` with its condition, plus optimistic `@Version` (**D-08**).
- **Acceptance** 200-against-50 under **real contention on a replica set**; conservation asserted
  **throughout** an interleaved run, not only at the end. An end-state check passes on an
  implementation that goes briefly negative.
- [ROADMAP §Cross-cutting](../ROADMAP.md): *an event can never be sold beyond its capacity.*

### BE-4 · The on-sale predicate and its four boundary tests
- **Spec** R3 · **§5** T4 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** refuse at `salesStartAt − 1s`, succeed at `salesStartAt`, succeed at
  `salesEndAt − 1s`, **refuse at `salesEndAt`**. All four, frozen clock.
- Inclusive start, exclusive end. Getting that backwards sells one ticket after close, every time.

### BE-5 · Capacity change — the delta consumer and the committed-floor refusal
- **Spec** R4 · **§5** T5 · **depends** BE-3 · **parallel-safe** **no — spans two services**
- **Acceptance** a capacity change concurrent with **100 in-flight reservations** conserves; a
  reduction **below committed** refuses.
- Capacity moves by **delta**, never by assignment. Setting `available = newCapacity - sold`
  computed from a stale read silently destroys every concurrent hold.

### BE-6 · Purchase limits, including the `alreadyHeld` count
- **Spec** R5 · **§5** T6 · **depends** BE-3 · **parallel-safe** yes
- `alreadyHeld` counts `HELD` reservations **plus** `PURCHASED` and `VALIDATED` tickets — counting
  only tickets lets a buyer stack holds up to any limit.
- **Acceptance** two parallel reservations jointly exceeding `maxPerBuyer` yield **one** success.

### BE-7 · Promotion codes — atomic redemption, release on failure, the validation type
- **Spec** R6 · **§5** T7 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** 200 parallel redemptions of 50 remaining yield **exactly 50**; an abandoned
  checkout **releases** the redemption.
- A promo redemption is inventory with a different name, and it leaks the same way.

### BE-8 · Close versus delete; the tier status machine
- **Spec** R7 · **§5** T8 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** a tier **with sales cannot be deleted**; a **closed tier still resolves** for
  existing tickets.
- A ticket whose tier no longer resolves is a ticket that cannot be displayed or validated.

### BE-9 · The subgraph halves — catalog's type, booking's `extend`
- **Spec** R1 · **§5** T9 · **depends** BE-2 · **parallel-safe** **no — two SDL files must agree**
- **Acceptance** static composition; **no `id` inside the extend block** (the "tried to redefine
  field 'id'" failure).

## B · Contract

### GQL-1 · 3 queries, 10 mutations, across two subgraphs
- **depends** BE-9 · **parallel-safe** no
- Catalog owns `TicketTier`; booking `extend`s it with the live counts. That split is the whole
  federation story of this spec: **definition in catalog, truth in booking.**
- `compose-supergraph.sh --static` → `npm run codegen` → commit; restart the local router.

## C · Frontend

### FE-1 · Tier editor panel *(Org Admin - Event Editor)*
- **depends** GQL-1, F0-2 · **parallel-safe** no
- Name, price, capacity, sales window, `maxPerOrder`, `maxPerBuyer`. Panel order from the screen.
- Currency input in Kwacha; display as `K 12,500` with tabular Fira Code.
- `salesEndAt` prefilled from the event's `startsAt` (BE-1) and shown as a default, not a blank.
- **testids** `tier-editor`, `tier-name`, `tier-price`, `tier-capacity`, `tier-sales-window`, `tier-limits`, `tier-save`

### FE-2 · Capacity change with the committed floor
- **depends** FE-1 · **parallel-safe** yes
- Show sold and held **before** accepting a reduction; refuse below committed with the actual
  floor named, not a generic error.
- **testids** `tier-capacity-change`, `tier-committed-floor`, `tier-capacity-error`

### FE-3 · Close versus delete
- **depends** FE-1 · **parallel-safe** yes
- Delete is offered **only** when the tier has no sales (BE-8); otherwise the action is **Close**,
  and the copy says what closing does. Two different words for two different operations.
- **testids** `tier-close`, `tier-delete`, `tier-delete-blocked`

### FE-4 · Promo code management
- **depends** BE-7, GQL-1 · **parallel-safe** yes
- Code, discount, redemption limit, remaining, window. Remaining is live — it moves during on-sale.
- **testids** `promo-row`, `promo-create`, `promo-remaining`, `promo-revoke`

### FE-5 · Buyer-facing tier display *(Ticketing - Event Detail)*
- **depends** GQL-1, F0-1 · **parallel-safe** yes
- Price, availability, sold-out, not-yet-on-sale (with the opening time), closed.
- **Availability is a live number under contention.** Show a stale count and the buyer's
  reservation fails with `TIER_SOLD_OUT` after they have committed. Poll on a
  visibility-aware interval (**D-12** — polling, not subscriptions) and treat any count as
  advisory: the authority is the reservation attempt.
- Sold-out is a **designed state**, not a disabled button.
- **testids** `tier-option`, `tier-available`, `tier-soldout`, `tier-not-on-sale`, `tier-closed`

### FE-6 · Purchase-limit feedback
- **depends** BE-6 · **parallel-safe** yes
- The quantity selector caps at `min(maxPerOrder, remaining allowance)` and says which limit
  bound it. `PURCHASE_LIMIT_EXCEEDED` from the server renders the same message — the client cap
  is convenience, the server is the rule.
- **testids** `tier-quantity`, `tier-limit-reason`

## D · Tests

### TS-1 · Atomicity *(L3 — the load-bearing test in the corpus)*
- 200-against-50 on a **replica set** → exactly 50, repeated 20× for stability.
- `Inventory.assertConserved(tierId)` **throughout** an interleaved hold/confirm/release run.
- A deliberate read-modify-write implementation must **fail** this test — write it, watch it fail,
  delete it. That contrast is the only proof the test has teeth.

### TS-2 · Sales window *(L1, frozen clock)*
All four boundaries. Inclusive start, exclusive end.

### TS-3 · Capacity *(L3)*
Delta application concurrent with 100 in-flight reservations conserves; reduction below committed
refuses naming the floor.

### TS-4 · Limits *(L3)*
`alreadyHeld` counts holds **and** tickets; two parallel reservations exceeding `maxPerBuyer`
yield one success.

### TS-5 · Promo *(L3)*
200 parallel redemptions of 50 → 50; abandonment releases; expiry at the boundary.

### TS-6 · Tier lifecycle *(L2/L3)*
Tier with sales cannot be deleted; closed tier resolves for existing tickets.

### TS-7 · Federation *(L4)*
Static composition; no `id` in the extend block; the live count resolves from **booking**, the
definition from **catalog**.

### TS-8 · e2e *(L5)*
- Org-admin: tier CRUD, capacity floor refusal, close-vs-delete, promo management.
- Ticketing: available / sold-out / not-yet-on-sale / closed — all four rendered states.
- Quantity cap and its stated reason.
- Loading, empty, error, populated throughout. Apollo-driven → **F0-4** fixture.

## E · Gate

- [ ] R0 recorded; any read-modify-write decrement classified `contradicted`
- [ ] One conditional `findAndModify` per movement, plus `@Version`
- [ ] 200-against-50 exactly 50, stable over 20 runs, on a replica set
- [ ] Conservation holds **throughout** an interleaved run, not just at the end
- [ ] A read-modify-write implementation was written and **seen to fail** the test
- [ ] All four sales-window boundaries; inclusive start, exclusive end
- [ ] Capacity moves by delta; reduction below committed refuses with the floor named
- [ ] `alreadyHeld` counts holds and tickets
- [ ] 200 parallel promo redemptions of 50 → 50; abandonment releases
- [ ] Tier with sales cannot be deleted; closed tiers still resolve
- [ ] No `id` inside the booking extend block; static composition green
- [ ] Buyer sees all four tier states as designed screens
- [ ] `mvn -q -f backend/catalog-service test -Dgroups=ET-CAT-002 -DfailIfNoTests=false` green
- [ ] Spec `status:` → `implemented`
