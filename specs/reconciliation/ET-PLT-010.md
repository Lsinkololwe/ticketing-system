# ET-PLT-010 · reconciliation

> **Schema evolution — event versions, GraphQL deprecation, document migration**  
> Wave 7 · `all` · subgraph `all` · priority `should` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `fully-present` — 3 of 3 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-PLT-010`; gate 0/13.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Collections

| Collection | State |
|---|---|
| `booking_migration_runs` | named and used, but no `@Document` binds it |
| `catalog_migration_runs` | named and used, but no `@Document` binds it |
| `identity_migration_runs` | named and used, but no `@Document` binds it |


1 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
