# ET-TKT-003 · Validation, check-in and the offline gate

> **Conformance** · US-SCN-001 · US Part I §3 event-level roles

## 1. Capability

At eight in the evening, at a gate, with four hundred people queuing, a steward points a
phone at a QR code and needs an answer in under a second. The venue's Wi-Fi is contended,
the mobile signal in a steel-framed hall is intermittent, and the answer must be right
because letting one person in twice on one ticket is the failure everybody notices.

This spec builds that scan. It declares the two modes it operates in — **online**, where
the platform is the arbiter, and **offline**, where the scanner is — and it is honest about
what each can guarantee. Online, a duplicate scan is impossible: the transition is a
conditional atomic update and the second scan loses. Offline, a duplicate across two
devices cannot be prevented at the moment of scan, only detected on reconnect, so this spec
declares exactly what is detected, when, and what an operator does about it.

It declares who may scan — an event access grant, not an organization role, because the
person on the gate is usually not on the team ([ET-ORG-003](../../organization/003-permission-resolution/))
— the manual override for a ticket whose QR will not scan, and the check-in reporting an
organizer watches during the event.

The property it protects is that **a ticket admits one person, once**. Everything else here
is in service of that, including the parts that admit the guarantee is weaker offline.

## 2. Design decisions

**Two modes, and the difference is stated rather than hidden.** Online validation is a
server-side conditional update — exactly-once by construction. Offline validation verifies
the signature locally and records the scan for later upload; two devices offline can each
admit the same ticket, and no amount of client cleverness prevents that. The spec says so,
declares the detection on reconnect, and gives the operator a report. A platform that
claims offline exactly-once is a platform whose stewards will discover otherwise.

**The scanner holds the event key and verifies locally, always — even online.** Signature
verification is local in both modes ([ET-TKT-002](../002-ticket-issuance-and-qr/) R2). A
forged QR is rejected without a round trip, which keeps the queue moving and keeps the
platform from being a validation oracle for someone probing with fake codes.

**Online, the transition is a conditional atomic update.** `findAndModify` filtering on
`status = ISSUED`; an empty result means somebody already scanned it. That is the whole
duplicate defence, and it needs no lock.

**A duplicate scan returns a rich refusal, not a bare no.** `TICKET_ALREADY_VALIDATED`
carries `validatedAt` and `validatedById`. The steward needs to know whether it was scanned
two seconds ago at the next lane or two hours ago at a different gate — those are different
conversations with the person in front of them.

**Offline scans are queued locally and uploaded, and the upload is idempotent.** Each scan
carries a client-generated id; re-uploading the same batch changes nothing. The upload
resolves each scan to `ACCEPTED`, `DUPLICATE` or `REJECTED`, and returns that resolution so
the device can show its operator what actually happened.

**The first offline scan wins, by scan timestamp.** When two offline scans of one ticket
arrive, the earlier `scannedAt` is the accepted one. It is arbitrary but it must be
deterministic, and using the device clock is acceptable because both devices belong to the
same event and a skewed clock is a device problem the report surfaces.

**Scanning is authorised by an event access grant, checked at key issue and at upload.**
A steward is issued the event key when they hold `ticket:scan`
([ET-ORG-003](../../organization/003-permission-resolution/) §4), and the upload re-checks —
because a grant revoked mid-event must stop new admissions even though the key is already
on the device.

**A manual override exists, is recorded, and looks different in every report.** A phone
with a broken screen, a printed ticket that will not scan, a will-call collection. An
operator with `ticket:scan` admits by reference, gives a reason, and the check-in is marked
`MANUAL`. Making it impossible would just move the problem to a paper list nobody reconciles.

**Validation does not affect refund eligibility.** A validated ticket can still be refunded
([ET-FIN-004](../../finance/004-refunds-and-chargebacks/)) — for a cancelled event, a
platform error, or a dispute. Coupling them means a scan at the door forfeits a refund the
buyer is entitled to.

**Rejected alternatives**

- *Online-only validation.* Fails at exactly the venue where it matters.
- *Claiming offline exactly-once.* It is not achievable, and claiming it means nobody builds the reconciliation.
- *A server round trip for signature verification.* Makes the platform a validation oracle and puts the queue behind the network.
- *Last-write-wins on offline conflicts.* Non-deterministic and rewards the slower device.
- *No manual override.* Moves the problem to a paper list nobody reconciles.
- *Blocking a refund once a ticket is validated.* Forfeits an entitlement at the door.
- *Deleting a ticket on validation.* Loses the attendance record the organizer is paying for.

## 3. Requirements

### ET-TKT-003-R1 · Online, a ticket is validated exactly once

WHEN a ticket is scanned online, THE SYSTEM SHALL admit it exactly once, and IF it has
already been validated, THEN THE SYSTEM SHALL refuse with the prior scan's detail.

**Acceptance**
- [ ] The transition is a single `findAndModify` filtering on `status = ISSUED` and the correct `eventId`
- [ ] An empty result refuses with `TICKET_ALREADY_VALIDATED` carrying `validatedAt` and `validatedById`
- [ ] 50 parallel scans of one ticket produce exactly one `ACCEPTED` and 49 duplicates, under real contention
- [ ] A ticket for a different event refuses with `TICKET_NOT_VALID_FOR_EVENT` carrying `expectedEventId`
- [ ] A `REFUNDED`, `CANCELLED` or `EXPIRED` ticket refuses with `TICKET_STATE_INVALID` carrying `currentStatus`
- [ ] The scan records `validatedAt`, `validatedById`, `validationMethod` and the gate identifier
- [ ] `booking.TicketValidated` is published after commit

### ET-TKT-003-R2 · Signature verification is local, in both modes

THE SYSTEM SHALL verify the QR signature on the scanning device before any network call.

**Acceptance**
- [ ] The scanner verifies `HMAC-SHA256` locally against the event key ([ET-TKT-002](../002-ticket-issuance-and-qr/) R2)
- [ ] A signature failure refuses with `TICKET_SIGNATURE_INVALID` and makes **no** network call
- [ ] Verification is constant-time and discloses nothing about which component failed
- [ ] An online scan verifies locally first and only then submits — a forged code never reaches the server
- [ ] The key is obtained through `eventSigningKey` and cached on the device for the event's duration
- [ ] A device whose grant has expired refuses to fetch or refresh the key

### ET-TKT-003-R3 · Offline scans are queued, uploaded and individually resolved

WHILE a scanner is offline, THE SYSTEM SHALL permit local validation and SHALL resolve every
queued scan on upload.

**Acceptance**
- [ ] Each offline scan carries a client-generated `scanId`, `ticketId`, `scannedAt` and `deviceId`
- [ ] `uploadScans` accepts a batch and returns a resolution per scan: `ACCEPTED`, `DUPLICATE`, `REJECTED`
- [ ] Re-uploading a batch is idempotent — `scanId` is unique-indexed and a repeat changes nothing
- [ ] A scan whose ticket was already validated online resolves `DUPLICATE` with the prior detail
- [ ] A scan for a refunded or cancelled ticket resolves `REJECTED` with the reason
- [ ] The device displays each resolution to its operator, so a duplicate admitted offline is visible to the person who admitted it
- [ ] A batch of 500 scans uploads within `booking.checkin.upload-budget` (PT10S)

### ET-TKT-003-R4 · Offline conflicts resolve deterministically, and are reported

IF two offline scans of one ticket are uploaded, THEN THE SYSTEM SHALL accept the earlier
`scannedAt` and report the conflict.

**Acceptance**
- [ ] The scan with the earlier `scannedAt` is `ACCEPTED`; every other is `DUPLICATE`
- [ ] A tie is broken by `scanId` lexicographically, so the outcome is deterministic
- [ ] Every conflict creates a `booking_checkin_conflicts` row naming both devices, both operators and both timestamps
- [ ] Conflicts are surfaced in the organizer's check-in report and counted as a metric
- [ ] A device whose clock is more than `booking.checkin.max-clock-skew` (PT5M) from the server's is flagged on upload
- [ ] A test uploads two offline scans of one ticket in both orders and asserts the same winner each time
- [ ] The spec's own documentation states plainly that offline mode cannot prevent a duplicate admission at the gate, only detect it

### ET-TKT-003-R5 · Only a grant-holder may scan, checked twice

THE SYSTEM SHALL authorise scanning by event access grant at key issue and again at upload.

**Acceptance**
- [ ] `eventSigningKey` is issued only to an actor holding `ticket:scan` on that event ([ET-ORG-003](../../organization/003-permission-resolution/))
- [ ] Every online scan and every upload re-checks the grant
- [ ] A grant revoked mid-event causes subsequent scans and uploads to refuse with `ACTOR_NOT_PERMITTED`
- [ ] Scans already uploaded before revocation stand
- [ ] The permission resolution is [ET-ORG-003](../../organization/003-permission-resolution/)'s — this spec contains no role comparison
- [ ] A test revokes a grant mid-batch and asserts the remainder refuses

### ET-TKT-003-R6 · A manual override exists and is distinguishable everywhere

IF a QR cannot be scanned, THEN THE SYSTEM SHALL permit admission by reference with a
reason, and SHALL mark it as manual.

**Acceptance**
- [ ] `validateByReference` requires `ticket:scan` on the event and a reason of at least 10 characters
- [ ] The check-in records `validationMethod = MANUAL`, the operator and the reason
- [ ] A manual validation is subject to the same duplicate defence as a scan
- [ ] Manual validations appear separately in the check-in report and are counted as a metric
- [ ] A rate of manual validations above `booking.checkin.manual-alert-ratio` (10%) alerts the organizer — it usually means the QR rendering is broken
- [ ] Every manual validation writes an audit row ([ET-PLT-009](../../_platform/009-audit-trail/))

### ET-TKT-003-R7 · The organizer sees check-in as it happens

THE SYSTEM SHALL report attendance during the event.

**Acceptance**
- [ ] `checkInSummary(eventId)` returns admitted, expected, remaining, per-tier breakdown, per-gate breakdown and the manual ratio
- [ ] Figures are computed by a MongoDB aggregation with `$match` first, never client-side counting
- [ ] The summary is available to any actor holding `event:view` on the event
- [ ] It refreshes by polling (D-12), with a documented interval and no subscription
- [ ] A late upload from an offline device updates the summary and is reflected within one poll
- [ ] Recent scans are listable with operator, gate, method and timestamp
- [ ] Conflicts and manual validations are visible in the same view, not buried in a separate report

### ET-TKT-003-R8 · Validation does not remove any entitlement

THE SYSTEM SHALL keep a validated ticket refundable and attributable.

**Acceptance**
- [ ] A `VALIDATED` ticket may still enter `REFUND_PENDING` ([ET-FIN-004](../../finance/004-refunds-and-chargebacks/))
- [ ] An event cancellation refunds validated tickets alongside issued ones
- [ ] Validation deletes nothing and anonymises nothing
- [ ] The attendance record survives a refund — the person attended, whatever happened to the money
- [ ] A test validates a ticket, refunds it, and asserts both facts are recoverable

## 4. Model

### Documents

`booking_checkins` — one row per validation, including manual ones.

| Field | Type | Notes |
|---|---|---|
| `_id`, `ticketId`, `eventId` | `String` | `ticketId` **unique** — one accepted check-in per ticket |
| `scanId` | `String` | client-generated, **unique sparse**; null for online scans |
| `validatedById`, `deviceId`, `gateId` | `String` | |
| `validationMethod` | `ValidationMethod` | `QR_ONLINE`, `QR_OFFLINE`, `MANUAL` |
| `manualReason` | `String` | required when `MANUAL` |
| `scannedAt` | `Instant` | the device's clock for offline scans |
| `recordedAt` | `Instant` | the server's clock |
| `clockSkewMs` | `long` | recorded on upload |

`booking_checkin_conflicts`

| Field | Notes |
|---|---|
| `ticketId`, `eventId` | |
| `acceptedScanId`, `acceptedDeviceId`, `acceptedAt` | the winner |
| `duplicateScanId`, `duplicateDeviceId`, `duplicateAt` | the loser |
| `detectedAt` | on upload |
| `reviewedById`, `reviewedAt`, `reviewNote` | operator resolution |

### The two modes, honestly

| | Online | Offline |
|---|---|---|
| Signature check | local | local |
| Duplicate check | **server, exactly-once** | none at scan time |
| Guarantee | a ticket admits once | a ticket admits once **per device** |
| Duplicate across devices | impossible | **possible** — detected on upload |
| Latency | one round trip | none |
| Requires network | yes | no |

The offline row is the reason `booking_checkin_conflicts` exists. Offline mode trades a
guarantee for availability, and the trade is stated here so nobody discovers it at a gate.

### Online validation

```java
// exactly-once by construction — the second scanner's findAndModify matches nothing
Query q = Query.query(Criteria.where("_id").is(ticketId)
        .and("eventId").is(eventId)
        .and("status").is(TicketStatus.ISSUED));

Update u = new Update().set("status", VALIDATED)
                       .set("validatedAt", clock.instant())
                       .set("validatedById", operatorId)
                       .set("validationMethod", QR_ONLINE);

return mongo.findAndModify(q, u, options().returnNew(true), Ticket.class)
            .switchIfEmpty(explainRefusal(ticketId, eventId));   // only on the refusal path
```

`explainRefusal` performs the follow-up read that distinguishes *already validated* from
*wrong event* from *refunded*. It runs only when the scan failed, so the happy path is one
round trip.

### Offline upload

```
POST batch of { scanId, ticketId, scannedAt, deviceId, gateId }

per scan, in scannedAt order:
  1  scanId already recorded?          → return its prior resolution   (idempotent)
  2  grant still valid?                → REJECTED, ACTOR_NOT_PERMITTED
  3  ticket state not ISSUED?          → REJECTED with the reason
  4  conditional update to VALIDATED
       won   → ACCEPTED
       lost  → DUPLICATE + a conflict row naming both devices
  5  record clock skew
```

Processing in `scannedAt` order is what makes R4's earliest-wins rule hold within a batch;
across batches the conditional update plus the conflict row handles it.

### Validation refusals

| Condition | Code | Detail |
|---|---|---|
| bad signature | `TICKET_SIGNATURE_INVALID` | — |
| already validated | `TICKET_ALREADY_VALIDATED` | `validatedAt`, `validatedById` |
| wrong event | `TICKET_NOT_VALID_FOR_EVENT` | `expectedEventId` |
| refunded, cancelled, expired | `TICKET_STATE_INVALID` | `currentStatus` |
| no grant | `ACTOR_NOT_PERMITTED` | — |

### GraphQL

Subgraph `booking`.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `checkInSummary(eventId)` | query | `ORGANIZER` | `CheckInSummary!` |
| `recentCheckIns(eventId, limit)` | query | `ORGANIZER` | `[CheckIn!]!` — bounded, ≤ 100 |
| `checkInConflicts(eventId, page)` | query | `ORGANIZER` | `CheckInConflictPage!` |
| `validateTicket(input)` | mutation | `ORGANIZER` | `ValidationResult!` |
| `validateByReference(input)` | mutation | `ORGANIZER` | `ValidationResult!` |
| `uploadScans(input)` | mutation | `ORGANIZER` | `[ScanResolution!]!` |
| `reviewConflict(id, note)` | mutation | `ORGANIZER` | `CheckInConflict!` |

Every one additionally requires `ticket:scan` on that event through
[ET-ORG-003](../../organization/003-permission-resolution/); the `ORGANIZER` gate is
coarse and never sufficient.

`ValidationResult` carries `outcome`, `ticket`, `attendeeName`, `tierName` and, on refusal,
the code and its detail — everything the steward needs on one screen.

### Events

| Tier | Name | When | Consumers |
|---|---|---|---|
| bus | `booking.TicketValidated` v1 | after commit | catalog → check-in counters |
| module | `CheckInConflictDetectedEvent` | R4 | alerting, the organizer's report |

### Configuration

| Property | Value |
|---|---|
| `booking.checkin.upload-batch-max` | 500 |
| `booking.checkin.upload-budget` | `PT10S` |
| `booking.checkin.max-clock-skew` | `PT5M` |
| `booking.checkin.manual-alert-ratio` | 10% |
| `booking.checkin.summary-poll-interval` | `PT15S` |

### Error codes

`TICKET_ALREADY_VALIDATED`, `TICKET_NOT_VALID_FOR_EVENT` — rows of
[ET-PLT-005 §4](../../_platform/005-error-contract/) introduced by this spec.
`TICKET_SIGNATURE_INVALID`, `TICKET_STATE_INVALID` and `ACTOR_NOT_PERMITTED` are raised
here and owned elsewhere.

## 5. Tasks

- [ ] **T1 · The check-in document, its unique index and the online conditional update**
  - requirements: R1
  - files: `backend/booking-service/.../service/impl/ValidationServiceImpl.java`
  - verify: 50 parallel scans yield one acceptance under real contention
  - parallel-safe: no
  - depends: —

- [ ] **T2 · The refusal explainer, on the refusal path only**
  - requirements: R1
  - files: `backend/booking-service/.../service/impl/ValidationServiceImpl.java`
  - verify: each refusal carries its declared detail; the happy path is one round trip
  - parallel-safe: yes
  - depends: T1

- [ ] **T3 · Local signature verification in the scanner client**
  - requirements: R2
  - files: `frontend/mobile/.../scanner/`, `backend/.../TicketSigner.java`
  - verify: a forged code makes no network call; verification is constant-time
  - parallel-safe: yes
  - depends: T1

- [ ] **T4 · `uploadScans`: idempotent, ordered, per-scan resolution**
  - requirements: R3
  - files: `backend/booking-service/.../service/impl/ScanUploadService.java`
  - verify: a re-uploaded batch changes nothing; 500 scans within budget
  - parallel-safe: no
  - depends: T2

- [ ] **T5 · Conflict detection, the earliest-wins rule and the conflict document**
  - requirements: R4
  - files: `backend/booking-service/.../service/impl/ScanUploadService.java`
  - verify: two offline scans in either upload order yield the same winner
  - parallel-safe: yes
  - depends: T4

- [ ] **T6 · Grant checks at key issue and at every scan and upload**
  - requirements: R5
  - files: `backend/booking-service/.../infrastructure/client/PermissionClient.java`
  - verify: a mid-batch revocation refuses the remainder; earlier scans stand
  - parallel-safe: yes
  - depends: T4

- [ ] **T7 · Manual override, its reason, its metric and its alert ratio**
  - requirements: R6
  - files: `backend/booking-service/.../service/impl/ValidationServiceImpl.java`
  - verify: manual validations are distinguishable in every report; the ratio alerts
  - parallel-safe: yes
  - depends: T2

- [ ] **T8 · The check-in summary aggregation and its polling contract**
  - requirements: R7
  - files: `backend/booking-service/.../repository/impl/CheckInStatsRepository.java`
  - verify: `$match` first; a late upload is reflected within one poll
  - parallel-safe: yes
  - depends: T5

- [ ] **T9 · The subgraph half; the refund-after-validation test**
  - requirements: R8
  - files: `backend/booking-service/src/main/resources/graphql/schema.graphqls`
  - verify: a validated ticket refunds and both facts survive
  - parallel-safe: no — shared SDL across booking's specs
  - depends: T7

## 6. Out of scope

| Capability | Spec |
|---|---|
| The QR payload, its signing and the event key | [ET-TKT-002](../002-ticket-issuance-and-qr/) |
| Who holds `ticket:scan` on which event | [ET-ORG-003](../../organization/003-permission-resolution/) |
| Refunding a validated ticket | [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) |
| Transferring a ticket before the gate | [ET-TKT-004](../004-transfer-and-resale/) |
| The event lifecycle that makes a ticket scannable | [ET-CAT-001](../../catalog/001-event-lifecycle/) |
| Attendance analytics beyond the live summary | [ET-ADM-004](../../admin/004-analytics-and-statistics/) |
| Audit rows for manual validations | [ET-PLT-009](../../_platform/009-audit-trail/) |

Deliberately never in scope: **claiming exactly-once offline** (it is not achievable, and
claiming it means nobody builds the reconciliation), **online-only validation** (it fails at
exactly the venue where it matters), and **blocking a refund once a ticket is scanned** (it
forfeits an entitlement at the door).
