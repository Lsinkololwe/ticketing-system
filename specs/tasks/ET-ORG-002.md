# ET-ORG-002 · Members, invitations and ownership transfer — tasks

> **Spec** [`specs/organization/002-teams-and-invitations/spec.md`](../organization/002-teams-and-invitations/spec.md) · **Wave 1** · `blocked_by:` ET-PLT-002, ET-PLT-003, ET-PLT-005, ET-PLT-007, ET-ORG-001
> **Screen** `Org Admin - Team & Permissions.dc.html` — **read it first**
> **Routes** `apps/organization-admin/src/app/(dashboard)/team/page.tsx`, `team/invite/page.tsx`, plus a public invitation-acceptance route
> **Verify** `mvn -q -f backend/identity-service test -Dgroups=ET-ORG-002 -DfailIfNoTests=true` · `compose-supergraph.sh --static`

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

### BE-8 · The Keycloak mirror, `mirrorPending`, the reconciliation sweep
- **Spec** R8 · **§5** T8 · **depends** BE-6 · **parallel-safe** yes
- **Acceptance** a **Keycloak outage does not fail a role change**; the sweep repairs **both
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
**Stop the container**; a role change still succeeds and marks `mirrorPending`. The sweep repairs
in both directions.

### TS-8 · e2e *(L5, org-admin — requires F0-2)*
- Roster, invite, pending, accept, role change, remove, transfer.
- Invitation acceptance in all five states, including signed-in-as-someone-else.
- Loading, empty (a team of one is an empty state worth designing), error, populated.
- Compliance: `data-brand="org-admin"`, teal, Inter, tokens only.

## E · Gate

- [ ] R0 recorded; read-check ownership enforcement classified `contradicted`
- [ ] Role effective sets match §4; incomparability asserted both ways
- [ ] Partial unique owner index exists **live**, with its filter
- [ ] Never two live invitations; day-6/day-8 boundaries asserted
- [ ] Preview type carries exactly five fields **in the composed schema**
- [ ] Mismatched identity refused; concurrent acceptance yields one membership
- [ ] Owner cannot be removed or re-roled, in backend and UI
- [ ] Removal revokes event grants in the same transaction
- [ ] Concurrent transfer confirmations yield one owner; no admin override exists
- [ ] Role change survives a stopped Keycloak; sweep repairs both directions
- [ ] `Org Admin - Team & Permissions.dc.html` read; roster shape and empty state match
- [ ] Invitation acceptance covers all five states
- [ ] e2e green on the F0-2 harness; compliance suite green
- [ ] `mvn -q -f backend/identity-service test -Dgroups=ET-ORG-002 -DfailIfNoTests=true` green
- [ ] Spec `status:` → `implemented`
