# ET-IDN-002 · Keycloak ↔ MongoDB user synchronisation — tasks

> **Spec** [`specs/identity/002-keycloak-user-sync/spec.md`](../identity/002-keycloak-user-sync/spec.md) · **Wave 1** · `blocked_by:` ET-PLT-002, ET-PLT-005, ET-PLT-007, ET-IDN-001
> **Screens** — **none.** The design authority's Coverage map is explicit: *"Keycloak → MongoDB user sync is a backend listener with no UI of its own."* Building a screen for it is a defect.
> **Verify** `mvn -q -f backend/identity-service test -Dgroups=ET-IDN-002 -DfailIfNoTests=false` · `mvn -q -f backend/keycloak-extensions package`

The subtle spec. Two stores hold user data, and the only thing keeping them honest is a strict
rule about **which store owns which field**. Get that wrong and both are authoritative, which
means neither is.

## R0 · Reconcile *(do this first)*

`UserSyncEventListener`, `UserSyncEventListenerFactory`, `KeycloakSyncController` and
`UserSyncService` already exist. The requirements existing implementations usually miss:

- Does the listener **swallow and log**, or can it fail a login? A sync listener that can break
  authentication has inverted the dependency.
- Is there a **2-second timeout**? Without it, a slow identity-service makes Keycloak slow.
- Does sync write only Keycloak-owned fields, or does it overwrite MongoDB-owned ones?
- **`keycloakUserId`** — R1 requires it not to exist ([`ET-PLT-007`](ET-PLT-007.md) R4). Almost
  certainly present: `contradicted`.
- Is there a **lazy repair** path, or does the platform depend entirely on the listener firing?

## A · Backend

### BE-1 · `identity_users`, its ownership split, its indexes
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** no
- **The document `_id` is the Keycloak `sub`.** There is no second identifier.
- §4 splits every field into Keycloak-owned (credentials, email verification, enablement) and
  MongoDB-owned (profile, preferences, organization membership). **Write that split down in code**
  — a comment is not enforcement; a test over the field list is.
- **Acceptance** no `keycloakUserId` anywhere; concurrent upserts produce **one** document.

### BE-2 · `UserSyncService` — upsert, field-ownership enforcement, event mapping
- **Spec** R1, R2 · **§5** T2 · **depends** BE-1 · **parallel-safe** no
- Events per §4: `REGISTER`, `UPDATE_PROFILE`, `UPDATE_EMAIL`, `VERIFY_EMAIL`, `LOGIN`, and the
  admin `CREATE`/`UPDATE`/`DELETE`.
- **Acceptance** each Keycloak event produces **exactly** the §4 action and **touches no
  MongoDB-owned field**.

### BE-3 · `UserSyncEventListener` SPI — swallow-and-log, 2-second timeout
- **Spec** R2 · **§5** T3 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** **a login succeeds with identity-service stopped.** Test it by actually stopping
  the container, not by mocking a failure — the timeout is the thing being tested and a mock
  returns instantly.

### BE-4 · Lazy repair on the authenticated request path
- **Spec** R3 · **§5** T4 · **depends** BE-2 · **parallel-safe** no *(it is on every request path)*
- **Acceptance** with the listener **disabled entirely**, a new user logs in and the platform is
  fully functional.
- This is the requirement that makes BE-3's swallow-and-log safe. The listener is an optimisation;
  correctness comes from repair on the request path. If the platform breaks when the listener is
  off, the listener was load-bearing and R2 was never really satisfied.

### BE-5 · The reconciliation Schedule, the backfill workflow, its metrics, the operator mutation
- **Spec** R4 · **§5** T5 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** seeded drift of **each kind** repaired in one run; a fire while a run is open is
  **skipped** (overlap `SKIP`), and an operator request reaches the running backfill.

- [x] **BE-5 · the Schedule and the workflow, 2026-09-13** — `identity-user-reconciliation` fires daily
  at 03:30 UTC and starts `UserBackfillWorkflow`, which pages Keycloak 100 users a run and signals each
  user's `UserSyncWorkflow`; the operator mutation and `POST /api/internal/keycloak/sync/all` start the
  same workflow. `UserBackfillWorkflowTest` (7 cases, time skipping, replayed) and
  `UserReconciliationScheduleTest` (3 cases). Marking documents for users Keycloak no longer holds is
  not built ([F-032](../FINDINGS.md)).

### BE-6 · `updateMyProfile` with the write-through order
- **Spec** R5 · **§5** T6 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** each field lands in **exactly one** store; a Keycloak failure **refuses and
  writes nothing** — no half-applied profile update.

### BE-7 · `changePhoneNumber` behind OTP verification of the **new** number
- **Spec** R6 · **§5** T7 · **depends** BE-6 · **parallel-safe** yes
- **Acceptance** no profile input accepts a phone field; a number held by another user refuses
  **without disclosing** that it is held.
- The phone number is the login credential ([`ET-IDN-001`](ET-IDN-001.md)). Changing it through a
  profile form is an account-takeover primitive, which is why it is a separate, OTP-gated
  operation.

### BE-8 · Deletion tombstone and the orphan-resolution test
- **Spec** R7 · **§5** T8 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** a deleted user's tickets **still resolve their owner**; `me` refuses with
  `USER_UNKNOWN`.
- A hard delete breaks every federated `Ticket.owner` reference into a null the router cannot
  explain. The tombstone is what keeps history readable — and it is what
  [`ET-PLT-008`](ET-PLT-008.md)'s anonymised retention builds on.

## B · Contract

### GQL-1 · Subgraph half — 3 queries, 5 mutations
- **depends** BE-6, BE-7, BE-8 · **parallel-safe** no *(shared identity SDL)*
- `@auth` on every field; `@tag` every admin element.
- Then `compose-supergraph.sh --static` → `npm run codegen` → commit generated types.
- **Restart the local router** after recomposing; hot-reload does not reliably fire here.

## C · Frontend

**No screen.** The Coverage map says so explicitly, and §6 of the spec agrees.

Two indirect obligations only, and neither is a new surface:

### FE-1 · Profile fields bind to the ownership split
- **depends** GQL-1 · **parallel-safe** yes
- The existing profile forms (`apps/organization-admin/.../settings/profile`,
  `Ticketing - Profile & Registration`) must not offer a phone field —
  `changePhoneNumber` is its own OTP-gated flow (BE-7).
- **Acceptance** no profile form submits a phone number.

### FE-2 · `USER_UNKNOWN` renders as a sign-out, not a retry
- **depends** [`ET-PLT-005`](ET-PLT-005.md) FE-1 · **parallel-safe** yes
- A tombstoned user retrying `me` forever is a loop.

## D · Tests

### TS-1 · Ownership *(L1)*
Reflective test over the field list: every field is assigned to exactly one store. A field in
neither, or both, fails.

### TS-2 · Event mapping *(L3, Keycloak Testcontainer)*
Each of the eight events → exactly the §4 action; no MongoDB-owned field touched.

### TS-3 · Independence *(L3 — the important one)*
- **Stop identity-service; log in.** The login succeeds.
- **Disable the listener entirely; log in as a new user.** The platform is fully functional via
  lazy repair.

Both by stopping real containers. Mocking the failure tests the mock.

### TS-4 · Drift *(L3)*
Seed each drift kind; one reconciliation run repairs all; an overlapping fire is skipped.

### TS-5 · Profile and phone *(L2/L3)*
- Each field lands in one store; Keycloak failure writes nothing.
- Phone change requires OTP on the **new** number; a taken number refuses without disclosure.

### TS-6 · Tombstone *(L3)*
Deleted user's tickets resolve their owner; `me` → `USER_UNKNOWN`.

## E · Gate

- [ ] R0 recorded; `keycloakUserId` classified `contradicted` and removed
- [ ] `_id` **is** the Keycloak `sub`; no second identifier
- [ ] Field-ownership split enforced by a test, not a comment
- [ ] Login succeeds with identity-service **stopped** (real container, not a mock)
- [ ] Platform fully functional with the listener **disabled**
- [ ] The reconciliation run repairs every drift kind; overlap is `SKIP`
- [ ] Profile writes each field to exactly one store; Keycloak failure writes nothing
- [ ] Phone change is OTP-gated on the new number; taken numbers refuse without disclosure
- [ ] Tombstoned users' tickets still resolve their owner
- [ ] **No screen was built for this spec**
- [ ] `compose-supergraph.sh --static` green; codegen clean and committed
- [ ] `mvn -q -f backend/identity-service test -Dgroups=ET-IDN-002 -DfailIfNoTests=false` green
- [ ] Spec `status:` → `implemented`
