# ET-FIN-002 · Commission — rate resolution and two-stage recognition — tasks

> **Spec** [`specs/finance/002-commission/spec.md`](../finance/002-commission/spec.md) · **Wave 4** · `blocked_by:` ET-PLT-005, 006, ET-FIN-001, ET-CAT-001, ET-CAT-002
> **Screens** `Admin - Ledger, Commission & Reconciliation.dc.html` *(rate card, records, recognition)* · `Org Admin - Event Editor.dc.html` *(commission preview)*
> **Authority** `docs/ARCHITECTURE_REDESIGN_V3_COMPLETE.md` §2
> **Verify** `mvn -q -f backend/booking-service test -Dgroups=ET-FIN-002 -DfailIfNoTests=false`

**D-04: two-stage — pending at purchase, recognised at event completion.** Money owed on a ticket
for an event that is later cancelled **was never revenue**, and a platform that books it at
purchase reports a profit it must then reverse.

## R0 · Reconcile

Classify. The decisive questions:
- Is commission **recognised at purchase**? That is `contradicted` — the whole point of D-04.
- Is commission apportioned at **order** level or **line** level? Order-level apportionment
  produces rounding that does not reconcile per ticket, which then does not reconcile per refund.
- Is the resolved **rate snapshotted on the ticket**, or re-read at refund time? Re-reading means a
  rate-card change retroactively alters historical refunds.

## A · Backend

### BE-1 · The rate card, the resolution method and its layer-1 tests
- **Spec** R1, R3 · **§5** T1 · **depends** R0 · **parallel-safe** no
- **Acceptance** **lowest applicable rate wins**; boundary tests at **999 / 1000 / 1001**.
- Both sides of every band edge. A band test at 1000 alone passes on `>` and on `>=`.

### BE-2 · The computation and its two identities, over 1,000 randomised cases
- **Spec** R2 · **§5** T2 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** `net + commission == lineAfterDiscount` for **every** case; **no order-level
  apportionment**.
- Randomised, not hand-picked. Hand-picked cases test the cases the author already thought about;
  1,000 random ones find the rounding edge nobody did.

### BE-3 · The record, written with the sale, in the same transaction
- **Spec** R4 · **§5** T3 · **depends** BE-2 · **parallel-safe** **no — inside the confirmation transaction**
- **Acceptance** **no path credits `4010` from a purchase**; the `PENDING` sum **equals** the
  `2020` balance.
- `4010` is recognised revenue. A purchase touching it is the exact defect D-04 exists to prevent,
  so assert it as a source-level prohibition, not only behaviourally.

### BE-4 · Recognition in the finance workflow — batched, idempotent, contended
- **Spec** R5 · **§5** T4 · **depends** BE-3 · **parallel-safe** no
- **Acceptance** two concurrent recognitions recognise each record **once**; a **cancelled event never
  recognises**.
- Triggered by `EventFinanceWorkflow` once [`ET-CAT-001`](ET-CAT-001.md)'s completion has arrived and the
  hold has elapsed, which is why completion's idempotence matters here: a double completion would
  double-recognise revenue.

### BE-5 · Cancellation and clawback paths, and the clawback metric
- **Spec** R6 · **§5** T5 · **depends** BE-4 · **parallel-safe** yes
- **Acceptance** a **pre-recognition** refund never touches `4010`; a **post-recognition** one
  does; **both balance**.
- The two paths differ because the accounting differs — before recognition the liability simply
  never became revenue; after, revenue must be reversed. Collapsing them into one path is wrong in
  one direction or the other.

### BE-6 · The preview, sharing the resolution method
- **Spec** R7 · **§5** T6 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** the preview and the sale return **identical rates for identical inputs** — because
  they call the same method. A separate preview calculation drifts, and the organizer is quoted a
  rate they are not charged.

### BE-7 · The per-organization override applied at resolution
- **Spec** R1 · **§5** T7 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** an override replaces the resolved rate **and is snapshotted on the ticket**.

### BE-8 · The subgraph half; organizer-scoped versus `@tag(name: "admin")` fields
- **Spec** R7 · **§5** T8 · **depends** BE-6 · **parallel-safe** **no — shared booking SDL**
- **Acceptance** an organizer sees **their own** commission and **no platform aggregate**. The
  platform's total take is not an organizer's business.

## B · Contract

### GQL-1 · 4 queries, 1 mutation
- **depends** BE-8 · **parallel-safe** no

## C · Frontend

> **Infographics gate before any chart or stat tile.**
> **Kernel (admin):** *"Commission owed is not commission earned until the event happens."*
> The two-stage split **is** the message; a single "commission" total destroys it.

### Admin · `Admin - Ledger, Commission & Reconciliation.dc.html`

### FE-1 · Pending versus recognised — never one number
- **depends** GQL-1 · **parallel-safe** no
- Two figures, visibly distinct, with the relationship stated. `PENDING` is a **liability**
  (`2020`); recognised is **revenue** (`4010`).
- Money in `--color-money` (jade), **semantically** — a pending chip must not read as a brand chip.
- **testids** `commission-pending`, `commission-recognised`, `commission-split`

### FE-2 · Rate card management
- **depends** GQL-1 · **parallel-safe** yes
- Bands with their thresholds; lowest-applicable-wins stated in the UI, because a reader looking
  at overlapping bands cannot infer the tie-break.
- Per-organization overrides listed separately from the default card.
- **testids** `rate-card-table`, `rate-band-row`, `rate-override-row`, `rate-band-edit`

### FE-3 · Commission records
- **depends** GQL-1 · **parallel-safe** yes
- Per ticket: rate applied (snapshotted), amount, status, recognition date. Filter by event and
  status.
- **testids** `commission-records-table`, `commission-record-row`, `commission-record-status`

### FE-4 · Clawback visibility
- **depends** BE-5 · **parallel-safe** yes
- Clawbacks shown with their metric. A rising clawback rate is a signal about event quality, not a
  routine line item — surface it where it will be seen.
- **testids** `clawback-total`, `clawback-rate`

### Organizer · `Org Admin - Event Editor.dc.html`

### FE-5 · Commission preview
- **depends** BE-6, GQL-1, F0-2 · **parallel-safe** yes
- While setting tier prices: *at this price, you receive `K X` per ticket and the platform takes
  `K Y`*. Live as the price changes.
- **The preview must call the server's preview query**, never recompute the bands in TypeScript.
  A client-side copy of the rate card is a second resolver, and it will disagree.
- **testids** `commission-preview`, `commission-preview-net`, `commission-preview-fee`

### FE-6 · Organizer commission view
- **depends** BE-8 · **parallel-safe** yes
- Own commission only. **No platform aggregate** anywhere in org-admin.
- **Acceptance** the compliance suite asserts org-admin queries no admin-tagged commission field.

## D · Tests

### TS-1 · Rate resolution *(L1)*
Lowest applicable wins; 999 / 1000 / 1001; overrides replace the resolved rate.

### TS-2 · Computation *(L1)*
1,000 randomised cases: `net + commission == lineAfterDiscount` exactly. No order-level
apportionment — assert per line.

### TS-3 · Two-stage *(L3 — the core)*
- **No purchase path credits `4010`** (source scan **and** behavioural).
- `PENDING` sum equals the `2020` balance.
- Two concurrent recognitions recognise each record once.
- A cancelled event never recognises.

### TS-4 · Clawback *(L3)*
Pre-recognition refund does not touch `4010`; post-recognition does; `Ledger.assertBalanced()`
after both.

### TS-5 · Preview parity *(L1/L2)*
Preview and sale return identical rates for identical inputs, across the randomised case set from
TS-2 — not one sample.

### TS-6 · Contract *(L4)*
An organizer token retrieves own commission and **no** platform aggregate.

### TS-7 · e2e *(L5)*
- Admin: pending and recognised shown separately; rate card edit; records filtered; clawbacks.
- Org-admin: live preview as tier price changes; no platform figures anywhere.
- Loading, empty, error, populated. Money as `K`, tabular Fira Code, jade used semantically only.

## E · Gate

- [ ] R0 recorded; recognition-at-purchase or order-level apportionment classified `contradicted`
- [ ] Lowest applicable rate wins; both sides of every band boundary
- [ ] 1,000 randomised cases satisfy both identities exactly
- [ ] No purchase path credits `4010`
- [ ] `PENDING` sum equals the `2020` balance
- [x] Concurrent recognition sweeps recognise once; cancelled events never recognise — recognition runs inside the event's single `event-finance/{eventId}` execution (no sweep exists to run concurrently); `EventFinanceWorkflowTest.holdsThenBecomesEligible` recognises once, `aCancellationRefundsEveryone` never
- [ ] Pre- and post-recognition refunds take different paths and both balance
- [ ] Preview shares the resolver's method — no TypeScript rate logic
- [ ] Rate snapshotted on the ticket
- [ ] Organizers see no platform aggregate
- [ ] **Infographics gate passed; pending and recognised are never merged into one number**
- [ ] `mvn -q -f backend/booking-service test -Dgroups=ET-FIN-002 -DfailIfNoTests=false` green
- [ ] Spec `status:` → `implemented`
