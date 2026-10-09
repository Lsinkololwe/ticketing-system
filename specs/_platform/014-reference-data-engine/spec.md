# ET-PLT-014 · The reference data engine — enum-derived, administrator-owned lookups

> **Conformance** · V3 §7 collections · US Part V §23–25 reference data

## 1. Capability

Every screen in the platform offers lists the business owns rather than the code: mobile-money
operators, banks, event categories, music genres, KYB document types, cancellation reasons, refund
reasons, tax rates. Each of these changes on a business timescale — a new operator launches, a
category is renamed — and none of them should require a deployment.

At the same time the platform has a second family of lists that look identical on screen and are
not: **workflow statuses**. A ticket status, a payout status and an organization status are
rendered from the same picker, but code branches on them, so an administrator adding a row to one
of those lists is changing behaviour rather than vocabulary.

This spec builds one engine for both. A single polymorphic collection holds every list, keyed by a
compiled **type** discriminator and a runtime **code**. Adding a *type* is a deliberate code change,
because a new type needs a metadata contract and a consumer. Adding a *row within a type* is
administration and needs nothing but the admin screen.

For workflow types the engine derives the initial rows **from the code's own enums, by reflection**,
so the value list is never hand-maintained and cannot drift from what the code accepts. Every such
row carries a **semantic** — the coarse meaning code is allowed to branch on. Code branches on the
semantic, never on the code string, so an administrator may add `AWAITING_COMPLIANCE_REVIEW` to the
payout statuses and the money still moves through the branch that handles *pending*.

## 2. Design decisions

**One polymorphic collection, discriminated by a compiled type.** Twenty small collections with
identical shapes is twenty sets of indexes, twenty repositories and twenty admin screens for one
concept. The rows differ only in which list they belong to.

**Reference data describes values, never their appearance.** *(Amended 2026-09-19, product
owner.)* A row is a code, a name, its place in a hierarchy and its typed metadata. It carries no
colour, icon, image or style: the reference engine does not determine the frontend, and each
application's design decides how a value is drawn. `ReferenceMetadataValidator` refuses a
presentation key (`color`, `icon`, `iconUrl`, `image`, …) with `COMMAND_NOT_WELL_FORMED`;
`displayOrder` stays, because the order of a list is data, not styling.

**Types are compiled; rows are runtime.** A type carries a metadata contract — MSISDN prefixes for
an operator, a SWIFT code for a bank — and something in the code consumes it. Letting an
administrator invent a type produces a list nothing reads.

**Workflow rows are reflected out of the enums, not written into a seed file.** The values are
already written down: they are the constants the code compiles against. Re-typing them into a seed
creates a second copy that drifts the first time somebody adds a constant and forgets, and the
drift is silent — the picker simply never offers the new value.

**The bootstrapper inserts and never updates.** A row that exists is left exactly as it is. That is
the contract with the administrator: they rename a status, choose a colour, reorder the list, and a
deployment does not undo any of it.

**A workflow row with no declared semantic aborts the boot.** Defaulting it would produce a status
that looks configured, reads correctly on the admin screen, and routes behaviour down the wrong
branch — discovered weeks later by an organizer whose payout never moved. A status filed under the
wrong meaning is worse than one filed under none.

**Semantics are declared by hand, precisely because they cannot be reflected.** A constant's name
is a hint, not a fact. `PENDING_REVIEW` really is *pending*; `PENDING_VERIFICATION` on a ticket is
a payment still in flight; `PENDING_DELETION` on an organization is a live account with a countdown
on it. A pattern match on the word "PENDING" gets one of those three right.

**Code branches on the semantic, never on the code string.** There are six semantics and there may
be any number of codes. This is what makes the list extensible without a deployment: a new code
inherits an existing branch.

**Some machines are code-owned.** The transitions of a ticket, a reservation and a payment are
defined by their own specs and are not the administrator's to redraw. Those types accept new rows
for display and reporting, and refuse edits to the transition table.

**Rows are deactivated, never deleted.** A past event references the category it was filed under.
Deleting it breaks the history; deactivating it removes it from selection and leaves the record
readable.

**Rejected alternatives**

- *A collection per list.* Twenty schemas for one concept, and a twenty-first the day marketing asks for a new list.
- *A seed file of workflow values.* A second copy of the enum constants, drifting silently.
- *Reflecting the semantic from the constant name.* `PENDING_DELETION` is not pending.
- *Defaulting a missing semantic.* Looks configured, behaves wrong, found late.
- *Letting administrators define types.* A list nothing reads.
- *Letting administrators redraw a code-owned transition table.* The state machine belongs to the spec that owns the money.
- *Hard deletion.* Breaks every historical record that referenced the row.
- *Branching on the code string.* Freezes the list; every new value needs a deployment, which is the problem this engine exists to remove.

## 3. Requirements

### ET-PLT-014-R1 · One collection, one row per (type, code)

THE SYSTEM SHALL store every reference list in `catalog_reference_data`, discriminated by `type`,
with `code` unique within a type.

**Acceptance**
- [ ] The collection is named `catalog_reference_data` and is a row of the [ET-PLT-002](../002-persistence-baseline/) §4 registry
- [ ] A unique index on `{ type: 1, code: 1 }` refuses a duplicate, confirmed against a live database
- [ ] `ReferenceType` is a compiled enum; no mutation creates a type
- [ ] Every row carries `type`, `code`, `label`, `displayOrder`, `active`, and a typed `metadata` document
- [ ] `metadata` is validated per type at the service layer and by collection-level schema validation
- [ ] Two instances bootstrapping concurrently produce one row per code

### ET-PLT-014-R2 · Workflow rows are derived from the enums by reflection

WHEN the engine bootstraps, THE SYSTEM SHALL materialise one row per constant of every registered
enum, without a seed list.

**Acceptance**
- [ ] Every registered enum class is reflected over; no file enumerates its constants
- [ ] Adding a constant to a registered enum produces a row on the next boot with no other change
- [ ] The derived `label` is the humanised constant — `PENDING_FINANCE_APPROVAL` becomes `Pending finance approval`
- [ ] A registry of zero registrations is reported as an error, not treated as nothing to do
- [ ] The bootstrap is idempotent: a second run inserts nothing and reports the retained count

### ET-PLT-014-R3 · The bootstrapper inserts and never updates

THE SYSTEM SHALL leave an existing row exactly as it is.

**Acceptance**
- [ ] A row renamed, recoloured or reordered by an administrator is unchanged by a subsequent boot
- [ ] A row deactivated by an administrator is not reactivated by a boot
- [ ] The result reports `inserted` and `retained` separately
- [ ] No boot path issues an update or a delete against `catalog_reference_data`
- [ ] Bootstrapping is safe on every start; it is not a one-off migration

### ET-PLT-014-R4 · Every workflow row declares a semantic, and a missing one aborts the boot

IF a constant of a workflow enum has no declared `WorkflowSemantic`, THEN THE SYSTEM SHALL fail to
start and SHALL name the constant.

**Acceptance**
- [ ] `WorkflowSemantic` is exactly `INITIAL`, `PENDING`, `IN_PROGRESS`, `SUCCEEDED`, `FAILED`, `CANCELLED`
- [ ] A registered workflow enum with an unclassified constant aborts the boot, naming type and constant
- [ ] No code path defaults, infers or pattern-matches a semantic from the constant name
- [ ] Every constant of every registered workflow enum has a declared semantic, asserted by a test over the registry
- [ ] A semantic chosen by an administrator when adding a row is stored on the row and is required

### ET-PLT-014-R5 · Code branches on the semantic, never on the code string

THE SYSTEM SHALL make every behavioural decision over a reference-backed status from its semantic.

**Acceptance**
- [ ] No production branch compares a status to a reference `code` literal, asserted by a source scan
- [ ] A new code added at runtime under an existing semantic is handled by the existing branch, asserted by an end-to-end test that adds a status and drives the flow
- [ ] Reports group by semantic and display by label
- [ ] A row whose semantic is changed by an administrator changes behaviour only prospectively

### ET-PLT-014-R6 · Code-owned machines accept rows but refuse transition edits

WHERE a type is code-owned, THE SYSTEM SHALL refuse any attempt to edit its transition table.

**Acceptance**
- [ ] `TICKET_STATUS`, `RESERVATION_STATUS` and `PAYMENT_STATUS` are marked code-owned
- [ ] An attempt to add, remove or redirect a transition on a code-owned type is refused with `REFERENCE_MACHINE_CODE_OWNED`
- [ ] A new display row on a code-owned type is accepted and appears in reporting
- [ ] The transition tables of the owning specs remain the sole authority for those machines
- [ ] The admin screen shows a code-owned type as read-only for transitions and editable for presentation

### ET-PLT-014-R7 · Rows are deactivated, never deleted, and history keeps rendering

THE SYSTEM SHALL provide no deletion path, and a deactivated row SHALL continue to resolve for
records that reference it.

**Acceptance**
- [ ] No delete mutation exists for a reference row, asserted against the composed schema
- [ ] A deactivated row is excluded from every picker
- [ ] An event, ticket or organization referencing a deactivated row renders correctly, asserted per reference kind
- [ ] Deactivation is audited with actor and reason
- [ ] Reactivation restores the row to selection without altering records that referenced it

### ET-PLT-014-R8 · Reads are cached and evicted on change, and nothing polls

THE SYSTEM SHALL serve reference reads from cache, evicting on mutation.

**Acceptance**
- [ ] Cold and warm answers are identical
- [ ] A mutation evicts the affected type across every instance within `platform.reference.eviction-budget` (PT5S)
- [ ] No scheduled task refreshes reference data
- [ ] A cache miss on an unreachable store returns the stored answer or refuses; it never returns an empty list as though the list were empty
- [ ] The picker query for a type reports `IXSCAN` on `{ type, active, displayOrder }`

### ET-PLT-014-R9 · Every list a screen offers is a reference type, an API enum's labels, or UI-only

THE SYSTEM SHALL serve every platform-owned list a client renders from a reference type, and SHALL leave
in a client only (a) labels for a closed API enum, typed from the generated enum, and (b) UI-only choices
(sort order, view toggles). *(Added 2026-10-06, product owner: "every reference dropdown must be integrated
with the backend, not hardcoded in the frontend".)*

Eight types are added to the compiled enum. No new collection: the single polymorphic collection, its
unique `{ type, code }` index and its per-type metadata contract are the registry rows for all of them.

| Type | Group | Metadata contract | Mirrors |
|---|---|---|---|
| `ORGANIZER_TYPE` | KYB | none | identity `OrganizationType` |
| `BUSINESS_TYPE` | KYB | `requiredDocuments` (codes of `KYB_DOCUMENT_TYPE`) | identity `BusinessType`, `RequiredDocuments` |
| `TICKET_TIER_CATEGORY` | Events | none | catalog `TicketCategory` |
| `ORGANIZATION_ROLE`, `EVENT_ROLE` | Access | `invitable` | identity `OrganizationRole`, `EventRole` |
| `NOTIFICATION_CHANNEL` | Communication | `preferenceKey` | identity `NotificationChannel`, `NotificationPreferences` |
| `NOTIFICATION_CATEGORY` | Communication | `preferenceKey`, `locked` | identity `NotificationPreferences` |
| `REPORT_PERIOD` | Reporting | `days`, `bucket` | the analytics bucket enum |

**Acceptance**
- [ ] Each type is in `ReferenceType`, in the `referenceTypes` registry with its group, and in the collection validator's `type` and `parentType` enums
- [ ] A row of a type that names a required metadata key without it is refused by `ReferenceMetadataValidator`
- [ ] A row whose codes mirror an API enum carries exactly that enum's members, asserted by reading the enum from the owning service's schema (a member added on one side only fails a test)
- [ ] `BUSINESS_TYPE.requiredDocuments` equals identity's `RequiredDocuments` table for every business type, and `KYB_DOCUMENT_TYPE` codes equal identity's document vocabulary
- [ ] `CITY` coordinates are optional: a district town without a surveyed position is a valid row

### ET-PLT-014-R10 · Countries come from a maintained standard, not a list in code or a client

THE SYSTEM SHALL seed `COUNTRY` from the regions libphonenumber supports, each with its calling code and
ISO 3166 alpha-3, and SHALL expose them through `referenceData(type: COUNTRY)`.

**Acceptance**
- [ ] Every supported region with an ISO alpha-3 is a `COUNTRY` row: code, English name, `dialCode` (`+260`), `iso3`
- [ ] Zambia is first and its neighbours follow; the rest are alphabetical; `displayOrder` is data an administrator may change
- [ ] No client holds a country list; the phone field takes its options from the query
- [ ] Upgrading libphonenumber adds the new regions on the next release step without an edit to the seed

### ET-PLT-014-R11 · A visitor with no token reads the public types and nothing else

THE SYSTEM SHALL admit `referenceData` and `referenceDataByParent` without a token for the public types only.

**Acceptance**
- [ ] Both root fields are on the catalog's public allowlist; `referenceItem`, `referenceTypes` and `referenceDataAll` are not
- [ ] The public types are `COUNTRY`, `CURRENCY`, `LANGUAGE`, `TIMEZONE`, `PROVINCE`, `CITY`, `MOBILE_MONEY_OPERATOR`, `EVENT_TYPE`, `EVENT_CATEGORY`, `MUSIC_GENRE`, `AGE_RESTRICTION`, `TICKET_TIER_CATEGORY`, `REFUND_REASON`, `NOTIFICATION_CHANNEL`, `NOTIFICATION_CATEGORY`, `CARD_SCHEME`, `TICKET_STATUS`, `RESERVATION_STATUS`, `EVENT_STATUS`
- [ ] Every other type (banks, verification documents, legal and organizer types, reason codes, tax rates, roles, report periods, money statuses) is refused to a visitor with an access error and answered to any signed-in caller
- [ ] The per-type gate is asserted through the real method-security proxy for every `ReferenceType`
- [ ] The public surface test names the allowlist and fails if a role gate other than this one appears on an allowlisted root

### ET-PLT-014-R12 · Reference data ships as versioned, idempotent release steps

THE SYSTEM SHALL deliver real reference configuration through numbered steps of the service's migration
runner, each applied once and each safe to apply again.

**Acceptance**
- [ ] Release 2 is the step `reference-data-seed-v2`, recorded in the migration ledger; a correction is a later step, never an edit of this one
- [ ] It ensures the vocabularies of R9, the corrected Zambian mobile-money prefixes (MTN 096 and 076, Airtel 097 and 077, Zamtel 095 and 055), identity's KYB document codes, the 116 districts of Zambia as `CITY` rows under their province, and the countries of R10
- [ ] Applying it twice yields the same collection; an administrator's `isActive` choice is never reversed by it
- [ ] A document code it retires is deactivated, never deleted (R7); a row an administrator created is untouched
- [ ] The release passes the real collection validator, asserted against a live database
- [ ] A type is owned by exactly one seed file, so no row is rewritten on every boot

### ET-PLT-014-R13 · There is one province, city and category list, and the public queries read it

THE SYSTEM SHALL answer `categories`, `provinces`, `cities` and `citiesWithEvents` from the `EVENT_CATEGORY`,
`PROVINCE` and `CITY` rows, with `id` equal to the row's code.

**Acceptance**
- [ ] An event, a venue and a discovery filter all name a category or city by the same code the public queries return as `id`
- [ ] A fresh database with only the reference seed serves non-empty `categories`, `provinces` and `cities`
- [ ] `citiesWithEvents` returns the cities with at least one published, active, undeleted event, each with its count, and omits a city whose only events are drafts
- [ ] A deactivated row leaves each public list

## 4. Model

> **Amended 2026-09-01 under [D-19](../../ROADMAP.md).** 3 operation names below adopt the
> shipped names: `createReferenceRow` → `createReferenceData`, `setReferenceRowActive` → `setReferenceDataActive`, `updateReferenceRow` → `updateReferenceData`. D-19 rules that where the schema and §4 disagree on an operation's
> *name*, the schema stands and §4 adopts it.
>
> **Only the names were adopted.** Argument lists and return types were not re-verified against
> the schema, so a row here can now name a real operation and still describe it wrongly. That
> gap is unmeasured, and calling it verified would be the same mistake as counting a file's
> existence as proof it runs.

### Documents

| Collection | Owning service | Key fields | Notes |
|---|---|---|---|
| `catalog_reference_data` | catalog-service | `_id`, `type`, `code`, `label`, `displayOrder`, `active`, `parentCode`, `semantic`, `metadata` | one row per (type, code); `semantic` required for workflow types |

### Indexes

| Collection | Index | Kind | Why |
|---|---|---|---|
| `catalog_reference_data` | `{ type: 1, code: 1 }` | unique | the uniqueness contract, and what makes concurrent bootstrap safe |
| `catalog_reference_data` | `{ type: 1, active: 1, displayOrder: 1 }` | compound | every picker query |
| `catalog_reference_data` | `{ type: 1, parentCode: 1 }` | compound | cities within a province, genres within a category |

### Type groups

| Group | Types |
|---|---|
| Geography | `COUNTRY` (from libphonenumber, R10), `CURRENCY`, `LANGUAGE`, `TIMEZONE`, `PROVINCE`, `CITY` (parent `PROVINCE`; metadata optional `latitude`, `longitude`) |
| Payments | `MOBILE_MONEY_OPERATOR`, `BANK` |
| Events | `EVENT_TYPE`, `EVENT_CATEGORY`, `MUSIC_GENRE`, `AGE_RESTRICTION`, `TICKET_TIER_CATEGORY` |
| KYB & Onboarding | `KYB_DOCUMENT_TYPE`, `ORGANIZER_TYPE`, `BUSINESS_TYPE` |
| Roles & Access | `ORGANIZATION_ROLE`, `EVENT_ROLE` |
| Communication | `NOTIFICATION_CHANNEL`, `NOTIFICATION_CATEGORY` |
| Reporting | `REPORT_PERIOD` |
| Operations | `CANCELLATION_REASON`, `REFUND_REASON`, `REJECTION_REASON` |
| Finance | `TAX_RATE`, `CARD_SCHEME` |
| Workflow | `TICKET_STATUS`, `RESERVATION_STATUS`, `PAYMENT_STATUS`, `PAYOUT_STATUS`, `REFUND_STATUS`, `ESCROW_STATUS`, `EVENT_STATUS`, `ORGANIZATION_STATUS`, `INVITATION_STATUS`, `DOCUMENT_STATUS`, `CHARGEBACK_STATUS`, `TRANSACTION_STATUS` |

### `WorkflowSemantic`

`INITIAL` · `PENDING` · `IN_PROGRESS` · `SUCCEEDED` · `FAILED` · `CANCELLED`

Six meanings, any number of codes. Every behavioural branch is over these six.

### Code-owned machines

| Type | Owning spec |
|---|---|
| `TICKET_STATUS` | [ET-TKT-002](../../ticketing/002-ticket-issuance-and-qr/) |
| `RESERVATION_STATUS` | [ET-TKT-001](../../ticketing/001-reservation-and-hold/) |
| `PAYMENT_STATUS` | [ET-PAY-001](../../payment/001-payment-intents-and-providers/) |

New rows for display and reporting: accepted. Transition edits: refused.

### Bootstrap

```
boot
 └─ load registrations (enum class + declared semantics per constant)
      │  registry empty            → ERROR, not "nothing to do"
      │  constant without semantic → ABORT, naming type and constant
      ▼
    reflect constants → for each: insert if absent, retain if present
      │  duplicate key from a concurrent instance → retained
      ▼
    Result(inserted, retained)
```

### GraphQL

Subgraph `catalog`.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `referenceTypes` | query | signed in | `[ReferenceTypeInfo!]!` — grouped, for the admin type picker |
| `referenceData(type)` | query | public for the R11 types, signed in for the rest | `[ReferenceData!]!` — active rows, ordered |
| `referenceDataByParent(type, parentCode)` | query | same gate | `[ReferenceData!]!` — active child rows |
| `referenceDataAll(type)` | query | `ADMIN` | `[ReferenceData!]!` — including inactive |
| `createReferenceData(input)` | mutation | `ADMIN` | `ReferenceData!` |
| `updateReferenceData(input)` | mutation | `ADMIN` | `ReferenceData!` |
| `reorderReferenceRows(input)` | mutation | `ADMIN` | `[ReferenceData!]!` |
| `setReferenceDataActive(input)` | mutation | `ADMIN` | `ReferenceData!` |

There is no `deleteReferenceRow`.

### Events

| Tier | Java type | Wire name | Topic | Consumers |
|---|---|---|---|---|
| bus | `ReferenceDataChangedEvent` | `catalog.ReferenceDataChanged` v`1` | `catalog-events` | booking, identity — cache eviction |

Catalog evicts its own cache in the method that changes the row; there is no in-memory event.

### Redis keys

| Key | Type | TTL | Purpose |
|---|---|---|---|
| `ref:{type}` | STRING | 1 h | the active, ordered rows of one type; authority is `catalog_reference_data` |

### Configuration

| Property | Value |
|---|---|
| `platform.reference.eviction-budget` | `PT5S` |
| `platform.reference.cache-ttl` | `PT1H` |

### Error registry rows

| Code | Refusal type | GraphQL `ErrorType` | Retryable |
|---|---|---|---|
| `REFERENCE_TYPE_UNKNOWN` | `ReferenceTypeUnknown` | `NOT_FOUND` | no |
| `REFERENCE_CODE_DUPLICATE` | `ReferenceCodeDuplicate` | `FAILED_PRECONDITION` | no |
| `REFERENCE_SEMANTIC_REQUIRED` | `ReferenceSemanticRequired` | `BAD_REQUEST` | no |
| `REFERENCE_MACHINE_CODE_OWNED` | `ReferenceMachineCodeOwned` | `FAILED_PRECONDITION` | no |

## 5. Tasks

- [ ] **T1 · `ReferenceType`, `ReferenceGroup`, `WorkflowSemantic` and the document**
  - requirements: R1
  - files: `backend/catalog-service/.../domain/`, `backend/shared-library/.../constants/`
  - verify: the collection is a row of the ET-PLT-002 registry; the unique index refuses a duplicate against a live database
  - parallel-safe: no
  - depends: —

- [ ] **T2 · The registration table: enum class per type, semantic per constant**
  - requirements: R4
  - files: `backend/catalog-service/.../service/referencedata/ReferenceDataRegistrations.java`
  - verify: every constant of every registered workflow enum has a declared semantic; no name-based inference exists
  - parallel-safe: no
  - depends: T1

- [ ] **T3 · The reflecting bootstrapper: insert-only, idempotent, fail-loud**
  - requirements: R2, R3
  - files: `backend/catalog-service/.../service/referencedata/ReferenceDataBootstrapper.java`
  - verify: a second run inserts nothing; an administrator's rename survives a boot; an unclassified constant aborts naming type and constant; an empty registry errors
  - parallel-safe: no
  - depends: T2

- [ ] **T4 · Semantic-only branching, and the source scan that enforces it**
  - requirements: R5
  - files: the status consumers across all three services
  - verify: no production branch compares a status to a reference code literal; a runtime-added code is handled by the existing branch, asserted end to end
  - parallel-safe: yes — one service per agent
  - depends: T2

- [ ] **T5 · Code-owned machines and the transition-edit refusal**
  - requirements: R6
  - files: `backend/catalog-service/.../service/impl/ReferenceDataServiceImpl.java`
  - verify: a transition edit on a code-owned type refuses; a display row on one is accepted
  - parallel-safe: yes
  - depends: T3

- [ ] **T6 · Per-type metadata validation and collection-level schema**
  - requirements: R1
  - files: `.../service/referencedata/ReferenceMetadataValidator.java`, `src/main/resources/mongodb/schemas/`
  - verify: each type's metadata contract refuses its violation at the service layer and at the collection
  - parallel-safe: yes — one type per agent
  - depends: T1

- [ ] **T7 · Deactivation everywhere; the past-record rendering test**
  - requirements: R7
  - files: `backend/catalog-service/.../service/impl/`, the three services' consumers
  - verify: no delete mutation exists in the composed schema; a past record renders after each reference kind is deactivated
  - parallel-safe: yes
  - depends: T5

- [ ] **T8 · Caching, cross-instance eviction, and the no-polling assertion**
  - requirements: R8
  - files: `backend/catalog-service/.../infrastructure/cache/`, `.../event/`
  - verify: cold equals warm; a mutation evicts everywhere within the budget; no scheduled task refreshes reference data; the picker query reports `IXSCAN`
  - parallel-safe: yes
  - depends: T6

- [ ] **T9 · The subgraph half; the grouped type picker; `@auth` on every field**
  - requirements: R1–R8
  - files: `backend/catalog-service/src/main/resources/graphql/schema.graphqls`
  - verify: `compose-supergraph.sh --static`; the admin type picker renders from `referenceTypes` with no hardcoded list on the client
  - parallel-safe: no — shared SDL with ET-CAT-001, ET-CAT-002 and ET-CAT-003
  - depends: T7

## 6. Out of scope

| Capability | Spec |
|---|---|
| Provinces, cities, venues and event categories as a discovery surface | [ET-CAT-003](../../catalog/003-locations-and-reference-data/) |
| The transition tables of the code-owned machines | [ET-TKT-001](../../ticketing/001-reservation-and-hold/), [ET-TKT-002](../../ticketing/002-ticket-issuance-and-qr/), [ET-PAY-001](../../payment/001-payment-intents-and-providers/) |
| Platform settings, commission defaults and feature flags | [ET-ADM-002](../../admin/002-platform-configuration/) |
| Who may edit reference data | [ET-PLT-013](../013-permission-engine/) |
| Recording that an edit happened | [ET-PLT-009](../009-audit-trail/) |

Deliberately never in scope: **administrator-defined types** (a list nothing reads),
**hard deletion** (it breaks every record that referenced the row), and **branching on a code
string** (it freezes the list and reintroduces the deployment this engine removes).
