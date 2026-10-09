# ET-FIN-002 · reconciliation

> **Commission — rate resolution and two-stage recognition**  
> Wave 4 · `booking-service` · subgraph `booking` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `mostly-absent` — 6 of 11 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-FIN-002`; gate 0/13.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Collections

| Collection | State |
|---|---|
| `booking_commission_records` | bound to an `@Document` |


3 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `commissionPreview` | `absent` |
| `commissionRecords` | `absent` |
| `commissionSummary` | `absent` |
| `platformCommissionSummary` | `absent` |


### Mutations

| Operation | State |
|---|---|
| `recogniseCommission` | `absent` |


### Error codes

All 2 registered in `shared-library/.../error/ErrorCode.java`.


### Events

3 of 3 wire names appear in production source.


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
