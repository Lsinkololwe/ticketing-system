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
delete, no mutation path. A hash chain linking each row to the previous one for its subject
would make tampering by direct database access detectable — which matters because the people who
could tamper are the people the trail is about. *The chain is deferred past launch by the owner
(2026-10-13); see R3.*

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

**Audit rows are kept indefinitely.** *(Decided 2026-10-13, replacing the original seven-year and
two-year classes.)* The trail is evidence; nothing purges, expires or archives a row, and the
collection carries no expiry index so that a later change cannot start deleting it by accident.

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
- [x] No service, repository or mutation updates or deletes an `identity_audit_logs` document — `PlatformWideAuditTrailTest` (`noCodeChangesOrRemovesARow`, `noRepositoryForTheTrail`, `noMutationTouchesTheTrail`)
- [ ] Each row carries `previousHash` and `rowHash`, chaining per `{subjectType, subjectId}` — *deferred past launch by the owner on 2026-10-13; see the decision under R3*
- [ ] `rowHash = SHA-256(previousHash ‖ canonical row content)` — *deferred past launch by the owner on 2026-10-13; see the decision under R3*
- [ ] `verifyAuditChain(subjectType, subjectId)` recomputes the chain and reports the first broken link — *deferred past launch by the owner on 2026-10-13; see the decision under R3*
- [ ] A scheduled verification checks a sample and alerts on a break — *deferred past launch by the owner on 2026-10-13; see the decision under R3*
- [ ] A test modifies a row directly in the database and asserts verification detects it — *deferred past launch by the owner on 2026-10-13; see the decision under R3*
- [ ] The chain survives the retention purge — purging a prefix records a checkpoint hash rather than orphaning the chain — *deferred past launch by the owner on 2026-10-13; see the decision under R3*

> **Decided 2026-10-13 (owner): tamper evidence is not required before launch.** The hash chain, its
> verification and the scheduled check are *deferred past launch*; the boxes above stay open. What
> holds today is that the application offers no way to alter or delete a row (the box above, proved by
> `PlatformWideAuditTrailTest`); tampering at the database level is out of scope for launch. Nothing
> in the collection or the row shape prevents adding the chain later.

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

### ET-PLT-009-R6 · Audit rows are kept indefinitely

> **Decided 2026-10-13 (owner): audit rows never expire.** This replaces the class-based retention
> this requirement first described (seven years for financial and security actions, two for the
> rest, enforced by a purge job). The expiry index the collection carried was attached to a field no
> audit row ever set, so it never removed anything; it is gone, and so are the retention properties
> and the purge Schedule, so that nothing can start deleting evidence by accident.

THE SYSTEM SHALL keep every audit row for as long as the platform runs and SHALL NOT purge, expire or
archive one.

**Acceptance**
- [x] The audit collection has no expiry index, declared or live: no index of `identity_audit_logs` carries `expireAfterSeconds` — `IdentityIndexRegistryTest.auditRowsNeverExpire`
- [x] An expiry index an earlier version left on the collection is dropped at start-up, and its absence is not an error — `IdentityIndexRegistryTest.legacyAuditExpiryIsDropped`
- [x] The persistence registry declares no TTL on the collection and says so — `IdentityIndexRegistryTest.declarationsMatchTheSpecification`
- [ ] ~~Registry rows classed `FINANCIAL` or `SECURITY` retain for `audit.retention.long`~~ — superseded by the decision above
- [ ] ~~All others retain for `audit.retention.short`; a purge job with a checkpoint row~~ — superseded by the decision above

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
| `CONTACT_CHANGED` | account | no | [ET-IDN-004](../../identity/004-accounts-and-contacts/) R5 — replaces `PHONE_NUMBER_CHANGED`; records contact type and masked values only |
| `ACCESS_REVOKED` | account or token | **yes** | [ET-IDN-003](../../identity/003-token-revocation/) R5 — written only when a platform administrator revokes by hand; automatic triggers and a person's own sign-out are not audited here |
| `ACCOUNT_DELETED` | account | no | [ET-IDN-004](../../identity/004-accounts-and-contacts/) R7 — the tombstone step of an erasure |
| `ACCOUNT_ADOPTED` | account | no | [ET-IDN-004](../../identity/004-accounts-and-contacts/) R4 — a Keycloak user adopted into an account |
| `ACCOUNT_REPAIRED` | account | no | [ET-IDN-004](../../identity/004-accounts-and-contacts/) R4 — a security heal (`enabled`, roles) of drift class D4/D5 |
| `SIGN_IN_REFUSED` | account | no | [ET-IDN-001](../../identity/001-phone-otp-identity/) R6/R7 — suspended, merging, locked, or staff in the contact flow; never records a code |
| `STAFF_SIGNED_IN` | account | no | [ET-IDN-001](../../identity/001-phone-otp-identity/) R7 — platform staff in `myticketzm-admin`; buyer sign-ins are Keycloak events, not audit rows |
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

**Fifty actions as first written, plus the identity actions added 2026-10-04 (F-044): `PHONE_NUMBER_CHANGED` is replaced by `CONTACT_CHANGED`, and `ACCESS_REVOKED` (an administrator's manual revocation), `ACCOUNT_DELETED`, `ACCOUNT_ADOPTED`, `ACCOUNT_REPAIRED`, `SIGN_IN_REFUSED`, `STAFF_SIGNED_IN` are added (56 in all). `ACCOUNT_MERGED` is not in the registry: accounts are not merged (F-049).** Nothing else is audited.

**No personal data in audit rows or in ids (D-46).** `subjectId` is the account id; `previousValue` and `newValue` for contact actions hold masked values; the audit write never receives a raw contact, a code, a proof or a login handle.

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

> **Decided 2026-10-13 (owner), for platform-wide reaches.** An administrator keeps the whole
> trail, money reaches included, and finance gets its own narrower money view (R10). The
> exclusion above is therefore not applied to `TENANT_BOUNDARY_CROSSED` rows; it remains the
> design for the financial-action rows of the `@Audited` registry, which are not built.

### Schedules

Recurring audit work is a Temporal Schedule starting `AuditMaintenanceWorkflow` on
`identity-onboarding` ([ET-PLT-015](../015-durable-execution/) §4). Overlap policy `SKIP` is the
mutex; no Redis lock exists.

| Schedule | Runs | Cadence (UTC) | Overlap | Purpose |
|---|---|---|---|---|
| `audit-verify` | `AuditMaintenanceWorkflow(VERIFY)` | daily 03:00 | `SKIP` | sample and verify — *deferred with the hash chain (R3)* |
| `audit-purge` | — | — | — | **not to be built**: audit rows are kept indefinitely (R6, decided 2026-10-13) |

### Configuration

| Property | Value |
|---|---|
| `audit.retention.long`, `audit.retention.short`, `audit.purge.batch-size` | **removed**: audit rows are kept indefinitely (R6, decided 2026-10-13) |
| `audit.verification.sample-size` | 1,000 subjects per run |
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
  - files: `backend/identity-service/.../workflow/audit/AuditMaintenanceWorkflowImpl.java`, `.../workflow/audit/AuditSchedules.java`
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

---

## Amendment, 2026-10-04 — reading the trail

### ET-PLT-009-R9 · The audit query

**Acceptance**
- [ ] `auditLogs(filter, pagination)` is ADMIN or SUPER_ADMIN and filters by time range, action, actor, resource and status; rows carry ids and non-personal metadata only
- [ ] Administrator actions added by the 2026-10-04 amendments (commission, payout-account review, suspension, deletion requests, session revocation, alert acknowledgement, announcements) each write an audit row with the actor

---

## Amendment, 2026-10-13 — platform-wide reaches

Platform staff acting across organizers or buyers are recorded as searchable audit rows, not
only as a log line. The registry value is the §4 `TENANT_BOUNDARY_CROSSED`; the row carries the
actor, the role they acted under, the service, the fixed reason, the operation and the time, and
never a request, a filter value, a token or a contact detail.

### ET-PLT-009-R10 · A platform-wide reach is a searchable row

**Acceptance**
- [x] Every use of the platform-wide path — administrator, super administrator and finance alike — writes one `TENANT_BOUNDARY_CROSSED` row with `performedBy`, `actorRole`, `service`, `reason`, `operation` and `timestamp`; one request is one row per distinct reason and operation — `PlatformWideAuditTrailTest`, `PlatformStaffAccessTest`
- [x] Booking and catalog deliver the row to identity over `POST /api/internal/audit/platform-wide`; identity stores its own receipt time beside the time the sender says it happened and refuses any body outside the closed shapes — `PlatformWideAuditEndToEndTest`, `PlatformWideAuditForwarderTest`
- [x] A refusal leaves no row; an audit store that is down, slow or refusing does not block the staff member, is logged as an error carrying the same facts, and is counted on `platform.wide.audit.failures` — `PlatformStaffAccessTest`, `PlatformWideAuditTrailTest`
- [x] `auditLogs` filters by `service` and `reason` as well as time range and actor, gives ADMIN and SUPER_ADMIN the whole trail, refuses an oversized page with `PAGE_SIZE_EXCEEDED`, and each such read leaves one `TENANT_BOUNDARY_CROSSED` row with reason `AUDIT_TRAIL_READ` — `PlatformWideAuditEndToEndTest`
- [x] Finance reads the money view of the same query: rows whose reason is in the fixed money set (bank accounts, refund-request cancellation, buyer bookings and reservation support reads, platform sales analytics, recovery propose and confirm), by any actor, and no other row. The restriction is applied by the server from the caller's role to whatever filter is sent, so a filter can only narrow it; a reason, actor, service or action outside the set answers an empty page. Account events are never merged into it. Finance's own read leaves one row with reason `FINANCE_AUDIT_VIEW_READ`, which finance cannot see. Organizers, customers and scanners are refused with no row — `FinanceAuditViewTest`, `PlatformWideAuditEndToEndTest`
- [x] No code path updates or removes an audit row, no repository exists for the collection and no mutation names it — `PlatformWideAuditTrailTest`
- [ ] The row records the filter used on an `AUDIT_TRAIL_READ` (R5): the reason is a fixed value and no request data is carried, so a read is recorded as a read, not as which filter it used
- [ ] The row is hash-chained (R3): deferred past launch by the owner on 2026-10-13; the application offers no way to alter or delete a row, and database-level tampering is out of scope for launch
- [x] The row is kept indefinitely (R6, decided 2026-10-13): the inert expiry index is removed and a test pins that the collection has none — `IdentityIndexRegistryTest.auditRowsNeverExpire`

### ET-PLT-009-R11 · A reach is never lost because identity is unavailable

> **Decided 2026-10-13 (owner).** Booking and catalog keep each reach locally first and forward it
> until identity confirms, so no platform-wide action is missing from the searchable trail. The
> administrator is never blocked or slowed by the audit store: the local write is one insert into the
> service's own database, and if even that fails the reach still goes through (an error line carrying
> the facts, and a count on `platform.wide.audit.failures`).

THE SYSTEM SHALL write every platform-wide reach of booking and catalog to the service's own outbox
before anything leaves the service, and SHALL deliver it to identity until identity confirms.

**Acceptance**
- [x] The reach is a row of `booking_platform_wide_audit_outbox` / `catalog_platform_wide_audit_outbox` holding the facts of `PlatformWideAuditRecord`, a permanent `recordId` (the `_id`, a UUID), the time it happened and a delivery state `PENDING`, `DELIVERED` or `REFUSED`; the collection's validator refuses any other property, so no request body, token or free text can be stored — `PlatformWideAuditOutboxTest`, `OutboxPlatformWideAuditSinkTest`
- [x] Delivery is `PlatformWideAuditDeliveryWorkflow`, id `audit-delivery/{recordId}`, started right after the local write on the service's own audit queue; one activity retried without an attempt limit from 5 s doubling to a 15 min ceiling; no `@Scheduled`, no Schedule, no poller — `PlatformWideAuditDeliveryRulesTest`, `PlatformWideAuditDeliveryTest`, `BookingAuditDeliveryTest`, `CatalogAuditDeliveryTest`, `TemporalRegistryLintTest`, `WorkflowDeterminismLintTest`
- [x] Identity down at the moment of the reach: the row is stored locally `PENDING`, the reach is not held up, and when identity returns the row is delivered exactly once and appears in `auditLogs` with the original time — `PlatformWideAuditEndToEndTest.reachSurvivesAnIdentityOutage`, `PlatformWideAuditDeliveryTest.deliveredOnceAfterTheOutage`
- [x] A duplicate delivery is a no-op that is still answered as accepted: identity holds one row per `recordId` — `PlatformWideAuditTrailTest`, `PlatformWideAuditEndToEndTest` (`duplicateDeliveryIsOneRow`, `repeatedDeliveryAfterStorageIsOneRow`), `IdentityIndexRegistryTest.oneRowPerRecordId`
- [x] Identity accepts `occurredAt` only from thirty days before receipt to two minutes after it, and always stores its own `receivedAt` beside it; both are in the row and in `auditLogs` (`at`, `receivedAt`); a body outside the closed shape or the window is refused and stores nothing — `PlatformWideAuditEndToEndTest` (`occurredAtIsBounded`, `recordIdIsRequired`, `extraFieldsAreNeverStored`, `internalWriteIsClosed`)
- [x] A start that fails (Temporal unreachable) leaves the row `PENDING`; it is started again once, when the service next starts, by a bounded pass that is not a sweep — `PlatformWideAuditDrainTest`, `PlatformWideAuditDeliveryTest.temporalDownAtStartIsRedrivenAtNextStart`, `PlatformWideAuditEndToEndTest.temporalDownAtTheStart`
- [x] An outbox write that fails does not block the administrator, and is logged as an error and counted — `OutboxPlatformWideAuditSinkTest.outboxWriteFailureDoesNotBlock`; identity's own reaches follow the same rule — `PlatformWideAuditTrailTest.storeRefusalIsCounted`
- [x] A delivery identity refuses for good (400 or 422) settles the row `REFUSED`, keeps it, logs an error and counts it; any other failure is retried — `PlatformWideAuditForwarderTest`
- [x] Operators can watch the backlog: `platform.wide.audit.outbox.pending`, `platform.wide.audit.outbox.oldest.age.seconds`, `platform.wide.audit.delivered`, `platform.wide.audit.delivery.failed` and `platform.wide.audit.delivery.start.failures`, each tagged by service — `PlatformWideAuditMetricsTest`, `PlatformWideAuditForwarderTest`, `PlatformWideAuditDrainTest`
- [ ] The residual window: a reach whose workflow start failed and whose service then never restarts stays `PENDING` until the next start (visible on the age gauge); a service that is lost together with its database before delivery loses the row. Both are accepted limits, not closed ones
