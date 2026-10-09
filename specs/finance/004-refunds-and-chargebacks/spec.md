# ET-FIN-004 · Refunds, cancellation refunds and chargebacks

> **Amended 2026-10-05 (partial refunds, buyer withdrawal, admin-created requests).**
> `refundTicket(ticketNumber, reason, amount?)` may refund part of a seat: the amount is a positive sum of ngwee (2 dp) not above what remains
> (`price - refundedAmount`), else `REFUND_NOT_PERMITTED` / `COMMAND_NOT_WELL_FORMED`; a seat is `REFUNDED` only when nothing remains. The commission and
> escrow share the amount proportionally (`RefundSplit`; the two always add up to the refund) and the booking's `refundedAmount` rises, so the booking reads
> `PARTIALLY_REFUNDED` then `REFUNDED`. It reuses the existing refund workflow (idempotent per ticket and amount). A buyer may
> `cancelRefundRequest` only while it is still pending and only their own; an admin may `createAdminRefundRequest` for any ticket;
> `refundRequestsByOrganizer` lists only the caller's organization. A ticket received by transfer is not refundable by the new holder (the
> organizer or platform may refund it). Tests: `BookingOperationsRulesTest` (L1).
>
> **Verified 2026-10-05 (integration tests `RefundMoneyTest`, `RefundAccessTest`: real workflow, services, ledger and MongoDB).** A ticket has at most one *open* refund request: asking again
> for the same sum returns it, asking for a different sum is `REFUND_NOT_PERMITTED` (withdraw the open one first); a request that has `COMPLETED` no longer stands in the way, so a part refund
> can be followed by another until the price is reached (previously the completed one was handed back and the second part refund never happened). Eight simultaneous requests make one request and one
> provider refund. A refund completing is applied once (one escrow debit, one ledger entry, the ticket credited once) however often it is told to complete, and every entry balances. The ledger
> account `2010-<event>` is opened by the first posting that names it (nothing created it before, so a sale or refund for an event with no such account failed). A refund goes to whoever paid; the
> organizer's team refunds only its own events (`TICKET_UNKNOWN` otherwise).

> **Conformance** · V3 §9.2 event cancellation · V3 §14 refund processing fees · US Part III §16

> **Amendment, 2026-09-19 — the refund policies are platform configuration.** Decided by the
> product owner: the platform defines the refund policies and their schedules as a kind of
> system configuration ([ET-ADM-002 §4 *Refund policies*](../../admin/002-platform-configuration/)),
> and the organizer picks one of the active policies for each event. The four policies below
> are the platform's starting set, not a code enum. R1 reads the schedule from
> `catalog_platform_configuration`; the event records the policy code and the version in force
> when it was published, and a buyer is refunded under that version.

## 1. Capability

Money goes back for three reasons, and they are not the same reason. A buyer changes their
mind — that is a refund, and the platform's policy governs how much comes back and who
pays the processing fee. An organizer cancels an event — that is a full refund of everything
to everyone, immediately, with the organizer bearing the cost, because the buyer did
nothing wrong. And a buyer disputes a charge with their provider — that is a chargeback,
which is not a refund at all: it is money being taken back without asking, and the platform
finds out afterwards.

This spec handles all three, and it keeps them distinct because the accounting differs at
every step. It declares the refund policy and the window it applies over; the fee-bearer
rules that decide whether the buyer, the platform or the organizer absorbs the provider's
cost; the mass-refund path a cancellation triggers, with its closing arithmetic; and the
chargeback lifecycle, including the case that hurts — a chargeback against an event whose
money has already been paid out.

The invariant it exists to protect is that **every refund is fully funded and fully
accounted**. What the buyer receives, what the escrow gives up, what commission is
cancelled or clawed back, and what fee somebody bears must sum exactly. A refund that
leaves the ledger unbalanced is `JOURNAL_UNBALANCED`, which is `INTERNAL`, which pages
somebody.

## 2. Design decisions

**Three distinct paths, never one generic "reversal".** Refund, cancellation refund and
chargeback differ in who initiates, what percentage comes back, who bears the fee, whether
approval is required and what happens to commission. Collapsing them produces a single
function with four flags, and the flags get the wrong combination.

**The refund policy is a property of the event, chosen by the organizer from the set the
platform defines.** The platform starts with `FLEXIBLE`, `MODERATE`, `STRICT` and
`NO_REFUNDS` — each a schedule of percentage against time-before-event — and administrators
maintain the set as platform configuration (ET-ADM-002). Organizers never define their own. A free-text policy is unenforceable; a per-event percentage
field invites a hundred variants nobody can display to a buyer in a sentence.

**The buyer bears the refund processing fee by default; the organizer bears it on a
cancellation (V3 §14).** `RefundFeeBearer.CUSTOMER` is the industry default and is
configured platform-wide. On an event cancellation the policy switches to
`ORGANIZER_PAYS` — the buyer gets 100% of what they paid, and the provider's fee comes out
of the escrow. A buyer who is refunded because somebody else cancelled must not be out of
pocket.

**Commission follows the two-stage model without special handling
([ET-FIN-002](../002-commission/) R6).** A refund before recognition cancels the pending
entry; after recognition it claws back. This spec does not decide which — it calls the
commission service, which reads the record's status.

**A cancellation refunds everything, immediately, with no approval step.** There is nothing
to review: the event is not happening. The mass refund is a job, batched, resumable and
idempotent, and it closes the escrow when the last one settles. The arithmetic
`escrowDebit + commissionCancelled + clawedBack == totalRefunded` is asserted per event
([ET-CAT-001](../../catalog/001-event-lifecycle/) R7).

**A chargeback is recorded when the platform learns of it, and it is not reversible by the
platform.** The provider has already taken the money. What the platform does is account for
it, decide who absorbs it, and — if it wishes to contest — supply evidence and wait. The
escrow is debited if funds remain; if they do not, the loss is the platform's and lands in
`5030 Chargeback Losses`.

**A chargeback after payout is the platform's loss and is recorded as such.** The
organizer's money has left. The platform will not claw back from a settled bank transfer,
so the loss is booked against `5030` and the organizer's chargeback rate becomes a risk
signal that can extend their hold period. Pretending it can be recovered produces a
receivable nobody will ever collect.

**Only an event cancellation's refund approves itself; every other refund is decided by a
person** (ROADMAP D-26). A cancellation refunds everyone at 100% under R6, so there is nothing to
judge. Every other request, whatever its size, waits for finance; one nobody decides is escalated
after two days and again after five, and is never approved by waiting.

**A refund is idempotent on the ticket.** One ticket, one refund. The unique index on
`ticketId` where the refund is not rejected is what enforces it, because a double refund is
money that does not come back.

**Rejected alternatives**

- *One generic reversal path with flags.* Four flags, and the wrong combination is chosen.
- *Free-text or per-event percentage refund policies.* Unenforceable, and undisplayable to a buyer in a sentence.
- *Making the buyer bear the fee on an organizer's cancellation.* Penalises the one party who did nothing wrong.
- *Approving every refund.* A queue of K50 decisions that nobody reads carefully by item fifty.
- *Clawing back a settled payout after a chargeback.* Creates a receivable against an organizer who has spent the money.
- *Treating a chargeback as a refund.* It is not requested, not approvable and not declinable — the money is already gone.
- *Partial refunds computed on the gross including commission.* Refunds the buyer money the platform recognised, or short-changes them, depending on the direction of the mistake.

## 3. Requirements

### ET-FIN-004-R1 · The refund policy is platform-defined, and each event carries one

THE SYSTEM SHALL apply to every event one of the refund policies the platform defines, as
chosen by its organizer, and SHALL compute the refundable percentage from that policy's
schedule at the version the event was published under.

**Acceptance**
- [ ] The policies are read from the platform settings (ET-ADM-002), seeded with `FLEXIBLE`, `MODERATE`, `STRICT`, `NO_REFUNDS` and the §4 schedules
- [ ] An organizer can pick only an active policy; an event cannot be published without one
- [ ] The event records the policy code and version; a later change to the policy does not change the refund of a ticket already sold
- [ ] The percentage is computed by one method over values, testable at layer 1
- [ ] A request outside every window is refused with `REFUND_WINDOW_CLOSED` carrying `closedAt`
- [ ] `NO_REFUNDS` refuses every buyer-initiated request with `REFUND_NOT_PERMITTED` carrying `reason`
- [ ] The applicable percentage is shown to the buyer before they request
- [ ] Boundary tests assert the percentage either side of every schedule step

### ET-FIN-004-R2 · The fee bearer is decided by the reason, not by the amount

WHEN a refund is calculated, THE SYSTEM SHALL determine who bears the provider's fee from
the §4 rules.

**Acceptance**
- [ ] A buyer-initiated refund uses `finance.refund.fee-bearer` (`CUSTOMER`) — the buyer receives the refund less the fee
- [ ] An event cancellation uses `ORGANIZER_PAYS` — the buyer receives 100% of what they paid and the escrow bears the fee
- [ ] A reschedule refund uses `ORGANIZER_PAYS` — same reasoning
- [ ] A platform-error refund uses `PLATFORM` — `5020 Refund Processing Fees` bears it
- [ ] The calculation returns `amountToCustomer`, `escrowDeduction`, `commissionToCancel` and `platformCost`, and they reconcile exactly
- [ ] A test drives every (reason × bearer) combination and asserts the four figures sum correctly
- [ ] A refund below `finance.refund.minimum` (K10) is refused — the fee would exceed the refund

### ET-FIN-004-R3 · Every refund is one balanced set of entries

WHEN a refund settles, THE SYSTEM SHALL write balanced journal entries covering the escrow,
the commission and the fee.

**Acceptance**
- [ ] The entries are those of §4, and `Σ debits == Σ credits` for each
- [ ] The commission side calls [ET-FIN-002](../002-commission/) R6 — this spec does not decide cancel versus clawback
- [ ] `amountToCustomer + platformCost + retainedByOrganizer == grossAmount` for every refund, at K0.01
- [ ] `Ledger.assertBalanced()` holds after every refund
- [ ] A partial refund apportions escrow and commission by the refunded fraction, rounded once
- [ ] A test refunds at 100%, 50% and 0% and asserts the ledger balances in each
- [ ] The ticket moves `REFUND_PENDING` → `REFUNDED` only when the provider confirms

### ET-FIN-004-R4 · One ticket, one refund, enforced at the database

THE SYSTEM SHALL permit at most one non-rejected refund per ticket.

**Acceptance**
- [ ] `booking_refund_requests` carries a partial unique index on `ticketId` where `status` is not `REJECTED`
- [ ] A second request is refused with `REFUND_ALREADY_ISSUED` carrying `refundRequestId`
- [ ] Two parallel requests for one ticket produce exactly one
- [ ] A rejected refund may be re-requested
- [ ] Every money-moving refund mutation carries an `idempotencyKey`
- [ ] The PawaPay refund id is minted once and stored on the request before PawaPay is called; every retry — after a crash, a timeout or an unreachable provider — sends that same id, so PawaPay drops a repeat rather than paying twice
- [ ] The escrow debit for a refund is taken once per refund request, however many times the refund is processed
- [ ] An unreachable provider (open circuit breaker) is retried with the same id, never recorded as a refusal
- [ ] A test processes one refund concurrently and after a lost answer, and asserts one refund id and one escrow debit (`RefundProviderRetryTest`)

### ET-FIN-004-R5 · Only a cancellation's refund is automatic; every other refund is decided by a person

IF a refund arises from an event cancellation (R6), THEN THE SYSTEM SHALL approve it
automatically; otherwise it SHALL require finance approval, and WHILE a request waits it SHALL
escalate it to finance after `P2D` and again after `P5D`.

**Acceptance**
- [ ] A cancellation's refund moves straight to `APPROVED`, recorded as automatic with the cancellation reason
- [ ] Every other request stays `PENDING` until a person approves or rejects it, whatever its amount
- [ ] A request still `PENDING` after each of `finance.refund.review-escalations` (`P2D`, `P5D`) alerts the finance lead by email and WhatsApp, with a copy to the finance channel, once, and records `REVIEW_ESCALATED_{n}` in its history; it is never approved by waiting
- [ ] `approveRefund` requires `FINANCE`, `ticket:refund` and an `idempotencyKey`
- [ ] An out-of-policy approval requires a reason, which is audited
- [ ] `rejectRefund` requires a reason and notifies the buyer
- [ ] A test asserts a K50 buyer refund waits for a person, and an undecided one is escalated at two and five days

### ET-FIN-004-R6 · An event cancellation refunds everyone, resumably

WHEN an event is cancelled, THE SYSTEM SHALL refund every live ticket at 100% and close the
escrow.

**Acceptance**
- [ ] `catalog.EventCancelled` triggers a batched mass refund over every ticket in `ISSUED` or `VALIDATED`
- [ ] Each refund is 100% of `grossAmount` with `ORGANIZER_PAYS` fees; no approval is required
- [ ] The job is resumable — a restart continues rather than re-refunding, asserted by a kill-and-resume test
- [ ] It is idempotent per ticket, guarded by R4's index
- [ ] `escrowDebit + commissionCancelled + clawedBack == totalRefunded` for the whole event, at K0.01
- [ ] The escrow reaches zero and `CLOSED`; a non-zero residual raises `JOURNAL_UNBALANCED` and pages
- [ ] IF the escrow cannot fund every refund — because a payout already settled — THEN the shortfall is booked to `5030` and finance is alerted, and every buyer is still refunded in full
- [ ] A test cancels an event with 500 tickets and asserts 500 refunds, a closed escrow and a balanced ledger

### ET-FIN-004-R7 · A reschedule opens an unconditional refund window

WHEN an event is rescheduled, THE SYSTEM SHALL grant every holder a 100% refund option for
a fixed window, overriding the event's policy.

**Acceptance**
- [ ] `catalog.EventRescheduled` opens a window of `catalog.reschedule.refund-window` (P7D) from the event
- [ ] Within it, any holder may refund at 100% with `ORGANIZER_PAYS` fees, regardless of the event's `RefundPolicy` — including `NO_REFUNDS`
- [ ] The window is recorded per ticket so a later reschedule extends rather than replaces it ([ET-CAT-001](../../catalog/001-event-lifecycle/) R5)
- [ ] After it closes, the event's normal policy resumes
- [ ] Every holder is notified that the option exists and when it ends
- [ ] A test reschedules a `NO_REFUNDS` event and asserts a holder can refund at 100%

### ET-FIN-004-R8 · A chargeback is recorded, funded where possible, and never invented back

WHEN a chargeback is received, THE SYSTEM SHALL record it, debit the escrow if funds remain,
and otherwise book the loss.

**Acceptance**
- [ ] A provider chargeback notification creates a `booking_chargebacks` row with the provider's reference, reason and amount
- [ ] `ChargebackStatus` is `RECEIVED`, `UNDER_REVIEW`, `CONTESTED`, `ACCEPTED`, `REVERSED`
- [ ] The escrow is debited if it holds sufficient funds; the ticket moves to `REFUNDED`
- [ ] IF the escrow is insufficient — typically because a payout settled — THEN the shortfall is booked to `5030 Chargeback Losses` and finance is alerted
- [ ] No path claws back from a settled payout
- [ ] `openDisputeCount` on the escrow is incremented on receipt and decremented on resolution, which is what blocks payout eligibility ([ET-FIN-003](../003-payouts-and-settlement/) R1)
- [ ] Contesting supplies evidence and awaits the provider; a `REVERSED` chargeback re-credits the escrow with a balanced entry
- [ ] A chargeback with no decision 24 hours before its response deadline alerts the finance lead by email and WhatsApp, with a copy to the finance channel, once, and records `escalatedAt`; at the deadline it is accepted (ROADMAP D-25)
- [ ] The chargeback rate per organization is a metric and may extend that organization's hold period

## 4. Model

> **Reconciliation note, 2026-09-01 — `myRefundRequests` · **built**, not renamed.**
>
> **Resolved 2026-09-01.** Built as `myRefundRequests(pagination)` — **subject-scoped, not
> tenant-scoped**. §4 declares it `AUTHENTICATED`, so the question is "my refunds" for a buyer,
> and a buyer is not a tenant: the id comes from the token through `CallerScope.subject()` and
> there is deliberately **no argument to override it**. That is the difference from
> `refundRequestsByBuyer(buyerId)`, which stands and is still the admin view.
>
> An unauthenticated caller gets `ACTOR_NOT_AUTHENTICATED`, not a disguised `*_UNKNOWN`: nothing
> was supplied, so there is no id to enumerate and nothing to hide — and answering "unknown"
> would tell a signed-out user their own refunds do not exist. Covered by `CallerScopedReadTest`.
>
> `refundRequestsByBuyer(buyerId)` is caller-supplied; §4 asks for the authenticated buyer's own.
> Same reasoning as `myPayoutRequests` in [ET-FIN-003](../../finance/003-payouts-and-settlement/spec.md).
> Deferred to F-001's read-path conversion.

> **Amended 2026-09-01 under [D-19](../../ROADMAP.md).** `requestRefund` → `createUserRefundRequest` — §4 says CUSTOMER, and the shipped mutation is `isAuthenticated()`; `createAdminRefundRequest` is the admin path and takes `bypassApproval`.

> **Amended 2026-09-01 under [D-19](../../ROADMAP.md).** 2 operation names below adopt the
> shipped names: `approveRefund` → `approveRefundRequest`, `rejectRefund` → `rejectRefundRequest`. D-19 rules that where the schema and §4 disagree on an operation's
> *name*, the schema stands and §4 adopts it.
>
> **Only the names were adopted.** Argument lists and return types were not re-verified against
> the schema, so a row here can now name a real operation and still describe it wrongly. That
> gap is unmeasured, and calling it verified would be the same mistake as counting a file's
> existence as proof it runs.

### Refund policies

Percentage refundable, by hours before the event starts.

| Policy | > 168 h (7 d) | 168–48 h | 48–24 h | < 24 h |
|---|---|---|---|---|
| `FLEXIBLE` | 100% | 100% | 50% | 0% |
| `MODERATE` | 100% | 50% | 0% | 0% |
| `STRICT` | 50% | 0% | 0% | 0% |
| `NO_REFUNDS` | 0% | 0% | 0% | 0% |

The platform's starting set — administrators maintain it in the platform settings
(ET-ADM-002). Chosen by the organizer at event creation from the active policies, displayed to
the buyer before purchase, and overridden to 100% by cancellation (R6) and by a reschedule
window (R7).

### Fee bearers

| Reason | Bearer | Buyer receives | Escrow gives up | Platform bears |
|---|---|---|---|---|
| buyer request | `CUSTOMER` | refundable − fee | net portion | — |
| event cancelled | `ORGANIZER_PAYS` | **100%** | net portion + fee | — |
| reschedule window | `ORGANIZER_PAYS` | **100%** | net portion + fee | — |
| platform error | `PLATFORM` | 100% | net portion | fee |

### The calculation

```
refundable        = grossAmount × policyPercentage
providerFee       = provider.refundFee(refundable)
escrowPortion     = netAmount × policyPercentage
commissionPortion = commissionAmount × policyPercentage

CUSTOMER        → amountToCustomer = refundable − providerFee
                  escrowDeduction  = escrowPortion
                  platformCost     = 0
ORGANIZER_PAYS  → amountToCustomer = refundable
                  escrowDeduction  = escrowPortion + providerFee
                  platformCost     = 0
PLATFORM        → amountToCustomer = refundable
                  escrowDeduction  = escrowPortion
                  platformCost     = providerFee
```

One rounding, `HALF_UP`, scale 2, applied to each output. `commissionToCancel` is
`commissionPortion` and is handed to [ET-FIN-002](../002-commission/).

### Journal entries

**Buyer refund, `CUSTOMER` bears the fee**

| Account | Direction | Amount |
|---|---|---|
| `2010` Event Escrow | debit | escrowPortion |
| `2020` Pending Commission *(or `4010` if recognised)* | debit | commissionPortion |
| `2030` Refunds Payable | credit | amountToCustomer |
| `1010` Provider Settlement Receivable | credit | providerFee |

**Cancellation refund, `ORGANIZER_PAYS`**

| Account | Direction | Amount |
|---|---|---|
| `2010` Event Escrow | debit | escrowPortion + providerFee |
| `2020` / `4010` | debit | commissionPortion |
| `2030` Refunds Payable | credit | grossAmount |

**Settlement of a refund**

| Account | Direction | Amount |
|---|---|---|
| `2030` Refunds Payable | debit | amountToCustomer |
| `1020` Platform Bank | credit | amountToCustomer |

**Chargeback with insufficient escrow**

| Account | Direction | Amount |
|---|---|---|
| `2010` Event Escrow | debit | whatever remains |
| `5030` Chargeback Losses | debit | the shortfall |
| `1020` Platform Bank | credit | chargeback amount |

Every one balances.

### Documents

`booking_refund_requests`

| Field | Type | Notes |
|---|---|---|
| `_id`, `ticketId`, `eventId`, `organizationId` | `String` | `ticketId` **partial unique** where not `REJECTED` |
| `requestedById`, `reason`, `reasonCategory` | | |
| `refundReason` | `RefundReason` | `BUYER_REQUEST`, `EVENT_CANCELLED`, `EVENT_RESCHEDULED`, `PLATFORM_ERROR` |
| `policyPercentage` | `BigDecimal` | the snapshot |
| `grossAmount`, `refundableAmount`, `providerFee` | `BigDecimal` | |
| `amountToCustomer`, `escrowDeduction`, `commissionToCancel`, `platformCost` | `BigDecimal` | the four outputs |
| `feeBearer` | `RefundFeeBearer` | |
| `status` | `RefundStatus` | `PENDING_REVIEW`, `APPROVED`, `PROCESSING`, `COMPLETED`, `REJECTED`, `FAILED` |
| `autoApproved` | `boolean` | R5's reporting distinction |
| `approvedById`, `approvedAt`, `rejectionReason` | | |
| `providerReference`, `settledAt` | | |
| `journalEntryIds` | `List<String>` | |
| `idempotencyKey` | `String` | |
| `version` | `Long` | |

`booking_chargebacks`

| Field | Type | Notes |
|---|---|---|
| `_id`, `ticketId`, `eventId`, `organizationId` | `String` | |
| `providerReference`, `providerReason` | `String` | |
| `amount`, `currency` | | |
| `status` | `ChargebackStatus` | the five |
| `escrowDebited`, `platformLoss` | `BigDecimal` | the split |
| `evidenceSubmittedAt`, `resolvedAt`, `resolution` | | |
| `receivedAt` | `Instant` | |

### Refund state machine

| From | Action | To |
|---|---|---|
| — | `requestRefund` | `PENDING_REVIEW` or `APPROVED` (auto) |
| `PENDING_REVIEW` | `approveRefund` | `APPROVED` |
| `PENDING_REVIEW` | `rejectRefund` | `REJECTED` |
| `APPROVED` | initiate with the provider | `PROCESSING` |
| `PROCESSING` | provider confirms | `COMPLETED` |
| `PROCESSING` | provider fails past retries | `FAILED` |
| `FAILED` | `retryRefund` | `PROCESSING` |

`COMPLETED` and `REJECTED` are terminal. The ticket becomes `REFUNDED` only at `COMPLETED`.

### GraphQL

Subgraph `booking`.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `refundQuote(ticketId)` | query | `AUTHENTICATED` | `RefundQuote!` |
| `refundRequest(id)` | query | `AUTHENTICATED` | `RefundRequest` |
| `myRefundRequests(page)` | query | `AUTHENTICATED` | `RefundRequestPage!` |
| `refundRequests(status, page)` | query | `FINANCE` | `RefundRequestPage!` `@tag(name: "admin")` |
| `chargebacks(status, page)` | query | `FINANCE` | `ChargebackPage!` `@tag(name: "admin")` |
| `createUserRefundRequest(input)` | mutation | `CUSTOMER` | `RefundRequest!` |
| `approveRefundRequest(input)` | mutation | `FINANCE` | `RefundRequest!` `@tag(name: "admin")` |
| `rejectRefundRequest(id, reason)` | mutation | `FINANCE` | `RefundRequest!` `@tag(name: "admin")` |
| `retryRefund(id)` | mutation | `FINANCE` | `RefundRequest!` `@tag(name: "admin")` |
| `contestChargeback(input)` | mutation | `FINANCE` | `Chargeback!` `@tag(name: "admin")` |
| `acceptChargeback(id, reason)` | mutation | `FINANCE` | `Chargeback!` `@tag(name: "admin")` |

`RefundQuote` shows the buyer the percentage, the fee and what they will actually receive,
**before** they request — which is the number that determines whether they complain.

### Consumed events

| Wire name | Effect |
|---|---|
| `catalog.EventCancelled` | mass refund at 100%, `ORGANIZER_PAYS`, then close the escrow |
| `catalog.EventRescheduled` | open the 7-day unconditional window per ticket |

### Published events

| Tier | Name | Consumers |
|---|---|---|
| bus | `booking.RefundCompleted` v1 | catalog → restore counters; identity → notify |

Chargeback alerting and risk flags are activities of `ChargebackWorkflow`, not in-memory events.

### Configuration

| Property | Value |
|---|---|
| `finance.refund.fee-bearer` | `CUSTOMER` |
| `finance.refund.minimum` | `K10.00` |
| `finance.refund.review-escalations` | `P2D`, `P5D` |
| `finance.chargeback.escalate-before-deadline` | `PT24H` |
| `finance.refund.max-attempts` | 3 |
| `finance.cancellation.batch-size` | 200 |
| `catalog.reschedule.refund-window` | `P7D` |

### Error codes

`REFUND_NOT_PERMITTED`, `REFUND_WINDOW_CLOSED`, `REFUND_ALREADY_ISSUED`,
`CHARGEBACK_STATE_INVALID` — rows of
[ET-PLT-005 §4](../../_platform/005-error-contract/) introduced by this spec.

## 5. Tasks

- [ ] **T1 · The policy schedule read from the platform settings, and the boundary tests**
  - requirements: R1
  - files: `backend/booking-service/.../domain/RefundSchedule.java`, a shared-library reader over `catalog_platform_configuration`
  - verify: the percentage either side of every schedule step; a policy change leaves a sold ticket's refund unchanged
  - parallel-safe: no
  - depends: —

- [ ] **T2 · The calculation and its four outputs, per fee bearer**
  - requirements: R2
  - files: `backend/booking-service/.../domain/RefundCalculation.java`
  - verify: every (reason × bearer) combination sums correctly; below-minimum refuses
  - parallel-safe: yes
  - depends: T1

- [ ] **T3 · The request, its partial unique index and the approval rule**
  - requirements: R4, R5
  - files: `backend/booking-service/.../domain/model/RefundRequest.java`, `.../service/impl/`
  - verify: two parallel requests yield one; only a cancellation's refund approves itself, and a waiting one escalates at P2D and P5D
  - parallel-safe: no
  - depends: T2

- [ ] **T4 · The journal entries per bearer, and the settlement entry**
  - requirements: R3
  - files: `backend/booking-service/.../service/impl/RefundServiceImpl.java`
  - verify: 100%, 50% and 0% refunds each balance; the ticket moves only on confirmation
  - parallel-safe: no
  - depends: T3

- [ ] **T5 · The mass-refund job: batched, resumable, idempotent**
  - requirements: R6
  - files: `backend/booking-service/.../service/impl/EventCancellationRefundJob.java`
  - verify: 500 tickets, a kill-and-resume, a closed escrow, a balanced ledger
  - parallel-safe: no
  - depends: T4

- [ ] **T6 · The reschedule window, per ticket, extending on a second reschedule**
  - requirements: R7
  - files: `backend/booking-service/.../event/listener/EventRescheduleListener.java`
  - verify: a `NO_REFUNDS` event refunds at 100% inside the window
  - parallel-safe: yes
  - depends: T4

- [ ] **T7 · Chargebacks: the lifecycle, the loss split, the dispute counter**
  - requirements: R8
  - files: `backend/booking-service/.../service/impl/ChargebackServiceImpl.java`
  - verify: an insufficient escrow books to `5030`; no path claws back a settled payout; `openDisputeCount` moves
  - parallel-safe: yes
  - depends: T4

- [ ] **T8 · `RefundQuote` shown before the request**
  - requirements: R1, R2
  - files: `backend/booking-service/.../web/graphql/query/RefundQueryResolver.java`
  - verify: the quote and the executed refund produce identical figures
  - parallel-safe: yes
  - depends: T2

- [ ] **T9 · The subgraph half; finance operations `@tag`ged**
  - requirements: R1–R8
  - files: `backend/booking-service/src/main/resources/graphql/schema.graphqls`
  - verify: `compose-supergraph.sh --static`
  - parallel-safe: no — shared SDL across booking's specs
  - depends: T7

## 6. Out of scope

| Capability | Spec |
|---|---|
| The accounts, journal and escrow this spec posts into | [ET-FIN-001](../001-escrow-and-ledger/) |
| Whether a commission is cancelled or clawed back | [ET-FIN-002](../002-commission/) |
| Payouts, whose settlement causes the insufficient-escrow case | [ET-FIN-003](../003-payouts-and-settlement/) |
| Reconciling refunds against the provider | [ET-FIN-005](../005-reconciliation/) |
| The cancellation and reschedule transitions that trigger this | [ET-CAT-001](../../catalog/001-event-lifecycle/) |
| The provider refund call itself | [ET-PAY-001](../../payment/001-payment-intents-and-providers/) |
| Notifying buyers at each step | [ET-NTF-002](../../notification/002-lifecycle-triggers/) |
| Operator recovery of a failed refund | [ET-ADM-003](../../admin/003-transaction-recovery/) |

Deliberately never in scope: **one generic reversal path** (four flags and the wrong
combination), **clawing back a settled payout after a chargeback** (a receivable against
somebody who has spent the money), and **making the buyer bear the fee when the organizer
cancels** (it penalises the only party who did nothing wrong).
