# ET-IDN-002 · Keycloak ↔ MongoDB user synchronisation

> **Conformance** · US Part I §6 Keycloak integration · US Part II §7 user CRUD
>
> **Amended 2026-10-04 (D-43, D-47, D-48; F-044).** This spec is **re-scoped to adoption and repair**: accounts
> are now created identity-first by [ET-IDN-004](../004-accounts-and-contacts/), the account (not the Keycloak
> user) is the record, and the listener only reports facts. Where this spec's earlier text disagrees with the
> list below, the list wins:
> 1. **`enabled` is owned by identity-service** (D-47). It decides suspension and applies it to Keycloak; a change made in the Keycloak console is adopted back into the account and audited. This **supersedes** the earlier statement in §2 that `enabled` lives only in Keycloak.
> 2. **No phone change by profile.** A contact changes only by re-proof through `ContactChangeWorkflow` ([ET-IDN-004](../004-accounts-and-contacts/) R5). R6 below is superseded and `changePhoneNumber` is removed.
> 3. **Tombstone, never hard delete**, on every path (R7 is unchanged in intent and now also governs the Keycloak delete event).
> 4. **Roles and account type are never read from user-editable Keycloak attributes** (`roles`, `accountType`); they derive from database policy ([ET-IDN-004](../004-accounts-and-contacts/) R4).
> 5. **Two realms** (D-48): the listener is enabled in both `myticketzm` and `myticketzm-admin`; the event body is `{eventId, eventType, userId, username, realm, enabled, emailVerified, timestamp}` (plus `sid` on `LOGOUT` and `REFRESH_TOKEN_ERROR` only, which identity-service turns into a session revocation, [ET-IDN-003](../003-token-revocation/) R7) and carries no attributes, names, roles or phone ([CONTRACT §4.6](../004-accounts-and-contacts/CONTRACT.md)).
> 6. Email, first name and last name are **optional** in Keycloak and in `identity_users`; buyers may have neither.
> 7. New accounts: `_id` = account UUID and Keycloak username = account id; legacy accounts keep `_id` = Keycloak id and are linked by `keycloakUserId`.

## 1. Capability

Keycloak knows who a person is. The platform needs to know rather more: what their avatar
is, which language they read, which organizations they belong to, what they have bought,
and what they should be sent. None of that belongs in an identity provider, and Keycloak's
own user store is a poor place to join against — so every authenticated person has a
document in `identity_users` whose `_id` **is** their Keycloak user ID, holding the
profile the platform owns and a cached copy of the few identity fields it constantly needs
to display.

Two copies of anything is a synchronisation problem, and this spec is how that problem is
made small rather than pretended away. It declares which fields Keycloak owns and which
MongoDB owns, so that no field has two writers. It declares the event listener that carries
Keycloak's changes across. And — because that listener runs inside Keycloak's own request
path and must never be allowed to fail a login — it declares the two repairs that make the
platform correct even when the listener does not fire: a lazy repair on the next
authenticated request, which costs nothing and fixes the common case, and a nightly
reconciliation workflow that finds the rest.

The design principle is that **the listener is an optimisation, not the guarantee**. A
platform whose correctness depends on a webhook from another process, delivered exactly
once, over a network, during a login, will be wrong. A platform that can rebuild any user
document from a JWT and the Admin API will not be.

It also declares the small user-facing surface this implies: `me`, profile updates, and the
narrow set of fields a profile update is allowed to write back into Keycloak.

## 2. Design decisions

**One field, one writer.** The split is total and there is no field on both sides.

| Field | Owner | Notes |
|---|---|---|
| credentials, MFA (staff only) | Keycloak | never mirrored |
| `enabled` | identity-service decides, applies to Keycloak (D-47) | a console change is adopted back and audited |
| `email`, `firstName`, `lastName`, `username` | Keycloak | **cached** in MongoDB for display and search |
| contacts (`phone`, `email`), `emailVerified` | MongoDB `identity_contacts` ([ET-IDN-004](../004-accounts-and-contacts/)) | Keycloak `email`/`emailVerified` derive from the primary email contact; no phone in Keycloak |
| realm roles | Keycloak, derived from database policy by identity-service | read from the JWT per request, never cached; user-editable attributes never grant authority |
| `avatarUrl`, `bio`, `dateOfBirth`, `locale`, `timezone` | MongoDB | never in Keycloak |
| `userType`, `accountStatus` | MongoDB | derived from platform state |
| `primaryOrganizationId` | MongoDB | mirrored to Keycloak as a routing convenience only |
| organization membership | MongoDB | [ET-ORG-002](../../organization/002-teams-and-invitations/) |

**`User.id` is the Keycloak user ID for legacy accounts.** *(Amended: new accounts use an account UUID and `keycloakUserId` links the two — see the amendment above.)* There is no `keycloakUserId` field for legacy accounts, because a second
identifier for one identity is where the two eventually disagree, and reconciling them is
work that exists only because the field does.

**The listener never fails a Keycloak operation.** `UserSyncEventListener` runs inside
Keycloak's request path. If identity-service is down, redeploying, or slow, a login must
still succeed — a user locked out of a concert because a profile cache was unavailable is a
worse outcome than a stale display name. The call is bounded by a short timeout, every
failure is swallowed and logged, and correctness comes from the repairs below.

**Lazy repair is the primary guarantee.** The first authenticated request from a user
whose document is missing creates it from the JWT claims — which carry the subject, the
username, the email, the name and the phone attributes. This costs one conditional write on
a path that is already reading the user, needs no coordination, and means a user who logs
in after a listener outage is simply correct. Anything the JWT does not carry is filled by
the Admin API on the same path.

**Reconciliation finds what neither the listener nor a login found.** A nightly Temporal
Schedule starts `UserBackfillWorkflow`, which pages through Keycloak's users and hands each to that
user's own `UserSyncWorkflow`, so a reconciliation and a live change for the same user are applied
one after the other by one writer. It compares in both directions: users in Keycloak with no
document, documents whose cached fields have drifted, and documents for users Keycloak no longer
has. The Schedule's overlap policy is its lock; it reports counts as metrics, and
repairs rather than reports — a reconciliation that only reports is a dashboard nobody
reads.

**Profile updates write through only for the three fields Keycloak owns.** A user changing
their display name changes it in both, in that order — Keycloak first, MongoDB second, so a
failure leaves MongoDB stale rather than leaving Keycloak wrong. Everything else is a
MongoDB write and never touches the identity provider.

**A contact is changed by re-proof, never by editing a profile field.** *(Superseded 2026-10-04: now [ET-IDN-004](../004-accounts-and-contacts/) R5; the text below is the original rationale.)*
Phone is the login identity. Allowing it to be edited like a display name means account
takeover by profile update. The change runs through [ET-IDN-001](../001-phone-otp-identity/)'s
verification against the new number, and only then is `phone_number` rewritten.

**Deletion is a status, and the document survives.** A user removed in Keycloak marks
`accountStatus = DELETED` in MongoDB and keeps the document, because tickets, payments and
ledger entries reference it and an orphaned foreign key is worse than a tombstone. Actual
erasure — anonymising the personal fields while retaining the financial record — is
[ET-PLT-008](../../_platform/008-data-protection/)'s, and this spec deliberately stops at
the marker. Reconciliation never infers deletion from absence: a document whose Keycloak user is
missing from a listing is left as it is, because a partial or failed read would otherwise tombstone
real accounts (ROADMAP D-29).

**Rejected alternatives**

- *Keycloak's user-storage SPI, with MongoDB as the federated store.* Inverts the dependency: every Keycloak login then requires MongoDB, and the identity provider inherits the platform's availability.
- *Reading users from Keycloak's Admin API on demand, with no local document.* A join against an HTTP API for every list of ticket buyers.
- *Making the listener synchronous and failing the Keycloak operation on sync failure.* Couples the ability to log in to a cache being writable.
- *Relying on the listener alone.* One at-most-once webhook between two processes, and the failure is silent and permanent.
- *A `keycloakUserId` field alongside `_id`.* Two identifiers for one identity.
- *Bidirectional sync for profile fields.* Two writers per field, and a last-write-wins race whose loser is whichever the user typed first.
- *Hard-deleting the document when Keycloak deletes the user.* Orphans every ticket, payment and journal line that references it.

## 3. Requirements

### ET-IDN-002-R1 · One identity, one document, one identifier

THE SYSTEM SHALL hold at most one `identity_users` document per Keycloak user, keyed by the
Keycloak user ID.

**Acceptance**
- [ ] Legacy `identity_users._id` is the Keycloak user ID; new accounts have `_id` = account UUID and a unique sparse `keycloakUserId` (amended)
- [ ] Every write is an upsert keyed on that id — no code path inserts unconditionally
- [ ] Two concurrent syncs for one user produce one document
- [ ] `email` and `phoneNumber` carry unique sparse indexes ([ET-PLT-002](../../_platform/002-persistence-baseline/) §4)
- [ ] Permission resolution exists in exactly one implementation, `/api/internal/**` is scope-gated, and no `User.keycloakUserId` exists finds no `keycloakUserId` in the tree

### ET-IDN-002-R2 · Keycloak changes propagate, and never block Keycloak

WHEN a user is created or changed in Keycloak, THE SYSTEM SHALL update the MongoDB
document, and IF that update fails, THEN THE SYSTEM SHALL allow the Keycloak operation to
succeed regardless.

**Acceptance**
- [ ] `UserSyncEventListener` handles `REGISTER`, `UPDATE_PROFILE`, `UPDATE_EMAIL`, `VERIFY_EMAIL` and `LOGIN`, and the admin events `CREATE`, `UPDATE` and `DELETE` on `users`
- [ ] Every call to identity-service is bounded by a timeout no greater than 2 seconds
- [ ] Every failure — timeout, 5xx, connection refused — is caught, logged with the user id, and **not** rethrown
- [ ] A login succeeds with identity-service stopped, asserted by an integration test
- [ ] `LOGIN` updates only `lastLoginAt`, and does so without rewriting the profile
- [ ] The event body is exactly the six-plus fields of CONTRACT §4.6 and the listener is enabled in both realms (amended)
- [ ] The listener authenticates with `client_credentials` as `internal-service`
- [ ] Registering `user-sync` as a realm event listener is part of the realm export, not a manual console step

### ET-IDN-002-R3 · A missing document repairs itself on the next request

IF an authenticated request arrives for a user with no document, THEN THE SYSTEM SHALL
create it from the token's claims before serving the request.

**Acceptance**
- [ ] The first authenticated request for an unknown subject is **adopted** ([ET-IDN-004](../004-accounts-and-contacts/) R4): an account is created from the `accountId` claim (else `sub`) and `preferred_username` only; roles and account type are never taken from token claims or user-editable attributes (amended)
- [ ] Fields the token does not carry are fetched from the Keycloak Admin API on the same path, and a failure there leaves them null rather than failing the request
- [ ] The repair is idempotent and concurrent-safe — ten parallel first requests produce one document
- [ ] After the repair the request proceeds normally; the user observes no error and no additional round trip they can perceive
- [ ] An integration test disables the listener entirely, logs a new user in, and asserts the platform is fully functional for them
- [ ] The repair path is instrumented, so its rate is the signal that the listener has stopped working

### ET-IDN-002-R4 · Reconciliation finds and repairs drift in both directions

THE SYSTEM SHALL periodically compare Keycloak's users against `identity_users` and repair
every difference it finds.

**Acceptance**
- [ ] The `identity-user-reconciliation` Schedule (daily 03:30 UTC, overlap `SKIP`) starts `UserBackfillWorkflow`, which pages through Keycloak's users 100 at a time, continuing as new per page, and signals each user's `UserSyncWorkflow`; at boot an existing Schedule takes this cadence and keeps an operator\'s pause (ROADMAP D-34)
- [ ] A Keycloak user with no document is created; a document whose cached fields differ is updated; a document whose Keycloak user is absent is left as it is (ROADMAP D-29)
- [ ] A fire while a run is open is skipped by the Schedule, and an operator request while a backfill runs reaches it (`user-backfill`, `USE_EXISTING`); no Redis lock exists
- [ ] Counts of each repair kind are exported as metrics, and a non-zero created-or-drifted count alerts
- [ ] An operator can trigger a full reconciliation on demand, and it is `SUPER_ADMIN`-only on both the mutation and `POST /api/internal/keycloak/sync/all` (ROADMAP D-30)
- [ ] The reconciliation never writes a MongoDB-owned field — a reconciliation that overwrites an avatar is a data-loss bug
- [ ] A test seeds drift of each kind and asserts one reconciliation run repairs all of them; the backfill's recorded history replays

### ET-IDN-002-R5 · Profile writes go to exactly one owner per field

WHEN a user updates their profile, THE SYSTEM SHALL write each field to its owner and to no
other store.

**Acceptance**
- [ ] `updateMyProfile` writes `avatarUrl`, `bio`, `dateOfBirth`, `locale` and `timezone` to MongoDB only
- [ ] `firstName`, `lastName` and `email` write to **Keycloak first**, then MongoDB — so a partial failure leaves MongoDB stale, never Keycloak wrong
- [ ] A Keycloak write failure refuses the mutation with `USER_SYNC_CONFLICT`, marked retryable, and writes nothing to MongoDB
- [ ] No mutation writes `phoneNumber`, `phoneVerified`, `emailVerified`, `userType` or any realm role
- [ ] An email change sets `emailVerified` false in Keycloak and lets Keycloak's own verification restore it
- [ ] A test asserts each field lands in exactly one store

### ET-IDN-002-R6 · The phone number changes only by verification *(superseded by ET-IDN-004-R5 on 2026-10-04: `changePhoneNumber` is removed; a contact changes only through `ContactChangeWorkflow`)*

IF a user changes their phone number, THEN THE SYSTEM SHALL require verification of the new
number before it takes effect.

**Acceptance**
- [ ] No profile mutation accepts a phone number as an input field
- [ ] `changePhoneNumber` requires a valid OTP for the **new** number, verified through [ET-IDN-001](../001-phone-otp-identity/)
- [ ] The old number remains the login identity until the new one verifies
- [ ] A number already held by another user is refused, and the refusal does not disclose which account holds it
- [ ] On success, Keycloak's `phone_number` attribute and the MongoDB cache are both updated, Keycloak first
- [ ] The change writes an audit row ([ET-PLT-009](../../_platform/009-audit-trail/))

### ET-IDN-002-R7 · A deleted user leaves a tombstone, not an orphan

WHEN a user is removed from Keycloak, THE SYSTEM SHALL mark the document deleted and retain
it.

**Acceptance**
- [ ] A Keycloak admin `DELETE` on a user sets `status = DELETED` and `deletedAt`, and deletes no document; the previous hard delete is removed (amended)
- [ ] A deleted user's tickets, payments and journal lines still resolve their owner
- [ ] A deleted user cannot authenticate, and every `@auth`-gated operation refuses
- [ ] `me` for a deleted subject refuses with `USER_UNKNOWN` rather than returning a tombstone
- [ ] Erasure of the personal fields is out of scope here and is triggered by [ET-PLT-008](../../_platform/008-data-protection/)

## 4. Model

### The document

`identity_users` — `_id` is the Keycloak user ID.

| Field | Owner | Type | Notes |
|---|---|---|---|
| `_id` | Keycloak | `String` | the Keycloak user UUID |
| `username`, `email`, `firstName`, `lastName` | Keycloak | `String` | cached |
| `emailVerified`, `phoneVerified` | Keycloak | `boolean` | cached |
| `phoneNumber` | Keycloak | `String` | cached, E.164 |
| `avatarUrl`, `bio` | MongoDB | `String` | |
| `dateOfBirth` | MongoDB | `LocalDate` | a calendar date, not an instant — the one exception to [ET-PLT-002](../../_platform/002-persistence-baseline/) R5, stated here because a birthday has no time zone |
| `locale`, `timezone` | MongoDB | `String` | |
| `userType` | MongoDB | `UserType` | `CUSTOMER`, `ORGANIZER`, `ADMIN` |
| `accountStatus` | MongoDB | `AccountStatus` | `ACTIVE`, `SUSPENDED`, `PENDING_DELETION`, `DELETED` |
| `primaryOrganizationId` | MongoDB | `String` | mirrored to Keycloak for client routing only |
| `lastLoginAt` | Keycloak event | `Instant` | |
| `syncedAt` | MongoDB | `Instant` | when the cache was last confirmed — R4 reads it |
| `createdAt`, `updatedAt`, `deletedAt` | MongoDB | `Instant` | |
| `version` | MongoDB | `Long` | |

### Keycloak event mapping

| Keycloak event | Action |
|---|---|
| `REGISTER` | upsert the document; `userType = CUSTOMER` |
| `UPDATE_PROFILE` | refresh the cached identity fields |
| `UPDATE_EMAIL` | refresh `email`, set `emailVerified` false |
| `VERIFY_EMAIL` | set `emailVerified` true |
| `LOGIN` | set `lastLoginAt` — **and nothing else** |
| admin `CREATE` on `users` | upsert |
| admin `UPDATE` on `users` | refresh cached fields |
| admin `DELETE` on `users` | `accountStatus = DELETED`, `deletedAt` |

### Internal REST

| Method | Path | Scope | Purpose |
|---|---|---|---|
| `POST` | `/api/internal/keycloak/sync/user` | `internal-write` | sync one user by id |
| `POST` | `/api/internal/keycloak/sync/event` | `internal-write` | process one Keycloak event |

Both are declared in [ET-PLT-007 §4](../../_platform/007-security-and-authorization/).
A full reconciliation is **not** an internal endpoint — it is a `SUPER_ADMIN` GraphQL
mutation, because it is an operator action and belongs in the audit trail.

### The three paths to a correct document

```
1 · listener       Keycloak event → REST → UserSyncWorkflow signal → upsert   fast, best-effort, may not fire
2 · lazy repair    first authenticated request → upsert     the actual guarantee
3 · reconciliation nightly Schedule → UserBackfillWorkflow → upsert / mark deleted   catches what 1 and 2 miss
```

Path 2 is what makes paths 1 and 3 optimisations. A user who logs in is correct; a user who
never logs in is repaired by 3; and nothing depends on 1 succeeding.

### GraphQL

Subgraph `identity`. Every field carries `@auth` explicitly
([ET-PLT-007](../../_platform/007-security-and-authorization/) R3).

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `me` | query | `AUTHENTICATED` | `User!` |
| `user(id: ID!)` | query | `ADMIN` | `User` |
| `users(filter, page)` | query | `ADMIN` | `UserPage!` |
| `updateMyProfile(input:)` | mutation | `AUTHENTICATED` | `User!` |
| `changePhoneNumber(input:)` | mutation | `AUTHENTICATED` | `User!` |
| `updateUser(id:, input:)` | mutation | `ADMIN` | `User!` |
| `suspendUser(id:, reason:)` | mutation | `ADMIN` | `User!` |
| `reconcileUsers` | mutation | `SUPER_ADMIN` | `ReconciliationResult!` |

`UpdateMyProfileInput` carries `avatarUrl`, `bio`, `dateOfBirth`, `locale`, `timezone`,
`firstName`, `lastName`, `email` — **and no phone field** (R6).
`ChangePhoneNumberInput` carries `newPhoneNumber` and `code`.
`ReconciliationResult` carries `scanned`, `created`, `updated`, `markedDeleted`, `durationMs`.

`User` is the identity subgraph's `@key(fields: "id")` type
([ET-PLT-004 §4](../../_platform/004-federation-contract/)); booking extends it with
`purchasedTickets` and `totalSpent`.

### Redis keys

None. Reconciliation's mutex is its Schedule's overlap policy and the backfill's workflow id.

### Configuration

| Property | Value |
|---|---|
| `identity.sync.listener-timeout` | `PT2S` |
| `identity.sync.reconciliation-interval` | `PT6H` |
| `identity.sync.reconciliation-page-size` | 200 |
| `identity.sync.lazy-repair.enabled` | `true` — a startup check refuses `false` outside tests |

### Error codes

`USER_UNKNOWN`, `USER_SYNC_CONFLICT` — rows of
[ET-PLT-005 §4](../../_platform/005-error-contract/) introduced by this spec.

## 5. Tasks

- [ ] **T1 · The `identity_users` document, its ownership split and its indexes**
  - requirements: R1
  - files: `backend/identity-service/.../domain/model/User.java`
  - verify: no `keycloakUserId` anywhere; concurrent upserts produce one document
  - parallel-safe: no
  - depends: —

- [ ] **T2 · `UserSyncService`: upsert, field-ownership enforcement, event mapping**
  - requirements: R1, R2
  - files: `backend/identity-service/.../service/impl/UserSyncServiceImpl.java`
  - verify: each Keycloak event produces exactly the §4 action and touches no MongoDB-owned field
  - parallel-safe: no
  - depends: T1

- [ ] **T3 · `UserSyncEventListener` SPI, swallow-and-log, 2-second timeout**
  - requirements: R2
  - files: `backend/keycloak-extensions/.../eventlistener/`
  - verify: a login succeeds with identity-service stopped
  - parallel-safe: yes
  - depends: T2

- [ ] **T4 · Lazy repair on the authenticated request path (re-scoped: adoption per ET-IDN-004 R4, never trusting token claims for roles)**
  - requirements: R3
  - files: `backend/identity-service/.../security/`, the `me` resolver
  - verify: the listener disabled entirely, a new user logs in and the platform is fully functional
  - parallel-safe: no — it is on every request path
  - depends: T2

- [ ] **T5 · The reconciliation Schedule, the backfill workflow, its metrics and the operator mutation**
  - requirements: R4
  - files: `backend/identity-service/.../workflow/usersync/UserBackfillWorkflowImpl.java`, `.../workflow/usersync/UserReconciliationSchedule.java`
  - verify: seeded drift of each kind is repaired in one run; overlap is `SKIP`; the history replays
  - parallel-safe: yes
  - depends: T2

- [ ] **T6 · `updateMyProfile` with the write-through order**
  - requirements: R5
  - files: `backend/identity-service/.../web/graphql/mutation/UserMutationResolver.java`
  - verify: each field lands in exactly one store; a Keycloak failure refuses and writes nothing
  - parallel-safe: yes
  - depends: T2

- [ ] **T7 · ~~`changePhoneNumber` behind OTP verification~~ — superseded: remove `changePhoneNumber`; the contact-change flow is ET-IDN-004 T6**
  - requirements: R6
  - files: `backend/identity-service/.../service/impl/UserServiceImpl.java`
  - verify: no profile input accepts a phone field; a number held by another user refuses without disclosure
  - parallel-safe: yes
  - depends: T6

- [ ] **T8 · Deletion tombstone (the hard delete is removed) and the orphan-resolution test**
  - requirements: R7
  - files: `backend/identity-service/.../service/impl/UserSyncServiceImpl.java`
  - verify: a deleted user's tickets still resolve their owner; `me` refuses with `USER_UNKNOWN`
  - parallel-safe: yes
  - depends: T2

## 6. Out of scope

| Capability | Spec |
|---|---|
| The login flow, OTP generation, delivery and verification | [ET-IDN-001](../001-phone-otp-identity/) |
| The realm, roles, clients, scopes and token validation | [ET-PLT-007](../../_platform/007-security-and-authorization/) |
| Organization membership and the Keycloak group mirror | [ET-ORG-002](../../organization/002-teams-and-invitations/) |
| Permission resolution over membership | [ET-ORG-003](../../organization/003-permission-resolution/) |
| Erasing personal fields; the deletion grace period | [ET-PLT-008](../../_platform/008-data-protection/) |
| Recording profile and phone changes for audit | [ET-PLT-009](../../_platform/009-audit-trail/) |
| Notification preferences and devices | [ET-NTF-001](../../notification/001-notification-transport/) |
| User statistics and admin dashboards | [ET-ADM-004](../../admin/004-analytics-and-statistics/) |

Deliberately never in scope: **Keycloak's user-storage SPI with MongoDB as the federated
store** (the identity provider would inherit the platform's availability), and
**bidirectional profile sync** (two writers per field, and the loser is whichever the user
typed first).
