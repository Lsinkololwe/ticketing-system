# ET-PLT-009 · The audit trail — what is recorded, by whom, and for how long

## 1. Capability

Scattered across this corpus, roughly thirty acceptance criteria end with *and writes an
audit row*. An admin approving an organizer. A finance operator confirming a payout. A
`SUPER_ADMIN` posting a manual ledger adjustment. Somebody reading a subscriber's raw
decline message. A cross-tenant read taken through the platform-wide bypass.

Those rows are the platform's answer to *who did this*. They are read when something has
gone wrong, often months later, sometimes by somebody who does not trust the answer. Which
means the trail has exactly three properties that matter: it is **complete** for the actions
that matter, it is **immutable**, and it is **readable by the people who need it and nobody
else**.

This spec declares all three. It declares the closed registry of auditable actions —
because a trail that records everything is a trail nobody can search, and one that records
what a developer remembered is a trail with a hole exactly where somebody was careless. It
declares append-only storage and the checks that prove it stayed that way. And it declares
who may read the trail, because an audit log full of who-did-what is itself sensitive.

## 2. Design decisions

**A closed registry of auditable actions, not a blanket rule.** Auditing every mutation
produces a collection nobody can search and a write amplification nobody budgeted. The
registry in §4 is the complete list, and it is chosen by one test: *would somebody
investigating a problem need to know this happened?*

**The trail is append-only, and that is enforced rather than asserted.** No update, no
delete, no mutation path. A hash chain links each row to the previous one for its subject,
so tampering by direct database access is detectable — which matters precisely because the
people who could tamper are the people the trail is about.

**Every row records five things: who, what, when, to what, and why.** The actor, the action,
the timestamp from the `Clock`, the subject, and — where the action required one — the
reason the actor gave. Missing any of the five makes the row useless six months later.

**Before-and-after values are recorded for state changes, redacted per the PII inventory.**
Knowing an organization moved from `PENDING_REVIEW` to `ACTIVE` is useful; knowing a bank
account's number changed from one full number to another is a liability
([ET-PLT-008](../008-data-protection/) §4). Values are recorded through the same redaction
the inventory declares.

**Reading the audit trail is itself audited.** A trail that can be read silently by anybody
with `ADMIN` is a trail that tells an investigator nothing about who was looking. Reads are
recorded, and the read-of-a-read is not — the recursion stops at one level, deliberately.

**Retention is seven years for financial and security actions, two for the rest.** The long
window matches [ET-PLT-008](../008-data-protection/)'s financial retention because the two
answer the same questions. The short window keeps operational noise from dominating a
search.

**Audit writing never fails the action it records.** An audit write that fails must not roll
back a payout. It is written in the same transaction where one exists and asynchronously
where it does not, with a failure raising an alert rather than an exception — because
losing an audit row is bad and losing a payout is worse.

**The trail is not a metric and not a debug log.** Metrics are
[ET-ADM-005](../../admin/005-observability-and-health/)'s and answer *how much*. Logs answer
*what happened technically*. The audit trail answers *who decided this*, and conflating them
produces a collection with a billion rows and no signal.

**Rejected alternatives**

- *Auditing every mutation.* Unsearchable, and a write amplification nobody budgeted.
- *Append-only by convention.* The people who can violate it are the people it is about.
- *Recording full before-and-after values.* Duplicates every sensitive field into a second collection.
- *Unaudited reads.* The trail tells an investigator nothing about who was looking.
- *One retention window.* Either operational noise is kept for seven years or a financial decision is lost after two.
- *Failing an action when its audit write fails.* Losing a payout to protect a log entry.
- *Using the audit trail as an event log.* A billion rows and no signal.

## 3. Requirements

### ET-PLT-009-R1 · The auditable-action registry is closed

THE SYSTEM SHALL record exactly the §4 actions and SHALL NOT audit others.

**Acceptance**
- [ ] Every action in §4 writes a row when it occurs, asserted per action
- [ ] Every audit row's `action` is a registry value
- [ ] An `@Audited` annotation marks each auditable service method, and a test asserts the annotated set equals the registry
- [ ] Adding an action changes this spec's §4 in the same commit
- [ ] No routine read or ordinary mutation writes an audit row
- [ ] Every acceptance criterion elsewhere in this corpus that says *writes an audit row* names an action in §4 — a cross-reference test asserts it

### ET-PLT-009-R2 · Every row records who, what, when, to what and why

THE SYSTEM SHALL record the five mandatory components on every audit row.

**Acceptance**
- [ ] `actorId`, `actorRole`, `action`, `occurredAt`, `subjectType`, `subjectId` are non-null on every row
- [ ] `reason` is non-null for every action the registry marks `reasonRequired`
- [ ] `occurredAt` comes from the injected `Clock` ([ET-PLT-001](../001-runtime-baseline/) R3)
- [ ] `correlationId` links the row to the request that caused it ([ET-ADM-005](../../admin/005-observability-and-health/) R4)
- [ ] A system-initiated action records `actorId = "system:{component}"` rather than null
- [ ] `sourceIp` and `userAgent` are recorded for actor-initiated actions
- [ ] A test asserts every registry action produces a complete row

### ET-PLT-009-R3 · The trail is append-only and tampering is detectable

THE SYSTEM SHALL permit only inserts, and SHALL make undetected modification infeasible.

**Acceptance**
- [ ] No service, repository or mutation updates or deletes an `identity_audit_logs` document
- [ ] Each row carries `previousHash` and `rowHash`, chaining per `{subjectType, subjectId}`
- [ ] `rowHash = SHA-256(previousHash ‖ canonical row content)`
- [ ] `verifyAuditChain(subjectType, subjectId)` recomputes the chain and reports the first broken link
- [ ] A scheduled verification checks a sample and alerts on a break
- [ ] A test modifies a row directly in the database and asserts verification detects it
- [ ] The chain survives the retention purge — purging a prefix records a checkpoint hash rather than orphaning the chain

### ET-PLT-009-R4 · State changes record before and after, redacted

WHEN an audited action changes state, THE SYSTEM SHALL record the previous and new values,
redacted per the PII inventory.

**Acceptance**
- [ ] `previousValue` and `newValue` are recorded for every state-changing action
- [ ] Any field marked `DIRECT_IDENTIFIER` or `SENSITIVE` in [ET-PLT-008](../008-data-protection/) §4 is recorded redacted, never in full
- [ ] A bank account change records that it changed and the masked forms, never two full numbers
- [ ] A status change records both statuses in full — they are not PII
- [ ] A monetary change records both amounts in full
- [ ] A test audits a bank-account change and asserts no full account number is present

### ET-PLT-009-R5 · Reading the trail is audited, once

WHEN the audit trail is read, THE SYSTEM SHALL record the read, and SHALL NOT audit that
recording.

**Acceptance**
- [ ] Every `auditLogs` query writes an `AUDIT_TRAIL_READ` row naming the filter used
- [ ] That row does not itself produce a further row — the recursion stops at one level
- [ ] Reading the trail requires `ADMIN`; reading financial actions requires `FINANCE`
- [ ] An actor cannot filter the trail to exclude their own actions
- [ ] Export of the trail requires `SUPER_ADMIN` and is audited
- [ ] A test reads the trail and asserts exactly one read row results

### ET-PLT-009-R6 · Retention differs by class and the purge is safe

THE SYSTEM SHALL retain financial and security actions for seven years and others for two,
and the purge SHALL preserve the chain.

**Acceptance**
- [ ] Registry rows classed `FINANCIAL` or `SECURITY` retain for `audit.retention.long` (P7Y)
- [ ] All others retain for `audit.retention.short` (P2Y)
- [ ] Retention is enforced by a purge job, not a blanket TTL index — the class determines the window
- [ ] A purge writes a checkpoint row carrying the hash of the last purged row, so the chain remains verifiable
- [ ] The purge is bounded per run and reports what it removed
- [ ] A test purges a prefix and asserts the chain still verifies from the checkpoint

### ET-PLT-009-R7 · Audit failure never fails the action

IF an audit write fails, THEN THE SYSTEM SHALL complete the action and raise an alert.

**Acceptance**
- [ ] Where the action has a transaction, the audit row is written inside it
- [ ] Where it does not, the row is written asynchronously with a bounded retry
- [ ] A failure after the retries raises an alert and records a gap marker — the trail says a row is missing rather than being silently short
- [ ] No audited action throws because its audit write failed
- [ ] A test forces an audit failure on a payout approval and asserts the payout completes and an alert fires
- [ ] The audit-failure rate is a metric ([ET-ADM-005](../../admin/005-observability-and-health/) §4)

## 4. Model

### Auditable-action registry

**Security** — 7-year retention.

| Action | Subject | Reason required | Introduced by |
|---|---|---|---|
| `PERMISSION_PLATFORM_OVERRIDE` | any | no | [ET-ORG-003](../../organization/003-permission-resolution/) R4 |
| `TENANT_BOUNDARY_CROSSED` | any | no | [ET-PLT-007](../007-security-and-authorization/) R4 |
| `PROVIDER_DETAIL_VIEWED` | payment intent | no | [ET-ADM-003](../../admin/003-transaction-recovery/) R5 |
| `AUDIT_TRAIL_READ` | — | no | this spec R5 |
| `AUDIT_TRAIL_EXPORTED` | — | **yes** | this spec R5 |
| `USER_SUSPENDED` | user | **yes** | [ET-IDN-002](../../identity/002-keycloak-user-sync/) |
| `PHONE_NUMBER_CHANGED` | user | no | [ET-IDN-002](../../identity/002-keycloak-user-sync/) R6 |
| `ROLE_GRANTED` / `ROLE_REVOKED` | user | **yes** | [ET-PLT-007](../007-security-and-authorization/) |

**Financial** — 7-year retention.

| Action | Subject | Reason required | Introduced by |
|---|---|---|---|
| `MANUAL_LEDGER_ADJUSTMENT` | journal entry | **yes** | [ET-FIN-001](../../finance/001-escrow-and-ledger/) R8 |
| `OPERATIONS_TRANSFER` | platform account | **yes** | [ET-FIN-001](../../finance/001-escrow-and-ledger/) R7 |
| `ESCROW_SUSPENDED` / `ESCROW_REACTIVATED` / `ESCROW_CLOSED` | escrow | **yes** | [ET-FIN-001](../../finance/001-escrow-and-ledger/) |
| `COMMISSION_RECOGNISED_MANUALLY` | event | **yes** | [ET-FIN-002](../../finance/002-commission/) |
| `PAYOUT_APPROVED` / `PAYOUT_REJECTED` | payout | **yes** | [ET-FIN-003](../../finance/003-payouts-and-settlement/) R3 |
| `PAYOUT_RETRIED` | payout | **yes** | [ET-FIN-003](../../finance/003-payouts-and-settlement/) R7 |
| `BANK_ACCOUNT_CHANGED` | bank account | no | [ET-FIN-003](../../finance/003-payouts-and-settlement/) R4 |
| `REFUND_APPROVED` / `REFUND_REJECTED` | refund | **yes** | [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) R5 |
| `CHARGEBACK_ACCEPTED` / `CHARGEBACK_CONTESTED` | chargeback | **yes** | [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) R8 |
| `RECONCILIATION_ITEM_RESOLVED` | item | **yes** | [ET-FIN-005](../../finance/005-reconciliation/) R5 |
| `RECONCILIATION_ITEM_WRITTEN_OFF` | item | **yes** | [ET-FIN-005](../../finance/005-reconciliation/) R5 |
| `BANK_SETTLEMENT_RECORDED` | run | **yes** | [ET-FIN-005](../../finance/005-reconciliation/) R4 |
| `RECOVERY_ACTION_PROPOSED` / `RECOVERY_ACTION_CONFIRMED` | item | **yes** | [ET-ADM-003](../../admin/003-transaction-recovery/) R3 |
| `DEAD_LETTER_DISCARDED` | message | **yes** | [ET-ADM-003](../../admin/003-transaction-recovery/) R6 |

**Operational** — 2-year retention.

| Action | Subject | Reason required | Introduced by |
|---|---|---|---|
| `ORGANIZATION_APPROVED` / `_REJECTED` / `_CHANGES_REQUESTED` | organization | **yes** on the latter two | [ET-ORG-001](../../organization/001-organizer-onboarding/) R5 |
| `ORGANIZATION_SUSPENDED` / `_REACTIVATED` | organization | **yes** | [ET-ORG-001](../../organization/001-organizer-onboarding/) R8 |
| `EVENT_APPROVED` / `EVENT_REJECTED` | event | **yes** on rejection | [ET-CAT-001](../../catalog/001-event-lifecycle/) R1 |
| `EVENT_CANCELLED` | event | **yes** | [ET-CAT-001](../../catalog/001-event-lifecycle/) R7 |
| `MEMBER_ROLE_CHANGED` / `MEMBER_REMOVED` | membership | no | [ET-ORG-002](../../organization/002-teams-and-invitations/) R6 |
| `OWNERSHIP_TRANSFERRED` | organization | no | [ET-ORG-002](../../organization/002-teams-and-invitations/) R7 |
| `EVENT_ACCESS_GRANTED` / `_REVOKED` | grant | no | [ET-ORG-003](../../organization/003-permission-resolution/) R5 |
| `TICKET_VALIDATED_MANUALLY` | ticket | **yes** | [ET-TKT-003](../../ticketing/003-validation-and-checkin/) R6 |
| `TICKET_REISSUED` | ticket | no | [ET-TKT-002](../../ticketing/002-ticket-issuance-and-qr/) R5 |
| `CONFIGURATION_CHANGED` | config key | **yes** | [ET-ADM-002](../../admin/002-platform-configuration/) R2 |
| `FEATURE_FLAG_CHANGED` | flag | **yes** | [ET-ADM-002](../../admin/002-platform-configuration/) R5 |
| `ERASURE_REQUESTED` / `ERASURE_EXECUTED` | user | no | [ET-PLT-008](../008-data-protection/) R3 |
| `DATA_EXPORT_REQUESTED` / `_DOWNLOADED` | user | no | [ET-PLT-008](../008-data-protection/) R6 |
| `BULK_EVENT_APPROVAL` | events | **yes** | [ET-ADM-001](../../admin/001-approvals-workbench/) R6 |
| `ALERT_ACKNOWLEDGED` | alert | no | [ET-ADM-005](../../admin/005-observability-and-health/) R8 |

**Fifty actions.** Nothing else is audited.

### The row

`identity_audit_logs`

| Field | Type | Notes |
|---|---|---|
| `_id` | `String` | |
| `action` | `AuditAction` | a registry value |
| `actionClass` | `AuditClass` | `SECURITY`, `FINANCIAL`, `OPERATIONAL` — drives retention |
| `actorId`, `actorRole` | `String` | `system:{component}` for system actions |
| `subjectType`, `subjectId` | `String` | what it was done to |
| `organizationId` | `String` | for tenant-scoped filtering |
| `reason` | `String` | required per the registry |
| `previousValue`, `newValue` | `String` | redacted per [ET-PLT-008](../008-data-protection/) §4 |
| `correlationId` | `String` | [ET-ADM-005](../../admin/005-observability-and-health/) R4 |
| `sourceIp`, `userAgent` | `String` | actor-initiated only |
| `previousHash`, `rowHash` | `String` | the chain |
| `occurredAt` | `Instant` | from the `Clock` |

### The chain

```
rowHash = SHA-256( previousHash ‖ action ‖ actorId ‖ subjectType ‖ subjectId
                   ‖ previousValue ‖ newValue ‖ occurredAt )

chained per {subjectType, subjectId}
first row for a subject: previousHash = GENESIS
```

Per-subject rather than global, because a global chain serialises every audit write in the
platform through one predecessor lookup. Per-subject chains are independently verifiable and
write in parallel.

A purge writes a `CHAIN_CHECKPOINT` row carrying the hash of the last purged row, so
verification resumes from the checkpoint rather than failing at a gap.

### Redaction

Values are recorded through [ET-PLT-008](../008-data-protection/) §4's inventory:

| Field class | Recorded as |
|---|---|
| `DIRECT_IDENTIFIER` | `[redacted]` |
| `SENSITIVE` | `[redacted]`, or a masked suffix where the inventory allows |
| `INDIRECT_IDENTIFIER` | the id only |
| everything else — statuses, amounts, dates, flags | **in full** |

Statuses and amounts in full is the point: a change from `PENDING_REVIEW` to `ACTIVE`, or
from K0 to K28,500, is exactly what an investigator needs.

### GraphQL

Subgraph `identity`. Every field `@tag(name: "admin")`.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `auditLogs(filter, page)` | query | `ADMIN` | `AuditLogPage!` |
| `auditLogsForSubject(subjectType, subjectId)` | query | `ADMIN` | `[AuditLog!]!` |
| `myAuditTrail(page)` | query | `AUTHENTICATED` | `AuditLogPage!` |
| `verifyAuditChain(subjectType, subjectId)` | query | `SUPER_ADMIN` | `ChainVerification!` |
| `exportAuditTrail(filter)` | mutation | `SUPER_ADMIN` | `AuditExport!` |

`myAuditTrail` shows a user the actions taken **on their own account** — a legitimate
transparency right and the one audit surface that is not `ADMIN`.

Financial actions are visible only to `FINANCE` and above; the filter silently excludes them
for a plain `ADMIN` rather than refusing, so an ordinary admin sees a complete operational
trail without knowing what they cannot see.

### Sweeps

| Sweep | Lock | Cadence | Purpose |
|---|---|---|---|
| chain verification | `lock:sweep:audit-verify` | daily | sample and verify |
| retention purge | `lock:sweep:audit-purge` | weekly | R6, with checkpoints |

### Configuration

| Property | Value |
|---|---|
| `audit.retention.long` | `P7Y` — `SECURITY`, `FINANCIAL` |
| `audit.retention.short` | `P2Y` — `OPERATIONAL` |
| `audit.verification.sample-size` | 1,000 subjects per run |
| `audit.purge.batch-size` | 10,000 |
| `audit.write.max-retries` | 3 |

### Error codes

None introduced. An audit failure is an alert, not a client error.

## 5. Tasks

- [ ] **T1 · The registry, the `@Audited` annotation and the cross-reference test**
  - requirements: R1
  - files: `backend/shared-library/.../audit/`
  - verify: the annotated set equals the registry; every *writes an audit row* elsewhere resolves
  - parallel-safe: no
  - depends: —

- [ ] **T2 · The row, its five mandatory components and the `Clock`**
  - requirements: R2
  - files: `backend/identity-service/.../domain/model/AuditLog.java`
  - verify: every registry action produces a complete row
  - parallel-safe: no
  - depends: T1

- [ ] **T3 · The per-subject hash chain and its verification**
  - requirements: R3
  - files: `backend/identity-service/.../service/impl/AuditServiceImpl.java`
  - verify: a directly modified row is detected; chains write in parallel
  - parallel-safe: no
  - depends: T2

- [ ] **T4 · Redaction driven by the PII inventory**
  - requirements: R4
  - files: `backend/shared-library/.../audit/AuditRedactor.java`
  - verify: a bank-account change records no full number; statuses and amounts are in full
  - parallel-safe: yes
  - depends: T2

- [ ] **T5 · Read auditing, with the recursion stopped at one level**
  - requirements: R5
  - files: `backend/identity-service/.../web/graphql/query/AuditQueryResolver.java`
  - verify: one read yields exactly one read row; an actor cannot filter out their own actions
  - parallel-safe: yes
  - depends: T3

- [ ] **T6 · Class-based retention, the purge and the checkpoint**
  - requirements: R6
  - files: `backend/identity-service/.../scheduler/AuditPurgeSweeper.java`
  - verify: the chain verifies from a checkpoint after a purge
  - parallel-safe: yes
  - depends: T3

- [ ] **T7 · Write-failure isolation, the gap marker and the alert**
  - requirements: R7
  - files: `backend/shared-library/.../audit/AuditWriter.java`
  - verify: a forced failure on a payout approval completes the payout and alerts
  - parallel-safe: yes
  - depends: T2

- [ ] **T8 · The subgraph half, including `myAuditTrail`**
  - requirements: R5
  - files: `backend/identity-service/src/main/resources/graphql/schema.graphqls`
  - verify: a plain `ADMIN` sees no financial action; a user sees their own trail
  - parallel-safe: no — shared SDL across identity's specs
  - depends: T5

## 6. Out of scope

| Capability | Spec |
|---|---|
| Metrics and alerting, which answer *how much* rather than *who* | [ET-ADM-005](../../admin/005-observability-and-health/) |
| The PII inventory that drives redaction | [ET-PLT-008](../008-data-protection/) |
| Application logging and its retention | operations, not a spec in this corpus |
| The journal, which is a financial record and not an audit trail | [ET-FIN-001](../../finance/001-escrow-and-ledger/) |
| The approval timeline, which is a workflow record | [ET-ADM-001](../../admin/001-approvals-workbench/) |
| Who holds `audit:view` | [ET-ORG-003](../../organization/003-permission-resolution/) |

Deliberately never in scope: **auditing every mutation** (unsearchable, and a write
amplification nobody budgeted), **append-only by convention** (the people who can violate it
are the people it is about), and **failing an action when its audit write fails** (losing a
payout to protect a log entry).
