# ET-FIN-004 · Refunds, cancellation refunds and chargebacks — tasks

> **Spec** [`specs/finance/004-refunds-and-chargebacks/spec.md`](../finance/004-refunds-and-chargebacks/spec.md) · **Wave 4** · `blocked_by:` ET-PLT-005, 007, ET-FIN-001, 002, 003, ET-CAT-001, ET-TKT-002
> **Screens** `Admin - Ledger, Commission & Reconciliation.dc.html` *(approval, contest)* · `Ticketing - My Tickets & Transfer.dc.html` *(refund quote — the Coverage map marks this **partial**)*
> **Authority** `docs/ARCHITECTURE_REDESIGN_V3_COMPLETE.md` §9, §14
> **Verify** `mvn -q -f backend/booking-service test -Dgroups=ET-FIN-004 -DfailIfNoTests=false`

8 requirements, **65 acceptance boxes** — the largest box count in the corpus, because every
(reason × fee-bearer × timing) combination is a distinct arithmetic that must balance.

## R0 · Reconcile

`RefundService`, `ChargebackService`, `ChargebackRecord`, `ChargebackStatus` (deleted),
`ChargebackResolvedEvent`, `RefundCompletedEvent` exist. 16 references to `ET-FIN-004`.

The decisive checks:
- Is the refund **fee bearer** modelled at all, or does the platform always absorb the fee? V3 §14
  is authoritative and the bearer varies by reason.
- Does the ticket move state **on request** or **on confirmation**? On request is `contradicted` —
  a refund that later fails would leave a refunded ticket that was never refunded.
- Is there any path that **claws back a settled payout**? There must not be.

## A · Backend

### BE-1 · The platform's policies, the schedule and the boundary tests
- **Spec** R1 · **§5** T1 · **depends** R0, ET-ADM-002 (refund policies) · **parallel-safe** no
- **Acceptance** the percentage **either side of every schedule step**. Both sides, every step —
  this is the arithmetic a buyer will check against the policy page.
- *Amended 2026-09-19:* the policies are platform configuration (ET-ADM-002 §4), not a code
  enum. The schedule is read at the version the event was published under, so a policy the
  platform edits later does not change a sold ticket's refund.

### BE-2 · The calculation and its four outputs, per fee bearer
- **Spec** R2 · **§5** T2 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** **every (reason × bearer) combination sums correctly**; below-minimum **refuses**.
- Refunding K3 when the processing fee is K5 costs the platform money to give the buyer nothing.

### BE-3 · The request, its partial unique index and the approval rule
- **Spec** R4, R5 · **§5** T3 · **depends** BE-2 · **parallel-safe** no
- **Acceptance** two parallel requests yield **one**; **only a cancellation's refund approves itself; a waiting one escalates at P2D and P5D** (ROADMAP D-26).
- Both sides of the threshold. Index confirmed live via MCP.

### BE-4 · The journal entries per bearer, and the settlement entry
- **Spec** R3 · **§5** T4 · **depends** BE-3 · **parallel-safe** no
- **Acceptance** 100%, 50% and 0% refunds **each balance**; **the ticket moves only on
  confirmation**.
- A 0% refund still produces entries — the fee moved even though the buyer received nothing.
  Skipping the journal for it is the case that quietly unbalances the ledger.

### BE-5 · The mass-refund job — batched, resumable, idempotent
- **Spec** R6 · **§5** T5 · **depends** BE-4 · **parallel-safe** no
- **Acceptance** 500 tickets, a **kill-and-resume**, a **closed escrow**, a **balanced ledger**.
- Event cancellation refunds everyone at once. A job that restarts from the beginning double-refunds
  the first batch; one that skips forward misses buyers. Resumability is the requirement.

### BE-6 · The reschedule window, per ticket, extending on a second reschedule
- **Spec** R7 · **§5** T6 · **depends** BE-4 · **parallel-safe** yes
- **Acceptance** a **`NO_REFUNDS` event refunds at 100% inside the window**.
- The organizer's no-refunds policy does not survive the organizer moving the date. The buyer
  bought a date. Per **ticket**, and a second reschedule **extends** rather than replaces
  ([`ET-CAT-001`](ET-CAT-001.md) BE-5).

### BE-7 · Chargebacks — the lifecycle, the loss split, the dispute counter
- **Spec** R8 · **§5** T7 · **depends** BE-4 · **parallel-safe** yes
- **Acceptance** an insufficient escrow books to **`5030`**; **no path claws back a settled
  payout**; `openDisputeCount` moves.
- Once money has left for an organizer's bank account it is gone. The platform absorbs the loss to
  `5030` and pursues it commercially — a system that tries to reverse a settled payout produces
  negative escrow and a broken ledger.
- `openDisputeCount` gates payout eligibility ([`ET-FIN-003`](ET-FIN-003.md) BE-5), so it must
  move in **both** directions.

### BE-8 · `RefundQuote` shown before the request
- **Spec** R1, R2 · **§5** T8 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** **the quote and the executed refund produce identical figures.** Same method, as
  with commission preview — a separate quote calculation drifts and the buyer disputes the
  difference.

### BE-9 · The subgraph half; finance operations `@tag`ged
- **Spec** R1–R8 · **§5** T9 · **depends** BE-7 · **parallel-safe** **no — shared booking SDL**

## B · Contract

### GQL-1 · 5 queries, 6 mutations
- **depends** BE-9 · **parallel-safe** no
- The **buyer's** quote and request are `CUSTOMER`-scoped and narrow; everything else is admin.

## C · Frontend

### Buyer · `Ticketing - My Tickets & Transfer.dc.html` *(Coverage map: **partial**)*

### FE-1 · Refund quote before requesting — **the whole point**
- **depends** BE-8, GQL-1, F0-1 · **parallel-safe** no
- Show, before anything is committed: refund amount, fee, who bears it, and **why this
  percentage** (which schedule step applies and when the next step lands).
- Sourced from the server quote (BE-8), never recomputed client-side.
- A buyer who requests a refund and *then* discovers it is 50% has been misled by the interface.
- **testids** `refund-quote`, `refund-amount`, `refund-fee`, `refund-policy-reason`, `refund-next-step-date`

### FE-2 · Request refund
- **depends** FE-1 · **parallel-safe** yes
- Reason selection; confirm shows the quote again. Below-minimum refuses **with the minimum
  named**, before submission.
- **testids** `refund-request`, `refund-reason`, `refund-confirm`, `refund-below-minimum`

### FE-3 · Refund status
- **depends** GQL-1 · **parallel-safe** yes
- Requested → approved → settled, with expected arrival. **The ticket stays valid until the refund
  confirms** (BE-4) — say so, or the buyer believes they have lost entry while the refund is
  pending.
- **testids** `refund-status`, `refund-ticket-still-valid`

### FE-4 · Cancellation and reschedule notices
- **depends** BE-6 · **parallel-safe** yes
- Cancelled → automatic full refund, no action needed. Rescheduled → **the window, its deadline,
  and that 100% applies even on a `NO_REFUNDS` event**.
- Both belong at the top of the ticket, not in a settings page.
- **testids** `event-cancelled-notice`, `event-rescheduled-refund-window`

### Admin · `Admin - Ledger, Commission & Reconciliation.dc.html`

> **Infographics gate.** **Kernel:** *"Every refund and chargeback is accounted for, and we know
> who bore each cost."* Fee-bearer is the dimension the screen exists to show.

### FE-5 · Refund approval queue
- **depends** GQL-1 · **parallel-safe** yes
- Every request except a cancellation's (BE-3). Each row shows the computed split.
- **testids** `refund-queue-row`, `refund-approve`, `refund-reject`, `refund-split-breakdown`

### FE-6 · Chargeback management
- **depends** BE-7 · **parallel-safe** yes
- Lifecycle, evidence deadline, contest action, loss split. `5030` losses shown as their own
  figure — that number is how the business learns what disputes cost.
- **Settled payouts are visibly non-clawback-able**, with the reason. An operator must not spend
  time looking for a button that must not exist.
- **testids** `chargeback-row`, `chargeback-contest`, `chargeback-deadline`, `chargeback-loss-split`, `chargeback-settled-noclawback`

### FE-7 · Mass-refund monitor
- **depends** BE-5 · **parallel-safe** yes
- For a cancelled event: progress, resumability, failures. 500 tickets is a long-running job and
  the operator needs to know it is advancing, not hung.
- **testids** `mass-refund-progress`, `mass-refund-failed`, `mass-refund-resume`

## D · Tests

### TS-1 · Policy *(L1)* — percentage either side of **every** schedule step.

### TS-2 · Calculation *(L1)*
Every (reason × bearer) combination sums; below-minimum refuses.

### TS-3 · Request *(L3)*
Two parallel requests → one; only a cancellation's refund approves itself; escalations at P2D and P5D; index live.

### TS-4 · Journal *(L3)*
100% / 50% / **0%** each balance; the ticket moves **only** on confirmation — assert by failing the
settlement and observing the ticket unchanged.

### TS-5 · Mass refund *(L3)*
500 tickets; **kill and resume** → no double refund, no skipped buyer; escrow closes; ledger
balanced.

### TS-6 · Reschedule window *(L3, frozen clock)*
`NO_REFUNDS` event refunds 100% inside the window; a second reschedule extends per ticket.

### TS-7 · Chargebacks *(L3)*
Insufficient escrow → `5030`; **no path claws back a settled payout** (assert the mutation does not
exist); `openDisputeCount` moves both up and down.

### TS-8 · Quote parity *(L1/L2)*
Quote and executed refund identical across the full combination matrix, not one sample.

### TS-9 · e2e *(L5)*
- Buyer: quote → request → status; below-minimum; cancelled and rescheduled notices; ticket
  remains valid while pending.
- Admin: approval queue with splits; chargeback contest; settled-payout no-clawback state;
  mass-refund progress.
- Loading, empty, error, populated. `K` currency, jade semantic only.

## E · Gate

- [ ] R0 recorded; ticket-moves-on-request and any settled-payout clawback classified `contradicted`
- [ ] Both sides of every policy schedule step
- [ ] Every (reason × bearer) combination sums; below-minimum refuses
- [ ] Two parallel requests yield one; a buyer refund waits for a person and escalates at P2D and P5D
- [ ] 0% refunds still produce balanced journal entries
- [ ] Ticket moves **only** on confirmation
- [ ] Mass refund resumes without double-refunding or skipping; ledger balanced
- [ ] `NO_REFUNDS` refunds 100% inside a reschedule window; second reschedule extends
- [ ] Insufficient escrow books to `5030`; **no settled-payout clawback path exists**
- [ ] `openDisputeCount` moves in both directions
- [ ] Quote equals executed refund across the full matrix
- [ ] Buyer sees the quote **before** committing, with the reason for the percentage
- [ ] `mvn -q -f backend/booking-service test -Dgroups=ET-FIN-004 -DfailIfNoTests=false` green
- [ ] Spec `status:` → `implemented`
