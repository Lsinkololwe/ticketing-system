# ET-ORG-001 · reconciliation

> **Organizer onboarding — nine states, staged access, the approval saga**  
> Wave 1 · `identity-service` · subgraph `identity` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `partially-satisfied` — 20 of 31 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-ORG-001`; gate 0/15.

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
| `identity_organizations` | bound to an `@Document` |
| `identity_verification_documents` | bound to an `@Document` |


4 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `myOrganization` | `contradicted` — built under another name: `hasOrganizationPermission`, `myOrganizationMembership`, `myOrganizationRole`, `myOwnedOrganization` |
| `organization` | `already-satisfied` — in SDL, resolver bound |
| `organizationBySlug` | `already-satisfied` — in SDL, resolver bound |
| `organizations` | `contradicted` — built under another name: `myOrganizations`, `organizationsCursorPagination`, `organizationsOffsetPagination` |
| `organizerApplications` | `absent` |
| `verificationDocuments` | `already-satisfied` — in SDL, resolver bound |


### Mutations

| Operation | State |
|---|---|
| `applyToBeOrganizer` | `already-satisfied` — in SDL, resolver bound |
| `approveOrganization` | `already-satisfied` — in SDL, resolver bound |
| `cancelOrganizationDeletion` | `absent` |
| `deactivateOrganization` | `absent` |
| `reactivateOrganization` | `absent` |
| `rejectOrganization` | `already-satisfied` — in SDL, resolver bound |
| `requestOrganizationChanges` | `already-satisfied` — in SDL, resolver bound |
| `requestOrganizationDeletion` | `absent` |
| `reviewDocument` | `absent` |
| `submitForReview` | `contradicted` — built under another name: `submitOrganizationForReview` |
| `suspendOrganization` | `already-satisfied` — in SDL, resolver bound |
| `updateOrganization` | `already-satisfied` — in SDL, resolver bound |


### Error codes

All 7 registered in `shared-library/.../error/ErrorCode.java`.


### Events

2 of 4 wire names appear in production source. Missing: `OrganizationDecidedEvent`, `OrganizationSubmittedEvent`


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
