# ET-ADM-001 · The approvals workbench — queues, SLA and escalation

> **Conformance** · US-PADM-001 review organizer applications · US-PADM-002 approve organizer · US-ADM-001 approve event

## 1. Capability

Two queues stand between a person wanting to sell tickets and being able to: the organizer
application, reviewed once, and the event, reviewed every time. Both are staffed by humans
looking at documents and forms, and both are the platform's throughput bottleneck at
exactly the moment it is growing — a reviewer who takes four days is an organizer who
launched somewhere else.

This spec builds the workbench those reviewers use. It declares the queues and how work is
ordered within them, the SLA each queue carries and what happens when it is breached, the
claim mechanism that stops two reviewers opening the same application, and the decision
surface that makes approve, reject and request-changes one action each rather than a form
somebody assembles.

It does not own the decisions. Approving an organizer is
[ET-ORG-001](../../organization/001-organizer-onboarding/) R5; approving an event is
[ET-CAT-001](../../catalog/001-event-lifecycle/) R1. This spec owns getting the right item
in front of the right reviewer at the right time, and knowing when that has stopped
happening.

The measure it exists to move is **time to decision**, and every mechanism here — ordering,
claiming, SLA, escalation, bulk actions on the safe cases — is in service of it.

## 2. Design decisions

**Two queues, one workbench, one claim model.** Organizer applications and event approvals
are different objects with different reviewers, and merging them into a generic "approvals"
list means a reviewer switching context every item. Separate queues, shared mechanics.

**Work is claimed, not just viewed.** A reviewer claims an item, holds it for a bounded
period, and the claim expires if they walk away. Without it, two reviewers open the same
application and one of them wastes their time — or worse, both decide.

**Oldest first, with escalated items floated.** FIFO by `submittedAt` is the only ordering
that is fair and that nobody has to explain. Items past their SLA float to the top with a
marker, because an item that has already breached is the one costing the most.

**Each queue carries an SLA, and breaching it is an event, not a report.** Organizer
applications: 48 hours. Event approvals: 24 hours — an event is time-sensitive in a way an
application is not. A breach raises an escalation record, notifies a supervisor and shows
in the queue. A dashboard nobody opens is not an SLA.

**Escalation has levels and they mean different things.** Level 1 at the SLA: the reviewer
is reminded. Level 2 at twice the SLA: a supervisor is notified. Level 3 at four times:
platform leadership. Each is a record with a timestamp, so *how long did this actually
take* is answerable afterwards.

**A decision requires everything the decision needs, checked before it is offered.** The
approve action is unavailable while a required document is unreviewed. That is what stops
an approval that has to be reversed, and reversing an approval is expensive
([ET-ORG-001](../../organization/001-organizer-onboarding/) R6's compensating saga).

**Bulk actions exist only for the safe direction.** A reviewer may bulk-approve events from
organizations with a clean history against a declared rule set. Nothing bulk-rejects, and
nothing bulk-approves an organizer application — a rejection is a person being told no, and
it deserves an individual reason.

**Everything is recorded, including the time spent.** Who claimed it, when, how long they
held it, what they decided and why. That record is the input to knowing whether the queue
is understaffed or the guidance is unclear.

**Rejected alternatives**

- *One merged approvals queue.* Context-switching per item, and no per-type SLA.
- *View-only queues with no claim.* Two reviewers on one item, and sometimes two decisions.
- *Priority scoring by organization size or revenue.* Unfair, unexplainable, and it optimises for the organizers who need help least.
- *SLA as a dashboard metric only.* Nobody opens it, and the breach is discovered by the applicant.
- *Allowing approval with unreviewed documents.* Produces approvals that have to be compensated.
- *Bulk rejection.* A rejection is a person being told no.
- *Deleting a decided item from the queue.* Loses the record of how long it took.

## 3. Requirements

### ET-ADM-001-R1 · Two queues, ordered oldest-first with breaches floated

THE SYSTEM SHALL present an organizer-application queue and an event-approval queue,
ordered by submission time with breached items first.

**Acceptance**
- [ ] `organizerApprovalQueue` lists organizations in `PENDING_REVIEW`, oldest `submittedAt` first
- [ ] `eventApprovalQueue` lists events in `PENDING_APPROVAL`, oldest first
- [ ] Items past their SLA appear first, marked, within their own oldest-first order
- [ ] Both are `ADMIN`-gated and paged (offset — an operator needs *page 7 of 41*)
- [ ] Each row carries what a reviewer needs to triage without opening it: name, submitted-at, age, document count, SLA state, claim state
- [ ] Queue depth and oldest-item age are metrics
- [ ] The queries are served by the `{ status: 1, submittedAt: 1 }` indexes and report `IXSCAN`

### ET-ADM-001-R2 · An item is claimed before it is worked

WHEN a reviewer opens an item, THE SYSTEM SHALL claim it for them and SHALL release the
claim if they do not act.

**Acceptance**
- [ ] `claimReviewItem` assigns the item to the caller for `admin.review.claim-ttl` (PT30M)
- [ ] A second reviewer claiming a held item is refused and told who holds it and until when
- [ ] The claim is released by a decision, by an explicit release, or by expiry
- [ ] A sweep under `lock:sweep:review-claims` expires stale claims
- [ ] Claiming is idempotent — the holder re-claiming extends rather than fails
- [ ] Two parallel claims produce exactly one holder, asserted under contention
- [ ] An expired claim returns the item to the queue at its original position, not the back

### ET-ADM-001-R3 · Each queue has an SLA, and a breach is an event

WHILE an item is unresolved past its SLA, THE SYSTEM SHALL escalate it through the declared
levels.

**Acceptance**
- [ ] Organizer applications carry `admin.sla.organizer` (PT48H); events carry `admin.sla.event` (PT24H)
- [ ] The clock starts at `submittedAt` and pauses while the item is in `CHANGES_REQUESTED`
- [ ] Level 1 at 1× the SLA notifies the claim holder, or the queue if unclaimed
- [ ] Level 2 at 2× notifies a supervisor; level 3 at 4× notifies platform leadership
- [ ] Each escalation writes a `catalog_approval_escalations` row with its level and timestamp
- [ ] Escalation is idempotent — a level fires once per item
- [ ] A test ages an item past each threshold and asserts one escalation per level

### ET-ADM-001-R4 · A decision is unavailable until its preconditions are met

IF an item's required inputs are incomplete, THEN THE SYSTEM SHALL not offer approval.

**Acceptance**
- [ ] An organizer application with any required document not `ACCEPTED` cannot be approved — the mutation refuses with `DOCUMENT_REQUIRED` carrying `missingDocumentTypes`
- [ ] An event with no published tier, no location or no capacity cannot be approved
- [ ] The queue row shows which precondition is outstanding, so a reviewer does not open an item they cannot decide
- [ ] Reject and request-changes are always available — a reviewer is never trapped
- [ ] The precondition check is the same one the decision mutation enforces; there is no second implementation
- [ ] A test attempts approval with each precondition unmet and asserts a refusal naming it

### ET-ADM-001-R5 · Every decision is one action with a recorded reason

WHEN a reviewer decides, THE SYSTEM SHALL record the decision, the reason and the elapsed
time, and SHALL release the claim.

**Acceptance**
- [ ] Approve, reject and request-changes are one mutation each, delegating to [ET-ORG-001](../../organization/001-organizer-onboarding/) R5 or [ET-CAT-001](../../catalog/001-event-lifecycle/) R1
- [ ] Reject and request-changes require a reason of at least 20 characters
- [ ] The decision records the reviewer, the timestamp, the reason and `timeToDecisionMs` from `submittedAt`
- [ ] A `catalog_approval_timelines` row is appended for every state change, including claims and escalations
- [ ] The claim is released atomically with the decision
- [ ] Deciding an item claimed by somebody else is refused
- [ ] The applicant is notified ([ET-NTF-002](../../notification/002-lifecycle-triggers/) rows 14–19)

### ET-ADM-001-R6 · Bulk approval exists for events only, against a declared rule set

WHERE an event meets every bulk-eligibility rule, THE SYSTEM SHALL permit bulk approval, and
no bulk rejection SHALL exist.

**Acceptance**
- [ ] `bulkApproveEvents` accepts up to `admin.review.bulk-max` (50) event ids
- [ ] Every event must satisfy the §4 bulk-eligibility rules; an ineligible one is skipped and reported, not silently included
- [ ] The response reports each id's outcome individually
- [ ] No bulk rejection or bulk request-changes mutation exists anywhere
- [ ] No bulk action applies to organizer applications
- [ ] Each approval in a bulk action writes its own timeline row and audit row
- [ ] A bulk action is idempotent — re-submitting the same ids approves nothing twice

### ET-ADM-001-R7 · The queue's health is measurable and visible

THE SYSTEM SHALL report throughput, ageing and reviewer load.

**Acceptance**
- [ ] `approvalMetrics(from, to)` returns decided count, median and p90 time-to-decision, breach count and per-reviewer counts
- [ ] Figures come from a MongoDB aggregation with `$match` first, never client-side counting
- [ ] Current queue depth, oldest-item age and open escalations by level are live metrics
- [ ] A rising oldest-item age alerts before the SLA is breached, not after
- [ ] Time-to-decision excludes time spent in `CHANGES_REQUESTED`, matching R3's clock
- [ ] The workbench refreshes by polling (D-12) at a declared interval

## 4. Model

### The two queues

| Queue | Source | Status filter | SLA | Reviewer |
|---|---|---|---|---|
| organizer applications | `identity_organizations` | `PENDING_REVIEW` | `PT48H` | `ADMIN` |
| event approvals | `catalog_events` | `PENDING_APPROVAL` | `PT24H` | `ADMIN` |

Both ordered `submittedAt` ascending, with breached items floated and marked.

### Documents

`catalog_approval_timelines` — append-only, one row per state change.

| Field | Notes |
|---|---|
| `_id`, `subjectType`, `subjectId` | `ORGANIZATION` or `EVENT` |
| `action` | `SUBMITTED`, `CLAIMED`, `CLAIM_RELEASED`, `CLAIM_EXPIRED`, `ESCALATED`, `APPROVED`, `REJECTED`, `CHANGES_REQUESTED`, `RESUBMITTED` |
| `actorId` | null for system actions |
| `reason` | required on the three decisions |
| `previousStatus`, `newStatus` | |
| `occurredAt` | |

`catalog_approval_escalations`

| Field | Notes |
|---|---|
| `_id`, `subjectType`, `subjectId` | |
| `level` | 1, 2, 3 |
| `slaBreachedAt`, `escalatedAt` | |
| `notifiedRoleOrUserId` | |
| `status` | `OPEN`, `RESOLVED` |
| `resolvedAt` | set by the decision |

`identity_review_claims`

| Field | Notes |
|---|---|
| `_id`, `subjectType`, `subjectId` | `{subjectType, subjectId}` **unique** — one claim per item |
| `claimedById`, `claimedAt`, `expiresAt` | |
| `status` | `HELD`, `RELEASED`, `EXPIRED` |

### The SLA clock

```
elapsed = now − submittedAt − Σ(time spent in CHANGES_REQUESTED)

level 1  elapsed ≥ 1 × sla   → notify the holder, or the queue
level 2  elapsed ≥ 2 × sla   → notify a supervisor
level 3  elapsed ≥ 4 × sla   → notify platform leadership
```

Pausing during `CHANGES_REQUESTED` is the point: the platform is not late while it is
waiting for the applicant.

### Bulk-eligibility rules — events only

An event may be bulk-approved only when **every** rule holds:

| # | Rule |
|---|---|
| 1 | the organization is `ACTIVE` and has at least `admin.bulk.min-completed-events` (3) completed events |
| 2 | the organization has no open escalation, suspension or chargeback in `admin.bulk.clean-window` (P180D) |
| 3 | the event has at least one tier, a location and a capacity |
| 4 | the event's capacity is within `admin.bulk.max-capacity` (5,000) |
| 5 | the event's category is not on the platform's manual-review list |
| 6 | the event has been in the queue less than 2× its SLA — a long-waiting event gets a human |

Rule 6 is deliberate: an item that has already breached is the one most likely to be
unusual, and bulk-approving it is how the unusual case ships.

### Decision surface

| Mutation | Subject | Delegates to |
|---|---|---|
| `approveOrganizationApplication(input)` | organization | [ET-ORG-001](../../organization/001-organizer-onboarding/) R5, R6 |
| `rejectOrganizationApplication(input)` | organization | [ET-ORG-001](../../organization/001-organizer-onboarding/) R5 |
| `requestOrganizationChanges(input)` | organization | [ET-ORG-001](../../organization/001-organizer-onboarding/) R5 |
| `approveEventSubmission(input)` | event | [ET-CAT-001](../../catalog/001-event-lifecycle/) R1 |
| `rejectEventSubmission(input)` | event | [ET-CAT-001](../../catalog/001-event-lifecycle/) R1 |
| `bulkApproveEvents(ids)` | events | as above, per id |

This spec adds no state machine. Every decision is the owning spec's transition, invoked
with the workbench's claim, reason and timeline bookkeeping around it.

### GraphQL

Split across `identity` (organizations) and `catalog` (events), because each subgraph owns
its own type. Every field is `ADMIN` and `@tag(name: "admin")`.

| Operation | Kind | Subgraph | Returns |
|---|---|---|---|
| `organizerApprovalQueue(page)` | query | identity | `OrganizationPage!` |
| `eventApprovalQueue(page)` | query | catalog | `EventPage!` |
| `approvalTimeline(subjectType, subjectId)` | query | catalog | `[ApprovalTimelineEntry!]!` |
| `openEscalations(level, page)` | query | catalog | `ApprovalEscalationPage!` |
| `approvalMetrics(from, to)` | query | catalog | `ApprovalMetrics!` |
| `myClaimedItems` | query | catalog | `[ReviewClaim!]!` |
| `claimReviewItem(subjectType, subjectId)` | mutation | catalog | `ReviewClaim!` |
| `releaseReviewItem(id)` | mutation | catalog | `Boolean!` |
| `bulkApproveEvents(ids)` | mutation | catalog | `[BulkApprovalOutcome!]!` |

The decision mutations themselves live in their owning specs' subgraphs and are listed in
§4's decision surface; this spec adds only the workbench operations.

`BulkApprovalOutcome` carries `eventId`, `approved` and, when skipped, the rule that
excluded it — so a reviewer sees exactly why an event was left behind.

### Sweeps

| Sweep | Lock | Interval | Purpose |
|---|---|---|---|
| claim expiry | `lock:sweep:review-claims` | `PT5M` | R2 |
| SLA escalation | `lock:sweep:approval-sla` | `PT15M` | R3 |

### Configuration

| Property | Value |
|---|---|
| `admin.sla.organizer` | `PT48H` |
| `admin.sla.event` | `PT24H` |
| `admin.review.claim-ttl` | `PT30M` |
| `admin.review.bulk-max` | 50 |
| `admin.bulk.min-completed-events` | 3 |
| `admin.bulk.clean-window` | `P180D` |
| `admin.bulk.max-capacity` | 5,000 |
| `admin.workbench.poll-interval` | `PT30S` |

### Error codes

None introduced. `DOCUMENT_REQUIRED`, `ORGANIZATION_STATE_INVALID` and
`EVENT_STATE_INVALID` are raised here and owned by
[ET-ORG-001](../../organization/001-organizer-onboarding/) and
[ET-CAT-001](../../catalog/001-event-lifecycle/).

## 5. Tasks

- [ ] **T1 · The two queue queries, their ordering and their indexes**
  - requirements: R1
  - files: `backend/identity-service/.../query/`, `backend/catalog-service/.../query/`
  - verify: `explain()` reports `IXSCAN`; breached items float
  - parallel-safe: yes — one service per agent
  - depends: —

- [ ] **T2 · Claims: the unique index, the TTL, the expiry sweep**
  - requirements: R2
  - files: `backend/catalog-service/.../domain/model/ReviewClaim.java`, `.../scheduler/`
  - verify: two parallel claims yield one holder; an expired claim keeps its queue position
  - parallel-safe: no
  - depends: T1

- [ ] **T3 · The SLA clock, its pause, and the three escalation levels**
  - requirements: R3
  - files: `backend/catalog-service/.../service/impl/ApprovalSlaService.java`
  - verify: one escalation per level; the clock pauses in `CHANGES_REQUESTED`
  - parallel-safe: yes
  - depends: T1

- [ ] **T4 · Precondition checks, shared with the decision mutations**
  - requirements: R4
  - files: the owning specs' services
  - verify: each unmet precondition refuses by name; there is no second implementation
  - parallel-safe: yes
  - depends: T1

- [ ] **T5 · The decision surface, the timeline and the atomic claim release**
  - requirements: R5
  - files: `backend/*/.../web/graphql/mutation/`
  - verify: deciding another reviewer's claim refuses; every change appends a timeline row
  - parallel-safe: no
  - depends: T2, T4

- [ ] **T6 · Bulk approval, its six rules and its per-id outcomes**
  - requirements: R6
  - files: `backend/catalog-service/.../service/impl/BulkApprovalService.java`
  - verify: no bulk rejection exists anywhere; a re-submission approves nothing twice
  - parallel-safe: yes
  - depends: T5

- [ ] **T7 · Metrics: throughput, ageing, per-reviewer load, and the pre-breach alert**
  - requirements: R7
  - files: `backend/catalog-service/.../repository/impl/ApprovalMetricsRepository.java`
  - verify: `$match` first; the alert fires before the SLA, not after
  - parallel-safe: yes
  - depends: T3

- [ ] **T8 · The subgraph halves; every field `ADMIN` and `@tag(name: "admin")`**
  - requirements: R1–R7
  - files: both `schema.graphqls`
  - verify: the public contract exposes no workbench field
  - parallel-safe: no — two SDL files
  - depends: T6

## 6. Out of scope

| Capability | Spec |
|---|---|
| The organizer application state machine and approval saga | [ET-ORG-001](../../organization/001-organizer-onboarding/) |
| The event state machine and its approval transition | [ET-CAT-001](../../catalog/001-event-lifecycle/) |
| Document upload, review and storage | [ET-ORG-001](../../organization/001-organizer-onboarding/) |
| Notifying the applicant of a decision | [ET-NTF-002](../../notification/002-lifecycle-triggers/) |
| Who may review — the `organization:approve` permission | [ET-ORG-003](../../organization/003-permission-resolution/) |
| Platform-wide dashboards and alert routing | [ET-ADM-005](../005-observability-and-health/) |
| Audit rows for every decision | [ET-PLT-009](../../_platform/009-audit-trail/) |

Deliberately never in scope: **bulk rejection** (a rejection is a person being told no),
**priority scoring by organization size** (it optimises for the organizers who need help
least), and **SLA as a dashboard metric only** (the breach is then discovered by the
applicant).
