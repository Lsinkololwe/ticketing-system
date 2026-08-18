# ET-CAT-001 · Event lifecycle — eight states, approval, publish, reschedule, cancel

> **Conformance** · V3 §5 event state machine · V3 §9 rescheduling and cancellation · US Part III §13–14 organizer stories

## 1. Capability

An event is the thing the platform actually sells. Everything else — tiers, tickets,
escrow, commission, payouts — hangs off one row in `catalog_events` and the state it is in.
That state has to answer several questions at once and answer them consistently: may this
be edited, may it be seen by the public, may a ticket be sold for it, is the money it has
taken releasable, and is it over.

This spec builds that lifecycle. It declares eight states and fourteen transitions, and it
separates two things that are commonly conflated — **approved** and **published**. Approval
is the platform's decision that this event may be sold; publishing is the organizer's
decision that it should be sold *now*. An organizer who has cleared review and wants to
announce on Friday needs somewhere to sit until Friday, and squeezing that into one state
means either publishing early or re-entering review.

It declares what a *material change* is, because that is the rule that keeps approval
meaningful: editing the description of an approved event is fine, and changing its venue,
date or organizer is not — those return it to `DRAFT` for re-approval. Without that rule,
approval is a formality anybody can walk around by editing afterwards.

And it declares the two hardest transitions: reschedule and cancel. Both happen to events
that have already taken money from people who made plans. Rescheduling keeps the tickets
valid and opens an unconditional refund window, because a person who bought a ticket for
March cannot be held to April. Cancellation refunds everyone in full, cancels the pending
commission rather than clawing it back, and closes the escrow — and the arithmetic of that
is asserted here even though the money is moved by
[ET-FIN-004](../../finance/004-refunds-and-chargebacks/).

## 2. Design decisions

**`APPROVED` and `PUBLISHED` are different states, and both are needed.** Approval is the
platform saying *this may be sold*; publishing is the organizer saying *sell it*. The gap
between them is where an announcement is scheduled, a press embargo is honoured, and a
final check is made. This is the opposite call from
[ET-ORG-001](../../organization/001-organizer-onboarding/), where `APPROVED` and `ACTIVE`
collapsed — there they had no behavioural difference, and here they do.

**A material change returns an event to `DRAFT`.** Date, time, venue, organization and
capacity are material; title, description, images and category are not. Approving an event
and then letting the organizer move it to a different venue on a different night makes the
review worthless. The material set is declared in §4 and is checked field by field, so
adding a field forces a decision about which side it is on.

**Unpublishing is possible only while nothing is sold.** Once a ticket exists the event has
made a promise to a person, and the way out of that promise is cancellation with refunds,
not quietly removing the listing. `PUBLISHED → APPROVED` therefore requires zero tickets in
any non-refunded state.

**Rescheduling is a self-transition, not a new state.** The event stays `PUBLISHED`,
tickets stay valid, and the date changes. A `RESCHEDULED` state would have to answer *may I
sell tickets in it*, and the answer is yes — which makes it `PUBLISHED` with a different
date. What rescheduling does produce is a **refund window**: seven days from the
notification, unconditional and at 100%, regardless of the event's normal refund policy.
Somebody who bought for a Saturday in March did not agree to a Tuesday in April.

**Completion is a sweep, not an organizer action.** An event is `COMPLETED` when its
`endsAt` has passed, detected by a scheduled sweep under a lock. Leaving completion to the
organizer would mean an organizer who never presses the button never triggers commission
recognition or the payout hold clock — which is a strong incentive not to press it.

**Cancellation is available until the event completes, and always refunds in full.** No
partial-refund policy applies to a cancellation, because the buyer did nothing wrong. The
escrow is debited, the pending commission is cancelled rather than clawed back (D-04), and
`escrowDebit + commissionCancelled = totalRefunded` is an invariant this spec asserts even
though [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) performs the movements.

**A draft may be deleted; anything that has been approved may not.** `DELETED` is reachable
only from `DRAFT` and `REJECTED`. Once an event has been approved it is part of the
platform's record — somebody reviewed it, and that review is a fact.

**Publishing requires an `ACTIVE` organization, checked at publish time and again by
event.** The gate is [ET-ORG-001](../../organization/001-organizer-onboarding/) R7's stage
matrix, reached over the permission API. An organization suspended after its events are
published has them unpublished by the `identity.OrganizationSuspended` consumer — the check
at publish time is not sufficient on its own, because status changes afterwards.

**Rejected alternatives**

- *One state for approved-and-published.* Forces an organizer to either publish the moment review clears or re-enter review to announce later.
- *Letting any edit stand after approval.* Makes approval a formality that an edit walks around.
- *A `RESCHEDULED` state.* It would have to permit ticket sales, which makes it `PUBLISHED` with a new date.
- *Unpublishing an event with tickets sold.* Removes the listing while the promise to the buyer stands.
- *Organizer-triggered completion.* The organizer's interest is not to trigger it — completion starts the commission clock.
- *Partial refunds on organizer cancellation.* The buyer did nothing wrong.
- *A soft `HIDDEN` flag alongside the status.* Two fields answering *is this visible*, and every discovery query then needs both or it is wrong.

## 3. Requirements

### ET-CAT-001-R1 · Eight states and fourteen transitions, enumerated once

THE SYSTEM SHALL admit exactly the eight states and fourteen transitions of §4, and IF an
action would cause any other transition, THEN THE SYSTEM SHALL refuse it and change
nothing.

**Acceptance**
- [ ] `EventStatus` declares exactly `DRAFT, PENDING_APPROVAL, REJECTED, APPROVED, PUBLISHED, COMPLETED, CANCELLED, DELETED`
- [ ] `EventTransitions.LEGAL` holds exactly 14 rows and equals the §4 table row for row
- [ ] `EventTransitions.next(status, action)` returns `Optional<EventStatus>`, empty for every other pair, and a test drives all 99 `(status, action)` pairs
- [ ] No mutation contains an `EventStatus` literal on the right of an assignment
- [ ] An illegal transition is refused with `EVENT_STATE_INVALID` carrying `currentStatus`, and persists nothing
- [ ] `COMPLETED`, `CANCELLED` and `DELETED` are terminal — they appear as a `to` value and never as a `from`

### ET-CAT-001-R2 · Only an approved organization may create and publish

WHEN an actor creates or publishes an event, THE SYSTEM SHALL verify their organization's
status and their permission, and IF either fails, THEN THE SYSTEM SHALL refuse.

**Acceptance**
- [ ] `createEvent` requires `event:create` and an organization in `PENDING_REVIEW` or later ([ET-ORG-001](../../organization/001-organizer-onboarding/) §4 stage matrix)
- [ ] `publishEvent` requires `event:publish` and an organization in `ACTIVE`; anything else is refused with `ORGANIZER_NOT_APPROVED` carrying `organizationStatus`
- [ ] Both checks resolve through [ET-ORG-003](../../organization/003-permission-resolution/)'s internal API — catalog-service contains no role comparison
- [ ] The creating actor is granted `EVENT_OWNER` on the new event in the same operation
- [ ] `identity.OrganizationSuspended` unpublishes every `PUBLISHED` event of that organization, returning them to `APPROVED`
- [ ] `identity.OrganizationApproved` unblocks publishing with no further action

### ET-CAT-001-R3 · A material change returns an event for re-approval

IF an approved or published event's material fields change, THEN THE SYSTEM SHALL return it
to `DRAFT`.

**Acceptance**
- [ ] The material set is exactly `startsAt`, `endsAt`, `locationId`, `organizationId`, `totalCapacity` — declared as a constant, not as a scattered set of `if`s
- [ ] Editing any material field of an `APPROVED` event moves it to `DRAFT` and clears `approvedAt` and `reviewedById`
- [ ] Editing a non-material field of an `APPROVED` event leaves the status unchanged
- [ ] A material change to a `PUBLISHED` event is **refused** — it must be unpublished or rescheduled first, and rescheduling is the only sanctioned way to change the date of a published event
- [ ] A test asserts each material field individually triggers the return, and each non-material field does not
- [ ] Adding a field to `Event` fails a test until it is classified

### ET-CAT-001-R4 · Publishing is reversible only while nothing is sold

WHILE an event is `PUBLISHED`, THE SYSTEM SHALL permit unpublishing only when no ticket
exists in a non-refunded state.

**Acceptance**
- [ ] `unpublishEvent` queries booking for the event's sold count and refuses when it is non-zero
- [ ] The refusal is `EVENT_STATE_INVALID` carrying the sold count in `details`
- [ ] Unpublishing closes every tier's sales window and returns the event to `APPROVED`
- [ ] A published event is visible to `PUBLIC` discovery; an `APPROVED` one is not, and a test asserts an unauthenticated query for an `APPROVED` event returns `EVENT_UNKNOWN`
- [ ] `catalog.EventPublished` is emitted on publish and carries `eventId`, `organizationId` and `startsAt`
- [ ] Re-publishing after an unpublish emits `catalog.EventPublished` again, and its consumers are idempotent on `eventId` ([ET-PLT-003](../../_platform/003-event-contract/) R5)

### ET-CAT-001-R5 · Rescheduling keeps tickets valid and opens a refund window

WHEN a published event is rescheduled, THE SYSTEM SHALL keep it published with the new
dates, keep every ticket valid, and grant every holder an unconditional refund window.

**Acceptance**
- [ ] `rescheduleEvent` requires `event:publish`, a reason, and a `newStartsAt` in the future
- [ ] The event remains `PUBLISHED`; no ticket changes status
- [ ] `previousStartsAt` is retained on the event so a holder can see what changed
- [ ] `catalog.EventRescheduled` carries `eventId`, `previousStartsAt` and `newStartsAt`
- [ ] Booking opens a 100% refund window of `catalog.reschedule.refund-window` (P7D) from the event, overriding the event's normal refund policy ([ET-FIN-004](../../finance/004-refunds-and-chargebacks/))
- [ ] The escrow hold clock is recalculated from the new `endsAt`, so a payout is not released against the old date
- [ ] Rescheduling twice within one window extends rather than replaces it — a holder who was mid-decision does not lose the option
- [ ] Every holder is notified ([ET-NTF-002](../../notification/002-lifecycle-triggers/))

### ET-CAT-001-R6 · Completion is detected, not declared

WHEN an event's `endsAt` has passed, THE SYSTEM SHALL move it to `COMPLETED` without an
organizer action.

**Acceptance**
- [ ] A scheduled sweep under `lock:sweep:event-completion` moves every `PUBLISHED` event whose `endsAt` is past to `COMPLETED`
- [ ] The sweep claims rows with a bounded batch and is idempotent — a second instance moves nothing twice
- [ ] `completedAt` is set from the `Clock`, and `catalog.EventCompleted` is emitted carrying `eventId` and `completedAt`
- [ ] No organizer-facing mutation moves an event to `COMPLETED`
- [ ] The sweep's interval is `catalog.completion.sweep-interval` and a frozen-clock test asserts an event completes at `endsAt + interval` at the latest
- [ ] An event cancelled before its `endsAt` is never swept

### ET-CAT-001-R7 · Cancellation refunds everyone and closes the escrow

WHEN an event is cancelled, THE SYSTEM SHALL refund every ticket in full, cancel the
pending commission, and close the escrow.

**Acceptance**
- [ ] `cancelEvent` requires `event:cancel` and a reason, and is legal from `APPROVED` and `PUBLISHED` only
- [ ] `catalog.EventCancelled` carries `eventId` and `reason`
- [ ] Every ticket in `PURCHASED` or `VALIDATED` receives a 100% refund request, regardless of the event's normal refund policy
- [ ] Commission in `PENDING` is **cancelled**, not clawed back; commission already `EARNED` is clawed back and marked `CLAWED_BACK` (D-04)
- [ ] `escrowDebit + commissionCancelled = totalRefundedToBuyers`, asserted to K0.01 across the whole event
- [ ] The escrow account reaches zero and `CLOSED`; a non-zero residual is `JOURNAL_UNBALANCED` and pages
- [ ] The refund fee is borne by the organizer on a cancellation (`CancellationFeePolicy.ORGANIZER_PAYS`), so the buyer receives the full ticket price
- [ ] A cancellation with an in-flight payout is refused until that payout resolves

### ET-CAT-001-R8 · Discovery shows published events and nothing else

THE SYSTEM SHALL expose only `PUBLISHED` events to unauthenticated callers, and SHALL
expose an organization's own events to its members.

**Acceptance**
- [ ] `events`, `eventsByCategory` and `eventsByCity` are `PUBLIC` and filter to `status = PUBLISHED` and `endsAt` in the future
- [ ] `event(id)` is `PUBLIC` and returns `EVENT_UNKNOWN` for any non-`PUBLISHED` event to a caller with no permission on it
- [ ] `myOrganizationEvents` returns every state including `DRAFT`, scoped by the caller's organizations at the repository ([ET-PLT-007](../../_platform/007-security-and-authorization/) R4)
- [ ] Discovery is served by the `{ status: 1, startsAt: 1 }` index and `explain()` reports `IXSCAN`
- [ ] Discovery lists are Relay connections; admin and organizer lists are offset pages ([ET-PLT-004](../../_platform/004-federation-contract/) §4)
- [ ] `catalog.EventCancelled` and unpublishing both remove the event from discovery within one cache TTL

## 4. Model

### The document

`catalog_events` — the fields this spec owns.

| Field | Type | Material | Notes |
|---|---|---|---|
| `_id` | `String` | — | |
| `organizationId` | `String` | **yes** | the tenant |
| `title`, `description`, `summary` | `String` | no | |
| `categoryId` | `String` | no | |
| `locationId` | `String` | **yes** | |
| `bannerUrl`, `imageUrls` | `String`, `List` | no | |
| `startsAt`, `endsAt` | `Instant` | **yes** | |
| `previousStartsAt` | `Instant` | no | set by reschedule |
| `timezone` | `String` | no | display only; storage is UTC |
| `totalCapacity` | `int` | **yes** | sum of tier capacities |
| `status` | `EventStatus` | — | the eight states |
| `visibility` | `EventVisibility` | no | `PUBLIC`, `UNLISTED` |
| `refundPolicy` | `RefundPolicy` | no | [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) |
| `submittedAt`, `approvedAt`, `publishedAt`, `completedAt`, `cancelledAt` | `Instant` | — | |
| `reviewedById`, `reviewReason` | `String` | — | |
| `cancellationReason` | `String` | — | |
| `rescheduleCount` | `int` | — | |
| `createdById` | `String` | — | granted `EVENT_OWNER` |
| `createdAt`, `updatedAt` | `Instant` | — | |
| `version` | `Long` | — | |

`MATERIAL_FIELDS` is a constant holding exactly `startsAt`, `endsAt`, `locationId`,
`organizationId`, `totalCapacity`.

### The state machine

| # | From | Action | To | Actor |
|---|---|---|---|---|
| 1 | — | `createEvent` | `DRAFT` | organizer |
| 2 | `DRAFT` | `submitEventForApproval` | `PENDING_APPROVAL` | organizer |
| 3 | `PENDING_APPROVAL` | `approveEvent` | `APPROVED` | admin |
| 4 | `PENDING_APPROVAL` | `rejectEvent` | `REJECTED` | admin |
| 5 | `REJECTED` | `reviseEvent` | `DRAFT` | organizer |
| 6 | `APPROVED` | `reviseEvent` | `DRAFT` | organizer / **material change** |
| 7 | `APPROVED` | `publishEvent` | `PUBLISHED` | organizer |
| 8 | `PUBLISHED` | `unpublishEvent` | `APPROVED` | organizer, zero sold only |
| 9 | `PUBLISHED` | `rescheduleEvent` | `PUBLISHED` | organizer |
| 10 | `PUBLISHED` | `completeEvent` | `COMPLETED` | **system sweep** |
| 11 | `PUBLISHED` | `cancelEvent` | `CANCELLED` | organizer / admin |
| 12 | `APPROVED` | `cancelEvent` | `CANCELLED` | organizer / admin |
| 13 | `DRAFT` | `deleteEvent` | `DELETED` | organizer |
| 14 | `REJECTED` | `deleteEvent` | `DELETED` | organizer |

Eight states plus the null origin, eleven actions: 99 pairs, of which 14 are legal.
`EventTransitionTest` drives all 99.

### Visibility by state

| State | `PUBLIC` discovery | Organization members | Admin |
|---|:-:|:-:|:-:|
| `DRAFT` | ❌ | ✅ | ✅ |
| `PENDING_APPROVAL` | ❌ | ✅ | ✅ |
| `REJECTED` | ❌ | ✅ | ✅ |
| `APPROVED` | ❌ | ✅ | ✅ |
| `PUBLISHED` | ✅ | ✅ | ✅ |
| `COMPLETED` | ✅ (past) | ✅ | ✅ |
| `CANCELLED` | ✅ (marked) | ✅ | ✅ |
| `DELETED` | ❌ | ❌ | ✅ |

`CANCELLED` stays visible deliberately — a person who bought a ticket must be able to find
the event and see that it was cancelled.

### Cancellation arithmetic

Asserted per event by R7, for every ticket:

```
escrowDebit          = Σ ticket.netAmount
commissionCancelled  = Σ ticket.commissionAmount where status = PENDING
commissionClawedBack = Σ ticket.commissionAmount where status = EARNED
totalRefunded        = Σ ticket.grossAmount

escrowDebit + commissionCancelled + commissionClawedBack == totalRefunded   (K0.01)
```

The refund provider fee is borne by the organizer's escrow, so the buyer receives
`grossAmount` unreduced ([ET-FIN-004](../../finance/004-refunds-and-chargebacks/) §4).

### GraphQL

Subgraph `catalog`. Every field carries `@auth` explicitly.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `event(id)` | query | `PUBLIC` | `Event` |
| `events(filter, first, after)` | query | `PUBLIC` | `EventConnection!` |
| `eventsByCategory(categoryId, first, after)` | query | `PUBLIC` | `EventConnection!` |
| `eventsByCity(cityId, first, after)` | query | `PUBLIC` | `EventConnection!` |
| `searchEvents(query, filter, first, after)` | query | `PUBLIC` | `EventConnection!` |
| `myOrganizationEvents(organizationId, status, page)` | query | `ORGANIZER` | `EventPage!` |
| `eventsPendingApproval(page)` | query | `ADMIN` | `EventPage!` |
| `createEvent(input)` | mutation | `ORGANIZER` | `Event!` |
| `updateEvent(id, input)` | mutation | `ORGANIZER` | `Event!` |
| `submitEventForApproval(id)` | mutation | `ORGANIZER` | `Event!` |
| `approveEvent(id)` | mutation | `ADMIN` | `Event!` |
| `rejectEvent(id, reason)` | mutation | `ADMIN` | `Event!` |
| `publishEvent(id)` | mutation | `ORGANIZER` | `Event!` |
| `unpublishEvent(id)` | mutation | `ORGANIZER` | `Event!` |
| `rescheduleEvent(input)` | mutation | `ORGANIZER` | `Event!` |
| `cancelEvent(id, reason)` | mutation | `ORGANIZER` | `Event!` |
| `deleteEvent(id)` | mutation | `ORGANIZER` | `Boolean!` |

`Event` is catalog's `@key(fields: "id")` type; identity extends it with `accessGrants` and
booking with `tickets`, `ticketsSold`, `grossRevenue` and `escrowAccount`
([ET-PLT-004 §4](../../_platform/004-federation-contract/)). `grossRevenue` and
`escrowAccount` carry `@tag(name: "admin")`.

### Events

| Tier | Name | When | Consumers |
|---|---|---|---|
| bus | `catalog.EventPublished` v1 | transition 7 | booking → open escrow; identity → notify |
| bus | `catalog.EventRescheduled` v1 | transition 9 | booking → refund window; identity → notify holders |
| bus | `catalog.EventCancelled` v1 | transitions 11, 12 | booking → mass refund; identity → notify holders |
| bus | `catalog.EventCompleted` v1 | transition 10 | booking → recognise commission, open payout window; identity → notify |
| module | `EventSubmittedEvent` | transition 2 | the approval queue ([ET-ADM-001](../../admin/001-approvals-workbench/)) |
| module | `EventDecidedEvent` | transitions 3, 4 | notify the organizer |

All four bus rows are §4 registry rows of
[ET-PLT-003](../../_platform/003-event-contract/).

### Consumed events

| Wire name | Effect |
|---|---|
| `identity.OrganizationApproved` | publishing is unblocked |
| `identity.OrganizationSuspended` | every `PUBLISHED` event of that organization returns to `APPROVED` |
| `booking.TicketPurchased` | increments the denormalised sold counter on the event |
| `booking.RefundCompleted` | decrements it |

The sold counter on `catalog_events` is a **display** figure. The authority is
`booking_tier_inventory` ([ET-PLT-002](../../_platform/002-persistence-baseline/) §2), and
R4's unpublish check queries booking rather than reading it.

### Redis keys

| Key | TTL | Purpose | Authority |
|---|---|---|---|
| `cache:event:{eventId}` | 300 s | discovery read-through | `catalog_events` |
| `lock:sweep:event-completion` | 30 s | the R6 sweep mutex | — |

### Configuration

| Property | Value |
|---|---|
| `catalog.completion.sweep-interval` | `PT15M` |
| `catalog.reschedule.refund-window` | `P7D` |
| `catalog.event.max-reschedules` | 3 |
| `catalog.event.min-lead-time` | `PT24H` — between publish and `startsAt` |

### Error codes

`EVENT_UNKNOWN`, `EVENT_STATE_INVALID`, `ORGANIZER_NOT_APPROVED` — rows of
[ET-PLT-005 §4](../../_platform/005-error-contract/) introduced by this spec.

## 5. Tasks

- [ ] **T1 · The document, `EventStatus`, `EventTransitions`, the 99-pair test**
  - requirements: R1
  - files: `backend/catalog-service/.../domain/`
  - verify: 14 legal rows, 85 refusals, no status literal in any mutation
  - parallel-safe: no
  - depends: —

- [ ] **T2 · `MATERIAL_FIELDS` and the re-approval rule**
  - requirements: R3
  - files: `backend/catalog-service/.../service/impl/EventServiceImpl.java`
  - verify: each material field individually returns an `APPROVED` event to `DRAFT`; adding a field fails a test until classified
  - parallel-safe: yes
  - depends: T1

- [ ] **T3 · Create and publish gates over the permission API; `EVENT_OWNER` grant**
  - requirements: R2
  - files: `backend/catalog-service/.../infrastructure/client/PermissionClient.java`
  - verify: catalog contains no role comparison; the creator holds `EVENT_OWNER`
  - parallel-safe: no — depends on ET-ORG-003's endpoint
  - depends: T1

- [ ] **T4 · Publish, unpublish and the zero-sold check**
  - requirements: R4
  - files: `backend/catalog-service/.../web/graphql/mutation/EventMutationResolver.java`
  - verify: an event with one sold ticket cannot be unpublished; an `APPROVED` event is invisible publicly
  - parallel-safe: yes
  - depends: T3

- [ ] **T5 · Reschedule: dates, refund window, escrow clock, window extension**
  - requirements: R5
  - files: `backend/catalog-service/.../service/impl/EventServiceImpl.java`
  - verify: tickets stay valid; a second reschedule extends rather than replaces the window
  - parallel-safe: yes
  - depends: T4

- [ ] **T6 · The completion sweep, its lock and its idempotence**
  - requirements: R6
  - files: `backend/catalog-service/.../scheduler/EventCompletionSweeper.java`
  - verify: a frozen-clock test completes at `endsAt + interval`; a second instance moves nothing twice
  - parallel-safe: yes
  - depends: T1

- [ ] **T7 · Cancellation and the arithmetic assertion**
  - requirements: R7
  - files: `backend/catalog-service/.../service/impl/`, booking's consumer
  - verify: `escrowDebit + commissionCancelled + clawedBack == totalRefunded` to K0.01
  - parallel-safe: no — spans two services
  - depends: T4

- [ ] **T8 · Consumers for the two identity events; the denormalised counter**
  - requirements: R2, R8
  - files: `backend/catalog-service/.../event/listener/`
  - verify: suspension unpublishes; the counter is display-only and never gates
  - parallel-safe: yes
  - depends: T4

- [ ] **T9 · Discovery: connections, the index, cache, `@auth` on every field**
  - requirements: R8
  - files: `backend/catalog-service/src/main/resources/graphql/schema.graphqls`
  - verify: `explain()` reports `IXSCAN`; `compose-supergraph.sh --static`
  - parallel-safe: no — shared SDL with ET-CAT-002 and ET-CAT-003
  - depends: T4

## 6. Out of scope

| Capability | Spec |
|---|---|
| Tiers, capacity, pricing and the sales window | [ET-CAT-002](../002-ticket-tiers-and-inventory/) |
| Venues, cities, categories and search infrastructure | [ET-CAT-003](../003-locations-and-reference-data/) |
| Reserving and selling a ticket | [ET-TKT-001](../../ticketing/001-reservation-and-hold/) |
| Opening and closing the escrow account | [ET-FIN-001](../../finance/001-escrow-and-ledger/) |
| Recognising commission at completion | [ET-FIN-002](../../finance/002-commission/) |
| Performing the refunds a cancellation requires | [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) |
| The approval queue, SLA and escalation | [ET-ADM-001](../../admin/001-approvals-workbench/) |
| The notifications each transition sends | [ET-NTF-002](../../notification/002-lifecycle-triggers/) |
| Organization status and the stage matrix this spec gates on | [ET-ORG-001](../../organization/001-organizer-onboarding/) |

Deliberately never in scope: **a `RESCHEDULED` state** (it would permit sales, which makes
it `PUBLISHED`), **organizer-declared completion** (the organizer's incentive runs the
wrong way), and **unpublishing an event with tickets sold** (the promise to the buyer
stands).
