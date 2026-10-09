# ET-PLT-003 · reconciliation

> **Event contract — two tiers, the envelope, the outbox, idempotent consumers**  
> Wave 0 · `all` · subgraph `None` · priority `must` · spec `status: in-progress`  
> Measured 2026-08-31 against the working tree.

**Presence** `partially-satisfied` — 22 of 23 §4 names exist.  
**Confidence** `under test` — 11 test class(es) tagged `ET-PLT-003`; gate 4/7.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Collections

| Collection | State |
|---|---|
| `booking_outbox` | named and used, but no `@Document` binds it |
| `catalog_outbox` | named and used, but no `@Document` binds it |
| `identity_outbox` | named and used, but no `@Document` binds it |


3 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Error codes

0 of 1 registered in `ErrorCode`. Missing: `UNSUPPORTED_SCHEMA_VERSION`


### Events

19 of 19 wire names appear in production source.


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
