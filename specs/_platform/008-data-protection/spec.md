# ET-PLT-008 · PII inventory, erasure and the retention schedule

## 1. Capability

This platform holds a great deal of information about people who are not its customers in
any commercial sense. A ticket buyer gives a phone number. An organizer gives a national ID,
a tax certificate and a bank account. A steward's name sits on every check-in they
performed. A webhook receipt keeps an MSISDN for ninety days. None of that was collected
carelessly, and all of it has to be findable, protectable and — when somebody asks — erasable.

This spec makes that possible. It declares the **PII inventory**: every field in every
collection that identifies a person, what it is for, how long it is kept and what happens to
it at the end. It declares **erasure** — the request, the thirty-day grace period, and the
crucial distinction between deleting a person's identity and destroying the financial record
they are part of. And it declares **retention**, because data kept forever with no stated
purpose is the finding an auditor writes up.

The rule that makes erasure tractable is that **a person is erased by anonymisation, not by
deletion**. A refunded ticket's journal entries must survive; a purchaser's name must not.
So the ticket keeps its id, its amounts and its links, and the person it pointed at becomes
an anonymised subject. The books stay balanced and the person is gone.

## 2. Design decisions

**Erasure is anonymisation, not deletion, everywhere money is involved.** Deleting a user
orphans tickets, payments, journal lines and reconciliation items — and a ledger that cannot
resolve a party is a ledger that fails an audit. The user document is retained with every
identifying field replaced and a tombstone marker; everything pointing at it still resolves.

**Anonymisation is irreversible and is proven to be.** Fields are overwritten with a
deterministic non-reversing token, not encrypted with a key somebody could keep. A test
asserts the original value is unrecoverable from the document, from any index, and from any
log.

**A thirty-day grace period, cancellable by the user, then automatic.** People change their
minds and people are pressured. Thirty days is long enough to reconsider, short enough to be
a real commitment, and the execution is automatic so that it does not depend on somebody
remembering.

**Financial records are retained for seven years and are exempt from erasure.** Journal
entries, payments, payouts and reconciliation items are kept as a legal obligation. They are
anonymised of personal identifiers where they carry any, and the amounts, dates and
references remain. The exemption is stated so that an erasure request is answered honestly
rather than quietly incompletely.

**Every PII field is inventoried, and the inventory is the erasure plan.** §4 lists each
field, its classification, its retention and its erasure action. The erasure job is driven
by that table rather than by a hand-written list of updates, so a field added without an
inventory row is a field erasure misses — and a lint catches it.

**Some PII is deleted rather than anonymised, because it has no downstream reference.**
Notification bodies, webhook raw payloads, OTP codes, device tokens. Nothing points at them
and nothing needs them after their retention window, so they are removed outright by TTL.

**Export is a right and it is a job, not a query.** A subject access request returns
everything the platform holds about a person, assembled asynchronously and delivered as a
download with a short-lived link. Doing it synchronously means a GraphQL query that scans
eight collections.

**Consent is recorded where it is required, with when and how.** Marketing is opt-in
([ET-NTF-001](../001-notification-transport/) §4) and the record of that opt-in — the
timestamp, the mechanism and the version of the terms — is itself retained.

**Rejected alternatives**

- *Deleting the user document on erasure.* Orphans every financial record that references it.
- *Encrypting PII with a per-user key and destroying the key (crypto-shredding).* Elegant, and it makes every read of every user field a decryption on the hot path, for a platform whose PII volume does not need it.
- *Immediate erasure with no grace period.* Irreversible, and people are pressured into it.
- *A hand-written erasure routine.* A field added later is a field erasure misses, silently.
- *Anonymising financial records.* The ledger cannot resolve a party and fails an audit.
- *Synchronous subject access export.* A query that scans eight collections while somebody waits.
- *Keeping everything forever "just in case".* The finding an auditor writes up.

## 3. Requirements

### ET-PLT-008-R1 · Every PII field is inventoried

THE SYSTEM SHALL maintain the §4 inventory covering every field that identifies a person.

**Acceptance**
- [ ] Every field in §4 names its collection, classification, purpose, retention and erasure action
- [ ] Classifications are `DIRECT_IDENTIFIER`, `INDIRECT_IDENTIFIER`, `SENSITIVE`, `FINANCIAL`
- [ ] A `@Pii` annotation marks each such field in code, and a test asserts every annotated field has an inventory row and vice versa
- [ ] A field added without an inventory row fails the build
- [ ] The inventory is queryable by `SUPER_ADMIN` so a data-protection question is answered from the platform, not from a spreadsheet
- [ ] No PII field appears in a cross-service event payload ([ET-PLT-003](../003-event-contract/) R3)

### ET-PLT-008-R2 · Erasure is anonymisation, and it is irreversible

WHEN an erasure executes, THE SYSTEM SHALL overwrite every identifying field
irreversibly and SHALL retain the document.

**Acceptance**
- [ ] Each inventory row's erasure action is applied exactly as declared
- [ ] Anonymised fields are overwritten with a non-reversing token — `anonymised-{subjectToken}` — never encrypted
- [ ] The original value is unrecoverable from the document, from any index, and from any log — asserted by a test that searches all three
- [ ] `identity_users` is retained with `accountStatus = ERASED`, so every foreign key still resolves
- [ ] A ticket belonging to an erased user still resolves its event, tier, amounts and journal entries
- [ ] Re-running erasure for a subject is a no-op
- [ ] The subject token is derived from the user id and a platform salt, and is stable so that duplicate detection still works

### ET-PLT-008-R3 · A request has a grace period and is then automatic

WHEN a user requests deletion, THE SYSTEM SHALL wait the grace period, permit cancellation
throughout, and then execute without human action.

**Acceptance**
- [ ] `requestAccountDeletion` sets `accountStatus = PENDING_DELETION` and `deletionScheduledFor` at `data.retention.grace` (P30D)
- [ ] `cancelAccountDeletion` restores `ACTIVE` at any point before execution
- [ ] The user is notified at the request, at seven days remaining, and at execution
- [ ] A sweep under `lock:sweep:erasure` executes due erasures with no operator action
- [ ] A user in `PENDING_DELETION` may still log in and use the platform — the request is not a suspension
- [ ] Execution is idempotent and resumable; a failure part-way retries from where it stopped
- [ ] An erasure blocked by an open obligation (R4) is reported to the user with the reason and re-attempted

### ET-PLT-008-R4 · Financial and legal obligations survive erasure, honestly

IF a record is subject to a retention obligation, THEN THE SYSTEM SHALL retain it and SHALL
tell the subject.

**Acceptance**
- [ ] Journal entries, journal lines, payments, payouts, refunds, chargebacks and reconciliation items are retained for `data.retention.financial` (P7Y)
- [ ] Those records are anonymised of personal identifiers where they carry any; amounts, dates and references remain
- [ ] `Ledger.assertBalanced()` holds after an erasure, asserted directly
- [ ] An erasure request is answered with what was erased **and** what was retained and why
- [ ] An open obligation — an unsettled payout, an open chargeback, a live ticket for a future event — defers erasure until it closes, and the user is told
- [ ] A test erases a user with a full purchase history and asserts the trial balance is unchanged

### ET-PLT-008-R5 · Short-lived PII is deleted by retention, not by erasure

THE SYSTEM SHALL remove PII with no downstream reference at the end of its retention window.

**Acceptance**
- [ ] Notification bodies and parameters are removed at `data.retention.notifications` (P180D) by TTL
- [ ] Webhook raw payloads are removed at 90 days by TTL ([ET-PLT-002](../002-persistence-baseline/) §4)
- [ ] OTP values are removed at 5 minutes by TTL ([ET-IDN-001](../../identity/001-phone-otp-identity/) §4)
- [ ] Device tokens are deactivated at 90 days of inactivity ([ET-NTF-001](../001-notification-transport/) R8)
- [ ] Audit rows follow [ET-PLT-009](../009-audit-trail/)'s retention, which this spec does not override
- [ ] Every TTL in the platform corresponds to an inventory row stating why that window
- [ ] A test asserts each TTL is configured and fires

### ET-PLT-008-R6 · A subject access request is an asynchronous export

WHEN a subject requests their data, THE SYSTEM SHALL assemble it asynchronously and deliver
it as a bounded download.

**Acceptance**
- [ ] `requestDataExport` creates a job; it does not return the data
- [ ] The export covers every collection holding that subject's data, per §4
- [ ] It is delivered as JSON via a presigned link valid for `data.export.link-ttl` (PT24H)
- [ ] The export excludes another person's PII — a ticket transferred to somebody else shows the transfer, not the recipient's details
- [ ] Generating it does not run on the request path and does not contend with purchases
- [ ] A user may hold one open export request at a time
- [ ] The request and the download are both audited

### ET-PLT-008-R7 · Consent is recorded with its provenance

WHERE consent is required, THE SYSTEM SHALL record when and how it was given.

**Acceptance**
- [ ] Marketing consent records the timestamp, the mechanism, the IP and the terms version
- [ ] Withdrawal is recorded the same way and takes effect immediately
- [ ] The consent record is retained for `data.retention.consent` (P7Y) — proving consent existed outlives the consent
- [ ] Consent records survive erasure, anonymised of the identifier
- [ ] No optional message is sent without a consent record ([ET-NTF-001](../001-notification-transport/) R3)
- [ ] A test asserts a marketing send with no consent record is suppressed

## 4. Model

### PII inventory

**identity-service**

| Collection · field | Class | Retention | On erasure |
|---|---|---|---|
| `identity_users.firstName`, `.lastName` | DIRECT | account life | anonymise |
| `identity_users.email` | DIRECT | account life | anonymise |
| `identity_users.phoneNumber` | DIRECT | account life | anonymise |
| `identity_users.avatarUrl`, `.bio` | INDIRECT | account life | delete |
| `identity_users.dateOfBirth` | SENSITIVE | account life | delete |
| `identity_users.username` | DIRECT | account life | anonymise |
| `identity_organizations.businessPhone`, `.businessEmail`, `.businessAddress` | DIRECT | P7Y | anonymise at P7Y |
| `identity_organizations.taxId`, `.businessRegistrationNumber` | SENSITIVE | P7Y | anonymise at P7Y |
| `identity_verification_documents.fileKey` | SENSITIVE | P7Y | **delete the object**, retain the metadata |
| `identity_team_invitations.email`, `.phoneNumber`, `.inviteeName` | DIRECT | P7D TTL | deleted by TTL |
| `identity_notifications.parameters` | INDIRECT | P180D TTL | deleted by TTL |
| `identity_user_devices.deviceToken` | INDIRECT | P90D inactive | delete |
| `identity_audit_logs.actorId` | INDIRECT | ET-PLT-009 | anonymise |

**booking-service**

| Collection · field | Class | Retention | On erasure |
|---|---|---|---|
| `booking_tickets.ownerId`, `.purchasedById` | INDIRECT | P7Y | **retain** — points at the anonymised user |
| `booking_payment_intents.msisdn` | DIRECT | P7Y | anonymise, keep the last two digits |
| `booking_payment_attempts.providerMessageRaw` | INDIRECT | P90D | delete |
| `booking_webhook_receipts.rawBody` | DIRECT | P90D TTL | deleted by TTL |
| `booking_bank_accounts.accountNumber` | **SENSITIVE** | P7Y | anonymise, keep the last four |
| `booking_bank_accounts.accountHolderName` | DIRECT | P7Y | anonymise |
| `booking_checkins.validatedById` | INDIRECT | P7Y | **retain** — anonymised subject |
| `booking_journal_lines.*` | FINANCIAL | **P7Y, exempt** | retain unchanged |
| `booking_ticket_transfers.recipientPhone`, `.recipientEmail` | DIRECT | P7Y | anonymise |

**catalog-service**

| Collection · field | Class | Retention | On erasure |
|---|---|---|---|
| `catalog_events.createdById` | INDIRECT | P7Y | retain — anonymised subject |
| `catalog_approval_timelines.actorId` | INDIRECT | P7Y | anonymise |
| `catalog_locations.createdById` | INDIRECT | P7Y | retain |

**Retain** means the id keeps pointing at the anonymised `identity_users` document. That is
what keeps the ledger and the audit trail resolvable while the person is gone.

### Erasure actions

| Action | Effect |
|---|---|
| `anonymise` | overwrite with `anonymised-{subjectToken}` |
| `delete` | set to null |
| `retain` | unchanged — the id resolves to an anonymised subject |
| `delete the object` | remove the stored file; keep the metadata row |
| `partial` | keep a stated suffix — last two digits of an MSISDN, last four of an account |

```
subjectToken = base64url(HMAC-SHA256(platformSalt, userId))[0..11]
```

Deterministic, so duplicate detection still works; non-reversing, so the original is gone.
The salt is environment-sourced and is never rotated — rotating it would orphan every
existing token.

### The erasure sequence

```
1  requestAccountDeletion            → PENDING_DELETION, scheduled + P30D, user notified
2  ...grace period...                  cancellable throughout; the account still works
3  at T−7d                             reminder notification
4  at T                               sweep claims it under lock:sweep:erasure
5  check open obligations             unsettled payout · open chargeback · live future ticket
                                      → defer, tell the user, re-attempt
6  per inventory row, per service     apply the declared action
7  identity_users                     → accountStatus = ERASED, erasedAt
8  Keycloak                           delete the user
9  assert                             Ledger.assertBalanced() unchanged
10 record                             an erasure certificate: what was erased, what retained, why
```

Step 5 is why erasure can be honest rather than silently partial. Step 10 is what the
subject receives.

### Open obligations that defer erasure

| Obligation | Source |
|---|---|
| an unsettled payout | [ET-FIN-003](../../finance/003-payouts-and-settlement/) |
| an open chargeback | [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) |
| a live ticket for a future event | [ET-TKT-002](../../ticketing/002-ticket-issuance-and-qr/) |
| an open reconciliation item naming the subject | [ET-FIN-005](../../finance/005-reconciliation/) |
| an organization the subject owns with an active event | [ET-ORG-001](../../organization/001-organizer-onboarding/) |

Each defers rather than blocks — the erasure re-attempts when the obligation closes.

### Documents

`identity_erasure_requests`

| Field | Notes |
|---|---|
| `_id`, `userId` | one open request per user |
| `requestedAt`, `scheduledFor`, `executedAt`, `cancelledAt` | |
| `status` | `PENDING`, `DEFERRED`, `EXECUTING`, `COMPLETED`, `CANCELLED` |
| `deferralReason`, `deferredUntil` | |
| `fieldsErased`, `recordsRetained` | the certificate's content |
| `subjectToken` | |

`identity_data_exports`

| Field | Notes |
|---|---|
| `_id`, `userId`, `status` | `PENDING`, `GENERATING`, `READY`, `EXPIRED`, `FAILED` |
| `fileKey`, `downloadUrl`, `expiresAt` | link TTL `PT24H` |
| `requestedAt`, `completedAt`, `downloadedAt` | |

`identity_consent_records`

| Field | Notes |
|---|---|
| `_id`, `userId`, `consentType` | `MARKETING`, `TERMS`, `PRIVACY_POLICY` |
| `granted` | boolean |
| `mechanism` | `SIGNUP_CHECKBOX`, `SETTINGS_TOGGLE`, `EXPLICIT_REQUEST` |
| `termsVersion`, `ipAddress`, `userAgent` | provenance |
| `recordedAt` | append-only — withdrawal is a new row |

### GraphQL

Subgraph `identity`.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `myErasureRequest` | query | `AUTHENTICATED` | `ErasureRequest` |
| `myDataExports` | query | `AUTHENTICATED` | `[DataExport!]!` |
| `myConsents` | query | `AUTHENTICATED` | `[ConsentRecord!]!` |
| `piiInventory` | query | `SUPER_ADMIN` | `[PiiInventoryEntry!]!` `@tag(name: "admin")` |
| `erasureRequests(status, page)` | query | `SUPER_ADMIN` | `ErasureRequestPage!` `@tag(name: "admin")` |
| `requestAccountDeletion(reason)` | mutation | `AUTHENTICATED` | `ErasureRequest!` |
| `cancelAccountDeletion` | mutation | `AUTHENTICATED` | `Boolean!` |
| `requestDataExport` | mutation | `AUTHENTICATED` | `DataExport!` |
| `recordConsent(input)` | mutation | `AUTHENTICATED` | `ConsentRecord!` |
| `withdrawConsent(consentType)` | mutation | `AUTHENTICATED` | `ConsentRecord!` |
| `executeErasure(userId)` | mutation | `SUPER_ADMIN` | `ErasureRequest!` `@tag(name: "admin")` |

`executeErasure` runs the same idempotent job the sweep runs, for a request that needs
expediting; it does not bypass the obligation checks.

### Configuration

| Property | Value |
|---|---|
| `data.retention.grace` | `P30D` |
| `data.retention.financial` | `P7Y` |
| `data.retention.notifications` | `P180D` |
| `data.retention.consent` | `P7Y` |
| `data.export.link-ttl` | `PT24H` |
| `data.erasure.sweep-interval` | `PT1H` |
| `PII_SUBJECT_SALT` | environment only, **never rotated** |

### Error codes

None introduced. A deferred erasure is a status, not an error.

## 5. Tasks

- [ ] **T1 · The `@Pii` annotation, the inventory and the build check**
  - requirements: R1
  - files: `backend/shared-library/.../privacy/Pii.java`, every annotated field
  - verify: every annotated field has a row and vice versa; an unannotated addition fails
  - parallel-safe: no
  - depends: —

- [ ] **T2 · The subject token, the anonymiser and the irreversibility test**
  - requirements: R2
  - files: `backend/shared-library/.../privacy/Anonymiser.java`
  - verify: the original is unrecoverable from document, index and log
  - parallel-safe: no
  - depends: T1

- [ ] **T3 · The request, the grace period, cancellation and the notifications**
  - requirements: R3
  - files: `backend/identity-service/.../service/impl/ErasureServiceImpl.java`
  - verify: a pending-deletion account still works; cancellation restores at any point
  - parallel-safe: yes
  - depends: T2

- [ ] **T4 · The obligation checks and the deferral path**
  - requirements: R4
  - files: `backend/identity-service/.../service/impl/ErasureObligationChecker.java`
  - verify: each obligation defers and re-attempts on closure
  - parallel-safe: yes
  - depends: T3

- [ ] **T5 · The inventory-driven erasure job across all three services**
  - requirements: R2, R4
  - files: `backend/*/src/main/java/com/pml/*/privacy/ErasureExecutor.java`
  - verify: the trial balance is unchanged; a full purchase history still resolves
  - parallel-safe: no — spans three services
  - depends: T4

- [ ] **T6 · TTLs for short-lived PII, each tied to an inventory row**
  - requirements: R5
  - files: every collection's index initialiser
  - verify: each TTL is configured, fires, and has a row stating its window
  - parallel-safe: yes
  - depends: T1

- [ ] **T7 · Asynchronous export with a presigned link**
  - requirements: R6
  - files: `backend/identity-service/.../service/impl/DataExportService.java`
  - verify: no third-party PII in the export; generation does not contend with purchases
  - parallel-safe: yes
  - depends: T1

- [ ] **T8 · Consent records with provenance, and the suppression rule**
  - requirements: R7
  - files: `backend/identity-service/.../service/impl/ConsentServiceImpl.java`
  - verify: a marketing send with no consent record is suppressed
  - parallel-safe: yes
  - depends: T1

- [ ] **T9 · The erasure certificate and the subgraph half**
  - requirements: R3, R4
  - files: `backend/identity-service/src/main/resources/graphql/schema.graphqls`
  - verify: the certificate states what was erased and what was retained and why
  - parallel-safe: no — shared SDL across identity's specs
  - depends: T5

## 6. Out of scope

| Capability | Spec |
|---|---|
| The audit trail's own retention and immutability | [ET-PLT-009](../009-audit-trail/) |
| The financial records erasure must not touch | [ET-FIN-001](../../finance/001-escrow-and-ledger/) |
| Keycloak deletion mechanics | [ET-IDN-002](../../identity/002-keycloak-user-sync/) |
| Notification retention windows | [ET-NTF-001](../001-notification-transport/) |
| Document storage and presigned URLs | [ET-ORG-001](../../organization/001-organizer-onboarding/) |
| Encryption at rest for bank accounts | [ET-FIN-003](../../finance/003-payouts-and-settlement/) |
| Access control over PII | [ET-PLT-007](../007-security-and-authorization/) |

Deliberately never in scope: **deleting the user document** (it orphans every financial
record), **crypto-shredding with per-user keys** (a decryption on every read for a PII volume
that does not need it), and **erasing financial records** (the ledger cannot then resolve a
party and fails an audit).
