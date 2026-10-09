# ET-FIN-005 · reconciliation

> **Reconciliation — proving the ledger against the world**  
> Wave 4 · `booking-service` · subgraph `booking` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `mostly-absent` — 6 of 15 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-FIN-005`; gate 0/14.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Collections

| Collection | State |
|---|---|
| `booking_reconciliation_items` | **constant declared, used nowhere** |
| `booking_reconciliation_runs` | bound to an `@Document` |


3 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `financialSummary` | `absent` |
| `openReconciliationItems` | `absent` |
| `reconciliationItems` | `absent` |
| `reconciliationRun` | `already-satisfied` — in SDL, resolver bound |
| `reconciliationRuns` | `contradicted` — built under another name: `reconciliationRunsByType`, `reconciliationRunsOffsetPagination`, `reconciliationRunsRequiringReview` |
| `trialBalance` | `contradicted` — **in SDL, no resolver; fails at runtime** |


### Mutations

| Operation | State |
|---|---|
| `investigateItem` | `absent` |
| `recordBankSettlement` | `absent` |
| `resolveItem` | `contradicted` — built under another name: `resolveReconciliationItem` |
| `startReconciliation` | `already-satisfied` — in SDL, resolver bound |
| `writeOffItem` | `absent` |


### Error codes

All 1 registered in `shared-library/.../error/ErrorCode.java`.


### Events

1 of 1 wire names appear in production source.


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
