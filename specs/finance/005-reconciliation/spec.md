# ET-FIN-005 · Reconciliation — proving the ledger against the world

> **Conformance** · V3 §11 transaction tracking · V3 §12 settlement process and reconciliation

## 1. Capability

Everything upstream of this spec is a claim. The ledger says an event escrow holds
K28,500; the provider says it collected K31,200 last Tuesday; the bank says K30,850
arrived on Thursday. Those three numbers are supposed to be reconcilable, and the moment
they are not, the platform has a problem it will otherwise discover from an organizer
asking where their money is.

This spec is the proof. It runs three reconciliations, each answering a different question.
**Internal reconciliation** proves that every cached balance equals the sum of its journal
lines — that the platform's own books are self-consistent. **Provider reconciliation**
proves that every collection and refund the provider recorded has a matching platform
transaction and vice versa. **Bank reconciliation** proves that money the provider says it
settled actually arrived, and that every payout the platform initiated actually left.

It declares the discrepancy taxonomy — because *the provider has a transaction we do not*
and *we have one the provider does not* are different problems needing different responses
— and the workflow by which each is investigated and resolved. And it declares the one
number that summarises the platform's financial health in a single query: the trial balance,
which must sum to zero.

The rule is that **reconciliation repairs what it safely can and escalates everything
else.** A cached balance that disagrees with its journal is corrected automatically, because
the journal is authoritative. A payment the provider has and the platform does not is never
auto-created, because inventing a transaction to make a report balance is how a
reconciliation becomes theatre.

## 2. Design decisions

**Three reconciliations, three questions, three schedules.** Internal runs hourly and is
cheap. Provider runs daily against the previous day's settlement window. Bank runs on
statement receipt, which is when the data exists. Collapsing them into one job means the
cheap check runs at the expensive one's cadence.

**Internal reconciliation repairs; the other two only report.** The journal is
authoritative over a cached balance ([ET-FIN-001](../001-escrow-and-ledger/) R3), so a
mismatch there is a projection bug and correcting it is safe. A provider or bank
discrepancy involves money outside the platform's control, and the correct response is
always a human — auto-creating a journal entry to match an external record is how a
reconciliation stops being a control.

**Every discrepancy has a class, and each class has an owner and a response.** Six classes,
declared in §4. A discrepancy with no class is itself a finding — it means something is
happening that nobody modelled.

**A reconciliation run is a document, not a log line.** `booking_reconciliation_runs` records
what was compared, over what window, what was found and what was done. An auditor asking
*was March reconciled* gets a row, not a grep. Individual differences are
`booking_reconciliation_items`, each with its own resolution state.

**The trial balance must sum to zero, and it is a query anyone in finance can run.**
Σ debits − Σ credits across every account, as at any instant. A non-zero result is
`JOURNAL_UNBALANCED` — `INTERNAL`, and it pages. This is the single cheapest and most
valuable check the platform has, and it should be on a dashboard.

**Settlement of `1010` is this spec's job.** [ET-FIN-001](../001-escrow-and-ledger/) debits
`1010 Provider Settlement Receivable` at every purchase and nothing credits it — that spec
flags the omission as a risk. Bank reconciliation is where the credit happens: when a
provider settlement lands in the platform bank, `debit 1020` / `credit 1010` for the
settled amount, and the fee difference is expensed. Without this, the receivable grows
forever and the balance sheet is permanently wrong.

**Reconciliation is read-mostly and must not contend with the write path.** It runs against
a secondary read preference where the driver supports it, in bounded batches, off-peak. A
reconciliation that slows the on-sale minute is a reconciliation that gets turned off.

**Unmatched items age into escalation rather than accumulating silently.** An item unresolved
past its class's threshold is raised to [ET-ADM-003](../../admin/003-transaction-recovery/)
and counted in an alerting metric. A growing unmatched queue is the earliest signal the
platform gets that something upstream is broken.

**Rejected alternatives**

- *One nightly job doing all three.* The cheap check then runs nightly and a projection bug lives a day.
- *Auto-creating journal entries to match provider records.* Makes the books agree with a claim rather than proving it, which is the opposite of a control.
- *Reconciling only on demand.* Nobody demands it until an organizer complains.
- *Logging discrepancies without a resolution state.* They accumulate in a file nobody closes the loop on.
- *Reconciling against the provider's dashboard by hand.* Works at a hundred transactions a day and fails silently at ten thousand.
- *Treating a fee difference as a discrepancy.* Providers deduct fees at settlement; the difference is expected and must be expensed, not investigated.

## 3. Requirements

### ET-FIN-005-R1 · The trial balance sums to zero, on demand and on a schedule

THE SYSTEM SHALL compute a trial balance across every account, and IF it does not sum to
zero, THEN THE SYSTEM SHALL raise an internal error and alert.

**Acceptance**
- [ ] `trialBalance(asOf)` returns every account code with its debit and credit totals as at that instant
- [ ] `Σ debits − Σ credits == 0` exactly, at `Decimal128` precision
- [ ] A non-zero result raises `JOURNAL_UNBALANCED`, mapped to `INTERNAL`, and pages
- [ ] The query is served by the `{ accountCode: 1, postedAt: 1 }` index and `explain()` reports `IXSCAN`
- [ ] It runs hourly as part of internal reconciliation and is available to `FINANCE` on demand
- [ ] A deliberately unbalanced entry — inserted directly, bypassing the service — is detected within one cycle
- [ ] The result is cached for the duration of one cycle so a dashboard refresh does not re-scan the journal

### ET-FIN-005-R2 · Internal reconciliation proves every cached balance and repairs it

THE SYSTEM SHALL compare every cached balance against its journal lines and SHALL correct a
mismatch.

**Acceptance**
- [ ] Every `booking_escrow_accounts.currentBalance` and every `booking_platform_accounts.currentBalance` is compared against the sum of its lines
- [ ] A mismatch is recorded as a `BALANCE_DRIFT` item, corrected to the journal's figure, and alerted
- [ ] The correction updates only the projection — it writes **no** journal entry, because the journal was already right
- [ ] Correction is bounded: a drift above `finance.reconciliation.auto-correct-limit` (K1,000) is escalated rather than corrected
- [ ] The run records how many accounts were checked, how many drifted, and the total drift
- [ ] A test corrupts a cached balance by K5 and by K5,000 and asserts the first is corrected and the second escalated
- [ ] Internal reconciliation runs hourly and completes within `finance.reconciliation.internal-budget` (PT5M)

### ET-FIN-005-R3 · Provider reconciliation matches both directions

THE SYSTEM SHALL compare the provider's transactions against the platform's for each
settlement window, in both directions.

**Acceptance**
- [ ] The run fetches the provider's collections and refunds for the window and matches on `providerReference`
- [ ] A provider transaction with no platform record is `PROVIDER_ONLY` — money the platform holds and cannot attribute
- [ ] A platform transaction the provider does not report is `PLATFORM_ONLY` — a payment the platform believes succeeded and the provider does not
- [ ] An amount difference on a matched pair is `AMOUNT_MISMATCH`
- [ ] A status difference is `STATUS_MISMATCH` — for example, the platform has `SUCCEEDED` and the provider has `FAILED`
- [ ] No discrepancy is resolved by creating a transaction; every one requires a human decision
- [ ] `PLATFORM_ONLY` on a succeeded payment is the highest-severity class and alerts immediately — a ticket exists for money that may not

### ET-FIN-005-R4 · Bank reconciliation settles the receivable

WHEN a provider settlement lands in the platform bank, THE SYSTEM SHALL credit
`1010 Provider Settlement Receivable` and expense the fee difference.

**Acceptance**
- [ ] A settlement of amount `S` covering gross collections `G` writes `debit 1020 Platform Bank S` / `credit 1010 G` / `debit 5010 Payment Processing Fees (G − S)`
- [ ] The entry balances exactly
- [ ] The fee difference is **expected** and is expensed, not raised as a discrepancy
- [ ] A settlement that cannot be attributed to a set of platform transactions is `UNATTRIBUTED_SETTLEMENT` and escalates
- [ ] `1010`'s balance after reconciliation equals collections not yet settled, asserted by a test over a two-day window
- [ ] Every initiated payout is matched against a bank debit; an unmatched payout past `finance.reconciliation.payout-match-window` (P3D) is `PAYOUT_UNCONFIRMED` and escalates
- [ ] Bank reconciliation runs on statement receipt, not on a fixed schedule

### ET-FIN-005-R5 · Every run is a document and every difference has a lifecycle

THE SYSTEM SHALL record each run and each difference, and SHALL track each difference to
resolution.

**Acceptance**
- [ ] `booking_reconciliation_runs` records `type`, `windowStart`, `windowEnd`, `status`, counts per class, and the operator or schedule that started it
- [ ] `booking_reconciliation_items` records the class, both sides of the difference, the amount, and a resolution state
- [ ] `ReconciliationItemStatus` is `OPEN`, `INVESTIGATING`, `RESOLVED`, `WRITTEN_OFF`, `FALSE_POSITIVE`
- [ ] Resolving an item requires a reason and an operator, and writes an audit row
- [ ] A `WRITTEN_OFF` item above `finance.reconciliation.write-off-limit` (K500) requires `SUPER_ADMIN`
- [ ] Re-running a window does not duplicate items already `OPEN` for the same underlying difference
- [ ] A run in progress refuses a second run of the same type with `RECONCILIATION_IN_PROGRESS`, retryable

### ET-FIN-005-R6 · Unresolved differences age into escalation

WHILE a reconciliation item remains unresolved past its threshold, THE SYSTEM SHALL
escalate it and count it.

**Acceptance**
- [ ] Each class carries an escalation threshold from §4
- [ ] An item past its threshold is surfaced to [ET-ADM-003](../../admin/003-transaction-recovery/) with its full context
- [ ] Open items per class and total unmatched value are metrics, and each alerts above a configured bound
- [ ] The oldest open item's age is a metric — a rising figure means resolution is not keeping up
- [ ] A dashboard shows open items by class and by age ([ET-ADM-005](../../admin/005-observability-and-health/))
- [ ] A test ages items past their thresholds and asserts each escalates once

### ET-FIN-005-R7 · Reconciliation does not contend with the write path

THE SYSTEM SHALL run reconciliation without degrading the purchase path.

**Acceptance**
- [ ] Every reconciliation read uses a secondary read preference where the deployment provides one
- [ ] Journal scans are batched at `finance.reconciliation.batch-size` (1,000) with a bounded cursor
- [ ] Provider and bank runs are scheduled off-peak by configuration
- [ ] Each run holds a lock, so two instances never run one type concurrently
- [ ] A load test runs reconciliation during a simulated on-sale and asserts reservation latency is unaffected beyond a stated bound
- [ ] A run exceeding its budget is abandoned and retried, never allowed to run indefinitely

## 4. Model

> **Amended 2026-09-01 under [D-19](../../ROADMAP.md).** 1 operation name below adopts the
> shipped name: `resolveItem` → `resolveReconciliationItem`. D-19 rules that where the schema and §4 disagree on an operation's
> *name*, the schema stands and §4 adopts it.
>
> **Only the names were adopted.** Argument lists and return types were not re-verified against
> the schema, so a row here can now name a real operation and still describe it wrongly. That
> gap is unmeasured, and calling it verified would be the same mistake as counting a file's
> existence as proof it runs.

### The three reconciliations

| Type | Question | Source | Cadence | Repairs? |
|---|---|---|---|---|
| `INTERNAL` | do cached balances match the journal? | the journal | hourly | **yes**, below the limit |
| `PROVIDER` | do our payments match the provider's? | provider API | daily, previous window | no — escalates |
| `BANK` | did the money actually move? | bank statement | on statement receipt | no — escalates |

### Discrepancy classes

| Class | Meaning | Severity | Escalate after |
|---|---|---|---|
| `BALANCE_DRIFT` | a cached balance disagrees with its journal | medium | immediate if > limit |
| `PROVIDER_ONLY` | the provider has a transaction the platform does not | **high** | 24 h |
| `PLATFORM_ONLY` | the platform has a succeeded payment the provider does not | **critical** | immediate |
| `AMOUNT_MISMATCH` | matched pair, different amounts | high | 24 h |
| `STATUS_MISMATCH` | matched pair, different outcomes | high | 24 h |
| `UNATTRIBUTED_SETTLEMENT` | bank credit that maps to no transaction set | high | 48 h |
| `PAYOUT_UNCONFIRMED` | initiated payout with no bank debit | **critical** | 72 h |

`PLATFORM_ONLY` is critical because a ticket has been issued, escrow credited and
commission recorded against money that may never have moved. `PAYOUT_UNCONFIRMED` is
critical because the escrow has been debited and the organizer may not have been paid.

### The settlement entry — this spec's own

For a provider settlement of `S` covering gross collections `G`:

| Account | Direction | Amount |
|---|---|---|
| `1020` Platform Bank | debit | `S` |
| `5010` Payment Processing Fees | debit | `G − S` |
| `1010` Provider Settlement Receivable | credit | `G` |

This is the entry that closes the loop [ET-FIN-001](../001-escrow-and-ledger/) opens at
every purchase. Without it `1010` grows monotonically and the trial balance is wrong on
the asset side forever.

### Documents

`booking_reconciliation_runs`

| Field | Type | Notes |
|---|---|---|
| `_id` | `String` | |
| `type` | `ReconciliationType` | `INTERNAL`, `PROVIDER`, `BANK` |
| `windowStart`, `windowEnd` | `Instant` | |
| `status` | `ReconciliationStatus` | `RUNNING`, `COMPLETED`, `FAILED`, `ABANDONED` |
| `recordsCompared` | `int` | |
| `itemsByClass` | `Map<String,Integer>` | |
| `totalDiscrepancyAmount` | `BigDecimal` | |
| `autoCorrectedCount`, `autoCorrectedAmount` | | internal only |
| `startedById` | `String` | null when scheduled |
| `startedAt`, `completedAt`, `durationMs` | | |

`booking_reconciliation_items`

| Field | Type | Notes |
|---|---|---|
| `_id`, `reconciliationRunId` | `String` | |
| `itemClass` | `String` | one of the seven |
| `platformReference`, `providerReference` | `String` | either may be null |
| `platformAmount`, `externalAmount`, `difference` | `BigDecimal` | |
| `platformStatus`, `externalStatus` | `String` | |
| `status` | `ReconciliationItemStatus` | `OPEN`, `INVESTIGATING`, `RESOLVED`, `WRITTEN_OFF`, `FALSE_POSITIVE` |
| `resolvedById`, `resolvedAt`, `resolutionNote` | | |
| `journalEntryId` | `String` | when resolution posted an entry |
| `escalatedAt` | `Instant` | |
| `createdAt` | `Instant` | |

### The trial balance

```
SELECT accountCode,
       Σ amount WHERE direction = DEBIT   AS debits,
       Σ amount WHERE direction = CREDIT  AS credits
FROM   booking_journal_lines
WHERE  postedAt <= :asOf
GROUP  BY accountCode

assert Σ debits == Σ credits          // exactly, Decimal128
```

Served by `{ accountCode: 1, postedAt: 1 }` — the hot index
[ET-PLT-002](../../_platform/002-persistence-baseline/) §4 declares.

### GraphQL

Subgraph `booking`. Every field is `FINANCE` and `@tag(name: "admin")`.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `trialBalance(asOf)` | query | `FINANCE` | `TrialBalance!` |
| `reconciliationRuns(type, page)` | query | `FINANCE` | `ReconciliationRunPage!` |
| `reconciliationRun(id)` | query | `FINANCE` | `ReconciliationRun` |
| `reconciliationItems(runId, itemClass, status, page)` | query | `FINANCE` | `ReconciliationItemPage!` |
| `openReconciliationItems(itemClass, page)` | query | `FINANCE` | `ReconciliationItemPage!` |
| `financialSummary(from, to)` | query | `FINANCE` | `FinancialSummary!` |
| `startReconciliation(type, windowStart, windowEnd)` | mutation | `FINANCE` | `ReconciliationRun!` |
| `investigateItem(id, note)` | mutation | `FINANCE` | `ReconciliationItem!` |
| `resolveReconciliationItem(input)` | mutation | `FINANCE` | `ReconciliationItem!` |
| `writeOffItem(id, reason)` | mutation | `SUPER_ADMIN` | `ReconciliationItem!` |
| `recordBankSettlement(input)` | mutation | `FINANCE` | `ReconciliationRun!` |

`recordBankSettlement` is how a statement enters the platform — a finance operator supplies
the settled amount and the window, and R4's entry is written.

### Schedules

Every scheduled run is a Temporal Schedule starting `ReconciliationWorkflow` with its type, on
`booking-recon` ([ET-PLT-015](../../_platform/015-durable-execution/) §4). Overlap policy `SKIP` is
the mutex, so two runs of one type never work the same window; no Redis lock exists. The workflow
runs one activity per type under the run's budget, and a run past its budget is abandoned and
retried by the next fire.

| Schedule | Runs | Cadence (UTC) | Overlap |
|---|---|---|---|
| `booking-recon-escrow` | `ReconciliationWorkflow(ESCROW)` — each cached escrow balance against its transactions | hourly at :05 | `SKIP` |
| `booking-recon-escrow-journal` | `ReconciliationWorkflow(ESCROW_JOURNAL)` — escrow balances against the journal | hourly at :20 | `SKIP` |
| `booking-recon-alerts` | `ReconciliationWorkflow(ALERTS)` — alerts on the open discrepancies | hourly at :35 | `SKIP` |
| `booking-recon-weekly-summary` | `ReconciliationWorkflow(WEEKLY_SUMMARY)` | Monday 05:00 | `SKIP` |
| `recon-provider` | `ReconciliationWorkflow(PROVIDER)` — planned | daily 03:00, the previous day's window | `SKIP` |

The two escrow checks are the `INTERNAL` reconciliation of the table above, and the alerts run is its
escalation. The runs are staggered so no two start together (ROADMAP D-24). The boot runner creates a
missing Schedule and moves an existing one to this cadence, keeping an operator's pause.

Bank reconciliation has no schedule — it runs on `recordBankSettlement`.

### Configuration

| Property | Value |
|---|---|
| `finance.reconciliation.internal-schedule` | `5 * * * *` and `20 * * * *` UTC, alerts `35 * * * *` — Temporal Schedules |
| `finance.reconciliation.internal-budget` | `PT5M` |
| `finance.reconciliation.provider-schedule` | `0 3 * * *` UTC — a Temporal Schedule |
| `finance.reconciliation.auto-correct-limit` | `K1,000.00` |
| `finance.reconciliation.write-off-limit` | `K500.00` |
| `finance.reconciliation.batch-size` | 1,000 |
| `finance.reconciliation.payout-match-window` | `P3D` |

### Error codes

`RECONCILIATION_IN_PROGRESS` — a row of
[ET-PLT-005 §4](../../_platform/005-error-contract/) introduced by this spec, retryable.
`JOURNAL_UNBALANCED` and `ACCOUNT_CODE_UNKNOWN` are
[ET-FIN-001](../001-escrow-and-ledger/)'s and are raised here.

## 5. Tasks

- [ ] **T1 · The trial balance query, its index and the paging alert**
  - requirements: R1
  - files: `backend/booking-service/.../repository/impl/JournalRepositoryImpl.java`
  - verify: `explain()` reports `IXSCAN`; a directly inserted unbalanced entry is caught within a cycle
  - parallel-safe: no
  - depends: —

- [ ] **T2 · Internal reconciliation: compare, correct below the limit, escalate above**
  - requirements: R2
  - files: `backend/booking-service/.../service/impl/InternalReconciliationService.java`
  - verify: a K5 drift corrects and a K5,000 drift escalates; no journal entry is written by a correction
  - parallel-safe: no
  - depends: T1

- [ ] **T3 · The run and item documents, and the item lifecycle**
  - requirements: R5
  - files: `backend/booking-service/.../domain/model/`
  - verify: re-running a window creates no duplicate open items; a second run refuses
  - parallel-safe: yes
  - depends: T1

- [ ] **T4 · Provider reconciliation and the four match classes**
  - requirements: R3
  - files: `backend/booking-service/.../service/impl/ProviderReconciliationService.java`
  - verify: each class is produced by a seeded scenario; nothing is auto-created
  - parallel-safe: yes
  - depends: T3

- [ ] **T5 · Bank settlement: the `1010` credit and the fee expense**
  - requirements: R4
  - files: `backend/booking-service/.../service/impl/BankReconciliationService.java`
  - verify: `1010` after a two-day window equals unsettled collections; the entry balances
  - parallel-safe: no — it closes ET-FIN-001's open loop
  - depends: T3

- [ ] **T6 · Payout matching and `PAYOUT_UNCONFIRMED`**
  - requirements: R4
  - files: `backend/booking-service/.../service/impl/BankReconciliationService.java`
  - verify: an unmatched payout past 3 days escalates as critical
  - parallel-safe: yes
  - depends: T5

- [ ] **T7 · Escalation ageing, the metrics and the write-off gate**
  - requirements: R5, R6
  - files: `backend/booking-service/.../workflow/recon/ReconciliationWorkflowImpl.java` (the `ESCALATION` run), `.../workflow/recon/ReconciliationSchedules.java`
  - verify: each class escalates once at its threshold; a K501 write-off requires `SUPER_ADMIN`
  - parallel-safe: yes
  - depends: T4

- [ ] **T8 · Read preference, batching and the on-sale contention test**
  - requirements: R7
  - files: `backend/booking-service/.../config/MongoConfig.java`, the three services
  - verify: reservation latency during a full reconciliation stays within the stated bound
  - parallel-safe: no
  - depends: T4

- [ ] **T9 · The subgraph half; every field `FINANCE` and `@tag(name: "admin")`**
  - requirements: R1–R7
  - files: `backend/booking-service/src/main/resources/graphql/schema.graphqls`
  - verify: the public contract exposes no reconciliation field
  - parallel-safe: no — shared SDL across booking's specs
  - depends: T5

## 6. Out of scope

| Capability | Spec |
|---|---|
| The chart, the journal and the balances being proved | [ET-FIN-001](../001-escrow-and-ledger/) |
| Commission recognition, which reconciliation counts but does not perform | [ET-FIN-002](../002-commission/) |
| Payouts, whose bank confirmation this spec matches | [ET-FIN-003](../003-payouts-and-settlement/) |
| Refunds and chargebacks, whose provider records this spec matches | [ET-FIN-004](../004-refunds-and-chargebacks/) |
| The provider's own callback-level reconciliation | [ET-PAY-002](../../payment/002-webhooks-and-settlement/) |
| Operator investigation tooling and bulk actions | [ET-ADM-003](../../admin/003-transaction-recovery/) |
| Dashboards, alert routing and thresholds | [ET-ADM-005](../../admin/005-observability-and-health/) |
| Audit rows for resolutions and write-offs | [ET-PLT-009](../../_platform/009-audit-trail/) |

Deliberately never in scope: **auto-creating a journal entry to match an external record**
(it makes the books agree with a claim instead of proving it), **treating the provider's
settlement fee as a discrepancy** (it is expected and must be expensed), and **on-demand
reconciliation only** (nobody demands it until an organizer complains).
