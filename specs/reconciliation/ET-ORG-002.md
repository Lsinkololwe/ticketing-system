# ET-ORG-002 · reconciliation

> **Members, invitations and ownership transfer**  
> Wave 1 · `identity-service` · subgraph `identity` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `partially-satisfied` — 30 of 36 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-ORG-002`; gate 0/15.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


> **Amended 2026-09-01 — identity's pagination twins were collapsed.**
> The `*OffsetPagination` / `*CursorPagination` names quoted below no longer exist: under
> [`ROADMAP.md` D-19](../ROADMAP.md) each pair became one field under its bare name, keeping the
> shape §4 declares. Read the substitutes below as `<name>` without the suffix. The classification
> is unchanged — where the shipped name still differs from §4's, the operation is still
> `contradicted`.

### Collections

| Collection | State |
|---|---|
| `identity_organization_members` | bound to an `@Document` |
| `identity_ownership_transfers` | bound to an `@Document` |
| `identity_team_invitations` | bound to an `@Document` |


8 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `invitationByToken` | `already-satisfied` — in SDL, resolver bound |
| `myOrganizationMembership` | `already-satisfied` — in SDL, resolver bound |
| `myOrganizations` | `already-satisfied` — in SDL, resolver bound |
| `myPendingInvitations` | `already-satisfied` — in SDL, resolver bound |
| `organizationMembers` | `contradicted` — built under another name: `organizationMembersCursorPagination`, `organizationMembersOffsetPagination` |
| `pendingInvitations` | `contradicted` — built under another name: `myPendingInvitations`, `pendingInvitationsCursorPagination`, `pendingInvitationsOffsetPagination` |
| `pendingOwnershipTransfer` | `already-satisfied` — in SDL, resolver bound |


### Mutations

| Operation | State |
|---|---|
| `acceptInvitation` | `already-satisfied` — in SDL, resolver bound |
| `cancelOwnershipTransfer` | `already-satisfied` — in SDL, resolver bound |
| `confirmOwnershipTransfer` | `absent` |
| `declineInvitation` | `already-satisfied` — in SDL, resolver bound |
| `initiateOwnershipTransfer` | `already-satisfied` — in SDL, resolver bound |
| `inviteTeamMember` | `already-satisfied` — in SDL, resolver bound |
| `leaveOrganization` | `already-satisfied` — in SDL, resolver bound |
| `reactivateMember` | `already-satisfied` — in SDL, resolver bound |
| `removeMember` | `already-satisfied` — in SDL, resolver bound |
| `resendInvitation` | `already-satisfied` — in SDL, resolver bound |
| `revokeInvitation` | `already-satisfied` — in SDL, resolver bound |
| `suspendMember` | `already-satisfied` — in SDL, resolver bound |
| `updateMemberRole` | `already-satisfied` — in SDL, resolver bound |


### Error codes

All 8 registered in `shared-library/.../error/ErrorCode.java`.


### Events

2 of 5 wire names appear in production source. Missing: `InvitationCreatedEvent`, `OwnershipTransferredEvent`, `TeamMemberJoinedEvent`


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
