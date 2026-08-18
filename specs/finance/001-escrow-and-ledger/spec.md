# ET-FIN-001 · Per-event escrow, the chart of accounts and double-entry

> **Conformance** · V3 §3.1 account types · V3 §3.2 fund flow · V3 §11 transaction tracking · V3 §13 account classification

## 1. Capability

From the moment a buyer's K300 leaves their wallet to the moment K285 reaches an
organizer's bank account, the platform is holding somebody else's money. How much, whose,
and on what basis are questions it must be able to answer exactly — not approximately, not
eventually, and not by adding up a column that some other process also writes to.

This spec builds the accounting substrate every other money capability stands on. It
declares the **chart of accounts** — the named places money can be — the **per-event escrow
account** that holds an organizer's takings until they are releasable, the **platform
accounts** that separate commission the platform has merely collected from commission it
has actually earned, and the **double-entry journal** that is the only mechanism by which
any of those balances change.

The rule is absolute and it is the reason this spec exists before commission, payouts or
refunds: **no balance is ever assigned; every balance is the sum of its journal lines.**
A cached balance column exists for speed, but it is a projection, it is reconcilable to the
journal on demand, and when the two disagree the journal is right. A platform that lets
code write `escrow.balance = x` has, at that moment, lost the ability to explain where the
money went.

The second rule is that debits equal credits. Every time. `JOURNAL_UNBALANCED` is the one
error in the whole registry mapped to `INTERNAL`
([ET-PLT-005 §4](../../_platform/005-error-contract/)), because unbalanced books are not a
user's mistake — they are a defect, and somebody has to be woken up.

## 2. Design decisions

**One escrow account per event, not per organizer (D-05).** An organizer running three
events has three escrow accounts. Cancelling one must not be able to reach into another's
funds, a refund obligation is computable per event, and the payout hold clock is a property
of an event's end date rather than of an organizer. Per-organizer escrow makes every one of
those a query with a date filter and an argument about which money is which.

**Commission is collected into a pending account and only later moved to revenue (D-04).**
At purchase the platform's cut goes to `PENDING_COMMISSION` — a liability-shaped account
holding money the platform has but has not earned. When the event completes and the hold
passes, it moves to `EARNED_REVENUE`. A refund before that point simply cancels the pending
entry; there is no clawback, because nothing was ever recognised. This is the decision that
makes refunds arithmetically simple, and it is why the two accounts are distinct.

**Every balance movement is a journal entry with at least two lines, and they sum to zero.**
`booking_journal_entries` is the header — what happened, what caused it, when — and
`booking_journal_lines` are the debits and credits. The entry is written with its lines in
one transaction, and a validator refuses to persist an entry whose lines do not balance.

**Balances are projections, and reconciliation is a first-class operation.**
`booking_escrow_accounts.currentBalance` exists because summing a year of journal lines on
every read is absurd. It is maintained by the same transaction that writes the lines, it is
`@Version`-locked, and `ET-FIN-005` proves it against the journal on a schedule. A
discrepancy is an incident, not a rounding difference.

**The chart of accounts is a closed, seeded registry with stable codes.** Numeric codes
grouped by type — assets 1xxx, liabilities 2xxx, revenue 4xxx, expenses 5xxx. Codes never
change meaning, because a report written against `4100` in March must mean the same thing
in December.

**Escrow has a lifecycle, and it opens when the event publishes.**
`catalog.EventPublished` opens the account; the first sale credits it; event completion plus
the hold period makes it `PAYOUT_ELIGIBLE`; a settled payout closes it. An account cannot be
credited once closed, and a cancellation closes it at zero after refunding everything.

**Money at rest is always attributable.** Every journal line names an account, an amount, a
direction and a reference to what caused it — a ticket, a refund, a payout, a chargeback.
There is no line whose origin is "adjustment". A manual correction is an entry with a
reason, an operator and an audit row, and it is the only kind that requires
`SUPER_ADMIN`.

**Rejected alternatives**

- *Per-organizer escrow.* Makes every refund obligation a query with a date filter and an argument about which event's money is being used.
- *A single balance field updated in place.* Fast, and it permanently destroys the ability to explain a discrepancy.
- *Recognising commission at purchase.* Books revenue on tickets for events that may be cancelled, then reverses it — and the reversal is the clawback D-04 exists to avoid.
- *Single-entry transaction logging.* Records movements without proving they are consistent; nothing catches the entry that credits without debiting.
- *Free-text account names.* Two spellings of "platform revenue" and a report that is quietly half right.
- *Deriving balances on read with no cached column.* Correct and unusable — an organizer dashboard would sum a year of lines per page view.
- *Allowing an "adjustment" line with no reference.* It becomes the place every unexplained difference is hidden.

## 3. Requirements

### ET-FIN-001-R1 · The chart of accounts is closed, coded and seeded

THE SYSTEM SHALL define every account in the §4 chart, and no balance SHALL exist outside
it.

**Acceptance**
- [ ] `booking_chart_of_accounts` is seeded from a migration with exactly the §4 rows, idempotently
- [ ] Every account carries `accountCode`, `name`, `accountType`, `normalBalance` and `currency`
- [ ] Account codes are unique and never reused; a code's meaning never changes
- [ ] Every journal line names an account code that resolves; an unknown code refuses with `ACCOUNT_CODE_UNKNOWN`
- [ ] The three platform accounts of §4 are singletons — a unique index prevents a second of each
- [ ] No monetary field anywhere in booking-service is outside an account or a document that references one

### ET-FIN-001-R2 · Every movement is a balanced journal entry

WHEN any balance changes, THE SYSTEM SHALL write a journal entry whose lines sum to zero,
and IF they do not, THEN THE SYSTEM SHALL refuse to persist it.

**Acceptance**
- [ ] Every entry has at least two lines and `Σ debits == Σ credits` exactly, at `Decimal128` precision
- [ ] The entry and its lines are written in one transaction; a partial write is impossible
- [ ] An unbalanced entry raises `JOURNAL_UNBALANCED`, mapped to `INTERNAL`, and alerts — it is a defect, not a user error
- [ ] Every line carries `accountCode`, `direction`, `amount`, `currency`, `referenceType` and `referenceId`
- [ ] No line has a null `referenceId`; there is no "adjustment" line without a cause
- [ ] `Ledger.assertBalanced()` ([ET-PLT-006](../../_platform/006-test-harness/) §4) holds after every money operation in every test
- [ ] Journal entries are append-only — no mutation updates or deletes one; a correction is a new reversing entry

### ET-FIN-001-R3 · No balance is assigned; every balance is derived and cached

THE SYSTEM SHALL update balances only as a consequence of journal lines, and every cached
balance SHALL be reconcilable to the journal.

**Acceptance**
- [ ] No code outside `LedgerService` writes a balance field — no production field typed `double` or `float` names an amount, balance, price, fee, total or commission; every rounding is `HALF_UP`; no balance is assigned outside the ledger
- [ ] The cached balance is updated in the same transaction as the lines that changed it
- [ ] `recomputeBalance(accountId)` sums the journal and returns the authoritative figure
- [ ] A test writes 1,000 random movements and asserts the cached balance equals the recomputed one exactly
- [ ] A deliberate corruption of a cached balance is detected by reconciliation ([ET-FIN-005](../005-reconciliation/))
- [ ] Where the two disagree, the journal is authoritative and the cache is corrected, never the reverse

### ET-FIN-001-R4 · One escrow account per event, with a lifecycle

WHEN an event is published, THE SYSTEM SHALL open exactly one escrow account for it, and
that account SHALL follow the §4 lifecycle.

**Acceptance**
- [ ] `booking_escrow_accounts` carries a **unique index on `eventId`**
- [ ] `catalog.EventPublished` opens the account, idempotently — a re-publish opens no second account
- [ ] `EscrowStatus` is `ACTIVE`, `HOLD`, `PAYOUT_ELIGIBLE`, `SUSPENDED`, `CLOSED`
- [ ] `catalog.EventCompleted` moves it to `HOLD` and sets `holdUntil = endsAt + finance.escrow.hold-period` (P7D)
- [ ] A sweep moves `HOLD` to `PAYOUT_ELIGIBLE` once `holdUntil` has passed and there are no open disputes
- [ ] A credit to a `CLOSED` account is refused with `ESCROW_NOT_ACTIVE` carrying `currentStatus`
- [ ] `holdUntil` is recomputed on `catalog.EventRescheduled` from the new end date

### ET-FIN-001-R5 · A purchase splits into escrow and pending commission

WHEN a ticket purchase confirms, THE SYSTEM SHALL credit the event escrow with the net
amount and the pending commission account with the platform's cut.

**Acceptance**
- [ ] The entry is `debit 1010 Provider Settlement Receivable` / `credit 2010 Event Escrow` + `credit 2020 Pending Commission`
- [ ] `netAmount + commissionAmount == grossAmount` for every ticket, at K0.01
- [ ] The commission amount is [ET-FIN-002](../002-commission/)'s calculation; this spec only places it
- [ ] The entry is written inside [ET-TKT-001](../../ticketing/001-reservation-and-hold/) R7's confirmation transaction
- [ ] `referenceType = TICKET` and `referenceId` is the ticket id on every line
- [ ] A test purchases 100 tickets across 3 tiers and asserts the escrow balance equals the sum of net amounts exactly
- [ ] The provider's collection fee is a separate entry: `debit 5010 Payment Processing Fees` / `credit 1010`, and never reduces the buyer's charge or the escrow credit

### ET-FIN-001-R6 · Escrow cannot go negative, and contention is detected

WHILE an escrow account is being debited, THE SYSTEM SHALL refuse any debit exceeding its
available balance and SHALL detect concurrent modification.

**Acceptance**
- [ ] A debit exceeding `currentBalance` is refused with `ESCROW_INSUFFICIENT_BALANCE` carrying `availableBalance`, and writes no journal entry
- [ ] The balance check and the debit are one conditional atomic update, not a read followed by a write
- [ ] `booking_escrow_accounts` carries `@Version`; a concurrent modification yields `RESOURCE_CONFLICT`, retryable ([ET-PLT-005](../../_platform/005-error-contract/) R7)
- [ ] Two parallel debits jointly exceeding the balance produce one success and one refusal
- [ ] No escrow balance is ever negative, asserted by an invariant test across a randomised operation sequence
- [ ] `Persistence.assertNothingPersisted("booking_journal_entries", "booking_journal_lines")` holds on every refusal

### ET-FIN-001-R7 · The three platform accounts are singletons with distinct meanings

THE SYSTEM SHALL maintain exactly one pending-commission, one earned-revenue and one
operations account, and SHALL NOT conflate them.

**Acceptance**
- [ ] `booking_platform_accounts` holds exactly three rows, enforced by a unique index on `accountType`
- [ ] `PENDING_COMMISSION` holds commission collected but not earned; nothing may be withdrawn from it
- [ ] `EARNED_REVENUE` holds recognised revenue; it is credited only by [ET-FIN-002](../002-commission/)'s recognition
- [ ] `OPERATIONS` holds working funds; it is funded only by an explicit transfer from `EARNED_REVENUE`
- [ ] A transfer from `EARNED_REVENUE` to `OPERATIONS` requires `SUPER_ADMIN` and writes an audit row
- [ ] A test asserts no code path credits `EARNED_REVENUE` directly from a purchase
- [ ] The sum of every event escrow balance plus the pending commission balance equals the platform's total unsettled liability, asserted by a reconciliation test

### ET-FIN-001-R8 · A manual correction is an entry, not an edit

IF an operator must correct the ledger, THEN THE SYSTEM SHALL record a reversing entry with
a reason and an operator, and SHALL NOT modify any existing entry.

**Acceptance**
- [ ] `postManualAdjustment` requires `SUPER_ADMIN`, a reason of at least 20 characters, and a reference
- [ ] It writes a new balanced entry with `entryType = MANUAL_ADJUSTMENT`; it modifies nothing
- [ ] The operator, the reason and both sides of the entry are written to the audit trail ([ET-PLT-009](../../_platform/009-audit-trail/))
- [ ] No mutation, service or repository method updates or deletes a `booking_journal_entries` or `booking_journal_lines` document
- [ ] A reversal of a specific entry links to it by `reversesEntryId`
- [ ] Manual adjustments are counted as a metric and a rising rate alerts — they are the symptom of a bug elsewhere

## 4. Model

### Chart of accounts — closed and seeded

| Code | Name | Type | Normal | Holds |
|---|---|---|---|---|
| `1010` | Provider Settlement Receivable | ASSET | debit | collected by the provider, not yet settled to the platform bank |
| `1020` | Platform Bank | ASSET | debit | settled cash |
| `2010` | Event Escrow | LIABILITY | credit | organizers' money, per event |
| `2020` | Pending Commission | LIABILITY | credit | **collected, not earned** |
| `2030` | Refunds Payable | LIABILITY | credit | approved refunds not yet paid |
| `4010` | Earned Revenue | REVENUE | credit | recognised commission |
| `5010` | Payment Processing Fees | EXPENSE | debit | the provider's collection fee |
| `5020` | Refund Processing Fees | EXPENSE | debit | the provider's refund fee, when the platform bears it |
| `5030` | Chargeback Losses | EXPENSE | debit | unrecoverable reversals |
| `6010` | Operations | ASSET | debit | working funds |

Ten accounts. `2010` is sub-ledgered per event by `booking_escrow_accounts`; every other
code is a single balance.

### Documents

`booking_escrow_accounts` — the per-event sub-ledger.

| Field | Type | Notes |
|---|---|---|
| `_id`, `eventId` | `String` | `eventId` **unique** |
| `organizationId` | `String` | |
| `status` | `EscrowStatus` | `ACTIVE`, `HOLD`, `PAYOUT_ELIGIBLE`, `SUSPENDED`, `CLOSED` |
| `currentBalance` | `BigDecimal` | **a projection of the journal** |
| `totalCredited`, `totalDebited`, `totalRefunded` | `BigDecimal` | projections |
| `currency` | `String` | |
| `holdUntil` | `Instant` | `endsAt + hold-period` |
| `openDisputeCount` | `int` | blocks `PAYOUT_ELIGIBLE` |
| `openedAt`, `closedAt` | `Instant` | |
| `version` | `Long` | `@Version` |

`booking_journal_entries` — append-only.

| Field | Type | Notes |
|---|---|---|
| `_id` | `String` | |
| `entryType` | `JournalEntryType` | `TICKET_SALE`, `COMMISSION_RECOGNITION`, `REFUND`, `PAYOUT`, `CHARGEBACK`, `PROVIDER_FEE`, `MANUAL_ADJUSTMENT`, `TRANSFER` |
| `referenceType`, `referenceId` | `String` | **never null** |
| `description` | `String` | |
| `postedAt` | `Instant` | |
| `postedById` | `String` | null for system entries |
| `reversesEntryId` | `String` | on a reversal |
| `currency` | `String` | |

`booking_journal_lines` — append-only, ≥ 2 per entry.

| Field | Type | Notes |
|---|---|---|
| `_id`, `journalEntryId` | `String` | |
| `accountCode` | `String` | resolves against the chart |
| `escrowAccountId` | `String` | set when `accountCode = 2010` |
| `direction` | `BalanceDirection` | `DEBIT`, `CREDIT` |
| `amount` | `BigDecimal` | always positive; direction carries the sign |
| `currency` | `String` | |
| `referenceType`, `referenceId` | `String` | |
| `postedAt` | `Instant` | |

`booking_platform_accounts` — three singleton rows.

| Field | Notes |
|---|---|
| `accountType` | `PENDING_COMMISSION`, `EARNED_REVENUE`, `OPERATIONS` — **unique** |
| `accountCode` | `2020`, `4010`, `6010` |
| `currentBalance`, `currency`, `version` | projection, `@Version` |

### The four canonical entries

**Ticket sale** — written inside the confirmation transaction.

| Account | Direction | Amount |
|---|---|---|
| `1010` Provider Settlement Receivable | debit | gross |
| `2010` Event Escrow | credit | net |
| `2020` Pending Commission | credit | commission |

**Provider collection fee** — a separate entry, never reducing the buyer's charge.

| Account | Direction | Amount |
|---|---|---|
| `5010` Payment Processing Fees | debit | fee |
| `1010` Provider Settlement Receivable | credit | fee |

**Commission recognition** — [ET-FIN-002](../002-commission/), at completion + hold.

| Account | Direction | Amount |
|---|---|---|
| `2020` Pending Commission | debit | commission |
| `4010` Earned Revenue | credit | commission |

**Payout** — [ET-FIN-003](../003-payouts-and-settlement/), on settlement.

| Account | Direction | Amount |
|---|---|---|
| `2010` Event Escrow | debit | payout |
| `1020` Platform Bank | credit | payout |

Every one balances. Refunds and chargebacks are [ET-FIN-004](../004-refunds-and-chargebacks/)'s
and follow the same shape.

### Escrow lifecycle

| From | Trigger | To |
|---|---|---|
| — | `catalog.EventPublished` | `ACTIVE` |
| `ACTIVE` | `catalog.EventCompleted` | `HOLD`, `holdUntil = endsAt + P7D` |
| `HOLD` | sweep, `holdUntil` passed, no open disputes | `PAYOUT_ELIGIBLE` |
| `PAYOUT_ELIGIBLE` | payout settles, balance zero | `CLOSED` |
| `ACTIVE`, `HOLD`, `PAYOUT_ELIGIBLE` | admin suspends | `SUSPENDED` |
| `SUSPENDED` | admin reactivates | previous |
| `ACTIVE` | `catalog.EventCancelled`, all refunds settled | `CLOSED` |

A `CLOSED` account accepts nothing. `catalog.EventRescheduled` recomputes `holdUntil`.

### The debit, conditional and atomic

```java
// balance check and debit in one round trip — a read-then-write races a concurrent payout
Query q = Query.query(Criteria.where("_id").is(escrowId)
        .and("status").in(ACTIVE, HOLD, PAYOUT_ELIGIBLE)
        .and("currentBalance").gte(amount));

return mongo.findAndModify(q, new Update().inc("currentBalance", amount.negate())
                                          .inc("totalDebited", amount),
                           options().returnNew(true), EscrowAccount.class)
            .switchIfEmpty(Mono.error(new EscrowInsufficientBalance(escrowId)))
            .flatMap(acct -> ledger.post(entry));    // the lines, same transaction
```

### GraphQL

Subgraph `booking`. Every field carries `@auth`; every finance field carries
`@tag(name: "admin")` unless it is the organizer's own.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `escrowAccount(eventId)` | query | `ORGANIZER` | `EscrowAccount` |
| `myEscrowAccounts(organizationId, page)` | query | `ORGANIZER` | `EscrowAccountPage!` |
| `escrowTransactions(escrowAccountId, page)` | query | `ORGANIZER` | `EscrowTransactionPage!` |
| `platformAccounts` | query | `FINANCE` | `[PlatformAccount!]!` `@tag(name: "admin")` |
| `chartOfAccounts` | query | `FINANCE` | `[ChartAccount!]!` `@tag(name: "admin")` |
| `journalEntries(filter, page)` | query | `FINANCE` | `JournalEntryPage!` `@tag(name: "admin")` |
| `trialBalance(asOf)` | query | `FINANCE` | `TrialBalance!` `@tag(name: "admin")` |
| `suspendEscrowAccount(id, reason)` | mutation | `ADMIN` | `EscrowAccount!` `@tag(name: "admin")` |
| `reactivateEscrowAccount(id, reason)` | mutation | `ADMIN` | `EscrowAccount!` `@tag(name: "admin")` |
| `closeEscrowAccount(id, reason)` | mutation | `ADMIN` | `EscrowAccount!` `@tag(name: "admin")` |
| `postManualAdjustment(input)` | mutation | `SUPER_ADMIN` | `JournalEntry!` `@tag(name: "admin")` |
| `transferToOperations(amount, reason)` | mutation | `SUPER_ADMIN` | `PlatformAccount!` `@tag(name: "admin")` |

`EscrowAccount` is contributed to `Event` as `escrowAccount`, `@tag(name: "admin")`
([ET-PLT-004 §4](../../_platform/004-federation-contract/)).

### Consumed events

| Wire name | Effect |
|---|---|
| `catalog.EventPublished` | open the escrow account, idempotent on `eventId` |
| `catalog.EventCompleted` | `ACTIVE` → `HOLD`, set `holdUntil` |
| `catalog.EventRescheduled` | recompute `holdUntil` |
| `catalog.EventCancelled` | begin the refund-and-close sequence ([ET-FIN-004](../004-refunds-and-chargebacks/)) |

### Sweeps

| Sweep | Lock | Interval | Purpose |
|---|---|---|---|
| hold release | `lock:sweep:escrow-hold` | `PT1H` | `HOLD` → `PAYOUT_ELIGIBLE` |

### Configuration

| Property | Value |
|---|---|
| `finance.escrow.hold-period` | `P7D` |
| `finance.ledger.rounding` | `HALF_UP`, scale 2 |
| `finance.ledger.base-currency` | `ZMW` |

### Error codes

`ESCROW_ACCOUNT_UNKNOWN`, `ESCROW_NOT_ACTIVE`, `ESCROW_INSUFFICIENT_BALANCE`,
`JOURNAL_UNBALANCED`, `ACCOUNT_CODE_UNKNOWN` — rows of
[ET-PLT-005 §4](../../_platform/005-error-contract/) introduced by this spec.
`JOURNAL_UNBALANCED` is `INTERNAL` deliberately.

## 5. Tasks

- [ ] **T1 · Seed the chart of accounts and the three platform singletons**
  - requirements: R1, R7
  - files: `backend/booking-service/.../migration/ChartOfAccountsSeeder.java`
  - verify: idempotent seeding; a second platform account of any type is rejected
  - parallel-safe: no
  - depends: —

- [ ] **T2 · `LedgerService`: balanced entries, append-only, the validator**
  - requirements: R2
  - files: `backend/booking-service/.../service/impl/LedgerServiceImpl.java`
  - verify: an unbalanced entry refuses; no method updates or deletes an entry
  - parallel-safe: no — every money spec depends on it
  - depends: T1

- [ ] **T3 · Balances as projections; `recomputeBalance`; the 1,000-movement test**
  - requirements: R3
  - files: `backend/booking-service/.../service/impl/LedgerServiceImpl.java`
  - verify: no production field typed `double` or `float` names an amount, balance, price, fee, total or commission; every rounding is `HALF_UP`; no balance is assigned outside the ledger; cached equals recomputed exactly
  - parallel-safe: no
  - depends: T2

- [ ] **T4 · The escrow account, its unique index and its lifecycle consumers**
  - requirements: R4
  - files: `backend/booking-service/.../domain/model/EscrowAccount.java`, `.../event/listener/`
  - verify: a re-publish opens no second account; a reschedule recomputes `holdUntil`
  - parallel-safe: yes
  - depends: T2

- [ ] **T5 · The purchase split entry and the separate provider-fee entry**
  - requirements: R5
  - files: `backend/booking-service/.../service/impl/EscrowServiceImpl.java`
  - verify: 100 tickets across 3 tiers sum exactly; the fee never touches the buyer's charge
  - parallel-safe: no — inside the confirmation transaction
  - depends: T4

- [ ] **T6 · The conditional atomic debit and the negative-balance invariant test**
  - requirements: R6
  - files: `backend/booking-service/.../repository/impl/EscrowAccountRepositoryImpl.java`
  - verify: two parallel over-debits yield one success; no balance is ever negative
  - parallel-safe: no
  - depends: T5

- [ ] **T7 · The hold-release sweep and the dispute block**
  - requirements: R4
  - files: `backend/booking-service/.../scheduler/EscrowHoldSweeper.java`
  - verify: an account with an open dispute does not become `PAYOUT_ELIGIBLE`
  - parallel-safe: yes
  - depends: T4

- [ ] **T8 · Manual adjustments, the reversal link, the audit row and the metric**
  - requirements: R8
  - files: `backend/booking-service/.../service/impl/LedgerServiceImpl.java`
  - verify: no existing entry is ever modified; every adjustment is audited
  - parallel-safe: yes
  - depends: T2

- [ ] **T9 · The subgraph half; `@tag(name: "admin")` on every finance field**
  - requirements: R1–R8
  - files: `backend/booking-service/src/main/resources/graphql/schema.graphqls`
  - verify: the public contract exposes no ledger, platform-account or journal field
  - parallel-safe: no — shared SDL across booking's specs
  - depends: T5

## 6. Out of scope

| Capability | Spec |
|---|---|
| How the commission amount is calculated and when it is recognised | [ET-FIN-002](../002-commission/) |
| Payout eligibility, requests and settlement | [ET-FIN-003](../003-payouts-and-settlement/) |
| Refunds, cancellation refunds and chargebacks | [ET-FIN-004](../004-refunds-and-chargebacks/) |
| Reconciling the ledger against the provider and the bank | [ET-FIN-005](../005-reconciliation/) |
| The purchase that triggers the sale entry | [ET-TKT-001](../../ticketing/001-reservation-and-hold/) |
| Collecting the money in the first place | [ET-PAY-001](../../payment/001-payment-intents-and-providers/) |
| Financial dashboards and revenue reporting | [ET-ADM-004](../../admin/004-analytics-and-statistics/) |
| Audit rows for manual adjustments | [ET-PLT-009](../../_platform/009-audit-trail/) |

Deliberately never in scope: **per-organizer escrow** (every refund obligation becomes a
query with a date filter), **assigning a balance directly** (it destroys the ability to
explain a discrepancy), and **an adjustment line with no reference** (it becomes the place
every unexplained difference is hidden).
