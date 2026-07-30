# ET-ORG-002 · Members, invitations and ownership transfer

> **Conformance** · US Part I §2 organization hierarchy · US Part I §5 team management · US Part II §9, §10 member and invitation CRUD · US Part IV §21 member state machine

## 1. Capability

An organization that sells tickets is rarely one person. Somebody creates the events,
somebody else runs the marketing, a third person answers the phone on the night, and a
fourth stands at the gate with a scanner. Each of those needs a different amount of access,
and exactly one person — the owner — is accountable for the money. This spec is how a
one-person application becomes a team without becoming a security problem.

It declares five organization roles with a strict inheritance chain, so that a permission
question has one answer rather than a matrix lookup per capability. It declares the
invitation flow, which is the only way a member is created: a tokenised link that expires,
that works whether or not the invitee has an account yet, and that can be revoked or
resent. It declares removal and self-departure, and the one rule that keeps an organization
from becoming unownable — the owner cannot leave or be removed, only transfer.

Ownership transfer is treated as the serious operation it is. It moves control of a
business's money to another person, so it is a two-party handshake with an expiring token,
confirmed by the recipient, not a role edit an admin can make unilaterally.

The Keycloak group tree mirrors all of this. It is written from MongoDB and never read as
truth — a token minted before a demotion carries the old groups, and the platform's
authorization
([ET-PLT-007](../../_platform/007-security-and-authorization/)) is built on that being
irrelevant. The mirror exists so an operator can see membership in the identity provider
and so future coarse partitioning has somewhere to hang.

## 2. Design decisions

**Five roles, one inheritance chain, resolved by rank.** `OWNER` ⊃ `ADMIN` ⊃ `MANAGER` ⊃
`CONTRIBUTOR`, with `MARKETER` a sibling of `MANAGER` that also contains `CONTRIBUTOR`.
Each role has a rank and a permission set, and a check is *does this role's transitive set
contain this permission*. A per-capability matrix in code — the shape the product
documentation uses to explain it to humans — becomes forty booleans nobody keeps aligned;
the matrix belongs in the docs and the chain belongs in the code.

**Exactly one `OWNER`, enforced by a unique index and not by a check.** A check-then-write
races. `identity_organization_members` carries a partial unique index on
`(organizationId)` where `role = OWNER`, so a second owner is a `DuplicateKeyException` at
the database rather than an invariant somebody remembered.

**A member is created only by accepting an invitation.** No mutation adds a member
directly, including for administrators. That gives every membership a provenance —
`invitedById`, a token, an acceptance timestamp — which is what a support conversation
about *who gave this person access* needs. The one exception is the `OWNER` row, created by
[ET-ORG-001](../001-organizer-onboarding/)'s approval saga, and it is an exception because
there is nobody to invite them.

**Invitations carry a token, expire in seven days, and are removed by a TTL index.** The
token is the identity of the invitation — the link works before the invitee has an account,
which is the whole point, so it cannot be keyed on a user. Seven days is long enough for
somebody who was away and short enough that a forwarded email is not a permanent key. The
TTL index on `expiresAt` does the cleanup, so no sweep is needed.

**Inviting somebody who already has a pending invitation revokes the old one.** Two live
invitations for one person to one organization means two links, one of which grants the
role the inviter changed their mind about.

**An invitation is accepted by the person the token was sent to, and identity is checked at
acceptance.** The token grants the right to *see* the invitation and to accept it as
whoever the email or phone identifies. An authenticated user whose identity does not match
the invitation is refused rather than silently accepted, because a forwarded link would
otherwise let the wrong person into the team.

**Ownership transfer is a two-party handshake with its own expiry.** The owner nominates an
existing `ADMIN`; the nominee confirms; only then do both roles change, atomically. A
one-sided transfer is how an organization is handed to somebody who has stopped working
there, and an unconfirmed transfer that never expires is a pending change of control
sitting in a table forever.

**Removal is a status, not a deletion.** `REMOVED` is terminal on the membership row, and
the row survives, because *who had access to this event's attendee list in March* is a
question the platform will be asked. Event access grants held by that member are revoked in
the same operation, or a removed member keeps scanner access to a specific event.

**The Keycloak mirror is best-effort and reconciled.** Every membership change attempts the
corresponding group write. A failure does not fail the mutation — MongoDB is the authority
and authorization does not read groups — but it does record drift, and a reconciliation
sweep repairs it. The alternative, failing a role change because Keycloak is restarting,
trades a real capability for a mirror nobody reads.

**Rejected alternatives**

- *A permission matrix per role in code.* Forty booleans across five roles, kept aligned by hand; the chain expresses the same thing in one comparison.
- *An `addMember` mutation for administrators.* Removes provenance from exactly the memberships most likely to be questioned.
- *Keying invitations on a user id.* Breaks the case the invitation exists for — inviting somebody who has no account yet.
- *An invitation that never expires.* A forwarded link becomes a permanent grant.
- *Unilateral ownership transfer by the current owner.* Hands a business to somebody who has not agreed to receive it.
- *Ownership transfer by a platform administrator.* Makes support staff able to reassign control of other people's money.
- *Deleting the membership row on removal.* Destroys the record of who had access when.
- *Reading organization role from the Keycloak groups claim.* Stale for a token lifetime after every change — [ET-PLT-007](../../_platform/007-security-and-authorization/) §2.
- *Failing a role change when the Keycloak group write fails.* Couples a real capability to a mirror that no decision reads.

## 3. Requirements

### ET-ORG-002-R1 · Five roles, one chain, one implementation

THE SYSTEM SHALL define exactly the five organization roles of §4 with the declared
inheritance, and every permission check SHALL resolve through it.

**Acceptance**
- [ ] `OrganizationRole` declares exactly `OWNER`, `ADMIN`, `MANAGER`, `MARKETER`, `CONTRIBUTOR`
- [ ] Each role declares its own permissions and its parents; the effective set is the transitive closure, computed in one method
- [ ] `OWNER` ⊃ `ADMIN` ⊃ `MANAGER` ⊃ `CONTRIBUTOR`; `MARKETER` ⊃ `CONTRIBUTOR`; `MARKETER` is not comparable to `MANAGER`
- [ ] No capability check compares a role by name — every check asks whether the effective set contains a permission
- [ ] A test asserts the effective set of each role equals the §4 table exactly
- [ ] The permission vocabulary is the closed catalogue of [ET-ORG-003](../003-permission-resolution/) §4

### ET-ORG-002-R2 · One owner, always, enforced at the database

THE SYSTEM SHALL permit exactly one `OWNER` membership per organization, and IF an
operation would produce a second or remove the only one, THEN THE SYSTEM SHALL refuse it.

**Acceptance**
- [ ] `identity_organization_members` carries a partial unique index on `organizationId` where `role = OWNER`
- [ ] A second `OWNER` insert raises `DuplicateKeyException`, mapped to `MEMBER_ALREADY_EXISTS`, not an internal error
- [ ] `removeMember` on the owner is refused with `OWNER_CANNOT_BE_REMOVED`
- [ ] `leaveOrganization` by the owner is refused with `OWNER_CANNOT_BE_REMOVED`
- [ ] `updateMemberRole` targeting the owner is refused with `OWNER_ROLE_IMMUTABLE`
- [ ] An `ADMIN` cannot change another `ADMIN`'s role; only the `OWNER` can
- [ ] Two concurrent ownership transfers for one organization produce exactly one owner

### ET-ORG-002-R3 · Membership is created only by accepting an invitation

WHEN a member joins an organization, THE SYSTEM SHALL create the membership from an
accepted invitation, and no mutation SHALL create one directly.

**Acceptance**
- [ ] No GraphQL mutation creates an `identity_organization_members` row other than `acceptInvitation`
- [ ] The `OWNER` row is created only by [ET-ORG-001](../001-organizer-onboarding/)'s approval saga, and that path is named in a comment saying why it is the exception
- [ ] Every membership carries `invitedById`, `joinedAt` and the invitation it came from
- [ ] `inviteTeamMember` requires `OWNER` or `ADMIN` of that organization, and an `ACTIVE` organization
- [ ] Inviting somebody who is already an active member is refused with `MEMBER_ALREADY_EXISTS`
- [ ] Inviting somebody with a pending invitation revokes the pending one and creates a new one — never two live

### ET-ORG-002-R4 · An invitation is a token that expires

WHEN an invitation is created, THE SYSTEM SHALL mint a single-use token valid for seven
days, and IF it is used after expiry, THEN THE SYSTEM SHALL refuse it.

**Acceptance**
- [ ] `invitationToken` is a 256-bit value from a `SecureRandom`, unique-indexed, and is the only way to look an invitation up before acceptance
- [ ] `expiresAt` is set from the `Clock` plus `identity.invitation.ttl` (P7D)
- [ ] A TTL index on `expiresAt` removes expired invitations without a sweep
- [ ] `invitationByToken` is `PUBLIC` and returns the organization name, the proposed role and the inviter's display name — **and no other field**, asserted by a test
- [ ] Accepting after expiry is refused with `INVITATION_EXPIRED` carrying `expiredAt`
- [ ] Accepting an invitation not in `PENDING` is refused with `INVITATION_NOT_PENDING` carrying `currentStatus`
- [ ] Frozen-clock tests assert acceptance at day 6 and refusal at day 8

### ET-ORG-002-R5 · Acceptance checks who is accepting

WHEN an invitation is accepted, THE SYSTEM SHALL verify the accepting identity matches the
invitee, and IF it does not, THEN THE SYSTEM SHALL refuse.

**Acceptance**
- [ ] An authenticated caller whose email and phone both differ from the invitation's is refused, and no membership is created
- [ ] An unauthenticated caller is sent to authenticate and returns to the same token
- [ ] A caller with no account registers, and the invitation is accepted on the same identity once created
- [ ] On acceptance the membership is created with the invitation's `proposedRole`, any `eventAccessGrants` on the invitation become grants ([ET-ORG-003](../003-permission-resolution/)), the invitation becomes `ACCEPTED`, and the inviter is notified
- [ ] `declineInvitation` moves it to `DECLINED` and notifies the inviter
- [ ] Two concurrent acceptances of one token produce exactly one membership
- [ ] Acceptance and the grants it creates are one transaction

### ET-ORG-002-R6 · Removal is a status, and it takes event access with it

WHEN a member is removed or leaves, THE SYSTEM SHALL mark the membership `REMOVED`, revoke
their event access, and retain the record.

**Acceptance**
- [ ] `removeMember` requires `OWNER` or `ADMIN`; `leaveOrganization` is the member's own action
- [ ] Both set `status = REMOVED` and `removedAt`; neither deletes a document
- [ ] Every `identity_event_access_grants` row that member holds for that organization's events is revoked in the same transaction
- [ ] `identity.MemberRemoved` is published, session-keyed on `organizationId`
- [ ] The member loses access on their **next request**, not on their next token — because organization role is resolved per request ([ET-PLT-007](../../_platform/007-security-and-authorization/) R7)
- [ ] `MemberStatus` is `ACTIVE`, `INACTIVE`, `SUSPENDED`, `REMOVED`, with `REMOVED` terminal
- [ ] A removed member re-invited later gets a new membership row; the old one is retained

### ET-ORG-002-R7 · Ownership transfers only by a confirmed handshake

WHEN an owner initiates a transfer, THE SYSTEM SHALL require the nominee's confirmation
within the transfer window before either role changes.

**Acceptance**
- [ ] `initiateOwnershipTransfer` requires `OWNER` and a nominee who is an **active `ADMIN`** of the same organization; anything else is refused with `TRANSFER_TARGET_INELIGIBLE` carrying `requiredRole`
- [ ] The transfer is a document in `identity_ownership_transfers` with its own token, `expiresAt` from `identity.transfer.ttl` (P3D), and status `PENDING`
- [ ] No role changes at initiation
- [ ] `confirmOwnershipTransfer` may be called only by the nominee, and it changes both roles — nominee to `OWNER`, previous owner to `ADMIN` — in one transaction
- [ ] `cancelOwnershipTransfer` is available to the initiating owner while `PENDING`
- [ ] An expired transfer changes nothing and is refused
- [ ] `identity_organizations.ownerId` and `identity_users.primaryOrganizationId` are updated in the same transaction as the roles
- [ ] No platform administrator can transfer ownership — the mutation is not available to `ADMIN`

### ET-ORG-002-R8 · Keycloak groups mirror membership, and drift is repaired

THE SYSTEM SHALL reflect every membership change into the Keycloak group tree, and IF a
group write fails, THEN THE SYSTEM SHALL complete the change anyway and repair the mirror
later.

**Acceptance**
- [ ] Creating, changing or removing a membership attempts the corresponding add or remove on `/organizations/{slug}/{role-group}`
- [ ] A Keycloak failure is logged with the organization and user, does not fail the mutation, and marks the membership `mirrorPending`
- [ ] A scheduled sweep under `lock:sweep:group-mirror` reconciles Keycloak's groups to MongoDB and clears the marker
- [ ] The sweep is one-directional: MongoDB wins, always; a group membership with no MongoDB row is removed
- [ ] No authorization decision anywhere reads a Keycloak group — asserted by a lint check for group claims in decision code
- [ ] The count of pending mirrors is a metric and alerts when it stays non-zero

## 4. Model

### Roles and their permissions

Permission names are rows of the [ET-ORG-003](../003-permission-resolution/) §4 catalogue.

| Role | Rank | Parents | Adds |
|---|---|---|---|
| `CONTRIBUTOR` | 1 | — | `event:view`, `attendee:view`, `ticket:scan` |
| `MARKETER` | 2 | `CONTRIBUTOR` | `analytics:view`, `promotion:manage` |
| `MANAGER` | 2 | `CONTRIBUTOR` | `event:create`, `event:edit`, `event:publish`, `analytics:view`, `promotion:manage`, `financial:view`* |
| `ADMIN` | 3 | `MANAGER`, `MARKETER` | `event:delete`, `team:invite`, `team:remove`, `team:role`, `organization:edit`, `payout:request`* |
| `OWNER` | 4 | `ADMIN` | `organization:billing`, `organization:transfer`, `organization:delete`, `payout:request` |

\* configurable per organization by `settings.managersCanViewFinancials` and
`settings.adminsCanRequestPayouts` — the only two permissions that are not fixed by role,
and they are opt-in.

`MARKETER` and `MANAGER` share rank 2 and are **not** comparable: a marketer is not a
lesser manager, and a role comparison that assumes a total order gets this wrong.

### Documents

`identity_organization_members`

| Field | Type | Notes |
|---|---|---|
| `_id` | `String` | |
| `userId`, `organizationId` | `String` | unique together |
| `role` | `OrganizationRole` | |
| `status` | `MemberStatus` | `ACTIVE`, `INACTIVE`, `SUSPENDED`, `REMOVED` |
| `customPermissions`, `deniedPermissions` | `List<String>` | catalogue rows; ET-ORG-003 resolves them |
| `invitedById`, `invitationId` | `String` | provenance |
| `mirrorPending` | `boolean` | R8 |
| `joinedAt`, `lastActiveAt`, `removedAt` | `Instant` | |
| `createdAt`, `updatedAt` | `Instant` | |

`identity_team_invitations`

| Field | Type | Notes |
|---|---|---|
| `_id` | `String` | |
| `organizationId` | `String` | |
| `email`, `phoneNumber`, `inviteeName` | `String` | at least one of email or phone required |
| `proposedRole` | `OrganizationRole` | never `OWNER` |
| `eventAccessGrants` | `List<{eventId, eventRole, expiresAt}>` | applied on acceptance |
| `invitedById`, `message` | `String` | |
| `invitationToken` | `String` | 256-bit, unique |
| `expiresAt` | `Instant` | **TTL index** |
| `status` | `InvitationStatus` | `PENDING`, `ACCEPTED`, `DECLINED`, `EXPIRED`, `REVOKED` |
| `acceptedAt`, `declinedAt`, `revokedAt` | `Instant` | |

`identity_ownership_transfers`

| Field | Type | Notes |
|---|---|---|
| `_id` | `String` | |
| `organizationId`, `fromUserId`, `toUserId` | `String` | |
| `transferToken` | `String` | unique |
| `expiresAt` | `Instant` | `P3D` |
| `status` | `TransferStatus` | `PENDING`, `CONFIRMED`, `CANCELLED`, `EXPIRED` |
| `initiatedAt`, `confirmedAt`, `cancelledAt` | `Instant` | |

### Indexes beyond [ET-PLT-002](../../_platform/002-persistence-baseline/) §4

| Collection | Index | Kind | Why |
|---|---|---|---|
| `identity_organization_members` | `{ organizationId: 1 }` where `role = OWNER` | **partial unique** | R2 — one owner, at the database |
| `identity_ownership_transfers` | `{ transferToken: 1 }` | unique | confirmation lookup |
| `identity_ownership_transfers` | `{ organizationId: 1, status: 1 }` | compound | one pending transfer per organization |

### Invitation state machine

| From | Action | To |
|---|---|---|
| — | `inviteTeamMember` | `PENDING` |
| `PENDING` | `acceptInvitation` | `ACCEPTED` |
| `PENDING` | `declineInvitation` | `DECLINED` |
| `PENDING` | `revokeInvitation` | `REVOKED` |
| `PENDING` | *TTL elapses* | *removed* |
| `PENDING` | `resendInvitation` | `REVOKED` + a new `PENDING` |

`ACCEPTED`, `DECLINED` and `REVOKED` are terminal. A resend is a revoke plus a new
invitation with a new token, never a mutation of the old one — so a link already sent
cannot be silently repointed at a different role.

### Member state machine

| From | Action | To |
|---|---|---|
| — | `acceptInvitation` | `ACTIVE` |
| `ACTIVE` | `suspendMember` | `SUSPENDED` |
| `SUSPENDED` | `reactivateMember` | `ACTIVE` |
| `ACTIVE` | `deactivateMember` | `INACTIVE` |
| `INACTIVE` | `reactivateMember` | `ACTIVE` |
| any non-terminal | `removeMember` / `leaveOrganization` | `REMOVED` |

### Keycloak group tree

```
/organizations/{slug}/
├── owners        ← exactly one member
├── admins
├── managers
├── marketers
└── contributors
```

Created by [ET-ORG-001](../001-organizer-onboarding/)'s saga step 5. Written from MongoDB
by this spec. **Read by no authorization decision, ever.**

### GraphQL

Subgraph `identity`. Every field carries `@auth` explicitly.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `organizationMembers(organizationId, role, status, page)` | query | `AUTHENTICATED` | `OrganizationMemberPage!` |
| `myOrganizationMembership(organizationId)` | query | `AUTHENTICATED` | `OrganizationMember` |
| `myOrganizations` | query | `AUTHENTICATED` | `[Organization!]!` |
| `pendingInvitations(organizationId)` | query | `AUTHENTICATED` | `[TeamInvitation!]!` |
| `myPendingInvitations` | query | `AUTHENTICATED` | `[TeamInvitation!]!` |
| `invitationByToken(token)` | query | `PUBLIC` | `InvitationPreview` |
| `pendingOwnershipTransfer(organizationId)` | query | `AUTHENTICATED` | `OwnershipTransfer` |
| `inviteTeamMember(input)` | mutation | `ORGANIZER` | `TeamInvitation!` |
| `resendInvitation(id)` | mutation | `ORGANIZER` | `TeamInvitation!` |
| `revokeInvitation(id)` | mutation | `ORGANIZER` | `TeamInvitation!` |
| `acceptInvitation(input)` | mutation | `AUTHENTICATED` | `OrganizationMember!` |
| `declineInvitation(token)` | mutation | `AUTHENTICATED` | `Boolean!` |
| `updateMemberRole(input)` | mutation | `ORGANIZER` | `OrganizationMember!` |
| `suspendMember(input)` | mutation | `ORGANIZER` | `OrganizationMember!` |
| `reactivateMember(input)` | mutation | `ORGANIZER` | `OrganizationMember!` |
| `removeMember(organizationId, memberId)` | mutation | `ORGANIZER` | `OrganizationMember!` |
| `leaveOrganization(organizationId)` | mutation | `AUTHENTICATED` | `Boolean!` |
| `initiateOwnershipTransfer(input)` | mutation | `ORGANIZER` | `OwnershipTransfer!` |
| `confirmOwnershipTransfer(token)` | mutation | `AUTHENTICATED` | `Organization!` |
| `cancelOwnershipTransfer(id)` | mutation | `ORGANIZER` | `OwnershipTransfer!` |

`InvitationPreview` is a deliberately narrow type — `organizationName`, `organizationLogoUrl`,
`proposedRole`, `inviterDisplayName`, `expiresAt` — because the token is a bearer credential
and anything it exposes is exposed to whoever the link was forwarded to.

Every `ORGANIZER`-gated mutation is additionally checked against the caller's role **in
that organization** by [ET-ORG-003](../003-permission-resolution/); the coarse gate is
never sufficient ([ET-PLT-007](../../_platform/007-security-and-authorization/) §2).

### Events

| Tier | Name | When | Consumers |
|---|---|---|---|
| bus | `identity.MemberRoleChanged` v1 | `updateMemberRole`, transfer confirmation | catalog, booking → invalidate |
| bus | `identity.MemberRemoved` v1 | removal, departure | catalog, booking → invalidate |
| module | `TeamMemberJoinedEvent` | acceptance | notify the inviter and the owner |
| module | `InvitationCreatedEvent` | invitation, resend | send the invitation |
| module | `OwnershipTransferredEvent` | confirmation | notify both parties |

Both bus rows are session-keyed on `organizationId`
([ET-PLT-003 §4](../../_platform/003-event-contract/)).

### Redis keys

| Key | TTL | Purpose |
|---|---|---|
| `lock:sweep:group-mirror` | 30 s | the R8 reconciliation mutex |

### Configuration

| Property | Value |
|---|---|
| `identity.invitation.ttl` | `P7D` |
| `identity.transfer.ttl` | `P3D` |
| `identity.team.max-members` | `null` — unbounded unless an organization sets one |

### Error codes

`MEMBER_UNKNOWN`, `MEMBER_ALREADY_EXISTS`, `OWNER_CANNOT_BE_REMOVED`,
`OWNER_ROLE_IMMUTABLE`, `INVITATION_UNKNOWN`, `INVITATION_EXPIRED`,
`INVITATION_NOT_PENDING`, `TRANSFER_TARGET_INELIGIBLE` — rows of
[ET-PLT-005 §4](../../_platform/005-error-contract/) introduced by this spec.

## 5. Tasks

- [ ] **T1 · `OrganizationRole`, the inheritance chain, the effective-set test**
  - requirements: R1
  - files: `backend/identity-service/.../domain/enums/OrganizationRole.java`
  - verify: each role's effective set equals §4; `MARKETER` and `MANAGER` are incomparable
  - parallel-safe: no — ET-ORG-003 depends on it
  - depends: —

- [ ] **T2 · Member and invitation documents, the partial unique owner index**
  - requirements: R2, R3
  - files: `backend/identity-service/.../domain/model/`, `.../config/MongoIndexInitializer.java`
  - verify: a second `OWNER` insert raises a duplicate key mapped to `MEMBER_ALREADY_EXISTS`
  - parallel-safe: no
  - depends: T1

- [ ] **T3 · `inviteTeamMember`, resend, revoke; the revoke-on-reinvite rule**
  - requirements: R3, R4
  - files: `backend/identity-service/.../service/impl/TeamInvitationServiceImpl.java`
  - verify: never two live invitations for one person and organization; day-6 and day-8 boundaries
  - parallel-safe: yes
  - depends: T2

- [ ] **T4 · `invitationByToken` and the narrow preview type**
  - requirements: R4
  - files: `backend/identity-service/.../web/graphql/query/`
  - verify: the preview exposes exactly five fields; nothing else is reachable by token
  - parallel-safe: yes
  - depends: T3

- [ ] **T5 · `acceptInvitation`: identity check, grants, one transaction**
  - requirements: R5
  - files: `backend/identity-service/.../service/impl/TeamInvitationServiceImpl.java`
  - verify: a mismatched identity is refused; two concurrent acceptances produce one membership
  - parallel-safe: no
  - depends: T3

- [ ] **T6 · Role changes, suspension, removal, departure, grant revocation**
  - requirements: R2, R6
  - files: `backend/identity-service/.../service/impl/OrganizationMemberServiceImpl.java`
  - verify: the owner cannot be removed or re-roled; removal revokes event grants in the same transaction
  - parallel-safe: yes
  - depends: T2

- [ ] **T7 · Ownership transfer: initiate, confirm, cancel, expire**
  - requirements: R7
  - files: `backend/identity-service/.../service/impl/OwnershipTransferServiceImpl.java`
  - verify: two concurrent confirmations produce one owner; no `ADMIN`-role platform path exists
  - parallel-safe: no
  - depends: T6

- [ ] **T8 · The Keycloak mirror, `mirrorPending`, the reconciliation sweep**
  - requirements: R8
  - files: `backend/identity-service/.../infrastructure/keycloak/`, `.../scheduler/`
  - verify: a Keycloak outage does not fail a role change; the sweep repairs both directions
  - parallel-safe: yes
  - depends: T6

- [ ] **T9 · The subgraph half; `@auth` on every field**
  - requirements: R1–R8
  - files: `backend/identity-service/src/main/resources/graphql/schema.graphqls`
  - verify: `compose-supergraph.sh --static`
  - parallel-safe: no — shared SDL with ET-ORG-001 and ET-ORG-003
  - depends: T5, T7

## 6. Out of scope

| Capability | Spec |
|---|---|
| The organization's own lifecycle and the approval saga | [ET-ORG-001](../001-organizer-onboarding/) |
| The permission catalogue, resolution order, event access grants | [ET-ORG-003](../003-permission-resolution/) |
| Realm roles, `@auth`, token validation, tenant scoping | [ET-PLT-007](../../_platform/007-security-and-authorization/) |
| Sending the invitation on email, SMS or WhatsApp | [ET-NTF-001](../../notification/001-notification-transport/), [ET-NTF-002](../../notification/002-lifecycle-triggers/) |
| Registering an account for an invitee who has none | [ET-IDN-001](../../identity/001-phone-otp-identity/) |
| Audit rows for role changes and transfers | [ET-PLT-009](../../_platform/009-audit-trail/) |
| What a member may do to an event once they have a role | [ET-CAT-001](../../catalog/001-event-lifecycle/) |

Deliberately never in scope: **a direct `addMember` mutation** (removes provenance from the
memberships most likely to be questioned), **unilateral or administrator-driven ownership
transfer** (hands a business to somebody who has not agreed to receive it), and **reading
organization role from Keycloak groups** (stale for a token lifetime after every change).
