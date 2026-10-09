# ET-PLT-005 · reconciliation

> **Error contract — the closed code registry, typed handlers, retryability**  
> Wave 0 · `all` · subgraph `all` · priority `must` · spec `status: in-progress`  
> Measured 2026-08-31 against the working tree.

**Presence** `fully-present` — 84 of 84 §4 names exist.  
**Confidence** `under test` — 12 test class(es) tagged `ET-PLT-005`; gate 0/11.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Error codes

All 84 registered in `shared-library/.../error/ErrorCode.java`.


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
