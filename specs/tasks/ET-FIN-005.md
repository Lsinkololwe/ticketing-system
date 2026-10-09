# ET-FIN-005 · Reconciliation — proving the ledger against the world — tasks

> **Spec** [`specs/finance/005-reconciliation/spec.md`](../finance/005-reconciliation/spec.md) · **Wave 4** · `blocked_by:` ET-PLT-005, ET-FIN-001, 002, 003, 004, ET-PAY-002
> **Screen** `Admin - Ledger, Commission & Reconciliation.dc.html` — **three reconciliation types, seven discrepancy classes**
> **Authority** `docs/ARCHITECTURE_REDESIGN_V3_COMPLETE.md` §11, §12
> **Verify** `mvn -q -f backend/booking-service test -Dgroups=ET-FIN-005 -DfailIfNoTests=false`

The spec that makes every other finance spec checkable. [ROADMAP §Cross-cutting](../ROADMAP.md)
gives it one job: **the ledger and every cached balance reconcile.**

## R0 · Reconcile

Classify. Note that a reconciliation implementation that **auto-creates** missing records to make
totals agree is worse than none — it converts a detectable discrepancy into a silent
fabrication. If any auto-create path exists, it is `contradicted`.

## A · Backend

### BE-1 · The trial balance query, its index and the paging alert
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** no
- **Acceptance** `explain()` reports `IXSCAN`; **a directly inserted unbalanced entry is caught
  within a cycle.**
- Test it by writing an unbalanced pair straight into MongoDB, bypassing `LedgerService` — the
  point is to catch what got past the service layer.

### BE-2 · Internal reconciliation — compare, correct below the limit, escalate above
- **Spec** R2 · **§5** T2 · **depends** BE-1 · **parallel-safe** no
- **Acceptance** a **K5 drift corrects** and a **K5,000 drift escalates**; **no journal entry is
  written by a correction**.
- Corrections repair the cached **projection**, not the ledger. A reconciliation that writes to
  the ledger to make it agree with a cache has inverted which one is authoritative.

### BE-3 · The run and item documents, and the item lifecycle
- **Spec** R5 · **§5** T3 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** re-running a window creates **no duplicate open items**; a second concurrent run
  **refuses**.

### BE-4 · Provider reconciliation and the four match classes
- **Spec** R3 · **§5** T4 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** each class produced by a seeded scenario; **nothing is auto-created**.

### BE-5 · Bank settlement — the `1010` credit and the fee expense
- **Spec** R4 · **§5** T5 · **depends** BE-3 · **parallel-safe** **no — it closes [`ET-FIN-001`](ET-FIN-001.md)'s open loop**
- **Acceptance** `1010` after a two-day window **equals unsettled collections**; the entry balances.
- Until the aggregator actually settles to the bank, collected money is a receivable. This is the
  entry that turns it into cash, and without it the platform's cash position is permanently
  overstated.

### BE-6 · Payout matching and `PAYOUT_UNCONFIRMED`
- **Spec** R4 · **§5** T6 · **depends** BE-5 · **parallel-safe** yes
- **Acceptance** an unmatched payout past **3 days escalates as critical**. A payout the bank
  never confirms is money that may not have arrived — an organizer is waiting and does not know.

### BE-7 · Escalation ageing, the metrics and the write-off gate
- **Spec** R5, R6 · **§5** T7 · **depends** BE-4 · **parallel-safe** yes
- **Acceptance** each class escalates **once** at its threshold; a **K501 write-off requires
  `SUPER_ADMIN`**.
- Both sides of the write-off threshold. Write-off is the one operation that makes a discrepancy
  disappear without explaining it, so it carries the highest authority requirement in the platform.

### BE-8 · Read preference, batching and the on-sale contention test
- **Spec** R7 · **§5** T8 · **depends** BE-4 · **parallel-safe** no
- **Acceptance** **reservation latency during a full reconciliation stays within the stated bound.**
- Reconciliation reads the entire journal. Run it against the primary during on-sale and it
  competes with the platform's most contended write (**D-16**: 5,000 reservations/minute).
  Secondary reads, batched, are what make it safe to run at all.

### BE-9 · The subgraph half; every field `FINANCE` and `@tag(name: "admin")`
- **Spec** R1–R7 · **§5** T9 · **depends** BE-5 · **parallel-safe** **no — shared booking SDL**
- **Acceptance** the public contract exposes **no reconciliation field**.

## B · Contract

### GQL-1 · 6 queries, 5 mutations
- **depends** BE-9 · **parallel-safe** no

## C · Frontend — `Admin - Ledger, Commission & Reconciliation.dc.html`

> **Run the infographics gate before writing any of this screen.** Seven discrepancy classes and
> three reconciliation types is precisely the data that becomes three competing focal points and a
> rainbow palette.
>
> **Kernel:** *"The ledger agrees with the world — or here is exactly where it does not."*
>
> One focal point: the **reconciled / not-reconciled** state. Everything else is supporting.
> Discrepancy classes are **labelled directly**, never by a seven-colour legend — a categorical
> palette caps at five, and seven classes encoded by hue alone fails both the cap and colour-vision
> deficiency. Class is carried by **position and label**; colour carries only severity.

### FE-1 · Reconciliation status — the focal element
- **depends** GQL-1 · **parallel-safe** no
- One state, dominant: reconciled, or **N open discrepancies worth `K X`**. Last run time, next run.
- Not reconciled is an alarm, not a chip.
- **testids** `reconciliation-status`, `reconciliation-open-count`, `reconciliation-open-value`, `reconciliation-last-run`

### FE-2 · The three types
- **depends** FE-1 · **parallel-safe** yes
- Internal (ledger vs cached balances), provider (platform vs PawaPay), bank (platform vs
  settlement). Each with its own state and last run — they fail independently and for different
  reasons.
- **testids** `reconciliation-type-internal`, `reconciliation-type-provider`, `reconciliation-type-bank`

### FE-3 · Discrepancy items
- **depends** GQL-1 · **parallel-safe** yes
- Class, amount, age, escalation state. Grouped by **class** (structure), coloured by **severity**
  (meaning). Ageing is visible because age is what turns a discrepancy into a loss.
- Wide table scrolls inside its own container; the page never scrolls horizontally.
- **testids** `discrepancy-row`, `discrepancy-class`, `discrepancy-age`, `discrepancy-severity`

### FE-4 · Resolution actions
- **depends** BE-7 · **parallel-safe** yes
- Investigate, resolve, write off. **Write-off requires `SUPER_ADMIN`, confirms, and demands a
  reason** — it is the action that makes a number disappear.
- **testids** `discrepancy-investigate`, `discrepancy-resolve`, `discrepancy-writeoff`, `discrepancy-writeoff-reason`, `discrepancy-writeoff-blocked`

### FE-5 · Trial balance panel
- **depends** [`ET-FIN-001`](ET-FIN-001.md) FE-1 · **parallel-safe** yes
- Shares the component. One trial balance across the platform, not two implementations that can
  disagree.

### FE-6 · Run history
- **depends** BE-3 · **parallel-safe** yes
- Per run: window, duration, items opened and closed. A run that opens more than it closes is the
  trend that matters.
- **testids** `reconciliation-run-row`, `reconciliation-run-duration`, `reconciliation-run-delta`

## D · Tests

### TS-1 · Trial balance *(L3)*
`IXSCAN`; a **directly inserted** unbalanced entry is caught within one cycle.

### TS-2 · Internal *(L3)*
K5 corrects, K5,000 escalates — both sides of the limit. **No journal entry written by a
correction**, asserted on the journal.

### TS-3 · Runs *(L3)*
Re-run creates no duplicate open items; a second concurrent run refuses.

### TS-4 · Provider *(L3, WireMock)*
Each of the four match classes from a seeded scenario; **nothing auto-created** — assert record
counts are unchanged by a reconciliation run.

### TS-5 · Bank *(L3)*
`1010` after two days equals unsettled collections; the entry balances; an unmatched payout past
3 days escalates critical.

### TS-6 · Escalation *(L3, frozen clock)*
Each class escalates **once** at its threshold — run the `ESCALATION` workflow repeatedly and assert no
duplicate escalation. K499 and K501 write-offs on both sides of the `SUPER_ADMIN` gate.

### TS-7 · Contention *(L3 — the one that protects on-sale)*
Full reconciliation **concurrent with** 200-against-50 reservations: reservation latency stays
within the bound and inventory is still conserved.

### TS-8 · Contract *(L4)* — public contract exposes no reconciliation field.

### TS-9 · e2e *(L5, admin)*
- Reconciled and not-reconciled states.
- Seven discrepancy classes rendered — **labelled, not legend-coded**; verify no more than five
  hues carry meaning.
- Write-off blocked for a non-`SUPER_ADMIN`; allowed with reason for one.
- Run history showing an opens-exceed-closes run.
- Loading, empty (fully reconciled **is** the empty state and it should look like success), error,
  populated.

## E · Gate

- [ ] R0 recorded; any auto-create path classified `contradicted`
- [ ] Directly inserted unbalanced entry caught within a cycle
- [ ] Corrections repair the projection and **never** write a journal entry
- [ ] Both sides of the correction limit
- [ ] Re-runs create no duplicate items; concurrent runs refuse
- [ ] All four provider match classes; nothing auto-created
- [ ] `1010` equals unsettled collections after the window
- [ ] Unmatched payouts escalate critical at 3 days
- [ ] Each class escalates exactly once; write-off gated on `SUPER_ADMIN` with a reason
- [ ] **Reservation latency holds during a full reconciliation run**
- [ ] **Infographics gate passed**; one focal point; classes labelled not legend-coded; ≤5 meaningful hues
- [ ] Trial balance component shared with ET-FIN-001, not reimplemented
- [ ] `mvn -q -f backend/booking-service test -Dgroups=ET-FIN-005 -DfailIfNoTests=false` green
- [ ] Spec `status:` → `implemented` — **Wave 5 does not open until all of Wave 4 is**
