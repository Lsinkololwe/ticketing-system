# ET-IDN-004 · Accounts and contacts — tasks

> **Spec** [`specs/identity/004-accounts-and-contacts/spec.md`](../identity/004-accounts-and-contacts/spec.md) · **Wave 1** · `blocked_by:` ET-PLT-002, ET-PLT-005, ET-PLT-007
> **Contract** [`CONTRACT.md`](../identity/004-accounts-and-contacts/CONTRACT.md) — binding; change it first if an implementation must deviate
> **Verify** `mvn -q -f backend/identity-service test -Dgroups=ET-IDN-004 -DfailIfNoTests=false`

An account is one application record per person; a contact is a verified WhatsApp number or email
owned by exactly one account; Keycloak holds a projection. Created identity-first by a workflow
(**D-43**), suspension decided by identity-service (**D-47**), no personal data in ids (**D-46**).

## R0 · Reconcile *(do this first)*

Measured 2026-10-04 by code review. No executing test exists for any of this area, so nothing is `already-satisfied`.

| Req | Class | Evidence | Action |
|---|---|---|---|
| R1 types and states | `contradicted` | `AccountTypeRoleMapper` lets registrants pick `ORGANIZER`; legacy `AccountStatus` only; one realm; `users-schema` requires email and names | add `status` and optional fields; remove the mapper (BE-1) |
| R2 identity-first creation | `contradicted` | the Keycloak authenticator creates users (`addUser`, `user_<last8>`) and grants `CUSTOMER`; no workflow; `KeycloakService` finds users by email and adopts on 409 | `AccountEnsureWorkflow` (BE-3); delete creation from the plugin |
| R3 contact uniqueness | `partially-satisfied` | phone uniqueness is a sparse index on `identity_users.phoneNumber`; no contacts collection, no encryption, no email contact; duplicate email indexes (partial + plain unique at `IdentityIndexInitializer` L283) | `identity_contacts` and `uniq_verified_contact` (BE-1) |
| R4 adoption and repair | `contradicted` | `UserSyncServiceImpl` trusts user-editable `roles`/`accountType` attributes, overwrites, and hard-deletes; the admin realm has no `user-sync` listener; a full-representation rule is not applied | rewrite as adoption plus repair (BE-4, BE-5) |
| R5 contact change | `contradicted` | phone change by profile mutation (`changePhoneNumber`) without re-proof of the new number's ownership as specified | `ContactChangeWorkflow` (BE-6) |
| R6 merge | `absent` | no merge exists | `AccountMergeWorkflow` (BE-7) |
| R7 deletion hook | `contradicted` | `UserSyncServiceImpl` hard-deletes on a Keycloak delete event | tombstone and hook (BE-8) |
| R8 consistency and gates | `absent` | no pending markers, no max ages, no consent gate | BE-5, BE-9 |

## A · Backend

### BE-1 · Model, indexes, validators, optional-field migration
- **Spec** R1, R3 · **§5** T1 · **depends** R0 · **parallel-safe** no
- `AccountState`, `ContactType`; `status`, `keycloakUserId`, `pendingKind`, `pendingSince` and the other optional fields on `identity_users`; `identity_contacts`, `identity_consents`, `identity_account_events`; validators for `identity_audit_logs` and `identity_token_revocations`; the duplicate plain email index dropped; `email`, `username`, `firstName`, `lastName` become optional in the schema.
- **Acceptance** two concurrent claims of one contact produce one winner (L3, real MongoDB); the index definitions verified against a running database (MongoDB MCP); legacy documents still read.

### BE-2 · `KeycloakAccountPort`
- **Spec** R2, R4 · **§5** T2 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** create by username with 409 read back; updates send a full representation (a partial update must not wipe attributes); `setEnabled`; `findByUsername`; no lookup by email.

### BE-3 · `AccountEnsureWorkflow`, activities, `AccountEnsurer`, queue `identity-account`
- **Spec** R2 · **§5** T3 · **depends** BE-1, BE-2 · **parallel-safe** no
- **Acceptance** worker killed after each step converges to one ACTIVE account; concurrent proofs yield one account and one Keycloak user; a lint fails on `@` or `+<digits>` in a workflow id or search attribute; replay test passes; the queue is added to `TaskQueues.java` and `application.yml` together with its [ET-PLT-015](ET-PLT-015.md) §4 registry row.

### BE-4 · Adoption and the slimmed event path
- **Spec** R4 · **§5** T4 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** body carries only the six fields of CONTRACT §4.6; unknown username adopted and flagged; `roles` and `accountType` attributes are never read for authority; hard delete replaced by a tombstone; the `user-sync` listener is present in **both** realms.

### BE-5 · `AccountRepairWorkflow`, Schedule, drift D1..D9, metrics
- **Spec** R4, R8 · **§5** T5 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** each class seeded and repaired exactly as the §4 table says; second run changes nothing; alerts at PT10M / PT2H / PT48H; security heals audited; the Schedule's cadence comes from code (D-34).

### BE-5 · `AccountRepairWorkflow`, Schedule, drift D1..D9, metrics - **done 2026-10-04**
- **Spec** R4, R8 · **§5** T5 · **depends** BE-3 · **parallel-safe** yes
- `AccountRepair` (three passes), `AccountRepairWorkflow` / `Activities` / `Schedule` / `ScheduleRunner`, ports `listUsers` and `readAttribute`; Schedule created at boot behind `identity.account.repair.enabled` (prod).
- **Tests** `AccountRepairEndToEndTest` (L2, real Keycloak: each class injected, repaired, audited, second run a no-op, 503 mid repair, Schedule), `AccountRepairTest` (L3).
- **Not done** the "two accounts for one person" half of D9 (no automatic signal); MERGING and DELETION_REQUESTED only alert until BE-7 and BE-8 exist.

### BE-6 · `ContactChangeWorkflow` and contact management - **done 2026-10-04**
- **Spec** R3, R5 · **§5** T6 · **depends** BE-3 · **parallel-safe** yes
- One workflow for ADD, CHANGE, REMOVE and PRIMARY; `ContactChangeService` behind the GraphQL operations of CONTRACT §14; `ContactChangeSteps` (claim, Keycloak, commit, sessions, marker, notices); `AccountRepair.clearStaleChanging` (D8, change part); quarantine in `ensure` creation; Keycloak admin client 26.0.12 (`setEmail`).
- **Decisions** F-044 (a) to (d), 2026-10-04: authorisation by a fresh code to the current primary; 30-day quarantine; last verified contact cannot be removed; one primary.
- **Acceptance** a lost claim changes nothing; success revokes sessions and updates the Keycloak email; both contacts are notified; `changePhoneNumber` removed from the profile (already gone); a crash at any step converges.
- **Tests** `ContactChangeWorkflowTest` (L3), `ContactChangeEndToEndTest` (L2), `ContactRulesTest`, `ContactChangeRulesTest`, `AccountRepairTest`.
- **Also (2026-10-04)** `resendContactCode`; add needs only the new contact's code (user decision).

### BE-7 · `AccountMergeWorkflow` and consumers - **withdrawn 2026-10-09 (product decision: no merge)**
- **Spec** R6 · **§5** T7
- Two accounts for one person are not merged. A merge needs proof of both accounts, a lock on both while it runs, and every other service re-pointing its data; a wrong one cannot be undone. Nothing in this task is built, and the `MERGING` marker and `ACCOUNT_MERGING` code stay only for the repair job's alerts.
- **Not decided** whether duplicates are prevented (a prompt after sign-in to add the other contact) or resolved by handing a contact over from an empty account. That proposal is recorded in F-049 and needs an owner's decision before any task is written.

### BE-8 · Deletion hook
- **Spec** R7 · **§5** T8 · **depends** BE-3, [`ET-PLT-008`](ET-PLT-008.md) T5 · **parallel-safe** yes
- **Acceptance** tombstone kept; contacts released; the Keycloak user deleted; id never reused.

### BE-9 · The three gates
- **Spec** R8 · **§5** T9 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** token, account state and consent each refuse independently with a typed error and no profile field.

## B · Contract

`subgraph: null`. The wire contract is [`CONTRACT.md`](../identity/004-accounts-and-contacts/CONTRACT.md). The four new error codes need documentation rows in [`ET-PLT-005`](ET-PLT-005.md) §4 and the proposed `identity.Account*` events need rows in [`ET-PLT-003`](ET-PLT-003.md) §4 before they are published.

## C · Frontend

No screen of its own. The admin app's staff-creation form and the support merge screen are specified with the admin specs; the buyer-facing contact-change step is a profile screen owned by [`ET-IDN-002`](ET-IDN-002.md).

## D · Tests

### TS-1 · Model *(L3)* — claim race; legacy documents; index definitions.
### TS-2 · Workflow *(L3, Temporal test server + Keycloak container)* — kill-after-each-step; replay; concurrent proofs; no personal data in ids. **Real-Keycloak ensure cases done** in `BuyerSignInEndToEndTest`: crash after claim then retry, orphan user adopted by username, Keycloak 5xx (proxy), Keycloak stopped.
### TS-3 · Repair *(L3)* — one seeded case per D1..D9; idempotent second run; alerts. **Done** as `AccountRepairEndToEndTest` (real Keycloak).
### TS-4 · Change and merge *(L3)* — lost claim; session revocation; sign-in refused while MERGING. **Change part done:** `ContactChangeWorkflowTest`, `ContactChangeEndToEndTest` (real Keycloak, claim race, quarantine, IDOR, outages, no contact in logs/Redis/events). Merge withdrawn (BE-7).
### TS-5 · Deletion *(L3)* — tombstone and release; no id reuse.
### TS-6 · Gates *(L3)* — each gate refuses independently.

## E · Gate

- [ ] R0 recorded; every requirement classified against the code review facts
- [ ] Two concurrent claims of one contact produce exactly one account and one Keycloak user
- [ ] Worker killed after every `AccountEnsureWorkflow` step converges; nothing is deleted on failure
- [ ] No workflow id, search attribute, payload or log contains a raw contact (lint)
- [ ] Drift D1..D9 each seeded and repaired once; a second run changes nothing
- [ ] Console change of `enabled` adopted and audited; user-editable attributes never grant authority
- [ ] Contact change, merge and deletion hook tested end to end with session revocation
- [ ] Hard delete in user sync replaced by a tombstone
- [ ] Three gates refuse independently
- [ ] Index definitions verified against a running database
- [ ] `mvn -q -f backend/identity-service test -Dgroups=ET-IDN-004 -DfailIfNoTests=false` green
- [ ] Spec `status:` -> `implemented`
