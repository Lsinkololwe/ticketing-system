# ET-PLT-014 · The reference data engine — tasks

> **Spec** [`specs/_platform/014-reference-data-engine/spec.md`](../_platform/014-reference-data-engine/spec.md) · **Wave 2** · `blocked_by:` ET-PLT-002, ET-PLT-004, ET-PLT-005
> **Status** `approved` — cleared to build
> **Screen** `Admin - Transactions & System.dc.html` *(reference data)* — **read it first**
> **Routes** `apps/admin/src/app/(dashboard)/system/reference-data`
> **Verify** `mvn -q -f backend/catalog-service test -Dgroups=ET-PLT-014 -DfailIfNoTests=true` · `compose-supergraph.sh --static`

The engine behind every list the business owns: operators, banks, categories, genres, KYB document
types, reason codes, tax rates — **and workflow statuses**, which look identical on screen and are
not, because code branches on them.

Its two governing ideas: **types are compiled, rows are runtime**, and **workflow rows are
reflected out of the enums rather than seeded**, so the value list cannot drift from what the code
accepts.

## R0 · Reconcile *(substantial — much of this exists)*

`ReferenceType`, `ReferenceGroup`, `WorkflowSemantic`, `ReferenceData`, `ReferenceDataSource`,
`ReferenceDataRegistrations` and `ReferenceDataBootstrapper` are already present, several of them
untracked.

```bash
grep -rn 'ReferenceType\|WorkflowSemantic\|ReferenceData' backend --include='*.java' | grep -v /src/test/
```

Four checks decide the size of this slice:

1. **Collection name.** The document must be `catalog_reference_data` to satisfy the
   [`ET-PLT-002`](ET-PLT-002.md) §4 prefixed registry. An unprefixed `reference_data` is a
   **registry violation**, not a preference — record it as `contradicted` and rename it in BE-1.
2. **Is the bootstrapper actually invoked at boot**, or is it an `@Service` nothing calls? A
   registered bean that never runs passes every test while doing nothing — the same failure mode as
   several of the untracked migration services. Check the call site, not the annotation.
3. **Does any code branch on a status *code string*** rather than its semantic? Each one freezes
   the list and is `contradicted` (R5).
4. **Does every registered workflow constant have a declared semantic?** A missing one must abort
   the boot, not default.

## A · Backend

### BE-1 · `ReferenceType`, `ReferenceGroup`, `WorkflowSemantic` and the document
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** no
- **Rename the collection to `catalog_reference_data`** and add it to the ET-PLT-002 §4 registry
  *(already done in the registry — the code must follow)*.
- **Acceptance** the unique index on `{ type, code }` refuses a duplicate **against a live
  database** — confirm with MongoDB MCP `collection-indexes`, including that it is genuinely
  unique; `ReferenceType` is a compiled enum and **no mutation creates a type**; every row carries
  `type`, `code`, `label`, `displayOrder`, `active` and typed `metadata`.
- The unique index is what makes concurrent bootstrap safe: two instances starting together both
  attempt the insert and one takes a duplicate-key, which is retained rather than an error.

### BE-2 · The registration table — enum class per type, semantic per constant
- **Spec** R4 · **§5** T2 · **depends** BE-1 · **parallel-safe** no
- **Acceptance** every constant of every registered workflow enum has a **declared** semantic;
  **no name-based inference exists anywhere**.
- This is the one thing written by hand, and deliberately so. A constant's name is a hint, not a
  fact: `PENDING_REVIEW` genuinely is *pending*; `PENDING_VERIFICATION` on a ticket is a payment
  still in flight; `PENDING_DELETION` on an organization is a live account with a countdown on it.
  A pattern match on the word "PENDING" gets one of those three right.

### BE-3 · The reflecting bootstrapper — insert-only, idempotent, fail-loud
- **Spec** R2, R3 · **§5** T3 · **depends** BE-2 · **parallel-safe** no
- Reflects over each registered enum class; **no file enumerates the constants.** Adding a constant
  produces a row on the next boot with no other change.
- Derived `label` is the humanised constant: `PENDING_FINANCE_APPROVAL` → `Pending finance approval`.
- **Acceptance** a second run inserts nothing and reports `retained`; **an administrator's rename,
  recolour, reorder or deactivation survives a boot**; an unclassified constant **aborts the boot,
  naming type and constant**; an **empty registry is an error, not "nothing to seed"**.
- That last one is the subtle failure: if the registrations' static initialiser has not run, an
  empty registry reads as *nothing to do* and the bootstrap silently no-ops. Force the
  initialisation before reading the registry, and treat empty as an error.

### BE-4 · Semantic-only branching, and the source scan that enforces it
- **Spec** R5 · **§5** T4 · **depends** BE-2 · **parallel-safe** yes *(one service per agent)*
- **Acceptance** **no production branch compares a status to a reference `code` literal**, asserted
  by source scan; a **runtime-added code under an existing semantic is handled by the existing
  branch**, asserted end to end by adding a status through the admin API and driving the flow.
- Six semantics, any number of codes. That end-to-end test is the whole promise of this engine: an
  administrator adds `AWAITING_COMPLIANCE_REVIEW` to the payout statuses and the money still moves
  through the branch that handles *pending*, with no deployment.

### BE-5 · Code-owned machines and the transition-edit refusal
- **Spec** R6 · **§5** T5 · **depends** BE-3 · **parallel-safe** yes
- `TICKET_STATUS`, `RESERVATION_STATUS` and `PAYMENT_STATUS` are code-owned — their transition
  tables belong to [`ET-TKT-002`](ET-TKT-002.md), [`ET-TKT-001`](ET-TKT-001.md) and
  [`ET-PAY-001`](ET-PAY-001.md) and are not the administrator's to redraw.
- **Acceptance** a transition edit on a code-owned type refuses with
  `REFERENCE_MACHINE_CODE_OWNED`; a **display row on one is accepted** and appears in reporting.
- Both halves. Refusing new rows outright would stop an operator adding a reporting distinction;
  accepting transition edits would let them reroute money.

### BE-6 · Per-type metadata validation and collection-level schema
- **Spec** R1 · **§5** T6 · **depends** BE-1 · **parallel-safe** yes *(one type per agent)*
- MSISDN prefixes for an operator, SWIFT for a bank, rate and effective dates for a tax rate.
- **Acceptance** each type's contract refuses its violation **at the service layer and at the
  collection**. Two layers because the collection schema catches anything that reaches the database
  by another path.

### BE-7 · Deactivation everywhere; the past-record rendering test
- **Spec** R7 · **§5** T7 · **depends** BE-5 · **parallel-safe** yes
- **Acceptance** **no delete mutation exists** in the composed schema; a past event, ticket or
  organization renders correctly after **each** reference kind is deactivated, in turn;
  deactivation is audited; reactivation restores selection without altering historical records.

### BE-8 · Caching, cross-instance eviction, and the no-polling assertion
- **Spec** R8 · **§5** T8 · **depends** BE-6 · **parallel-safe** yes
- **Acceptance** cold equals warm; a mutation evicts the affected type across every instance within
  `PT5S`; **no scheduled task refreshes reference data**, asserted on the absence of a scheduled
  bean rather than on behaviour; the picker query reports `IXSCAN` on `{ type, active, displayOrder }`.
- **A cache miss against an unreachable store must not return an empty list** — an empty picker is
  indistinguishable from a list that genuinely has no rows, and the operator picks nothing and
  concludes the data is gone.

### BE-9 · The subgraph half; the grouped type picker; `@auth` on every field
- **Spec** R1–R8 · **§5** T9 · **depends** BE-7 · **parallel-safe** **no — shared catalog SDL with CAT-001/002/003**
- **Acceptance** static composition; **the admin type picker renders from `referenceTypes` with no
  hardcoded list on the client.** A client-side list of types is a second registry that drifts the
  first time a type is added.

## B · Contract

### GQL-1 · 3 queries, 4 mutations
- **depends** BE-9 · **parallel-safe** no
- Reference **reads are public** (a buyer's category filter needs them); every **mutation is
  `ADMIN`** and `@tag`ged. There is no `deleteReferenceRow`.
- `compose-supergraph.sh --static` → `npm run codegen` → commit; restart the local router.

## C · Frontend — `Admin - Transactions & System.dc.html`

### FE-1 · Grouped type picker
- **depends** GQL-1 · **parallel-safe** no
- Rendered from `referenceTypes`, grouped by the §4 groups. **No hardcoded type list** (BE-9).
- **testids** `reference-type-group`, `reference-type-option`

### FE-2 · Row management
- **depends** FE-1 · **parallel-safe** no
- Create, edit label, reorder (drag or explicit order), deactivate. **No delete anywhere** — the
  action does not exist, so the UI must not offer one.
- Deactivation confirms and states the consequence: it disappears from selection, existing records
  keep it.
- **testids** `reference-row`, `reference-create`, `reference-edit`, `reference-reorder`, `reference-deactivate`, `reference-deactivate-confirm`

### FE-3 · Workflow types — the semantic is mandatory and consequential
- **depends** BE-2, BE-4 · **parallel-safe** no
- Adding a status **requires** a semantic, and the picker explains what each of the six means in
  behavioural terms — *"PENDING: the platform is waiting; the row stays in the queue"* — not just
  the constant name.
- Existing rows show their semantic. Changing one is called out as **behavioural and prospective**.
- **The administrator is choosing which branch this status routes to.** If the UI presents it as a
  colour-like attribute, someone will file a failure under `SUCCEEDED` and money will move.
- **testids** `reference-semantic-select`, `reference-semantic-help`, `reference-semantic-change-warning`

### FE-4 · Code-owned types are read-only for transitions
- **depends** BE-5 · **parallel-safe** yes
- Marked plainly, with **which spec owns the machine**. Presentation fields stay editable; the
  transition table is displayed and not editable.
- **testids** `reference-code-owned-badge`, `reference-transitions-readonly`

### FE-5 · Per-type metadata forms
- **depends** BE-6 · **parallel-safe** yes
- The metadata form is driven by the type — MSISDN prefixes for an operator, SWIFT for a bank.
  Validation mirrors the server contract; the server remains the rule.
- **testids** `reference-metadata-<type>`, `reference-metadata-error`

### FE-6 · Pickers across all three apps consume this engine
- **depends** GQL-1 · **parallel-safe** yes
- Category and genre filters in ticketing, KYB document types in the onboarding wizard, refund and
  cancellation reasons in admin — all from `referenceData(type)`.
- **No app hardcodes a list this engine owns.** A hardcoded reason dropdown is the deployment this
  engine exists to remove.
- Statuses render by **label**, never by code (**F0-7** humanisation applies to everything else).
- **Acceptance** the compliance suite asserts no hardcoded reference list in any app.

## D · Tests

### TS-1 · Collection and uniqueness *(L3)*
Named `catalog_reference_data`; unique `{ type, code }` confirmed **live** via MCP; concurrent
bootstrap from two instances produces one row per code.

### TS-2 · Reflection *(L3)*
Adding a constant to a registered enum produces a row on the next boot with no other change.
Humanised label correct. **Empty registry errors.**

### TS-3 · Insert-only *(L3 — the administrator's contract)*
Rename, recolour, reorder and deactivate a row, then boot: **all four survive**. No boot path
issues an update or delete.

### TS-4 · Semantics *(L1)*
Every registered constant classified. An unclassified constant **aborts the boot naming type and
constant**. No name-based inference exists.

### TS-5 · Semantic branching *(L3 + source scan — the point of the engine)*
No branch compares a status to a code literal. **Add a status at runtime through the admin API and
drive the flow end to end**; the existing branch handles it.

### TS-6 · Code-owned *(L3)*
Transition edit refused; display row accepted; the owning specs' transition tables unchanged.

### TS-7 · Metadata *(L3)* — each type's contract refused at service **and** collection layer.

### TS-8 · Deactivation *(L3)*
No delete mutation in the composed schema; a past record renders after each reference kind is
deactivated; reactivation changes no historical record.

### TS-9 · Cache *(L3)*
Cold equals warm; eviction across two instances within `PT5S`; **no scheduled refresh bean exists**;
`IXSCAN` on the picker query; an unreachable store does **not** yield an empty list.

### TS-10 · e2e *(L5, admin + the consuming apps)*
- Type picker from `referenceTypes`; row CRUD **without delete**; reorder persists.
- Adding a workflow status demands a semantic and explains the six.
- Code-owned type shows read-only transitions with its owning spec named.
- A newly added reason code appears in the admin refund form **without a deployment**.
- Loading, empty, error, populated. Compliance: no hardcoded reference list in any app.

## E · Gate

- [x] **Spec `approved`** (2026-08-18)
- [ ] R0 recorded; the unprefixed collection name classified `contradicted` and renamed
- [ ] **Bootstrapper confirmed to actually run at boot** — call site verified, not just the annotation
- [ ] Unique `{ type, code }` index live; concurrent bootstrap safe
- [ ] Rows reflected from enums; no file enumerates constants; empty registry errors
- [ ] Insert-only: rename, colour, order and deactivation all survive a boot
- [ ] Every workflow constant classified; a missing semantic aborts the boot by name
- [ ] No name-based semantic inference anywhere
- [ ] **No branch compares a status to a code literal**, asserted by source scan
- [ ] A runtime-added status is handled by the existing branch, proven end to end
- [ ] Code-owned types refuse transition edits and accept display rows
- [ ] No delete mutation exists; past records render after every deactivation
- [ ] Cache evicts cross-instance; nothing polls; an unreachable store never returns an empty list
- [ ] No app hardcodes a list this engine owns
- [ ] Workflow semantics are presented as behavioural, not decorative
- [ ] `mvn -q -f backend/catalog-service test -Dgroups=ET-PLT-014 -DfailIfNoTests=true` green
- [ ] Spec `status:` → `implemented`
