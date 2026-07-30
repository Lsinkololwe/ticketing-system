# ET-ORG-001 · Organizer onboarding — nine states, staged access, the approval saga

> **Conformance** · US Part I §4 onboarding stages · US Part II §8, §12 organization and profile CRUD · US Part IV §20 approval saga · US Part IV §21 state machines

## 1. Capability

Anybody may buy a ticket. Selling one means taking other people's money and promising them
an event, so before the platform lets somebody do that it wants to know who they are, that
the business exists, that it is registered, and that there is a bank account with a name on
it. That verification is the single highest-friction moment in the whole product: it stands
between a person who wants to sell tickets and a platform that wants them to, and every
step of it is a place they give up.

This spec builds that path, and it is built to be finishable. The organization exists from
the first click, as a draft, so nothing is lost between sessions. Access is **staged**
rather than all-or-nothing: an applicant under review may already build a draft event, so
the wait for a human reviewer is spent doing something useful rather than staring at a
holding page. A rejection is not the end — the applicant may re-apply. And *changes
requested* is a distinct outcome from *rejected*, because "your tax certificate is
illegible" and "we do not believe this business exists" are different messages and produce
different behaviour.

It declares one document, one status enum, nine states and fifteen transitions — because an
organization that is simultaneously an application and a tenant is one thing with one
lifecycle, and modelling it as two produces the perennial question of what an approved
application with an inactive organization means.

And it declares the approval itself as an explicit, compensating saga. Approval is not one
write: it activates the organization, makes the applicant its owner, upgrades their user
type, grants a realm role, creates five Keycloak groups and sends a welcome. Five of those
six steps are in a different system from the first, and the platform must never end up with
an active organization whose owner cannot log into it.

## 2. Design decisions

**One document, one lifecycle.** `identity_organizations` is both the application and the
tenant, and `OrganizationStatus` is one enum covering both halves. The alternative — a
separate `organizer_profiles` application document that spawns an organization on approval
— produces two ids for one business, two status fields whose legal combinations nobody
enumerates, and the recurring question of what an `APPROVED` profile pointing at an
`INACTIVE` organization means.

**`APPROVED` is not a state; `ACTIVE` is.** Approval is the transition, not the
destination. Having both means answering what distinguishes an approved organization from
an active one, and the honest answer is nothing — so there is one state, and the approval
is recorded as `approvedAt` and `reviewedById` on the row.

**Fifteen transitions, in one table, consulted by every mutation.** `OrganizationTransitions.LEGAL`
holds them, every mutation asks `next(current, action)`, and a mutation that cannot find a
row refuses with `ORGANIZATION_STATE_INVALID`. No mutation encodes a transition of its own,
so a state cannot be reachable by a path nobody wrote down — and the illegal transitions
become as enumerable as the legal ones, which is what makes one test cover all of them.

**Access is staged, and the stage matrix is a function of status.** A `PENDING_REVIEW`
applicant may create draft events but not publish them; only an `ACTIVE` organization may
publish, invite team members or request a payout. This is the decision that makes the wait
tolerable, and it costs nothing because publishing is already gated by
`ORGANIZER_NOT_APPROVED` at the catalogue.

**Required documents depend on the business type.** A sole proprietor does not have a
certificate of incorporation and asking for one is how an application is abandoned. The
required set is a function of `businessType`, declared in §4, and *submit* is refused with
`DOCUMENT_REQUIRED` naming exactly what is missing rather than a generic *incomplete*.

**Documents upload by presigned URL, never through the graph.** The browser `PUT`s the
bytes straight to storage and then registers the metadata (D-11). GraphQL multipart buffers
the file in the server's heap, adds a CSRF surface, and gives no progress — for a
photographed business licence over a mobile connection, progress is the difference between
waiting and giving up.

**Approval is a saga with an explicit state and explicit compensation.** Six steps across
two systems. The saga document records which step it reached, every step is idempotent and
re-entrant, and a failure past the retry budget compensates in reverse — because the
failure mode this prevents is an organization the platform believes is active whose owner
has no role, no group and no way in. Retry first, compensate only when retries are
exhausted, and record the outcome either way.

**A rejection is re-appliable; a suspension is not a rejection.** `REJECTED → DRAFT` lets
an applicant fix and return, which is the difference between a verification step and a
door. `SUSPENDED` is an administrative action on an organization that was already active,
it is reversible, and it is reached only from `ACTIVE`.

**Deletion is a request with a grace period, and it never deletes.** `PENDING_DELETION` is
reversible; what happens at the end of the grace period — cancel pending events, refund
unredeemed tickets, settle outstanding payouts, then anonymise — is
[ET-PLT-008](../../_platform/008-data-protection/)'s. This spec owns the state and the
reversal, and deliberately stops there.

**The slug is generated, unique, and immutable once active.** It is a public URL and a
Keycloak group path. Renaming an active organization would orphan links and require moving
a group tree while members are in it; the display name stays editable and the slug does
not.

**Rejected alternatives**

- *Separate `organizer_profiles` and `organizations` documents.* Two ids for one business, and an unenumerated product of two status enums.
- *`APPROVED` and `ACTIVE` as distinct states.* A distinction with no behavioural difference and a permanent source of "which one gates this".
- *All-or-nothing access until approved.* Makes the review wait pure dead time, which is when applicants abandon.
- *One required document set for every business type.* Asks sole proprietors for documents they cannot have.
- *GraphQL multipart upload for documents.* Buffers a photographed licence in the server heap and gives the user no progress bar.
- *Approval as a single transactional method.* Five of its six steps are in Keycloak, which is not in the transaction.
- *Compensating immediately on the first Keycloak failure.* Keycloak restarts; compensating a valid approval because of a five-second blip is worse than retrying.
- *A mutable slug.* Breaks public links and requires moving a Keycloak group tree with members inside it.
- *Hard-deleting an organization.* Orphans every event, ticket, escrow account and journal line that references it.

## 3. Requirements

### ET-ORG-001-R1 · An application exists from the first click and is never lost

WHEN a customer applies to become an organizer, THE SYSTEM SHALL create an organization in
`DRAFT` immediately, and IF they already have one, THEN THE SYSTEM SHALL refuse rather than
create a second.

**Acceptance**
- [ ] `applyToBeOrganizer` creates one `identity_organizations` document in `DRAFT` with `ownerId` set to the caller
- [ ] A caller who already owns an organization in any non-terminal state is refused with `ORGANIZATION_ALREADY_EXISTS`, and no second document is created
- [ ] Two concurrent applications by one caller produce exactly one organization
- [ ] The draft is retrievable by `myOrganization` across sessions with no further action
- [ ] A generated slug is unique; a collision appends a discriminator and `SLUG_TAKEN` carries `suggestedSlug` only where the caller supplied one
- [ ] The caller's `userType` remains `CUSTOMER` — it changes on approval, not on application

### ET-ORG-001-R2 · Nine states and fifteen transitions, enumerated once

THE SYSTEM SHALL admit exactly the nine states and fifteen transitions of the §4 table, and
IF a mutation would cause any other transition, THEN THE SYSTEM SHALL refuse it and change
nothing.

**Acceptance**
- [ ] `OrganizationStatus` declares exactly `DRAFT, PENDING_DOCUMENTS, PENDING_REVIEW, CHANGES_REQUESTED, REJECTED, ACTIVE, SUSPENDED, INACTIVE, PENDING_DELETION` — nine constants, no `APPROVED`
- [ ] `OrganizationTransitions.LEGAL` holds exactly 15 rows and equals the §4 table row for row, including the one row whose `from` is null
- [ ] `OrganizationTransitions.next(status, action)` returns `Optional<OrganizationStatus>`, empty for every other pair, and a test drives **all** `(status, action)` pairs, not only the legal ones
- [ ] Every mutation obtains its next status from `next(...)`; no mutation contains an `OrganizationStatus` literal on the right of an assignment
- [ ] An illegal transition is refused with `ORGANIZATION_STATE_INVALID` carrying `currentStatus`, and persists nothing
- [ ] `REJECTED → DRAFT` is legal, so a rejected applicant may re-apply without a new document

### ET-ORG-001-R3 · Submission requires the documents that business type actually needs

WHEN an applicant submits for review, THE SYSTEM SHALL verify the profile and the document
set required for their business type, and IF anything is missing, THEN THE SYSTEM SHALL
refuse naming exactly what.

**Acceptance**
- [ ] `submitForReview` is legal only from `PENDING_DOCUMENTS` and `CHANGES_REQUESTED`
- [ ] Missing profile fields are refused with `APPLICATION_INCOMPLETE` carrying `missingFields`
- [ ] Missing documents are refused with `DOCUMENT_REQUIRED` carrying `missingDocumentTypes`
- [ ] The required set is the §4 function of `businessType`, and a sole proprietor is never asked for a certificate of incorporation
- [ ] A document in `REJECTED` does not satisfy its requirement
- [ ] On success the status becomes `PENDING_REVIEW`, `submittedAt` is set from the `Clock`, and the review queue is notified
- [ ] `DRAFT → PENDING_DOCUMENTS` happens automatically when the profile becomes complete, without a user action

### ET-ORG-001-R4 · Documents upload by presigned URL and are reviewed individually

THE SYSTEM SHALL issue a presigned upload URL, register the uploaded document's metadata,
and allow a reviewer to accept or reject each document separately.

**Acceptance**
- [ ] `POST /api/v1/organizations/{orgId}/documents/upload-url` returns a presigned `PUT` URL valid for 15 minutes and a `fileKey`, and requires membership of that organization
- [ ] `POST /api/v1/organizations/{orgId}/documents` registers `documentType`, `fileKey`, `mimeType` and `fileSize`, and refuses a `fileKey` the caller was not issued
- [ ] No document byte passes through a GraphQL resolver
- [ ] Accepted MIME types and a maximum size are configured and enforced server-side, not only in the browser
- [ ] Each document carries its own `DocumentStatus` — `PENDING`, `ACCEPTED`, `REJECTED` — with `reviewedById`, `reviewedAt` and, when rejected, a reason
- [ ] Re-uploading a rejected document supersedes it and returns it to `PENDING`; the superseded record is retained
- [ ] A document's download URL is presigned, short-lived, and issued only to a reviewer or a member of the owning organization

### ET-ORG-001-R5 · Review has three outcomes and each says something different

WHEN a reviewer decides an application, THE SYSTEM SHALL record one of approve, reject or
request-changes, and each SHALL move the organization to the state §4 names for it.

**Acceptance**
- [ ] `approveOrganization`, `rejectOrganization` and `requestOrganizationChanges` are legal only from `PENDING_REVIEW` and require `ADMIN`
- [ ] Reject requires a reason and moves to `REJECTED`; request-changes requires a reason and moves to `CHANGES_REQUESTED`; the two are distinct states with distinct notifications
- [ ] `CHANGES_REQUESTED` permits editing and re-submission; `REJECTED` permits only re-application
- [ ] Approval sets `commissionRate` and `payoutSchedule` from the reviewer's input or the platform defaults of [ET-ADM-002](../../admin/002-platform-configuration/)
- [ ] Every decision records `reviewedById`, `reviewedAt` and the reason, and writes an audit row
- [ ] The applicant is notified on each of the three outcomes, with the reason where one exists

### ET-ORG-001-R6 · Approval is a compensating saga that never half-completes

WHEN an application is approved, THE SYSTEM SHALL perform the six steps of §4 in order, and
IF a step fails past its retry budget, THEN THE SYSTEM SHALL compensate the completed steps
in reverse and return the organization to `PENDING_REVIEW`.

**Acceptance**
- [ ] The saga's state is persisted with an explicit step marker before each step runs
- [ ] The six steps are: activate the organization, create the `OWNER` membership, set `userType = ORGANIZER`, grant the `ORGANIZER` realm role, create the group tree and add the owner, publish `identity.OrganizationApproved`
- [ ] Every step is idempotent — re-running the saga from any step converges rather than duplicating
- [ ] A Keycloak failure retries with backoff up to the configured budget before compensating
- [ ] Compensation reverses in order: remove from groups, revoke the role, restore `userType = CUSTOMER`, remove the membership, return the status to `PENDING_REVIEW` — and records why
- [ ] A test kills the process after each of the six steps in turn and asserts the saga either completes or fully compensates on restart, never a partial state
- [ ] No approval leaves an `ACTIVE` organization whose owner lacks the `ORGANIZER` role or the owners group — asserted directly

### ET-ORG-001-R7 · Capability follows status, at every gate

WHILE an organization is in a given status, THE SYSTEM SHALL permit exactly the capabilities
the §4 stage matrix grants it.

**Acceptance**
- [ ] Editing the profile and uploading documents are permitted in `DRAFT`, `PENDING_DOCUMENTS` and `CHANGES_REQUESTED`, and refused in `PENDING_REVIEW`, `REJECTED` and `SUSPENDED`
- [ ] Creating a **draft** event is permitted from `PENDING_REVIEW` onward; publishing one requires `ACTIVE`
- [ ] Inviting team members, requesting payouts and managing bank accounts require `ACTIVE`
- [ ] A non-`ACTIVE` organization attempting a gated capability is refused with `ORGANIZER_NOT_APPROVED` carrying `organizationStatus`
- [ ] The gate is one shared predicate, reachable from catalog and booking over the internal permission API — the matrix is not re-implemented per service
- [ ] `identity.OrganizationSuspended` causes catalog to unpublish that organization's events and booking to block its payouts

### ET-ORG-001-R8 · Suspension and deletion are reversible states, not erasures

WHEN an organization is suspended or requested for deletion, THE SYSTEM SHALL retain it and
SHALL permit the action to be reversed.

**Acceptance**
- [ ] `suspendOrganization` requires `ADMIN`, a reason, and is legal only from `ACTIVE`; `reactivateOrganization` returns it to `ACTIVE`
- [ ] `deactivateOrganization` is the owner's own action from `ACTIVE`, reaching `INACTIVE`, and is reversible by the owner
- [ ] `requestOrganizationDeletion` reaches `PENDING_DELETION` from `ACTIVE` or `INACTIVE`, records `deletionRequestedAt`, and is cancellable within the grace period
- [ ] No mutation deletes an `identity_organizations` document
- [ ] A suspended or pending-deletion organization's existing tickets remain valid and its escrow balance remains intact
- [ ] What happens when the grace period elapses is [ET-PLT-008](../../_platform/008-data-protection/)'s, and this spec triggers nothing at expiry

## 4. Model

### Documents

| Collection | Holds |
|---|---|
| `identity_organizations` | the application, the KYB data and the tenant — one document |
| `identity_verification_documents` | one row per uploaded document, with its own review state |

`identity_organizations`, the fields this spec owns:

| Field | Type | Notes |
|---|---|---|
| `_id` | `String` | `ObjectId` |
| `ownerId` | `String` | the Keycloak user ID |
| `name`, `slug` | `String` | slug unique, **immutable once `ACTIVE`** |
| `description`, `logoUrl`, `bannerUrl` | `String` | public branding |
| `businessType` | `BusinessType` | drives the required document set |
| `companyName`, `companyDescription` | `String` | KYB |
| `taxId`, `businessRegistrationNumber` | `String` | KYB |
| `businessPhone`, `businessEmail`, `businessAddress` | `String` | KYB |
| `status` | `OrganizationStatus` | the nine states |
| `keycloakGroupId` | `String` | set by saga step 5 |
| `commissionRate` | `BigDecimal` | set at approval ([ET-FIN-002](../../finance/002-commission/)) |
| `payoutSchedule` | `PayoutSchedule` | set at approval |
| `verified`, `documentsVerified`, `bankVerified` | `boolean` | |
| `submittedAt`, `reviewedAt`, `approvedAt`, `suspendedAt`, `deletionRequestedAt` | `Instant` | |
| `reviewedById` | `String` | |
| `reviewReason` | `String` | the reject or changes-requested reason |
| `createdAt`, `updatedAt` | `Instant` | |

### The state machine

Nine states. `OrganizationTransitions.LEGAL` is this table, in this order.

| # | From | Action | To | Actor |
|---|---|---|---|---|
| 1 | — | `applyToBeOrganizer` | `DRAFT` | customer |
| 2 | `DRAFT` | *profile complete* | `PENDING_DOCUMENTS` | system |
| 3 | `PENDING_DOCUMENTS` | `submitForReview` | `PENDING_REVIEW` | applicant |
| 4 | `CHANGES_REQUESTED` | `submitForReview` | `PENDING_REVIEW` | applicant |
| 5 | `PENDING_REVIEW` | `approveOrganization` | `ACTIVE` | admin |
| 6 | `PENDING_REVIEW` | `rejectOrganization` | `REJECTED` | admin |
| 7 | `PENDING_REVIEW` | `requestOrganizationChanges` | `CHANGES_REQUESTED` | admin |
| 8 | `REJECTED` | `applyToBeOrganizer` | `DRAFT` | applicant — re-apply |
| 9 | `ACTIVE` | `suspendOrganization` | `SUSPENDED` | admin |
| 10 | `SUSPENDED` | `reactivateOrganization` | `ACTIVE` | admin |
| 11 | `ACTIVE` | `deactivateOrganization` | `INACTIVE` | owner |
| 12 | `INACTIVE` | `reactivateOrganization` | `ACTIVE` | owner |
| 13 | `ACTIVE` | `requestOrganizationDeletion` | `PENDING_DELETION` | owner |
| 14 | `INACTIVE` | `requestOrganizationDeletion` | `PENDING_DELETION` | owner |
| 15 | `PENDING_DELETION` | `cancelOrganizationDeletion` | `ACTIVE` | owner |

Nine states plus the null origin, ten actions: 100 pairs, of which 15 are legal.
`OrganizationTransitionTest` drives all 100.

### Stage access matrix

| Capability | `DRAFT` | `PENDING_DOCUMENTS` | `PENDING_REVIEW` | `CHANGES_REQUESTED` | `ACTIVE` | `REJECTED` | `SUSPENDED` | `INACTIVE` | `PENDING_DELETION` |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| Edit profile | ✅ | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| Upload documents | ✅ | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| Submit for review | ❌ | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Re-apply | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| Create **draft** event | ❌ | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Publish** event | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| Invite team members | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| Request payout | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| Manage bank accounts | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |

`OrganizationCapabilities.permits(status, capability)` is the single implementation, and
catalog and booking reach it over `POST /api/internal/permissions/resolve`
([ET-ORG-003](../003-permission-resolution/)).

### Required documents by business type

| `BusinessType` | Required |
|---|---|
| `SOLE_PROPRIETOR` | `NATIONAL_ID`, `TAX_CERTIFICATE` |
| `PARTNERSHIP` | `NATIONAL_ID`, `TAX_CERTIFICATE`, `PARTNERSHIP_AGREEMENT` |
| `LIMITED_COMPANY` | `NATIONAL_ID`, `TAX_CERTIFICATE`, `CERTIFICATE_OF_INCORPORATION` |
| `NGO` | `NATIONAL_ID`, `TAX_CERTIFICATE`, `NGO_REGISTRATION` |
| `GOVERNMENT` | `NATIONAL_ID`, `AUTHORISATION_LETTER` |

`PROOF_OF_ADDRESS` and `BANK_STATEMENT` are optional everywhere and are what a reviewer
asks for via `requestOrganizationChanges` when something does not add up.

`identity_verification_documents`: `_id`, `organizationId`, `documentType`, `fileKey`,
`mimeType`, `fileSize`, `status`, `reviewedById`, `reviewedAt`, `rejectionReason`,
`supersededById`, `uploadedById`, `createdAt`, `updatedAt`.

### The approval saga

| # | Step | System | Compensation |
|---|---|---|---|
| 1 | status → `ACTIVE`, set `approvedAt`, `commissionRate`, `payoutSchedule` | MongoDB | status → `PENDING_REVIEW` |
| 2 | create the `OWNER` `identity_organization_members` row | MongoDB | remove the row |
| 3 | `identity_users.userType` → `ORGANIZER`, set `primaryOrganizationId` | MongoDB | restore `CUSTOMER`, clear |
| 4 | grant the `ORGANIZER` realm role | Keycloak | revoke |
| 5 | create `/organizations/{slug}` and its five role subgroups; add the owner to `owners`; store `keycloakGroupId` | Keycloak | remove from group; the tree is left (it is harmless and re-usable) |
| 6 | publish `identity.OrganizationApproved` | bus | — (a consumer receiving it for a compensated approval sees `OrganizationSuspended` next) |

Steps 4 and 5 retry with exponential backoff up to `identity.onboarding.saga.max-retries`
before compensation begins. The saga's own state lives on the organization document as
`approvalSagaStep` and `approvalSagaAttempts`, so recovery needs no extra collection.

### GraphQL

Subgraph `identity`. Every field carries `@auth` explicitly.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `myOrganization` | query | `AUTHENTICATED` | `Organization` |
| `organization(id)` | query | `AUTHENTICATED` | `Organization` |
| `organizationBySlug(slug)` | query | `PUBLIC` | `Organization` |
| `organizations(filter, page)` | query | `ADMIN` | `OrganizationPage!` |
| `organizerApplications(status, page)` | query | `ADMIN` | `OrganizationPage!` |
| `verificationDocuments(organizationId)` | query | `AUTHENTICATED` | `[VerificationDocument!]!` |
| `applyToBeOrganizer(input)` | mutation | `AUTHENTICATED` | `Organization!` |
| `updateOrganization(id, input)` | mutation | `AUTHENTICATED` | `Organization!` |
| `submitForReview(id)` | mutation | `AUTHENTICATED` | `Organization!` |
| `approveOrganization(input)` | mutation | `ADMIN` | `Organization!` |
| `rejectOrganization(input)` | mutation | `ADMIN` | `Organization!` |
| `requestOrganizationChanges(input)` | mutation | `ADMIN` | `Organization!` |
| `reviewDocument(input)` | mutation | `ADMIN` | `VerificationDocument!` |
| `suspendOrganization(id, reason)` | mutation | `ADMIN` | `Organization!` |
| `reactivateOrganization(id)` | mutation | `AUTHENTICATED` | `Organization!` |
| `deactivateOrganization(id)` | mutation | `AUTHENTICATED` | `Organization!` |
| `requestOrganizationDeletion(id)` | mutation | `AUTHENTICATED` | `Organization!` |
| `cancelOrganizationDeletion(id)` | mutation | `AUTHENTICATED` | `Organization!` |

`organizationBySlug` is `PUBLIC` and returns only the public projection — name, slug,
description, logo, banner, `verified`. It never exposes KYB fields, and a test asserts that.
`Organization` is identity's `@key(fields: "id")` type; catalog extends it with `events` and
booking with `bankAccounts`, `payoutRequests` and `availableBalance`
([ET-PLT-004 §4](../../_platform/004-federation-contract/)).

### REST

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/organizations/{orgId}/documents/upload-url` | member of the org | issue a 15-minute presigned `PUT` |
| `POST` | `/api/v1/organizations/{orgId}/documents` | member of the org | register the uploaded metadata |
| `GET` | `/api/v1/organizations/{orgId}/documents/{id}/download-url` | member or `ADMIN` | issue a short-lived `GET` |

### Events

| Tier | Name | When | Consumers |
|---|---|---|---|
| bus | `identity.OrganizationApproved` v1 | saga step 6 | catalog → may publish; booking → enable payouts |
| bus | `identity.OrganizationSuspended` v1 | transition 9, and on compensation | catalog → unpublish; booking → block payouts |
| module | `OrganizationSubmittedEvent` | transition 3, 4 | notify the review queue |
| module | `OrganizationDecidedEvent` | transitions 5, 6, 7 | notify the applicant |

Both bus rows are §4 registry rows of
[ET-PLT-003](../../_platform/003-event-contract/), session-keyed on `organizationId`.

### Configuration

| Property | Value |
|---|---|
| `identity.onboarding.document.max-size` | 10 MB |
| `identity.onboarding.document.accepted-types` | `image/jpeg`, `image/png`, `application/pdf` |
| `identity.onboarding.upload-url-ttl` | `PT15M` |
| `identity.onboarding.saga.max-retries` | 3 |
| `identity.onboarding.saga.backoff` | `PT2S` initial, exponential |
| `identity.onboarding.deletion-grace` | `P30D` |

### Error codes

`ORGANIZATION_UNKNOWN`, `ORGANIZATION_STATE_INVALID`, `ORGANIZATION_ALREADY_EXISTS`,
`SLUG_TAKEN`, `APPLICATION_INCOMPLETE`, `DOCUMENT_REQUIRED`, `DOCUMENT_STATE_INVALID` —
rows of [ET-PLT-005 §4](../../_platform/005-error-contract/) introduced by this spec —
plus `ORGANIZER_NOT_APPROVED`, introduced by
[ET-CAT-001](../../catalog/001-event-lifecycle/) and raised here by R7's gate.

## 5. Tasks

- [ ] **T1 · The document, `OrganizationStatus`, `OrganizationTransitions`, the 100-pair test**
  - requirements: R1, R2
  - files: `backend/identity-service/.../domain/`
  - verify: 15 legal rows, 85 refusals, no status literal in any mutation
  - parallel-safe: no — everything else depends on it
  - depends: —

- [ ] **T2 · `applyToBeOrganizer`, slug generation, the concurrent-application test**
  - requirements: R1
  - files: `backend/identity-service/.../service/impl/OrganizationServiceImpl.java`
  - verify: two concurrent applications produce one organization
  - parallel-safe: yes
  - depends: T1

- [ ] **T3 · Document upload: presigned URLs, registration, type and size enforcement**
  - requirements: R4
  - files: `backend/identity-service/.../web/rest/VerificationDocumentRestController.java`
  - verify: no byte passes a resolver; an unissued `fileKey` is refused; an oversize file is refused server-side
  - parallel-safe: yes
  - depends: T1

- [ ] **T4 · `submitForReview` with the business-type document function**
  - requirements: R3
  - files: `backend/identity-service/.../service/impl/`, `domain/RequiredDocuments.java`
  - verify: a sole proprietor is never asked for incorporation; refusals name what is missing
  - parallel-safe: yes
  - depends: T1, T3

- [ ] **T5 · The three review outcomes and per-document review**
  - requirements: R4, R5
  - files: `backend/identity-service/.../web/graphql/mutation/`
  - verify: reject and request-changes are distinct states with distinct notifications
  - parallel-safe: yes
  - depends: T4

- [ ] **T6 · The approval saga: six steps, persisted marker, retry, compensation**
  - requirements: R6
  - files: `backend/identity-service/.../service/impl/OrganizationApprovalSaga.java`
  - verify: killing the process after each step in turn yields completion or full compensation, never partial
  - parallel-safe: no — the highest-risk write in this spec
  - depends: T5

- [ ] **T7 · `OrganizationCapabilities` and the internal resolve endpoint**
  - requirements: R7
  - files: `backend/identity-service/.../domain/OrganizationCapabilities.java`, `.../web/rest/`
  - verify: catalog and booking gate on the same predicate; the matrix exists in one place
  - parallel-safe: no — two other services consume it
  - depends: T1

- [ ] **T8 · Suspension, deactivation, deletion request and cancellation**
  - requirements: R8
  - files: `backend/identity-service/.../web/graphql/mutation/`
  - verify: no mutation deletes a document; tickets and escrow survive suspension
  - parallel-safe: yes
  - depends: T1

- [ ] **T9 · The subgraph half: types, the public projection, `@auth` on every field**
  - requirements: R1–R8
  - files: `backend/identity-service/src/main/resources/graphql/schema.graphqls`
  - verify: `compose-supergraph.sh --static`; `organizationBySlug` exposes no KYB field
  - parallel-safe: no — shared SDL file with ET-ORG-002 and ET-ORG-003
  - depends: T2, T5, T8

## 6. Out of scope

| Capability | Spec |
|---|---|
| Members, roles, invitations, ownership transfer | [ET-ORG-002](../002-teams-and-invitations/) |
| The permission resolution algorithm and event access grants | [ET-ORG-003](../003-permission-resolution/) |
| What an organization may do to an event once active | [ET-CAT-001](../../catalog/001-event-lifecycle/) |
| Commission rates and how the approval-time rate is used | [ET-FIN-002](../../finance/002-commission/) |
| Bank accounts, payout schedules and eligibility | [ET-FIN-003](../../finance/003-payouts-and-settlement/) |
| The notifications each outcome sends | [ET-NTF-002](../../notification/002-lifecycle-triggers/) |
| The approval queue, SLA and escalation | [ET-ADM-001](../../admin/001-approvals-workbench/) |
| Platform default commission rate and payout schedule | [ET-ADM-002](../../admin/002-platform-configuration/) |
| What happens when the deletion grace period elapses | [ET-PLT-008](../../_platform/008-data-protection/) |
| The audit rows every decision writes | [ET-PLT-009](../../_platform/009-audit-trail/) |

Deliberately never in scope: **a separate `organizer_profiles` document** (two ids for one
business), **an `APPROVED` state distinct from `ACTIVE`** (a distinction with no
behavioural difference), and **hard deletion of an organization** (it orphans every event,
ticket, escrow account and journal line that references it).
