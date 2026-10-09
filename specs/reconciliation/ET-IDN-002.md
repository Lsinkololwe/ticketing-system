# ET-IDN-002 · reconciliation

> **Keycloak ↔ MongoDB user synchronisation**  
> Wave 1 · `identity-service` · subgraph `identity` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `partially-satisfied` — 8 of 11 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-IDN-002`; gate 0/13.

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
| `identity_users` | bound to an `@Document` |


3 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `me` | `already-satisfied` — in SDL, resolver bound |
| `user` | `already-satisfied` — in SDL, resolver bound |
| `users` | `contradicted` — built under another name: `usersByRole`, `usersCountByRole`, `usersCursorPagination`, `usersOffsetPagination` |


### Mutations

| Operation | State |
|---|---|
| `changePhoneNumber` | `absent` |
| `reconcileUsers` | `absent` |
| `suspendUser` | `contradicted` — **in SDL, no resolver; fails at runtime** |
| `updateMyProfile` | `contradicted` — **in SDL, no resolver; fails at runtime** |
| `updateUser` | `already-satisfied` — in SDL, resolver bound |


### Error codes

All 2 registered in `shared-library/.../error/ErrorCode.java`.


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
