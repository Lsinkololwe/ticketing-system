# ET-NTF-002 · reconciliation

> **Lifecycle triggers — which fact produces which message**  
> Wave 5 · `identity-service` · subgraph `identity` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `mostly-absent` — 13 of 20 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-NTF-002`; gate 0/13.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Collections

| Collection | State |
|---|---|
| `identity_event_reminders` | bound to an `@Document` |
| `identity_mass_sends` | **constant declared, used nowhere** |


4 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `massSend` | `absent` |
| `massSendsForEvent` | `absent` |
| `myUpcomingReminders` | `absent` |
| `triggerRegistry` | `absent` |


### Mutations

| Operation | State |
|---|---|
| `pauseMassSend` | `absent` |
| `resumeMassSend` | `absent` |


### Events

12 of 12 wire names appear in production source.


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
