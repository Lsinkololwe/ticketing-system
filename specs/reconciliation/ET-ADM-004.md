# ET-ADM-004 · reconciliation

> **Dashboard analytics and the polling contract**  
> Wave 6 · `all` · subgraph `all` · priority `should` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `fully-present` — 3 of 3 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-ADM-004`; gate 0/15.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Collections

| Collection | State |
|---|---|
| `booking_statistics_rollups` | named and used, but no `@Document` binds it |
| `catalog_statistics_rollups` | named and used, but no `@Document` binds it |
| `identity_statistics_rollups` | named and used, but no `@Document` binds it |


2 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
