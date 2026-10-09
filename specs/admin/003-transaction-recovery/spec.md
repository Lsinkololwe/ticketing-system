# ET-ADM-003 · Transaction recovery — stuck money and the operator's tools

> **Conformance** · PDI Phase 8 dead-letter queue and recovery
>
> **Verified 2026-10-05 (integration tests `FinanceOpsTest`, `AdminFinanceOpsTest`, `PaymentOperationsTest`).** A platform transfer is one balanced entry per idempotency key (unique index
> `booking_platform_transfers.idempotencyKey`), moves nothing when it is refused, and conserves total platform money under simultaneous transfers. A proposal cannot be confirmed by its maker,
> after its two hours, or without the role (ADMIN covers FINANCE; only SUPER_ADMIN proposes or confirms a force-complete); six simultaneous confirmers run the action once; a refused action leaves the
> proposal `FAILED` with the reason. Force-complete applies only the provider's confirmed answers. A chargeback recovery is recorded once per reference, bounded by what is unrecovered, and records who
> recorded it.

## 1. Capability

Every money spec in this corpus has an escape hatch that ends with the words *escalate to an
operator*. A payment stuck `PENDING` past thirty minutes. A webhook orphan that never
matched. A payout debited but never confirmed. A reconciliation item nobody resolved. A
dead-lettered message that cannot be processed. Each of those is money in an ambiguous
state, and every one of them was deliberately left for a human because the alternative —
guessing — is worse.

This spec is where those humans work. It declares the **recovery queue** that gathers every
escalation from every source into one place, the actions an operator can take on each kind,
and the guard rails that stop a recovery action becoming the incident. It declares the
dead-letter surface: what is in the queue, why it failed, and how to replay it safely.

The rule that shapes it is that **recovery actions are the most dangerous mutations in the
platform**. They operate on money in an unknown state, by hand, usually under pressure,
often at night. So every one of them is idempotent, every one requires a reason, every one
is audited, and the ones that move money require a second person.

## 2. Design decisions

**One queue, many sources.** A stuck payment, an orphaned webhook, an unconfirmed payout, a
reconciliation item and a dead letter are different objects, but an operator on shift wants
one list ordered by how much money is at risk. The queue is a projection over the sources,
not a copy — the underlying document remains authoritative.

**Ordered by amount at risk, then by age.** Not FIFO. A K50 stuck payment and a K48,000
unconfirmed payout are not equally urgent, and an operator working oldest-first will spend
the morning on small ones. Amount first, age as the tiebreak, with a floor so that small
items do not starve forever.

**Every action is idempotent and every action is recorded.** Re-running a recovery is the
normal case — an operator retries, a page is refreshed, two operators act. Each action is a
compare-and-set, and the second one reports "already done" rather than doing it again.

**Money-moving recoveries need two people.** Forcing a payment to succeeded, releasing a
held reservation, reversing a payout, writing off a reconciliation item above the limit —
each requires an operator to propose and a second to confirm. The pair are different
identities, checked as in [ET-FIN-003](../../finance/003-payouts-and-settlement/) R3, and
the proposal expires.

**A recovery never invents a fact.** An operator can re-drive the platform to ask the
provider again, can apply an outcome the provider has confirmed, and can record a
write-off. They cannot mark a payment succeeded because it probably was. That distinction
is the difference between a recovery tool and a fraud tool.

**Dead letters are inspectable, replayable and never silently dropped.** The queue's depth
is an alert; each message shows its payload, its failure and its attempt history; replay
runs through the same idempotent consumer, which is safe because
[ET-PLT-003](../../_platform/003-event-contract/) R5 makes every consumer idempotent.

**Bulk retry exists for the failure classes where it is safe, and is capped.** Fifty
payments that failed because the provider was down are one action. Fifty payments that
failed for fifty reasons are fifty decisions. Bulk applies only to classes declared safe in
§4, and only to a bounded batch.

**Nothing here is reachable without `FINANCE` or `ADMIN`, and the highest actions need
`SUPER_ADMIN`.** These are the mutations that would be most valuable to an attacker.

## 3. Requirements

### ET-ADM-003-R1 · One queue gathers every escalation

THE SYSTEM SHALL present a single recovery queue projecting every escalated item from the
§4 sources.

**Acceptance**
- [ ] The queue includes stuck payment intents, orphaned webhooks, failed payouts, unconfirmed payouts, open reconciliation items, failed refunds and dead-lettered messages
- [ ] Each row carries its source, its subject id, the amount at risk, its age, its failure reason and its available actions
- [ ] The queue is a projection — resolving an item updates the underlying document, and the queue reflects it
- [ ] It is `FINANCE`-gated, `@tag(name: "admin")`, and paged
- [ ] Ordering is by amount at risk descending, then age descending, with items older than `admin.recovery.starvation-floor` (P7D) floated regardless of amount
- [ ] Total amount at risk and item count by source are metrics that alert
- [ ] A test seeds one item of each source and asserts all appear

### ET-ADM-003-R2 · Every recovery action is idempotent

WHEN a recovery action is applied twice, THE SYSTEM SHALL apply its effect once.

**Acceptance**
- [ ] Every action is a compare-and-set from the expected state
- [ ] A second application returns the existing outcome and is not an error
- [ ] Two operators applying one action concurrently produce one effect, asserted under contention
- [ ] Every action carries an `idempotencyKey` where it moves money
- [ ] `Ledger.assertBalanced()` and `Inventory.assertConserved()` hold after every doubled action
- [ ] A test applies each action twice and asserts a single effect

### ET-ADM-003-R3 · Money-moving recoveries require two people

IF a recovery action moves money, THEN THE SYSTEM SHALL require a proposal by one operator
and a confirmation by another.

**Acceptance**
- [ ] The §4 table marks each action `single` or `dual`
- [ ] A `dual` action creates a proposal with a reason, valid for `admin.recovery.proposal-ttl` (PT2H)
- [ ] The confirmer's identity must differ from the proposer's; equality is refused with `ACTOR_NOT_PERMITTED`
- [ ] An expired proposal applies nothing and must be re-proposed
- [ ] Both identities, both timestamps and both reasons are recorded and audited
- [ ] A proposal may be withdrawn by the proposer while pending
- [ ] A test grants one actor every role and asserts they cannot confirm their own proposal

### ET-ADM-003-R4 · A recovery re-drives the platform; it never invents an outcome

THE SYSTEM SHALL permit only actions that re-query, re-apply a confirmed outcome, or record
a loss.

**Acceptance**
- [ ] `requeryProvider` calls the provider's status API and applies whatever it returns through the normal path
- [ ] `applyConfirmedOutcome` requires the operator to name the provider reference and is refused unless the provider's status API agrees
- [ ] No action sets a payment to `SUCCEEDED` without provider confirmation — asserted directly
- [ ] `writeOffItem` records a loss to the appropriate expense account and does not fabricate a receipt
- [ ] `releaseStuckReservation` releases inventory and refunds any captured payment; it never issues a ticket
- [ ] A test attempts to force a success for a payment the provider reports as failed and asserts a refusal

### ET-ADM-003-R5 · Stuck transactions are surfaced with everything needed to decide

THE SYSTEM SHALL present each stuck item with its full history.

**Acceptance**
- [ ] A stuck payment shows every `booking_payment_attempts` row with provider reference, status, timing and the mapped outcome
- [ ] An orphaned webhook shows its raw body, its correlation attempts and its age
- [ ] An unconfirmed payout shows the escrow debit, the transfer attempt and the bank reconciliation state
- [ ] Every item links to its reservation, tickets, escrow and journal entries
- [ ] The provider's raw message is visible **here**, to `FINANCE` only, and nowhere else in the platform ([ET-PLT-005](../../_platform/005-error-contract/) §2)
- [ ] Viewing an item's provider detail writes an audit row
- [ ] A test asserts the raw message is absent from every non-recovery surface

### ET-ADM-003-R6 · Dead letters are visible, replayable and bounded

THE SYSTEM SHALL expose the dead-letter queue and SHALL permit safe replay.

**Acceptance**
- [ ] `deadLetters(topic, subscription, page)` lists messages with their envelope, failure reason, delivery count and dead-lettered time
- [ ] `replayDeadLetter(id)` re-delivers through the normal consumer path
- [ ] Replay is safe because every consumer is idempotent ([ET-PLT-003](../../_platform/003-event-contract/) R5) — the acceptance test replays one of each message type and asserts no double effect
- [ ] `discardDeadLetter(id, reason)` requires `SUPER_ADMIN` and a reason, and is audited
- [ ] Dead-letter depth per subscription is a metric that alerts above zero for longer than a configured grace
- [ ] A message dead-lettered for an unsupported schema version is listed with that reason and is not replayable until the consumer supports it
- [ ] Nothing deletes a dead letter automatically

### ET-ADM-003-R7 · Bulk retry applies only to declared-safe classes

WHERE a failure class is marked bulk-safe, THE SYSTEM SHALL permit a bounded batch retry.

**Acceptance**
- [ ] The §4 table marks each failure class bulk-safe or not
- [ ] `bulkRetry` accepts up to `admin.recovery.bulk-max` (50) ids of one class
- [ ] An id whose class is not bulk-safe is skipped and reported, not silently included
- [ ] Each id's outcome is reported individually
- [ ] A bulk retry requires a reason and is audited as one action with its ids
- [ ] No bulk action exists for any `dual` action
- [ ] A test submits a mixed batch and asserts the unsafe ids are skipped

### ET-ADM-003-R8 · The queue's health is the platform's financial-risk signal

THE SYSTEM SHALL report the queue's size, age and value as first-class metrics.

**Acceptance**
- [ ] Total amount at risk, item count by source, oldest item age and median resolution time are metrics
- [ ] Total amount at risk above `admin.recovery.risk-alert-threshold` (K50,000) alerts
- [ ] An item unresolved past `admin.recovery.escalation-window` (P3D) alerts individually
- [ ] Resolution throughput per operator is reportable
- [ ] The dashboard is [ET-ADM-005](../005-observability-and-health/)'s; this spec provides the figures
- [ ] A test seeds items totalling above the threshold and asserts the alert fires

## 4. Model

> **Amended 2026-09-01 under [D-19](../../ROADMAP.md).** `markForReview` → `markPayoutForReview` — FINANCE matches the shipped `hasAnyRole('ADMIN','FINANCE')`; the singular form matches §4's `RecoveryItem!`.

> **Amended 2026-09-01 under [D-19](../../ROADMAP.md).** 1 operation name below adopts the
> shipped name: `bulkRetry` → `bulkRetryFailedPayouts`. D-19 rules that where the schema and §4 disagree on an operation's
> *name*, the schema stands and §4 adopts it.
>
> **Only the names were adopted.** Argument lists and return types were not re-verified against
> the schema, so a row here can now name a real operation and still describe it wrongly. That
> gap is unmeasured, and calling it verified would be the same mistake as counting a file's
> existence as proof it runs.

### Queue sources

| Source | Condition | Amount at risk | Spec |
|---|---|---|---|
| stuck payment | intent `PENDING` past `max-pending` | intent amount | [ET-PAY-001](../../payment/001-payment-intents-and-providers/) R5 |
| orphaned webhook | receipt `ORPHANED` past `orphan-escalate` | claimed amount | [ET-PAY-002](../../payment/002-webhooks-and-settlement/) R5 |
| disputed webhook | receipt `DISPUTED` | claimed amount | [ET-PAY-002](../../payment/002-webhooks-and-settlement/) R3 |
| failed payout | request `FAILED` | requested amount | [ET-FIN-003](../../finance/003-payouts-and-settlement/) R7 |
| unconfirmed payout | `PAYOUT_UNCONFIRMED` item | payout amount | [ET-FIN-005](../../finance/005-reconciliation/) R4 |
| reconciliation item | any `OPEN` item past its class threshold | difference | [ET-FIN-005](../../finance/005-reconciliation/) R6 |
| failed refund | request `FAILED` | refund amount | [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) |
| dead letter | any message in a dead-letter subscription | 0 or the payload's amount | [ET-PLT-003](../../_platform/003-event-contract/) R6 |

The queue is a **projection**. Resolving an item mutates the source document; the queue is
recomputed.

### Actions

| Action | Applies to | Effect | Approval |
|---|---|---|---|
| `requeryProvider` | stuck payment, unconfirmed payout | ask the provider, apply what it says | **single** |
| `applyConfirmedOutcome` | stuck payment, orphan | apply an outcome the provider confirms | **dual** |
| `releaseStuckReservation` | stuck payment | release inventory, refund any capture | **dual** |
| `retryPayout` | failed payout | re-attempt through [ET-FIN-003](../../finance/003-payouts-and-settlement/) R7 | **single** |
| `reversePayout` | unconfirmed payout | restore the escrow with a reversing entry | **dual** |
| `retryRefund` | failed refund | re-attempt through [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) | **single** |
| `resolveItem` | reconciliation item | record an explanation, post an entry if needed | **single** |
| `writeOffItem` | reconciliation item | expense the difference | **dual** above `write-off-limit` |
| `replayDeadLetter` | dead letter | re-deliver through the consumer | **single** |
| `discardDeadLetter` | dead letter | remove with a reason, `SUPER_ADMIN` | **dual** |
| `markForReview` | any | flag and assign, no money moves | **single** |

Every `dual` action moves money or destroys a record. Every `single` action either asks a
question or re-drives an existing, already-authorised operation.

### The proposal

`booking_recovery_proposals`

| Field | Notes |
|---|---|
| `_id`, `itemSource`, `itemId` | |
| `action` | one of the `dual` actions |
| `proposedById`, `proposedAt`, `proposalReason` | reason ≥ 20 characters |
| `confirmedById`, `confirmedAt`, `confirmationReason` | **≠ `proposedById`** |
| `status` | `PENDING`, `CONFIRMED`, `WITHDRAWN`, `EXPIRED`, `APPLIED` |
| `expiresAt` | `PT2H` |
| `idempotencyKey` | |
| `outcome` | recorded after application |

### Bulk-safe failure classes

| Class | Bulk-safe | Why |
|---|---|---|
| `PROVIDER_UNAVAILABLE` | ✅ | one cause, one fix, re-drive is harmless |
| `PROVIDER_TIMEOUT` | ✅ | same |
| `NETWORK_ERROR` | ✅ | same |
| `PAYMENT_DECLINED` | ❌ | each is a subscriber's own circumstance |
| `AMOUNT_MISMATCH` | ❌ | each needs a human to look at |
| `SUSPECTED_FRAUD` | ❌ | never |
| `BAD_ACCOUNT_DETAILS` | ❌ | each needs a corrected account |
| `UNSUPPORTED_SCHEMA_VERSION` | ❌ | replay fails again until the consumer ships |

Bulk applies to `single` actions only, capped at 50, one class per batch.

### The provider-detail exception

[ET-PLT-005](../../_platform/005-error-contract/) §2 forbids returning a provider's raw
message anywhere. This spec is the one exception: `recoveryItemDetail` exposes
`providerStatusRaw` and `providerMessageRaw` from `booking_payment_attempts`
([ET-PAY-001](../../payment/001-payment-intents-and-providers/) §4) to `FINANCE` only,
`@tag(name: "admin")`, and every access writes an audit row.

Without it an operator is diagnosing a stuck payment from a five-value enum, which is not
enough to decide anything.

### GraphQL

Subgraph `booking`. Every field `FINANCE` or higher, every field `@tag(name: "admin")`.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `recoveryQueue(source, page)` | query | `FINANCE` | `RecoveryItemPage!` |
| `recoveryItemDetail(source, itemId)` | query | `FINANCE` | `RecoveryItemDetail!` |
| `recoveryMetrics` | query | `FINANCE` | `RecoveryMetrics!` |
| `myRecoveryProposals` | query | `FINANCE` | `[RecoveryProposal!]!` |
| `pendingRecoveryProposals` | query | `FINANCE` | `[RecoveryProposal!]!` |
| `deadLetters(topic, subscription, page)` | query | `ADMIN` | `DeadLetterPage!` |
| `requeryProvider(input)` | mutation | `FINANCE` | `RecoveryOutcome!` |
| `retryPayout(id)` | mutation | `FINANCE` | `PayoutRequest!` |
| `retryRefund(id)` | mutation | `FINANCE` | `RefundRequest!` |
| `resolveReconciliationItem(input)` | mutation | `FINANCE` | `ReconciliationItem!` |
| `markPayoutForReview(input)` | mutation | `FINANCE` | `RecoveryItem!` |
| `proposeRecoveryAction(input)` | mutation | `FINANCE` | `RecoveryProposal!` |
| `confirmRecoveryAction(id, reason)` | mutation | `FINANCE` | `RecoveryOutcome!` |
| `withdrawRecoveryProposal(id)` | mutation | `FINANCE` | `RecoveryProposal!` |
| `bulkRetryFailedPayouts(input)` | mutation | `FINANCE` | `[RecoveryOutcome!]!` |
| `replayDeadLetter(id)` | mutation | `ADMIN` | `RecoveryOutcome!` |
| `discardDeadLetter(id, reason)` | mutation | `SUPER_ADMIN` | `Boolean!` |

`retryPayout` and `retryRefund` are the same mutations
[ET-FIN-003](../../finance/003-payouts-and-settlement/) and
[ET-FIN-004](../../finance/004-refunds-and-chargebacks/) declare — listed here because this
is where an operator reaches them, not because this spec redeclares them.

### Ordering

```
sort by: amountAtRisk desc, age desc
float:   any item older than admin.recovery.starvation-floor (P7D), regardless of amount
```

The floor is what stops a K20 item sitting behind larger ones forever.

### Configuration

| Property | Value |
|---|---|
| `admin.recovery.proposal-ttl` | `PT2H` |
| `admin.recovery.bulk-max` | 50 |
| `admin.recovery.starvation-floor` | `P7D` |
| `admin.recovery.escalation-window` | `P3D` |
| `admin.recovery.risk-alert-threshold` | `K50,000` |

### Error codes

`TRANSACTION_NOT_RECOVERABLE` — a row of
[ET-PLT-005 §4](../../_platform/005-error-contract/) introduced by this spec.
`ACTOR_NOT_PERMITTED` is raised by R3's identity check.

## 5. Tasks

- [ ] **T1 · The queue projection across all eight sources**
  - requirements: R1
  - files: `backend/booking-service/.../service/impl/RecoveryQueueService.java`
  - verify: one item of each source appears; resolving updates the source document
  - parallel-safe: no
  - depends: —

- [ ] **T2 · Ordering by amount at risk with the starvation floor**
  - requirements: R1
  - files: `backend/booking-service/.../repository/impl/RecoveryQueueRepository.java`
  - verify: a K20 item older than 7 days floats above a K48,000 item from yesterday
  - parallel-safe: yes
  - depends: T1

- [ ] **T3 · The `single` actions, each idempotent**
  - requirements: R2, R4
  - files: `backend/booking-service/.../service/impl/RecoveryActionService.java`
  - verify: each applied twice yields one effect; ledger and inventory hold
  - parallel-safe: yes
  - depends: T1

- [ ] **T4 · Proposals, confirmation, expiry and the identity check**
  - requirements: R3
  - files: `backend/booking-service/.../domain/model/RecoveryProposal.java`
  - verify: one actor with every role cannot confirm their own proposal; an expired proposal applies nothing
  - parallel-safe: no
  - depends: T3

- [ ] **T5 · The no-invention guard on `applyConfirmedOutcome`**
  - requirements: R4
  - files: `backend/booking-service/.../service/impl/RecoveryActionService.java`
  - verify: forcing a success the provider reports as failed is refused
  - parallel-safe: yes
  - depends: T4

- [ ] **T6 · Item detail, the provider-raw exception and its audit**
  - requirements: R5
  - files: `backend/booking-service/.../web/graphql/query/RecoveryQueryResolver.java`
  - verify: the raw message appears here and nowhere else; every access is audited
  - parallel-safe: yes
  - depends: T1

- [ ] **T7 · The dead-letter surface, replay and discard**
  - requirements: R6
  - files: `backend/booking-service/.../infrastructure/deadletter/`
  - verify: replaying one of each message type causes no double effect; nothing auto-deletes
  - parallel-safe: yes
  - depends: T3

- [ ] **T8 · Bulk retry, its safe classes and its per-id outcomes**
  - requirements: R7
  - files: `backend/booking-service/.../service/impl/RecoveryActionService.java`
  - verify: a mixed batch skips unsafe ids; no bulk applies to a `dual` action
  - parallel-safe: yes
  - depends: T3

- [ ] **T9 · Metrics, thresholds and the per-item escalation alert**
  - requirements: R8
  - files: `backend/booking-service/.../infrastructure/metrics/`
  - verify: seeded items above the threshold alert; an item past 3 days alerts individually
  - parallel-safe: yes
  - depends: T2

## 6. Out of scope

| Capability | Spec |
|---|---|
| Why a payment gets stuck, and the poll that precedes escalation | [ET-PAY-001](../../payment/001-payment-intents-and-providers/) |
| Orphaned and disputed webhooks | [ET-PAY-002](../../payment/002-webhooks-and-settlement/) |
| Payout failure and its compensation | [ET-FIN-003](../../finance/003-payouts-and-settlement/) |
| Refund failure | [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) |
| Reconciliation, its classes and its thresholds | [ET-FIN-005](../../finance/005-reconciliation/) |
| The dead-letter queue's configuration and retry policy | [ET-PLT-003](../../_platform/003-event-contract/) |
| Dashboards and alert routing | [ET-ADM-005](../005-observability-and-health/) |
| Audit row shape and retention | [ET-PLT-009](../../_platform/009-audit-trail/) |

Deliberately never in scope: **forcing an outcome the provider has not confirmed** (that is
a fraud tool, not a recovery tool), **bulk action on any dual-approval operation**, and
**automatic deletion of a dead letter** (the message is the only evidence of what failed).
