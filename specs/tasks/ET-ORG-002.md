# ET-ORG-002 · Members, invitations and ownership transfer — tasks

> **Spec** [`specs/organization/002-teams-and-invitations/spec.md`](../organization/002-teams-and-invitations/spec.md) · **Wave 1** · `blocked_by:` ET-PLT-002, ET-PLT-003, ET-PLT-005, ET-PLT-007, ET-ORG-001
> **Screen** `Org Admin - Team & Permissions.dc.html` — **read it first**
> **Routes** `apps/organization-admin/src/app/(dashboard)/team/page.tsx`, `team/invite/page.tsx`, plus a public invitation-acceptance route
> **Verify** `mvn -q -f backend/identity-service test -Dgroups=ET-ORG-002 -DfailIfNoTests=false` · `compose-supergraph.sh --static`

7 queries, 13 mutations — the largest GraphQL surface in Wave 1. Email exists in the channel set
(**D-15**) largely *because* the invitation flow needs a durable addressable identity: you cannot
invite someone by phone number they have not registered yet.

## R0 · Reconcile

```bash
grep -rn 'OrganizationRole\|Invitation\|Member' backend/identity-service --include='*.java' | grep -v /src/test/
```

Check especially:
- Is there a **partial unique index** on owner, or is single-ownership enforced by a read-check?
  A read-check is `contradicted` — two concurrent transfers race straight through it.
- Do role changes call Keycloak **synchronously**? R8 requires a mirror that tolerates an outage.

## A · Backend

### BE-1 · `OrganizationRole`, the inheritance chain, the effective-set test
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** no *([`ET-ORG-003`](ET-ORG-003.md) depends on it)*
- **Acceptance** each role's effective set equals §4; **`MARKETER` and `MANAGER` are
  incomparable.**
- Incomparability is the point: a role lattice is not a ladder. Modelling it as one integer level
  makes a marketer who can post announcements also able to edit ticket prices.

### BE-2 · Member and invitation documents, the partial unique owner index
- **Spec** R2, R3 · **§5** T2 · **depends** BE-1 · **parallel-safe** no
- **Acceptance** a second `OWNER` insert raises a duplicate key mapped to `MEMBER_ALREADY_EXISTS`.
- Enforce at the **database**, not by a check. Confirm the index exists with its partial filter
  via MongoDB MCP `collection-indexes` — a partial unique index declared but never created passes
  every single-threaded test.

### BE-3 · `inviteTeamMember`, resend, revoke; the revoke-on-reinvite rule
- **Spec** R3, R4 · **§5** T3 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** **never two live invitations** for one person and organization; day-6 and day-8
  boundaries asserted on a frozen clock.
- Two live invitations means two tokens, and revoking one leaves the other working — which is
  exactly the case an operator believes they have closed.

### BE-4 · `invitationByToken` and the narrow preview type
- **Spec** R4 · **§5** T4 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** the preview exposes **exactly five fields**; nothing else is reachable by token.
- This query is reachable by anyone holding a token, which by design includes anyone who was
  forwarded the email. A full `Organization` here leaks the tenant to a stranger.

### BE-5 · `acceptInvitation` — identity check, grants, one transaction
- **Spec** R5 · **§5** T5 · **depends** BE-3 · **parallel-safe** no
- **Acceptance** a **mismatched identity is refused**; two concurrent acceptances produce **one**
  membership.
- The token proves the invitation, not the person. Accepting while signed in as someone else must
  refuse, or an invitation becomes transferable by forwarding.

### BE-6 · Role changes, suspension, removal, departure, grant revocation
- **Spec** R2, R6 · **§5** T6 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** the owner **cannot** be removed or re-roled; removal **revokes event grants in
  the same transaction**.
- Same transaction, not "and then". A removal that commits while grants survive leaves a
  removed member with event access.
- Fires [`ET-IDN-003`](ET-IDN-003.md) BE-7 — the removed member loses access on their **next**
  request.

### BE-7 · Ownership transfer — initiate, confirm, cancel, expire
- **Spec** R7 · **§5** T7 · **depends** BE-6 · **parallel-safe** no
- **Acceptance** two concurrent confirmations produce **one** owner; **no `ADMIN`-role platform
  path exists** to reassign ownership.
- Two-step by design: an owner who mistypes a transfer target must not lose the organization on
  one click. The absence of an admin override is deliberate — a platform admin able to reassign
  ownership is a support-social-engineering vector.

### BE-8 · The Keycloak mirror, `mirrorPending`, the repair Schedule
- **Spec** R8 · **§5** T8 · **depends** BE-6 · **parallel-safe** yes
- **Acceptance** a **Keycloak outage does not fail a role change**; the repair run fixes **both
  directions**.
- MongoDB is authoritative for organization roles; Keycloak is a mirror. Blocking a role change on
  the mirror inverts that.

### BE-9 · Subgraph half; `@auth` on every field
- **Spec** R1–R8 · **§5** T9 · **depends** BE-5, BE-7 · **parallel-safe** **no — shared identity SDL with [`ET-ORG-001`](ET-ORG-001.md) and [`ET-ORG-003`](ET-ORG-003.md)**

## B · Contract

### GQL-1 · 7 queries, 13 mutations
- **depends** BE-9 · **parallel-safe** no
- `compose-supergraph.sh --static` → `npm run codegen` → commit; restart the local router.
- **Acceptance** the invitation preview type carries exactly five fields **in the composed
  schema** — a resolver that hides fields is not the same as a type that lacks them.

## C · Frontend — `Org Admin - Team & Permissions.dc.html`

### FE-1 · Team roster
- **depends** GQL-1, F0-2 · **parallel-safe** no
- Route `(dashboard)/team`. Columns, grouping and empty state from the screen.
- Role as `Badge` — colour ∈ `gray|accent|green|amber|red|blue`, closed set. Owner distinguished.
- **Roles are incomparable (BE-1), so do not render them as a hierarchy.** A sorted "seniority"
  list is a lie about the model.
- **testids** `team-member-row`, `team-member-role`, `team-owner-badge`

### FE-2 · Invite flow
- **depends** FE-1 · **parallel-safe** yes
- Route `team/invite`. Email + role. Re-inviting an already-invited person **revokes and replaces**
  (BE-3) — say so before firing, because the previous link stops working.
- **testids** `invite-email`, `invite-role`, `invite-submit`, `invite-reinvite-warning`

### FE-3 · Pending invitations
- **depends** FE-1 · **parallel-safe** yes
- Expiry countdown (7 days; the day-6/day-8 boundaries are real), resend, revoke.
- **testids** `invitation-row`, `invitation-expiry`, `invitation-resend`, `invitation-revoke`

### FE-4 · Invitation acceptance — a **public** route
- **depends** GQL-1 · **parallel-safe** yes
- Reached by token from an email, often by someone with no session. Shows only the five preview
  fields. Signed-in-as-someone-else must refuse **clearly** — "This invitation is for a different
  account", with a sign-out affordance — not a bare `ACTOR_NOT_PERMITTED`.
- States: valid, expired, revoked, already-accepted, identity-mismatch. All five are designed
  screens.
- **testids** `invitation-preview`, `invitation-accept`, `invitation-state-<state>`

### FE-5 · Role change, suspend, remove
- **depends** FE-1 · **parallel-safe** yes
- Owner row offers neither remove nor re-role (BE-6) — **absent**, not disabled-with-a-tooltip.
- Removal confirms and states the consequence: the member is signed out and loses event access
  immediately.
- **testids** `member-role-change`, `member-remove`, `member-remove-confirm`

### FE-6 · Ownership transfer
- **depends** BE-7, GQL-1 · **parallel-safe** yes
- Two-step: initiate (current owner) → confirm (recipient). Pending state visible to both. Cancel
  available until confirmed. Expiry shown.
- The most consequential action in the app — confirm copy names exactly what is lost.
- **testids** `ownership-transfer-initiate`, `ownership-transfer-confirm`, `ownership-transfer-cancel`, `ownership-transfer-pending`

## D · Tests

### TS-1 · Roles *(L1)*
Effective set per role equals §4; `MARKETER` and `MANAGER` incomparable, asserted in **both**
directions.

### TS-2 · Single ownership *(L3)*
Second `OWNER` insert → duplicate key → `MEMBER_ALREADY_EXISTS`. Index confirmed live via MCP,
with its partial filter.

### TS-3 · Invitations *(L1 + L3)*
Never two live invitations; re-invite revokes the prior; day-6 valid / day-8 expired on a frozen
clock; preview exposes exactly five fields.

### TS-4 · Acceptance *(L3)*
Mismatched identity refused; `Concurrency.inParallel` two acceptances → one membership.

### TS-5 · Removal *(L3)*
Owner cannot be removed or re-roled; removal revokes grants **in the same transaction** — assert
by failing the grant revocation and observing the membership survive.

### TS-6 · Transfer *(L3)*
Two concurrent confirmations → one owner; no admin path exists (assert the mutation is absent from
the composed schema, not merely unimplemented).

### TS-7 · Keycloak mirror *(L3, Keycloak Testcontainer)*
**Stop the container**; a role change still succeeds and marks `mirrorPending`. The Schedule's repair
run fixes both directions.

### TS-8 · e2e *(L5, org-admin — requires F0-2)*
- Roster, invite, pending, accept, role change, remove, transfer.
- Invitation acceptance in all five states, including signed-in-as-someone-else.
- Loading, empty (a team of one is an empty state worth designing), error, populated.
- Compliance: `data-brand="org-admin"`, teal, Inter, tokens only.

## E · Gate

> **Verification note, 2026-09-01 — closed.** Two by-id reads in this spec's surface answered
> without checking who was asking. `organizationMember(organizationId, userId)` is now
> tenant-scoped and `ownershipTransfer(id)` is scoped to the two named parties; `transferToken`
> has left the output type entirely, since a bearer credential nothing reads back has no reason to
> be a selectable field. See
> [F-011](../FINDINGS.md#f-011--two-more-by-id-reads-that-answer-without-checking-one-of-them-holding-half-a-credential).
> The remaining boxes below are the spec's own verification work and are still open.


- [ ] R0 recorded; read-check ownership enforcement classified `contradicted`
- [x] Role effective sets match §4; incomparability asserted both ways — **F-013, refactored
      2026-09-01.** `OrganizationRole` carried eleven `canX()` methods each comparing by name,
      plus a twelfth list in a fall-through `switch` — twelve lists that had to agree, and a
      chain where §4 declares a DAG. Each role now declares only what it adds and who it inherits
      from; one closure walks the parents; every capability asks that set.
      `AuthorizationServiceImpl.isAtLeast` became `includes` (subset containment), which has a
      correct answer for an incomparable pair where `>=` does not. `OrganizationRoleTest`,
      13 cases at layer 1, mutation-verified. **Two §4 deviations recorded rather than
      half-corrected**: the permission vocabulary is `SCREAMING_CASE` against §4/D-17'''s
      `module:action` (38 call sites across three services), and the two permissions §4 marks
      opt-in are unconditional pending ET-ORG-003. See
      [F-013](../FINDINGS.md#f-013--the-role-hierarchy-was-eleven-hand-maintained-lists-and-a-fall-through-chain)
- [x] Partial unique owner index exists **live**, with its filter — **F-012, built 2026-09-01.**
      It did not exist: the collection carried `{userId, organizationId}` unique and
      `{organizationId, role, status}` compound, neither of which prevents a second owner.
      `uniq_organization_owner` on `{organizationId}`, unique, partial on `role = OWNER`, asserted
      against a live database by `IdentityIndexRegistryTest`. Two `identity_ownership_transfers`
      rows §4 declares were missing alongside it. See
      [F-012](../FINDINGS.md#f-012--an-invitation-link-disclosed-the-invitees-contact-details-and-the-owner-index-was-never-built)
- [x] Never two live invitations; day-6/day-8 boundaries asserted — **F-014 and F-017, 2026-09-01.** `isValid`/`isExpired` called
      `Instant.now()`, so no boundary test was possible; both now take the instant, and
      `isExpired` uses `!isAfter` rather than `isBefore` — at the expiry instant exactly the
      old form answered false, keeping the token valid through the whole of its final moment.
      Day 6, day 7 exactly, one instant before, and day 8 are all asserted.
      **Never two live** — **F-017**: `invite` refused a second invitation where R3 says revoke the
      pending one and create a new one, stranding an inviter with a mistyped role for seven days.
      Supersession is now scoped to the address, the organization and `PENDING` only, in one
      transaction with the new invitation; `MEMBER_ALREADY_EXISTS` and `ORGANIZATION_NOT_ACTIVE`
      were both absent and are now checked. `InvitationUniquenessTest`, 7 cases on a replica set.
      See [F-017](../FINDINGS.md#f-017--re-inviting-refused-instead-of-replacing-and-two-checks-r3-requires-were-absent)
- [x] Preview type carries exactly five fields **in the composed schema** — **F-012.**
      `InvitationPreview` did not exist and `invitationByToken` returned the whole
      `TeamInvitation`, so a forwarded invitation link disclosed the invitee's `email` and
      `phoneNumber` along with the token itself. The five fields §4 names are now the type, and
      `InvitationPreviewTest` asserts exactly five — a sixth is a leak, fewer leaves the
      acceptance page unable to say who is inviting whom. Mutation-verified twice
- [x] Mismatched identity refused; concurrent acceptance yields one membership — **F-014.** `accept(token, userId)` created the membership
      for whoever called it, so any signed-in holder of a forwarded link joined in the proposed
      role. `addressedTo` now compares the invitation against the accepting user'''s own account;
      the refusal is `INVITATION_NOT_ADDRESSED_TO_CALLER` and says who asked, never who it was
      for. Completes the chain F-012 opened. See
      [F-014](../FINDINGS.md#f-014--a-forwarded-invitation-link-was-enough-to-join-the-organization).
      **Concurrent acceptance now yields one membership**: acceptance claims the invitation with a
      conditional `PENDING` update before creating anything, so exactly one of twenty-four
      parallel callers wins, and the claim, the membership and its grants are one transaction.
      `ConcurrentAcceptanceTest` (layer 5, `@RepeatedTest`) and `InvitationAcceptanceLintTest`,
      both mutation-verified
- [ ] Owner cannot be removed or re-roled, in backend and UI
- [x] Removal revokes event grants in the same transaction — **F-015, built 2026-09-01.** Neither
      `removeMember` nor `leaveOrganization` touched `identity_event_access_grants` at all, so a
      removed member kept every grant they held and went on editing the event they were removed
      over — while the team screen showed them gone. One transition now revokes every ACTIVE grant
      for that organization'''s events, sets `REMOVED` and `removedAt`, and does both in one
      transaction. Scoped by organization and by member, live grants only; Keycloak deliberately
      outside the boundary. `MemberRemovalTest` (6 cases, replica set) and `MemberRemovalLintTest`,
      mutation-verified. See
      [F-015](../FINDINGS.md#f-015--a-removed-member-kept-every-event-grant-they-held)
- [x] Concurrent transfer confirmations yield one owner — **F-016, fixed 2026-09-01.** Confirmation
      read the status before writing it, so two confirmations both ran the handover and the second
      demoted the owner the first had just promoted — leaving nobody as OWNER. A conditional
      `PENDING → COMPLETED` claim now admits exactly one, and the demote, the promote and
      `organizations.ownerId` are one transaction. The eligibility check also had only half its
      condition: role without status, so a SUSPENDED or REMOVED admin could be handed the business.
      `OwnershipTransferTest` (11 cases, layer 5) and `OwnershipTransferLintTest`,
      mutation-verified. **"No admin override exists" is separately true**: no mutation grants a
      platform administrator the transfer. See
      [F-016](../FINDINGS.md#f-016--a-suspended-admin-could-be-handed-the-organization-and-two-confirmations-could-run-the-handover-twice)
- [~] Role change survives a stopped Keycloak; sweep repairs **one** direction — **F-018,
      2026-09-01.** Surviving a stopped Keycloak already worked, but the failure was only a log
      line: `mirrorPending` did not exist and no sweep did either, so drift was real, invisible
      and unrepairable short of reconciling every member on the platform. The marker exists, and
      the repair runs as the Temporal Schedule `identity-group-mirror-repair` (overlap `SKIP`,
      every PT60S) through `GroupMirrorActivitiesImpl`, which uses the strict Keycloak writes and
      clears the marker **only on success** — `GroupMirrorScheduleTest` (L1),
      `GroupMirrorRepairWorkflowTest` (L3), ET-PLT-015 2026-09-13. A completed ownership transfer's
      Keycloak mirror is a retried activity that marks both memberships pending when it never lands
      (`OwnershipTransferWorkflowTest`). R8'''s lint —
      no authorization decision reads a Keycloak group — passes and is mutation-verified.
      **Open**: the reverse direction (a Keycloak group membership with no MongoDB row) needs a
      group-members listing `KeycloakService` does not expose, and the pending-count metric is
      not published. See
      [F-018](../FINDINGS.md#f-018--a-failed-keycloak-group-write-was-a-log-line-and-nothing-repaired-the-drift)
- [ ] `Org Admin - Team & Permissions.dc.html` read; roster shape and empty state match
- [ ] Invitation acceptance covers all five states
- [ ] e2e green on the F0-2 harness; compliance suite green
- [ ] `mvn -q -f backend/identity-service test -Dgroups=ET-ORG-002 -DfailIfNoTests=false` green
- [ ] Spec `status:` → `implemented`
