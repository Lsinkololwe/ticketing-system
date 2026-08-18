# ET-ORG-001 · Organizer onboarding — tasks

> **Spec** [`specs/organization/001-organizer-onboarding/spec.md`](../organization/001-organizer-onboarding/spec.md) · **Wave 1** · `blocked_by:` ET-PLT-002, ET-PLT-003, ET-PLT-005, ET-PLT-007, ET-IDN-002
> **Screens** `Org Admin - Onboarding Wizard.dc.html` *(applicant)* and `Admin - Approvals Workbench.dc.html` *(reviewer)* — **read both**
> **Routes** `apps/organization-admin/src/app/(application)/apply/{business-info,documents,review,status}`, `welcome`, `unavailable`; `apps/admin/src/app/(dashboard)/approvals/{organizers,documents}`, `organizers/[id]`
> **Verify** `mvn -q -f backend/identity-service test -Dgroups=ET-ORG-001 -DfailIfNoTests=true` · `compose-supergraph.sh --static`

> ⚠️ **Corpus defect — the spec wins.** [`ROADMAP.md`](../ROADMAP.md) line 84 describes this as
> *"six states"*. The spec's §4 defines **nine states**, ten actions, 100 pairs, **15 legal**.
> Build nine. Per [README §Precedence](../README.md#precedence) `specs/` outranks the roadmap;
> fix the roadmap row as part of this slice.

The largest Wave 1 spec (4,338 words, 8 requirements, 60 boxes) and the one with the most
existing code — `OrganizationOnboardingServiceImpl`, `RequiredDocuments` and
`OrganizationApplicationInput` already carry `ET-ORG-001` references.

## R0 · Reconcile *(do this first, and here it is substantial)*

```bash
grep -rn 'ET-ORG-001' backend --include='*.java'          # 7 references exist
grep -rn 'OrganizationStatus' backend/identity-service --include='*.java'
```

Classify all 8 requirements. Expect a mix — this is the most `partially-satisfied` spec in the
corpus. Specifically check:
- Does `OrganizationStatus` declare **nine** states, or the older six?
- Does `OrganizationTransitions.LEGAL` exist as a table, or are transitions written as `if`
  statements across services? Literal status assignment anywhere is `contradicted` (R2).
- Is the approval **saga** resumable, or a straight-line method that can half-apply?
- `docs/ORG_ADMIN_SPEC_CONFORMANCE.md` is an input **here**, in R0 — not to the spec.

## A · Backend

### BE-1 · Document, `OrganizationStatus`, `OrganizationTransitions`, the 100-pair test
- **Spec** R1, R2 · **§5** T1 · **depends** R0 · **parallel-safe** no *(everything depends on it)*
- Nine states: `DRAFT`, `PENDING_DOCUMENTS`, `PENDING_REVIEW`, `CHANGES_REQUESTED`, `ACTIVE`,
  `REJECTED`, `SUSPENDED`, `INACTIVE`, `PENDING_DELETION`.
- **Acceptance** `LEGAL` holds exactly **15** rows and equals §4 row for row; the test drives all
  **100** `(status, action)` pairs, asserting 15 allow and **85 refuse**; **no status literal in
  any mutation**.
- `slug` is unique and **immutable once `ACTIVE`** — a public URL that changes breaks every link
  ever shared.

### BE-2 · `applyToBeOrganizer`, slug generation, the concurrent-application test
- **Spec** R1 · **§5** T2 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** two concurrent applications produce **one** organization.

### BE-3 · Document upload — presigned URLs, registration, type and size enforcement
- **Spec** R4 · **§5** T3 · **depends** BE-1 · **parallel-safe** yes
- **D-11: REST with presigned URLs, never GraphQL multipart.** Streams to storage, no CSRF
  surface, native progress, and the server never buffers the file.
- **Acceptance** **no byte passes a resolver**; an unissued `fileKey` is refused; an oversize file
  is refused **server-side** (a client-side size check is a suggestion).

### BE-4 · `submitForReview` with the business-type document function
- **Spec** R3 · **§5** T4 · **depends** BE-1, BE-3 · **parallel-safe** yes
- **Acceptance** a **sole proprietor is never asked for incorporation**; refusals **name what is
  missing**. "Documents incomplete" makes the applicant guess; naming the missing document is the
  difference between a resubmission and an abandoned application.
- `PROOF_OF_ADDRESS` and `BANK_STATEMENT` are optional everywhere — a reviewer asks for them via
  `requestOrganizationChanges` when something does not add up.

### BE-5 · The three review outcomes and per-document review
- **Spec** R4, R5 · **§5** T5 · **depends** BE-4 · **parallel-safe** yes
- **Acceptance** **reject** and **request-changes** are distinct states with distinct
  notifications. Collapsing them tells a fixable applicant they failed.

### BE-6 · The approval saga — six steps, persisted marker, retry, compensation
- **Spec** R6 · **§5** T6 · **depends** BE-5 · **parallel-safe** **no — the highest-risk write in this spec**
- Step 1 sets `ACTIVE`, `approvedAt`, `commissionRate`, `payoutSchedule`; step 6 publishes
  `identity.OrganizationApproved`. The saga's state lives **on the organization document** — no
  separate saga collection.
- **Acceptance** killing the process after **each step in turn** yields completion or **full**
  compensation, **never partial**. Six kill points, six tests.

### BE-7 · `OrganizationCapabilities` and the internal resolve endpoint
- **Spec** R7 · **§5** T7 · **depends** BE-1 · **parallel-safe** no *(two other services consume it)*
- The staged-access matrix from §4 — e.g. a `PENDING_REVIEW` organization may create a **draft**
  event but not publish one.
- **Acceptance** catalog and booking gate on the **same predicate**; the matrix exists in **one
  place**. Two copies of a capability matrix diverge the first time a state is added.

### BE-8 · Suspension, deactivation, deletion request, cancellation
- **Spec** R8 · **§5** T8 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** **no mutation deletes a document**; tickets and escrow **survive suspension**.
- Suspending an organization must not reach into money already owed to it or tickets buyers
  already hold. Deletion is a request with a grace period, not an operation.
- Fires [`ET-IDN-003`](ET-IDN-003.md) BE-7's revocation trigger.

### BE-9 · Subgraph half — types, public projection, `@auth` on every field
- **Spec** R1–R8 · **§5** T9 · **depends** BE-2, BE-5, BE-8 · **parallel-safe** **no — shared identity SDL with [`ET-ORG-002`](ET-ORG-002.md) and [`ET-ORG-003`](ET-ORG-003.md)**
- 6 queries, 12 mutations.
- **Acceptance** `compose-supergraph.sh --static`; **`organizationBySlug` exposes no KYB field.**
  The public projection is a different type, not the same type with fields hidden by resolver
  logic — that is how a KYB document number ends up in a public API response.

## B · Contract

### GQL-1 · Compose, generate, verify roles
- **depends** BE-9 · **parallel-safe** no
- `compose-supergraph.sh --static` → `npm run codegen` → commit. **Restart the local router.**
- Every admin element `@tag`ged; the public contract carries no KYB vocabulary.
- **Acceptance** each of the 18 operations appears in `docs/FRONTEND_GRAPHQL_CONTRACT.md` with its
  role.

## C · Frontend — two surfaces, two apps

### Applicant · `Org Admin - Onboarding Wizard.dc.html` *(read it first)*

### FE-1 · The wizard shell — nine states mapped to four steps
- **depends** GQL-1, F0-2 · **parallel-safe** no
- Routes exist: `apply/business-info` → `apply/documents` → `apply/review` → `apply/status`.
- **The status drives the route, not the other way round.** On load, resolve the organization's
  state and land the applicant on the correct step. A wizard that remembers its step client-side
  sends a `CHANGES_REQUESTED` applicant back to step one.
- `CHANGES_REQUESTED` re-enters at documents **with the reviewer's reason displayed**.
- **testids** `apply-step-<n>`, `apply-status-banner`, `apply-changes-reason`

### FE-2 · Business info step
- **depends** FE-1 · **parallel-safe** yes
- Business type selection drives the document set (BE-4). Choosing "sole proprietor" must visibly
  shorten the requirements list — that feedback is what stops the applicant hunting for a
  certificate they will never have.
- Zod schema shared with the server contract; field errors from `extensions.fields`.
- **testids** `business-type-select`, `business-name-input`, `business-info-submit`

### FE-3 · Documents step — presigned upload with real progress
- **depends** FE-2, BE-3 · **parallel-safe** no
- Request upload URL → `PUT` bytes directly to storage with progress → register metadata. Three
  calls, and the middle one does not touch the platform.
- Per-document state: required / uploaded / under review / rejected-with-reason.
- **Reserve the layout space for progress before upload starts** — content jumping on a slow
  connection is the norm here, not the exception.
- **testids** `document-upload-<type>`, `document-progress-<type>`, `document-status-<type>`

### FE-4 · Review and submit
- **depends** FE-3 · **parallel-safe** yes
- Read-only summary; `submitForReview` refuses name **what is missing** (BE-4) and each missing
  item links back to its step.
- **testids** `apply-review-summary`, `apply-submit`, `apply-missing-<type>`

### FE-5 · Status screen — five terminal-ish states
- **depends** FE-1 · **parallel-safe** yes
- `PENDING_REVIEW` (waiting, with expectation set), `CHANGES_REQUESTED` (reason + resubmit),
  `REJECTED` (reason + re-apply, which is transition 8 → `DRAFT`), `ACTIVE` (enter dashboard),
  `SUSPENDED`.
- Statuses **humanised** — never `PENDING_REVIEW` on screen (**F0-7**).
- **testids** `apply-status-<status>`, `apply-reapply`

### FE-6 · Staged access in the dashboard shell
- **depends** BE-7, GQL-1 · **parallel-safe** yes
- `OrganizationCapabilities` drives what the sidebar offers. A `PENDING_REVIEW` organization sees
  event creation but not publish. Disabled affordances **say why** — a greyed button with no
  explanation reads as a bug.
- **Acceptance** the UI gates on the same capability predicate the backend uses, fetched, never
  reimplemented client-side.

### Reviewer · `Admin - Approvals Workbench.dc.html` *(read it first)*

### FE-7 · Organizer approval queue
- **depends** GQL-1 · **parallel-safe** yes
- Route `apps/admin/.../approvals/organizers`. Table shape, columns and empty state come from the
  screen. Sidebar grouping comes from the screen. Do not invent either.
- Actions read exactly **"Approve" / "Reject" / "Request Changes"** — never vaguer verbs.
- **testids** `approval-queue-row`, `approval-approve`, `approval-reject`, `approval-request-changes`

### FE-8 · Applicant detail and per-document review
- **depends** FE-7 · **parallel-safe** yes
- Route `approvals/documents` and `organizers/[id]`. Document viewer; per-document approve/reject
  with reason; the KYB fields that the **public** projection must never carry.
- Reject and request-changes are **different buttons with different consequences** (BE-5) — the UI
  must not blur them into one "decline".
- **testids** `document-review-<id>`, `document-approve`, `document-reject`, `document-reject-reason`

### FE-9 · Suspension / reactivation / deletion controls
- **depends** BE-8, GQL-1 · **parallel-safe** yes
- Destructive and outward-facing: confirm first, and state the consequence plainly — suspension
  signs out every member ([`ET-IDN-003`](ET-IDN-003.md) BE-7) while tickets and escrow survive.
- **testids** `org-suspend`, `org-suspend-confirm`, `org-reactivate`

## D · Tests

### TS-1 · State machine *(L1)*
All **100** pairs: 15 allow, 85 refuse with `ORGANIZATION_STATE_INVALID` carrying `currentStatus`.
No status literal anywhere — assert by source scan, so a future `if` cannot reintroduce one.

### TS-2 · Application *(L3)*
Two concurrent applications → one organization. Slug unique; immutable once `ACTIVE`.

### TS-3 · Documents *(L3)*
No byte through a resolver; unissued `fileKey` refused; oversize refused server-side; sole
proprietor never asked for incorporation; refusal names the missing document.

### TS-4 · The saga *(L3 — the highest-risk test in this spec)*
Kill after **each of the six steps**: completion or full compensation, never partial. Six tests,
one per kill point. `Persistence.assertNothingPersisted` on every compensated path.

### TS-5 · Capabilities *(L2/L3)*
Catalog and booking gate on the same predicate; the matrix exists in one place; each of the nine
states yields the §4 capability row.

### TS-6 · Lifecycle *(L3)*
No mutation deletes a document; tickets and escrow survive suspension; suspension revokes member
sessions.

### TS-7 · Contract *(L4)*
Static composition; `organizationBySlug` exposes **no** KYB field — assert on the composed public
contract, not on the resolver.

### TS-8 · Applicant e2e *(L5, org-admin)*
- **Requires F0-2** — org-admin has no auth harness and `page.route` cannot reach a Server
  Component's fetch. Without it these specs prove nothing.
- Full path: apply → business info → documents (upload with progress) → review → submit → status.
- `CHANGES_REQUESTED` re-entry lands on documents with the reason shown.
- `REJECTED` → re-apply returns to `DRAFT`.
- Loading, empty, error, populated on every step.

### TS-9 · Reviewer e2e *(L5, admin)*
- Queue: loading, **empty** (a designed screen), error, populated.
- Approve / reject / request-changes each produce their distinct state and notification.
- Suspension confirms before firing.
- Compliance suite: teal accent, `data-brand="admin"`, Inter, no hex, no `px`.

## E · Gate

- [ ] R0 recorded across all 8 requirements; `ORG_ADMIN_SPEC_CONFORMANCE.md` read here
- [ ] **Nine** states, 15 legal transitions, 100-pair test green — and `ROADMAP.md` line 84 corrected
- [ ] No status literal in any mutation
- [ ] Uploads presigned; no byte through a resolver; oversize refused server-side
- [ ] Sole proprietor never asked for incorporation; refusals name what is missing
- [ ] Reject and request-changes distinct, in backend **and** UI
- [ ] Six saga kill points each yield completion or full compensation
- [ ] Capability matrix in one place, consumed by catalog and booking
- [ ] Nothing deleted; tickets and escrow survive suspension
- [ ] `organizationBySlug` carries no KYB field, asserted on the composed contract
- [ ] Both `.dc.html` screens read; layout, table shape and empty states match
- [ ] Applicant e2e green **on the F0-2 auth harness**
- [ ] Compliance suite green on both apps
- [ ] `mvn -q -f backend/identity-service test -Dgroups=ET-ORG-001 -DfailIfNoTests=true` green
- [ ] Spec `status:` → `implemented`
