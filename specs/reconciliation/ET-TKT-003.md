# ET-TKT-003 · reconciliation

> **Validation, check-in and the offline gate**  
> Wave 5 · `booking-service` · subgraph `booking` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `partially-satisfied` — 11 of 13 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-TKT-003`; gate 0/14.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Collections

| Collection | State |
|---|---|
| `booking_checkin_conflicts` | bound to an `@Document` |
| `booking_checkins` | bound to an `@Document` |


4 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `checkInConflicts` | `already-satisfied` — in SDL, resolver bound |
| `checkInSummary` | `already-satisfied` — in SDL, resolver bound |
| `recentCheckIns` | `already-satisfied` — in SDL, resolver bound |


### Mutations

| Operation | State |
|---|---|
| `reviewConflict` | `already-satisfied` — in SDL, resolver bound |
| `uploadScans` | `already-satisfied` — in SDL, resolver bound |
| `validateByReference` | `absent` |
| `validateTicket` | `already-satisfied` — in SDL, resolver bound |


### Error codes

All 2 registered in `shared-library/.../error/ErrorCode.java`.


### Events

1 of 2 wire names appear in production source. Missing: `CheckInConflictDetectedEvent`


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
