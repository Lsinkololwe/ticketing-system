# ET-IDN-003 · reconciliation

> **Token revocation — cutting a live session before its token expires**  
> Wave 1 · `identity-service` · subgraph `identity` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `mostly-absent` — 4 of 11 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-IDN-003`; gate 0/13.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Collections

| Collection | State |
|---|---|
| `identity_token_revocations` | bound to an `@Document` |


3 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `activeRevocations` | `absent` |


### Mutations

| Operation | State |
|---|---|
| `revokeSession` | `absent` |
| `revokeToken` | `absent` |
| `revokeUserAccess` | `absent` |
| `signOutEverywhere` | `absent` |
| `signOutSession` | `absent` |


### Error codes

All 2 registered in `shared-library/.../error/ErrorCode.java`.


### Events

1 of 2 wire names appear in production source. Missing: `TokenRevokedEvent`


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
