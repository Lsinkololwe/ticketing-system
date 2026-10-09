# ET-IDN-004 · Accounts and contacts

> **Conformance** · US Part I §6 Keycloak integration · ASVS 2.5 credential recovery, 3.3 session termination
>
> Binding wire and data contract: [`CONTRACT.md`](CONTRACT.md) (same folder). If an implementation must
> deviate, change that file first, then this one. Decisions D-38..D-50 in [ROADMAP](../../ROADMAP.md);
> finding [F-044](../../FINDINGS.md).

## 1. Capability

The platform needs one durable answer to "who is this person?" that survives a change of phone, a
second email, a duplicate sign-up, a deletion request, and a day on which Keycloak and the database
disagree. That answer is the **account**: one application record per person, owned by
identity-service, identified by an opaque id that is derived from nothing the person typed. An
account is reachable through one or more **contacts** — a verified WhatsApp number or email address —
each owned by exactly one account. Keycloak holds a *projection* of the account that can issue tokens;
it is not where the person lives.

Five kinds of account exist, and they differ in how they are created and in what they may do, not in
where they live:

| Type | Realm | Created by | Authenticates with | Authority comes from |
|---|---|---|---|---|
| **Buyer** | `myticketzm` | the contact flow at checkout ([ET-IDN-001](../001-phone-otp-identity/)) | verified contact (WhatsApp or email) | role `CUSTOMER` |
| **Organizer** | `myticketzm` | a buyer account whose organization application is approved ([ET-ORG-001](../../organization/001-organizer-onboarding/)) | verified contact | organization role, set by the server, never chosen by the registrant |
| **Team member** | `myticketzm` | accepting an invitation with a verified contact ([ET-ORG-002](../../organization/002-teams-and-invitations/)) | verified contact | organization membership |
| **Event staff** | `myticketzm` | an event access grant to a verified contact ([ET-ORG-003](../../organization/003-permission-resolution/)) | verified contact | an event-scoped grant (scan and check-in), no realm role |
| **Platform staff** | `myticketzm-admin` | the admin app, identity-first (**D-49**) | password plus a second factor | realm roles `ADMIN`, `SUPER_ADMIN`, `FINANCE`, `FINANCE_LEAD` |

All of the first four are one thing — an account with a contact — to which authority is *added* by
other specs. That is deliberate: one person who buys a ticket and later organizes an event is the same
account throughout.

This spec delivers the account's life: creation identity-first by a durable workflow (**D-43**),
adoption of accounts created outside it, merging two accounts that turn out to be one person, changing
a contact, the hook by which erasure removes an account, and the scheduled repair that detects and
mends disagreement between the database and Keycloak. It states, as a contract, which system owns
which field and how long any two systems may legitimately disagree.

## 2. Design decisions

**The database decides; Keycloak follows.** The intended state of an account — including whether it
is suspended (**D-47**) — is written to MongoDB first, with a pending marker, and then applied to
Keycloak by an idempotent activity. A change made in the Keycloak console is *adopted back* into the
account and audited rather than overwritten blindly, because an operator acting in an emergency must
not be silently undone.

**Accounts are created identity-first and completed forward (D-43).** `AccountEnsureWorkflow`
claims the contact in the database (the unique partial index on a verified contact is the arbiter of a
race), creates the Keycloak user with `username = accountId`, applies role and attributes, and flips
PROVISIONING to ACTIVE in one transaction with consents and the outbox. A failure is retried or
repaired; it never deletes a half-made account. The Keycloak plugin never creates users.

**The account id is opaque.** New accounts get a UUID. It is not a hash of the contact, so changing
a contact never changes the account id, and neither workflow ids nor logs ever reveal a contact
(**D-46**).

**A contact belongs to exactly one account, enforced by an index, not by a check.** A
read-then-write uniqueness check races. The unique partial index `uniq_verified_contact` rejects the
loser, who re-reads and is given the winner's account.

**Contacts are stored encrypted and addressed by keyed hash.** `valueEncrypted` is the only place the
raw value lives; `valueHash` (the `contactKey`) is used for lookups; `valueMasked` for display.

**Two accounts that are one person are merged by a workflow, not by an edit.** The merge marks both
accounts, moves contacts and references to the survivor, disables and tombstones the other, and
revokes its tokens. It is resumable and leaves an audit trail.

**Changing a contact is a re-proof, not a form field.** The new contact must be proved by code and the
change is staged so a failure never leaves the account with no usable contact.

**Deletion is a hook into erasure.** [ET-PLT-008](../../_platform/008-data-protection/) owns the
erasure; this spec owns what the account does when erasure completes: contacts released, Keycloak user
deleted, a tombstone kept so financial records keep their foreign key.

**Repair is scheduled, classified and bounded.** A Temporal Schedule scans for nine classes of drift
(D1..D9), each with a defined fix, and every pending marker has a maximum age past which it alerts.
Security-relevant heals (`enabled`, roles) are audited and never silent.

**Rejected alternatives**

- *Deriving the account id from the contact.* A contact change then changes the identity, and the id leaks into every id and log.
- *The Keycloak plugin creating the user on first login.* Two concurrent logins race, the plugin needs realm-admin rights, and a failure leaves a user the database has never heard of.
- *Deleting a half-created account on failure.* A concurrent sign-in or a retry would then find nothing; completing forward is safe because every step is idempotent.
- *Letting Keycloak own `enabled` (previous ET-IDN-002 §2).* Suspension is a business decision with an audit and a reason; the database must be able to say "suspended" even when Keycloak is unreachable.
- *Trusting user-editable Keycloak attributes for roles or account type.* Any user can edit their own attributes through the account console unless the profile forbids it; roles are derived from the database.
- *A uniqueness pre-check before insert.* Races under concurrent first sign-in.
- *Automatic merge on contact match.* A matching contact is by definition the same account; a *different* contact claiming the same person is a human decision (open item F-044).

## 3. Requirements

### ET-IDN-004-R1 · Every person is one account in one of five types and a defined state

THE SYSTEM SHALL represent each person as exactly one account with a `status` from the state machine and an account type that is decided by the server.

**Acceptance**
- [ ] `status` is one of `PROVISIONING, ACTIVE, SUSPENDED, MERGED, DELETED`; transitions allowed: PROVISIONING→ACTIVE, ACTIVE→SUSPENDED, SUSPENDED→ACTIVE, ACTIVE|SUSPENDED→MERGED, ACTIVE|SUSPENDED→DELETED; any other transition is refused
- [ ] Transient work is marked with `pendingKind` (`MERGING`, `CHANGING`, `DELETION_REQUESTED`) and `pendingSince`; a deletion request leaves `status` ACTIVE
- [ ] Only an ACTIVE account is given a login handle, may sign in, or may hold a reservation; others are refused with `ACCOUNT_NOT_ACTIVE`, `ACCOUNT_SUSPENDED` or `ACCOUNT_MERGING`
- [ ] Legacy `AccountStatus` maps: ACTIVE→ACTIVE, INACTIVE/LOCKED/SUSPENDED→SUSPENDED, PENDING_VERIFICATION→PROVISIONING, PENDING_DELETION→ACTIVE + `DELETION_REQUESTED`
- [ ] Platform staff exist only in realm `myticketzm-admin`; no operation lets a registrant select `ORGANIZER`, `ADMIN` or any role
- [ ] A new account's `_id` is a UUID, and the Keycloak username equals it; legacy accounts keep `_id` = Keycloak id and are linked by `keycloakUserId`
- [ ] Keycloak ignores a supplied user id, so for new accounts the token `sub` is NOT the account id: every buyer token carries an `accountId` claim (mapper on the user attribute), and downstream services take the user id from that claim first and fall back to `sub` for staff and legacy users; revocation keeps keying on `jti`, `sid` and `sub`

### ET-IDN-004-R2 · An account is created identity-first by a durable workflow

WHEN a proof for an unknown contact is presented to `ensure`, THE SYSTEM SHALL run `AccountEnsureWorkflow` to completion and return an ACTIVE account.

**Acceptance**
- [ ] The workflow id is `account-ensure/{contactKey}`, queue `identity-account`, started by Update-with-Start with conflict policy `USE_EXISTING`; its input carries `proofId` and `contactKey`, never a raw contact
- [ ] Steps are activities, each repeatable: `claimContact` (reads the encrypted contact from `proof:{proofId}`, mints the account id, inserts under the unique index, returns the existing account if taken), `createKeycloakUser` (a 409 is read back by username), `applyAttributesAndRoles` (full representation), `activateAccount` (compare-and-set PROVISIONING→ACTIVE, consents and outbox in ONE transaction), `stageOutbox`
- [ ] Two concurrent proofs for one contact yield exactly one account and one Keycloak user
- [ ] Killing the worker after each step and restarting converges to an ACTIVE account with no duplicate
- [ ] A permanent failure leaves a PROVISIONING account that the repair schedule completes or alerts on after 10 minutes; nothing is deleted
- [ ] Consents (`TERMS` and the version) are recorded in `identity_consents` in the activating transaction
- [ ] Workflow ids, search attributes (`BusinessId` = account id, `ProcessKind`), payloads and logs contain no `@` and no `+<digits>`; a lint fails otherwise
- [ ] **Against a real Keycloak 26.5.2** (`BuyerSignInEndToEndTest`, through a proxy that answers genuine HTTP 503 on demand): a crash right after the claim (Keycloak answers 503 to the user create) leaves a PROVISIONING account and the retry finishes it, one account and one Keycloak user; an orphan Keycloak user (created, the answer lost) is read back by its exact username and adopted, no second user; a 5xx on the attribute write is retried until the account is ACTIVE with its role and `accountId` attribute; Keycloak stopped and restarted completes the account (see *Real-Keycloak cases* below)

### ET-IDN-004-R3 · A contact belongs to exactly one account, and is linked, removed and prioritised only by proof

THE SYSTEM SHALL keep each verified contact on exactly one account, stored encrypted and addressed by keyed hash, and SHALL let the signed-in buyer add, remove and re-prioritise contacts only by proving control of them.

*Decided for implementation 2026-10-04 ([F-044](../../FINDINGS.md), revisit notes there):* (a) a change, a removal and a primary switch are authorised by a fresh code sent to the **current primary verified contact**; there is no support-recovery path now, and an account with no verified contact able to receive a code is refused with `NO_VERIFIED_CONTACT`; (b) a released contact is quarantined for `identity.contact.quarantine` (default `P30D`, `PT0S` in the test profile) before any other account may claim it; (c) removing a contact is refused if it is the last verified one; (d) at most one contact is primary, and the primary may be switched among verified contacts after a step-up.

**Acceptance**
- [ ] `identity_contacts` has the unique partial index `uniq_verified_contact` on (`type`, `valueHash`) where `verifiedAt` exists and `releasedAt` is absent
- [ ] Two concurrent claims of one contact: one succeeds; the other receives `CONTACT_ALREADY_CLAIMED`, re-reads, and is answered with the winner's account. Two accounts adding one contact at the same moment: exactly one wins and the other's account, contacts and Keycloak user are untouched
- [ ] `valueEncrypted` is AES-GCM under `app.security.encryption.key`; no other collection, log, event, Redis key or Redis value contains the raw value
- [ ] `valueMasked` renders `+260 97* ***123` and `j***@gmail.com`; the GraphQL operations return masked values only
- [ ] **Add needs only the new contact's code** (user decision 2026-10-04: no step-up to the current primary for an add; the stolen-session risk, that a thief with a live session can attach their own contact, is accepted and to be revisited). `requestContactAdd` sends a code to the new contact; `confirmContactAdd` with the right code claims it (verified, not primary unless the account has none), writes the Keycloak `email` and `emailVerified` when it is an email, writes `CONTACT_ADDED` (masked) and stages `identity.ContactAdded`, and notifies the new contact. Register with WhatsApp then add email, or the reverse: both contacts verified, exactly one primary, the account id and Keycloak username unchanged
- [ ] **Add is refused** with `CONTACT_ALREADY_CLAIMED` when another account holds the contact or released it less than the quarantine ago; nothing changes in MongoDB or Keycloak. The check follows the proof, so the answer is only ever given to a person who proved control of that contact
- [ ] **Remove.** `requestContactRemoval(contactId)` is refused with `LAST_VERIFIED_CONTACT` for the last verified contact and `CONTACT_UNKNOWN` for a contact that is not the caller's; otherwise a code goes to the current primary and `confirmContactRemoval` releases the contact, passes the primary to the oldest remaining one if it was primary, clears the Keycloak email if an email left, ends the account's Keycloak sessions, writes `CONTACT_REMOVED` and stages `identity.ContactRemoved`
- [ ] **Primary.** `requestPrimaryContact` and `setPrimaryContact` move the primary among verified contacts after a code to the current primary; afterwards exactly one active contact is primary and `primaryContactId` names it
- [ ] An account has at most one `primary` contact; deleting its last verified contact is refused
- [ ] A released contact is quarantined for `identity.contact.quarantine` before another account may claim it: by `confirmContactAdd`, by a change, and by `ensure` creating a new account from a proof of it (`CONTACT_ALREADY_CLAIMED` in each case). The account that released it is not held back
- [ ] **Resend.** `resendContactCode` sends a pending add, change (either code), removal or primary code again once `identity.contact.resend-after` (default `PT5M`, `PT0S` in tests) has passed since it was last sent; earlier it is refused with `OTP_RATE_LIMITED` and `retryAfterSeconds`. The new code replaces the old one and has a **new challenge id** (the old one is `OTP_EXPIRED`); attempts reset only for the replaced code; the same `LimitService` caps apply; an open change keeps its 48-hour expiry and its attempt budget; a challenge that is not the caller's is `CONTACT_UNKNOWN`. *Interpretation to confirm: the user said the person may ask again "after five" - read as five minutes after the last send, configurable. The 60-second sign-in cooldown still applies underneath.*
- [ ] Wrong and expired codes: `OTP_INVALID` (with `attemptsRemaining`), `OTP_LOCKED` after five, `OTP_EXPIRED` when the code is gone; the contact does not join. Every code request goes through `LimitService` and `ChallengeService`, so every sign-in rate limit applies
- [ ] Every operation acts on the token's own account (`AccountIdentity`); no operation takes an account id. A contact id or challenge of another account is `CONTACT_UNKNOWN` / `OTP_EXPIRED`, indistinguishable from one that does not exist
- [ ] Only an ACTIVE buyer account may use the operations: others get `ACCOUNT_NOT_ACTIVE`, `ACCOUNT_MERGING` or `ACTOR_NOT_PERMITTED` (staff)

### ET-IDN-004-R4 · Accounts made elsewhere are adopted, and drift is repaired

WHERE a Keycloak user exists with no matching account, or an account and its Keycloak user disagree, THE SYSTEM SHALL classify the drift and apply the defined repair.

**Acceptance**
- [ ] The Keycloak event path carries `eventId, eventType, userId, username, realm, enabled, emailVerified, timestamp` and no attributes, names, roles or phone
- [ ] A user whose username is not a known account id is **adopted**: an account is created `createdVia=ADOPTED`, roles are taken from the database policy never from user-editable attributes, and the adoption is audited and flagged for review
- [ ] The Schedule `identity-account-repair` runs every PT15M with overlap policy `SKIP` and classifies drift D1..D9 (§4); each class has exactly one repair
- [ ] A change of `enabled` made in the Keycloak console is adopted into `status` (and audited) unless a pending marker shows identity-service initiated it, in which case Keycloak is corrected
- [ ] Repairs of `enabled` and roles write an audit entry naming the class; none is silent
- [ ] A PROVISIONING account older than 10 minutes raises an alert; `MERGING` older than 2 hours and `CHANGING` older than 48 hours do too
- [ ] Re-running the repair on a clean state changes nothing
- [ ] **Implemented 2026-10-04 (`AccountRepair`, `AccountRepairWorkflow`, Schedule `identity-account-repair`).** Three passes in order: the buyer realm's users (D2, D9), the accounts (D1, D3, D4, D5, D6), the markers (D7, D8). Each class has one repair and writes a `REPAIR_Dn` event to `identity_account_events` naming the class (ids and counts only, never a contact); a second run finds nothing and writes nothing; alerts (PROVISIONING past PT10M, MERGING past PT2H, CHANGING and DELETION_REQUESTED past PT48H) are the metric `identity.account.repair.alert` and a log line without personal data
- [ ] The Schedule is created idempotently at boot when `identity.account.repair.enabled=true` (on in the `prod` profile): interval `identity.account.repair.interval` (PT15M), overlap `SKIP`, run timeout PT14M; an operator's pause survives a release. A pass that fails (Keycloak 5xx) is retried 3 times, then left to the next interval; every repair is idempotent so nothing is left half done
- [ ] Which side wins, as built: D1 creates the missing user by username and links it; D3 links the user found by `username = accountId`; D4 reapplies `enabled=false` for a SUSPENDED account (and ends its sessions) but adopts a console-disabled ACTIVE account as SUSPENDED, never re-enabling (skipped while a pending marker is set); D5 resets realm roles to the database policy (CUSTOMER when none) and the `accountId` attribute; D6 rewrites email and `emailVerified` from the verified email contact (or clears them); D2 adopts a user with no account as `ADOPTED`, `CUSTOMER`, flagged `needsReview`; D9 disables a second Keycloak user that claims an already linked account and opens a support task, never merging or deleting; D7 resumes a stalled PROVISIONING account to ACTIVE; D8 clears an orphaned CHANGING marker and alerts on MERGING / DELETION_REQUESTED (their workflows arrive with BE-7 and BE-8)
- [ ] Only accounts this service owns (`createdVia` OTP or ADOPTED) are repaired; staff and legacy accounts, for which Keycloak is the source, are left alone. *Open:* the other half of D9 (two accounts for one person) has no automatic signal and stays a human report; the per-account Keycloak read makes a pass cost one call per account, which needs sizing at scale


### ET-IDN-004-R5 · A contact changes only by re-proof, without a gap

WHEN a signed-in buyer asks to change a contact, THE SYSTEM SHALL prove the new contact by code, authorise the change with a code to the current primary contact, and switch only after both, keeping the account reachable throughout.

**Acceptance**
- [ ] `ContactChangeWorkflow` has id `contact-change/{accountId}`, queue `identity-account`, one open change per account (a second start fails with `CONTACT_CHANGE_IN_PROGRESS`; so does any add, removal or primary request while one is open), and sets `pendingKind=CHANGING` (with `pendingSince`) while open. One workflow serves ADD, CHANGE, REMOVE and PRIMARY; the step sequence is versioned (`Workflow.getVersion`)
- [ ] `requestContactChange(contactId, type, value)` sends a code to the NEW contact and one to the CURRENT primary contact (decision a); `confirmContactChange(changeId, newContactCode, currentContactCode)` needs both (the current-contact code only until it has been accepted once). The type of the new contact equals the type of the contact it replaces
- [ ] Both confirmations reach the workflow as updates (`authorise`, `acceptNew`) carrying proof ids, never codes; a wrong or expired code is a `failedAttempt` signal; five abort the change with `OTP_ATTEMPTS_EXHAUSTED`; a change nobody confirms expires after PT48H with `OTP_EXPIRED`; `cancelContactChange` abandons it. Each ending clears the marker and leaves the account as it was
- [ ] The new contact is claimed under the unique index **before** the old one is released; a lost claim aborts with `CONTACT_ALREADY_CLAIMED` and changes nothing (no release, no Keycloak write, marker cleared)
- [ ] **No gap:** the old contact keeps signing the account in until the switch. Order of effects: claim new -> Keycloak email and `emailVerified` (full representation, `username` untouched) -> release old, decide primary, recompute verified flags, write the account event and stage the outbox row in ONE transaction -> end Keycloak sessions -> clear the marker -> notify. Keycloak down or answering 5xx delays the change and never opens a gap: the old contact stays the only live one, and the change completes after the restart
- [ ] A crash or restart of the worker at any step converges: every activity is repeatable (deterministic contact id from account, change and contact key; the commit finds its own account event), the claim is not repeated, and exactly one `CONTACT_CHANGED` event and one outbox row result
- [ ] On completion the Keycloak email and `emailVerified` are updated from the account's verified email contact, and the account's Keycloak sessions are ended ([ET-IDN-003](../003-token-revocation/)): a refresh token issued before the change no longer works. Access tokens already issued live until they expire (PT5M)
- [ ] An account event `CONTACT_CHANGED` (also `CONTACT_ADDED`, `CONTACT_REMOVED`, `CONTACT_PRIMARY_SET`) is written to `identity_account_events` in the committing transaction, with the contact type and masked values, never raw values. The `AuditLog` action `CONTACT_CHANGED` of [ET-PLT-009](../../_platform/009-audit-trail/) joins when that registry row lands; until then the account event is the audit record
- [ ] Both the old and new contact receive a notification of the change (fixed words, no personal data). Delivery is best effort and never holds the change open
- [ ] A marker left behind (workflow gone, cleanup failed) is cleared by repair class D8 once older than `identity.account.repair.changing-max-age` (`AccountRepair.clearStaleChanging`); a marker whose workflow is still open is left alone and alerted

### ET-IDN-004-R6 · Two accounts that are one person merge safely — **WITHDRAWN 2026-10-09: accounts are not merged; see F-049**

WHEN support merges two accounts, THE SYSTEM SHALL move everything to the survivor, retire the other and revoke its access.

**Acceptance**
- [ ] `AccountMergeWorkflow` has id `account-merge/{mergedAccountId}`; both accounts carry `pendingKind=MERGING` while it runs, and sign-in to either is refused with `ACCOUNT_MERGING`
- [ ] Contacts of the merged account are re-pointed to the survivor in one transaction; references in other services are re-pointed by the `identity.AccountMerged` event consumers, each idempotent on `eventId`
- [ ] The merged account ends `MERGED` with `mergedInto` set; its Keycloak user is disabled and its tokens revoked
- [ ] A merge cannot be undone by the workflow; the audit entry names both ids
- [ ] The workflow can be started only by an authorised platform action (policy automatic vs support: open item F-044)

### ET-IDN-004-R7 · Erasure removes an account through a defined hook

WHEN erasure completes for an account, THE SYSTEM SHALL release its contacts, delete its Keycloak user and keep a tombstone.

**Acceptance**
- [ ] A deletion request sets `pendingKind=DELETION_REQUESTED` and starts `ErasureWorkflow` ([ET-PLT-008](../../_platform/008-data-protection/)); the account can still sign in until the scheduled date and can cancel
- [ ] At completion: contacts get `releasedAt` and are anonymised; the Keycloak user is deleted; tokens are revoked; `status=DELETED`; personal fields are anonymised; the document remains as a tombstone
- [ ] A tombstoned account id is never reused, and its contacts may be claimed again after the quarantine
- [ ] The `identity.AccountDeleted` event carries the account id only

### ET-IDN-004-R8 · The consistency contract holds, and gates stand before the profile

THE SYSTEM SHALL honour the six consistency rules of §4 and SHALL pass three gates before returning a profile.

**Acceptance**
- [ ] Each cross-system change in the §4 table writes its pending marker before the second system is touched, and clears it only after both agree
- [ ] No operation reads Keycloak to decide who may sign in; the database `status` decides
- [ ] Creation gates (activation, plugin, app) hold in order: no login handle for a non-ACTIVE account, the plugin never creates a user, and the app sets no session unless the status is ACTIVE; a test removes the Keycloak user mid-flow and confirms no profile opens
- [ ] Request gate 1: the token is valid for the audience and not revoked ([ET-IDN-003](../003-token-revocation/)); request gate 2: the account is ACTIVE with no merge pending; request gate 3: consent to the current terms version is on record; failing any gate returns a typed refusal and no profile fields
- [ ] A test removes the Keycloak user, suspends in the database, and confirms the next request is refused and the repair restores or alerts according to class
- [ ] The `identity-account-repair` run reports counts per class as metrics with no personal data

### Real-Keycloak cases

The account and contact processes are proved against a real Keycloak 26.5.2 container, not a fake, with a proxy in front of it that answers genuine HTTP 503 on demand (`KeycloakFaultProxy`: fail before forwarding, or forward and then lose the answer):

| Case | Where | Proves |
|---|---|---|
| crash after the claim, then retry | `BuyerSignInEndToEndTest` | 503 on user create leaves PROVISIONING; retry finishes it; one account, one Keycloak user |
| orphan Keycloak user adopted by username | `BuyerSignInEndToEndTest` | create executed, answer lost; retry gets 409, reads back by exact username, links it; no duplicate |
| 5xx on the attribute write | `BuyerSignInEndToEndTest` | retried until ACTIVE with role and `accountId` |
| Keycloak stopped and restarted | `BuyerSignInEndToEndTest`, `ContactChangeEndToEndTest` | ensure and contact change complete after the restart; the old contact signs in throughout |
| 503 mid contact change | `ContactChangeEndToEndTest` | one lost write, one executed-but-lost write: the change converges, email written, one event |

## 4. Model

### Documents

| Collection | Owning service | Key fields | Notes |
|---|---|---|---|
| `identity_users` | identity-service | `_id`, `status`, `keycloakUserId`, `primaryContactId`, `preferredChannel`, `displayName`, `locale`, `mergedInto`, `provisionedAt`, `pendingKind`, `pendingSince`, `createdVia` | the account. `email`, `username`, `firstName`, `lastName` are optional. New `_id` = UUID; legacy `_id` = Keycloak id |
| `identity_contacts` | identity-service | `_id`, `accountId`, `type` (WHATSAPP\|EMAIL), `valueHash`, `valueEncrypted`, `valueMasked`, `verifiedAt`, `primary`, `source`, `createdAt`, `releasedAt?` | one verified owner per contact |
| `identity_consents` | identity-service | `accountId`, `purpose`, `version`, `grantedAt`, `source`, `withdrawnAt?` | consent record ([ET-PLT-008](../../_platform/008-data-protection/)) |
| `identity_account_events` | identity-service | `_id`/`eventId`, `accountId`, `kind`, `at`, `data` | no personal data; also holds `OTP_LOCK` |
| `identity_audit_logs`, `identity_token_revocations` | identity-service | existing | gain schema validators |

### Indexes

| Collection | Index | Kind | Why |
|---|---|---|---|
| `identity_contacts` | `uniq_verified_contact {type, valueHash}` where `verifiedAt` exists and `releasedAt` absent | unique partial | the arbiter of a claim race |
| `identity_contacts` | `idx_accountId {accountId}` | single | an account's contacts |
| `identity_users` | `{keycloakUserId}` | unique sparse | link and adoption lookup |
| `identity_users` | `{status, pendingSince}` | compound | the repair scan |
| `identity_users` | `{mergedInto}` | sparse | merge lookups |
| `identity_users` | `{email}` | partial unique (only one) | the duplicate plain-unique email index is dropped |
| `identity_consents` | `{accountId, purpose, version}` | compound | latest consent |
| `identity_account_events` | `{eventId}` | unique | idempotency |
| `identity_account_events` | `{accountId, at}` | compound | timeline |

### Field ownership

| Field | Owner | Notes |
|---|---|---|
| `accountId`, `status`, `pendingKind`, `pendingSince`, `mergedInto`, contacts, consents, `preferredChannel`, `displayName`, `locale` | MongoDB (identity-service) | Keycloak never writes them |
| Keycloak `username` | identity-service, once, at creation | equals the account id |
| `enabled` | identity-service decides, applies to Keycloak (**D-47**) | a console change is adopted back and audited |
| realm roles, `accountId` attribute | identity-service (derived from database policy) | user-editable attributes are never read for authority |
| `email`, `emailVerified` | derived from the primary email contact | optional in Keycloak |
| `firstName`, `lastName` | optional, display only | the profile name is `displayName` |
| credentials, MFA, sessions | Keycloak | staff only; never mirrored |
| `avatarUrl`, `bio`, `dateOfBirth`, `timezone` | MongoDB | profile ([ET-IDN-002](../002-keycloak-user-sync/)) |

### Cross-system changes, markers and maximum ages

| Change | First write | Second write | Pending marker | Max age before alert |
|---|---|---|---|---|
| Create | account `PROVISIONING` + contact claimed | Keycloak user + role + attributes, then `ACTIVE` | `status=PROVISIONING` | 10 min |
| Suspend / unsuspend | `status` | Keycloak `enabled` + token revocation | `pendingKind` unset; drift D4 detects | 15 min (one repair interval) |
| Role or type change | account / organization data | Keycloak realm roles | drift D5 detects | 15 min |
| Merge | both accounts `MERGING` | survivor receives contacts; merged → `MERGED`; Keycloak disabled | `pendingKind=MERGING` | 2 h |
| Contact change | `CHANGING`; new contact claimed | old released; Keycloak email updated; sessions revoked | `pendingKind=CHANGING` | 48 h |
| Deletion | `DELETION_REQUESTED` | at the date: contacts released, Keycloak user deleted, `DELETED` | `pendingKind=DELETION_REQUESTED` | scheduled date + 24 h |
| Adoption | Keycloak user found | account created `ADOPTED` | none; flagged for review | one repair interval |

### The consistency contract (six rules)

1. **The database is the record.** Identity facts live in MongoDB; Keycloak is a projection rebuildable from it, except credentials.
2. **Intent first.** The intended state and its pending marker are written to MongoDB before Keycloak is touched.
3. **Every cross-system step is an idempotent activity**, completed forward, never rolled back by deletion.
4. **Only ACTIVE signs in.** `ACTIVE` is set only after Keycloak is fully configured; sign-in decisions read the database `status`.
5. **A unique index is the only arbiter of ownership.** No read-then-write checks for contacts or Keycloak links.
6. **Disagreement is bounded and observable.** Every marker has a maximum age; security heals are audited; the repair run publishes counts per class.

### Creation gates: before the first profile

A new or adopted account reaches its profile only through three creation gates, in order:

1. **Activation gate.** The workflow moves the account to `ACTIVE` only after the Keycloak user exists with its role and attributes (compare-and-set needs the stored Keycloak id). A login handle is issued only for an `ACTIVE` account.
2. **Plugin gate.** The contact authenticator loads the user by username = account id and stops if it is missing or disabled. It never creates a user.
3. **App gate.** The buyer app's callback asks identity-service for the account status of the signed-in user and sets the session only when it is `ACTIVE`; every account page re-checks on the server.

If any step before a gate is unfinished the buyer sees "taking longer than usual" and no profile opens. Creation is also guarded by booking-service, which accepts a reservation only for an `ACTIVE` account with a verified contact.

### Request gates: on every profile request

1. **Token**: valid signature, issuer, audience and expiry, and not revoked ([ET-IDN-003](../003-token-revocation/)).
2. **Account**: `status = ACTIVE`, no merge pending.
3. **Consent**: acceptance of the current terms version is on record in `identity_consents`.

### Drift classes

| Class | Drift | Repair |
|---|---|---|
| D1 | account ACTIVE/PROVISIONING, no Keycloak user | resume `AccountEnsureWorkflow` steps (create user) |
| D2 | Keycloak user with no account | adopt (R4) |
| D3 | both exist, `keycloakUserId` unset | link by username = account id |
| D4 | `enabled` disagrees with `status` | adopt console change, or reapply if a marker shows we initiated it; audit |
| D5 | realm roles or `accountId` attribute disagree with database policy | reapply from the database; audit |
| D6 | Keycloak email / `emailVerified` disagree with the primary email contact | rewrite Keycloak from the contact |
| D7 | PROVISIONING older than 10 min | resume; alert |
| D8 | `MERGING` / `CHANGING` / `DELETION_REQUESTED` past its max age | resume the workflow; alert |
| D9 | duplicate: two Keycloak users for one account, or two accounts for one person | quarantine both, open a support task; never auto-merge |

### Events

Cross-service facts staged in the outbox by the transaction that makes them true. Payloads carry ids only: the single key `userId` (the account id, which is `User._id`; the key is `userId` because the registry's payload lint forbids keys containing `account`).
`AccountActivated`, `AccountSuspended` and `AccountDeleted` are rows of the [ET-PLT-003](../../_platform/003-event-contract/) §4 registry and listed in `spec.yaml` `events.bus`. `AccountMerged` is **proposed** only: it joins the registry with the merge wave, so the closure lint holds.

| Tier | Java type | Wire name | Topic | Consumers |
|---|---|---|---|---|
| bus | `AccountActivatedEvent` | `identity.AccountActivated` v1 | `identity-events` | none yet |
| bus | `AccountSuspendedEvent` | `identity.AccountSuspended` v1 | `identity-events` | booking (release holds) |
| bus | `AccountMergedEvent` | `identity.AccountMerged` v1 | `identity-events` | booking, catalog (re-point references) |
| bus | `AccountDeletedEvent` | `identity.AccountDeleted` v1 | `identity-events` | booking, catalog (anonymise) |
| bus | - | `identity.ContactAdded` v1 | `identity-events` | none yet |
| bus | - | `identity.ContactChanged` v1 | `identity-events` | none yet |
| bus | - | `identity.ContactRemoved` v1 | `identity-events` | none yet |

The three contact events are registry rows (ET-PLT-003 §4, `EventType`) staged by `ContactChangeWorkflow`'s commit, payload `userId` (the account id), `channel` (WHATSAPP or EMAIL), `changeId`: no contact value, masked or raw. Primary switches write the account event `CONTACT_PRIMARY_SET` and stage nothing.

### Workflows and Schedules

| Workflow | Id | Task queue | Start and conflict | Updates · signals | Timers |
|---|---|---|---|---|---|
| `AccountEnsureWorkflow` | `account-ensure/{contactKey}` | `identity-account` | Update-with-Start, `USE_EXISTING` | `ensure` | none |
| `AccountMergeWorkflow` | `account-merge/{mergedAccountId}` | `identity-account` | `USE_EXISTING` | `cancel` before MERGED | none |
| `ContactChangeWorkflow` | `contact-change/{accountId}` | `identity-account` | `FAIL` on a running id (`CONTACT_CHANGE_IN_PROGRESS`), reuse allowed after it closes | updates `authorise`, `acceptNew(proofId)` · signals `failedAttempt`, `cancel` · query `status` | expiry PT48H |
| `AccountRepairWorkflow` | `account-repair/scheduled`, suffixed by the Schedule | `identity-account` | Schedule `identity-account-repair`, PT15M, overlap `SKIP` | none | none |

`ErasureWorkflow` ([ET-PLT-008](../../_platform/008-data-protection/)) calls the deletion hook. All rows are rows of the [ET-PLT-015](../../_platform/015-durable-execution/) §4 registry; `AccountEnsureWorkflow` and `ContactChangeWorkflow` are `implemented`, the others `planned`.

`USE_EXISTING` was the first draft for `ContactChangeWorkflow`. It is `FAIL`: a second request carries different input (another contact), and joining the open execution would answer it with somebody else's change.

### GraphQL

`me` and profile operations remain ET-IDN-002's, behind the three gates. Support merge and unsuspend are platform-staff actions on internal REST until an admin spec takes them (open item F-044).

The signed-in buyer's own contacts (R3, R5) are added here, authenticated, acting on the token's own account, with no account argument: `requestContactAdd`, `confirmContactAdd`, `requestContactChange`, `confirmContactChange`, `cancelContactChange`, `requestContactRemoval`, `confirmContactRemoval`, `requestPrimaryContact`, `setPrimaryContact` and the query `myContacts`. Their shapes are in [CONTRACT.md](CONTRACT.md) §14. They are tagged `mobile` (the buyer clients) and carry masked values only.

### Redis keys

None owned here; challenge, proof and handle keys belong to [ET-IDN-001](../001-phone-otp-identity/).

### Error registry rows

| Code | Refusal type | GraphQL `ErrorType` | Retryable |
|---|---|---|---|
| `CONTACT_ALREADY_CLAIMED` | `ContactAlreadyClaimed` | `ALREADY_EXISTS` | yes (re-read) |
| `ACCOUNT_SUSPENDED` | `AccountSuspended` | `PERMISSION_DENIED` | no |
| `ACCOUNT_MERGING` | `AccountMerging` | `FAILED_PRECONDITION` | yes |
| `ACCOUNT_NOT_ACTIVE` | `AccountNotActive` | `FAILED_PRECONDITION` | yes |
| `CONTACT_UNKNOWN` | `ContactUnknown` | `NOT_FOUND` | no |
| `CONTACT_CHANGE_IN_PROGRESS` | `ContactChangeInProgress` | `FAILED_PRECONDITION` | no |
| `LAST_VERIFIED_CONTACT` | `LastVerifiedContact` | `FAILED_PRECONDITION` | no |
| `NO_VERIFIED_CONTACT` | `NoVerifiedContact` | `FAILED_PRECONDITION` | no |

### Flow

```
proof → ensure → known+ACTIVE ? answer from DB
              → unknown ? AccountEnsureWorkflow: claim contact (unique index) → Keycloak user
                          → roles+attributes → ACTIVE (+consents+outbox, one transaction)
Keycloak console change / drift → AccountRepairWorkflow (every 15 min) → classify D1..D9 → repair → audit
```

## 5. Tasks

- [ ] **T1 · Model: `AccountState`, `ContactType`, `status` and pending fields, `identity_contacts`, `identity_consents`, `identity_account_events`, indexes, validators, optional-field migration**
  - requirements: R1, R3
  - files: `backend/identity-service/src/main/java/com/pml/identity/account/`, `.../IdentityIndexInitializer.java`
  - verify: index tests (two concurrent claims → one winner); the duplicate plain email index is gone
  - parallel-safe: no
  - depends: —

- [ ] **T2 · `KeycloakAccountPort`: id-keyed admin client (create by username, full-representation updates, setEnabled)**
  - requirements: R2, R4
  - files: `backend/identity-service/.../infrastructure/keycloak/`
  - verify: a 409 on create is read back by username; updates send full representations
  - parallel-safe: yes
  - depends: T1

- [ ] **T3 · `AccountEnsureWorkflow`, activities, `AccountEnsurer` port, `identity-account` queue**
  - requirements: R2
  - files: `backend/identity-service/.../account/workflow/`, `TaskQueues.java`
  - verify: kill-after-each-step convergence; concurrent proofs → one account; lint for personal data in ids
  - parallel-safe: no
  - depends: T1, T2

- [ ] **T4 · Adoption and the slimmed Keycloak event path**
  - requirements: R4
  - files: `backend/identity-service/.../keycloak/sync/`, `backend/keycloak-extensions/.../UserSyncEventListener.java`
  - verify: unknown username adopted and flagged; roles ignore user-editable attributes; console `enabled` change adopted and audited
  - parallel-safe: yes
  - depends: T3

- [ ] **T5 · `AccountRepairWorkflow` and Schedule; drift classes D1..D9; metrics**
  - requirements: R4, R8
  - files: `backend/identity-service/.../account/repair/`
  - verify: each class seeded and repaired once; second run changes nothing; alerts at the max ages
  - parallel-safe: yes
  - depends: T3

- [x] **T6 · `ContactChangeWorkflow`** - implemented 2026-10-04
  - requirements: R3 (add, remove, primary), R5
  - files: `backend/identity-service/.../workflow/contactchange/`, `.../account/ContactChangeService.java`, `ContactChangeSteps.java`, `ContactRules.java`, `.../web/graphql/mutation/ContactMutationResolver.java`
  - verify: `ContactChangeWorkflowTest` (L3), `ContactChangeEndToEndTest` (L2, real Keycloak), `ContactRulesTest`, `ContactChangeRulesTest` (L1)
  - parallel-safe: yes
  - depends: T3

- [ ] **T7 · `AccountMergeWorkflow` and the `AccountMerged` event consumers**
  - requirements: R6
  - files: `backend/identity-service/.../account/merge/`, booking and catalog consumers
  - verify: sign-in refused while MERGING; contacts moved in one transaction; survivor intact
  - parallel-safe: yes
  - depends: T3

- [ ] **T8 · Deletion hook for `ErasureWorkflow`**
  - requirements: R7
  - files: `backend/identity-service/.../account/erasure/`
  - verify: tombstone kept; contacts released; Keycloak user deleted; id never reused
  - parallel-safe: yes
  - depends: T3, ET-PLT-008 T5

- [ ] **T9 · The three gates on `me` and booking entry**
  - requirements: R8
  - files: `backend/identity-service/.../graphql/`, `backend/booking-service/.../`
  - verify: each gate refused independently with a typed error and no profile fields
  - parallel-safe: yes
  - depends: T1

## 6. Out of scope

| Capability | Spec |
|---|---|
| Challenges, proofs, login handles, the Keycloak contact authenticator | [ET-IDN-001](../001-phone-otp-identity/) |
| Profile reads and writes, the listener transport | [ET-IDN-002](../002-keycloak-user-sync/) |
| Logout, revocation and back-channel delivery | [ET-IDN-003](../003-token-revocation/) |
| Realm, roles, token lifetimes, the buyer session | [ET-PLT-007](../../_platform/007-security-and-authorization/) |
| The erasure request, grace period and export | [ET-PLT-008](../../_platform/008-data-protection/) |
| Organizations, invitations, grants (what authority is added to an account) | [ET-ORG-001](../../organization/001-organizer-onboarding/), [ET-ORG-002](../../organization/002-teams-and-invitations/), [ET-ORG-003](../../organization/003-permission-resolution/) |
| Delivery of notifications about a change | [ET-NTF-001](../../notification/001-notification-transport/) |

Deliberately never in scope: **passwords for buyers**, **the plugin creating users**, and **automatic merge of different contacts**.

---

## Amendment, 2026-10-04 — staff, deletion, sessions

### ET-IDN-004-R9 · Account administration

**Acceptance**
- [ ] `deleteUser` is a soft delete (status DELETED, contacts released, Keycloak disabled, sessions ended) and repeatable; there is no hard delete
- [ ] `User.suspendReason` / `lockReason` are visible to administrators only
- [ ] `createUser` accepts a mobile number and a role; a number that does not parse refuses the call before the account is created
- [ ] `staffAccounts` lists staff-realm accounts, filtered by name or email and role

### ET-IDN-004-R10 · A person's own account

**Acceptance**
- [ ] `requestAccountDeletion` records a 30-day grace period and refuses an organization owner; asking twice returns the open request; `cancelAccountDeletion` withdraws it; both are audited
- [ ] `mySessions` lists the caller's live Keycloak sessions and marks the current one; `revokeSession` ends one of the caller's own sessions and refuses an id that is not theirs
- [ ] **Deferred**: executing a due deletion (a timer workflow calling `AccountService.delete`)

**Tests** `AccountLifecycleRulesTest` (L1)
