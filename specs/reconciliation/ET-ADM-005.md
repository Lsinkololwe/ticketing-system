# ET-ADM-005 · reconciliation

> **Observability — metrics, tracing, SLOs, health and alerting**  
> Wave 6 · `all` · subgraph `identity` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `absent` — 0 of 5 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-ADM-005`; gate 0/15.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Queries

| Operation | State |
|---|---|
| `sloAttainment` | `absent` |
| `systemAlerts` | `absent` |
| `systemHealth` | `absent` |
| `transactionHealth` | `absent` |


### Mutations

| Operation | State |
|---|---|
| `acknowledgeAlert` | `absent` |


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
