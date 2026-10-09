# ET-FIN-002 · Commission — rate resolution and two-stage recognition

> **Conformance** · V3 §2.1 revenue streams · V3 §2.2 commission structure · V3 §10.3 two-stage commission model

## 1. Capability

Commission is the platform's revenue. It is a percentage of every ticket sold, and the two
questions it raises are *how much* and *when is it ours* — and the second is far more
interesting than the first.

The naive answer is that commission is earned at purchase. It is not. A ticket sold for an
event in three months is a ticket that may be refunded, for an event that may be cancelled,
by an organizer who may be suspended. Booking that K15 as revenue in March means reversing
it in May, and a platform whose reported revenue is routinely reversed cannot be reported
on at all. So commission is **collected** at purchase into a liability account and
**recognised** as revenue only when the event has happened and the refund window has
closed (D-04).

That single decision is what makes refunds arithmetically simple. A refund before
recognition cancels a pending entry — no clawback, no negative revenue, no adjustment to a
period that has already been reported. Only the rare refund *after* recognition needs a
clawback, and it is rare precisely because recognition waits.

This spec declares the rate card and how a rate is resolved for a given event, the exact
expression that computes the commission amount, the recognition step that moves money from
pending to earned, and the clawback path for the cases that get through. It does not move
money itself — every movement is a journal entry through
[ET-FIN-001](../001-escrow-and-ledger/).

## 2. Design decisions

**Two stages: collected at purchase, recognised at completion plus hold (D-04).** The
`PENDING_COMMISSION` account (`2020`) is a liability — money the platform holds but has not
earned. `EARNED_REVENUE` (`4010`) is revenue. The move between them is the recognition
event, and it happens once the event is over and the hold period has elapsed. This is why
[ET-FIN-001](../001-escrow-and-ledger/) declares two accounts rather than one.

**The rate is resolved once, at purchase, and written onto the ticket.** Rates change;
tiers change; an organizer is promoted to a better rate. What a ticket owes was fixed when
it was sold, so `commissionRate` and `commissionAmount` are snapshotted on the ticket and
never recomputed. Recognition reads the snapshot.

**Four tiers, resolved by a declared precedence.** Charity 2%, high-volume 3%, premium
organizer 4%, standard 5%. An event can qualify for more than one, so the precedence is
explicit and the **lowest applicable rate wins** — a charity event run by a premium
organizer pays 2%, not an average. Precedence in a table beats a chain of `if`s that three
people have each extended.

**High-volume and premium-organizer status are evaluated at purchase, from counters the
platform already maintains.** Not from a nightly job that grants a badge. `soldQuantity`
for the event and completed-event count for the organization are both already available,
and evaluating at purchase means the rate a buyer's ticket carries reflects the truth at
the moment of sale.

**Commission is computed on the discounted total, not the list price.** A promotion reduces
what the organizer receives and what the platform takes
([ET-CAT-002](../../catalog/002-ticket-tiers-and-inventory/) §4). The platform does not
charge commission on money nobody paid.

**One rounding, at the ticket line, `HALF_UP` to two places.** Commission is computed per
ticket, not per order and then apportioned. Apportioning a rounded order-level figure across
lines produces a residual that has to go somewhere, and wherever it goes is arbitrary.

**Recognition is a step of the event's finance workflow, and it is idempotent per commission
record.** `booking_commission_records` carries a status; when `EventFinanceWorkflow` makes the
escrow `PAYOUT_ELIGIBLE` — the event completed, the hold elapsed, no dispute open — its
`recogniseCommission` activity moves `PENDING` to `EARNED`, writes one journal entry per batch,
and is safe to run twice.

**Cancellation before recognition is a cancel, not a clawback.** The pending entry is
reversed with a balanced journal entry, `EARNED_REVENUE` is never touched, and no reported
period changes. A refund after recognition is a clawback — a debit to `4010` — and it is
counted as a metric because a rising clawback rate means the hold period is too short.

**Rejected alternatives**

- *Recognising commission at purchase.* Books revenue that is routinely reversed, and makes every refund a prior-period adjustment.
- *A single commission account.* Loses the distinction between money held and money earned, which is the distinction that makes refunds simple.
- *Recomputing the rate at recognition.* A rate change in April silently re-prices tickets sold in February.
- *Averaging or stacking applicable tiers.* Produces a rate no organizer can predict and no invoice can explain.
- *Granting volume status by a nightly job.* The badge lags the sale that earned it.
- *Charging commission on the list price.* Takes a cut of money nobody paid.
- *Computing commission at the order level and apportioning.* Creates a residual with no principled home.
- *A per-organizer negotiated rate field with no tier.* Every rate becomes a support conversation and nothing is reportable.

## 3. Requirements

### ET-FIN-002-R1 · The rate card is closed and the precedence is declared

THE SYSTEM SHALL resolve every commission rate from the §4 card using the declared
precedence.

**Acceptance**
- [ ] `CommissionTier` declares exactly `CHARITY` (2%), `HIGH_VOLUME` (3%), `PREMIUM_ORGANIZER` (4%), `STANDARD` (5%)
- [ ] Resolution evaluates every tier's eligibility and selects the **lowest applicable rate**
- [ ] The resolution is one method over values, testable at layer 1 with no database
- [ ] An event qualifying for charity and premium-organizer resolves to 2%, asserted directly
- [ ] Every rate is a `BigDecimal` from configuration, not a literal in code
- [ ] A rate with no applicable tier is impossible — `STANDARD` always applies
- [ ] Changing a rate in configuration affects only tickets sold afterwards

### ET-FIN-002-R2 · The rate and amount are snapshotted at purchase

WHEN a ticket is issued, THE SYSTEM SHALL compute the commission from the discounted line
total and record both the rate and the amount on the ticket.

**Acceptance**
- [ ] `commissionRate`, `commissionTier`, `commissionAmount` and `netAmount` are written to the ticket at issue
- [ ] `commissionAmount = round(lineTotalAfterDiscount × rate, 2, HALF_UP)`, computed per ticket
- [ ] `netAmount = lineTotal − commissionAmount`, and `netAmount + commissionAmount == lineTotal` exactly
- [ ] A rate change after the sale does not alter any existing ticket, asserted by a test
- [ ] A discount reduces the commission proportionally — commission is never computed on the list price
- [ ] No order-level commission figure is computed and apportioned across lines
- [ ] A test over 1,000 randomised prices, discounts and rates asserts the two identities hold for every line

### ET-FIN-002-R3 · Volume and loyalty status are evaluated at the moment of sale

WHEN commission is resolved, THE SYSTEM SHALL evaluate high-volume and premium-organizer
eligibility from current figures.

**Acceptance**
- [ ] `HIGH_VOLUME` applies when the event's `soldQuantity` is at or above `finance.commission.high-volume-threshold` (1000) at the moment of sale
- [ ] `PREMIUM_ORGANIZER` applies when the organization's completed-event count is at or above `finance.commission.premium-threshold` (10)
- [ ] `CHARITY` applies when the organization's `businessType` is `NGO` and the event carries the charity flag set at approval
- [ ] Neither threshold is granted by a scheduled job or a stored badge
- [ ] The ticket that crosses the volume threshold gets the better rate; the one before it does not — asserted by a boundary test at 999, 1000 and 1001
- [ ] The completed-event count excludes cancelled events

### ET-FIN-002-R4 · Commission is collected as a pending liability, never as revenue

WHEN a purchase confirms, THE SYSTEM SHALL credit `2020 Pending Commission` and SHALL NOT
credit `4010 Earned Revenue`.

**Acceptance**
- [ ] The purchase entry credits `2020` with the commission amount ([ET-FIN-001](../001-escrow-and-ledger/) §4)
- [ ] No code path credits `4010` from a purchase, a confirmation or a webhook — asserted by a test that greps the call graph
- [ ] One `booking_commission_records` row is written per ticket, in the same transaction, with `status = PENDING`
- [ ] The record carries `ticketId`, `eventId`, `organizationId`, `rate`, `tier`, `amount`, `currency`
- [ ] `Ledger.assertBalanced()` holds after every purchase
- [ ] The sum of `PENDING` commission records equals the `2020` balance exactly, asserted by a reconciliation test

### ET-FIN-002-R5 · Recognition happens once, after completion plus the hold

WHILE an event has completed and its hold period has elapsed, THE SYSTEM SHALL move its
pending commission to earned revenue exactly once.

**Acceptance**
- [ ] The event's `EventFinanceWorkflow` runs `recogniseCommission` once the escrow is `PAYOUT_ELIGIBLE`, selecting that event's `PENDING` records; no sweep or lock exists
- [ ] Recognition writes one balanced entry per batch: `debit 2020` / `credit 4010`
- [ ] Records move to `EARNED` with `recognisedAt` in the same transaction as the entry
- [ ] Recognising an already-`EARNED` record is refused with `COMMISSION_ALREADY_RECOGNISED` carrying `recognisedAt`, and the activity skips rather than fails
- [ ] Two concurrent recognitions for one event — a retried activity, or the manual trigger racing the workflow — recognise each record exactly once, asserted under contention
- [ ] A cancelled event's records are never recognised, regardless of elapsed time
- [ ] `Ledger.assertBalanced()` holds after every recognition batch

### ET-FIN-002-R6 · A refund before recognition cancels; after recognition, it claws back

IF a ticket is refunded, THEN THE SYSTEM SHALL cancel its pending commission or claw back
its recognised commission, according to the record's status.

**Acceptance**
- [ ] A `PENDING` record is reversed with `debit 2020` / `credit 1010`, moves to `CANCELLED`, and never touches `4010`
- [ ] An `EARNED` record is clawed back with `debit 4010` / `credit 1010`, and moves to `CLAWED_BACK`
- [ ] `CommissionStatus` is exactly `PENDING`, `EARNED`, `CANCELLED`, `CLAWED_BACK`
- [ ] A partial refund cancels or claws back proportionally, rounded once, `HALF_UP`
- [ ] The clawback rate is a metric; a rising rate is the signal that the hold period is too short
- [ ] Cancelling an already-`CANCELLED` record is a no-op
- [ ] A test refunds one ticket before and one after recognition and asserts both ledgers balance and only the second touches `4010`

### ET-FIN-002-R7 · The rate an organizer will pay is visible before they commit

THE SYSTEM SHALL expose the resolved rate for an event to its organizer.

**Acceptance**
- [ ] `commissionPreview(eventId)` returns the tier, the rate and the reason it applies
- [ ] The preview uses the same resolution method as the sale — there is no second implementation
- [ ] An organizer sees the rate before publishing, and it is shown on the event's financial summary
- [ ] The preview states when a better rate would apply — for example, tickets remaining to reach the volume threshold
- [ ] Historical tickets display the rate they actually carried, not the current one
- [ ] `commissionRecords(eventId)` lets an organizer see, per ticket, what was charged

## 4. Model

### The rate card

Rates are configuration, not literals.

| Tier | Rate | Applies when | Precedence |
|---|---|---|---|
| `CHARITY` | 2% | `businessType = NGO` **and** the event is flagged charity at approval | 1 — lowest rate wins |
| `HIGH_VOLUME` | 3% | the event's `soldQuantity` ≥ 1000 at the moment of sale | 2 |
| `PREMIUM_ORGANIZER` | 4% | the organization's completed-event count ≥ 10 | 3 |
| `STANDARD` | 5% | always | 4 — the floor |

Resolution evaluates all four and takes the **lowest applicable rate**, not the first match.

```java
// one method, layer-1 testable, no database
public CommissionResolution resolve(CommissionContext ctx) {
    return Stream.of(CHARITY, HIGH_VOLUME, PREMIUM_ORGANIZER, STANDARD)
        .filter(t -> t.appliesTo(ctx))
        .min(comparing(CommissionTier::rate))     // lowest rate, not first match
        .map(t -> new CommissionResolution(t, t.rate(), t.reasonFor(ctx)))
        .orElseThrow(() -> new CommissionRateUnknown(ctx.eventId()));   // unreachable
}
```

### The computation

Per ticket line, one rounding, at the end.

```
lineTotal        = tier.price × 1                       (one ticket per document)
discountShare    = order.discountAmount × lineTotal / order.subtotal
lineAfterDiscount= lineTotal − discountShare
commissionAmount = round(lineAfterDiscount × rate, 2, HALF_UP)
netAmount        = lineAfterDiscount − commissionAmount
```

`netAmount + commissionAmount == lineAfterDiscount`, exactly, for every ticket.
The order's discount is apportioned by line value **before** rounding, so the sum of line
commissions is stable and no residual arises.

### The record

`booking_commission_records` — one per ticket.

| Field | Type | Notes |
|---|---|---|
| `_id`, `ticketId` | `String` | `ticketId` unique |
| `eventId`, `organizationId` | `String` | |
| `tier` | `CommissionTier` | the snapshot |
| `rate` | `BigDecimal` | the snapshot |
| `grossAmount`, `commissionAmount`, `netAmount` | `BigDecimal` | |
| `currency` | `String` | |
| `status` | `CommissionStatus` | `PENDING`, `EARNED`, `CANCELLED`, `CLAWED_BACK` |
| `recognisedAt`, `cancelledAt`, `clawedBackAt` | `Instant` | |
| `journalEntryId` | `String` | the entry that placed it |
| `createdAt`, `updatedAt` | `Instant` | |

### Status machine

| From | Trigger | To | Journal |
|---|---|---|---|
| — | purchase confirms | `PENDING` | `credit 2020` (part of the sale entry) |
| `PENDING` | recognition, in `EventFinanceWorkflow` | `EARNED` | `debit 2020` / `credit 4010` |
| `PENDING` | refund or event cancellation | `CANCELLED` | `debit 2020` / `credit 1010` |
| `EARNED` | refund after recognition | `CLAWED_BACK` | `debit 4010` / `credit 1010` |

`EARNED` is not terminal — a late refund still reaches it. `CANCELLED` and `CLAWED_BACK`
are.

### Recognition

```
EventFinanceWorkflow, once the escrow is PAYOUT_ELIGIBLE
  recogniseCommission(eventId)                       — an activity
  select booking_commission_records
    where eventId = :eventId
      and status = PENDING
    limit finance.commission.recognition-batch (500)

  per batch, one transaction:
    one journal entry: debit 2020 Σamount / credit 4010 Σamount
    records → EARNED, recognisedAt = now, journalEntryId = entry
```

The batch is one entry rather than one per record, because a thousand-ticket event would
otherwise produce a thousand entries that all say the same thing on the same day. Each
record still links to the entry that recognised it.

### GraphQL

Subgraph `booking`. Every finance field carries `@tag(name: "admin")` unless it is the
organizer's own.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `commissionPreview(eventId)` | query | `ORGANIZER` | `CommissionPreview!` |
| `commissionRecords(eventId, status, page)` | query | `ORGANIZER` | `CommissionRecordPage!` |
| `commissionSummary(organizationId, from, to)` | query | `ORGANIZER` | `CommissionSummary!` |
| `platformCommissionSummary(from, to)` | query | `FINANCE` | `PlatformCommissionSummary!` `@tag(name: "admin")` |
| `recogniseCommission(eventId)` | mutation | `FINANCE` | `RecognitionResult!` `@tag(name: "admin")` |

`recogniseCommission` is a manual trigger for the same idempotent operation the workflow's activity runs
— it exists so finance can settle an event early with a recorded reason, not so recognition
can be bypassed.

`CommissionPreview` carries `tier`, `rate`, `reason` and `nextTierAt` — the latter being
what R7 requires: how far from a better rate this event is.

### Consumed events

| Wire name | Effect |
|---|---|
| `catalog.EventCompleted` | makes the event's records eligible once the hold elapses |
| `catalog.EventCancelled` | cancels every `PENDING` record; claws back every `EARNED` one |
| `booking.RefundCompleted` | cancels or claws back the refunded ticket's record |

### Configuration

| Property | Value |
|---|---|
| `finance.commission.rate.charity` | `0.02` |
| `finance.commission.rate.high-volume` | `0.03` |
| `finance.commission.rate.premium-organizer` | `0.04` |
| `finance.commission.rate.standard` | `0.05` |
| `finance.commission.high-volume-threshold` | 1000 |
| `finance.commission.premium-threshold` | 10 |
| `finance.commission.recognition-interval` | `PT1H` |
| `finance.commission.recognition-batch` | 500 |

Rates are overridable per organization by [ET-ADM-002](../../admin/002-platform-configuration/)
at approval time ([ET-ORG-001](../../organization/001-organizer-onboarding/) R5); a
per-organization override replaces the resolved rate and is recorded on the ticket like any
other.

### Error codes

`COMMISSION_ALREADY_RECOGNISED`, `COMMISSION_RATE_UNKNOWN` — rows of
[ET-PLT-005 §4](../../_platform/005-error-contract/) introduced by this spec.

## 5. Tasks

- [ ] **T1 · The rate card, the resolution method and its layer-1 tests**
  - requirements: R1, R3
  - files: `backend/booking-service/.../domain/CommissionTier.java`, `.../CommissionResolver.java`
  - verify: lowest applicable rate wins; boundary tests at 999/1000/1001
  - parallel-safe: no
  - depends: —

- [ ] **T2 · The computation and its two identities, over 1,000 randomised cases**
  - requirements: R2
  - files: `backend/booking-service/.../domain/CommissionCalculation.java`
  - verify: `net + commission == lineAfterDiscount` for every case; no order-level apportionment
  - parallel-safe: yes
  - depends: T1

- [ ] **T3 · The record, written with the sale, in the same transaction**
  - requirements: R4
  - files: `backend/booking-service/.../service/impl/CommissionServiceImpl.java`
  - verify: no path credits `4010` from a purchase; `PENDING` sum equals the `2020` balance
  - parallel-safe: no — inside the confirmation transaction
  - depends: T2

- [ ] **T4 · Recognition in the finance workflow, batched, idempotent, contended**
  - requirements: R5
  - files: `backend/booking-service/.../workflow/finance/EventFinanceActivitiesImpl.java`, `.../service/impl/CommissionServiceImpl.java`
  - verify: two concurrent recognitions recognise each record once; a cancelled event never recognises
  - parallel-safe: no
  - depends: T3

- [ ] **T5 · Cancellation and clawback paths, and the clawback metric**
  - requirements: R6
  - files: `backend/booking-service/.../service/impl/CommissionServiceImpl.java`
  - verify: a pre-recognition refund never touches `4010`; a post-recognition one does; both balance
  - parallel-safe: yes
  - depends: T4

- [ ] **T6 · The preview, sharing the resolution method**
  - requirements: R7
  - files: `backend/booking-service/.../web/graphql/query/CommissionQueryResolver.java`
  - verify: the preview and the sale return identical rates for identical inputs
  - parallel-safe: yes
  - depends: T1

- [ ] **T7 · The per-organization override applied at resolution**
  - requirements: R1
  - files: `backend/booking-service/.../domain/CommissionResolver.java`
  - verify: an override replaces the resolved rate and is snapshotted on the ticket
  - parallel-safe: yes
  - depends: T1

- [ ] **T8 · The subgraph half; organizer-scoped versus `@tag(name: "admin")` fields**
  - requirements: R7
  - files: `backend/booking-service/src/main/resources/graphql/schema.graphqls`
  - verify: an organizer sees their own commission and no platform aggregate
  - parallel-safe: no — shared SDL across booking's specs
  - depends: T6

## 6. Out of scope

| Capability | Spec |
|---|---|
| The accounts, journal and escrow this spec posts into | [ET-FIN-001](../001-escrow-and-ledger/) |
| Paying the organizer their net amount | [ET-FIN-003](../003-payouts-and-settlement/) |
| Performing the refund that triggers a cancel or clawback | [ET-FIN-004](../004-refunds-and-chargebacks/) |
| Proving the commission ledger against the bank | [ET-FIN-005](../005-reconciliation/) |
| Where the discount comes from | [ET-CAT-002](../../catalog/002-ticket-tiers-and-inventory/) |
| Setting a per-organization rate at approval | [ET-ORG-001](../../organization/001-organizer-onboarding/), [ET-ADM-002](../../admin/002-platform-configuration/) |
| Revenue dashboards and reporting | [ET-ADM-004](../../admin/004-analytics-and-statistics/) |

Deliberately never in scope: **recognising commission at purchase** (it books revenue that
is routinely reversed), **stacking or averaging applicable tiers** (a rate no organizer can
predict), and **charging commission on the list price** (a cut of money nobody paid).
