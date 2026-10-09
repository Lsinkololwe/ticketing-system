# ET-PLT-011 · reconciliation

> **Rate limits, on-sale fairness and abuse control**  
> Wave 7 · `all` · subgraph `all` · priority `should` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `absent` — 1 of 11 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-PLT-011`; gate 0/14.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Collections

| Collection | State |
|---|---|
| `identity_temporary_blocks` | **constant declared, used nowhere** |


2 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `eventQueueStatus` | `absent` |
| `queuePosition` | `absent` |
| `rateLimitStatus` | `absent` |
| `topLimitedSubjects` | `absent` |


### Mutations

| Operation | State |
|---|---|
| `blockActor` | `absent` |
| `joinQueue` | `absent` |
| `leaveQueue` | `absent` |
| `liftBlock` | `absent` |
| `setEventHighDemand` | `absent` |


### Error codes

All 1 registered in `shared-library/.../error/ErrorCode.java`.


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
