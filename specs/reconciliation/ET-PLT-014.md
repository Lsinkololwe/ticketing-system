# ET-PLT-014 · reconciliation

> **The reference data engine — enum-derived, administrator-owned lookups and statuses**  
> Wave 2 · `catalog-service` · subgraph `catalog` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `mostly-absent` — 7 of 14 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-PLT-014`; gate 1/17.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Collections

| Collection | State |
|---|---|
| `catalog_reference_data` | bound to an `@Document` |


3 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `referenceData` | `already-satisfied` — in SDL, resolver bound |
| `referenceDataAll` | `contradicted` — built under another name: `referenceData`, `referenceDataByParent`, `referenceDataOffsetPagination` |
| `referenceTypes` | `already-satisfied` — in SDL, resolver bound |


### Mutations

| Operation | State |
|---|---|
| `createReferenceRow` | `contradicted` — built under another name: `createReferenceData` |
| `reorderReferenceRows` | `absent` |
| `setReferenceRowActive` | `contradicted` — built under another name: `setReferenceDataActive` |
| `updateReferenceRow` | `contradicted` — built under another name: `updateReferenceData` |


### Error codes

All 4 registered in `shared-library/.../error/ErrorCode.java`.


### Events

0 of 2 wire names appear in production source. Missing: `ReferenceDataChangedEvent`, `catalog.ReferenceDataChanged`


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
