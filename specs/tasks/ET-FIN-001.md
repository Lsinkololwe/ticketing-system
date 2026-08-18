# ET-FIN-001 · Per-event escrow, the chart of accounts and double-entry — tasks

> **Spec** [`specs/finance/001-escrow-and-ledger/spec.md`](../finance/001-escrow-and-ledger/spec.md) · **Wave 3** · `blocked_by:` ET-PLT-002, 003, 005, 006, ET-TKT-001, ET-CAT-001
> **Screen** `Admin - Ledger, Commission & Reconciliation.dc.html` — **read it first**
> **Routes** `apps/admin/src/app/(dashboard)/analytics/ledger`, `finance/escrow`
> **Authority** `docs/ARCHITECTURE_REDESIGN_V3_COMPLETE.md` §3, §11, §13 — **a spec contradicting it is the spec that is wrong**, and a task contradicting either is a defect
> **Verify** `mvn -q -f backend/booking-service test -Dgroups=ET-FIN-001 -DfailIfNoTests=true` · `compose-supergraph.sh --static`

**D-05: one escrow account per event, not per organizer.** Cancelling one event must not reach
into another event's settled funds, and a per-event balance is what makes a refund obligation
computable at all.

The invariant everything downstream rests on: **no balance is written except as a double-entry
pair**, and the ledger is **append-only**.

## R0 · Reconcile

`EventEscrowAccount`, `JournalEntry`, `EscrowService`, `StandaloneEscrowTransaction`,
`EscrowTransactionMigrationService`, `EscrowDocumentConformanceMigrationService` and
`EscrowStatusConformanceMigrationService` all exist. 23 references to `ET-FIN-001`.

The classifications that matter:
- Is any balance **assigned** rather than derived from journal entries? Direct assignment is
  `contradicted` and is the defect this whole spec exists to prevent.
- Is any journal entry **updated or deleted** anywhere? Append-only means append-only.
- Is escrow **per event** or per organizer? Per organizer is `contradicted` (D-05).
- Are those three migration services actually invoked at boot, or orphaned beans?

## A · Backend

### BE-1 · Seed the chart of accounts and the three platform singletons
- **Spec** R1, R7 · **§5** T1 · **depends** R0 · **parallel-safe** no
- **Acceptance** idempotent seeding; a **second platform account of any type is rejected**.
  Two platform commission accounts means the platform's own revenue is split across two places
  and every total is quietly wrong.

### BE-2 · `LedgerService` — balanced entries, append-only, the validator
- **Spec** R2 · **§5** T2 · **depends** BE-1 · **parallel-safe** **no — every money spec depends on it**
- **Acceptance** an **unbalanced entry refuses**; **no method updates or deletes an entry**.
- Corrections are **reversing entries**, not edits. An editable ledger is not a ledger; it is a
  table of current opinions.
- [ROADMAP §Cross-cutting](../ROADMAP.md): *no balance is written except as a double-entry pair* —
  this spec **and lint**.

### BE-3 · Balances as projections; `recomputeBalance`; the 1,000-movement test
- **Spec** R3 · **§5** T3 · **depends** BE-2 · **parallel-safe** no
- **Acceptance** no `double`/`float` in any monetary field; every rounding `HALF_UP`; **no balance
  assigned outside the ledger**; **cached equals recomputed exactly** after 1,000 movements.
- Exactly — not within a tolerance. A cached balance that drifts by a ngwee over 1,000 movements
  drifts by kwacha over a season, and there is no point at which anyone notices.

### BE-4 · The escrow account, its unique index and its lifecycle consumers
- **Spec** R4 · **§5** T4 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** a re-publish opens **no second account**; a reschedule **recomputes `holdUntil`**.
- One account per event, enforced by a unique index — confirm it live via MongoDB MCP. A
  reschedule moves the event, so it must move the hold, or funds release before an event that has
  not happened.

### BE-5 · The purchase split entry and the separate provider-fee entry
- **Spec** R5 · **§5** T5 · **depends** BE-4 · **parallel-safe** **no — inside the confirmation transaction**
- **Acceptance** 100 tickets across 3 tiers sum **exactly**; **the provider fee never touches the
  buyer's charge**.
- The buyer pays the ticket price. The aggregator's fee is a platform cost booked separately —
  folding it into the buyer's charge changes the price the buyer agreed to. V3 §11.

### BE-6 · The conditional atomic debit and the negative-balance invariant test
- **Spec** R6 · **§5** T6 · **depends** BE-5 · **parallel-safe** no
- **Acceptance** two parallel over-debits yield **one** success; **no balance is ever negative**,
  asserted throughout an interleaved run rather than at the end.

### BE-7 · The hold-release sweep and the dispute block
- **Spec** R4 · **§5** T7 · **depends** BE-4 · **parallel-safe** yes
- **Acceptance** an account with an **open dispute does not become `PAYOUT_ELIGIBLE`**.
- Paying out money that is under dispute means clawing it back from an organizer who has already
  spent it.

### BE-8 · Manual adjustments, the reversal link, the audit row and the metric
- **Spec** R8 · **§5** T8 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** **no existing entry is ever modified**; every adjustment is audited and metered.
- The metric matters: manual adjustments are a symptom. A rising count means something upstream is
  wrong and is being papered over by hand.

### BE-9 · The subgraph half; `@tag(name: "admin")` on every finance field
- **Spec** R1–R8 · **§5** T9 · **depends** BE-5 · **parallel-safe** **no — shared booking SDL**
- **Acceptance** the public contract exposes **no ledger, platform-account or journal field**.
  Platform account balances are the business's own P&L.

## B · Contract

### GQL-1 · 7 queries, 5 mutations
- **depends** BE-9 · **parallel-safe** no
- Every field `ADMIN` and `@tag`ged; organizer-visible escrow balance is a **separate, narrow
  projection** ([`ET-FIN-003`](ET-FIN-003.md)), not the admin type with fields hidden.

## C · Frontend — `Admin - Ledger, Commission & Reconciliation.dc.html`

> **Before writing any chart, stat tile or ledger visual, run the infographics gate**
> ([`infographics-skill`](../../frontend/web/.claude/skills/infographics-skill/SKILL.md)): kernel →
> spec → rubric → build. No code until the spec passes. This screen is dense with numbers and is
> exactly where three competing focal points appear by default.
>
> **Kernel for this screen:** *"Every kwacha the platform holds is accounted for, and the ledger
> proves it."* Everything that does not serve that gets cut.

### FE-1 · Trial balance — the focal element
- **depends** GQL-1 · **parallel-safe** no
- One number dominates: **debits − credits**, which must be zero. It is the largest type on the
  page, because it is the finding; a title larger than the finding inverts the hierarchy.
- Non-zero is not a red chip — it is an alarm state that names the imbalance and links to the
  entries.
- Currency `K 125,430`, **tabular Fira Code**, right-aligned so digits align.
- **testids** `trial-balance`, `trial-balance-delta`, `trial-balance-alarm`

### FE-2 · Chart of accounts
- **depends** GQL-1 · **parallel-safe** yes
- Account, type, balance. Grouped by type, not sorted by balance — the grouping is the structure.
- Gray carries structure; the accent marks only what the kernel is about. Money uses
  `--color-money` (jade) **semantically** — never as a brand accent.
- **testids** `accounts-table`, `account-row`, `account-balance`

### FE-3 · Journal entry explorer
- **depends** GQL-1 · **parallel-safe** yes
- Every entry shows **both legs**. Filter by account, date, type. Reversals link to what they
  reverse, in both directions.
- **No edit affordance anywhere.** The ledger is append-only, and a UI that appears to offer
  editing misrepresents the system.
- Wide table → scrolls inside its own container; the page body never scrolls horizontally.
- **testids** `journal-table`, `journal-entry-row`, `journal-leg`, `journal-reversal-link`

### FE-4 · Escrow accounts by event
- **depends** GQL-1 · **parallel-safe** yes
- Balance, hold-until, status, dispute flag. Disputed accounts are visibly blocked from payout
  eligibility **with the reason** (BE-7).
- **testids** `escrow-table`, `escrow-balance`, `escrow-hold-until`, `escrow-dispute-flag`

### FE-5 · Manual adjustment
- **depends** BE-8 · **parallel-safe** yes
- Both legs entered explicitly; the form **refuses to submit unbalanced** — the client mirrors the
  server rule so the operator sees the imbalance before it is rejected.
- Reason mandatory. Confirm before firing; it is audited and metered.
- **testids** `adjustment-form`, `adjustment-debit`, `adjustment-credit`, `adjustment-balance-check`, `adjustment-reason`, `adjustment-submit`

### FE-6 · Escrow trend *(only if it survives the gate)*
- **depends** FE-4 · **parallel-safe** yes
- If a chart is built: **position over length over angle over area**; bar baselines at zero; one
  y-axis; direct labels rather than a legend; one annotation on the focal point.
- If the kernel is served by a number, ship the number. A chart that restates one figure is
  decoration.

## D · Tests

### TS-1 · Chart of accounts *(L3)*
Idempotent seeding; a second platform account of any type rejected.

### TS-2 · Ledger invariants *(L1 + L3 — the corpus's most important money tests)*
- Unbalanced entry refuses.
- **No method updates or deletes an entry** — assert by reflection over the repository, so a
  future `save()` cannot slip in.
- `Ledger.assertBalanced()` after every operation in the suite.

### TS-3 · Balance projection *(L3)*
1,000 movements: cached **equals** recomputed, exactly. No `double`/`float` money. No balance
assigned outside the ledger (source scan).

### TS-4 · Escrow lifecycle *(L3)*
Re-publish opens no second account (unique index live via MCP); reschedule recomputes `holdUntil`;
disputed accounts never reach `PAYOUT_ELIGIBLE`.

### TS-5 · Purchase split *(L3)*
100 tickets × 3 tiers sum exactly; the provider fee is a separate entry and never touches the
buyer's charge.

### TS-6 · Debit safety *(L3)*
Two parallel over-debits → one success; balance never negative **throughout** an interleaved run.

### TS-7 · Adjustments *(L3)*
No entry modified; every adjustment audited; the metric increments.

### TS-8 · Contract *(L4)*
Public contract exposes no ledger, platform-account or journal field.

### TS-9 · e2e *(L5, admin)*
- Trial balance zero and non-zero (alarm) states.
- Journal explorer: filters, both legs, reversal links, **no edit affordance**.
- Adjustment form refuses unbalanced input client-side.
- Escrow table with a disputed account showing its block reason.
- Loading, empty, error, populated. Compliance: teal, Inter, tokens, tabular Fira Code for money.

## E · Gate

- [ ] R0 recorded; any assigned balance or mutable journal entry classified `contradicted`
- [ ] Escrow is **per event**, one account, unique index confirmed live
- [ ] Unbalanced entries refuse; no update or delete path exists on any entry
- [ ] Cached balance **equals** recomputed after 1,000 movements
- [ ] No `double`/`float` money; `HALF_UP` throughout
- [ ] Provider fee separate; never part of the buyer's charge
- [ ] Balance never negative; parallel over-debits yield one success
- [ ] Disputed accounts cannot become payout-eligible
- [ ] Adjustments append reversals, are audited and metered
- [ ] Public contract exposes no finance field
- [ ] **Infographics gate passed before any chart or stat tile was coded**
- [ ] Trial balance is the focal element; no competing focal points
- [ ] No edit affordance anywhere in the journal UI
- [ ] `mvn -q -f backend/booking-service test -Dgroups=ET-FIN-001 -DfailIfNoTests=true` green
- [ ] Spec `status:` → `implemented` — **Wave 4 does not open until all of Wave 3 is**
