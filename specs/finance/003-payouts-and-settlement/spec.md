# ET-FIN-003 · Payout eligibility, bank accounts and the payout workflow

> **Conformance** · V3 §3.3 bank account linking · V3 §10.1 payout eligibility · V3 §10.2 payout flow · US-ORG-007 · US-FIN-001

## 1. Capability

A payout is the platform keeping its promise. An organizer sold three hundred tickets,
their event happened, the refund window closed, and there is K28,500 in an escrow account
with their name on it. Getting that money from the platform's bank to theirs is the last
step of the whole product, and it is the step where mistakes are least recoverable — a
payout sent to the wrong account is not undone by an apology.

This spec builds it. It declares **eligibility** as a conjunction of four conditions that
are each checked at request and re-checked at approval, because time passes between them.
It declares **bank accounts** and their verification, because the platform must not send
money to an account nobody has proved exists. It declares the **payout workflow** —
request, approve, debit, transfer, confirm, one Temporal execution per escrow account — with a compensation path for the transfer that
fails after the escrow has already been debited, which is the failure that leaves an
organizer's money in limbo.

And it declares the control that makes all of it safe: **the person who requests a payout
is never the person who approves it.** An organizer requests; a finance operator approves.
That separation is the difference between a platform with a payments function and a
platform with an unmonitored withdrawal endpoint.

## 2. Design decisions

**Four eligibility conditions, all four required, checked twice.** The event has completed;
the hold period has elapsed; there are no open disputes; the balance is above the minimum.
They are checked when the organizer requests and again when finance approves, because days
pass between and a chargeback can arrive in that window. A single check at request would
approve a payout against money that has since been claimed back.

**Payout is per escrow account, therefore per event.** An organizer with three completed
events makes three requests. This follows from D-05 and it is what makes the eligibility
question answerable at all — "has the hold elapsed" is a property of one event's end date.

**Requesting and approving are different people, enforced by role.** `payout:request` is
an organization permission; `payout:approve` is a platform `FINANCE` permission
([ET-ORG-003](../../organization/003-permission-resolution/) §4). No actor holds both for
the same payout, and an actor who somehow did is refused — the check is on identity, not
only on role.

**A bank account is verified by micro-deposit before it can receive anything.** The
platform sends a small amount, the organizer confirms what arrived, and only then is the
account `VERIFIED`. It proves the account exists, that the organizer controls it, and that
the account number was typed correctly — which is the failure the confirmation actually
catches. An unverified account is refused with `BANK_ACCOUNT_NOT_VERIFIED`.

**Bank account details are encrypted at rest and masked everywhere else.** The full account
number is written once, encrypted, and every read path returns the masked form. A support
screen showing a full account number is a support screen that gets photographed.

**The escrow is debited when the transfer is initiated, not when it settles — and the
compensation is explicit.** Debiting late means two concurrent payouts can both pass the
balance check. Debiting early means a failed transfer has taken money out of an account it
must be returned to. The platform debits early and compensates on failure with a balanced
reversing entry, because the double-spend is unrecoverable and the reversal is not.

**A failed transfer retries three times, then stops and escalates.** Retrying forever
against a rejected account details produces a support case with fifty identical failures.
Three attempts, then `FAILED` with the escrow restored and finance notified.

**Partial payouts are not built.** An organizer takes the whole balance or none of it.
Partial withdrawal invites a fee structure, a minimum-remaining rule and an argument about
which tickets' money was withdrawn — for a capability nobody has asked for. It is recorded
as rejected so that adding it later is deliberate.

**A payout carries no fee.** The platform earns its per-ticket commission
([ET-FIN-002](../002-commission/)); the settled amount is the full escrow balance, and a payout
request has no platform or processing fee (ROADMAP D-23).

**Rejected alternatives**

- *Checking eligibility only at request.* Approves a payout against money a chargeback has since claimed.
- *Per-organizer payouts across events.* Makes "has the hold elapsed" unanswerable.
- *Allowing an organizer to self-approve above a threshold.* An unmonitored withdrawal endpoint with extra steps.
- *Accepting an unverified bank account with a warning.* The warning is dismissed and the money goes to a mistyped account.
- *Debiting escrow on settlement confirmation.* Two concurrent payouts both pass the balance check.
- *Unlimited retry of a failed transfer.* Fifty identical failures and a support case.
- *Partial payouts.* A fee structure and an apportionment argument for a capability nobody asked for.
- *Storing the full account number in plaintext for support convenience.* It gets photographed.

## 3. Requirements

### ET-FIN-003-R1 · Eligibility is four conditions, checked at request and at approval

WHEN a payout is requested or approved, THE SYSTEM SHALL verify all four eligibility
conditions, and IF any fails, THEN THE SYSTEM SHALL refuse.

**Acceptance**
- [ ] The conditions are: the event is `COMPLETED`; `escrow.holdUntil` has passed; `escrow.openDisputeCount` is zero; `escrow.currentBalance` ≥ `finance.payout.minimum`
- [ ] All four are evaluated by one method over values, testable at layer 1
- [ ] A request failing the hold is refused with `PAYOUT_WINDOW_NOT_OPEN` carrying `opensAt`
- [ ] A request below the minimum is refused with `PAYOUT_BELOW_MINIMUM` carrying `minimumAmount`
- [ ] Approval re-evaluates all four; a payout that became ineligible after request is refused at approval
- [ ] A test makes a request eligible, opens a dispute, and asserts approval refuses
- [ ] Eligibility is exposed to the organizer before they request, with the failing condition named

### ET-FIN-003-R2 · A payout targets one escrow account

THE SYSTEM SHALL scope every payout request to a single event's escrow account.

**Acceptance**
- [ ] `booking_payout_requests` carries exactly one `escrowAccountId` and one `eventId`
- [ ] An organizer with three eligible events makes three requests
- [ ] A second `PENDING` request for one escrow account is refused — at most one open request per account
- [ ] The requested amount equals the escrow's full available balance; a partial amount is refused
- [ ] The amount is recomputed at approval, so a credit or debit between request and approval is reflected
- [ ] A test asserts a payout never spans two events

### ET-FIN-003-R3 · Request and approval are separated by identity, not only by role

WHEN a payout is approved, THE SYSTEM SHALL verify the approver is not the requester.

**Acceptance**
- [ ] `requestPayout` requires `payout:request` on the owning organization
- [ ] `approvePayout` requires the platform `FINANCE` role and `payout:approve`
- [ ] An approver whose `userId` equals `requestedById` is refused with `ACTOR_NOT_PERMITTED`, regardless of the roles they hold
- [ ] `approvedById`, `approvedAt` and any approval note are recorded and audited
- [ ] `rejectPayout` requires a reason and returns the request to the organizer
- [ ] A test grants one actor both roles and asserts they cannot approve their own request

### ET-FIN-003-R4 · A bank account is verified before it can receive money

THE SYSTEM SHALL verify a bank account by micro-deposit before permitting a payout to it.

**Acceptance**
- [ ] `createBankAccount` records `bankName`, `bankCode`, `accountNumber`, `accountHolderName`, `branchCode`, `organizationId`
- [ ] Verification sends `finance.payout.micro-deposit` (K0.50) and requires the organizer to confirm the exact amount
- [ ] A deposit the provider confirms failed has its `5050` entry reversed once and the account returned to `PENDING`; a deposit never answered for keeps its cost (ROADMAP D-31)
- [ ] Three wrong confirmations lock verification for that account for 24 hours
- [ ] Only a `VERIFIED` account may be selected for a payout; anything else refuses with `BANK_ACCOUNT_NOT_VERIFIED`
- [ ] Changing the account number resets verification to `PENDING`
- [ ] Exactly one account per organization may be `isDefault`, enforced by a partial unique index
- [ ] A test asserts an unverified account cannot receive a payout by any path

### ET-FIN-003-R5 · Account numbers are encrypted at rest and masked in every response

THE SYSTEM SHALL encrypt the full account number and SHALL return only a masked form.

**Acceptance**
- [ ] `accountNumber` is encrypted at rest with a key from the environment
- [ ] Every GraphQL and REST response returns `accountNumberMasked` — the last four digits only
- [ ] No log statement, error message, event payload or audit row contains a full account number
- [ ] The plaintext is decrypted only in the adapter that initiates a transfer
- [ ] A test captures all output across a full payout and asserts no full account number appears
- [ ] `accountHolderName` is returned in full — it is what a support conversation actually needs

### ET-FIN-003-R6 · Settlement is a workflow that debits early and compensates on failure

WHEN an approved payout is settled, THE SYSTEM SHALL debit the escrow before initiating the
transfer, and IF the transfer fails, THEN THE SYSTEM SHALL restore the escrow with a
balanced reversing entry.

**Acceptance**
- [ ] The escrow debit and the `PROCESSING` transition are one transaction, before any provider call
- [ ] The journal entry is `debit 2010 Event Escrow` / `credit 1020 Platform Bank` ([ET-FIN-001](../001-escrow-and-ledger/) §4)
- [ ] The transfer is initiated outside that transaction, through `PaymentProviderPort` ([ET-PAY-001](../../payment/001-payment-intents-and-providers/) §4)
- [ ] A failure writes a reversing entry restoring the escrow exactly, and moves the request to `FAILED`
- [ ] `Ledger.assertBalanced()` holds after a failure and its reversal
- [ ] A test forces a transfer failure and asserts the escrow balance is exactly what it was before
- [ ] A worker killed between the debit and the transfer resumes from the `PayoutWorkflow`'s history and resolves to either settled or reversed, never neither — asserted by a time-skipping test and a replay of the recorded history

### ET-FIN-003-R7 · A failed transfer retries three times, then escalates

IF a transfer fails transiently, THEN THE SYSTEM SHALL retry up to three times, and past
that SHALL escalate with the escrow restored.

**Acceptance**
- [ ] `retryPayout` is an update on the `PayoutWorkflow`, accepted while `attemptCount` is below `finance.payout.max-attempts` (3) and within 30 days of the failure
- [ ] Retries use exponential backoff and each is recorded with its provider response
- [ ] Past the limit the request is `FAILED`, the escrow is restored, and finance is notified
- [ ] A provider response indicating bad account details does **not** retry — it fails immediately and marks the bank account `VERIFICATION_FAILED`
- [ ] A `FAILED` payout may be re-requested after the account is corrected and re-verified
- [ ] The failure rate per provider is a metric and alerts

### ET-FIN-003-R8 · The request has seven states and each transition is recorded

THE SYSTEM SHALL admit exactly the seven payout states of §4 and SHALL record who caused
each transition.

**Acceptance**
- [ ] `PayoutStatus` declares exactly `PENDING`, `APPROVED`, `PROCESSING`, `COMPLETED`, `FAILED`, `REJECTED`, `CANCELLED`
- [ ] `COMPLETED`, `REJECTED` and `CANCELLED` are terminal; `FAILED` permits retry and re-request
- [ ] An illegal transition is refused with `PAYOUT_STATE_INVALID` carrying `currentStatus`
- [ ] Every transition records the actor, the timestamp and any reason, and writes an audit row
- [ ] A test drives all `(status, action)` pairs
- [ ] An organizer may cancel only while `PENDING`
- [ ] `booking.PayoutCompleted` is published after commit on settlement

## 4. Model

> **Reconciliation note, 2026-09-01 — `myPayoutRequests` · **built**, not renamed.**
>
> **Resolved 2026-09-01.** Built as `myPayoutRequests(organizationId, status, pagination)`,
> scoped through `CallerScope`. Covered by `CallerScopedReadTest` on a real replica set, which
> asserts a two-organization member sees both and only both, and that a selector naming another
> tenant refuses indistinguishably from one naming nothing. The reasoning that ruled out renaming
> to `payoutRequestsByOrganizer` follows, and still applies to that operation.
>
> `payoutRequestsByOrganizer(organizerId)` takes the subject from the client; §4 asks for the
> caller's own. Renaming would sanction a caller-supplied key where the spec asked for an implicit
> one. Deferred to F-001's read-path conversion, where the scoping is built rather than renamed
> around. (Contrast `createPayoutRequest`, adopted above: it takes an id *and* checks it against
> `authentication.principal.subject`, which is why that one was safe.)

> **Amended 2026-09-01 under [D-19](../../ROADMAP.md).** `requestPayout` → `createPayoutRequest` — the only `create*` among nine `*PayoutRequest` mutations, and it already enforces `#input.organizerId == authentication.principal.subject`.

> **Amended 2026-09-01 under [D-19](../../ROADMAP.md).** 5 operation names below adopt the
> shipped names: `approvePayout` → `approvePayoutRequest`, `bankAccounts` → `bankAccountsByOrganizer`, `cancelPayout` → `cancelPayoutRequest`, `rejectPayout` → `rejectPayoutRequest`, `retryPayout` → `retryPayoutRequest`. D-19 rules that where the schema and §4 disagree on an operation's
> *name*, the schema stands and §4 adopts it.
>
> **Only the names were adopted.** Argument lists and return types were not re-verified against
> the schema, so a row here can now name a real operation and still describe it wrongly. That
> gap is unmeasured, and calling it verified would be the same mistake as counting a file's
> existence as proof it runs.

### The request

`booking_payout_requests`

| Field | Type | Notes |
|---|---|---|
| `_id` | `String` | |
| `escrowAccountId`, `eventId`, `organizationId` | `String` | one event, one escrow |
| `bankAccountId` | `String` | must be `VERIFIED` |
| `requestedAmount`, `settledAmount` | `BigDecimal` | recomputed at approval |
| `currency` | `String` | |
| `status` | `PayoutStatus` | the seven |
| `requestedById`, `requestedAt` | | |
| `approvedById`, `approvedAt`, `approvalNote` | | **≠ `requestedById`** |
| `rejectedById`, `rejectedAt`, `rejectionReason` | | |
| `providerReference` | `String` | the transfer's id |
| `attemptCount`, `lastAttemptAt` | `int`, `Instant` | |
| `failureReason`, `failureCategory` | | |
| `settledAt`, `journalEntryId`, `reversalEntryId` | | |
| `idempotencyKey` | `String` | [ET-PLT-007](../../_platform/007-security-and-authorization/) §4 |
| `version` | `Long` | `@Version` |

### The bank account

`booking_bank_accounts`

| Field | Type | Notes |
|---|---|---|
| `_id`, `organizationId` | `String` | |
| `bankName`, `bankCode`, `branchCode` | `String` | |
| `accountNumber` | `String` | **encrypted at rest** |
| `accountNumberMasked` | `String` | last four only — what every response returns |
| `accountHolderName` | `String` | returned in full |
| `status` | `BankAccountStatus` | `PENDING`, `VERIFYING`, `VERIFIED`, `VERIFICATION_FAILED`, `DISABLED` |
| `isDefault` | `boolean` | partial unique per organization |
| `microDepositAmount` | `BigDecimal` | **never returned** |
| `verificationAttempts` | `int` | three, then a 24-hour lock |
| `verifiedAt`, `lockedUntil` | `Instant` | |
| `createdAt`, `updatedAt` | `Instant` | |

### Eligibility

```java
// one method, layer-1 testable — evaluated at request AND at approval
public PayoutEligibility evaluate(Event event, EscrowAccount escrow, Money minimum) {
    List<String> failures = new ArrayList<>();
    if (event.status() != COMPLETED)                    failures.add("EVENT_NOT_COMPLETED");
    if (escrow.holdUntil().isAfter(clock.instant()))    failures.add("HOLD_NOT_ELAPSED");
    if (escrow.openDisputeCount() > 0)                  failures.add("OPEN_DISPUTES");
    if (escrow.currentBalance().compareTo(minimum) < 0) failures.add("BELOW_MINIMUM");
    return new PayoutEligibility(failures.isEmpty(), failures, escrow.holdUntil());
}
```

### The state machine

| # | From | Action | To | Actor |
|---|---|---|---|---|
| 1 | — | `requestPayout` | `PENDING` | organizer, `payout:request` |
| 2 | `PENDING` | `approvePayout` | `APPROVED` | finance, **≠ requester** |
| 3 | `PENDING` | `rejectPayout` | `REJECTED` | finance |
| 4 | `PENDING` | `cancelPayout` | `CANCELLED` | organizer |
| 5 | `APPROVED` | settle — debit + initiate | `PROCESSING` | system |
| 6 | `PROCESSING` | transfer confirmed | `COMPLETED` | provider |
| 7 | `PROCESSING` | transfer failed, retries exhausted | `FAILED` | system |
| 8 | `FAILED` | `retryPayout` | `PROCESSING` | finance |

Seven states, six actions. `COMPLETED`, `REJECTED` and `CANCELLED` are terminal; `FAILED`
is not, because it must permit retry and re-request.

### The payout workflow

| | |
|---|---|
| Type | `PayoutWorkflow` |
| Id | `payout/{escrowAccountId}` — one open request per escrow account |
| Queues | `booking-finance`; transfers and status reads on `booking-provider` |
| Start | Update-with-Start `submit`, conflict policy `FAIL` |
| Updates | `approve` (its validator refuses the requester), `reject`, `cancel`, `retry`, `confirmTransfer` |
| Signal | `providerCallback` — a verified payout webhook wakes the status poll |
| Reached through | `PayoutProcess` |

```
1  approve                 eligibility re-checked, amount recomputed        → APPROVED
2  debit + mark            [TRANSACTION]                                    → PROCESSING
     journal: debit 2010 / credit 1020
3  initiate transfer       PaymentProviderPort.payout(...)  — OUTSIDE the transaction
4a confirmed               settledAt, stage PayoutCompleted in the outbox   → COMPLETED
4b failed, attempts < 3    backoff, retry step 3
4c failed, attempts = 3    [TRANSACTION] reversing entry, restore escrow    → FAILED
4d no verified answer, 3d  escalate as unconfirmed; never assumed failed  → PROCESSING, flagged
     journal: debit 1020 / credit 2010
5  escrow balance zero     escrow → CLOSED                                  ET-FIN-001
```

Step 2 debits **before** step 3 initiates. That ordering is what stops two concurrent
payouts both passing the balance check, and step 4c is the price of it.

### Micro-deposit verification

| Step | What happens |
|---|---|
| 1 | Organizer adds an account; status `PENDING` |
| 2 | Platform sends `finance.payout.micro-deposit` (K0.50) with a random cent variation, status `VERIFYING` |
| 3 | Organizer confirms the exact amount received |
| 4 | Match → `VERIFIED`; mismatch → `verificationAttempts++` |
| 5 | Three mismatches → `lockedUntil = now + 24h` |

The deposit is a platform expense: `debit 5050 Account Verification Costs` / `credit 1020`, one
entry per deposit, booked by `BankVerificationWorkflow` once the provider accepts it (ROADMAP D-27).
`5050` is a row of [ET-FIN-001](../001-escrow-and-ledger/)'s chart; the seeder creates any standard
account a database lacks at every boot. WHILE the owner has not confirmed, the workflow asks the
provider what became of the deposit; a confirmed failure reverses the entry and returns the account
to `PENDING`, so the owner can start again (ROADMAP D-31).

### GraphQL

Subgraph `booking`. Organizer-scoped fields are not `@tag`ged; platform aggregates are.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `payoutEligibility(eventId)` | query | `ORGANIZER` | `PayoutEligibility!` |
| `payoutRequest(id)` | query | `ORGANIZER` | `PayoutRequest` |
| `myPayoutRequests(organizationId, status, page)` | query | `ORGANIZER` | `PayoutRequestPage!` |
| `payoutRequests(status, page)` | query | `FINANCE` | `PayoutRequestPage!` `@tag(name: "admin")` |
| `payoutRequestsForReview(page)` | query | `FINANCE` | `PayoutRequestPage!` `@tag(name: "admin")` |
| `bankAccountsByOrganizer(organizationId)` | query | `ORGANIZER` | `[BankAccount!]!` |
| `createPayoutRequest(input)` | mutation | `ORGANIZER` | `PayoutRequest!` |
| `cancelPayoutRequest(id)` | mutation | `ORGANIZER` | `PayoutRequest!` |
| `approvePayoutRequest(input)` | mutation | `FINANCE` | `PayoutRequest!` `@tag(name: "admin")` |
| `rejectPayoutRequest(id, reason)` | mutation | `FINANCE` | `PayoutRequest!` `@tag(name: "admin")` |
| `retryPayoutRequest(id)` | mutation | `FINANCE` | `PayoutRequest!` `@tag(name: "admin")` |
| `createBankAccount(input)` | mutation | `ORGANIZER` | `BankAccount!` |
| `updateBankAccount(id, input)` | mutation | `ORGANIZER` | `BankAccount!` |
| `deleteBankAccount(id)` | mutation | `ORGANIZER` | `Boolean!` |
| `setDefaultBankAccount(id)` | mutation | `ORGANIZER` | `BankAccount!` |
| `startBankVerification(id)` | mutation | `ORGANIZER` | `BankAccount!` |
| `confirmBankVerification(id, amount)` | mutation | `ORGANIZER` | `BankAccount!` |

`requestPayout`, `approvePayout` and `retryPayout` each take an `idempotencyKey`
([ET-PLT-007](../../_platform/007-security-and-authorization/) §4).

`BankAccount` never exposes `accountNumber` or `microDepositAmount` — neither field is on
the GraphQL type at all, which is stronger than gating them.

`Organization` is extended with `bankAccounts`, `payoutRequests` and `availableBalance`
([ET-PLT-004 §4](../../_platform/004-federation-contract/)).

### Events

| Tier | Name | When | Consumers |
|---|---|---|---|
| bus | `booking.PayoutCompleted` v1 | staged in the settlement transaction | identity → notify the organizer |

Telling the organizer of an approval and alerting finance of a failure are activities of the
payout workflow, not in-memory events.

### Configuration

| Property | Value |
|---|---|
| `finance.payout.minimum` | `K100.00` |
| `finance.payout.max-attempts` | 3 |
| `finance.payout.retry-backoff` | `PT5M` initial, exponential |
| `finance.payout.micro-deposit` | `K0.50` ± a random cent |
| `finance.payout.verification-lockout` | `PT24H` |
| `BANK_ACCOUNT_ENCRYPTION_KEY` | environment only |

### Error codes

`PAYOUT_BELOW_MINIMUM`, `PAYOUT_WINDOW_NOT_OPEN`, `PAYOUT_STATE_INVALID`,
`BANK_ACCOUNT_UNKNOWN`, `BANK_ACCOUNT_NOT_VERIFIED` — rows of
[ET-PLT-005 §4](../../_platform/005-error-contract/) introduced by this spec.
`ESCROW_INSUFFICIENT_BALANCE` and `ACTOR_NOT_PERMITTED` are raised here and owned
elsewhere.

## 5. Tasks

- [ ] **T1 · The eligibility method and its layer-1 tests**
  - requirements: R1
  - files: `backend/booking-service/.../domain/PayoutEligibility.java`
  - verify: each condition fails independently; the failing condition is named
  - parallel-safe: no
  - depends: —

- [ ] **T2 · Bank accounts: encryption at rest, masking, the default index**
  - requirements: R4, R5
  - files: `backend/booking-service/.../domain/model/BankAccount.java`
  - verify: no full account number in any output; one default per organization
  - parallel-safe: yes
  - depends: —

- [ ] **T3 · Micro-deposit verification, its lockout and its journal entry**
  - requirements: R4
  - files: `backend/booking-service/.../service/impl/BankAccountServiceImpl.java`
  - verify: three mismatches lock for 24 h; the deposit is expensed to `5050`
  - parallel-safe: yes
  - depends: T2

- [ ] **T4 · The request, the state machine and the one-open-request rule**
  - requirements: R2, R8
  - files: `backend/booking-service/.../domain/`, `.../service/impl/PayoutServiceImpl.java`
  - verify: all `(status, action)` pairs; a second `PENDING` request refuses
  - parallel-safe: no
  - depends: T1

- [ ] **T5 · Approval: re-check, recompute, and the requester ≠ approver rule**
  - requirements: R1, R3
  - files: `backend/booking-service/.../service/impl/PayoutServiceImpl.java`
  - verify: an actor holding both roles cannot approve their own request; a dispute opened after request refuses
  - parallel-safe: yes
  - depends: T4

- [ ] **T6 · The payout workflow: debit-then-transfer, with compensation**
  - requirements: R6
  - files: `backend/booking-service/.../workflow/payout/PayoutWorkflowImpl.java`, `.../service/PayoutSettlementService.java`
  - verify: a forced failure restores the escrow exactly; a worker killed between debit and transfer resolves either way; the history replays
  - parallel-safe: no — the highest-risk write in the platform
  - depends: T5

- [ ] **T7 · Retry, the bad-details fast-fail and the escalation**
  - requirements: R7
  - files: `backend/booking-service/.../workflow/payout/PayoutRules.java`, `.../workflow/payout/PayoutWorkflowImpl.java`
  - verify: bad account details fail immediately without retry and mark the account
  - parallel-safe: yes
  - depends: T6

- [ ] **T8 · The subgraph half; account fields absent from the type entirely**
  - requirements: R5
  - files: `backend/booking-service/src/main/resources/graphql/schema.graphqls`
  - verify: `accountNumber` appears in no schema; `compose-supergraph.sh --static`
  - parallel-safe: no — shared SDL across booking's specs
  - depends: T2

## 6. Out of scope

| Capability | Spec |
|---|---|
| The escrow account, the journal and the chart | [ET-FIN-001](../001-escrow-and-ledger/) |
| Commission recognition, which precedes payout eligibility | [ET-FIN-002](../002-commission/) |
| Refunds and chargebacks, which affect the balance being paid out | [ET-FIN-004](../004-refunds-and-chargebacks/) |
| Reconciling settled payouts against the bank | [ET-FIN-005](../005-reconciliation/) |
| The provider port the transfer goes through | [ET-PAY-001](../../payment/001-payment-intents-and-providers/) |
| Operator recovery of a stuck payout | [ET-ADM-003](../../admin/003-transaction-recovery/) |
| The `payout:request` and `payout:approve` permissions | [ET-ORG-003](../../organization/003-permission-resolution/) |
| Notifying the organizer at each transition | [ET-NTF-002](../../notification/002-lifecycle-triggers/) |

Deliberately never in scope: **partial payouts** (a fee structure and an apportionment
argument for a capability nobody asked for), **self-approval at any threshold** (an
unmonitored withdrawal endpoint), and **payouts to unverified accounts** (the warning is
dismissed and the money goes to a mistyped account).
