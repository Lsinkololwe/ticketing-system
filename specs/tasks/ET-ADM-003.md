# ET-ADM-003 · Transaction recovery — stuck money and the operator's tools — tasks

> **Spec** [`specs/admin/003-transaction-recovery/spec.md`](../admin/003-transaction-recovery/spec.md) · **Wave 6** · `blocked_by:` ET-PLT-003, 005, ET-PAY-001, 002, ET-FIN-003, 004, 005
> **Screen** `Admin - Transaction Recovery.dc.html` — **read it first**
> **Routes** `apps/admin/src/app/(dashboard)/transactions/recovery`
> **Verify** `mvn -q -f backend/booking-service test -Dgroups=ET-ADM-003 -DfailIfNoTests=false`

The destination for everything the platform deliberately refused to guess about: escalated
reservations ([`ET-TKT-001`](ET-TKT-001.md) BE-7), never-answering payments
([`ET-PAY-001`](ET-PAY-001.md) BE-6), unmatched webhook orphans
([`ET-PAY-002`](ET-PAY-002.md) BE-6), stuck settlements ([`ET-FIN-003`](ET-FIN-003.md) BE-6) and
reconciliation discrepancies ([`ET-FIN-005`](ET-FIN-005.md)).

8 requirements, 62 boxes, **9 mutations** — the largest mutation surface in the corpus, because
each recovery action is its own carefully-bounded operation.

## R0 · Reconcile

`PurchaseEscalation`, `PurchaseEscalationService`, `PurchaseRecoveryService`,
`PurchaseRecoveryScheduler` and `PayoutRecoveryService` exist. 3 references to `ET-ADM-003`.

The decisive check: **does any existing recovery action invent an outcome?** Forcing a "success"
that the provider reports as failed is the single most dangerous thing in this spec, and R4 exists
to forbid it.

## A · Backend

### BE-1 · The queue projection across all eight sources
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** no
- **Acceptance** one item of **each** source appears; **resolving updates the source document**.
- A projection, not a copy. An item resolved in the queue but still stuck in its source document
  is a queue that lies.

### BE-2 · Ordering by amount at risk with the starvation floor
- **Spec** R1 · **§5** T2 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** **a K20 item older than 7 days floats above a K48,000 item from yesterday.**
- Pure value ordering starves small items forever. The person owed K20 waits indefinitely while
  bigger items keep arriving, and they are the one most likely to be told nothing.

### BE-3 · The `single` actions, each idempotent
- **Spec** R2, R4 · **§5** T3 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** each applied **twice yields one effect**; ledger and inventory hold.
- Operators double-click under stress. Every one of these actions moves money.

### BE-4 · Proposals, confirmation, expiry and the identity check
- **Spec** R3 · **§5** T4 · **depends** BE-3 · **parallel-safe** no
- **Acceptance** **one actor with every role cannot confirm their own proposal**; an **expired
  proposal applies nothing**.
- Dual control on the dangerous actions, same principle as
  [`ET-FIN-003`](ET-FIN-003.md) BE-5's requester ≠ approver. Holding both roles is not a bypass.

### BE-5 · The no-invention guard on `applyConfirmedOutcome`
- **Spec** R4 · **§5** T5 · **depends** BE-4 · **parallel-safe** yes
- **Acceptance** **forcing a success the provider reports as failed is refused.**
- The whole spec turns on this. An operator may resolve ambiguity; they may not overrule the
  provider. Recovery tools exist to apply the truth once it is known, not to assert one.

### BE-6 · Item detail, the provider-raw exception and its audit
- **Spec** R5 · **§5** T6 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** the raw provider message appears **here and nowhere else**; **every access is
  audited**.
- This is the one place provider internals are visible ([`ET-PAY-001`](ET-PAY-001.md) BE-9 hides
  them everywhere else), because an operator diagnosing a stuck payment needs the real error. It
  is audited precisely because it is an exception.

### BE-7 · The dead-letter surface, replay and discard
- **Spec** R6 · **§5** T7 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** replaying **one of each message type causes no double effect**; **nothing
  auto-deletes**.
- Replay is safe only because [`ET-PLT-003`](ET-PLT-003.md) BE-5 made every consumer idempotent —
  this is where that investment pays.

### BE-8 · Bulk retry, its safe classes and its per-id outcomes
- **Spec** R7 · **§5** T8 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** a mixed batch **skips unsafe ids**; **no bulk applies to a `dual` action.**
- Bulk is for the safe classes only. Applying a dual-control action in bulk would defeat the
  control, which is the same reasoning as [`ET-ADM-001`](ET-ADM-001.md) BE-6's no-bulk-reject rule.

### BE-9 · Metrics, thresholds and the per-item escalation alert
- **Spec** R8 · **§5** T9 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** seeded items above the threshold alert; **an item past 3 days alerts
  individually**.
- Aggregate alerts hide the single item that has been stuck for a week — which is exactly the one
  that becomes a complaint.

## B · Contract

### GQL-1 · 6 queries, 9 mutations
- **depends** BE-8 · **parallel-safe** no *(shared booking SDL — the most contended file in the corpus)*
- Every field `ADMIN`, `@tag`ged.

## C · Frontend — `Admin - Transaction Recovery.dc.html`

> **Infographics gate.**
> **Kernel:** *"This much money is stuck, and this is the oldest thing waiting."*
> **Total at risk** is the focal number; **oldest item age** is the second story. Not four equal
> tiles.

### FE-1 · The queue
- **depends** GQL-1 · **parallel-safe** no
- Source, amount at risk, age, state. Ordered by BE-2 — and **the starvation floor must be
  legible**, or an operator seeing a K20 item above a K48,000 one will think the sort is broken.
- Money in `K`, tabular Fira Code, right-aligned.
- **testids** `recovery-queue`, `recovery-row`, `recovery-amount`, `recovery-age`, `recovery-source`, `recovery-queue-empty`

### FE-2 · Item detail with the provider drawer
- **depends** BE-6 · **parallel-safe** no
- Timeline, related documents, and the **raw provider message** — the one place it appears.
- Show that access is audited. Not as a warning, as a fact.
- **testids** `recovery-detail`, `recovery-timeline`, `recovery-provider-raw`, `recovery-audit-notice`

### FE-3 · Single actions
- **depends** BE-3 · **parallel-safe** yes
- Each states what it will do to money and inventory **before** firing. Idempotent server-side
  (BE-3), and the button disables during flight — both, because neither alone is enough.
- **testids** `recovery-action-<name>`, `recovery-action-confirm`, `recovery-action-effect-summary`

### FE-4 · Dual-control proposals
- **depends** BE-4 · **parallel-safe** no
- Propose → a second operator confirms. Proposer, reason, expiry visible.
- **The proposer's own confirm control is absent**, not disabled-with-a-tooltip (BE-4). And the
  server refuses regardless.
- **testids** `recovery-propose`, `recovery-proposal-row`, `recovery-proposal-confirm`, `recovery-proposal-expiry`, `recovery-self-confirm-absent`

### FE-5 · The refusal to invent
- **depends** BE-5 · **parallel-safe** yes
- When an operator attempts an outcome the provider contradicts, the refusal explains **what the
  provider says** and what can be done instead. A bare "not permitted" here sends the operator
  looking for a workaround.
- **testids** `recovery-invention-refused`, `recovery-provider-truth`

### FE-6 · Dead letters
- **depends** BE-7 · **parallel-safe** yes
- Message type, failure reason ([`ET-PLT-003`](ET-PLT-003.md) BE-6 guarantees there is one), age.
  Replay and discard. Discard confirms and is audited; **nothing auto-deletes**, and the UI must
  not imply it does.
- **testids** `deadletter-row`, `deadletter-reason`, `deadletter-replay`, `deadletter-discard`, `deadletter-discard-confirm`

### FE-7 · Bulk retry
- **depends** BE-8 · **parallel-safe** yes
- Safe classes only. Unsafe ids are **visibly excluded from selection with the reason** — not
  silently dropped after submission.
- Per-id outcomes.
- **testids** `bulk-retry-select`, `bulk-retry-excluded`, `bulk-retry-submit`, `bulk-retry-result-row`

### FE-8 · Header metrics
- **depends** BE-9 · **parallel-safe** yes
- Total at risk (focal), oldest age, count by source, items past 3 days.
- **testids** `recovery-total-at-risk`, `recovery-oldest`, `recovery-by-source`, `recovery-overdue`

## D · Tests

### TS-1 · Projection *(L3)*
One item from **each of the eight sources**; resolving updates the source document — assert on the
source, not the queue.

### TS-2 · Ordering *(L1/L3)* — the K20/7-day vs K48,000/yesterday case, exactly as specified.

### TS-3 · Idempotency *(L3)*
Every `single` action applied twice → one effect; `Ledger.assertBalanced()` and
`Inventory.assertConserved()` after each.

### TS-4 · Dual control *(L3)*
One actor holding **every** role cannot confirm their own proposal; expired proposals apply
nothing.

### TS-5 · No invention *(L3, WireMock — the most important test here)*
Provider reports failure; forcing success is **refused**. Repeat across every action that could
assert an outcome.

### TS-6 · Provider exposure *(L3 + L4)*
Raw message appears **only** on this surface — assert its absence from the composed public
contract and from every other resolver. Every access audited.

### TS-7 · Dead letters *(L3)*
Replay one of **each** message type → no double effect; **nothing auto-deletes**.

### TS-8 · Bulk *(L3)* — mixed batch skips unsafe ids; **no bulk path reaches a `dual` action**.

### TS-9 · Alerts *(L3)* — threshold alerts; an item past 3 days alerts **individually**.

### TS-10 · e2e *(L5, admin)*
- Queue with the starvation floor legible; empty state (nothing stuck **is** success).
- Detail with the provider drawer; audit notice.
- Single action with its effect summary; double-click yields one effect.
- Propose → self-confirm control **absent** → second operator confirms.
- Invention refused with the provider's truth shown.
- Dead-letter replay and discard-with-confirm.
- Bulk with excluded ids and reasons.
- Loading, empty, error, populated throughout.

## E · Gate

- [ ] R0 recorded; any outcome-inventing action classified `contradicted`
- [ ] All eight sources project into the queue; resolution updates the source
- [ ] Starvation floor works and is legible in the UI
- [ ] Every single action idempotent; ledger and inventory hold
- [ ] Dual control unbypassable by role accumulation; expired proposals do nothing
- [ ] **Forcing an outcome the provider contradicts is refused**
- [ ] Raw provider messages appear only here, and every access is audited
- [ ] Dead-letter replay causes no double effect; nothing auto-deletes
- [ ] Bulk never reaches a `dual` action; unsafe ids excluded visibly
- [ ] Items past 3 days alert individually
- [ ] **Infographics gate passed**; total-at-risk is the single focal number
- [ ] `mvn -q -f backend/booking-service test -Dgroups=ET-ADM-003 -DfailIfNoTests=false` green
- [ ] Spec `status:` → `implemented`
