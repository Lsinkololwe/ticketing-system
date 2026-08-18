# ET-FIN-003 · Payout eligibility, bank accounts and the settlement saga — tasks

> **Spec** [`specs/finance/003-payouts-and-settlement/spec.md`](../finance/003-payouts-and-settlement/spec.md) · **Wave 4** · `blocked_by:` ET-PLT-005, 007, ET-FIN-001, ET-FIN-002, ET-PAY-001, ET-ORG-003
> **Screens** `Admin - Finance.dc.html` *(approve/reject queue)* · `Org Admin - Events & Finance.dc.html` *(request, bank accounts, escrow balance)*
> **Routes** admin `(dashboard)/finance/payouts` · org-admin `(dashboard)/finance/{page,payouts,bank-accounts}`
> **Authority** `docs/ARCHITECTURE_REDESIGN_V3_COMPLETE.md` §10, §12
> **Verify** `mvn -q -f backend/booking-service test -Dgroups=ET-FIN-003 -DfailIfNoTests=true`

6 queries, 11 mutations. **BE-6 is the highest-risk write in the platform** — it moves real money
out of the system, and the failure mode is money that has left escrow but never arrived.

## R0 · Reconcile

`PayoutRequest`, `PayoutRequestService`, `PayoutRecoveryService`, `PayoutEligibilityService`,
`PayoutEligibility`, `PayoutConformanceMigrationService` all exist. 20 references to `ET-FIN-003`.

The decisive checks:
- Is the settlement **debit-then-transfer** with compensation, or transfer-then-debit? The latter
  can pay twice on a retry.
- Is a **full account number** stored unencrypted, or reachable in any output? Both are
  `contradicted`.
- Is there a **requester ≠ approver** rule, or can one actor holding both roles self-approve?

## A · Backend

### BE-1 · The eligibility method and its layer-1 tests
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** no
- **Acceptance** each condition fails **independently**, and **the failing condition is named**.
- Naming it is what makes the organizer-facing screen possible. "Not eligible" with no reason
  produces a support ticket every time.

### BE-2 · Bank accounts — encryption at rest, masking, the default index
- **Spec** R4, R5 · **§5** T2 · **depends** R0 · **parallel-safe** yes
- **Acceptance** **no full account number in any output**; **one default per organization**
  (partial unique index — confirm live via MCP).

### BE-3 · Micro-deposit verification, its lockout and its journal entry
- **Spec** R4 · **§5** T3 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** three mismatches lock for 24 h; **the deposit is expensed to `5040`**.
- The micro-deposit is real money leaving the platform, so it is a ledger entry like any other.
  Verification that is not booked is a slow leak nobody reconciles.

### BE-4 · The request, the state machine and the one-open-request rule
- **Spec** R2, R8 · **§5** T4 · **depends** BE-1 · **parallel-safe** no
- **Acceptance** all `(status, action)` pairs asserted; a second `PENDING` request **refuses**.

### BE-5 · Approval — re-check, recompute, and the requester ≠ approver rule
- **Spec** R1, R3 · **§5** T5 · **depends** BE-4 · **parallel-safe** yes
- **Acceptance** an actor holding **both roles cannot approve their own request**; a dispute
  opened **after** the request **refuses**.
- Re-check at approval, not only at request. Between the two, a chargeback can arrive, an event can
  be cancelled, or a dispute can open — approving on stale eligibility pays out money that is owed
  to a buyer.

### BE-6 · The settlement saga — debit-then-transfer, with compensation
- **Spec** R6 · **§5** T6 · **depends** BE-5 · **parallel-safe** **no — the highest-risk write in the platform**
- **Acceptance** a forced failure **restores the escrow exactly**; a **kill between debit and
  transfer resolves either way** — completing the transfer or restoring the escrow, never leaving
  the money nowhere.
- Debit first: an escrow debited with no transfer is visible and recoverable; a transfer with no
  debit pays the same money twice on the next run.

### BE-7 · Retry, the bad-details fast-fail and the escalation
- **Spec** R7 · **§5** T7 · **depends** BE-6 · **parallel-safe** yes
- **Acceptance** bad account details **fail immediately without retry** and **mark the account**.
- Retrying a wrong account number cannot succeed; it just delays telling the organizer why their
  money has not arrived, and each attempt may cost a fee.

### BE-8 · The subgraph half; account fields absent from the type **entirely**
- **Spec** R5 · **§5** T8 · **depends** BE-2 · **parallel-safe** **no — shared booking SDL**
- **Acceptance** **`accountNumber` appears in no schema.** Absent from the type, not hidden by a
  resolver — a field that exists can be selected by someone, eventually.

## B · Contract

### GQL-1 · 6 queries, 11 mutations
- **depends** BE-8 · **parallel-safe** no
- Organizer-scoped escrow balance is a **narrow projection**, not the admin escrow type.

## C · Frontend — two audiences, opposite concerns

> **Infographics gate before any balance visual.**
> **Kernel (organizer):** *"This is what you can withdraw today, and this is when the rest unlocks."*
> Available-now versus held-until is the message. One "balance" number destroys it.

### Organizer · `Org Admin - Events & Finance.dc.html`

### FE-1 · Escrow balance — available now versus held
- **depends** GQL-1, F0-2 · **parallel-safe** no
- Two figures with the **unlock date** on the held portion. Per event, because escrow is per event
  (**D-05**) — a single organizer-level total would be a fiction.
- `K 125,430`, tabular Fira Code, jade used semantically.
- **testids** `escrow-available`, `escrow-held`, `escrow-unlock-date`, `escrow-by-event`

### FE-2 · Eligibility, with the reason
- **depends** BE-1 · **parallel-safe** no
- When a payout cannot be requested, **name the failing condition** (BE-1) — hold period, open
  dispute, unverified account, below minimum. A disabled button with no reason is the single most
  common support contact in this domain.
- **testids** `payout-eligibility`, `payout-blocked-reason`

### FE-3 · Request payout
- **depends** FE-2, GQL-1 · **parallel-safe** yes
- Amount, destination account, expected arrival. One open request at a time (BE-4) — say so when
  blocked, rather than failing on submit.
- **testids** `payout-request`, `payout-amount`, `payout-account-select`, `payout-request-submit`

### FE-4 · Bank accounts
- **depends** BE-2, BE-3 · **parallel-safe** yes
- **Masked always** — the full number is never rendered because it is never sent (BE-8).
- Micro-deposit verification with attempts remaining and, on lockout, **when it lifts**.
- One default, clearly marked.
- **testids** `bank-account-row`, `bank-account-masked`, `bank-account-verify`, `bank-account-attempts`, `bank-account-locked-until`, `bank-account-default`

### FE-5 · Payout history
- **depends** GQL-1 · **parallel-safe** yes
- Status humanised; failures show **why** (BE-7), especially bad details, which the organizer must
  act on.
- **testids** `payout-history-row`, `payout-status`, `payout-failure-reason`

### Admin · `Admin - Finance.dc.html`

### FE-6 · Payout approval queue
- **depends** GQL-1 · **parallel-safe** yes
- Eligibility **re-checked at view time** (BE-5) so the approver sees current state, not the state
  at request.
- **The requester cannot appear as an available approver on their own request** — enforce in the UI
  and on the server (BE-5).
- **testids** `payout-queue-row`, `payout-approve`, `payout-reject`, `payout-reject-reason`, `payout-self-approve-blocked`

### FE-7 · Settlement monitor
- **depends** BE-6, BE-7 · **parallel-safe** yes
- In-flight settlements and their saga state. A settlement stuck between debit and transfer is the
  most urgent thing on the platform — it must be **visible and unmissable**, and it links to
  [`ET-ADM-003`](ET-ADM-003.md).
- **testids** `settlement-row`, `settlement-state`, `settlement-stuck-alert`

## D · Tests

### TS-1 · Eligibility *(L1)* — each condition fails independently and names itself.

### TS-2 · Bank accounts *(L3)*
Encrypted at rest; **no full number in any output**, asserted across GraphQL, REST and logs; one
default per organization (index live via MCP); three mismatches lock 24 h; the deposit hits `5040`.

### TS-3 · Request lifecycle *(L3)*
All `(status, action)` pairs; a second `PENDING` request refuses under `Concurrency.inParallel`.

### TS-4 · Approval *(L3)*
Self-approval refused even holding both roles; a dispute opened after the request refuses at
approval; amounts recomputed, not trusted from the request.

### TS-5 · Settlement saga *(L3 — the most important test in Wave 4)*
- Forced failure **restores the escrow exactly** — assert the balance, not just the status.
- **Kill between debit and transfer** resolves either way; money is never nowhere.
- `Ledger.assertBalanced()` after every outcome.
- Repeat the kill test at each saga step.

### TS-6 · Retry *(L3, WireMock)*
Bad details fail immediately **without retry** and mark the account; transient failures do retry.

### TS-7 · Contract *(L4)* — `accountNumber` appears in **no** schema.

### TS-8 · e2e *(L5)*
- Org-admin: available/held with unlock date; blocked eligibility naming the reason; request;
  bank account add → verify → lockout; history with failure reasons.
- Admin: queue with re-checked eligibility; self-approval blocked; settlement monitor showing a
  stuck settlement.
- Loading, empty, error, populated. No full account number anywhere in the DOM — assert it.

## E · Gate

- [ ] R0 recorded; transfer-before-debit and any unencrypted account number classified `contradicted`
- [ ] Every eligibility condition fails independently and names itself
- [ ] Account numbers encrypted; **absent from every schema**; never in output or logs
- [ ] One default account per organization, index live
- [ ] Micro-deposit lockout after three attempts; deposit expensed to `5040`
- [ ] One open request at a time
- [ ] Eligibility re-checked and amounts recomputed at approval
- [ ] Requester cannot approve their own request
- [ ] Forced failure restores escrow **exactly**; kill between debit and transfer resolves either way
- [ ] Bad details fail fast, mark the account, and do not retry
- [ ] Organizer sees available vs held with an unlock date, per event
- [ ] Blocked payouts always state the failing condition
- [ ] Stuck settlements are visible and linked to transaction recovery
- [ ] `mvn -q -f backend/booking-service test -Dgroups=ET-FIN-003 -DfailIfNoTests=true` green
- [ ] Spec `status:` → `implemented`
