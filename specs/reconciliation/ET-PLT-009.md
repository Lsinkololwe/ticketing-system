# ET-PLT-009 · reconciliation

> **The audit trail — what is recorded, by whom, and for how long**  
> Wave 7 · `identity-service` · subgraph `identity` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `mostly-absent` — 1 of 6 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-PLT-009`; gate 0/12.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Collections

| Collection | State |
|---|---|
| `identity_audit_logs` | bound to an `@Document` |


5 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `auditLogs` | `absent` |
| `auditLogsForSubject` | `absent` |
| `myAuditTrail` | `absent` |
| `verifyAuditChain` | `absent` |


### Mutations

| Operation | State |
|---|---|
| `exportAuditTrail` | `absent` |


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
